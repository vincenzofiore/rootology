package app.rootology.ui;

/**
 * The awareness level of an official MeteoAlarm warning (#136), as the garden home colours it: yellow, orange
 * and red as MeteoAlarm paints them, the neutral colour for green (level 1, no danger) and for a level the CAP
 * document does not state, which is never guessed. The level's name is the icon's tooltip and aria label.
 */
enum OfficialWarningLevel {

    /** Level 1: no danger, shown in the neutral colour. */
    GREEN("rootology-official-neutral", "icons/alerts/warning.svg", "warning.level.green"),
    /** Level 2: yellow. */
    YELLOW("rootology-official-yellow", "icons/alerts/warning.svg", "warning.level.yellow"),
    /** Level 3: orange. */
    ORANGE("rootology-official-orange", "icons/alerts/warning.svg", "warning.level.orange"),
    /** Level 4: red. */
    RED("rootology-official-red", "icons/alerts/warning.svg", "warning.level.red"),
    /** A warning without a level (0): shown, in the neutral colour, never taken for a level it does not have. */
    OTHER("rootology-official-neutral", "icons/alerts/warning.svg", "warning.level.other");

    private final String className;
    private final String icon;
    private final String label;

    OfficialWarningLevel(String className, String icon, String label) {
        this.className = className;
        this.icon = icon;
        this.label = label;
    }

    /** The class that colours the row, from the stylesheet. */
    String className() {
        return className;
    }

    /** The warning triangle with its exclamation mark, in the level's colour (#169). */
    String icon() {
        return icon;
    }

    /** The key of the level's name, the icon's tooltip and aria label. */
    String label() {
        return label;
    }

    /** The level of a warning, 1 to 4; 0 or anything else is {@link #OTHER}. */
    static OfficialWarningLevel of(int level) {
        return switch (level) {
            case 1 -> GREEN;
            case 2 -> YELLOW;
            case 3 -> ORANGE;
            case 4 -> RED;
            default -> OTHER;
        };
    }
}
