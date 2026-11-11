package app.rootology.ui;

import com.vaadin.flow.component.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Why a question waits for a person, in the page's language (#31). The decision layers word their reasons in
 * English for the suite and the logs; the known ones are translated here, anything else (for example a
 * reviewer's note) is shown as it is.
 */
final class ReasonText {

    private record Known(Pattern pattern, String key) {
    }

    private static final List<Known> KNOWN = List.of(
            new Known(Pattern.compile("(.+) is not in the dataset"), "reason.notindataset"),
            new Known(Pattern.compile("Jev: low confidence \\((.+)\\)"), "reason.lowconfidence"),
            new Known(Pattern.compile("Jev: the facts do not answer this \\(p=(.+)\\)"), "reason.noanswer"),
            new Known(Pattern.compile("Jev: confident \\((.+)\\)"), "reason.confident"),
            new Known(Pattern.compile("the question does not fit any supported shape"), "reason.othershape"),
            new Known(Pattern.compile("the decision layer did not answer"), "reason.jevdown"),
            new Known(Pattern.compile("the decision layer is not available right now"), "reason.jevdown"),
            new Known(Pattern.compile("nothing is recorded as growing in this space's gardens"), "reason.nothinggrowing"),
            new Known(Pattern.compile("nothing is declared to sow or plant in (.+)"), "reason.nothingtosow"),
            new Known(Pattern.compile("active season of (.+) is not declared"), "reason.noseason"),
            new Known(Pattern.compile("what (.+) repels is not declared"), "reason.norepels"),
            new Known(Pattern.compile("the question needs a garden"), "reason.needsgarden"),
            new Known(Pattern.compile("the garden no longer exists"), "reason.gardengone"),
            new Known(Pattern.compile("the plot is not in this garden"), "reason.plot"),
            new Known(Pattern.compile("the garden named is not clear"), "reason.gardenname"));

    static String text(String reason, Component in) {
        return text(reason, in::getTranslation);
    }

    static String text(String reason, NoteText.Texts in) {
        if (reason == null) {
            return "";
        }
        for (Known known : KNOWN) {
            Matcher m = known.pattern().matcher(reason);
            if (m.matches()) {
                Object[] args = new Object[m.groupCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = m.group(i + 1);
                }
                return in.get(known.key(), args);
            }
        }
        return reason;
    }

    private ReasonText() {
    }
}
