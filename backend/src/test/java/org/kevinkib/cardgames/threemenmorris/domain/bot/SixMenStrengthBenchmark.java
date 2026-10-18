package org.kevinkib.cardgames.threemenmorris.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisPosition;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisRules;

import java.util.List;
import java.util.Random;

/**
 * Not a test: a measurement, run on demand with {@code -Dmorris.bench=true -Dtest=SixMenStrengthBenchmark}.
 * It prints the results of the Expert (search) bot against the greedy bot and of the greedy bot against the
 * random bot over 100 games each, colours alternating, and the time per Expert decision. A game is drawn
 * after 50 movement plies without a removal (the rule of the aggregate), or after 400 plies in all.
 */
@EnabledIfSystemProperty(named = "morris.bench", matches = "true")
class SixMenStrengthBenchmark {

    private static final int GAMES = 100;
    private static final int QUIET_LIMIT = MorrisRules.SIX_MEN.quietPlyLimit();
    private static final int PLY_LIMIT = 400;

    private record Score(int firstWins, int secondWins, int draws) {
        @Override
        public String toString() {
            return "first=" + firstWins + " second=" + secondWins + " draws=" + draws;
        }
    }

    private long expertNanos;
    private long expertMaxNanos;
    private int expertDecisions;

    @Test
    void strengthAndTime() {
        Random random = new Random(11);
        System.out.println("BENCH search vs greedy: " + match(new SearchBot(random), new GreedyBot(random), random));
        System.out.println("BENCH greedy vs random: " + match(new GreedyBot(random), new RandomBot(random), random));
        System.out.println("BENCH search vs random: " + match(new SearchBot(random), new RandomBot(random), random));
        System.out.println("BENCH expert decision: mean " + expertNanos / Math.max(1, expertDecisions) / 1_000
                + " us, max " + expertMaxNanos / 1_000 + " us over " + expertDecisions + " decisions");
    }

    /** The first bot plays White in half of the games and Black in the other half. */
    private Score match(MorrisBot first, MorrisBot second, Random random) {
        int firstWins = 0;
        int secondWins = 0;
        int draws = 0;
        for (int game = 0; game < GAMES; game++) {
            boolean firstIsWhite = game % 2 == 0;
            int winner = play(firstIsWhite ? first : second, firstIsWhite ? second : first, first);
            if (winner < 0) {
                draws++;
            } else if ((winner == 0) == firstIsWhite) {
                firstWins++;
            } else {
                secondWins++;
            }
        }
        return new Score(firstWins, secondWins, draws);
    }

    /** The winning seat, or -1 for a draw. */
    private int play(MorrisBot white, MorrisBot black, MorrisBot timed) {
        MorrisPosition position = new MorrisPosition(MorrisRules.SIX_MEN,
                java.util.Collections.nCopies(MorrisRules.SIX_MEN.geometry().points(), null), List.of(6, 6), 0);
        int quiet = 0;
        for (int ply = 0; ply < PLY_LIMIT; ply++) {
            MorrisBot bot = position.toMove() == 0 ? white : black;
            long start = System.nanoTime();
            MorrisDecision decision = bot.decide(position, new PlayerId(position.toMove()), false);
            if (bot == timed && bot instanceof SearchBot) {
                long elapsed = System.nanoTime() - start;
                expertNanos += elapsed;
                expertMaxNanos = Math.max(expertMaxNanos, elapsed);
                expertDecisions++;
            }
            MorrisTransitions.Outcome outcome = MorrisTransitions.apply(position, decision);
            if (outcome instanceof MorrisTransitions.Outcome.Win win) {
                return win.seat();
            }
            boolean movement = !MorrisTransitions.isPlacement(position);
            quiet = decision instanceof MorrisDecision.Remove || !movement ? 0 : quiet + 1;
            if (quiet >= QUIET_LIMIT) {
                return -1;
            }
            position = ((MorrisTransitions.Outcome.Next) outcome).position();
        }
        return -1;
    }
}
