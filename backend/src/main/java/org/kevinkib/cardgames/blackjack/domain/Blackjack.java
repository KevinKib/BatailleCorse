package org.kevinkib.cardgames.blackjack.domain;

import org.kevinkib.cardgames.blackjack.domain.event.BetPlaced;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackEvent;
import org.kevinkib.cardgames.blackjack.domain.event.BlackjackResult;
import org.kevinkib.cardgames.blackjack.domain.event.Busted;
import org.kevinkib.cardgames.blackjack.domain.event.CardDrawn;
import org.kevinkib.cardgames.blackjack.domain.event.DealerBlackjack;
import org.kevinkib.cardgames.blackjack.domain.event.DealerPlayed;
import org.kevinkib.cardgames.blackjack.domain.event.Doubled;
import org.kevinkib.cardgames.blackjack.domain.event.Eliminated;
import org.kevinkib.cardgames.blackjack.domain.event.Forfeited;
import org.kevinkib.cardgames.blackjack.domain.event.GameFinished;
import org.kevinkib.cardgames.blackjack.domain.event.HandDealt;
import org.kevinkib.cardgames.blackjack.domain.event.HandSettled;
import org.kevinkib.cardgames.blackjack.domain.event.SplitHand;
import org.kevinkib.cardgames.blackjack.domain.event.Stood;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A Blackjack game: one to six players who each play their own hands against the same automated dealer, over a
 * fixed number of hands, with sealed simultaneous bets (spec sections 3 and 6). One seat is a solo game, two or
 * more a tournament ranked by points. Every shuffle comes from the {@link ShoeSource}.
 *
 * <p>All state changes go through synchronized methods; a rejected command throws a {@link BlackjackException} and
 * changes nothing (the version does not move).
 */
public class Blackjack implements Game {

    public static final int MIN_PLAYERS = 1;
    public static final int MAX_PLAYERS = 6;

    private static final int BLACKJACK = 21;

    /** The mutable table state of one seat; the outside world only sees {@link SeatState} snapshots. */
    private static final class Seat {
        final PlayerId id;
        int stack;
        int bet;
        boolean betPlaced;
        int lastBet;
        List<PlayerHand> hands = new ArrayList<>();
        int activeHand;
        int eliminatedAtHand;
        int forfeitOrder;
        Integer place;

        Seat(PlayerId id, int stack) {
            this.id = id;
            this.stack = stack;
        }

        boolean out() {
            return eliminatedAtHand > 0 || forfeitOrder > 0;
        }

        /** Points of this seat that are on the table right now: a placed bet or the bets of its hands. */
        int committed(Phase phase) {
            if (phase == Phase.BETTING) {
                return betPlaced ? bet : 0;
            }
            return hands.stream().mapToInt(PlayerHand::bet).sum();
        }
    }

    private final GameId id;
    private final BlackjackOptions options;
    private final ShoeSource shoeSource;
    private final List<Seat> seats = new ArrayList<>();
    private final List<Card> dealerCards = new ArrayList<>();
    private Shoe shoe;
    private boolean holeShown;
    private Phase phase = Phase.BETTING;
    private int handNumber;
    private PlayerId button;
    private PlayerId currentSeat;
    private int version;
    private int forfeitCount;
    private LastHand lastHand;
    private BlackjackResult result;
    /** What the last {@link #forfeit(PlayerId)} changed, for the presentation layer to announce. */
    private List<BlackjackEvent> lastForfeitEvents = List.of();

    public Blackjack(GameId id, int nbPlayers, ShoeSource shoeSource, BlackjackOptions options) {
        this(id, options, shoeSource, startingStacks(nbPlayers), 1, 0);
    }

    /** Test constructor: an explicit state, in the betting phase of {@code handNumber} with the given button. */
    Blackjack(GameId id, BlackjackOptions options, ShoeSource shoeSource, List<Integer> stacks, int handNumber,
              int button) {
        if (stacks.size() < MIN_PLAYERS || stacks.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("Blackjack is played by 1 to 6 players, not " + stacks.size());
        }
        this.id = id;
        this.options = options;
        this.shoeSource = shoeSource;
        this.handNumber = handNumber;
        for (int i = 0; i < stacks.size(); i++) {
            Seat seat = new Seat(new PlayerId(i), stacks.get(i));
            if (seat.stack == 0) {
                seat.eliminatedAtHand = Math.max(1, handNumber - 1);
            }
            seats.add(seat);
        }
        this.button = seats.get(button).id;
    }

