package app.rootology.ui;

import app.rootology.pipeline.GardenPipeline;
import app.rootology.resilience.ServiceUnavailableException;
import app.rootology.usage.UsageLimitException;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Runs a question in the background and brings its outcome back to the page (#82): shared by Ask and by the
 * garden home's question dialog (#178), so both tell the person the same thing when a question cannot be
 * answered.
 */
final class QuestionRunner {

    /** What the page does with the outcome, always on the UI thread. */
    interface Outcome {
        void finished(GardenPipeline.Run run);

        /** A reason the person is told: the daily limit, or the model not available. */
        void failed(String text);

        /** Any other failure, including a refused access (#166). */
        void failedWith(RuntimeException ex);
    }

    private QuestionRunner() {
    }

    /**
     * Runs the work on the executor; settled runs on the background thread as soon as it ends, before the page
     * is updated, so a second question can start. Without a UI (the tests) the outcome runs on the same thread.
     */
    static void start(Executor executor, UI ui, Component page, Supplier<GardenPipeline.Run> work,
                      Runnable settled, Outcome outcome) {
        executor.execute(() -> {
            try {
                GardenPipeline.Run run = work.get();
                settled.run();
                update(ui, page, () -> outcome.finished(run));
            } catch (UsageLimitException ex) {
                settled.run();
                int limit = ex.limit();
                update(ui, page, () -> outcome.failed(page.getTranslation("ask.limit", limit)));
            } catch (ServiceUnavailableException ex) {
                settled.run();
                update(ui, page, () -> outcome.failed(page.getTranslation("error.claude")));
            } catch (RuntimeException ex) {
                settled.run();
                update(ui, page, () -> outcome.failedWith(ex));
            }
        });
    }

    /**
     * A change to the page from the background thread (#82). A page that is gone, or a dialog closed meanwhile,
     * drops it quietly: the answer is in the history anyway.
     */
    private static void update(UI ui, Component page, Runnable change) {
        if (ui == null) {
            change.run();
            return;
        }
        if (ui.isClosing()) {
            return;
        }
        try {
            ui.access(() -> {
                if (page.isAttached()) {
                    change.run();
                }
            });
        } catch (com.vaadin.flow.component.UIDetachedException | IllegalStateException ignored) {
            // The visit ended while the question ran: nothing to show.
        }
    }
}
