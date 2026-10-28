package org.kevinkib.cardgames.blackjack.domain.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kevinkib.cardgames.blackjack.domain.Action;
import org.kevinkib.cardgames.blackjack.domain.BlackjackOptions;
import org.kevinkib.cardgames.blackjack.domain.Phase;
import org.kevinkib.cardgames.game.PlayerId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Opt-in strength benchmark, not run by default:
 * {@code mvn test -Dtest=BlackjackBotStrengthBenchmark -Dblackjack.bench=true}.
 *
 * <p>Plays tournaments of four bots, 10 hands, default options, seats rotated tournament after tournament so the seat
 * order favours nobody, and prints the share of tournaments each level wins (a tie for first splits the win); a fair
 * seat wins 25%. It runs on a small simulator of the rules written here (6-deck shoe shuffled every hand, dealer
 * peek, 3:2 blackjack, S17, double, one split with no resplit, split Aces one card each), not on the aggregate,
 * which lands in another chunk.
 */
@EnabledIfSystemProperty(named = "blackjack.bench", matches = "true")
class BlackjackBotStrengthBenchmark {

    private static final int TOURNAMENTS = Integer.getInteger("blackjack.bench.tournaments", 1000);
    private static final int SEATS = 4;
    private static final int HANDS = 10;
    private static final BlackjackOptions OPTIONS = BlackjackOptions.defaults();

    @Test
    void winShareOfEachLevel() {
        report("1 hard vs 3 medium", BlackjackBotLevel.HARD, BlackjackBotLevel.MEDIUM, BlackjackBotLevel.MEDIUM,
                BlackjackBotLevel.MEDIUM);
        report("1 medium vs 3 easy", BlackjackBotLevel.MEDIUM, BlackjackBotLevel.EASY, BlackjackBotLevel.EASY,
                BlackjackBotLevel.EASY);
        report("hard + medium + 2 easy", BlackjackBotLevel.HARD, BlackjackBotLevel.MEDIUM, BlackjackBotLevel.EASY,
                BlackjackBotLevel.EASY);
        report("4 medium (control)", BlackjackBotLevel.MEDIUM, BlackjackBotLevel.MEDIUM, BlackjackBotLevel.MEDIUM,
                BlackjackBotLevel.MEDIUM);
    }

    private void report(String name, BlackjackBotLevel... levels) {
        Random random = new Random(2026);
        Map<BlackjackBotLevel, double[]> wins = new EnumMap<>(BlackjackBotLevel.class);
        for (int t = 0; t < TOURNAMENTS; t++) {
            int shift = t % SEATS;
            List<BlackjackBotLevel> table = new ArrayList<>();
            for (int seat = 0; seat < SEATS; seat++) {
                table.add(levels[(seat + shift) % SEATS]);
            }
            double[] share = playTournament(table, random);
            for (int seat = 0; seat < SEATS; seat++) {
                double[] sum = wins.computeIfAbsent(table.get(seat), level -> new double[2]);
                sum[0] += share[seat];
                sum[1]++;
            }
        }
        StringBuilder line = new StringBuilder(String.format("%-26s", name));
        for (Map.Entry<BlackjackBotLevel, double[]> entry : wins.entrySet()) {
            double[] sum = entry.getValue();
            line.append(String.format("  %s %.1f%% per seat", entry.getKey().key(), 100 * sum[0] / sum[1]));
        }
        System.out.println(line);
    }

    // ---- simulator

    private static final class HandState {
        final List<Integer> cards = new ArrayList<>();
        int bet;
        boolean doubled;
        boolean fromSplit;
        boolean splitAce;
        boolean done;
    }

    private static final class Seat {
        final PlayerId id;
        final BlackjackBotStrategy bot;
        int stack = BlackjackOptions.STARTING_STACK;
        boolean live = true;
        boolean split;
        final List<HandState> hands = new ArrayList<>();

        Seat(int index, BlackjackBotLevel level, Random random) {
            this.id = new PlayerId(index);
            this.bot = BlackjackBots.forLevel(level, random);
        }
    }

