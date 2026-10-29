package org.kevinkib.cardgames.kilodemerde.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kilodemerde.domain.bot.KiloDeMerdeBotView;
import org.kevinkib.cardgames.kilodemerde.domain.event.FalseSlap;
import org.kevinkib.cardgames.kilodemerde.domain.event.Flushed;
import org.kevinkib.cardgames.kilodemerde.domain.event.Forfeited;
import org.kevinkib.cardgames.kilodemerde.domain.event.GameFinished;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDeMerdeEvent;
import org.kevinkib.cardgames.kilodemerde.domain.event.KiloDrawn;
import org.kevinkib.cardgames.kilodemerde.domain.event.PassChosen;
import org.kevinkib.cardgames.kilodemerde.domain.event.RaceOpened;
import org.kevinkib.cardgames.kilodemerde.domain.event.ReadyMarked;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundDealt;
import org.kevinkib.cardgames.kilodemerde.domain.event.RoundOver;
import org.kevinkib.cardgames.kilodemerde.domain.event.Slapped;
import org.kevinkib.cardgames.kilodemerde.domain.event.StepResolved;
import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A Kilo de merde game: rounds of simultaneous pass steps in which the seats collect four of a kind, a slap race
 * whose last slapper draws kilos from the pile, and a ranking by fewest kilos (spec sections 3 and 8). Every random
 * choice comes from the {@link DeckSource}.
 *
 * <p>All state changes go through synchronized methods; a rejected command throws a {@link KiloDeMerdeException}
 * and changes nothing (the version does not move).
 */
public class KiloDeMerde implements Game {

    public static final int MIN_PLAYERS = KiloDeck.MIN_PLAYERS;
    public static final int MAX_PLAYERS = KiloDeck.MAX_PLAYERS;

    private final GameId id;
    private final KiloDeMerdeOptions options;
    private final DeckSource deckSource;
    /** Every seat of the game, in seat order, including the ones that left. */
    private final List<PlayerId> seats = new ArrayList<>();
    /** The seats still playing, in seat order: a seat passes to the next one. */
    private final List<PlayerId> active = new ArrayList<>();
    private final Map<PlayerId, List<Card>> hands = new LinkedHashMap<>();
    private final Map<PlayerId, List<Card>> kiloCards = new LinkedHashMap<>();
    private final Map<PlayerId, Card> chosen = new LinkedHashMap<>();
    private final Map<PlayerId, Card> lastGiven = new LinkedHashMap<>();
    private final Map<PlayerId, Card> lastReceived = new LinkedHashMap<>();
    private final Map<PlayerId, List<KiloDeMerdeBotView.Pass>> passHistory = new LinkedHashMap<>();
    /** The places of the seats that left, 1 being the best. */
    private final Map<PlayerId, Integer> places = new LinkedHashMap<>();
    /** The kilos a seat had when it left. */
    private final Map<PlayerId, Integer> leftKilos = new LinkedHashMap<>();
    private final List<PlayerId> slapOrder = new ArrayList<>();
    /** The seats that confirmed they are ready, while the game is in {@link Phase#AWAITING_START} or {@link Phase#ROUND_OVER}. */
    private final Set<PlayerId> ready = new LinkedHashSet<>();
    /** The kilo pile, the top card first. */
    private List<Card> pile = new ArrayList<>();
    private Phase phase = Phase.AWAITING_START;
    /** The number of the round being played (or just played): rounds played + 1 while passing. */
    private int round;
    private int roundsPlayed;
    private int step = 1;
    private int raceId;
    private int version;
    private RoundOutcome lastOutcome;
    private KiloDeMerdeResult result;
    /** What the last {@link #forfeit(PlayerId)} changed, for the presentation layer to announce. */
    private List<KiloDeMerdeEvent> lastForfeitEvents = List.of();

