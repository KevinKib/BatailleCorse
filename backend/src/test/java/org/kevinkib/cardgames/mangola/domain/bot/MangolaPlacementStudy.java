package org.kevinkib.cardgames.mangola.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaBoard;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.Side;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Not a test: the Mangola placement study (docs/specs/2026-10-25-mangola-placement-study.md), run on demand with
 * {@code mvn -s ../deploy/docker/settings.xml test -Dtest=MangolaPlacementStudy -Dmangola.study=all} (or a comma
 * list of stages: {@code tournament,layoutscore,budget,weights,oracle,duel,hybrid,book,regression}). Optional {@code
 * -Dmangola.study.threads=4} and {@code -Dmangola.study.rounds=N} (oracle rounds in all: the extended pool of an
 * earlier run in {@code target/mangola-study/} is resumed, and the rounds stop early when one adds nothing). It prints its findings and writes them, with the matrices, to {@code
 * target/mangola-study/}. Every number is reproducible: seeds are fixed and the budgets are node counts, never
 * wall-clock time. Nothing here is part of the production jar, and nothing changes the bots.
 */
@EnabledIfSystemProperty(named = "mangola.study", matches = ".+")
class MangolaPlacementStudy {

    private static final MangolaRules OWNER = MangolaRules.OWNER;
    private static final long CAP = 4_000;
    private static final long PRODUCTION_LIKE_CAP = 40_000;
    private static final int REPETITIONS = 2;
    private static final long TOURNAMENT_SEED = 20261025L;
    private static final long DUEL_SEED = 900_001L;
    private static final int MAX_PLIES = 600;
    private static final int FICTITIOUS_PLAY_ITERATIONS = 2_000_000;
    private static final double SUPPORT_THRESHOLD = 0.02;
    /** Oracle rounds in all (rounds already in the cache count): {@code -Dmangola.study.rounds}, default 2. */
    private static final int ORACLE_ROUNDS = Integer.getInteger("mangola.study.rounds", 2);
    private static final int ORACLE_CLIMBS_PER_SIDE = 3;
    private static final int ORACLE_STEPS = 40;
    private static final int DUEL_GAMES = 400;
    private static final Path OUT = Path.of("target", "mangola-study");

    private final Set<String> stages = new HashSet<>(Arrays.asList(System.getProperty("mangola.study", "").split(",")));
    private final int threads = Integer.getInteger("mangola.study.threads", 4);
    private final StringBuilder report = new StringBuilder();

    private boolean stage(String name) {
        return stages.contains("all") || stages.contains(name);
    }

    private static PlacementTournament.MoveSearch expert(long cap, StudyExpert.Weights weights) {
        return seed -> new StudyExpert(new Random(seed), cap, weights);
    }

