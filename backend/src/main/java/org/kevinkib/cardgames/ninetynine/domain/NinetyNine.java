package org.kevinkib.cardgames.ninetynine.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.ninetynine.domain.event.Busted;
import org.kevinkib.cardgames.ninetynine.domain.event.Eliminated;
import org.kevinkib.cardgames.ninetynine.domain.event.Forfeited;
import org.kevinkib.cardgames.ninetynine.domain.event.GameFinished;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineEvent;
import org.kevinkib.cardgames.ninetynine.domain.event.NinetyNineResult;
import org.kevinkib.cardgames.ninetynine.domain.event.Played;
import org.kevinkib.cardgames.ninetynine.domain.event.RoundDealt;
import org.kevinkib.cardgames.ninetynine.domain.event.StockReshuffled;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A 99 game: rounds in which the seats play one card in turn on a running total, and the seat that cannot stay at
 * 99 or below loses a life (spec sections 3 and 6). Every random choice comes from the {@link DeckSource}.
 *
 * <p>All state changes go through synchronized methods; a rejected command throws a {@link NinetyNineException}
 * and changes nothing (the version does not move).
 */
public class NinetyNine implements Game {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 12;
    public static final int HAND_SIZE = 3;

    private final GameId id;
    private final NinetyNineOptions options;
    private final DeckSource deckSource;
    private final List<PlayerId> seats = new ArrayList<>();
    private final Map<PlayerId, Integer> lives = new LinkedHashMap<>();
    private final Map<PlayerId, List<Card>> hands = new LinkedHashMap<>();
    /** The places of the seats that are out, 1 being the best. */
    private final Map<PlayerId, Integer> places = new LinkedHashMap<>();
    /** Next card to draw first. */
    private List<Card> stock = new ArrayList<>();
    /** Face-up cards since the last reshuffle, the top card last. */
    private List<Card> pile = new ArrayList<>();
    private int total;
    private Direction direction = Direction.CLOCKWISE;
    private PlayerId current;
    /** The seat that lost the last round: it starts the next one if still in, else the next live seat. */
    private PlayerId nextStarter;
    private int round;
    private Phase phase = Phase.PLAYING;
    private Busted lastBust;
    private LastPlay lastPlay;
    private NinetyNineResult result;
    private int version;
    /** What the last {@link #forfeit(PlayerId)} changed, for the presentation layer to announce. */
    private List<NinetyNineEvent> lastForfeitEvents = List.of();

    public NinetyNine(GameId id, int nbPlayers, DeckSource deckSource, NinetyNineOptions options) {
        if (nbPlayers < MIN_PLAYERS || nbPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("99 is played by 2 to 12 players, not " + nbPlayers);
        }
        this.id = id;
        this.options = options;
        this.deckSource = deckSource;
        for (int i = 0; i < nbPlayers; i++) {
            PlayerId seat = new PlayerId(i);
            seats.add(seat);
            lives.put(seat, options.lives().count());
            hands.put(seat, new ArrayList<>());
        }
        deal(seats.get(deckSource.firstSeat(id, nbPlayers)));
    }

    /** Test constructor: an explicit state. A seat with 0 lives is already out. */
    NinetyNine(GameId id, NinetyNineOptions options, DeckSource deckSource, int round, Map<PlayerId, Integer> lives,
               Map<PlayerId, List<Card>> hands, List<Card> stock, List<Card> pile, int total, Direction direction,
               PlayerId current) {
        this.id = id;
        this.options = options;
        this.deckSource = deckSource;
        this.round = round;
        this.seats.addAll(lives.keySet());
        this.lives.putAll(lives);
        hands.forEach((seat, hand) -> this.hands.put(seat, new ArrayList<>(hand)));
        this.stock = new ArrayList<>(stock);
        this.pile = new ArrayList<>(pile);
        this.total = total;
        this.direction = direction;
        this.current = current;
        int place = seats.size();
        for (PlayerId seat : seats) {
            if (lives.get(seat) == 0) {
                places.put(seat, place--);
            }
        }
    }

    // ---- commands ----

    /**
     * Plays a card of the seat on its turn. An Ace needs a value (1, or 11 when that stays at 99); the value is
     * ignored for other cards. A seat that holds a safe card must play a safe card. After the draw, the next seat
     * busts at once when none of its cards is safe.
     */
    public synchronized List<NinetyNineEvent> play(PlayerId seat, Card card, Integer aceValue)
            throws NinetyNineException {
        requirePlaying();
        if (!seat.equals(current)) {
            throw new NotPlayersTurnException();
        }
        List<Card> hand = hands.get(seat);
        if (!hand.contains(card)) {
            throw new CardNotInHandException();
        }
        boolean ace = card.getRank() == FrenchRank.ACE;
        if (ace && (aceValue == null || (aceValue != 1 && aceValue != 11)
                || (aceValue == 11 && total + 11 > CardEffect.LIMIT))) {
            throw new AceValueException();
        }
        Integer used = ace ? aceValue : null;
        int after = CardEffect.apply(total, card, used);
        if (after > CardEffect.LIMIT) {
            throw new UnsafeCardException();
        }

        List<NinetyNineEvent> events = new ArrayList<>();
        hand.remove(card);
        pile.add(card);
        int before = total;
        total = after;
        boolean reversed = CardEffect.reverses(card);
        if (reversed) {
            direction = direction.reversed();
        }
        lastPlay = new LastPlay(seat, card, used, before, after, reversed);
        events.add(new Played(seat, card, used, after, reversed));
        draw(seat, events);
        current = nextLive(seat, direction);
        bustIfStuck(events);
        version++;
        return events;
    }

