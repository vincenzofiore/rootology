package app.rootology.privacy;

import app.rootology.TestcontainersConfiguration;
import app.rootology.spaces.SpaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Account deletion, data export and retention (#40) on a real database. Each test uses its own logins, so
 * tests never see each other's rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PrivacyTest {

    @Autowired
    SpaceService spaces;

    @Autowired
    AccountDeletion deletion;

    @Autowired
    DataExport export;

    @Autowired
    RetentionJob retentionJob;

    @Autowired
    Retention retention;

    @Autowired
    JdbcClient jdbc;

    @Test
    void deletingAnAccountRemovesItsOwnDataAndAnonymisesWhatOthersKeep() {
        spaces.signedIn("del-ada");
        spaces.signedIn("del-bob");
        long adaSpace = personalSpace("del-ada");
        long bobSpace = personalSpace("del-bob");
        spaces.addMember(bobSpace, "del-bob", "del-ada", app.rootology.spaces.Role.MEMBER);

        long garden = jdbc.sql("insert into garden (space_id, name, created_at) values (:s, 'Orto', now()) returning id")
                .param("s", adaSpace).query(Long.class).single();
        jdbc.sql("insert into plot (garden_id, name, kind, soil) values (:g, 'A', 'GROUND', 'LOAMY')").param("g", garden).update();
        run(adaSpace, "del-ada", "a question in my own space", Instant.now());
        run(bobSpace, "del-ada", "a question in bob's space", Instant.now());
        run(bobSpace, "del-bob", "bob's own question", Instant.now());
        jdbc.sql("insert into notification_preference (github_login, email) values ('del-ada', 'ada@example.org')").update();
        jdbc.sql("""
                insert into user_identity (user_id, provider, subject, email, linked_at)
                values ((select id from app_user where login = 'del-ada'), 'github', '4242', 'ada@example.org', now())""").update();
        jdbc.sql("""
                insert into user_identity (user_id, provider, subject, email, linked_at)
                values ((select id from app_user where login = 'del-ada'), 'google', '4243', 'ada@gmail.com', now())""").update();
        notification("del-ada", bobSpace);
        notification("del-bob", bobSpace);
        jdbc.sql("insert into telegram_link (login, chat_id, linked_at) values ('del-ada', 6001, now())").update();
        jdbc.sql("""
                insert into telegram_link_code (code_hash, login, created_at, expires_at)
                values ('aaaa', 'del-ada', now(), now() + interval '10 minutes')""").update();
        jdbc.sql("""
                insert into usage_record (github_login, space_id, kind, at, claude_calls, prompt_tokens, completion_tokens,
                                          cost_usd, jev_calls)
                values ('del-ada', :s, 'QUESTION', now(), 2, 100, 10, 0.01, 1)""").param("s", bobSpace).update();
        long globalFact = proposal(adaSpace, "GLOBAL", "APPROVED", "del-ada");
        proposal(adaSpace, "GLOBAL", "PENDING", "del-ada");
        long spaceFact = proposal(adaSpace, "SPACE", "APPROVED", "del-ada");
        long inBobSpace = proposal(bobSpace, "GLOBAL", "PENDING", "del-ada");

        assertThat(deletion.preview("del-ada").spacesDeleted()).containsExactly("del-ada's garden");
        deletion.delete("DEL-Ada");

        assertThat(count("select count(*) from app_user where login = 'del-ada'")).isZero();
        // The identities go with the account (#28), by the foreign key's cascade.
        assertThat(count("select count(*) from user_identity where subject in ('4242', '4243')")).isZero();
        assertThat(count("select count(*) from space where id = " + adaSpace)).isZero();
        assertThat(count("select count(*) from garden where id = " + garden)).isZero();
        assertThat(count("select count(*) from notification_preference where github_login = 'del-ada'")).isZero();
        assertThat(count("select count(*) from usage_record where github_login = 'del-ada'")).isZero();
        assertThat(count("select count(*) from notification where recipient = 'del-ada'")).isZero();
        assertThat(count("select count(*) from notification where recipient = 'del-bob'")).isEqualTo(1);
        // The Telegram link and any code of that person go with the account (#38).
        assertThat(count("select count(*) from telegram_link where login = 'del-ada'")).isZero();
        assertThat(count("select count(*) from telegram_link_code where login = 'del-ada'")).isZero();
        // Bob's space keeps Ada's question, without her login, and Bob's own question as it was.
        assertThat(jdbc.sql("select started_by from pipeline_run where question = 'a question in bob''s space'")
                .query(String.class).single()).isEqualTo(AccountDeletion.DELETED_USER);
        assertThat(count("select count(*) from pipeline_run where question = 'a question in my own space'")).isZero();
        assertThat(count("select count(*) from pipeline_run where started_by = 'del-bob'")).isEqualTo(1);
        // The approved global fact stays for everyone, with no space; the open proposal in her space goes.
        assertThat(jdbc.sql("select coalesce(space_id, -1) || ' ' || proposed_by from knowledge_proposal where id = :id")
                .param("id", globalFact).query(String.class).single()).isEqualTo("-1 " + AccountDeletion.DELETED_USER);
        assertThat(count("select count(*) from knowledge_proposal where proposed_by = 'del-ada'")).isZero();
        assertThat(count("select count(*) from knowledge_proposal where id = " + spaceFact)).isZero();
        assertThat(count("select count(*) from knowledge_proposal where id = " + inBobSpace)).isEqualTo(1);
        assertThat(spaces.membersOf(bobSpace, "del-bob")).extracting(app.rootology.spaces.Member::githubLogin)
                .containsExactly("del-bob");
    }

    @Test
    void theOnlyOwnerOfASpaceOthersUseCannotDeleteTheAccount() {
        spaces.signedIn("own-cleo");
        spaces.signedIn("own-dan");
        long shared = jdbc.sql("insert into space (name, created_at) values ('Community garden', now()) returning id")
                .query(Long.class).single();
        member(shared, "own-cleo", "OWNER");
        member(shared, "own-dan", "MEMBER");

        AccountDeletion.Preview preview = deletion.preview("own-cleo");
        assertThat(preview.allowed()).isFalse();
        assertThat(preview.blockingSpaces()).containsExactly("Community garden");
        assertThatThrownBy(() -> deletion.delete("own-cleo")).isInstanceOf(IllegalStateException.class);
        assertThat(count("select count(*) from app_user where login = 'own-cleo'")).isEqualTo(1);

        // Dan, a plain member, can go: the space stays with Cleo.
        deletion.delete("own-dan");
        assertThat(count("select count(*) from space where id = " + shared)).isEqualTo(1);
        assertThat(deletion.preview("own-cleo").spacesDeleted()).contains("Community garden");
    }

    @Test
    void theLastAdminCannotDeleteTheAccount() {
        spaces.signedIn("adm-eve");
        long before = count("select count(*) from app_role_grant where role = 'ADMIN' and revoked_at is null");
        jdbc.sql("""
                insert into app_role_grant (github_login, role, granted_by, granted_at)
                values ('adm-eve', 'ADMIN', 'setup', now())""").update();
        assertThat(deletion.preview("adm-eve").lastAdmin()).isEqualTo(before == 0);
        jdbc.sql("update app_role_grant set revoked_at = now(), revoked_by = 'setup' where github_login = 'adm-eve'").update();
    }

    @Test
    void theExportHoldsThePersonsDataAndNotOtherPeoplesQuestions() {
        spaces.signedIn("exp-fay");
        spaces.signedIn("exp-gus");
        long gusSpace = personalSpace("exp-gus");
        spaces.addMember(gusSpace, "exp-gus", "exp-fay", app.rootology.spaces.Role.MEMBER);
        run(gusSpace, "exp-fay", "fay asks about basil", Instant.now());
        run(gusSpace, "exp-gus", "gus asks about leeks", Instant.now());
        jdbc.sql("insert into garden (space_id, name, created_at) values (:s, 'Gus garden', now())").param("s", gusSpace).update();
        jdbc.sql("""
                insert into plot (garden_id, name, kind, soil, orientation, direct_sun, wind, floor)
                values ((select id from garden where space_id = :s), 'Gus pot', 'CONTAINER', 'POTTING_MIX',
                        'N', 'SHADE', 'EXPOSED', 3)""").param("s", gusSpace).update();
        jdbc.sql("""
                insert into user_identity (user_id, provider, subject, email, linked_at)
                values ((select id from app_user where login = 'exp-fay'), 'github', '5252', 'fay@example.org', now())""").update();
        jdbc.sql("""
                insert into user_identity (user_id, provider, subject, email, linked_at)
                values ((select id from app_user where login = 'exp-fay'), 'google', '5253', 'fay@gmail.com', now())""").update();

        jdbc.sql("insert into telegram_link (login, chat_id, linked_at) values ('exp-fay', 6002, now())").update();

        String json = new String(export.export("exp-fay"), StandardCharsets.UTF_8);

        assertThat(json).contains("\"githubLogin\" : \"exp-fay\"", "fay asks about basil", "Gus garden", "exp-gus's garden",
                        "\"identities\"", "\"provider\" : \"github\"", "\"subject\" : \"5252\"", "fay@example.org",
                        "\"provider\" : \"google\"", "\"subject\" : \"5253\"", "fay@gmail.com",
                        "Gus pot", "\"direct_sun\" : \"SHADE\"", "\"floor\" : 3",
                        // #56: the garden a question belongs to is part of the export, like its space.
                        "\"garden_id\"",
                        // #38: the Telegram chat the person linked.
                        "\"telegramLinks\"", "\"chat_id\" : 6002")
                .doesNotContain("gus asks about leeks");
    }

    @Test
    void theNightlyJobDeletesOnlyWhatIsOlderThanTheRetention() {
        spaces.signedIn("ret-hal");
        long space = personalSpace("ret-hal");
        Instant old = Instant.now().minus(retention.history()).minus(Duration.ofDays(1));
        run(space, "ret-hal", "an old question", old);
        run(space, "ret-hal", "a recent question", Instant.now());

        jdbc.sql("""
                insert into telegram_link (login, chat_id, linked_at, failed_at)
                values ('ret-hal', 6003, now() - interval '60 days', now() - interval '31 days')""").update();
        jdbc.sql("""
                insert into telegram_link (login, chat_id, linked_at)
                values ('ret-hal-other', 6004, now())""").update();
        jdbc.sql("""
                insert into telegram_link_code (code_hash, login, created_at, expires_at, used_at)
                values ('bbbb', 'ret-hal', now() - interval '31 days', now() - interval '31 days',
                        now() - interval '31 days')""").update();

        retentionJob.run();

        assertThat(count("select count(*) from telegram_link where chat_id = 6003"))
                .as("a link Telegram no longer delivers to, after the retention").isZero();
        assertThat(count("select count(*) from telegram_link where chat_id = 6004"))
                .as("a working link stays").isEqualTo(1);
        assertThat(count("select count(*) from telegram_link_code where code_hash = 'bbbb'")).isZero();
        assertThat(count("select count(*) from pipeline_run where question = 'an old question'")).isZero();
        assertThat(count("select count(*) from pipeline_run where question = 'a recent question'")).isEqualTo(1);
    }

    private long personalSpace(String login) {
        return spaces.spacesOf(login).getFirst().id();
    }

    private void member(long space, String login, String role) {
        jdbc.sql("""
                insert into space_member (space_id, user_id, role, joined_at)
                values (:s, (select id from app_user where login = :l), :r, now())""")
                .param("s", space).param("l", login).param("r", role).update();
    }

    private void run(long space, String login, String question, Instant at) {
        jdbc.sql("""
                insert into pipeline_run (process_id, question_id, question, noise_dropout, noise_spurious, noise_mislabel,
                    noise_near_subject, noise_seed, decision_answer, decision_confidence, decision_escalated,
                    decision_reason, answer, answered_by_human, answer_text, finished_at, space_id, started_by)
                values (gen_random_uuid()::text, 'free', :q, 0, 0, 0, false, 0, '{}', 1, false, 'test', '{}', false,
                    'an answer', :at, :s, :l)""")
                .param("q", question).param("at", Timestamp.from(at)).param("s", space).param("l", login).update();
    }

    private void notification(String recipient, long space) {
        jdbc.sql("""
                insert into notification (recipient, space_id, kind, title, body, created_at)
                values (:r, :s, 'ESCALATION_WAITING', 'A question waits', 'body', now())""")
                .param("r", recipient).param("s", space).update();
    }

    private long proposal(long space, String scope, String status, String by) {
        return jdbc.sql("""
                insert into knowledge_proposal (space_id, scope, kind, target_id, alias_name, alias_language,
                    proposed_by, created_at, status, citation)
                values (:s, :scope, 'ALIAS', 'tomato', 'pomodorino', 'it', :by, now(), :status, 'a source')
                returning id""")
                .param("s", space).param("scope", scope).param("status", status).param("by", by)
                .query(Long.class).single();
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
