package app.rootology.enrichment;

import java.util.List;

/** A proposal that breaks the knowledge base's rules, with every problem found. */
public class InvalidProposalException extends RuntimeException {

    private final List<String> problems;

    public InvalidProposalException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
