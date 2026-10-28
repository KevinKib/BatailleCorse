package org.kevinkib.cardgames.ninetynine.domain.bot;

/** Every constant of the 99 bots in one place so tests can pin them. */
public final class NinetyNineBotTuning {

    /** Sampled deals of the unseen cards per decision of the {@code hard} level. */
    public static final int SAMPLES = 200;

    /** The time a {@code hard} decision may take: sampling stops at the cap (20 ms). */
    public static final long CAP_NANOS = 20_000_000L;

    /** Small cost of spending an escape card (K, 10, 4, 9) when the sampled scores are otherwise equal. */
    static final double ESCAPE_CARD_COST = 0.03;

    private NinetyNineBotTuning() {
    }
}
