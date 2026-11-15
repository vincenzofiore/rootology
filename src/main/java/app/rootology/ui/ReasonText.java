package app.rootology.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.shared.Tooltip;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Why a question waits for a person, in the page's language (#31). The decision layers word their reasons in
 * English for the suite and the logs; the known ones are translated here, anything else (for example a
 * reviewer's note) is shown as it is. A decision layer's number is not part of the words (#114): "i fatti trovati
 * non bastano a rispondere" says what a gardener needs, and "probabilità 0,08" stays as the words' tooltip.
 */
final class ReasonText {

    private record Known(Pattern pattern, String key, boolean numbered) {

        Known(Pattern pattern, String key) {
            this(pattern, key, false);
        }
    }

    private static final List<Known> KNOWN = List.of(
            new Known(Pattern.compile("(.+) is not in the dataset"), "reason.notindataset"),
            new Known(Pattern.compile("Jev: low confidence \\((.+)\\)"), "reason.lowconfidence", true),
            new Known(Pattern.compile("Jev: the facts do not answer this \\(p=(.+)\\)"), "reason.noanswer", true),
            new Known(Pattern.compile("Jev: confident \\((.+)\\)"), "reason.confident", true),
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
        return worded(reason, in).words();
    }

    /** A reason's words, and its number when a decision layer gave one (#114), otherwise {@code null}. */
    record Worded(String words, String number) {
    }

    static Worded worded(String reason, NoteText.Texts in) {
        if (reason == null) {
            return new Worded("", null);
        }
        for (Known known : KNOWN) {
            Matcher m = known.pattern().matcher(reason);
            if (m.matches()) {
                Object[] args = new Object[m.groupCount()];
                for (int i = 0; i < args.length; i++) {
                    args[i] = m.group(i + 1);
                }
                return new Worded(in.get(known.key(), args),
                        known.numbered() ? in.get(known.key() + ".number", args) : null);
            }
        }
        return new Worded(reason, null);
    }

    /** The reason's number as the tooltip of the component that shows its words, when there is one (#114). */
    static <T extends Component> T withNumber(T shown, String reason, Component in) {
        String number = worded(reason, in::getTranslation).number();
        if (number != null) {
            Tooltip.forComponent(shown).setText(number);
        }
        return shown;
    }

    private ReasonText() {
    }
}