    @Test
    void study() throws IOException {
        Files.createDirectories(OUT);
        long started = System.nanoTime();
        List<PlacementTournament.Placement> pool = basePool();
        line("Pool: %d placements (%s)", pool.size(), describePoolSources(pool));
        for (int i = 0; i < pool.size(); i++) {
            line("  %-5s %s", pool.get(i).name(), show(pool.get(i).layout()));
        }

        PlacementTournament.Results results = null;
        if (stage("tournament") || stage("oracle") || stage("duel") || stage("layoutscore") || stage("budget")) {
            results = readPairs("base", pool);
            if (results == null) {
                results = timed("tournament " + pool.size() + "x" + pool.size(), () -> PlacementTournament.run(pool,
                        new PlacementTournament.Config(OWNER, REPETITIONS, TOURNAMENT_SEED, threads, MAX_PLIES),
                        expert(CAP, StudyExpert.Weights.PRODUCTION)));
                writeMatrices("base", pool, results);
            } else {
                line("[tournament read back from %s: the same pool, seed and budget were already played]",
                        OUT.resolve("pairs-base.csv"));
            }
            analyse("BASE POOL", pool, results);
        }

        if (stage("layoutscore")) {
            layoutScoreCorrelation(pool, results);
        }
        List<Integer> subset = IntStream.range(0, pool.size()).filter(i -> i % 4 == 0).boxed().toList();
        if (stage("budget")) {
            robustness("NODE BUDGET " + PRODUCTION_LIKE_CAP, pool, subset, results,
                    expert(PRODUCTION_LIKE_CAP, StudyExpert.Weights.PRODUCTION), 1);
        }
        if (stage("weights")) {
            PlacementTournament.Results reference = results;
            for (StudyExpert.Weights weights : List.of(new StudyExpert.Weights(10, 0, 0, 0),
                    new StudyExpert.Weights(10, 6, 10, 6), new StudyExpert.Weights(10, 1, 2, 1))) {
                robustness("WEIGHTS " + weights, pool, subset, reference, expert(CAP, weights), REPETITIONS);
            }
            layoutScoreUnderWeights(pool);
        }

        List<PlacementTournament.Placement> extended = pool;
        PlacementTournament.Results extendedResults = results;
        if (stage("oracle") || stage("duel")) {
            List<PlacementTournament.Placement> cached = Files.exists(OUT.resolve("layouts-final.csv"))
                    ? readLayouts("final") : null;
            PlacementTournament.Results cachedResults = cached == null ? null : readPairs("final", cached);
            int firstRound = 1;
            if (cachedResults != null) {
                extended = cached;
                extendedResults = cachedResults;
                firstRound = lastRound(cached) + 1;
                line("[extended pool of %d read back from %s: oracle rounds up to %d already played]", cached.size(),
                        OUT.resolve("pairs-final.csv"), firstRound - 1);
                analyse("CACHED FINAL POOL", extended, extendedResults);
            }
            State state = new State(extended, extendedResults);
            int played = OracleRounds.run(firstRound, ORACLE_ROUNDS, round -> {
                List<PlacementTournament.Placement> responses = oracle(round, state.pool, state.results);
                if (responses.isEmpty()) {
                    line("Oracle round %d found no new layout: the oracle has settled.", round);
                    return false;
                }
                List<PlacementTournament.Placement> grown = new ArrayList<>(state.pool);
                grown.addAll(responses);
                PlacementTournament.Results known = state.results;
                state.results = timed("oracle round " + round + " extension to " + grown.size(),
                        () -> PlacementTournament.extend(known, grown,
                                new PlacementTournament.Config(OWNER, REPETITIONS, TOURNAMENT_SEED, threads, MAX_PLIES),
                                expert(CAP, StudyExpert.Weights.PRODUCTION)));
                state.pool = grown;
                analyse("AFTER ORACLE ROUND " + round, state.pool, state.results);
                writeMatricesQuietly("final", state.pool, state.results);
                boolean entered = enteredAMix(state.pool, state.results, round);
                line("Oracle round %d: %d new layout(s), %s.", round, responses.size(),
                        entered ? "at least one entered a mix" : "none entered a mix: the oracle has settled");
                return entered;
            });
            line("Oracle rounds run in this execution: %d (cache up to round %d, limit %d).", played,
                    firstRound - 1, ORACLE_ROUNDS);
            extended = state.pool;
            extendedResults = state.results;
            writeMatrices("final", extended, extendedResults);
        }
        if (stage("book")) {
            List<PlacementTournament.Placement> finalPool = readLayouts("final");
            book(finalPool, readPairs("final", finalPool));
        }

        if (stage("duel")) {
            duels(extended, extendedResults);
        }
        if (stage("regression")) {
            regression();
        }
        if (stage("hybrid")) {
            List<PlacementTournament.Placement> finalPool = stage("oracle") ? extended : readLayouts("final");
            PlacementTournament.Results finalResults = stage("oracle") ? extendedResults : readPairs("final", finalPool);
            hybridDuels(finalPool, finalResults);
        }
        line("Total wall time %.1f s, %.1f s CPU (this JVM), %d threads", (System.nanoTime() - started) / 1e9,
                cpuMillis() / 1e3, threads);
        Files.writeString(OUT.resolve("report.txt"), report.toString(), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ oracle bookkeeping

    /** The pool and results the oracle rounds grow, shared with the round lambda. */
    private static final class State {
        List<PlacementTournament.Placement> pool;
        PlacementTournament.Results results;

        State(List<PlacementTournament.Placement> pool, PlacementTournament.Results results) {
            this.pool = pool;
            this.results = results;
        }
    }

    private static final java.util.regex.Pattern ORACLE_NAME = java.util.regex.Pattern.compile("O(\\d+)[AB]\\d+");

    /** The highest oracle round whose layouts (named {@code O<round><side><n>}) are in the pool, or 0. */
    static int lastRound(List<PlacementTournament.Placement> pool) {
        int last = 0;
        for (PlacementTournament.Placement p : pool) {
            java.util.regex.Matcher m = ORACLE_NAME.matcher(p.name());
            if (m.matches()) {
                last = Math.max(last, Integer.parseInt(m.group(1)));
            }
        }
        return last;
    }

    /** Whether a layout found in this round carries weight (at least the support threshold) in either pruned mix. */
    private boolean enteredAMix(List<PlacementTournament.Placement> pool, PlacementTournament.Results results, int round) {
        ZeroSumGame.Solution aware = ZeroSumGame.solve(results.starter(), FICTITIOUS_PLAY_ITERATIONS);
        double[] starterMix = pruned(aware.rowMix());
        double[] secondMix = pruned(aware.columnMix());
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i).name().startsWith("O" + round) && (starterMix[i] > 0 || secondMix[i] > 0)) {
                return true;
            }
        }
        return false;
    }

    private void writeMatricesQuietly(String tag, List<PlacementTournament.Placement> pool,
                                      PlacementTournament.Results results) {
        try {
            writeMatrices(tag, pool, results);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The {@code book} stage: the final pruned mixes of the cached pool, as the Java the placement book will freeze. */
    private void book(List<PlacementTournament.Placement> pool, PlacementTournament.Results results) {
        ZeroSumGame.Solution aware = ZeroSumGame.solve(results.starter(), FICTITIOUS_PLAY_ITERATIONS);
        line("");
        line("=== BOOK (pool of %d, starter's value in [%.3f, %.3f], mixes pruned below %.0f%%)", pool.size(),
                aware.lowerValue(), aware.upperValue(), SUPPORT_THRESHOLD * 100);
        for (Side side : Side.values()) {
            double[] mix = pruned(side == Side.A ? aware.rowMix() : aware.columnMix());
            line("%s:", side == Side.A ? "starter (side A)" : "second player (side B)");
            Integer[] order = IntStream.range(0, mix.length).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -mix[i]));
            for (int i : order) {
                if (mix[i] > 0) {
                    line("  new Entry(\"%s\", %.3f, new int[]{%s}), // %s", pool.get(i).name(), mix[i],
                            Arrays.stream(pool.get(i).layout()).mapToObj(Integer::toString)
                                    .collect(java.util.stream.Collectors.joining(",")), show(pool.get(i).layout()));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the pool

    /** The 53 catalogue layouts, 15 random layouts and 12 layouts chosen by the production expert (6 per side). */
    static List<PlacementTournament.Placement> basePool() {
        List<PlacementTournament.Placement> pool = new ArrayList<>();
        Set<List<Integer>> seen = new HashSet<>();
        List<int[]> catalogue = MangolaLayouts.catalogue();
        for (int i = 0; i < catalogue.size(); i++) {
            add(pool, seen, String.format("C%02d", i), catalogue.get(i).clone());
        }
        int randoms = 0;
        for (long seed = 1_000; randoms < 15; seed++) {
            int[] layout = MangolaLayouts.random(new Random(seed));
            if (!Mangola.onlyEndlessMoves(OWNER, Side.A, MangolaLayouts.boxed(layout))
                    && add(pool, seen, String.format("R%02d", randoms), layout)) {
                randoms++;
            }
        }
        for (Side side : Side.values()) {
            for (long seed = 1; seed <= 6; seed++) {
                int[] layout = new StudyExpert(new Random(seed), 1, StudyExpert.Weights.PRODUCTION)
                        .proposeLayout(OWNER, side);
                add(pool, seen, "E" + side + seed, layout);
            }
        }
        return pool;
    }

    private static boolean add(List<PlacementTournament.Placement> pool, Set<List<Integer>> seen, String name,
                               int[] layout) {
        if (!seen.add(MangolaLayouts.boxed(layout))) {
            return false;
        }
        pool.add(new PlacementTournament.Placement(name, layout));
        return true;
    }

    private static String describePoolSources(List<PlacementTournament.Placement> pool) {
        long c = pool.stream().filter(p -> p.name().startsWith("C")).count();
        long r = pool.stream().filter(p -> p.name().startsWith("R")).count();
        long e = pool.stream().filter(p -> p.name().startsWith("E")).count();
        return c + " catalogue, " + r + " random, " + e + " expert";
    }

    /** A layout seen from its owner: the inner row then the outer row, both from column a to column h. */
    static String show(int[] layout) {
        StringBuilder inner = new StringBuilder();
        StringBuilder outer = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            inner.append(Character.forDigit(layout[i], 36));
            outer.append(Character.forDigit(layout[15 - i], 36));
        }
        return "inner " + inner + " / outer " + outer;
    }

    // ------------------------------------------------------------------ analysis

    private void analyse(String title, List<PlacementTournament.Placement> pool, PlacementTournament.Results results) {
        int n = pool.size();
        double[][] starter = results.starter();
        double[][] swapped = results.seatSwapped();
        long games = 0;
        long winsA = 0;
        long winsB = 0;
        long draws = 0;
        long capped = 0;
        long stuck = 0;
        long plies = 0;
        long agreeing = 0;
        long repeatedPairs = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                PlacementTournament.PairResult pair = results.pair(i, j);
                games += pair.played();
                winsA += pair.winsA();
                winsB += pair.winsB();
                draws += pair.draws();
                capped += pair.capped();
                stuck += pair.stuck();
                plies += pair.plies();
                if (pair.played() == REPETITIONS) {
                    repeatedPairs++;
                    if (pair.winsA() == REPETITIONS || pair.winsB() == REPETITIONS || pair.draws() == REPETITIONS) {
                        agreeing++;
                    }
                }
            }
        }
        line("");
        line("=== %s: %d placements, %d games (cap %d nodes, %d per ordered pair, seed %d)", title, n, games, CAP,
                REPETITIONS, TOURNAMENT_SEED);
        line("First mover: starter wins %.1f%%, second wins %.1f%%, draws %.1f%% (%d at the ply cap); %d pair starts refused (stuck)",
                100.0 * winsA / games, 100.0 * winsB / games, 100.0 * draws / games, capped, stuck);
        line("Mean game length %.1f plies; both repetitions of a pair agree in %.1f%% of pairs", (double) plies / games,
                100.0 * agreeing / repeatedPairs);

        // Each placement as the starter (row of the starter matrix) and as the second player (column).
        double[] asStarter = new double[n];
        double[] asSecond = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                asStarter[i] += starter[i][j] / n;
                asSecond[i] += (1 - starter[j][i]) / n;
            }
        }
        double se = Math.sqrt(0.25 / (n * REPETITIONS)); // the largest standard error of a mean of n*reps games
        line("Per placement (uniform field, %d games per side, standard error at most %.3f):", n * REPETITIONS, se);
        Integer[] order = IntStream.range(0, n).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -(asStarter[i] + asSecond[i])));
        for (int k = 0; k < n; k++) {
            int i = order[k];
            if (k < 15 || k >= n - 5) {
                line("  %-5s as starter %.3f  as second %.3f  mean %.3f  %s", pool.get(i).name(), asStarter[i],
                        asSecond[i], (asStarter[i] + asSecond[i]) / 2, show(pool.get(i).layout()));
            } else if (k == 15) {
                line("  ...");
            }
        }
        line("Best as second player (uniform field): %s", top(pool, asSecond, 5));
        line("Best as starter (uniform field): %s", top(pool, asStarter, 5));

        // The seat-swapped (side-blind) game.
        line("Seat-swapped game (side unknown): dominated %d of %d; maximin %s; best against a uniform field %s",
                ZeroSumGame.dominatedRows(swapped, 0.0).size(), n, pool.get(ZeroSumGame.maximinRow(swapped)).name(),
                pool.get(ZeroSumGame.bestAgainstUniformRow(swapped)).name());
        ZeroSumGame.Solution blind = ZeroSumGame.solve(swapped, FICTITIOUS_PLAY_ITERATIONS);
        line("  fictitious play (%d iterations): value in [%.3f, %.3f], mix %s", FICTITIOUS_PLAY_ITERATIONS,
                blind.lowerValue(), blind.upperValue(), mix(pool, blind.rowMix()));

        // The side-aware game: the bot knows its side when it arranges (side A starts).
        List<Integer> rowsDominated = ZeroSumGame.dominatedRows(starter, 0.0);
        List<Integer> columnsDominated = ZeroSumGame.dominatedColumns(starter, 0.0);
        line("Side-aware game (rows: starter's layout, columns: second player's): dominated starter layouts %d, dominated second-player layouts %d",
                rowsDominated.size(), columnsDominated.size());
        line("  best pure starter (maximin) %s guarantees %.3f; best pure second player (minimax) %s concedes %.3f",
                pool.get(ZeroSumGame.maximinRow(starter)).name(), minRow(starter, ZeroSumGame.maximinRow(starter)),
                pool.get(minimaxColumn(starter)).name(), maxColumn(starter, minimaxColumn(starter)));
        ZeroSumGame.Solution aware = ZeroSumGame.solve(starter, FICTITIOUS_PLAY_ITERATIONS);
        line("  fictitious play: starter's value in [%.3f, %.3f] (gap %.4f)", aware.lowerValue(), aware.upperValue(),
                aware.gap());
        line("  starter's mix: %s", mix(pool, aware.rowMix()));
        line("  second player's mix: %s", mix(pool, aware.columnMix()));
        double[] uniformRows = new double[n];
        Arrays.fill(uniformRows, 1.0 / n);
        line("  for reference: the uniform field as starter scores %.3f against the second player's mix; the second player's mix scores %.3f against a uniform starter",
                mean(ZeroSumGame.columnValuesAgainst(starter, uniformRows), aware.columnMix()),
                1 - mean(ZeroSumGame.columnValuesAgainst(starter, uniformRows), aware.columnMix()));
    }

    private static double mean(double[] values, double[] weights) {
        double total = 0;
        for (int i = 0; i < values.length; i++) {
            total += values[i] * weights[i];
        }
        return total;
    }

    private static double minRow(double[][] game, int row) {
        return Arrays.stream(game[row]).min().orElseThrow();
    }

    private static int minimaxColumn(double[][] game) {
        int best = 0;
        for (int j = 1; j < game[0].length; j++) {
            if (maxColumn(game, j) < maxColumn(game, best)) {
                best = j;
            }
        }
        return best;
    }

    private static double maxColumn(double[][] game, int column) {
        double max = 0;
        for (double[] row : game) {
            max = Math.max(max, row[column]);
        }
        return max;
    }

    private static String top(List<PlacementTournament.Placement> pool, double[] values, int count) {
        Integer[] order = IntStream.range(0, values.length).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -values[i]));
        StringBuilder text = new StringBuilder();
        for (int k = 0; k < count && k < order.length; k++) {
            text.append(k == 0 ? "" : ", ").append(pool.get(order[k]).name())
                    .append(String.format(Locale.ROOT, " %.3f", values[order[k]]));
        }
        return text.toString();
    }

    private static String mix(List<PlacementTournament.Placement> pool, double[] mix) {
        Integer[] order = IntStream.range(0, mix.length).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -mix[i]));
        StringBuilder text = new StringBuilder();
        double shown = 0;
        for (int i : order) {
            if (mix[i] < 0.005) {
                break;
            }
            shown += mix[i];
            text.append(text.length() == 0 ? "" : ", ").append(pool.get(i).name())
                    .append(String.format(Locale.ROOT, " %.3f", mix[i]));
        }
        return text + String.format(Locale.ROOT, " (%.3f shown)", shown);
    }

    // ------------------------------------------------------------------ calibration

    /** Does the expert's own 300-node layout score predict the tournament? Spearman rank correlations. */
    private void layoutScoreCorrelation(List<PlacementTournament.Placement> pool, PlacementTournament.Results results) {
        int n = pool.size();
        double[][] starter = results.starter();
        double[] asStarter = new double[n];
        double[] asSecond = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                asStarter[i] += starter[i][j] / n;
                asSecond[i] += (1 - starter[j][i]) / n;
            }
        }
        ZeroSumGame.Solution aware = ZeroSumGame.solve(starter, FICTITIOUS_PLAY_ITERATIONS);
        double[] starterVsMix = ZeroSumGame.rowValuesAgainst(starter, aware.columnMix());
        double[] secondVsMix = ZeroSumGame.columnValuesAgainst(starter, aware.rowMix());
        for (int j = 0; j < n; j++) {
            secondVsMix[j] = 1 - secondVsMix[j];
        }
        double[][] scores = layoutScores(pool, StudyExpert.Weights.PRODUCTION);
        line("");
        line("=== LAYOUT SCORE: the expert's 300-node layout score (mean of 8 draws of its 4 typical opponents)");
        line("Spearman, side A score vs tournament as starter: uniform field %.3f, against the second player's mix %.3f",
                spearman(scores[0], asStarter), spearman(scores[0], starterVsMix));
        line("Spearman, side B score vs tournament as second: uniform field %.3f, against the starter's mix %.3f",
                spearman(scores[1], asSecond), spearman(scores[1], secondVsMix));
        line("Layouts the score ranks first: side A %s; side B %s", top(pool, scores[0], 5), top(pool, scores[1], 5));
        writeColumns("layout-scores.csv", pool, new String[]{"scoreA", "scoreB", "asStarter", "asSecond"},
                scores[0], scores[1], asStarter, asSecond);
    }

    private double[][] layoutScores(List<PlacementTournament.Placement> pool, StudyExpert.Weights weights) {
        double[][] scores = new double[2][pool.size()];
        for (Side side : Side.values()) {
            for (int i = 0; i < pool.size(); i++) {
                double total = 0;
                for (long seed = 1; seed <= 8; seed++) {
                    StudyExpert expert = new StudyExpert(new Random(seed), 1, weights);
                    total += expert.layoutScore(OWNER, side, pool.get(i).layout(), expert.typicalOpponents());
                }
                scores[side.ordinal()][i] = total / 8;
            }
        }
        return scores;
    }

    private void layoutScoreUnderWeights(List<PlacementTournament.Placement> pool) {
        double[][] production = layoutScores(pool, StudyExpert.Weights.PRODUCTION);
        for (StudyExpert.Weights weights : List.of(new StudyExpert.Weights(10, 0, 0, 0),
                new StudyExpert.Weights(10, 6, 10, 6))) {
            double[][] other = layoutScores(pool, weights);
            line("Layout score under %s vs production weights: Spearman side A %.3f, side B %.3f", weights,
                    spearman(production[0], other[0]), spearman(production[1], other[1]));
        }
    }

    /** The subset's sub-tournament under another move search, compared with the reference results. */
    private void robustness(String title, List<PlacementTournament.Placement> pool, List<Integer> subset,
                            PlacementTournament.Results reference, PlacementTournament.MoveSearch search, int reps) {
        List<PlacementTournament.Placement> sub = subset.stream().map(pool::get).toList();
        PlacementTournament.Results results = timed(title, () -> PlacementTournament.run(sub,
                new PlacementTournament.Config(OWNER, reps, TOURNAMENT_SEED + 1, threads, MAX_PLIES), search));
        int n = sub.size();
        double[][] other = results.starter();
        double[] refStarter = new double[n];
        double[] refSecond = new double[n];
        double[] starter = new double[n];
        double[] second = new double[n];
        long games = 0;
        long winsA = 0;
        long sameWinner = 0;
        long compared = 0;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                PlacementTournament.PairResult ref = reference.pair(subset.get(a), subset.get(b));
                refStarter[a] += ref.scoreA() / n;
                refSecond[b] += (1 - ref.scoreA()) / n;
                starter[a] += other[a][b] / n;
                second[b] += (1 - other[a][b]) / n;
                PlacementTournament.PairResult pair = results.pair(a, b);
                games += pair.played();
                winsA += pair.winsA();
                if (pair.played() > 0 && ref.played() > 0) {
                    compared++;
                    if (Math.round(pair.scoreA()) == Math.round(ref.scoreA())) {
                        sameWinner++;
                    }
                }
            }
        }
        line("");
        line("=== %s: %d placements (every 4th of the pool), %d games, starter wins %.1f%%", title, n, games,
                100.0 * winsA / games);
        line("Same majority result as the reference in %.1f%% of ordered pairs; Spearman as starter %.3f, as second %.3f",
                100.0 * sameWinner / compared, spearman(refStarter, starter), spearman(refSecond, second));
        line("Best as second here: %s; in the reference: %s", top(sub, second, 4), top(sub, refSecond, 4));
    }

    // ------------------------------------------------------------------ the oracle (approximate best responses)

    /**
     * One round of an approximate double oracle: hill-climbs new layouts against the current equilibrium mixes
     * (the starter's layouts against the second player's mix and the reverse), to find what the pool lacks.
     */
    private List<PlacementTournament.Placement> oracle(int round, List<PlacementTournament.Placement> pool,
                                                       PlacementTournament.Results results) {
        double[][] starter = results.starter();
        ZeroSumGame.Solution aware = ZeroSumGame.solve(starter, FICTITIOUS_PLAY_ITERATIONS);
        List<PlacementTournament.Placement> found = new ArrayList<>();
        line("");
        line("=== ORACLE ROUND %d", round);
        for (Side side : Side.values()) {
            double[] opponentsMix = side == Side.A ? aware.columnMix() : aware.rowMix();
            List<int[]> opponents = new ArrayList<>();
            List<Double> weights = new ArrayList<>();
            for (int k = 0; k < pool.size(); k++) {
                if (opponentsMix[k] >= SUPPORT_THRESHOLD) {
                    opponents.add(pool.get(k).layout());
                    weights.add(opponentsMix[k]);
                }
            }
            double[] values = side == Side.A
                    ? ZeroSumGame.rowValuesAgainst(starter, aware.columnMix())
                    : complement(ZeroSumGame.columnValuesAgainst(starter, aware.rowMix()));
            Integer[] order = IntStream.range(0, pool.size()).boxed().toArray(Integer[]::new);
            Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -values[i]));
            for (int climb = 0; climb < ORACLE_CLIMBS_PER_SIDE; climb++) {
                int[] startLayout = climb < 2 ? pool.get(order[climb]).layout()
                        : MangolaLayouts.random(new Random(round * 100L + climb + side.ordinal() * 10));
                long seed = PlacementTournament.mix(TOURNAMENT_SEED, round, side.ordinal(), climb);
                long t = System.nanoTime();
                Climb result = climb(startLayout, side, opponents, weights, seed);
                String name = "O" + round + side + climb;
                line("  %s from %s: %.3f -> %.3f against the opponent's support (%d layouts) in %.0f s; %s", name,
                        climb < 2 ? pool.get(order[climb]).name() : "random", result.startScore(), result.score(),
                        opponents.size(), (System.nanoTime() - t) / 1e9, show(result.layout()));
                if (pool.stream().noneMatch(p -> Arrays.equals(p.layout(), result.layout()))
                        && found.stream().noneMatch(p -> Arrays.equals(p.layout(), result.layout()))) {
                    found.add(new PlacementTournament.Placement(name, result.layout()));
                }
            }
        }
        return found;
    }

    private record Climb(int[] layout, double startScore, double score) {
    }

    /** First-improvement hill climbing: move one or two seeds, keep the change when the weighted score rises. */
    private Climb climb(int[] start, Side side, List<int[]> opponents, List<Double> weights, long seed) {
        Random random = new Random(seed);
        int[] best = start.clone();
        double bestScore = weightedScore(best, side, opponents, weights, seed);
        double startScore = bestScore;
        for (int step = 0; step < ORACLE_STEPS; step++) {
            int[] candidate = best.clone();
            int moves = 1 + random.nextInt(2);
            for (int m = 0; m < moves; m++) {
                int from;
                do {
                    from = random.nextInt(16);
                } while (candidate[from] == 0);
                candidate[from]--;
                candidate[random.nextInt(16)]++;
            }
            if (Mangola.onlyEndlessMoves(OWNER, side, MangolaLayouts.boxed(candidate))) {
                continue;
            }
            double score = weightedScore(candidate, side, opponents, weights, seed);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return new Climb(best, startScore, bestScore);
    }

    /** The layout's mean score for its side against the weighted opponents, one game each, common seeds. */
    private double weightedScore(int[] layout, Side side, List<int[]> opponents, List<Double> weights, long seed) {
        double[] scores = new double[opponents.size()];
        List<java.util.concurrent.Callable<Void>> tasks = new ArrayList<>();
        for (int index = 0; index < opponents.size(); index++) {
            int k = index;
            tasks.add(() -> {
                MangolaBoard board = MangolaBoard.empty().withLayout(side, layout)
                        .withLayout(side.other(), opponents.get(k));
                if (MangolaMoves.moves(board, Side.A, OWNER).isEmpty()) {
                    scores[k] = 0.5; // the game would refuse this start
                    return null;
                }
                long gameSeed = PlacementTournament.mix(seed, k, 0, 0);
                PlacementTournament.Outcome outcome = PlacementTournament.play(board,
                        new StudyExpert(new Random(gameSeed), CAP, StudyExpert.Weights.PRODUCTION),
                        new StudyExpert(new Random(~gameSeed), CAP, StudyExpert.Weights.PRODUCTION), OWNER, MAX_PLIES);
                scores[k] = outcome.winner() == null ? 0.5 : outcome.winner() == side ? 1 : 0;
                return null;
            });
        }
        PlacementTournament.runAll(tasks, threads);
        double total = 0;
        double weight = 0;
        for (int k = 0; k < scores.length; k++) {
            total += scores[k] * weights.get(k);
            weight += weights.get(k);
        }
        return total / weight;
    }

    // ------------------------------------------------------------------ validation duels

    private void duels(List<PlacementTournament.Placement> pool, PlacementTournament.Results results) {
        ZeroSumGame.Solution aware = ZeroSumGame.solve(results.starter(), FICTITIOUS_PLAY_ITERATIONS);
        ZeroSumGame.Solution blind = ZeroSumGame.solve(results.seatSwapped(), FICTITIOUS_PLAY_ITERATIONS);
        double[] starterMix = pruned(aware.rowMix());
        double[] secondMix = pruned(aware.columnMix());
        double[] blindMix = pruned(blind.rowMix());
        line("");
        line("=== PROPOSED LISTS (mixes pruned below %.0f%% and renormalised)", SUPPORT_THRESHOLD * 100);
        line("Starter: %s", mix(pool, starterMix));
        for (int i = 0; i < pool.size(); i++) {
            if (starterMix[i] > 0) {
                line("  %-5s %.3f %s %s", pool.get(i).name(), starterMix[i], show(pool.get(i).layout()),
                        Arrays.toString(pool.get(i).layout()));
            }
        }
        line("Second player: %s", mix(pool, secondMix));
        for (int i = 0; i < pool.size(); i++) {
            if (secondMix[i] > 0) {
                line("  %-5s %.3f %s %s", pool.get(i).name(), secondMix[i], show(pool.get(i).layout()),
                        Arrays.toString(pool.get(i).layout()));
            }
        }
        line("Side-blind: %s", mix(pool, blindMix));

        PlacementTournament.LayoutPolicy proposed = (side, random) ->
                draw(pool, side == Side.A ? starterMix : secondMix, random);
        PlacementTournament.LayoutPolicy sideBlind = (side, random) -> draw(pool, blindMix, random);
        PlacementTournament.LayoutPolicy supportUniform = (side, random) -> {
            double[] mix = side == Side.A ? starterMix : secondMix;
            double[] uniform = new double[mix.length];
            for (int i = 0; i < mix.length; i++) {
                uniform[i] = mix[i] > 0 ? 1 : 0;
            }
            return draw(pool, uniform, random);
        };
        PlacementTournament.LayoutPolicy currentExpert = (side, random) ->
                octoberSearch(random, side);
        PlacementTournament.LayoutPolicy currentMedium = (side, random) ->
                new GreedyMangolaBot(random).decideLayout(OWNER, side, Set.of());
        PlacementTournament.LayoutPolicy easy = (side, random) ->
                new RandomMangolaBot(random).decideLayout(OWNER, side, Set.of());

        line("");
        line("=== VALIDATION DUELS (fresh seed %d, %d games each, the first policy starts every other game, both seats played by the expert at %d nodes)",
                DUEL_SEED, DUEL_GAMES, CAP);
        duel("proposed vs current expert", proposed, currentExpert);
        duel("proposed vs current medium", proposed, currentMedium);
        duel("current expert vs current medium", currentExpert, currentMedium);
        duel("side-blind mix vs current expert", sideBlind, currentExpert);
        duel("support drawn uniformly vs current medium", supportUniform, currentMedium);
        duel("current medium vs easy", currentMedium, easy);
        duel("proposed vs current expert (" + PRODUCTION_LIKE_CAP + " nodes, 100 games)", proposed, currentExpert,
                expert(PRODUCTION_LIKE_CAP, StudyExpert.Weights.PRODUCTION), 100);
    }

    /**
     * The hybrid: the current expert's layout search, with the equilibrium list of its side scored first and the
     * other side's list added to its typical opponents. Measured against the same references as the proposal.
     */
    private void hybridDuels(List<PlacementTournament.Placement> pool, PlacementTournament.Results results) {
        ZeroSumGame.Solution aware = ZeroSumGame.solve(results.starter(), FICTITIOUS_PLAY_ITERATIONS);
        double[] starterMix = pruned(aware.rowMix());
        double[] secondMix = pruned(aware.columnMix());
        List<int[]> starterList = support(pool, starterMix);
        List<int[]> secondList = support(pool, secondMix);
        PlacementTournament.LayoutPolicy hybrid = (side, random) -> new StudyExpert(random, 1,
                StudyExpert.Weights.PRODUCTION, 300, side == Side.A ? starterList : secondList,
                side == Side.A ? secondList : starterList).proposeLayout(OWNER, side);
        PlacementTournament.LayoutPolicy proposed = (side, random) ->
                draw(pool, side == Side.A ? starterMix : secondMix, random);
        PlacementTournament.LayoutPolicy currentExpert = (side, random) ->
                octoberSearch(random, side);
        PlacementTournament.LayoutPolicy currentMedium = (side, random) ->
                new GreedyMangolaBot(random).decideLayout(OWNER, side, Set.of());
        PlacementTournament.LayoutPolicy easy = (side, random) ->
                new RandomMangolaBot(random).decideLayout(OWNER, side, Set.of());
        line("");
        line("=== HYBRID DUELS (seed %d, %d games each, expert moves at %d nodes): the current search, its side's list scored first, the other side's list among its opponents",
                DUEL_SEED, DUEL_GAMES, CAP);
        duel("hybrid vs current expert", hybrid, currentExpert);
        duel("hybrid vs proposed", hybrid, proposed);
        duel("hybrid vs current medium", hybrid, currentMedium);
        duel("proposed vs easy", proposed, easy);
        duel("current expert vs easy", currentExpert, easy);
        duel("hybrid vs easy", hybrid, easy);
    }

    /** The layout the production expert chose before the placement book: the October 2026 layout search. */
    private static int[] octoberSearch(java.util.random.RandomGenerator random, Side side) {
        return new StudyExpert(random, 1, StudyExpert.Weights.PRODUCTION).decideLayout(OWNER, side, Set.of());
    }

    /**
     * The {@code regression} stage: the production expert's placement (the book, through {@code
     * ExpertMangolaBot}) against the October 2026 layout search ({@link StudyExpert}), 400 games, the same seed,
     * budget and move search as the validation duels. Re-run after any change of the book, the rules or the engine
     * and compare with the 0.605 +- 0.048 the study measured for the round-15 lists.
     */
    private void regression() {
        PlacementTournament.LayoutPolicy book = (side, random) ->
                new ExpertMangolaBot(random, new ExpertMangolaBot.Budget(StudyExpert.UNLIMITED_MILLIS, 1))
                        .decideLayout(OWNER, side, Set.of());
        PlacementTournament.LayoutPolicy october = (side, random) -> octoberSearch(random, side);
        line("");
        line("=== REGRESSION (seed %d, %d games, both seats played by the expert at %d nodes): the placement book against the October 2026 layout search",
                DUEL_SEED, DUEL_GAMES, CAP);
        duel("placement book vs October 2026 search", book, october);
    }

    private static List<int[]> support(List<PlacementTournament.Placement> pool, double[] mix) {
        List<int[]> layouts = new ArrayList<>();
        for (int i = 0; i < mix.length; i++) {
            if (mix[i] > 0) {
                layouts.add(pool.get(i).layout());
            }
        }
        return layouts;
    }

    private static List<PlacementTournament.Placement> readLayouts(String tag) throws IOException {
        List<PlacementTournament.Placement> pool = new ArrayList<>();
        List<String> lines = Files.readAllLines(OUT.resolve("layouts-" + tag + ".csv"), StandardCharsets.UTF_8);
        for (String text : lines.subList(1, lines.size())) {
            String name = text.substring(0, text.indexOf(','));
            String counts = text.substring(text.indexOf('[') + 1, text.indexOf(']'));
            pool.add(new PlacementTournament.Placement(name,
                    Arrays.stream(counts.split(",\\s*")).mapToInt(Integer::parseInt).toArray()));
        }
        return pool;
    }

    private void duel(String title, PlacementTournament.LayoutPolicy first, PlacementTournament.LayoutPolicy second) {
        duel(title, first, second, expert(CAP, StudyExpert.Weights.PRODUCTION), DUEL_GAMES);
    }

    private void duel(String title, PlacementTournament.LayoutPolicy first, PlacementTournament.LayoutPolicy second,
                      PlacementTournament.MoveSearch search, int games) {
        PlacementTournament.Duel duel = timed(title, () -> PlacementTournament.duel(first, second, search, games,
                DUEL_SEED, threads, OWNER, MAX_PLIES));
        int n = duel.games();
        double m = duel.score();
        double variance = (duel.wins() * Math.pow(1 - m, 2) + duel.draws() * Math.pow(0.5 - m, 2)
                + duel.losses() * Math.pow(m, 2)) / n;
        int startGames = (n + 1) / 2;
        int secondWins = duel.wins() - duel.winsWhenStarting();
        line("%s: score %.3f +- %.3f (95%%), %d-%d-%d; wins %d/%d when starting, %d/%d when second; %d starts drawn again",
                title, m, 1.96 * Math.sqrt(variance / n), duel.wins(), duel.draws(), duel.losses(),
                duel.winsWhenStarting(), startGames, secondWins, n - startGames, duel.redrawn());
    }

    private static int[] draw(List<PlacementTournament.Placement> pool, double[] weights, java.util.random.RandomGenerator random) {
        double total = Arrays.stream(weights).sum();
        double x = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            x -= weights[i];
            if (x < 0 && weights[i] > 0) {
                return pool.get(i).layout().clone();
            }
        }
        return pool.get(ZeroSumGame.argMax(weights)).layout().clone();
    }

    private static double[] pruned(double[] mix) {
        double[] kept = new double[mix.length];
        double total = 0;
        for (int i = 0; i < mix.length; i++) {
            if (mix[i] >= SUPPORT_THRESHOLD) {
                kept[i] = mix[i];
                total += mix[i];
            }
        }
        for (int i = 0; i < kept.length; i++) {
            kept[i] /= total;
        }
        return kept;
    }

    // ------------------------------------------------------------------ helpers

    private static double[] complement(double[] values) {
        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = 1 - values[i];
        }
        return result;
    }

    /** Spearman's rank correlation (ties get their mean rank). */
    static double spearman(double[] x, double[] y) {
        double[] rx = ranks(x);
        double[] ry = ranks(y);
        double mx = Arrays.stream(rx).average().orElse(0);
        double my = Arrays.stream(ry).average().orElse(0);
        double covariance = 0;
        double vx = 0;
        double vy = 0;
        for (int i = 0; i < x.length; i++) {
            covariance += (rx[i] - mx) * (ry[i] - my);
            vx += (rx[i] - mx) * (rx[i] - mx);
            vy += (ry[i] - my) * (ry[i] - my);
        }
        return vx == 0 || vy == 0 ? 0 : covariance / Math.sqrt(vx * vy);
    }

    private static double[] ranks(double[] values) {
        Integer[] order = IntStream.range(0, values.length).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> values[i]));
        double[] ranks = new double[values.length];
        for (int k = 0; k < order.length; ) {
            int end = k;
            while (end + 1 < order.length && values[order[end + 1]] == values[order[k]]) {
                end++;
            }
            for (int m = k; m <= end; m++) {
                ranks[order[m]] = (k + end) / 2.0;
            }
            k = end + 1;
        }
        return ranks;
    }

    private interface Work<T> {
        T run();
    }

    private <T> T timed(String title, Work<T> work) {
        long start = System.nanoTime();
        long cpuStart = cpuMillis();
        T result = work.run();
        line("[%s: %.1f s wall, %.1f s CPU]", title, (System.nanoTime() - start) / 1e9, (cpuMillis() - cpuStart) / 1e3);
        return result;
    }

    /** The CPU time of the whole JVM process, all threads. */
    private static long cpuMillis() {
        java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getProcessCpuTime() / 1_000_000;
        }
        return 0;
    }

    private void line(String format, Object... args) {
        String text = String.format(Locale.ROOT, format, args);
        report.append(text).append(System.lineSeparator());
        System.out.println("STUDY " + text);
    }

    private void writeMatrices(String tag, List<PlacementTournament.Placement> pool, PlacementTournament.Results results)
            throws IOException {
        StringBuilder pairs = new StringBuilder("a,b,winsA,draws,winsB,stuck,capped,plies\n");
        for (int i = 0; i < pool.size(); i++) {
            for (int j = 0; j < pool.size(); j++) {
                PlacementTournament.PairResult p = results.pair(i, j);
                pairs.append(pool.get(i).name()).append(',').append(pool.get(j).name()).append(',')
                        .append(p.winsA()).append(',').append(p.draws()).append(',').append(p.winsB()).append(',')
                        .append(p.stuck()).append(',').append(p.capped()).append(',').append(p.plies()).append('\n');
            }
        }
        Files.writeString(OUT.resolve("pairs-" + tag + ".csv"), pairs.toString(), StandardCharsets.UTF_8);
        StringBuilder layouts = new StringBuilder("name,layout\n");
        for (PlacementTournament.Placement p : pool) {
            layouts.append(p.name()).append(",\"").append(Arrays.toString(p.layout())).append("\"\n");
        }
        Files.writeString(OUT.resolve("layouts-" + tag + ".csv"), layouts.toString(), StandardCharsets.UTF_8);
    }

    /**
     * The pairs of an earlier run of the same pool (same names and layouts), or null. The cache is only valid for
     * the constants of this class (seed, cap, repetitions): delete {@code target/mangola-study} after changing them.
     */
    private static PlacementTournament.Results readPairs(String tag, List<PlacementTournament.Placement> pool)
            throws IOException {
        Path pairsFile = OUT.resolve("pairs-" + tag + ".csv");
        Path layoutsFile = OUT.resolve("layouts-" + tag + ".csv");
        if (!Files.exists(pairsFile) || !Files.exists(layoutsFile)) {
            return null;
        }
        List<String> layouts = Files.readAllLines(layoutsFile, StandardCharsets.UTF_8);
        if (layouts.size() != pool.size() + 1) {
            return null;
        }
        for (int i = 0; i < pool.size(); i++) {
            PlacementTournament.Placement p = pool.get(i);
            if (!layouts.get(i + 1).equals(p.name() + ",\"" + Arrays.toString(p.layout()) + "\"")) {
                return null;
            }
        }
        List<String> lines = Files.readAllLines(pairsFile, StandardCharsets.UTF_8);
        PlacementTournament.PairResult[][] pairs = new PlacementTournament.PairResult[pool.size()][pool.size()];
        for (int k = 1; k < lines.size(); k++) {
            String[] f = lines.get(k).split(",");
            int i = (k - 1) / pool.size();
            int j = (k - 1) % pool.size();
            pairs[i][j] = new PlacementTournament.PairResult(Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]), Long.parseLong(f[7]));
        }
        return new PlacementTournament.Results(pairs);
    }

    private void writeColumns(String file, List<PlacementTournament.Placement> pool, String[] names, double[]... columns) {
        StringBuilder text = new StringBuilder("name," + String.join(",", names) + "\n");
        for (int i = 0; i < pool.size(); i++) {
            text.append(pool.get(i).name());
            for (double[] column : columns) {
                text.append(',').append(String.format(Locale.ROOT, "%.4f", column[i]));
            }
            text.append('\n');
        }
        try {
            Files.writeString(OUT.resolve(file), text.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
