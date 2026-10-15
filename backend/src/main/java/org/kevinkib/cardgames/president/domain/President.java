package org.kevinkib.cardgames.president.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.president.domain.card.RankOrder;
import org.kevinkib.cardgames.president.domain.event.CardsGivenBack;
import org.kevinkib.cardgames.president.domain.event.MatchFinished;
import org.kevinkib.cardgames.president.domain.event.PresidentEvent;
import org.kevinkib.cardgames.president.domain.event.PresidentResult;
import org.kevinkib.cardgames.president.domain.event.Ready;
import org.kevinkib.cardgames.president.domain.event.RoundDealt;
import org.kevinkib.cardgames.president.domain.event.RoundScored;
import org.kevinkib.cardgames.president.domain.exchange.BestCards;
import org.kevinkib.cardgames.president.domain.exchange.ExchangePlan;
import org.kevinkib.cardgames.president.domain.exchange.PendingReturn;
import org.kevinkib.cardgames.president.domain.exchange.Title;
import org.kevinkib.cardgames.president.domain.exchange.Titles;
import org.kevinkib.cardgames.president.domain.exchange.WrongGiveBackException;
import org.kevinkib.cardgames.president.domain.play.Play;
import org.kevinkib.cardgames.president.domain.round.Action;
import org.kevinkib.cardgames.president.domain.round.AlreadyReadyException;
import org.kevinkib.cardgames.president.domain.round.FinishedGameException;
import org.kevinkib.cardgames.president.domain.round.NotPlayersTurnException;
import org.kevinkib.cardgames.president.domain.round.Phase;
import org.kevinkib.cardgames.president.domain.round.Round;
import org.kevinkib.cardgames.president.domain.round.WrongPhaseException;
import org.kevinkib.cardgames.president.domain.rules.FirstLead;
import org.kevinkib.cardgames.president.domain.rules.GiveBack;
import org.kevinkib.cardgames.president.domain.rules.NextLead;
import org.kevinkib.cardgames.president.domain.rules.PresidentException;
import org.kevinkib.cardgames.president.domain.rules.PresidentOptions;
import org.kevinkib.cardgames.president.domain.scoring.MatchScoring;
import org.kevinkib.cardgames.president.domain.scoring.RoundResult;
import org.kevinkib.cardgames.president.domain.scoring.Standings;
import org.kevinkib.cardgames.president.domain.trick.TrickView;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * A President match: a series of rounds. Each round is dealt (from round 2 by the previous Scum), the high
 * titles receive the best cards of the low titles and give cards back, the tricks are played, the finishing
 * order is scored, and the table waits until every player is ready. The match ends when the length option is
 * reached or a single player is left.
 *
 * <p>All state changes go through synchronized methods, and a rejected command throws a
 * {@link PresidentException} and changes nothing (the version does not move).
 */
public class President implements Game {

    public static final int MIN_PLAYERS = 3;
    public static final int MAX_PLAYERS = 8;
    /** One French deck, no joker. */
    public static final int DECK_SIZE = 52;

    private final GameId id;
    private final PresidentOptions options;
    private final DeckSource deckSource;
    private final long seed;
    private final int players;
    private final RankOrder rankOrder = RankOrder.standard();
    /** Seats still in the match, in seat order; a forfeit removes a seat for good. */
    private final Set<Integer> active = new TreeSet<>();
    private final Map<PlayerId, Integer> totals = new LinkedHashMap<>();
    private final Set<PlayerId> ready = new LinkedHashSet<>();
    private final Map<PlayerId, List<Card>> received = new HashMap<>();
    private final Map<PlayerId, List<Card>> given = new HashMap<>();
    private List<PendingReturn> exchange = new ArrayList<>();
    /** Titles in force in this round, earned in the previous one; empty in round 1. */
    private Map<PlayerId, Title> titles = Map.of();
    /** The finishing order of the last scored round, without the players who left before it ended. */
    private List<PlayerId> previousOrder = List.of();
    private RoundResult lastRound;
    private PresidentResult result = PresidentResult.ONGOING;
    private Round round;
    private int number = 1;
    private int version;
    private boolean finished;

    public President(GameId id, int nbPlayers) {
        this(id, nbPlayers, new Random().nextLong());
    }

