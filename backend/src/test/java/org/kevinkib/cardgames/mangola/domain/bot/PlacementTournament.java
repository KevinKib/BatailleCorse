package org.kevinkib.cardgames.mangola.domain.bot;

import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaException;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.random.RandomGenerator;

/**
 * Placement study only (test scope): games between fixed layouts, played by a move search on both sides through
 * the real aggregate ({@link Mangola}: the threefold repetition draw, the endless rule), with a ply cap counted as
 * a draw. Every game has its own seed derived from the base seed and the game's coordinates, so the results do
 * not depend on the number of threads or on the order in which games finish.
 */
final class PlacementTournament {

    private static final PlayerId SEAT_A = new PlayerId(0);

    /** A named layout: 16 counts in loop order (the same counts mean the same thing on either side). */
    record Placement(String name, int[] layout) {
    }

    /** The move player of a game, built for each game from that game's seed. */
    interface MoveSearch {
        MangolaBot bot(long seed);
    }

    /** How a seat arranges its seeds, knowing its side (side A starts), with the given generator. */
    interface LayoutPolicy {
        int[] layout(Side side, RandomGenerator random);
    }

    /** {@code repetitions} games per ordered pair (who starts matters), {@code maxPlies} before a game is a draw. */
    record Config(MangolaRules rules, int repetitions, long baseSeed, int threads, int maxPlies) {
    }

    /** The winning side (null for a draw) and the plies played; {@code capped} when the ply cap ended it. */
    record Outcome(Side winner, int plies, boolean capped) {
    }

    /**
     * The games of one ordered pair: the first layout on side A (it starts), the second on side B. {@code stuck}
     * counts the starts the game would refuse (side A without a legal move), which are not played.
     */
    record PairResult(int winsA, int draws, int winsB, int stuck, int capped, long plies) {
        int played() {
            return winsA + draws + winsB;
        }

        /** The starter's mean score (win 1, draw 0.5), 0.5 when nothing could be played. */
        double scoreA() {
            return played() == 0 ? 0.5 : (winsA + 0.5 * draws) / played();
        }
    }

    /** A duel of two layout policies, counted for the first one. */
    record Duel(int wins, int draws, int losses, int winsWhenStarting, int redrawn) {
        int games() {
            return wins + draws + losses;
        }

        double score() {
            return (wins + 0.5 * draws) / games();
        }
    }

    /** All ordered pairs of a round robin. */
    static final class Results {
        private final PairResult[][] pairs;

        Results(PairResult[][] pairs) {
            this.pairs = pairs;
        }

        int size() {
            return pairs.length;
        }

        PairResult pair(int a, int b) {
            return pairs[a][b];
        }

        /** {@code [i][j]}: the starter's score with layout {@code i} on side A against layout {@code j} on side B. */
        double[][] starter() {
            double[][] matrix = new double[size()][size()];
            for (int i = 0; i < size(); i++) {
                for (int j = 0; j < size(); j++) {
                    matrix[i][j] = pairs[i][j].scoreA();
                }
            }
            return matrix;
        }

        /**
         * {@code [i][j]}: the score of layout {@code i} against layout {@code j}, the mean of the game where it
         * starts and the game where it does not, so the first-move advantage cancels out: zero-sum (the entries of
         * {@code [i][j]} and {@code [j][i]} sum to 1) and 0.5 on the diagonal.
         */
        double[][] seatSwapped() {
            double[][] matrix = new double[size()][size()];
            for (int i = 0; i < size(); i++) {
                for (int j = 0; j < size(); j++) {
                    matrix[i][j] = (pairs[i][j].scoreA() + 1 - pairs[j][i].scoreA()) / 2;
                }
            }
            return matrix;
        }
    }

    private PlacementTournament() {
    }

    /** One game from a full board, side A to move, to its end or to the ply cap. */
    static Outcome play(MangolaBoard board, MangolaBot a, MangolaBot b, MangolaRules rules, int maxPlies) {
        Mangola game = Mangola.fromLayout(GameId.generate(), rules, SEAT_A, board);
        int plies = 0;
        try {
            while (!game.isFinished() && plies < maxPlies) {
                PlayerId mover = game.currentSeat();
                Side side = game.sideOf(mover);
                MangolaBot bot = side == Side.A ? a : b;
                game.sow(mover, bot.decideMove(game.position(), side));
                plies++;
            }
        } catch (MangolaException e) {
            throw new IllegalStateException("A bot played an illegal move", e);
        }
        if (!game.isFinished()) {
            return new Outcome(null, plies, true);
        }
        PlayerId winner = game.result().winner();
        return new Outcome(winner == null ? null : game.sideOf(winner), plies, false);
    }

    /** Every ordered pair of placements, {@code repetitions} times each. */
    static Results run(List<Placement> placements, Config config, MoveSearch search) {
        return extend(new Results(new PairResult[0][0]), placements, config, search);
    }

