package org.kevinkib.cardgames.mangola.domain;

/**
 * Refused confirmation: once both layouts are revealed, the seat that starts would have no legal move (every
 * pit it could sow is an endless sowing). Owner decision of 2026-10-15. The message is the stable locale key,
 * the same for both seats and free of any layout, so the refusal says nothing more about the other half.
 */
public class StuckStartException extends MangolaException {

    /** Locale key the frontend maps to its text. */
    public static final String LOCALE_KEY = "mangola.errors.stuckStart";

    public StuckStartException() {
        super(LOCALE_KEY);
    }
}