    /** The share of the tournament each seat wins: 1 for the sole best final stack, split on a tie. */
    private double[] playTournament(List<BlackjackBotLevel> levels, Random random) {
        List<Seat> seats = new ArrayList<>();
        for (int i = 0; i < levels.size(); i++) {
            seats.add(new Seat(i, levels.get(i), random));
        }
        int button = 0;
        for (int hand = 1; hand <= HANDS && liveCount(seats) > 1; hand++) {
            playHand(seats, button, HANDS - hand + 1, hand, random);
            for (Seat seat : seats) {
                if (seat.live && seat.stack == 0) {
                    seat.live = false;
                }
            }
            if (liveCount(seats) > 1) {
                do {
                    button = (button + 1) % seats.size();
                } while (!seats.get(button).live);
            }
        }
        int best = seats.stream().filter(s -> s.live).mapToInt(s -> s.stack).max().orElse(0);
        long tied = seats.stream().filter(s -> s.live && s.stack == best).count();
        double[] share = new double[seats.size()];
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            if (seat.live && seat.stack == best && tied > 0) {
                share[i] = 1.0 / tied;
            }
        }
        return share;
    }

    private static long liveCount(List<Seat> seats) {
        return seats.stream().filter(s -> s.live).count();
    }

    private void playHand(List<Seat> seats, int button, int handsLeft, int handNumber, Random random) {
        List<Seat> order = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get((button + i) % seats.size());
            if (seat.live) {
                order.add(seat);
            }
        }
        List<Integer> shoe = newShoe(random);
        int[] next = {0};

        for (Seat seat : order) {
            seat.hands.clear();
            seat.split = false;
            int maxBet = Math.min(OPTIONS.maxBet().amount(), seat.stack);
            BlackjackBotView view = new BlackjackBotView(seat.id, handNumber, handNumber, handsLeft, Phase.BETTING, 0,
                    List.of(), 0, List.of(Action.BET), BlackjackOptions.MIN_BET, maxBet, stacks(order),
                    order.get(0).id, order.size() == 1, OPTIONS);
            int bet = seat.bot.decideBet(view);
            HandState state = new HandState();
            state.bet = bet;
            seat.stack -= bet;
            seat.hands.add(state);
        }
        for (Seat seat : order) {
            seat.hands.get(0).cards.add(shoe.get(next[0]++));
        }
        int up = shoe.get(next[0]++);
        for (Seat seat : order) {
            seat.hands.get(0).cards.add(shoe.get(next[0]++));
        }
        List<Integer> dealer = new ArrayList<>(List.of(up, shoe.get(next[0]++)));

        boolean dealerBlackjack = (up >= 10) && total(dealer) == 21;
        if (!dealerBlackjack) {
            for (Seat seat : order) {
                playSeat(seat, order, up, handsLeft, handNumber, shoe, next);
            }
            boolean anyLive = order.stream().flatMap(s -> s.hands.stream())
                    .anyMatch(h -> total(h.cards) <= 21 && !isBlackjack(h));
            if (anyLive) {
                while (total(dealer) < 17) {
                    dealer.add(shoe.get(next[0]++));
                }
            }
        }
        int dealerTotal = total(dealer);
        for (Seat seat : order) {
            for (HandState h : seat.hands) {
                seat.stack += h.bet + net(h, dealerBlackjack, dealerTotal);
            }
        }
    }

    private static int net(HandState h, boolean dealerBlackjack, int dealerTotal) {
        int total = total(h.cards);
        if (dealerBlackjack) {
            return isBlackjack(h) ? 0 : -h.bet;
        }
        if (total > 21) {
            return -h.bet;
        }
        if (isBlackjack(h)) {
            return 3 * h.bet / 2;
        }
        if (dealerTotal > 21 || total > dealerTotal) {
            return h.bet;
        }
        return total == dealerTotal ? 0 : -h.bet;
    }

    private void playSeat(Seat seat, List<Seat> order, int up, int handsLeft, int handNumber, List<Integer> shoe,
                          int[] next) {
        HandState first = seat.hands.get(0);
        if (isBlackjack(first)) {
            return;
        }
        for (int index = 0; index < seat.hands.size(); index++) {
            HandState h = seat.hands.get(index);
            if (h.cards.size() == 1) {
                h.cards.add(shoe.get(next[0]++));
            }
            while (!h.done && !h.splitAce && total(h.cards) < 21) {
                boolean pair = h.cards.size() == 2 && !seat.split && value(h.cards.get(0)) == value(h.cards.get(1));
                List<Action> actions = new ArrayList<>(List.of(Action.HIT, Action.STAND));
                if (h.cards.size() == 2 && seat.stack >= h.bet) {
                    actions.add(Action.DOUBLE);
                    if (pair) {
                        actions.add(Action.SPLIT);
                    }
                }
                BlackjackBotView view = new BlackjackBotView(seat.id, handNumber, handNumber, handsLeft,
                        Phase.PLAYING, index, handViews(seat), value(up), actions, 0, 0, stacks(order),
                        order.get(0).id, order.size() == 1, OPTIONS);
                Action action = seat.bot.decidePlay(view);
                switch (action) {
                    case HIT -> h.cards.add(shoe.get(next[0]++));
                    case STAND -> h.done = true;
                    case DOUBLE -> {
                        seat.stack -= h.bet;
                        h.bet *= 2;
                        h.doubled = true;
                        h.cards.add(shoe.get(next[0]++));
                        h.done = true;
                    }
                    case SPLIT -> split(seat, h, shoe, next);
                    default -> throw new IllegalStateException("Unexpected " + action);
                }
            }
        }
    }

    private void split(Seat seat, HandState h, List<Integer> shoe, int[] next) {
        seat.stack -= h.bet;
        seat.split = true;
        HandState second = new HandState();
        second.bet = h.bet;
        second.fromSplit = true;
        h.fromSplit = true;
        second.cards.add(h.cards.remove(1));
        h.cards.add(shoe.get(next[0]++));
        if (h.cards.get(0) == 11) {
            h.splitAce = true;
            second.splitAce = true;
            second.cards.add(shoe.get(next[0]++));
        }
        seat.hands.add(second);
    }

    private static List<BlackjackBotView.Hand> handViews(Seat seat) {
        List<BlackjackBotView.Hand> views = new ArrayList<>();
        for (HandState h : seat.hands) {
            boolean pair = h.cards.size() == 2 && !seat.split && value(h.cards.get(0)) == value(h.cards.get(1));
            views.add(new BlackjackBotView.Hand(total(h.cards), soft(h.cards), pair, h.bet, h.doubled));
        }
        return views;
    }

    private static Map<PlayerId, Integer> stacks(List<Seat> order) {
        Map<PlayerId, Integer> map = new LinkedHashMap<>();
        for (Seat s : order) {
            map.put(s.id, s.stack);
        }
        return map;
    }

    private static List<Integer> newShoe(Random random) {
        List<Integer> shoe = new ArrayList<>(312);
        for (int deck = 0; deck < 6; deck++) {
            for (int suit = 0; suit < 4; suit++) {
                for (int rank = 2; rank <= 11; rank++) {
                    int copies = rank == 10 ? 4 : 1;
                    for (int c = 0; c < copies; c++) {
                        shoe.add(rank);
                    }
                }
            }
        }
        Collections.shuffle(shoe, random);
        return shoe;
    }

    private static int value(int card) {
        return card;
    }

    private static boolean isBlackjack(HandState h) {
        return !h.fromSplit && h.cards.size() == 2 && total(h.cards) == 21;
    }

    private static int total(List<Integer> cards) {
        int sum = cards.stream().mapToInt(Integer::intValue).sum();
        int aces = (int) cards.stream().filter(c -> c == 11).count();
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }

    private static boolean soft(List<Integer> cards) {
        int sum = cards.stream().mapToInt(Integer::intValue).sum();
        int aces = (int) cards.stream().filter(c -> c == 11).count();
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return aces > 0;
    }
}
