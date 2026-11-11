package app.rootology.decision;

/**
 * Stand-in domain filter for when no TypeSafe API key is configured (#53): nothing is judged and no question
 * is ever refused, so the normal path stays exactly as it was.
 */
public class PassThroughDomainFilter implements DomainFilter {

    @Override
    public Verdict judge(String question) {
        return Verdict.allowing(1.0);
    }
}
