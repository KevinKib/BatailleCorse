package org.kevinkib.cardgames.mangola.domain;

import java.util.List;

/**
 * The outcome of sowing one pit. When the sowing never ends ({@code endless}), the board is the
 * unchanged input and the trace is empty: the move cannot be played to its end.
 *
 * @param drops the seeds dropped before the sowing ended or was found endless
 */
public record SowingResult(MangolaBoard board, List<SowingStep> trace, boolean endless, int drops) {

    public SowingResult {
        trace = List.copyOf(trace);
    }

    /** The seeds taken from the opponent in this move. */
    public int captured() {
        return trace.stream().filter(step -> step instanceof SowingStep.Capture)
                .mapToInt(step -> ((SowingStep.Capture) step).count()).sum();
    }
}
