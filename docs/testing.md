# Tests

`./mvnw test` is offline and free: no test calls Claude, Jev or MET Norway. It needs Docker, because
`TestcontainersConfiguration` starts the real databases and a mail catcher.

## Which kind of test

Take the first level that can test the behaviour:

1. **A plain unit test, no Spring**: the default for rules, calculations, parsing and texts. If logic can
   only be tested by starting Spring, that is usually a sign it belongs in a plain class of its own.
2. **One of the shared contexts**: when the database, the wiring or security is the point. Extend
   `notifications.SettableClockContext` (moves time) or `pipeline.PipelineContext` (the agent), or use the
   main one exactly: `@Import(TestcontainersConfiguration.class)` and a bare `@SpringBootTest`. Add no
   properties, mocks or `@TestBean` of the class's own: read a configured value with `@Value` instead.
3. **A context of its own, only with a reason**: a real HTTP port, a property that changes the behaviour
   under test. Write the reason in the class's javadoc. A slice test (`@DataJpaTest`, `@WebMvcTest`) is
   one of these too: it makes its own context, so it is worth it only if it is lighter and shared.

Each new context costs 3-4 s of every build (see the numbers of #174 below).

## Code quality gates (#151)

`./mvnw verify` also runs PMD, CPD and JaCoCo, so CI runs them with the tests:

| Tool | What it checks | Where the report is |
|---|---|---|
| PMD | design (GodClass, NcssCount, CyclomaticComplexity, CognitiveComplexity) and the error-prone bug patterns, on `src/main/java` only | `target/reports/pmd.html`, `target/pmd.xml` |
| CPD | copy-paste: duplicated blocks of 100 tokens or more | `target/reports/cpd.html`, `target/cpd.xml` |
| JaCoCo | line coverage, with a floor on `pipeline`, `facts`, `decision` and `garden` | `target/site/jacoco/index.html`, `jacoco.csv` |

The ruleset is `config/pmd-ruleset.xml`; it says in a comment which error-prone rules are
excluded and why. The violations that existed when the gates were introduced are frozen in
`config/pmd-baseline.properties` (per class and rule) and `config/cpd-baseline.txt` (per
duplicated block): the build fails on a violation that is not in them. The baseline is a
freezer, not a permission slip: **it only shrinks**. When a hotspot is fixed, remove its
entry from the baseline in the same commit — a class that disappears from the report leaves a
line that no longer matches anything, and a rule no longer violated must be dropped so it
cannot come back unnoticed. Never add an entry to make a build pass: fix the code, or open an
issue for the hotspot and leave the build failing until it is agreed.

Coverage minimums live in the `jacoco-maven-plugin` configuration in `pom.xml`, one per module,
set at the value measured when the gate was introduced rounded down to the whole percent: they
can only rise. Measure them as CI does: `./mvnw clean verify` without the `ROOTOLOGY_*` keys in the
environment (`unset -m 'ROOTOLOGY_*'` in zsh). With a TypeSafe key the application context builds the
real Jev client, and without `clean` JaCoCo adds the data of earlier runs (live runs included): both
raise `decision` above what CI measures (about 90–95 % against 87 %). When coverage of a module rises well above its floor, raise the floor in the same
commit.

To reproduce what CI checks without the browsers: `./mvnw verify`, then open the reports above.
`-Dpmd.skip=true -Djacoco.skip=true` skips the gates for a quick local loop; CI never does.

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

### One context per group (#174)

Every new context costs 3-4 s, so tests of the same area share one. Two overrides are the same only if they
use the same `@TestBean` factory method and, for by-type mocks, the same field name: a copy in each class is
a new context. So the shared configuration lives in one base class per group, and a new test extends it
instead of declaring its own properties, clock or doubles:

- `notifications.SettableClockContext`: a settable clock (`now`, set by each class in `@BeforeEach`), the scheduled jobs
  once an hour, a mocked `TelegramClient` and a spied `MailService`; for notifications, access and usage.
- `pipeline.PipelineContext`: Embabel's base with no Jev key, a mocked `WeatherService` and spied
  `DomainFilter` and `DecisionLayer`.

Stub a shared mock or spy with `doReturn(...).when(mock).method(...)`, never `when(mock.method())`: on a spy
that form calls the real method, and on any shared double a scheduled job calling it from another thread can
take the stub (Mockito's `WrongTypeOfReturnValue`, seen in CI after #174).

To see which classes make their own context, add `digital.pragmatech.testing:spring-test-profiler` (test
scope, MIT) on a local branch and read `target/spring-test-profiler/latest.html` after a run; it is not kept
in the build.

### Limits

- Neo4j: `server.memory.heap.initial_size=256m`, `server.memory.heap.max_size=512m`,
  `server.memory.pagecache.size=128m`. Without a cap its JVM takes a quarter of the (unlimited) container
  memory, and long runs used to end with Neo4j OOMKilled or unreachable (#68).
- Postgres: `max_connections=300`, and `spring.datasource.hikari.maximum-pool-size=4` (with
  `minimum-idle=0`) in `src/test/resources/config/application.properties`. Every cached context keeps its
  own pool: the default pool of 10 over the 9 contexts is more than Postgres' default of 100.

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
  use `TestSpaces.personal(spaces, login)`. The order of the test classes differs between macOS and the CI
  runner, so a test that only passes locally may fail in CI.
- **Wait for the Vaadin dev server.** A test that reads a Vaadin page over HTTP may get Vaadin's waiting page
  while the dev server starts (`PublicSeoTest.page` retries until it is gone).

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

## Numbers (#174, macOS, Docker Desktop, `./mvnw clean verify`)

| | Before | Notifications and pipeline grouped | Access, usage and properties too |
|---|---|---|---|
| Spring contexts | 24 | 15 | 9 |
| Time creating them | 128 s | 89 s | 67 s |
| Wall clock | 215 s | 165 s | 145 s |
| Tests | 921, 0 failures | 921, 0 failures | 921, 0 failures |

The 9 left: the main one, `SettableClockContext`, `PipelineContext`, `WeatherAlertsTest` (a zero grouping
window), `GoldenQuestionsTest` (the quality suite), the random port of `PublicSeoTest` and
`LoggedOutPageTest`, `SecurityTest` (MockMvc), `ContextResolverTest` and `WeatherServiceTest` (a mock each).