    /** Deals the next round after the pause; refused outside {@link Phase#ROUND_OVER}. */
    public synchronized List<NinetyNineEvent> nextRound() throws NinetyNineException {
        requireNotFinished();
        if (phase != Phase.ROUND_OVER) {
            throw new WrongPhaseException();
        }
        PlayerId starter = firstLiveFrom(nextStarter);
        deal(starter);
        version++;
        return List.of(new RoundDealt(round, starter));
    }

    @Override
    public synchronized void forfeit(PlayerId loser) {
        lastForfeitEvents = forfeitWithEvents(loser);
    }

    /**
     * The events of the last {@link #forfeit(PlayerId)} (the elimination, a bust of the next seat, the end of the
     * game), empty when it changed nothing. The {@code Game} contract returns nothing, so the lifecycle broadcaster
     * reads them here to announce them.
     */
    public synchronized List<NinetyNineEvent> lastForfeitEvents() {
        return lastForfeitEvents;
    }

    /**
     * Same as {@link #forfeit(PlayerId)}, returning what changed: the seat is out at once, placed below every
     * live seat; the turn moves on if it was theirs; one live seat left ends the game. A forfeit does not trigger
     * the quick-mode end. A seat already out, unknown, or a finished game change nothing.
     */
    public synchronized List<NinetyNineEvent> forfeitWithEvents(PlayerId loser) {
        if (phase == Phase.FINISHED || lives.getOrDefault(loser, 0) == 0) {
            return List.of();
        }
        List<NinetyNineEvent> events = new ArrayList<>();
        events.add(new Forfeited(loser));
        eliminate(loser, events);
        if (liveSeats().size() == 1) {
            finish(NinetyNineResult.Reason.FORFEIT, events);
        } else if (phase == Phase.PLAYING && loser.equals(current)) {
            current = nextLive(loser, direction);
            bustIfStuck(events);
        }
        version++;
        return events;
    }

    // ---- internals ----

    private void requireNotFinished() throws FinishedGameException {
        if (phase == Phase.FINISHED) {
            throw new FinishedGameException();
        }
    }

    private void requirePlaying() throws NinetyNineException {
        requireNotFinished();
        if (phase != Phase.PLAYING) {
            throw new WrongPhaseException();
        }
    }

    /**
     * Starts a round: a fresh deck, three cards to each live seat, total 0, seat order. Failure-atomic: the deck is
     * drawn and the hands built aside, and the state changes only once nothing can fail any more.
     */
    private void deal(PlayerId starter) {
        int newRound = round + 1;
        List<Card> deck = new ArrayList<>(deckSource.shuffledDeck(id, newRound));
        List<PlayerId> live = liveSeats();
        Map<PlayerId, List<Card>> dealt = new LinkedHashMap<>();
        live.forEach(seat -> dealt.put(seat, new ArrayList<>()));
        int next = 0;
        for (int pass = 0; pass < HAND_SIZE; pass++) {
            for (PlayerId seat : live) {
                dealt.get(seat).add(deck.get(next++));
            }
        }
        List<Card> newStock = new ArrayList<>(deck.subList(next, deck.size()));

        round = newRound;
        hands.values().forEach(List::clear);
        dealt.forEach((seat, hand) -> hands.get(seat).addAll(hand));
        stock = newStock;
        pile = new ArrayList<>();
        total = 0;
        direction = Direction.CLOCKWISE;
        current = starter;
        phase = Phase.PLAYING;
        lastBust = null;
        lastPlay = null;
    }

    /** Draws the replacement card, rebuilding the stock from the pile (top card kept) when it is empty. */
    private void draw(PlayerId seat, List<NinetyNineEvent> events) {
        if (stock.isEmpty() && pile.size() > 1) {
            Card top = pile.get(pile.size() - 1);
            List<Card> under = new ArrayList<>(pile.subList(0, pile.size() - 1));
            stock = new ArrayList<>(deckSource.reshuffle(id, under));
            pile = new ArrayList<>(List.of(top));
            events.add(new StockReshuffled(stock.size()));
        }
        if (!stock.isEmpty()) {
            hands.get(seat).add(stock.remove(0));
        }
    }

    private void bustIfStuck(List<NinetyNineEvent> events) {
        if (CardEffect.safeCards(total, hands.get(current)).isEmpty()) {
            bust(current, events);
        }
    }