    public KiloDeMerde(GameId id, int nbPlayers, DeckSource deckSource, KiloDeMerdeOptions options) {
        if (nbPlayers < MIN_PLAYERS || nbPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("Kilo de merde is played by 3 to 10 players, not " + nbPlayers);
        }
        this.id = id;
        this.options = options;
        this.deckSource = deckSource;
        for (int i = 0; i < nbPlayers; i++) {
            seats.add(new PlayerId(i));
        }
        active.addAll(seats);
        seats.forEach(seat -> kiloCards.put(seat, new ArrayList<>()));
        this.pile = new ArrayList<>(deckSource.shufflePile(id, KiloDeck.kiloPile(nbPlayers)));
        deal();
        // The hands are dealt through the seam now, but nobody sees them and nobody passes until every seat is ready.
        phase = Phase.AWAITING_START;
    }

    /** Test constructor: an explicit state. Seats are the keys of {@code hands}. */
    KiloDeMerde(GameId id, KiloDeMerdeOptions options, DeckSource deckSource, int roundsPlayed, int step,
                Map<PlayerId, List<Card>> hands, Map<PlayerId, List<Card>> kiloCards, List<Card> pile) {
        this.id = id;
        this.options = options;
        this.deckSource = deckSource;
        this.roundsPlayed = roundsPlayed;
        this.round = roundsPlayed + 1;
        this.step = step;
        this.phase = Phase.PASSING;
        this.seats.addAll(hands.keySet());
        this.active.addAll(hands.keySet());
        hands.forEach((seat, hand) -> this.hands.put(seat, new ArrayList<>(hand)));
        hands.keySet().forEach(seat -> {
            this.kiloCards.put(seat, new ArrayList<>(kiloCards.getOrDefault(seat, List.of())));
            this.passHistory.put(seat, new ArrayList<>());
        });
        this.pile = new ArrayList<>(pile);
    }

    // ---- commands ----

    /**
     * Chooses the card a seat passes this step. When it is the last choice, the step resolves in the same call.
     */
    public synchronized List<KiloDeMerdeEvent> choosePass(PlayerId seat, Card card) throws KiloDeMerdeException {
        requireNotFinished();
        if (phase != Phase.PASSING) {
            throw new WrongPhaseException();
        }
        requireActive(seat);
        if (chosen.containsKey(seat)) {
            throw new AlreadyChoseException();
        }
        if (!hands.get(seat).contains(card)) {
            throw new CardNotInHandException();
        }
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        chosen.put(seat, card);
        events.add(new PassChosen(seat));
        if (chosen.size() == active.size()) {
            resolveStep(events);
        }
        version++;
        return events;
    }

    /**
     * Slaps the pile. While passing, the slap is valid when any hand is four of a kind (it opens a race) and false
     * otherwise (the slapper draws alone and the round ends). In a race it records the seat; the last seat left
     * draws and the round ends.
     */
    public synchronized List<KiloDeMerdeEvent> slap(PlayerId seat) throws KiloDeMerdeException {
        requireNotFinished();
        if (phase != Phase.PASSING && phase != Phase.RACE) {
            throw new WrongPhaseException();
        }
        requireActive(seat);
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        if (phase == Phase.PASSING) {
            if (KiloDeMerdeRules.anyFourOfAKind(hands.values())) {
                raceId++;
                phase = Phase.RACE;
                slapOrder.add(seat);
                events.add(new RaceOpened(seat));
                events.add(new Slapped(seat, 1));
            } else {
                events.add(new FalseSlap(seat));
                slapOrder.add(seat);
                List<KiloDraw> draws = new ArrayList<>();
                drawFor(seat, draws, events);
                endRound(RoundOutcome.Kind.FALSE_SLAP, draws, events);
            }
        } else {
            if (slapOrder.contains(seat)) {
                throw new AlreadySlappedException();
            }
            slapOrder.add(seat);
            events.add(new Slapped(seat, slapOrder.size()));
            if (slapOrder.size() == active.size() - 1) {
                PlayerId last = active.stream().filter(s -> !slapOrder.contains(s)).findFirst().orElseThrow();
                List<KiloDraw> draws = new ArrayList<>();
                drawFor(last, draws, events);
                endRound(RoundOutcome.Kind.RACE, draws, events);
            }
        }
        version++;
        return events;
    }

