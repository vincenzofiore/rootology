package io.github.vincenzofiore.rootology.experiments;

/** The five experiment cases, with the question text as asked to every contestant. */
public enum ExperimentCase {

    EASY_1("easy-1", "Which plants are companions of Tomato?"),
    EASY_2("easy-2", "Which pests are relevant for Basil in summer?"),
    HARD_1("hard-1", "Find companions of plants that repel the same pest repelled by Tomato"),
    HARD_2("hard-2", "In which season is Mosquito active?"),
    HARD_3("hard-3", "Which plants are companions of Lavender?");

    private final String id;
    private final String question;

    ExperimentCase(String id, String question) {
        this.id = id;
        this.question = question;
    }

    public String id() {
        return id;
    }

    public String question() {
        return question;
    }
}
