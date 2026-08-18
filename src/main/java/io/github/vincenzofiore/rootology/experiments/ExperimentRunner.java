package io.github.vincenzofiore.rootology.experiments;

import io.github.vincenzofiore.rootology.inference.ChainedRelationQuery;
import io.github.vincenzofiore.rootology.inference.CompanionQuery;
import io.github.vincenzofiore.rootology.inference.FactPresenceQuery;
import io.github.vincenzofiore.rootology.inference.PestQuery;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs the Jena side of each experiment question automatically.
 * The LLM side is supplied manually (see recordLlmAnswer), since it comes
 * from a real conversation with Claude Code / Copilot using the skill file.
 */
@Component
public class ExperimentRunner {

    private final CompanionQuery companionQuery;
    private final PestQuery pestQuery;
    private final ChainedRelationQuery chainedRelationQuery;
    private final FactPresenceQuery factPresenceQuery;
    private final List<ExperimentResult> results = new ArrayList<>();

    public ExperimentRunner(CompanionQuery companionQuery, PestQuery pestQuery, ChainedRelationQuery chainedRelationQuery, FactPresenceQuery factPresenceQuery) {
        this.companionQuery = companionQuery;
        this.pestQuery = pestQuery;
        this.chainedRelationQuery = chainedRelationQuery;
        this.factPresenceQuery = factPresenceQuery;
    }

    /** Case 1: simple direct relation (one hop). */
    public List<String> jenaCompanionsOf(String plantName) {
        return companionQuery.findCompanionsOf(plantName);
    }

    /** Case 2: two-step filter (active pests minus repelled pests). */
    public List<String> jenaRelevantPests(String plantName, String seasonName) {
        return pestQuery.findRelevantPests(plantName, seasonName);
    }

    /** Case hard-1: three-hop chained relation through a shared repelled pest. */
    public List<String> jenaChainedCompanions(String referencePlantName) {
        return chainedRelationQuery.findCompanionsOfPlantsSharingRepelledPest(referencePlantName);
    }

    /** Case hard-2: explicit check for a possibly-missing fact (no guessing). */
    public Optional<List<String>> jenaDeclaredSeasonsOf(String pestName) {
        return factPresenceQuery.activeSeasonsOf(pestName);
    }

    /** Records a comparison with only the "with skill" LLM arm. */
    public ExperimentResult recordLlmAnswer(String questionId, String question,
                                            List<String> jenaAnswer, List<String> llmAnswer,
                                            String notes) {
        ExperimentResult result = ExperimentResult.of(questionId, question, jenaAnswer, llmAnswer, notes);
        results.add(result);
        return result;
    }

    /** Records a comparison including a "without skill" baseline for contrast. */
    public ExperimentResult recordLlmAnswerWithBaseline(String questionId, String question,
                                                        List<String> jenaAnswer,
                                                        List<String> llmWithSkillAnswer,
                                                        List<String> llmWithoutSkillAnswer,
                                                        String notes) {
        ExperimentResult result = ExperimentResult.withBaseline(questionId, question, jenaAnswer,
                llmWithSkillAnswer, llmWithoutSkillAnswer, notes);
        results.add(result);
        return result;
    }

    public List<ExperimentResult> allResults() {
        return List.copyOf(results);
    }
}
