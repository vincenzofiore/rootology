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

    private final CuratedKnowledge knowledge = CuratedKnowledgeReader.read(new CropCatalog());

    private final List<Organism> organisms = knowledge.organisms().stream()
            .map(o -> new Organism(o.id(), o.italianName(), o.englishName())).toList();

    private final java.util.Set<String> methods = knowledge.controls().stream()
            .map(CuratedKnowledge.ControlRow::method).collect(java.util.stream.Collectors.toUnmodifiableSet());

    public List<Organism> all() {
        return organisms;
    }

    /** Whether a value is a control method (#140): a sentence in English, which the answer's text says instead. */
    public boolean isControlMethod(String value) {
        return methods.contains(value);
    }
}
