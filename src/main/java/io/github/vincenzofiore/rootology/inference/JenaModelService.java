package io.github.vincenzofiore.rootology.inference;


import io.github.vincenzofiore.rootology.ontology.OntologyDefinition;
import io.github.vincenzofiore.rootology.ontology.Pest;
import io.github.vincenzofiore.rootology.ontology.Plant;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.springframework.stereotype.Service;

@Service
public class JenaModelService {

    private final OntologyDefinition ontologyDefinition;
    private Model model;

    public JenaModelService(OntologyDefinition ontologyDefinition) {
        this.ontologyDefinition = ontologyDefinition;
    }

    public synchronized Model getModel() {
        if (model == null) {
            model = buildModel();
        }
        return model;
    }

    /** Rebuild the in-memory graph from scratch, reading current data from OntologyDefinition. */
    public synchronized void refresh() {
        model = buildModel();
    }

    private Model buildModel() {
        Model m = ModelFactory.createDefaultModel();

        Property companionOf = m.createProperty(Vocabulary.COMPANION_OF);
        Property repels = m.createProperty(Vocabulary.REPELS);
        Property growsInSeason = m.createProperty(Vocabulary.GROWS_IN_SEASON);
        Property compatibleWithSoil = m.createProperty(Vocabulary.COMPATIBLE_WITH_SOIL);
        Property activeInSeason = m.createProperty(Vocabulary.ACTIVE_IN_SEASON);

        for (Plant plant : ontologyDefinition.plants()) {
            Resource plantRes = m.createResource(Vocabulary.plant(plant.name()));

            plant.growingSeasons().forEach(season ->
                    plantRes.addProperty(growsInSeason, m.createResource(Vocabulary.season(season.name()))));

            plant.compatibleSoils().forEach(soil ->
                    plantRes.addProperty(compatibleWithSoil, m.createResource(Vocabulary.soil(soil.name()))));

            plant.companionPlants().forEach(companionName ->
                    plantRes.addProperty(companionOf, m.createResource(Vocabulary.plant(companionName))));

            plant.repelledPests().forEach(pestName ->
                    plantRes.addProperty(repels, m.createResource(Vocabulary.pest(pestName))));
        }

        for (Pest pest : ontologyDefinition.pests()) {
            Resource pestRes = m.createResource(Vocabulary.pest(pest.name()));
            pest.activeSeasons().forEach(season ->
                    pestRes.addProperty(activeInSeason, m.createResource(Vocabulary.season(season.name()))));
        }

        return m;
    }
}