    public President(GameId id, int nbPlayers, long seed) {
        this(id, nbPlayers, seed, new RandomDeckSource());
    }

    public President(GameId id, int nbPlayers, long seed, DeckSource deckSource) {
        this(id, nbPlayers, seed, deckSource, PresidentOptions.defaults());
    }

    public President(GameId id, int nbPlayers, long seed, DeckSource deckSource, PresidentOptions options) {
        if (nbPlayers < MIN_PLAYERS || nbPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("President is played by 3 to 8 players, not " + nbPlayers);
        }
        this.id = id;
        this.options = options;
        this.deckSource = deckSource;
        this.seed = seed;
        this.players = nbPlayers;
        seatEveryone();
        List<Card> deck = deckFor(number);
        int dealer = nbPlayers - 1;
        List<List<Card>> hands = deal(deck, new ArrayList<>(active), dealer, nbPlayers);
        int leader = firstLead(hands, options.firstLead(), new Random(seed));
        this.round = new Round(options, hands, dealer, leader);
    }

    /** Test constructor: explicit hands (any sizes) and the seat that leads; later rounds are dealt by the source. */
    President(GameId id, PresidentOptions options, long seed, DeckSource deckSource, List<List<Card>> hands,
              int leader) {
        this.id = id;
        this.options = options;
        this.deckSource = deckSource;
        this.seed = seed;
        this.players = hands.size();
        seatEveryone();
        this.round = new Round(options, hands, players - 1, leader);
    }

    /** Test constructor with the default deck source. */
    President(GameId id, PresidentOptions options, long seed, List<List<Card>> hands, int leader) {
        this(id, options, seed, new RandomDeckSource(), hands, leader);
    }

    private void seatEveryone() {
        for (int seat = 0; seat < players; seat++) {
            active.add(seat);
            totals.put(new PlayerId(seat), 0);
        }
    }

    private List<Card> deckFor(int roundNumber) {
        List<Card> deck = deckSource.deck(id, roundNumber, seed + roundNumber - 1);
        if (deck.size() != DECK_SIZE) {
            throw new IllegalArgumentException("A deck holds " + DECK_SIZE + " cards, not " + deck.size());
        }
        return deck;
    }

    /**
     * One card at a time to the active seats, starting after the dealer: the extra cards go to the seats
     * right after them. A seat that left holds nothing.
     */
    private static List<List<Card>> deal(List<Card> deck, List<Integer> activeSeats, int dealer, int seats) {
        List<List<Card>> hands = new ArrayList<>();
        for (int i = 0; i < seats; i++) {
            hands.add(new ArrayList<>());
        }
        int first = 0;
        for (int step = 1; step <= seats; step++) {
            int seat = (dealer + step) % seats;
            if (activeSeats.contains(seat)) {
                first = activeSeats.indexOf(seat);
                break;
            }
        }
        for (int i = 0; i < deck.size(); i++) {
            hands.get(activeSeats.get((first + i) % activeSeats.size())).add(deck.get(i));
        }
        return hands;
    }

    private static int firstLead(List<List<Card>> hands, FirstLead rule, Random random) {
        return switch (rule) {
            case KING_OF_HEARTS -> holderOf(hands, new Card(FrenchRank.KING, FrenchSuit.HEART));
            case LOWEST_CLUB -> holderOf(hands, new Card(FrenchRank.THREE, FrenchSuit.CLUB));
            case QUEEN_OF_HEARTS -> holderOf(hands, new Card(FrenchRank.QUEEN, FrenchSuit.HEART));
            case RANDOM -> random.nextInt(hands.size());
        };
    }

    /** The first holder of the card; the seat after the dealer when nobody holds it (cannot happen with a full deck). */
    private static int holderOf(List<List<Card>> hands, Card card) {
        for (int seat = 0; seat < hands.size(); seat++) {
            if (hands.get(seat).contains(card)) {
                return seat;
            }
        }
        return 0;
    }

    // ----- commands -----

    public synchronized List<PresidentEvent> play(PlayerId seat, List<Card> cards) throws PresidentException {
        requireNotFinished();
        List<PresidentEvent> events = new ArrayList<>(round.play(seat, cards));
        scoreIfRoundEnded(events);
        version++;
        return events;
    }