    private static List<Integer> startingStacks(int nbPlayers) {
        if (nbPlayers < MIN_PLAYERS || nbPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("Blackjack is played by 1 to 6 players, not " + nbPlayers);
        }
        return Collections.nCopies(nbPlayers, BlackjackOptions.STARTING_STACK);
    }

    // ---- commands ----

    /**
     * Places the bet of a seat for the hand. The bet leaves the stack at once. When the last live seat has bet, the
     * cards are dealt in the same call.
     */
    public synchronized List<BlackjackEvent> bet(PlayerId seat, int amount) throws BlackjackException {
        requireNotFinished();
        requirePhase(Phase.BETTING);
        Seat s = requireLive(seat);
        if (s.betPlaced) {
            throw new AlreadyBetException();
        }
        int max = maxBetOf(s);
        if (amount < BlackjackOptions.MIN_BET || amount > max) {
            throw new InvalidBetException(amount, BlackjackOptions.MIN_BET, max);
        }
        List<BlackjackEvent> events = new ArrayList<>();
        s.stack -= amount;
        s.bet = amount;
        s.betPlaced = true;
        s.lastBet = amount;
        events.add(new BetPlaced(seat));
        if (allLiveSeatsHaveBet()) {
            deal(events);
        }
        version++;
        return events;
    }

    /** Takes one card on the seat's active hand. */
    public synchronized List<BlackjackEvent> hit(PlayerId seat) throws BlackjackException {
        Seat s = requireTurn(seat);
        List<BlackjackEvent> events = new ArrayList<>();
        int index = s.activeHand;
        Card card = shoe.draw();
        PlayerHand hand = s.hands.get(index).withCard(card);
        HandValue value = hand.value();
        events.add(new CardDrawn(seat, index, card, value.total()));
        if (value.bust()) {
            hand = hand.withStatus(PlayerHand.Status.BUST);
            events.add(new Busted(seat, index));
        } else if (value.total() == BLACKJACK) {
            hand = hand.withStatus(PlayerHand.Status.STOOD);
            events.add(new Stood(seat, index));
        }
        s.hands.set(index, hand);
        afterHandAction(s, events);
        version++;
        return events;
    }

    /** Ends the seat's active hand with the cards it holds. */
    public synchronized List<BlackjackEvent> stand(PlayerId seat) throws BlackjackException {
        Seat s = requireTurn(seat);
        List<BlackjackEvent> events = new ArrayList<>();
        int index = s.activeHand;
        s.hands.set(index, s.hands.get(index).withStatus(PlayerHand.Status.STOOD));
        events.add(new Stood(seat, index));
        afterHandAction(s, events);
        version++;
        return events;
    }

    /** Doubles the active hand's bet, takes exactly one card and stands. Only on the first two cards. */
    public synchronized List<BlackjackEvent> doubleDown(PlayerId seat) throws BlackjackException {
        Seat s = requireTurn(seat);
        requireAvailable(s, Action.DOUBLE);
        List<BlackjackEvent> events = new ArrayList<>();
        int index = s.activeHand;
        PlayerHand hand = s.hands.get(index);
        s.stack -= hand.bet();
        Card card = shoe.draw();
        hand = hand.doubledWith(card);
        events.add(new Doubled(seat, index, card));
        if (hand.value().bust()) {
            hand = hand.withStatus(PlayerHand.Status.BUST);
            events.add(new Busted(seat, index));
        } else {
            hand = hand.withStatus(PlayerHand.Status.STOOD);
        }
        s.hands.set(index, hand);
        afterHandAction(s, events);
        version++;
        return events;
    }