    /**
     * The pass timer of {@code step} expired: every seat that has not chosen passes its {@link
     * KiloDeMerdeRules#defaultPass}. Refused unless the game is passing at that very step.
     */
    public synchronized List<KiloDeMerdeEvent> expirePassTimer(int step) throws KiloDeMerdeException {
        requireNotFinished();
        if (phase != Phase.PASSING || step != this.step) {
            throw new WrongPhaseException();
        }
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        for (PlayerId seat : active) {
            if (!chosen.containsKey(seat)) {
                chosen.put(seat, KiloDeMerdeRules.defaultPass(hands.get(seat)));
                events.add(new PassChosen(seat));
            }
        }
        resolveStep(events);
        version++;
        return events;
    }

    /**
     * The race timeout of {@code raceId} expired: every seat that has not slapped draws one card, in seat order
     * starting left of the first slapper. Refused unless that race is the open one.
     */
    public synchronized List<KiloDeMerdeEvent> closeRace(int raceId) throws KiloDeMerdeException {
        requireNotFinished();
        if (phase != Phase.RACE || raceId != this.raceId) {
            throw new WrongPhaseException();
        }
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        List<KiloDraw> draws = new ArrayList<>();
        int first = active.indexOf(slapOrder.get(0));
        for (int i = 1; i <= active.size(); i++) {
            PlayerId seat = active.get((first + i) % active.size());
            if (!slapOrder.contains(seat)) {
                drawFor(seat, draws, events);
            }
        }
        endRound(RoundOutcome.Kind.RACE, draws, events);
        version++;
        return events;
    }

    /**
     * A seat confirms it has seen the table and is ready: before the first deal ({@link Phase#AWAITING_START}) or
     * between two rounds ({@link Phase#ROUND_OVER}). When the last active seat is ready the game moves on: the first
     * round starts, or the next round is dealt, or the game ends. Confirming twice changes nothing.
     */
    public synchronized List<KiloDeMerdeEvent> ready(PlayerId seat) throws KiloDeMerdeException {
        requireNotFinished();
        requireAwaitingReady();
        requireActive(seat);
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        if (ready.add(seat)) {
            events.add(new ReadyMarked(seat));
            advanceIfEveryoneReady(events);
            version++;
        }
        return events;
    }

    /**
     * The ready timeout expired: every active seat that is not ready is counted ready and the game moves on. Refused
     * outside the two waiting phases.
     */
    public synchronized List<KiloDeMerdeEvent> expireReady() throws KiloDeMerdeException {
        requireNotFinished();
        requireAwaitingReady();
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        for (PlayerId seat : active) {
            if (ready.add(seat)) {
                events.add(new ReadyMarked(seat));
            }
        }
        advanceIfEveryoneReady(events);
        version++;
        return events;
    }