    public synchronized List<PresidentEvent> pass(PlayerId seat) throws PresidentException {
        requireNotFinished();
        List<PresidentEvent> events = new ArrayList<>(round.pass(seat));
        scoreIfRoundEnded(events);
        version++;
        return events;
    }

    /** A high title gives back the cards of their choice to the low title of their pair. */
    public synchronized List<PresidentEvent> giveBack(PlayerId seat, List<Card> cards) throws PresidentException {
        requireNotFinished();
        if (round.phase() != Phase.EXCHANGE) {
            throw new WrongPhaseException(phase(), "EXCHANGE");
        }
        int index = pendingIndex(seat);
        if (index < 0) {
            throw new WrongGiveBackException("You have no card to give back");
        }
        PendingReturn pending = exchange.get(index);
        if (cards == null || cards.size() != pending.count()) {
            throw new WrongGiveBackException("Give back exactly " + pending.count() + " card(s)");
        }
        round.transfer(pending.high(), pending.low(), cards);
        recordReturn(index, cards);
        List<PresidentEvent> events = new ArrayList<>();
        events.add(new CardsGivenBack(pending.high(), pending.low(), pending.count()));
        if (pendingReturns().isEmpty()) {
            round.beginPlay();
        }
        version++;
        return events;
    }

    /** A player is ready for the next round; the last one to say so deals it. */
    public synchronized List<PresidentEvent> ready(PlayerId seat) throws PresidentException {
        requireNotFinished();
        if (round.phase() != Phase.ROUND_OVER) {
            throw new WrongPhaseException(phase(), "ROUND_OVER");
        }
        if (!isActive(seat)) {
            throw new NotPlayersTurnException();
        }
        if (ready.contains(seat)) {
            throw new AlreadyReadyException();
        }
        ready.add(seat);
        boolean opened = ready.containsAll(getPlayerIds());
        List<PresidentEvent> events = new ArrayList<>();
        events.add(new Ready(seat, opened));
        if (opened) {
            startNextRound(events);
        }
        version++;
        return events;
    }

    /**
     * The player leaves the match for good (the {@link Game} contract). Their hand is set aside and their
     * points are dropped; during the exchange a pending return of theirs is first completed with their lowest
     * cards. The match goes on down to two players; a single player left wins by forfeit.
     */
    @Override
    public synchronized void forfeit(PlayerId loser) {
        if (finished || !isActive(loser)) {
            return;
        }
        Phase before = round.phase();
        if (before == Phase.EXCHANGE) {
            settleExchangeFor(loser);
        }
        active.remove(loser.id());
        totals.remove(loser);
        ready.remove(loser);
        List<PresidentEvent> events = new ArrayList<>(round.forfeit(loser));
        version++;

        if (active.size() == 1) {
            finished = true;
            result = new PresidentResult(getPlayerIds(), PresidentResult.Reason.FORFEIT);
            return;
        }
        switch (before) {
            case EXCHANGE -> {
                if (pendingReturns().isEmpty()) {
                    round.beginPlay();
                }
            }
            case PLAYING -> scoreIfRoundEnded(events);
            case ROUND_OVER -> {
                if (ready.containsAll(getPlayerIds())) {
                    startNextRound(events);
                }
            }
            default -> { }
        }
    }

    // ----- rounds -----

    private void scoreIfRoundEnded(List<PresidentEvent> events) {
        if (round.phase() != Phase.ROUND_OVER) {
            return;
        }
        List<PlayerId> order = round.finishingOrder().stream().filter(this::isActive).toList();
        Map<PlayerId, Integer> points = MatchScoring.points(options.scoring(), order);
        points.forEach((seat, value) -> totals.merge(seat, value, Integer::sum));
        Map<PlayerId, Title> earned = new LinkedHashMap<>();
        List<Title> byPlace = Titles.forActivePlayers(order.size());
        for (int place = 0; place < order.size(); place++) {
            earned.put(order.get(place), byPlace.get(place));
        }
        lastRound = new RoundResult(number, order, earned, points, new LinkedHashMap<>(totals));
        previousOrder = order;
        events.add(new RoundScored(lastRound));
        if (options.length().isOver(number, options.scoring(), totals.values())) {
            finished = true;
            result = new PresidentResult(standings().leaders(), PresidentResult.Reason.POINTS);
            events.add(new MatchFinished(result));
        }
    }

