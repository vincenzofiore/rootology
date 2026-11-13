package app.rootology.facts;

import java.util.List;

/**
 * The curated knowledge files (`src/main/resources/knowledge/`) as typed rows, before they are
 * validated and loaded into Neo4j. Empty numbers are null.
 */
public record CuratedKnowledge(List<SourceRow> sources, List<Crop> crops, List<NeedRow> needs,
                               List<WindowRow> windows, List<PairRow> pairs, List<OrganismRow> organisms,
                               List<HostRow> hosts) {

    public record SourceRow(String id, String title, String url, String license, boolean commercial,
                            String retrievedOn) {
    }

    /** tempKillEarly: the temperature that kills young plants (ECOCROP), used by the frost rule. */
    public record NeedRow(String cropId, String ecocropId, Double tempOptMin, Double tempOptMax,
                          Double tempAbsMin, Double tempAbsMax, Double rainOptMin, Double rainOptMax,
                          Double phOptMin, Double phOptMax, Double phAbsMin, Double phAbsMax,
                          Double cycleMinDays, Double cycleMaxDays, String soilTexture, String soilDrainage,
                          String light, Double tempKillEarly, String source) {
    }

    /** activity: SOW, SOW_SEEDBED, TRANSPLANT, PLANT or HARVEST; months 1-12, wrapping when from > to. */
    public record WindowRow(String cropId, String zone, String activity, int fromMonth, int toMonth, String note,
                            String source, boolean verified) {
    }

    /** relation: COMPANION or ANTAGONIST; evidence: study, extension or traditional. */
    public record PairRow(String cropId, String partnerId, String relation, String effect, String evidence,
                          List<String> sources) {
    }

    /** A pest or disease, identified by its EPPO code; kind: INSECT, MITE, MOLLUSC or DISEASE. */
    public record OrganismRow(String id, String italianName, String englishName, String scientificName,
                              String kind, String source) {
    }

    public record HostRow(String organismId, String cropId, int fromMonth, int toMonth, String note, String source,
                          boolean verified) {
    }
}
