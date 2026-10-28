package org.kevinkib.cardgames.kilodemerde.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeRules;
import org.kevinkib.cardgames.kilodemerde.domain.KiloDeck;
import org.kevinkib.cardgames.kilodemerde.domain.KiloValue;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Opt-in strength benchmark, not run by default:
 * {@code mvn test -Dtest=KiloDeMerdeBotStrengthBenchmark -Dkilodemerde.bench=true}.
 *
 * <p>Plays full games of four bots, 10 rounds, seats rotated game after game so the seat order favours nobody, and
 * prints the mean kilos per level (fewer is better). It runs on a small simulator of the rules written here (deal,
 * simultaneous pass steps, a slap race decided by each level's sampled reaction delays, draws with values and the
 * flush), not on the aggregate, which lands in another chunk. Races: a seat holding a four of a kind slaps after its
 * own-set delay, the others follow the first slapper after their follow delay, and the last slapper draws.
 */
@EnabledIfSystemProperty(named = "kilodemerde.bench", matches = "true")
class KiloDeMerdeBotStrengthBenchmark {

    private static final int GAMES = Integer.getInteger("kilodemerde.bench.games", 200);
    private static final int PLAYERS = 4;
    private static final int ROUNDS = 10;

    @Test
    void meanKilosPerLevel() {
        report("easy, medium, hard, hard", KiloDeMerdeBotLevel.EASY, KiloDeMerdeBotLevel.MEDIUM,
                KiloDeMerdeBotLevel.HARD, KiloDeMerdeBotLevel.HARD);
        report("1 hard vs 3 medium", KiloDeMerdeBotLevel.HARD, KiloDeMerdeBotLevel.MEDIUM,
                KiloDeMerdeBotLevel.MEDIUM, KiloDeMerdeBotLevel.MEDIUM);
        report("1 hard vs 3 easy", KiloDeMerdeBotLevel.HARD, KiloDeMerdeBotLevel.EASY,
                KiloDeMerdeBotLevel.EASY, KiloDeMerdeBotLevel.EASY);
        report("1 medium vs 3 easy", KiloDeMerdeBotLevel.MEDIUM, KiloDeMerdeBotLevel.EASY,
                KiloDeMerdeBotLevel.EASY, KiloDeMerdeBotLevel.EASY);
        report("4 medium (control)", KiloDeMerdeBotLevel.MEDIUM, KiloDeMerdeBotLevel.MEDIUM,
                KiloDeMerdeBotLevel.MEDIUM, KiloDeMerdeBotLevel.MEDIUM);
    }

    private void report(String name, KiloDeMerdeBotLevel... levels) {
        Random random = new Random(2026);
        Map<KiloDeMerdeBotLevel, double[]> sums = new EnumMap<>(KiloDeMerdeBotLevel.class);
        for (int game = 0; game < GAMES; game++) {
            int shift = game % PLAYERS;
            List<KiloDeMerdeBotLevel> table = new ArrayList<>();
            for (int seat = 0; seat < PLAYERS; seat++) {
                table.add(levels[(seat + shift) % PLAYERS]);
            }
            int[] kilos = playGame(table, random);
            for (int seat = 0; seat < PLAYERS; seat++) {
                double[] sum = sums.computeIfAbsent(table.get(seat), level -> new double[2]);
                sum[0] += kilos[seat];
                sum[1]++;
            }
        }
        StringBuilder line = new StringBuilder(String.format("%-28s", name));
        sums.forEach((level, sum) -> line.append(String.format("  %s %.1f kg", level.key(), sum[0] / sum[1])));
        System.out.println("BENCH " + line);
    }

