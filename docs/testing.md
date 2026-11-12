# Tests

`./mvnw test` is offline and free: no test calls Claude, Jev or MET Norway. It needs Docker, because
`TestcontainersConfiguration` starts the real databases and a mail catcher.

## One set of containers per JVM (#98)

`src/test/java/app/rootology/TestcontainersConfiguration.java` holds Postgres 17, Neo4j and Mailpit as
**static singletons**: they start once, when the first Spring context asks for their addresses, and stay up
until the test JVM exits (Testcontainers' Ryuk removes them after that, also when the JVM is killed). Every
`@SpringBootTest` imports the class, so every context — and there are many, one per distinct configuration —
reuses the same three containers.

Spring builds a context for each distinct test configuration (property sources, bean overrides,
`webEnvironment`). Its context cache keeps up to 32 of them alive (the default `spring.test.context.cache.maxSize`),
so the addresses must stay valid for the whole run. The containers are therefore **not Spring beans**: a bean
of type `Startable` is stopped when its own context closes, and one context that fails to load (a database
not reachable yet) would stop the containers of every other context with it. The addresses reach the
contexts as properties from a `DynamicPropertyRegistrar`.

Do not enable `withReuse()`: it needs a per-user setting and would keep containers after the run.

### Limits

- Neo4j: `server.memory.heap.initial_size=256m`, `server.memory.heap.max_size=512m`,
  `server.memory.pagecache.size=128m`. Without a cap its JVM takes a quarter of the (unlimited) container
  memory, and long runs used to end with Neo4j OOMKilled or unreachable (#68).
- Postgres: `max_connections=300`, and `spring.datasource.hikari.maximum-pool-size=4` (with
  `minimum-idle=0`) in `src/test/resources/config/application.properties`. Every cached context keeps its
  own pool: the default pool of 10 over about 17 contexts is more than Postgres' default of 100.

## Isolation: the data is shared now

The containers are shared, so the rows, the graph and the mailbox of one test class are visible to the next.
Classes run sequentially in one JVM, so there is no race, but nothing is empty by accident any more.

Rules for a new test:

- **Unique logins per test class.** Rows are keyed by login (`spaces.signedIn`, notifications, roles, usage,
  invitation limits). A login another class also uses brings its spaces, its notifications and its email
  preference along. Prefix the logins with something of the class (`n-rita` in `NotificationServiceTest`).
- **Never count a whole table.** Count rows for the identities the test made:
  `where subject = ...`, `where recipient = ...`, or a helper that speaks only about its own logins.
- **Mailpit: filter by recipient.** `messageTo("x@example.org")` searches `to:"x@example.org"`; the mailbox
  holds every email of the run. Give the address of the test, and make it unique with the login.
- **The digest counts the whole outbox.** `NotificationService.digest()` words every notification that waits,
  whoever made it: a test that asserts how many digests went out deletes the outbox first
  (`NotificationServiceTest.emptyTheOutbox`). Same for the daily caps (`AccessRequestServiceTest`,
  `AccessNotificationsTest` delete the access requests they would count).
- **The graph is rebuilt at every context start** (`FactGraphLoader`): the curated knowledge comes back, and
  space-scoped facts of other classes disappear with it. An **approved global** proposal, instead, is written
  into the graph again and is visible to everyone: give a global proposal a name no other test uses
  (`pomodorino di ada` in `PrivacyTest`), or it will be resolved by name in another class.
- **Ask for the personal space, not the first one.** A login can belong to spaces other classes made:
  `spaces.spacesOf(login).getFirst()` may return a shared space where that login is a guest
  (`KnowledgeEnrichmentTest.spaceOf` filters `personal()`).

If a test needs an empty database, it says so and empties what it counts; it does not rely on the order of
the classes.

## Numbers (#98, macOS, Docker Desktop, one suite at a time)

| | Before | After |
|---|---|---|
| Spring contexts in one run | 17 (16 loaded, 1 failed) | 17 |
| Test containers at peak | 46 | 3 (+ Ryuk) |
| `./mvnw clean test` wall clock | 523 s, with one Postgres connection error (#68) | 113-120 s, no error |
| Tests | 502 run, 1 error | 549 run, 0 failures |

Three consecutive runs after the change, alone on the machine:

| Run | Tests | Failures | Errors | Wall clock | Containers at peak |
|---|---|---|---|---|---|
| 1 | 549 | 0 | 0 | 120 s | 3 (+ Ryuk) |
| 2 | 549 | 0 | 0 | 113 s | 3 (+ Ryuk) |
| 3 | 549 | 0 | 0 | 115 s | 3 (+ Ryuk) |

The contexts did not change: 6 of them come from the pipeline tests on Embabel's
`EmbabelMockitoIntegrationTest`, each with its own bean overrides, and the rest from property sources that
each class really needs (a fixed `Clock`, a mocked weather service or Telegram client, MockMvc, a random
port, the invitation and usage limits). The containers did: one set instead of one per context is what
removed the time and the out-of-memory failures.