    /** Deals the next round: titles in force, best cards of the low titles to the high titles, then the returns. */
    private void startNextRound(List<PresidentEvent> events) {
        number++;
        ready.clear();
        received.clear();
        given.clear();
        List<PlayerId> order = previousOrder.stream().filter(this::isActive).toList();
        List<Title> byPlace = Titles.forActivePlayers(order.size());
        Map<PlayerId, Title> inForce = new LinkedHashMap<>();
        for (int place = 0; place < order.size(); place++) {
            inForce.put(order.get(place), byPlace.get(place));
        }
        titles = inForce;

        PlayerId president = order.get(0);
        PlayerId scum = order.get(order.size() - 1);
        ExchangePlan plan = ExchangePlan.of(options.exchange(), order.size());
        List<List<Card>> hands = deal(deckFor(number), new ArrayList<>(active), scum.id(), players);
        int leader = (options.nextLead() == NextLead.SCUM ? scum : president).id();
        exchange = new ArrayList<>();
        round = new Round(options, hands, scum.id(), leader,
                plan.pairs().isEmpty() ? Phase.PLAYING : Phase.EXCHANGE);
        events.add(new RoundDealt(number, scum));

        try {
            for (ExchangePlan.Pair pair : plan.pairs()) {
                PlayerId high = order.get(pair.highPlace());
                PlayerId low = order.get(pair.lowPlace());
                List<Card> best = BestCards.best(round.hand(low), pair.count(), rankOrder);
                round.transfer(low, high, best);
                given.put(low, new ArrayList<>(best));
                received.put(high, new ArrayList<>(best));
                exchange.add(new PendingReturn(high, low, best.size(), false));
            }
            if (options.giveBack() == GiveBack.LOWEST) {
                for (int index = 0; index < exchange.size(); index++) {
                    PendingReturn pending = exchange.get(index);
                    List<Card> lowest = BestCards.lowest(round.hand(pending.high()), pending.count(), rankOrder);
                    round.transfer(pending.high(), pending.low(), lowest);
                    recordReturn(index, lowest);
                    events.add(new CardsGivenBack(pending.high(), pending.low(), pending.count()));
                }
            }
        } catch (PresidentException e) {
            throw new IllegalStateException("The exchange moves cards the hands hold", e);
        }
        if (pendingReturns().isEmpty()) {
            round.beginPlay();
        }
    }

    // ----- exchange helpers -----

    private int pendingIndex(PlayerId high) {
        for (int index = 0; index < exchange.size(); index++) {
            PendingReturn pending = exchange.get(index);
            if (!pending.done() && pending.high().equals(high)) {
                return index;
            }
        }
        return -1;
    }

    private void recordReturn(int index, List<Card> cards) {
        PendingReturn pending = exchange.get(index);
        given.put(pending.high(), new ArrayList<>(cards));
        received.put(pending.low(), new ArrayList<>(cards));
        exchange.set(index, new PendingReturn(pending.high(), pending.low(), pending.count(), true));
    }

    /** A leaver's pending return is completed with their lowest cards; a return owed to a leaver is cancelled. */
    private void settleExchangeFor(PlayerId loser) {
        for (int index = 0; index < exchange.size(); index++) {
            PendingReturn pending = exchange.get(index);
            if (pending.done()) {
                continue;
            }
            if (pending.high().equals(loser)) {
                List<Card> lowest = BestCards.lowest(round.hand(loser), pending.count(), rankOrder);
                try {
                    round.transfer(loser, pending.low(), lowest);
                } catch (PresidentException e) {
                    throw new IllegalStateException("The lowest cards come from the hand", e);
                }
                recordReturn(index, lowest);
            } else if (pending.low().equals(loser)) {
                exchange.set(index, new PendingReturn(pending.high(), pending.low(), pending.count(), true));
            }
        }
    }

    // ----- guards -----

    private void requireNotFinished() throws FinishedGameException {
        if (finished) {
            throw new FinishedGameException();
        }
    }

    private boolean isActive(PlayerId seat) {
        return seat != null && seat.id() != null && active.contains(seat.id());
    }

    // ----- queries -----

