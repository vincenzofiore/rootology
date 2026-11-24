package app.rootology.facts;

import app.rootology.facts.CuratedKnowledge.ControlRow;
import app.rootology.facts.CuratedKnowledge.HostRow;
import app.rootology.facts.CuratedKnowledge.OrganismRow;
import app.rootology.facts.CuratedKnowledge.PairRow;
import app.rootology.facts.CuratedKnowledge.SourceRow;
import app.rootology.facts.CuratedKnowledge.WindowRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The validator accepts the curated files and catches each kind of mistake, which would stop startup. */
class KnowledgeValidatorTest {

    private final CuratedKnowledge real = CuratedKnowledgeReader.read(new CropCatalog());

    @SafeVarargs
    private static <T> List<T> plus(List<T> list, T... extra) {
        List<T> copy = new ArrayList<>(list);
        copy.addAll(List.of(extra));
        return copy;
    }

    @Test
    void theCuratedFilesAreValid() {
        assertThat(KnowledgeValidator.validate(real)).isEmpty();
        assertThat(real.crops()).hasSize(122);
        assertThat(real.needs()).hasSize(113);
        assertThat(real.pairs()).hasSize(66);
        assertThat(real.organisms()).hasSize(24);
        assertThat(real.windows()).hasSize(346);
        assertThat(real.controls()).hasSize(92);
        assertThat(real.controls()).extracting(ControlRow::organismId).containsAll(
                real.organisms().stream().map(OrganismRow::id).toList());
    }

    @Test
    void refusesACropWithoutAValidFrostGroup() {
        var k = new CuratedKnowledge(real.sources(),
                plus(real.crops(), new Crop("papaya", "Papaya", "Papaya", "Carica papaya",
                        "Caricaceae", "Q34887", null, "NOT_A_GROUP", "la", "PERENNIAL", null, null, "FOOD")),
                real.needs(), real.windows(), real.pairs(), real.organisms(), real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).contains(
                "crop papaya: frost_group must be one of " + FrostShiftRule.GROUPS + ", not NOT_A_GROUP");
    }

    @Test
    void refusesACropWithoutAKnownItalianArticle() {
        var k = new CuratedKnowledge(real.sources(),
                plus(real.crops(), new Crop("papaya", "Papaya", "Papaya", "Carica papaya",
                        "Caricaceae", "Q34887", null, "TENDER", "una", "PERENNIAL", null, null, "FOOD")),
                real.needs(), real.windows(), real.pairs(), real.organisms(), real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).anyMatch(p -> p.startsWith("crop papaya: italian_article"));
    }

    @Test
    void refusesACropWhoseUseIsNeitherFoodNorService() {
        var k = new CuratedKnowledge(real.sources(),
                plus(real.crops(), new Crop("papaya", "Papaya", "Papaya", "Carica papaya",
                        "Caricaceae", "Q34887", null, "TENDER", "la", "PERENNIAL", null, null, "FLOWER")),
                real.needs(), real.windows(), real.pairs(), real.organisms(), real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).contains(
                "crop papaya: use must be one of " + Crop.USES + ", not FLOWER");
    }

    @Test
    void keepsFrostHardinessForPerennialsWithAKnownSource() {
        var k = new CuratedKnowledge(real.sources(), plus(real.crops(),
                new Crop("papaya", "Papaya", "Papaya", "Carica papaya", "Caricaceae", "Q34887", null,
                        "HARDY", "la", "ANNUAL", -15.0, "rootology-editorial", "FOOD"),
                new Crop("mango", "Mango", "Mango", "Mangifera indica", "Anacardiaceae", "Q3919027", null, "HARDY", "il",
                        "PERENNIAL", -15.0, "a-blog", "FOOD"),
                new Crop("feijoa", "Feijoa", "Feijoa", "Acca sellowiana", "Myrtaceae", "Q1092496", null, "HARDY", "la",
                        "EVERGREEN", null, null, "FOOD")),
                real.needs(), real.windows(), real.pairs(), real.organisms(), real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).contains(
                "crop papaya: frost_hardiness is for perennials only",
                "crop mango: hardiness_source: unknown source a-blog",
                "crop feijoa: lifecycle must be one of " + Crop.LIFECYCLES + ", not EVERGREEN");
    }

