package io.github.vincenzofiore.rootology.enrichment;

import io.github.vincenzofiore.rootology.facts.KnowledgeContributions;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Knowledge proposals: export and account deletion (#40, docs/privacy.md). */
@Component
public class ProposalPersonalData {

    private final JdbcClient jdbc;
    private final KnowledgeContributions graph;

    ProposalPersonalData(JdbcClient jdbc, KnowledgeContributions graph) {
        this.jdbc = jdbc;
        this.graph = graph;
    }

    public List<Map<String, Object>> export(String login) {
        return jdbc.sql("""
                        select id, space_id, scope, kind, target_id, alias_name, alias_language, crop_id, partner_id,
                               relation, effect, organism_id, from_month, to_month, citation, created_at, status,
                               reviewed_by, reviewed_at, review_note
                        from knowledge_proposal where proposed_by = :login order by id""")
                .param("login", login).query().listOfRows();
    }

    /**
     * In the deleted spaces, space facts go (rows and graph); an approved global fact stays for everyone, with
     * no space; other global proposals go. Elsewhere the login is replaced. The graph changes after commit,
     * so a failed deletion leaves the facts in place.
     */
    public void forget(String login, Collection<Long> spaces, String replacement) {
        if (!spaces.isEmpty()) {
            List<Long> spaceFacts = jdbc.sql("""
                            select id from knowledge_proposal where space_id in (:spaces) and scope = 'SPACE' and status = 'APPROVED'""")
                    .param("spaces", spaces).query(Long.class).list();
            jdbc.sql("""
                            update knowledge_proposal set space_id = null
                            where space_id in (:spaces) and scope = 'GLOBAL' and status = 'APPROVED'""")
                    .param("spaces", spaces).update();
            jdbc.sql("delete from knowledge_proposal where space_id in (:spaces)").param("spaces", spaces).update();
            if (!spaceFacts.isEmpty()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        spaceFacts.forEach(graph::remove);
                    }
                });
            }
        }
        for (String column : List.of("proposed_by", "reviewed_by", "withdrawn_by")) {
            jdbc.sql("update knowledge_proposal set " + column + " = :r where " + column + " = :login")
                    .param("r", replacement).param("login", login).update();
        }
    }
}