    private int[] playGame(List<KiloDeMerdeBotLevel> levels, Random random) {
        KiloDeMerdeReflex reflex = new KiloDeMerdeReflex(KiloDeMerdeBotTuning.defaults());
        List<KiloDeMerdeBotStrategy> bots = new ArrayList<>();
        for (KiloDeMerdeBotLevel level : levels) {
            bots.add(KiloDeMerdeBots.forLevel(level, random));
        }
        List<Card> pile = new ArrayList<>(KiloDeck.kiloPile(PLAYERS));
        Collections.shuffle(pile, random);
        List<List<Card>> kiloCards = new ArrayList<>();
        for (int seat = 0; seat < PLAYERS; seat++) {
            kiloCards.add(new ArrayList<>());
        }
        for (int round = 1; round <= ROUNDS && !pile.isEmpty(); round++) {
            List<Card> deck = new ArrayList<>(KiloDeck.handDeck(PLAYERS));
            Collections.shuffle(deck, random);
            List<List<Card>> hands = new ArrayList<>();
            List<List<KiloDeMerdeBotView.Pass>> histories = new ArrayList<>();
            for (int seat = 0; seat < PLAYERS; seat++) {
                hands.add(new ArrayList<>(deck.subList(seat * 4, seat * 4 + 4)));
                histories.add(new ArrayList<>());
            }
            int step = 0;
            while (!hasFour(hands) && step < KiloDeMerdeRules.STALL_LIMIT) {
                step++;
                List<Card> given = new ArrayList<>();
                for (int seat = 0; seat < PLAYERS; seat++) {
                    given.add(bots.get(seat).choosePass(view(seat, round, step, hands.get(seat), histories.get(seat))));
                }
                for (int seat = 0; seat < PLAYERS; seat++) {
                    hands.get(seat).remove(given.get(seat));
                }
                for (int seat = 0; seat < PLAYERS; seat++) {
                    int from = (seat + 1) % PLAYERS;
                    hands.get(seat).add(given.get(from));
                    histories.get(seat).add(new KiloDeMerdeBotView.Pass(step, given.get(seat), given.get(from)));
                }
            }
            if (hasFour(hands)) {
                int drawer = lastSlapper(levels, hands, reflex, random);
                Card drawn = pile.remove(0);
                if (KiloValue.isFlush(drawn)) {
                    pile.addAll(kiloCards.get(drawer));
                    pile.add(drawn);
                    kiloCards.get(drawer).clear();
                    Collections.shuffle(pile, random);
                } else {
                    kiloCards.get(drawer).add(drawn);
                }
            }
        }
        return kiloCards.stream().mapToInt(KiloValue::total).toArray();
    }

    private static boolean hasFour(List<List<Card>> hands) {
        return KiloDeMerdeRules.anyFourOfAKind(hands);
    }

    private static int lastSlapper(List<KiloDeMerdeBotLevel> levels, List<List<Card>> hands, KiloDeMerdeReflex reflex,
                                   Random random) {
        double[] own = new double[PLAYERS];
        double first = Double.MAX_VALUE;
        for (int seat = 0; seat < PLAYERS; seat++) {
            if (KiloDeMerdeRules.isFourOfAKind(hands.get(seat))) {
                own[seat] = reflex.ownSetDelayMs(levels.get(seat), random);
                first = Math.min(first, own[seat]);
            }
        }
        int last = -1;
        double lastTime = -1;
        for (int seat = 0; seat < PLAYERS; seat++) {
            double time = KiloDeMerdeRules.isFourOfAKind(hands.get(seat))
                    ? own[seat]
                    : first + reflex.followDelayMs(levels.get(seat), random);
            if (time > lastTime) {
                lastTime = time;
                last = seat;
            }
        }
        return last;
    }

    private static KiloDeMerdeBotView view(int seat, int round, int step, List<Card> hand,
                                           List<KiloDeMerdeBotView.Pass> history) {
        List<org.kevinkib.cardgames.game.PlayerId> seats = new ArrayList<>();
        Map<org.kevinkib.cardgames.game.PlayerId, Integer> kilos = new java.util.HashMap<>();
        for (int i = 0; i < PLAYERS; i++) {
            seats.add(new org.kevinkib.cardgames.game.PlayerId(i));
            kilos.put(seats.get(i), 0);
        }
        return new KiloDeMerdeBotView(seats.get(seat), step, round, step,
                org.kevinkib.cardgames.kilodemerde.domain.Phase.PASSING, hand, history, kilos, seats, List.of(), 0,
                org.kevinkib.cardgames.kilodemerde.domain.KiloDeMerdeOptions.defaults());
    }
}