    /**
     * Splits a pair into two hands with a bet each. The first hand gets its second card at once; the second gets
     * its own when the first is finished. Split Aces get one card each and both stand.
     */
    public synchronized List<BlackjackEvent> split(PlayerId seat) throws BlackjackException {
        Seat s = requireTurn(seat);
        requireAvailable(s, Action.SPLIT);
        List<BlackjackEvent> events = new ArrayList<>();
        PlayerHand pair = s.hands.get(0);
        s.stack -= pair.bet();
        boolean aces = HandValue.of(List.of(pair.cards().get(0)), false).soft();
        PlayerHand first = new PlayerHand(List.of(pair.cards().get(0)), pair.bet(), false, true, aces,
                PlayerHand.Status.ACTIVE).withCard(shoe.draw());
        PlayerHand second = new PlayerHand(List.of(pair.cards().get(1)), pair.bet(), false, true, aces,
                PlayerHand.Status.WAITING);
        if (aces) {
            second = second.withCard(shoe.draw());
            first = first.withStatus(PlayerHand.Status.STOOD);
            second = second.withStatus(PlayerHand.Status.STOOD);
        } else if (first.value().total() == BLACKJACK) {
            first = first.withStatus(PlayerHand.Status.STOOD);
        }
        List<Card> cards = new ArrayList<>(first.cards());
        cards.addAll(second.cards());
        events.add(new SplitHand(seat, cards));
        if (first.status() == PlayerHand.Status.STOOD) {
            events.add(new Stood(seat, 0));
        }
        if (second.status() == PlayerHand.Status.STOOD) {
            events.add(new Stood(seat, 1));
        }
        s.hands = new ArrayList<>(List.of(first, second));
        s.activeHand = 0;
        afterHandAction(s, events);
        version++;
        return events;
    }

    @Override
    public synchronized void forfeit(PlayerId loser) {
        lastForfeitEvents = forfeitWithEvents(loser);
    }

    /**
     * The events of the last {@link #forfeit(PlayerId)}, empty when it changed nothing. The {@code Game} contract
     * returns nothing, so the lifecycle broadcaster reads them here to announce them.
     */
    public synchronized List<BlackjackEvent> lastForfeitEvents() {
        return lastForfeitEvents;
    }

    /**
     * Same as {@link #forfeit(PlayerId)}, returning what changed: the seat is eliminated at once, its placed bet is
     * lost, its hands leave the table and the game goes on without it (the turn moves on, the deal no longer waits
     * for its bet). When no more than one live seat is left, or in a solo game, the game ends. A seat already out,
     * an unknown seat, or a finished game change nothing.
     */
    public synchronized List<BlackjackEvent> forfeitWithEvents(PlayerId loser) {
        Seat s = find(loser);
        if (phase == Phase.FINISHED || s == null || s.out()) {
            return List.of();
        }
        List<BlackjackEvent> events = new ArrayList<>();
        events.add(new Forfeited(loser));
        boolean wasOnTurn = phase == Phase.PLAYING && loser.equals(currentSeat);
        s.forfeitOrder = ++forfeitCount;
        s.hands = new ArrayList<>();
        s.activeHand = 0;
        s.bet = 0;
        s.betPlaced = false;
        if (isSolo() || liveSeats().size() <= 1) {
            refundCommittedBets();
            finish(BlackjackResult.Reason.FORFEIT, events);
        } else if (phase == Phase.BETTING) {
            if (allLiveSeatsHaveBet()) {
                deal(events);
            }
        } else if (wasOnTurn) {
            moveToNextActor(loser, events);
        }
        version++;
        return events;
    }

    // ---- turn flow ----

    private void deal(List<BlackjackEvent> events) {
        List<Seat> order = liveSeatsFromButton();
        button = order.get(0).id;
        shoe = new Shoe(shoeSource.shuffledShoe(id, handNumber));
        dealerCards.clear();
        holeShown = false;
        for (Seat s : order) {
            s.hands = new ArrayList<>(List.of(new PlayerHand(List.of(), s.bet, false, false, false,
                    PlayerHand.Status.ACTIVE)));
            s.activeHand = 0;
        }
        for (Seat s : order) {
            s.hands.set(0, s.hands.get(0).withCard(shoe.draw()));
        }
        Card up = shoe.draw();
        dealerCards.add(up);
        for (Seat s : order) {
            s.hands.set(0, s.hands.get(0).withCard(shoe.draw()));
        }
        Card hole = shoe.draw();
        dealerCards.add(hole);
        Map<PlayerId, Integer> bets = new LinkedHashMap<>();
        Map<PlayerId, List<Card>> seatCards = new LinkedHashMap<>();
        for (Seat s : order) {
            PlayerHand hand = s.hands.get(0);
            if (hand.value().blackjack()) {
                s.hands.set(0, hand.withStatus(PlayerHand.Status.BLACKJACK));
            }
            bets.put(s.id, s.bet);
            seatCards.put(s.id, hand.cards());
        }
        phase = Phase.PLAYING;
        events.add(new HandDealt(handNumber, button, bets, up, seatCards));
        if (HandValue.of(List.of(up), false).total() >= 10 && HandValue.of(dealerCards, false).blackjack()) {
            holeShown = true;
            events.add(new DealerBlackjack(hole));
            settle(events);
        } else {
            moveToNextActor(null, events);
        }
    }

