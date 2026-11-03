package io.github.vincenzofiore.rootology.facts;

import io.github.vincenzofiore.rootology.facts.CuratedKnowledge.SourceRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** The knowledge base's sources (`knowledge/sources.csv`), for attribution and for showing them in answers. */
@Component
public class SourceCatalog {

    private final List<SourceRow> sources = CuratedKnowledgeReader.read(new CropCatalog()).sources();

    public List<SourceRow> all() {
        return sources;
    }

    public Optional<SourceRow> byId(String id) {
        return sources.stream().filter(s -> s.id().equals(id)).findFirst();
    }
}