    @Test
    void checksTheClimateCellsFile() {
        assertThat(KnowledgeValidator.validateCells(new ClimateCells())).isEmpty();

        ClimateCells bad = new ClimateCells(new int[]{0}, new int[]{0}, new short[]{-10_000}, new byte[]{101},
                new short[]{0}, new short[]{400}, new short[]{0}, new short[]{0}, new byte[]{31},
                new short[]{ClimateCells.NO_WINTER_MIN});

        assertThat(KnowledgeValidator.validateCells(bad)).anyMatch(p -> p.contains("elevation -10000"))
                .anyMatch(p -> p.contains("land fraction 1.01"))
                .anyMatch(p -> p.contains("last frost p80 day 400"))
                .anyMatch(p -> p.contains("frost years 31"));
    }

    @Test
    void refusesASourceThatDoesNotAllowCommercialUse() {
        var k = new CuratedKnowledge(plus(real.sources(), new SourceRow("pfaf", "PFAF", "https://pfaf.org",
                "CC BY-NC-SA", false, "2026-11-03")), real.crops(), real.needs(), real.windows(), real.pairs(),
                real.organisms(), real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).contains("source pfaf does not allow commercial use");
    }

    @Test
    void refusesUnknownCropsSourcesAndOrganisms() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(),
                plus(real.windows(), new WindowRow("papaya", "CENTRO", "SOW", 3, 4, "", "rootology-editorial", false)),
                plus(real.pairs(), new PairRow("tomato", "basil", "COMPANION", "x", "traditional", List.of("blog"))),
                real.organisms(),
                plus(real.hosts(), new HostRow("XXXXXX", "tomato", 5, 6, "", "rootology-editorial", false)), real.controls());

        assertThat(KnowledgeValidator.validate(k)).contains(
                "window papaya SOW: unknown crop papaya",
                "pair tomato-basil: unknown source blog",
                "host XXXXXX-tomato: unknown organism XXXXXX");
    }

    @Test
    void refusesControlsWithoutKnownPestKindSourceOrOrganicListing() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(), real.windows(), real.pairs(),
                real.organisms(), real.hosts(), plus(real.controls(),
                new ControlRow("XXXXXX", "Hand-pick", "PRACTICE", "", "traditional", List.of("rootology-editorial")),
                new ControlRow("1APHIF", "Spray", "POTION", "", "folklore", List.of("blog")),
                new ControlRow("1APHIF", "Pyrethrins", "SUBSTANCE", "", "extension", List.of("rhs-aphids")),
                new ControlRow("1APHIF", "Sow marigolds", "SERVICE_PLANT", "papaya", "traditional",
                        List.of("rootology-editorial"))));

        assertThat(KnowledgeValidator.validate(k)).contains(
                "control XXXXXX \"Hand-pick\": unknown organism XXXXXX",
                "control 1APHIF \"Spray\": unknown source blog",
                "control 1APHIF \"Pyrethrins\": a substance or microorganism must cite eu-2021-1165",
                "control 1APHIF \"Sow marigolds\": unknown crop papaya")
                .anyMatch(p -> p.startsWith("control 1APHIF \"Spray\": kind"))
                .anyMatch(p -> p.startsWith("control 1APHIF \"Spray\": evidence"));
    }

    @Test
    void refusesAPairThatIsBothCompanionAndAntagonist() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(), real.windows(),
                plus(real.pairs(), new PairRow("basil", "tomato", "ANTAGONIST", "x", "traditional",
                        List.of("cornell-1999"))), real.organisms(), real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).anyMatch(p -> p.endsWith("is both companion and antagonist"));
    }

    @Test
    void refusesBadMonthsKindsAndCodes() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(),
                plus(real.windows(), new WindowRow("tomato", "CENTRO", "HARVEST", 13, 2, "", "rootology-editorial", false)),
                real.pairs(),
                plus(real.organisms(), new OrganismRow("aphid", "x", "x", "x", "BIRD", "eppo-codes")),
                real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).contains(
                "window tomato HARVEST: month 13 is not between 1 and 12",
                "organism aphid is not an EPPO code",
                "organism aphid: kind must be one of " + KnowledgeValidator.KINDS + ", not BIRD",
                "organism aphid affects no crop");
    }

    @Test
    void refusesACommonNameThatIsAlreadyAnotherName() {
        var k = new CuratedKnowledge(real.sources(), real.crops(), real.needs(), real.windows(), real.pairs(),
                plus(real.organisms(), new OrganismRow("ABCDEF", "x", "x", "x", "INSECT", "eppo-codes",
                        java.util.List.of("pomodoro", "lumache", "Mosca"))),
                real.hosts(), real.controls());

        assertThat(KnowledgeValidator.validate(k)).contains(
                "organism ABCDEF: other name 'pomodoro' is already the name of crop tomato",
                "organism ABCDEF: other name 'lumache' is already the name of organism DERORE",
                "organism ABCDEF: other name 'Mosca' is not a lower-case name");
    }
}
