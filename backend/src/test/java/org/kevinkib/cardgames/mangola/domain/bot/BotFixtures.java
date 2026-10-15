package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaPosition;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/** Test helpers for the bots: boards written with pit names, positions, and generators. */
final class BotFixtures {

    /** Every draw is 0: a fully deterministic generator. */
    static final RandomGenerator CONSTANT = new RandomGenerator() {
        @Override
        public long nextLong() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            return 0;
        }
    };

    private BotFixtures() {
    }

    /** A board from "pit:seeds" tokens such as {@code "a2:2 c2:1 c3:2"}; pits not listed are empty. */
    static MangolaBoard board(String spec) {
        MangolaBoard board = MangolaBoard.empty();
        for (String token : spec.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                String[] parts = token.split(":");
                board = board.with(MangolaGeometry.pit(parts[0]), Integer.parseInt(parts[1]));
            }
        }
        return board;
    }

    static MangolaPosition position(MangolaRules rules, MangolaBoard board, int toMove) {
        return new MangolaPosition(rules, board.toList(), toMove);
    }

    static MangolaPosition position(MangolaBoard board, Side toMove) {
        return position(MangolaRules.OWNER, board, toMove == Side.A ? 0 : 1);
    }

    static int sum(int[] counts) {
        return Arrays.stream(counts).sum();
    }

    static List<Integer> list(int[] counts) {
        return Arrays.stream(counts).boxed().toList();
    }

    static boolean isLegalLayout(int[] counts) {
        return counts.length == MangolaGeometry.LOOP_LENGTH && sum(counts) == 32
                && Arrays.stream(counts).allMatch(c -> c >= 0);
    }

    static boolean onlyEndless(MangolaRules rules, Side side, int[] counts) {
        return Mangola.onlyEndlessMoves(rules, side, list(counts));
    }
}