    @Override
    public GameId getId() {
        return id;
    }

    /** Finished when the match is over: the length is reached, or a single player is left. */
    @Override
    public synchronized boolean isFinished() {
        return finished;
    }

    /** The players still in the match, in seat order. */
    @Override
    public synchronized List<PlayerId> getPlayerIds() {
        return active.stream().map(PlayerId::new).toList();
    }

    public PresidentOptions options() {
        return options;
    }

    public synchronized int version() {
        return version;
    }

    public synchronized int round() {
        return number;
    }

    public synchronized Phase phase() {
        return finished ? Phase.FINISHED : round.phase();
    }

    public synchronized PlayerId dealer() {
        return round.dealer();
    }

    public synchronized PlayerId currentSeat() {
        return round.currentSeat();
    }

    /** The cards of a seat, from the weakest to the strongest; nothing for a seat that left. */
    public synchronized List<Card> hand(PlayerId seat) {
        return round.hand(seat);
    }

    public synchronized List<Integer> handSizes() {
        return round.handSizes();
    }

    public synchronized TrickView trick() {
        return round.trick();
    }

    /** The cards of the tricks already won and of the hands of the players who left, set aside face down. */
    public synchronized List<Card> discard() {
        return round.discard();
    }

    /** The players who are out of this round, best place first; complete once the round is over. */
    public synchronized List<PlayerId> finishingOrder() {
        return round.finishingOrder();
    }

    /** Every legal play of the seat to act; nothing for any other seat. */
    public synchronized List<Play> legalPlays(PlayerId seat) {
        return round.legalPlays(seat);
    }

    /**
     * What the seat may do now: play (and pass unless it leads) when it is its turn, give back while a return
     * is owed, say it is ready at the barrier; nothing for any other seat, a seat that left, or once finished.
     */
    public synchronized List<Action> availableActions(PlayerId seat) {
        if (finished || !isActive(seat)) {
            return List.of();
        }
        return switch (round.phase()) {
            case PLAYING -> {
                if (!seat.equals(round.currentSeat()) || round.hand(seat).isEmpty()) {
                    yield List.of();
                }
                List<Action> actions = new ArrayList<>();
                if (!round.legalPlays(seat).isEmpty()) {
                    actions.add(Action.PLAY);
                }
                if (!round.trick().plays().isEmpty()) {
                    actions.add(Action.PASS);
                }
                yield actions;
            }
            case EXCHANGE -> pendingIndex(seat) >= 0 ? List.of(Action.GIVE_BACK) : List.of();
            case ROUND_OVER -> ready.contains(seat) ? List.of() : List.of(Action.READY);
            case FINISHED -> List.of();
        };
    }

    /** The titles earned by the round just scored; empty while a round is being played or dealt. */
    public synchronized Map<PlayerId, Title> roundTitles() {
        if (round.phase() != Phase.ROUND_OVER || lastRound == null || lastRound.round() != number) {
            return Map.of();
        }
        return lastRound.titles();
    }

    /** The titles in force in this round (earned in the previous one); empty in round 1. */
    public synchronized Map<PlayerId, Title> titles() {
        return Map.copyOf(titles);
    }

    /** Every pair of this round's exchange, with its return done or not: seats and counts only. */
    public synchronized List<PendingReturn> exchange() {
        return List.copyOf(exchange);
    }

    /** The pairs whose high title still has to give cards back. */
    public synchronized List<PendingReturn> pendingReturns() {
        return exchange.stream().filter(p -> !p.done()).toList();
    }

    /** What the seat received in this round's exchange; private to that seat. */
    public synchronized List<Card> received(PlayerId seat) {
        return List.copyOf(received.getOrDefault(seat, List.of()));
    }

    /** What the seat gave in this round's exchange; private to that seat. */
    public synchronized List<Card> given(PlayerId seat) {
        return List.copyOf(given.getOrDefault(seat, List.of()));
    }

    public synchronized Set<PlayerId> readySeats() {
        return Set.copyOf(ready);
    }

    public synchronized Standings standings() {
        return new Standings(totals);
    }

    /** The last scored round, or {@code null} before the first round ends. */
    public synchronized RoundResult lastRoundResult() {
        return lastRound;
    }

    public synchronized PresidentResult result() {
        return result;
    }
}
