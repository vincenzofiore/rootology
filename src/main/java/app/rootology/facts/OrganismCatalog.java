package app.rootology.facts;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The knowledge base's pests and diseases (`knowledge/pests.csv`), with their names in both languages, so a
 * name can be shown in the language the page reads (#110) without reading the graph.
 */
@Component
public class OrganismCatalog {

    /** A pest or disease: its EPPO code, its Italian and English names. */
    public record Organism(String id, String italianName, String englishName) {
    }

    private final List<Organism> organisms = CuratedKnowledgeReader.read(new CropCatalog()).organisms().stream()
            .map(o -> new Organism(o.id(), o.italianName(), o.englishName())).toList();

    public List<Organism> all() {
        return organisms;
    }
}
