package app.rootology.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;

/**
 * The question while Claude and Jev work (#82): the words asked, a bar that moves, and the seconds so far. The
 * count runs in the browser, so it keeps moving between two server updates; it stops itself once the card is
 * removed, when the answer arrives. Nothing here tells what the pipeline is doing: the steps are not reported.
 */
class ThinkingCard extends Div {

    private final Span elapsed = new Span();
    private final long startedAt = System.currentTimeMillis();
    private final String secondsPattern;

    ThinkingCard(String question, String secondsPattern) {
        this.secondsPattern = secondsPattern;
        addClassName("rootology-thinking");
        H4 asked = new H4(question);
        asked.addClassName("rootology-thinking-question");
        ProgressBar bar = new ProgressBar();
        bar.setIndeterminate(true);
        bar.addClassName("rootology-thinking-bar");
        elapsed.addClassName("rootology-thinking-elapsed");
        elapsed.setText(secondsPattern.replace("{0}", "0"));
        add(asked, bar, elapsed);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        startCounting();
    }

    /** A client-side timer: the server is busy with the question and pushes only when the answer is ready. */
    private void startCounting() {
        elapsed.getElement().executeJs(
                "const el = this;"
                        + "clearInterval(el.__rootologyTimer);"
                        + "const started = $0;"
                        + "const pattern = $1;"
                        + "const paint = () => { el.textContent = pattern.replace('{0}',"
                        + " String(Math.round((Date.now() - started) / 1000))); };"
                        + "paint();"
                        + "el.__rootologyTimer = setInterval(() => {"
                        + "  if (!el.isConnected) { clearInterval(el.__rootologyTimer); return; }"
                        + "  paint();"
                        + "}, 500);",
                startedAt, secondsPattern);
    }
}
