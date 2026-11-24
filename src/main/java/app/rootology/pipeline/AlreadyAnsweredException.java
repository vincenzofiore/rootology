package app.rootology.pipeline;

/** Another person is answering, or has just answered, the same waiting question (#152). */
public class AlreadyAnsweredException extends RuntimeException {

    public AlreadyAnsweredException(String processId) {
        super("Process " + processId + " is already being answered");
    }
}
