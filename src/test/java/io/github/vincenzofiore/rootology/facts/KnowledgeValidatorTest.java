package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.HostRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.OrganismRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.PairRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.SourceRow;
import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.WindowRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The validator accepts the curated files and catches each kind of mistake, which would stop startup. */
class KnowledgeValidatorTest {

    private final CuratedKnowledge real = CuratedKnowledgeReader.read(new CropCatalog());

    private static <T> List<T> plus(List<T> list, T extra) {
        List<T> copy = new ArrayList<>(list);
        copy.add(extra);
        return copy;
    }

    @Test
    void theCuratedFilesAreValid() {
        assertThat(KnowledgeValidator.validate(real)).isEmpty();
        assertThat(real.crops()).hasSize(39);
        assertThat(real.needs()).hasSize(38);
        assertThat(real.pairs()).hasSize(66);
        assertThat(real.organisms()).hasSize(24);
        assertThat(real.windows()).hasSize(93);
    }

    @Test
    void refusesASourceThatDoesNotAllowCommercialUse() {
        var k = new CuratedKnowledge(plus(real.sources(), new SourceRow("pfaf", "PFAF", "https://pfaf.org",
                "CC BY-NC-SA", false, "2026-11-03")), real.crops(), real.needs(), real.windows(), real.pairs(),
                real.organisms(), real.hosts());

        assertThat(KnowledgeValidator.validate(k)).contains("source pfaf does not allow commercial use");
    }

    @Test
    void refusesUnknownCropsSourcesAndOrganisms() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(),
                plus(real.windows(), new WindowRow("lavender", "CENTRO", "SOW", 3, 4, "", "rootology-editorial", false)),
                plus(real.pairs(), new PairRow("tomato", "basil", "COMPANION", "x", "traditional", List.of("blog"))),
                real.organisms(),
                plus(real.hosts(), new HostRow("XXXXXX", "tomato", 5, 6, "", "rootology-editorial", false)));

        assertThat(KnowledgeValidator.validate(k)).contains(
                "window lavender SOW: unknown crop lavender",
                "pair tomato-basil: unknown source blog",
                "host XXXXXX-tomato: unknown organism XXXXXX");
    }

    @Test
    void refusesAPairThatIsBothCompanionAndAntagonist() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(), real.windows(),
                plus(real.pairs(), new PairRow("basil", "tomato", "ANTAGONIST", "x", "traditional",
                        List.of("cornell-1999"))), real.organisms(), real.hosts());

        assertThat(KnowledgeValidator.validate(k)).anyMatch(p -> p.endsWith("is both companion and antagonist"));
    }

    @Test
    void refusesBadMonthsKindsAndCodes() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(),
                plus(real.windows(), new WindowRow("tomato", "CENTRO", "HARVEST", 13, 2, "", "rootology-editorial", false)),
                real.pairs(),
                plus(real.organisms(), new OrganismRow("aphid", "x", "x", "x", "BIRD", "eppo-codes")),
                real.hosts());

        assertThat(KnowledgeValidator.validate(k)).contains(
                "window tomato HARVEST: month 13 is not between 1 and 12",
                "organism aphid is not an EPPO code",
                "organism aphid: kind must be one of " + KnowledgeValidator.KINDS + ", not BIRD",
                "organism aphid affects no crop");
    }
}