    /** After a command on the active hand: stay on it, go to the second split hand, or pass the turn on. */
    private void afterHandAction(Seat s, List<BlackjackEvent> events) {
        if (s.hands.get(s.activeHand).status() == PlayerHand.Status.ACTIVE) {
            return;
        }
        if (s.activeHand == 0 && s.hands.size() == 2 && s.hands.get(1).status() == PlayerHand.Status.WAITING) {
            s.activeHand = 1;
            Card card = shoe.draw();
            PlayerHand second = s.hands.get(1).withCard(card);
            int total = second.value().total();
            events.add(new CardDrawn(s.id, 1, card, total));
            if (total == BLACKJACK) {
                second = second.withStatus(PlayerHand.Status.STOOD);
                events.add(new Stood(s.id, 1));
            } else {
                second = second.withStatus(PlayerHand.Status.ACTIVE);
            }
            s.hands.set(1, second);
            if (second.status() == PlayerHand.Status.ACTIVE) {
                return;
            }
        }
        moveToNextActor(s.id, events);
    }

    /** The next seat after {@code after} (the first one when null) with a hand to play, else the dealer plays. */
    private void moveToNextActor(PlayerId after, List<BlackjackEvent> events) {
        List<Seat> order = seatsFromButton();
        int start = after == null ? -1 : indexOf(order, after);
        for (int i = start + 1; i < order.size(); i++) {
            Seat s = order.get(i);
            if (!s.hands.isEmpty() && s.hands.get(0).status() == PlayerHand.Status.ACTIVE) {
                currentSeat = s.id;
                s.activeHand = 0;
                return;
            }
        }
        currentSeat = null;
        playDealer(events);
    }

    private void playDealer(List<BlackjackEvent> events) {
        holeShown = true;
        boolean someoneToBeat = seats.stream().flatMap(s -> s.hands.stream())
                .anyMatch(h -> h.status() != PlayerHand.Status.BUST && h.status() != PlayerHand.Status.BLACKJACK);
        List<Card> drawn = new ArrayList<>();
        if (someoneToBeat) {
            while (DealerRule.mustDraw(HandValue.of(dealerCards, false))) {
                Card card = shoe.draw();
                dealerCards.add(card);
                drawn.add(card);
            }
        }
        events.add(new DealerPlayed(dealerCards.get(1), drawn, HandValue.of(dealerCards, false).total()));
        settle(events);
    }

    private void settle(List<BlackjackEvent> events) {
        HandValue dealer = HandValue.of(dealerCards, false);
        Map<PlayerId, Integer> nets = new LinkedHashMap<>();
        Map<PlayerId, List<LastHand.SettledHand>> settledHands = new LinkedHashMap<>();
        for (Seat s : seats) {
            if (s.hands.isEmpty()) {
                continue;
            }
            int total = 0;
            List<LastHand.SettledHand> settled = new ArrayList<>();
            for (PlayerHand hand : s.hands) {
                int net = Settlement.net(hand, dealer);
                s.stack += hand.bet() + net;
                total += net;
                settled.add(new LastHand.SettledHand(hand, net));
            }
            nets.put(s.id, total);
            settledHands.put(s.id, settled);
        }
        List<PlayerId> best = Settlement.bestSeats(nets);
        events.add(new HandSettled(handNumber, nets, stacks(), best));
        lastHand = new LastHand(handNumber, dealerCards, settledHands, nets, best);
        currentSeat = null;
        for (Seat s : seats) {
            if (!s.out() && s.stack == 0) {
                s.eliminatedAtHand = handNumber;
                events.add(new Eliminated(s.id, handNumber));
            }
        }
        if (isSolo()) {
            if (seats.get(0).out()) {
                finish(BlackjackResult.Reason.BROKE, events);
            } else if (handNumber >= handsTotal()) {
                finish(BlackjackResult.Reason.HANDS_PLAYED, events);
            } else {
                openNextHand();
            }
        } else if (liveSeats().size() <= 1) {
            finish(BlackjackResult.Reason.LAST_STANDING, events);
        } else if (handNumber >= handsTotal()) {
            finish(BlackjackResult.Reason.HANDS_PLAYED, events);
        } else {
            openNextHand();
        }
    }

