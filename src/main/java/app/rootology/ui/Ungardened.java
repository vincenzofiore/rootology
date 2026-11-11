package app.rootology.ui;

import app.rootology.history.RunHistory;
import app.rootology.pipeline.GardenPipeline;
import org.springframework.stereotype.Component;

/**
 * Whether a space has questions without a garden (#56): the ones asked outside a garden, the ones stored
 * before #56 in a space with several gardens, and those of a deleted garden. The overview and its menu
 * offer them only then, so nobody hunts through empty pages.
 */
@Component
public class Ungardened {

    private final RunHistory history;
    private final GardenPipeline pipeline;

    Ungardened(RunHistory history, GardenPipeline pipeline) {
        this.history = history;
        this.pipeline = pipeline;
    }

    public boolean any(long spaceId, String login) {
        return pipeline.hasUngardened(spaceId, login) || history.hasUngardened(spaceId, login);
    }
}