    /**
     * The round robin of a pool whose first placements were already played with the same config: their pairs
     * are copied, only the pairs with a new placement are played (the seeds being the same, it equals a full run).
     */
    static Results extend(Results known, List<Placement> placements, Config config, MoveSearch search) {
        int size = placements.size();
        PairResult[][] pairs = new PairResult[size][size];
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int a = i;
                int b = j;
                if (a < known.size() && b < known.size()) {
                    pairs[a][b] = known.pair(a, b);
                    continue;
                }
                tasks.add(() -> {
                    pairs[a][b] = playPair(placements.get(a).layout(), placements.get(b).layout(), a, b, config, search);
                    return null;
                });
            }
        }
        runAll(tasks, config.threads());
        return new Results(pairs);
    }

    private static PairResult playPair(int[] layoutA, int[] layoutB, int a, int b, Config config, MoveSearch search) {
        MangolaBoard board = MangolaBoard.empty().withLayout(Side.A, layoutA).withLayout(Side.B, layoutB);
        if (MangolaMoves.moves(board, Side.A, config.rules()).isEmpty()) {
            return new PairResult(0, 0, 0, config.repetitions(), 0, 0);
        }
        int winsA = 0;
        int draws = 0;
        int winsB = 0;
        int capped = 0;
        long plies = 0;
        for (int rep = 0; rep < config.repetitions(); rep++) {
            long seed = mix(config.baseSeed(), a, b, rep);
            Outcome outcome = play(board, search.bot(seed), search.bot(~seed), config.rules(), config.maxPlies());
            plies += outcome.plies();
            if (outcome.winner() == Side.A) {
                winsA++;
            } else if (outcome.winner() == Side.B) {
                winsB++;
            } else {
                draws++;
                if (outcome.capped()) {
                    capped++;
                }
            }
        }
        return new PairResult(winsA, draws, winsB, 0, capped, plies);
    }

    /**
     * {@code games} games between two layout policies, the first one starting (side A) in the even games. A start
     * the game would refuse (side A without a legal move) is drawn again from both policies, as the real game
     * asks the seats to arrange again.
     */
    static Duel duel(LayoutPolicy first, LayoutPolicy second, MoveSearch search, int games, long seed, int threads,
                     MangolaRules rules, int maxPlies) {
        Outcome[] outcomes = new Outcome[games];
        boolean[] firstStarts = new boolean[games];
        int[] redrawn = new int[games];
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int g = 0; g < games; g++) {
            int game = g;
            tasks.add(() -> {
                long gameSeed = mix(seed, game, -1, -1);
                boolean starts = game % 2 == 0;
                Random firstRandom = new Random(gameSeed);
                Random secondRandom = new Random(~gameSeed);
                Side firstSide = starts ? Side.A : Side.B;
                MangolaBoard board;
                int tries = 0;
                do {
                    if (tries++ > 20) {
                        throw new IllegalStateException("No playable start between the two policies");
                    }
                    board = MangolaBoard.empty()
                            .withLayout(firstSide, first.layout(firstSide, firstRandom))
                            .withLayout(firstSide.other(), second.layout(firstSide.other(), secondRandom));
                } while (MangolaMoves.moves(board, Side.A, rules).isEmpty());
                redrawn[game] = tries - 1;
                MangolaBot firstBot = search.bot(gameSeed * 31 + 1);
                MangolaBot secondBot = search.bot(gameSeed * 31 + 2);
                outcomes[game] = starts
                        ? play(board, firstBot, secondBot, rules, maxPlies)
                        : play(board, secondBot, firstBot, rules, maxPlies);
                firstStarts[game] = starts;
                return null;
            });
        }
        runAll(tasks, threads);
        int wins = 0;
        int draws = 0;
        int losses = 0;
        int startWins = 0;
        int redraws = 0;
        for (int g = 0; g < games; g++) {
            Side firstSide = firstStarts[g] ? Side.A : Side.B;
            Side winner = outcomes[g].winner();
            redraws += redrawn[g];
            if (winner == null) {
                draws++;
            } else if (winner == firstSide) {
                wins++;
                if (firstStarts[g]) {
                    startWins++;
                }
            } else {
                losses++;
            }
        }
        return new Duel(wins, draws, losses, startWins, redraws);
    }

    /** A well-spread seed from the coordinates of a game (SplitMix64 finaliser). */
    static long mix(long base, long x, long y, long z) {
        long h = base;
        for (long part : new long[]{x, y, z}) {
            h += 0x9E3779B97F4A7C15L + part;
            h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
            h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
            h = h ^ (h >>> 31);
        }
        return h;
    }

    static void runAll(List<Callable<Void>> tasks, int threads) {
        if (threads <= 1) {
            for (Callable<Void> task : tasks) {
                try {
                    task.call();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
