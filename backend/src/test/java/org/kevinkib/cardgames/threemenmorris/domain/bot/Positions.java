package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Test helpers for the bot tests: positions written as strings and a generator that always answers 0. */
final class Positions {

    /** Always the first candidate: a bot driven by it is fully deterministic. */
    static final RandomGenerator FIRST = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    };

    private Positions() {
    }

    /** Nine characters, row by row: W is seat 0, B is seat 1, anything else is free. */
    static MorrisPosition of(MorrisRules rules, String cells, int whiteInHand, int blackInHand, int toMove) {
        List<Integer> board = new ArrayList<>();
        for (char c : cells.toCharArray()) {
            board.add(c == 'W' ? Integer.valueOf(0) : c == 'B' ? Integer.valueOf(1) : null);
        }
        return new MorrisPosition(rules, board, List.of(whiteInHand, blackInHand), toMove);
    }

    static MorrisPosition tapatan(String cells, int whiteInHand, int blackInHand, int toMove) {
        return of(MorrisRules.TAPATAN, cells, whiteInHand, blackInHand, toMove);
    }
}
