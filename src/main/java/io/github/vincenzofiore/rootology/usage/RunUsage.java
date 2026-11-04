package io.github.vincenzofiore.rootology.usage;

/** What one run consumed: Claude calls and tokens, the estimated cost of those calls, Jev calls. */
public record RunUsage(int claudeCalls, int promptTokens, int completionTokens, double costUsd, int jevCalls) {
}
