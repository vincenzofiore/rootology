package app.rootology.ui;

import app.rootology.enrichment.KnowledgeEnrichment;
import app.rootology.facts.ClimateCells;
import app.rootology.facts.CropCatalog;
import app.rootology.facts.KnowledgeGraph;
import app.rootology.facts.SourceCatalog;
import app.rootology.garden.GardenService;
import app.rootology.geocoding.GeoNames;
import app.rootology.pipeline.GardenPipeline;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * Makes the question dialog of the garden home (#178) with everything Ask uses to answer, so the home needs one
 * dependency instead of ten.
 */
@Component
// One per view: CurrentUser belongs to the Vaadin session, so this cannot be a singleton.
@Scope("prototype")
public class QuestionDialogs {

    final GardenPipeline pipeline;
    final SourceCatalog sources;
    final KnowledgeEnrichment enrichment;
    final CurrentUser currentUser;
    final CropCatalog crops;
    final KnowledgeGraph knowledge;
    final GardenService gardens;
    final GeoNames geoNames;
    final ClimateCells cells;
    /** Where a question runs while the page stays usable (#82): the app's own, never the common pool. */
    final Executor executor;

    QuestionDialogs(GardenPipeline pipeline, SourceCatalog sources, KnowledgeEnrichment enrichment,
                    CurrentUser currentUser, CropCatalog crops, KnowledgeGraph knowledge, GardenService gardens,
                    GeoNames geoNames, ClimateCells cells, @Qualifier("applicationTaskExecutor") Executor executor) {
        this.pipeline = pipeline;
        this.sources = sources;
        this.enrichment = enrichment;
        this.currentUser = currentUser;
        this.crops = crops;
        this.knowledge = knowledge;
        this.gardens = gardens;
        this.geoNames = geoNames;
        this.cells = cells;
        this.executor = executor;
    }

    /** A dialog that has started answering the question in this garden; the caller opens it. */
    QuestionDialog ask(long spaceId, long gardenId, String gardenName, String asked) {
        QuestionDialog dialog = new QuestionDialog(this, spaceId, gardenId, gardenName);
        dialog.ask(asked, false);
        return dialog;
    }
}
