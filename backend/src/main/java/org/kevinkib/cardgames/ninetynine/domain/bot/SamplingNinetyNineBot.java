package org.kevinkib.cardgames.ninetynine.domain.bot;

import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.CardEffect;
import org.kevinkib.cardgames.ninetynine.domain.Direction;
import org.kevinkib.cardgames.ninetynine.domain.bot.NinetyNineMoves.Move;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.LongSupplier;

/**
 * The {@code hard} level: a modest determinised Monte Carlo, not a search. For each candidate move it samples deals
 * of the unseen cards (the deck minus its own hand and the public pile) into the other hands and the stock, lets
 * greedy opponents play one round, and scores the move: the chance that the next seat in the resulting direction
 * is stuck (worth more against a seat with many lives), minus the chance that the bot itself is stuck on its next
 * turn (worth more when its own lives are few), minus a small cost for spending an escape card. Every candidate is
 * scored on the same sampled deals. Sampling stops at the cap, with at least one deal done.
 */
public class SamplingNinetyNineBot implements NinetyNineBotStrategy {

    private final Random random;
    private final int samples;
    private final long capNanos;
    private final LongSupplier clock;
    private int lastSampleCount;

    public SamplingNinetyNineBot(Random random) {
        this(random, NinetyNineBotTuning.SAMPLES, NinetyNineBotTuning.CAP_NANOS, System::nanoTime);
    }

    /** @param clock the time source in nanoseconds, injected so the cap can be tested */
    SamplingNinetyNineBot(Random random, int samples, long capNanos, LongSupplier clock) {
        this.random = random;
        this.samples = samples;
        this.capNanos = capNanos;
        this.clock = clock;
    }

    /** How many deals the last decision sampled. */
    int lastSampleCount() {
        return lastSampleCount;
    }

    @Override
    public NinetyNineDecision decide(NinetyNineBotView view) {
        List<Move> moves = NinetyNineMoves.of(view.total(), view.hand());
        if (moves.isEmpty()) {
            throw new IllegalStateException("No safe card to play at " + view.total());
        }
        lastSampleCount = 0;
        if (moves.size() == 1) {
            return moves.get(0).toDecision();
        }
        List<Card> unseen = unseenCards(view);
        List<PlayerId> others = view.liveSeats().stream().filter(seat -> !seat.equals(view.seat())).toList();
        double[] scores = new double[moves.size()];
        long deadline = clock.getAsLong() + capNanos;
        int done = 0;
        while (done < samples && (done == 0 || clock.getAsLong() < deadline)) {
            Collections.shuffle(unseen, random);
            for (int i = 0; i < moves.size(); i++) {
                scores[i] += simulate(view, moves.get(i), deal(view, others, unseen));
            }
            done++;
        }
        lastSampleCount = done;
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < moves.size(); i++) {
            double score = scores[i] / done
                    - (NinetyNineMoves.isEscape(moves.get(i).card()) ? NinetyNineBotTuning.ESCAPE_CARD_COST : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return moves.get(best).toDecision();
    }

    private static List<Card> unseenCards(NinetyNineBotView view) {
        List<Card> unseen = new ArrayList<>();
        for (FrenchRank rank : FrenchRank.values()) {
            if (rank == FrenchRank.JOKER) {
                continue;
            }
            for (FrenchSuit suit : FrenchSuit.getSuits()) {
                Card card = new Card(rank, suit);
                if (!view.hand().contains(card) && !view.pileSinceReshuffle().contains(card)) {
                    unseen.add(card);
                }
            }
        }
        return unseen;
    }

    /** A sampled deal: the other hands (as mutable lists) and the stock, cut from the shuffled unseen cards. */
    private record Deal(Map<PlayerId, List<Card>> hands, List<Card> stock) {
    }

    private static Deal deal(NinetyNineBotView view, List<PlayerId> others, List<Card> shuffled) {
        Map<PlayerId, List<Card>> hands = new HashMap<>();
        int next = 0;
        for (PlayerId seat : others) {
            int size = Math.min(view.handSizes().getOrDefault(seat, 0), shuffled.size() - next);
            hands.put(seat, new ArrayList<>(shuffled.subList(next, next + size)));
            next += size;
        }
        return new Deal(hands, new ArrayList<>(shuffled.subList(next, shuffled.size())));
    }

    /** The score of one move on one deal: one round of greedy opponents after the move. */
    private double simulate(NinetyNineBotView view, Move move, Deal deal) {
        List<PlayerId> live = view.liveSeats();
        int me = live.indexOf(view.seat());
        int total = move.totalAfter();
        Direction direction = CardEffect.reverses(move.card()) ? view.direction().reversed() : view.direction();
        List<Card> mine = new ArrayList<>(view.hand());
        mine.remove(move.card());
        draw(mine, deal.stock());

        int maxLives = live.stream().mapToInt(seat -> view.lives().getOrDefault(seat, 1)).max().orElse(1);
        int current = step(me, direction, live.size());
        boolean first = true;
        for (int turns = 0; current != me && turns < 2 * live.size(); turns++) {
            PlayerId seat = live.get(current);
            List<Card> hand = deal.hands().get(seat);
            Move reply = GreedyNinetyNineBot.pick(total, hand);
            if (reply == null) {
                // the seat is stuck and busts: the round ends without the bot being hurt
                double ratio = (double) view.lives().getOrDefault(seat, 1) / maxLives;
                return first ? 0.5 + 0.5 * ratio : 0;
            }
            hand.remove(reply.card());
            draw(hand, deal.stock());
            total = reply.totalAfter();
            if (CardEffect.reverses(reply.card())) {
                direction = direction.reversed();
            }
            first = false;
            current = step(current, direction, live.size());
        }
        if (current == me && CardEffect.safeCards(total, mine).isEmpty()) {
            return -(1 + 1.0 / view.lives().getOrDefault(view.seat(), 1));
        }
        return 0;
    }

    private static void draw(List<Card> hand, List<Card> stock) {
        if (!stock.isEmpty()) {
            hand.add(stock.remove(stock.size() - 1));
        }
    }

    private static int step(int index, Direction direction, int size) {
        return direction == Direction.CLOCKWISE ? (index + 1) % size : (index + size - 1) % size;
    }
}
