package org.kevinkib.cardgames.mangola.domain.bot;

import java.util.function.IntPredicate;

/** Test scope: the loop of the approximate double oracle, stopping at the limit or when a round adds nothing. */
final class OracleRounds {

    private OracleRounds() {
    }

    /**
     * Plays rounds {@code first} to {@code last}; a round returns whether it added a layout to a mix, and the loop
     * stops after the first that did not.
     *
     * @return the number of rounds played
     */
    static int run(int first, int last, IntPredicate roundAddedSomething) {
        int played = 0;
        for (int round = first; round <= last; round++) {
            played++;
            if (!roundAddedSomething.test(round)) {
                break;
            }
        }
        return played;
    }
}
