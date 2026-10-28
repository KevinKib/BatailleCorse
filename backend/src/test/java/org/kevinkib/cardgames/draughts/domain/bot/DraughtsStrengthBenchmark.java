package org.kevinkib.cardgames.draughts.domain.bot;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.draughts.domain.Colour;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsPosition;
import org.kevinkib.cardgames.draughts.domain.MoveGenerator;

/**
 * Not a test: a measurement, run on demand with
 * {@code -Ddraughts.bench=true -Dtest=DraughtsStrengthBenchmark} (the games are played with the real expert budget,
 * so it takes several minutes). It prints Expert against Medium and Medium against Easy: 100 games each, colours
 * alternating, a draw after 200 plies, plus the mean and worst decision time of each bot.
 */
@EnabledIfSystemProperty(named = "draughts.bench", matches = "true")
class DraughtsStrengthBenchmark {

    private static final int GAMES = Integer.getInteger("draughts.bench.games", 100);
    private static final int MAX_PLIES = 200;

    private final MoveGenerator generator = new MoveGenerator();

    @Test
    void expertAgainstMediumAndMediumAgainstEasy() {
        ExpertDraughtsBot.Budget budget = ExpertDraughtsBot.Budget.DEFAULT;
        play("Expert vs Medium", seed -> new ExpertDraughtsBot(new Random(seed), budget),
                seed -> new GreedyDraughtsBot(new Random(seed + 1)));
        play("Medium vs Easy", seed -> new GreedyDraughtsBot(new Random(seed)),
                seed -> new RandomDraughtsBot(new Random(seed + 1)));
    }

    private void play(String name, java.util.function.LongFunction<DraughtsBot> strong,
            java.util.function.LongFunction<DraughtsBot> weak) {
        int wins = 0;
        int losses = 0;
        int draws = 0;
        Timing strongTime = new Timing();
        Timing weakTime = new Timing();
        for (int game = 0; game < GAMES; game++) {
            Colour strongColour = game % 2 == 0 ? Colour.WHITE : Colour.BLACK;
            DraughtsBot strongBot = strong.apply(game);
            DraughtsBot weakBot = weak.apply(game);
            Colour winner = playOne(strongBot, weakBot, strongColour, strongTime, weakTime);
            if (winner == null) {
                draws++;
            } else if (winner == strongColour) {
                wins++;
            } else {
                losses++;
            }
        }
        System.out.println("BENCH " + name + ": " + GAMES + " games, strong wins=" + wins + " losses=" + losses
                + " draws=" + draws + " | strong decisions mean=" + strongTime.mean() + " ms worst="
                + strongTime.worst() + " ms | weak decisions mean=" + weakTime.mean() + " ms worst="
                + weakTime.worst() + " ms");
    }

    /** The winner, or null for a draw. */
    private Colour playOne(DraughtsBot strong, DraughtsBot weak, Colour strongColour, Timing strongTime,
            Timing weakTime) {
        DraughtsPosition position = DraughtsPosition.initial();
        for (int ply = 0; ply < MAX_PLIES; ply++) {
            if (generator.legalMoves(position).isEmpty()) {
                return position.toMove().opposite();
            }
            boolean strongToMove = position.toMove() == strongColour;
            long start = System.nanoTime();
            DraughtsMove move = ((DraughtsDecision.Play) (strongToMove ? strong : weak)
                    .decide(position, position.toMove(), false)).move();
            (strongToMove ? strongTime : weakTime).add((System.nanoTime() - start) / 1_000_000.0);
            position = generator.apply(position, move);
        }
        return null;
    }

    private static final class Timing {
        private double total;
        private double worst;
        private long count;

        void add(double millis) {
            total += millis;
            worst = Math.max(worst, millis);
            count++;
        }

        String mean() {
            return String.format("%.1f", count == 0 ? 0 : total / count);
        }

        String worst() {
            return String.format("%.0f", worst);
        }
    }
}