    private void openNextHand() {
        handNumber++;
        List<Seat> order = seatsFromButton();
        button = order.stream().skip(1).filter(s -> !s.out()).findFirst().orElse(order.get(0)).id;
        for (Seat s : seats) {
            s.bet = 0;
            s.betPlaced = false;
            s.hands = new ArrayList<>();
            s.activeHand = 0;
        }
        dealerCards.clear();
        holeShown = false;
        shoe = null;
        currentSeat = null;
        phase = Phase.BETTING;
    }

    /** A forfeit that ends the game gives the surviving seats the points they had on the unfinished hand. */
    private void refundCommittedBets() {
        for (Seat s : seats) {
            if (!s.out()) {
                s.stack += s.committed(phase);
                s.bet = 0;
                s.betPlaced = false;
                s.hands = new ArrayList<>();
                s.activeHand = 0;
            }
        }
    }

    private void finish(BlackjackResult.Reason reason, List<BlackjackEvent> events) {
        Map<PlayerId, Integer> places = isSolo() ? Map.of() : ranking();
        places.forEach((seat, place) -> find(seat).place = place);
        result = new BlackjackResult(places, stacks(), reason);
        phase = Phase.FINISHED;
        currentSeat = null;
        events.add(new GameFinished(result));
    }

    /**
     * Competition ranking: live seats by stack (more is better), then eliminated seats, the later elimination higher,
     * then forfeiters, the later forfeit higher; equal keys share the place.
     */
    private Map<PlayerId, Integer> ranking() {
        Comparator<Seat> order = Comparator.<Seat>comparingInt(s -> s.forfeitOrder > 0 ? 2 : s.eliminatedAtHand > 0 ? 1 : 0)
                .thenComparingInt(s -> s.forfeitOrder > 0 ? -s.forfeitOrder
                        : s.eliminatedAtHand > 0 ? -s.eliminatedAtHand : -s.stack);
        Map<PlayerId, Integer> places = new LinkedHashMap<>();
        for (Seat s : seats) {
            places.put(s.id, 1 + (int) seats.stream().filter(t -> order.compare(t, s) < 0).count());
        }
        return places;
    }

    // ---- queries ----

    @Override
    public GameId getId() {
        return id;
    }