    /** The seat goes over: it loses a life and the round ends, or the game when that settles it. */
    private void bust(PlayerId seat, List<NinetyNineEvent> events) {
        int livesLeft = lives.get(seat) - 1;
        lastBust = new Busted(seat, total, hands.get(seat), livesLeft);
        events.add(lastBust);
        nextStarter = seat;
        if (livesLeft > 0) {
            lives.put(seat, livesLeft);
            phase = Phase.ROUND_OVER;
            return;
        }
        eliminate(seat, events);
        if (options.quick() == NinetyNineOptions.QuickMode.ON) {
            finish(NinetyNineResult.Reason.QUICK, events);
        } else if (liveSeats().size() == 1) {
            finish(NinetyNineResult.Reason.LAST_STANDING, events);
        } else {
            phase = Phase.ROUND_OVER;
        }
    }

    /** Takes the seat out with the place just below every live seat. */
    private void eliminate(PlayerId seat, List<NinetyNineEvent> events) {
        int place = liveSeats().size();
        lives.put(seat, 0);
        hands.get(seat).clear();
        places.put(seat, place);
        events.add(new Eliminated(seat, place));
    }

    private void finish(NinetyNineResult.Reason reason, List<NinetyNineEvent> events) {
        List<PlayerId> live = liveSeats();
        for (PlayerId seat : live) {
            int better = (int) live.stream().filter(other -> lives.get(other) > lives.get(seat)).count();
            places.put(seat, reason == NinetyNineResult.Reason.QUICK ? 1 + better : 1);
        }
        result = new NinetyNineResult(places, reason);
        phase = Phase.FINISHED;
        events.add(new GameFinished(result));
    }

    /** The next live seat after {@code from} in the direction. */
    private PlayerId nextLive(PlayerId from, Direction way) {
        int step = way == Direction.CLOCKWISE ? 1 : -1;
        int index = seats.indexOf(from);
        for (int i = 0; i < seats.size(); i++) {
            index = Math.floorMod(index + step, seats.size());
            if (lives.get(seats.get(index)) > 0) {
                return seats.get(index);
            }
        }
        throw new IllegalStateException("No live seat left");
    }

    /** {@code from} itself when it is still in, else the next live seat in seat order. */
    private PlayerId firstLiveFrom(PlayerId from) {
        return lives.get(from) > 0 ? from : nextLive(from, Direction.CLOCKWISE);
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
    public synchronized List<PlayerId> getPlayerIds() {
        return List.copyOf(seats);
    }

    public NinetyNineOptions options() {
        return options;
    }

    public synchronized int version() {
        return version;
    }

    public synchronized int round() {
        return round;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized PlayerId currentSeat() {
        return current;
    }

    public synchronized Direction direction() {
        return direction;
    }

    public synchronized int total() {
        return total;
    }

    /** A copy of the seat's hand; empty for a seat that is out or unknown. */
    public synchronized List<Card> hand(PlayerId seat) {
        return List.copyOf(hands.getOrDefault(seat, List.of()));
    }

    public synchronized Map<PlayerId, Integer> handSizes() {
        Map<PlayerId, Integer> sizes = new LinkedHashMap<>();
        seats.forEach(seat -> sizes.put(seat, hands.get(seat).size()));
        return sizes;
    }

    public synchronized Map<PlayerId, Integer> lives() {
        return new LinkedHashMap<>(lives);
    }

    /** The seats still in the game, in seat order. */
    public synchronized List<PlayerId> liveSeats() {
        return seats.stream().filter(seat -> lives.get(seat) > 0).toList();
    }

    public synchronized Optional<Card> pileTop() {
        return pile.isEmpty() ? Optional.empty() : Optional.of(pile.get(pile.size() - 1));
    }

    /** The public face-up pile since the last reshuffle, the top card last. */
    public synchronized List<Card> pileSinceReshuffle() {
        return List.copyOf(pile);
    }

    public synchronized int stockSize() {
        return stock.size();
    }

    /** The bust that ended the last round, until the next deal. */
    public synchronized Optional<Busted> lastBust() {
        return Optional.ofNullable(lastBust);
    }

    /** The last card played in the current round, until the next deal. */
    public synchronized Optional<LastPlay> lastPlay() {
        return Optional.ofNullable(lastPlay);
    }

    /** Places of the seats that are out (everyone once the game is over). */
    public synchronized Map<PlayerId, Integer> places() {
        return new LinkedHashMap<>(places);
    }

    /** @throws NotEliminatedException when the seat is still in the game */
    public synchronized int place(PlayerId seat) throws NotEliminatedException {
        Integer place = places.get(seat);
        if (place == null) {
            throw new NotEliminatedException();
        }
        return place;
    }

    /** The cards of the seat's hand it may play now: none outside its turn. */
    public synchronized List<Card> safeCards(PlayerId seat) {
        if (phase != Phase.PLAYING || !seat.equals(current)) {
            return List.of();
        }
        return CardEffect.safeCards(total, hands.get(seat));
    }

    public synchronized List<Action> availableActions(PlayerId seat) {
        return phase == Phase.PLAYING && seat.equals(current) ? List.of(Action.PLAY) : List.of();
    }

    public synchronized Optional<NinetyNineResult> result() {
        return Optional.ofNullable(result);
    }
}