    /**
     * Forces the move on from the end of a round, whoever is ready: ends the game (rounds played, or an empty kilo
     * pile) or deals the next round. Refused outside {@link Phase#ROUND_OVER}.
     */
    public synchronized List<KiloDeMerdeEvent> nextRound() throws KiloDeMerdeException {
        requireNotFinished();
        if (phase != Phase.ROUND_OVER) {
            throw new WrongPhaseException();
        }
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        advance(events);
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
    public synchronized List<KiloDeMerdeEvent> lastForfeitEvents() {
        return lastForfeitEvents;
    }

    /**
     * Same as {@link #forfeit(PlayerId)}, returning what changed (A14): the seat is placed below every remaining seat
     * and leaves; the round in progress is voided; the table re-forms for one player fewer (the rank no longer dealt
     * and the leaver's kilo cards join the pile, which is shuffled) and a new round is dealt. Below three players the
     * game ends, the others ranked by kilos. A seat already out, unknown, or a finished game change nothing.
     */
    public synchronized List<KiloDeMerdeEvent> forfeitWithEvents(PlayerId loser) {
        if (phase == Phase.FINISHED || !active.contains(loser)) {
            return List.of();
        }
        List<KiloDeMerdeEvent> events = new ArrayList<>();
        events.add(new Forfeited(loser));
        int before = active.size();
        places.put(loser, before);
        leftKilos.put(loser, kilos(loser));
        List<Card> returned = kiloCards.put(loser, new ArrayList<>());
        active.remove(loser);
        hands.put(loser, new ArrayList<>());
        chosen.remove(loser);
        ready.remove(loser);
        if (active.size() < MIN_PLAYERS) {
            finish(KiloDeMerdeResult.Reason.FORFEIT, events);
        } else {
            List<Card> merged = new ArrayList<>(pile);
            List<Card> stillDealt = KiloDeck.handDeck(active.size());
            KiloDeck.handDeck(before).stream().filter(card -> !stillDealt.contains(card)).forEach(merged::add);
            merged.addAll(returned);
            pile = new ArrayList<>(deckSource.shufflePile(id, merged));
            if (phase == Phase.AWAITING_START) {
                Set<PlayerId> confirmed = new LinkedHashSet<>(ready);
                deal();
                phase = Phase.AWAITING_START;
                ready.addAll(confirmed);
                advanceIfEveryoneReady(events);
            } else if (phase == Phase.ROUND_OVER) {
                advanceIfEveryoneReady(events);
            } else {
                deal();
                events.add(new RoundDealt(round));
            }
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

    private void requireActive(PlayerId seat) throws NotActiveSeatException {
        if (!active.contains(seat)) {
            throw new NotActiveSeatException();
        }
    }

    private void requireAwaitingReady() throws WrongPhaseException {
        if (phase != Phase.AWAITING_START && phase != Phase.ROUND_OVER) {
            throw new WrongPhaseException();
        }
    }

    /** Moves on once every active seat is ready (a seat that left no longer counts). */
    private void advanceIfEveryoneReady(List<KiloDeMerdeEvent> events) {
        if (ready.containsAll(active)) {
            advance(events);
        }
    }

    /** Leaves the waiting phase: starts the first round, ends the game, or deals the next round. */
    private void advance(List<KiloDeMerdeEvent> events) {
        if (phase == Phase.AWAITING_START) {
            ready.clear();
            phase = Phase.PASSING;
            events.add(new RoundDealt(round));
        } else if (roundsPlayed >= options.rounds().count()) {
            finish(KiloDeMerdeResult.Reason.ROUNDS_PLAYED, events);
        } else if (pile.isEmpty()) {
            finish(KiloDeMerdeResult.Reason.PILE_EMPTY, events);
        } else {
            deal();
            events.add(new RoundDealt(round));
        }
    }

    /**
     * Starts a round: a freshly shuffled hand deck for the active seats, four cards each, step 1. The kilo pile and
     * the kilos are kept. The deck is drawn aside, so the state changes only once nothing can fail any more.
     */
    private void deal() {
        List<Card> deck = new ArrayList<>(deckSource.shuffleHandDeck(id, KiloDeck.handDeck(active.size())));
        int next = 0;
        Map<PlayerId, List<Card>> dealt = new LinkedHashMap<>();
        for (PlayerId seat : active) {
            dealt.put(seat, new ArrayList<>(deck.subList(next, next + KiloDeMerdeRules.HAND_SIZE)));
            next += KiloDeMerdeRules.HAND_SIZE;
        }
        round = roundsPlayed + 1;
        step = 1;
        phase = Phase.PASSING;
        hands.replaceAll((seat, hand) -> new ArrayList<>());
        hands.putAll(dealt);
        chosen.clear();
        lastGiven.clear();
        lastReceived.clear();
        slapOrder.clear();
        ready.clear();
        lastOutcome = null;
        passHistory.clear();
        active.forEach(seat -> passHistory.put(seat, new ArrayList<>()));
    }

    /** Every chosen card moves to the next active seat at once; the round stalls after the stall limit (A10). */
    private void resolveStep(List<KiloDeMerdeEvent> events) {
        int count = active.size();
        Map<PlayerId, List<Card>> next = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            PlayerId seat = active.get(i);
            PlayerId giver = active.get(Math.floorMod(i - 1, count));
            List<Card> hand = new ArrayList<>(hands.get(seat));
            hand.set(hand.indexOf(chosen.get(seat)), chosen.get(giver));
            next.put(seat, hand);
        }
        for (int i = 0; i < count; i++) {
            PlayerId seat = active.get(i);
            Card given = chosen.get(seat);
            Card received = chosen.get(active.get(Math.floorMod(i - 1, count)));
            lastGiven.put(seat, given);
            lastReceived.put(seat, received);
            passHistory.get(seat).add(new KiloDeMerdeBotView.Pass(step, given, received));
        }
        hands.putAll(next);
        chosen.clear();
        step++;
        events.add(new StepResolved(round, step));
        if (step - 1 >= KiloDeMerdeRules.STALL_LIMIT) {
            endRound(RoundOutcome.Kind.STALLED, List.of(), events);
        }
    }

    /**
     * The seat draws the top kilo card (A9: nothing when the pile is empty). A 7 is the flush: the seat's kilo cards
     * and the 7 go back into the pile, which is shuffled (A8).
     */
    private void drawFor(PlayerId seat, List<KiloDraw> draws, List<KiloDeMerdeEvent> events) {
        if (pile.isEmpty()) {
            draws.add(new KiloDraw(seat, null, false, kilos(seat)));
            events.add(new KiloDrawn(seat, null, kilos(seat)));
            return;
        }
        Card card = pile.get(0);
        if (KiloValue.isFlush(card)) {
            List<Card> returned = new ArrayList<>(kiloCards.get(seat));
            returned.add(card);
            List<Card> merged = new ArrayList<>(pile.subList(1, pile.size()));
            merged.addAll(returned);
            pile = new ArrayList<>(deckSource.shufflePile(id, merged));
            kiloCards.put(seat, new ArrayList<>());
            draws.add(new KiloDraw(seat, card, true, 0));
            events.add(new Flushed(seat, returned.size()));
        } else {
            pile.remove(0);
            kiloCards.get(seat).add(card);
            draws.add(new KiloDraw(seat, card, false, kilos(seat)));
            events.add(new KiloDrawn(seat, card, kilos(seat)));
        }
    }

    /** The round is over: every hand is revealed in the outcome, and it counts towards the total. */
    private void endRound(RoundOutcome.Kind kind, List<KiloDraw> draws, List<KiloDeMerdeEvent> events) {
        List<PlayerId> holders = active.stream().filter(seat -> KiloDeMerdeRules.isFourOfAKind(hands.get(seat)))
                .toList();
        Map<PlayerId, List<Card>> revealed = new LinkedHashMap<>();
        active.forEach(seat -> revealed.put(seat, List.copyOf(hands.get(seat))));
        PlayerId first = slapOrder.isEmpty() ? null : slapOrder.get(0);
        lastOutcome = new RoundOutcome(kind, first, slapOrder, holders, draws, revealed);
        roundsPlayed++;
        phase = Phase.ROUND_OVER;
        events.add(new RoundOver(lastOutcome));
    }

    /** Ranks the seats still playing by kilos (equal kilos share a place, A13) below nobody who left earlier. */
    private void finish(KiloDeMerdeResult.Reason reason, List<KiloDeMerdeEvent> events) {
        Map<PlayerId, Integer> finalPlaces = new LinkedHashMap<>(places);
        Map<PlayerId, Integer> finalKilos = new LinkedHashMap<>(leftKilos);
        for (PlayerId seat : active) {
            int fewer = (int) active.stream().filter(other -> kilos(other) < kilos(seat)).count();
            finalPlaces.put(seat, 1 + fewer);
            finalKilos.put(seat, kilos(seat));
        }
        result = new KiloDeMerdeResult(finalPlaces, finalKilos, reason);
        phase = Phase.FINISHED;
        events.add(new GameFinished(result));
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

    /** Every seat of the game, including the ones that left. */
    @Override
    public synchronized List<PlayerId> getPlayerIds() {
        return List.copyOf(seats);
    }

    public KiloDeMerdeOptions options() {
        return options;
    }

    public synchronized int version() {
        return version;
    }

    public synchronized Phase phase() {
        return phase;
    }

    /** The number of the round being played, or the one just played between two rounds. */
    public synchronized int round() {
        return round;
    }

    /** Rounds that ended (a voided round does not count). */
    public synchronized int roundsPlayed() {
        return roundsPlayed;
    }

    public int roundsTotal() {
        return options.rounds().count();
    }

    public synchronized int step() {
        return step;
    }

    /** The number of the last race opened (0 before the first); a race timeout is keyed on it. */
    public synchronized int raceId() {
        return raceId;
    }

    /** The seats still playing, in seat order. */
    public synchronized List<PlayerId> activeSeats() {
        return List.copyOf(active);
    }

    /** A copy of the seat's hand; empty for a seat that left or is unknown. */
    public synchronized List<Card> hand(PlayerId seat) {
        return List.copyOf(hands.getOrDefault(seat, List.of()));
    }

    /** The card the seat chose to pass this step (private to the seat). */
    public synchronized Optional<Card> chosen(PlayerId seat) {
        return Optional.ofNullable(chosen.get(seat));
    }

    /** Whether the seat confirmed it is ready (public: the table sees who it waits for). */
    public synchronized boolean isReady(PlayerId seat) {
        return ready.contains(seat);
    }

    /** Whether the seat chose this step (public: the table sees who it waits for). */
    public synchronized boolean hasChosen(PlayerId seat) {
        return chosen.containsKey(seat);
    }

    /** The card the seat received at the last resolved step of this round. */
    public synchronized Optional<Card> lastReceived(PlayerId seat) {
        return Optional.ofNullable(lastReceived.get(seat));
    }

    /** The card the seat gave at the last resolved step of this round. */
    public synchronized Optional<Card> lastGiven(PlayerId seat) {
        return Optional.ofNullable(lastGiven.get(seat));
    }

    /** The seat's own given and received cards this round, oldest step first (for its bot). */
    public synchronized List<KiloDeMerdeBotView.Pass> passHistory(PlayerId seat) {
        return List.copyOf(passHistory.getOrDefault(seat, List.of()));
    }

    /** The seats that slapped in the open race (or the round just ended), first slapper first. */
    public synchronized List<PlayerId> slapOrder() {
        return List.copyOf(slapOrder);
    }

    /** The seat a card passed by {@code seat} goes to. */
    public synchronized PlayerId leftOf(PlayerId seat) {
        return active.get(Math.floorMod(indexOf(seat) + 1, active.size()));
    }

    /** The seat a card received by {@code seat} comes from. */
    public synchronized PlayerId rightOf(PlayerId seat) {
        return active.get(Math.floorMod(indexOf(seat) - 1, active.size()));
    }

    private int indexOf(PlayerId seat) {
        int index = active.indexOf(seat);
        if (index < 0) {
            throw new IllegalArgumentException("Seat " + seat + " is not playing");
        }
        return index;
    }

    /** The kilo cards the seat holds, in the order drawn. */
    public synchronized List<Card> kiloCards(PlayerId seat) {
        return List.copyOf(kiloCards.getOrDefault(seat, List.of()));
    }

    public synchronized int kilos(PlayerId seat) {
        return KiloValue.total(kiloCards.getOrDefault(seat, List.of()));
    }

    public synchronized int pileSize() {
        return pile.size();
    }

    /** How the last round ended, until the next deal. */
    public synchronized Optional<RoundOutcome> lastOutcome() {
        return Optional.ofNullable(lastOutcome);
    }

    /** What the seat may do now. */
    public synchronized List<Action> availableActions(PlayerId seat) {
        if (!active.contains(seat)) {
            return List.of();
        }
        if (phase == Phase.PASSING) {
            return chosen.containsKey(seat) ? List.of(Action.SLAP) : List.of(Action.PASS, Action.SLAP);
        }
        if ((phase == Phase.AWAITING_START || phase == Phase.ROUND_OVER) && !ready.contains(seat)) {
            return List.of(Action.READY);
        }
        if (phase == Phase.RACE && !slapOrder.contains(seat)) {
            return List.of(Action.SLAP);
        }
        return List.of();
    }

    public synchronized Optional<KiloDeMerdeResult> result() {
        return Optional.ofNullable(result);
    }
}
