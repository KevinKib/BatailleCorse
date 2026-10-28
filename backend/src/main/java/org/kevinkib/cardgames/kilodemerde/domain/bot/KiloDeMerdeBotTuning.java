package org.kevinkib.cardgames.kilodemerde.domain.bot;

/**
 * Every delay range of the Kilo de merde bots (spec section 7), in one place so tests can pin them. The presentation
 * chunk binds these to {@code kilodemerde.bots.*} properties; {@link #defaults()} are the live values and
 * {@link #instant()} the values of the {@code test} profile.
 *
 * @param pass    the pass delay, the same for every level
 * @param easy    the slap delays of the {@code easy} level
 * @param medium  the slap delays of the {@code medium} level
 * @param hard    the slap delays of the {@code hard} level
 */
public record KiloDeMerdeBotTuning(Range pass, Slap easy, Slap medium, Slap hard) {

    /** A delay range in milliseconds, both ends included. */
    public record Range(long minMs, long maxMs) {
        public Range {
            if (minMs < 0 || maxMs < minMs) {
                throw new IllegalArgumentException("Invalid delay range " + minMs + ".." + maxMs);
            }
        }

        public static Range fixed(long ms) {
            return new Range(ms, ms);
        }
    }

    /**
     * The slap reaction of one level.
     *
     * @param ownSet the delay before slapping a four of a kind in the bot's own hand
     * @param follow the delay before following a race another seat opened
     */
    public record Slap(Range ownSet, Range follow) {
    }

    public static KiloDeMerdeBotTuning defaults() {
        return new KiloDeMerdeBotTuning(
                new Range(500, 1000),
                new Slap(new Range(1500, 2500), new Range(1200, 2000)),
                new Slap(new Range(700, 1100), new Range(700, 1100)),
                new Slap(new Range(350, 600), new Range(450, 750)));
    }

    /** No waiting at all: the {@code test} profile. */
    public static KiloDeMerdeBotTuning instant() {
        Slap none = new Slap(Range.fixed(0), Range.fixed(0));
        return new KiloDeMerdeBotTuning(Range.fixed(0), none, none, none);
    }

    public Slap forLevel(KiloDeMerdeBotLevel level) {
        return switch (level) {
            case EASY -> easy;
            case MEDIUM -> medium;
            case HARD -> hard;
        };
    }
}
