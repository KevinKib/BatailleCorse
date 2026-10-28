package org.kevinkib.cardgames.blackjack.domain.bot;

import java.util.random.RandomGenerator;

/** The strategy of each level. */
public final class BlackjackBots {

    private BlackjackBots() {
    }

    /** @param random the random source of the {@code easy} level (seeded in tests) */
    public static BlackjackBotStrategy forLevel(BlackjackBotLevel level, RandomGenerator random) {
        return switch (level) {
            case EASY -> new RandomBlackjackBot(random);
            case MEDIUM -> new BasicStrategyBlackjackBot();
            case HARD -> new TournamentBlackjackBot();
        };
    }
}