    @Override
    public synchronized boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    @Override
    public List<PlayerId> getPlayerIds() {
        return seats.stream().map(s -> s.id).toList();
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized int version() {
        return version;
    }

    /** The hand in progress (or just played, once the game is over), from 1. */
    public synchronized int handNumber() {
        return handNumber;
    }

    public int handsTotal() {
        return options.hands().count();
    }

    public BlackjackOptions options() {
        return options;
    }

    /** Whether the game was started with a single seat. */
    public boolean isSolo() {
        return seats.size() == 1;
    }

    /** The seat that plays first this hand. */
    public synchronized PlayerId button() {
        return button;
    }

    /** The seat whose turn it is, null outside the playing phase or when no seat has anything to do. */
    public synchronized PlayerId currentSeat() {
        return currentSeat;
    }

    /** A snapshot of one seat. */
    public synchronized SeatState seat(PlayerId seat) {
        Seat s = require(seat);
        return new SeatState(s.stack, s.bet, s.betPlaced, s.hands, s.activeHand, s.eliminatedAtHand,
                s.forfeitOrder > 0, s.place);
    }

    /** The dealer's up card, null before the deal. */
    public synchronized Card dealerUpCard() {
        return dealerCards.isEmpty() ? null : dealerCards.get(0);
    }

    /** The dealer's cards; the hole card only once it is shown. */
    public synchronized List<Card> dealerCards() {
        return holeShown || dealerCards.size() < 2 ? List.copyOf(dealerCards) : List.of(dealerCards.get(0));
    }

    public synchronized boolean holeCardShown() {
        return holeShown;
    }

    /** The hand that was just settled, null before the first settlement. */
    public synchronized LastHand lastHand() {
        return lastHand;
    }

    /** How the game ended, empty while it is on. */
    public synchronized Optional<BlackjackResult> result() {
        return Optional.ofNullable(result);
    }

    /** The largest bet the seat may place now: the option's cap, capped by its stack. */
    public synchronized int maxBetFor(PlayerId seat) {
        return maxBetOf(require(seat));
    }

    /** The bet the seat placed last, empty before its first bet. */
    public synchronized OptionalInt lastBet(PlayerId seat) {
        Seat s = require(seat);
        return s.lastBet == 0 ? OptionalInt.empty() : OptionalInt.of(s.lastBet);
    }

    /** The current rank of a live seat by stack (equal stacks share it), 0 for a seat that is out. */
    public synchronized int standing(PlayerId seat) {
        Seat s = require(seat);
        if (s.out()) {
            return 0;
        }
        return 1 + (int) seats.stream().filter(t -> !t.out() && t.stack > s.stack).count();
    }

    /** What the seat may do now: the single source of the buttons a client shows. */
    public synchronized List<Action> availableActions(PlayerId seat) {
        Seat s = require(seat);
        return actionsOf(s);
    }

    private List<Action> actionsOf(Seat s) {
        if (phase == Phase.FINISHED || s.out()) {
            return List.of();
        }
        if (phase == Phase.BETTING) {
            return s.betPlaced ? List.of() : List.of(Action.BET);
        }
        if (!s.id.equals(currentSeat) || s.hands.isEmpty()) {
            return List.of();
        }
        PlayerHand hand = s.hands.get(s.activeHand);
        if (hand.status() != PlayerHand.Status.ACTIVE) {
            return List.of();
        }
        List<Action> actions = new ArrayList<>(List.of(Action.HIT, Action.STAND));
        boolean firstTwoCards = hand.cards().size() == 2;
        boolean affordable = s.stack >= hand.bet();
        if (firstTwoCards && !hand.splitAces() && affordable) {
            actions.add(Action.DOUBLE);
        }
        if (s.hands.size() == 1 && hand.isPair() && affordable) {
            actions.add(Action.SPLIT);
        }
        return actions;
    }

    // ---- helpers ----

    private Seat find(PlayerId seat) {
        return seats.stream().filter(s -> s.id.equals(seat)).findFirst().orElse(null);
    }

    private Seat require(PlayerId seat) {
        Seat s = find(seat);
        if (s == null) {
            throw new IllegalArgumentException("No such seat " + seat);
        }
        return s;
    }

    private void requireNotFinished() throws BlackjackException {
        if (phase == Phase.FINISHED) {
            throw new FinishedGameException();
        }
    }

    private void requirePhase(Phase expected) throws BlackjackException {
        if (phase != expected) {
            throw new WrongPhaseException(expected, phase);
        }
    }

    private Seat requireLive(PlayerId seat) throws BlackjackException {
        Seat s = require(seat);
        if (s.out()) {
            throw new EliminatedSeatException();
        }
        return s;
    }

    private Seat requireTurn(PlayerId seat) throws BlackjackException {
        requireNotFinished();
        requirePhase(Phase.PLAYING);
        Seat s = requireLive(seat);
        if (!seat.equals(currentSeat)) {
            throw new NotPlayersTurnException();
        }
        return s;
    }

    private void requireAvailable(Seat s, Action action) throws BlackjackException {
        if (!actionsOf(s).contains(action)) {
            throw new ActionNotAllowedException(action);
        }
    }

    private int maxBetOf(Seat s) {
        return Math.min(options.maxBet().amount(), s.stack);
    }

    private List<Seat> liveSeats() {
        return seats.stream().filter(s -> !s.out()).toList();
    }

    private boolean allLiveSeatsHaveBet() {
        return liveSeats().stream().allMatch(s -> s.betPlaced);
    }

    /** Every seat, in seat order, starting at the button. */
    private List<Seat> seatsFromButton() {
        int start = indexOf(seats, button);
        List<Seat> order = new ArrayList<>(seats.subList(start, seats.size()));
        order.addAll(seats.subList(0, start));
        return order;
    }

    private List<Seat> liveSeatsFromButton() {
        return seatsFromButton().stream().filter(s -> !s.out()).toList();
    }

    private static int indexOf(List<Seat> order, PlayerId seat) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).id.equals(seat)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No such seat " + seat);
    }

    private Map<PlayerId, Integer> stacks() {
        Map<PlayerId, Integer> stacks = new LinkedHashMap<>();
        seats.forEach(s -> stacks.put(s.id, s.stack));
        return stacks;
    }
}
