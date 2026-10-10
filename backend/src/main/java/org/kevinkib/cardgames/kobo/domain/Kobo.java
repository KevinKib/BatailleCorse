package org.kevinkib.cardgames.kobo.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.CardsService;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Rank;
import org.kevinkib.cards.domain.Visibility;
import org.kevinkib.cards.domain.deck.Deck;
import org.kevinkib.cards.domain.deck.DeckCreationOptions;
import org.kevinkib.cards.domain.deck.DeckType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * A Kobo match: rounds played until a total goes above 100. All state changes go through
 * synchronized methods, so concurrent commands (notably the matching-discard race) are processed
 * one at a time and the first one processed wins. A rejected command throws a
 * {@link KoboException} and changes nothing (the version does not move).
 */
public class Kobo implements Game {

    private static final Set<Phase> LIVE_PHASES =
            Set.of(Phase.DRAW, Phase.DECISION, Phase.POWER, Phase.KING_DECISION, Phase.TURN_END);

    private static final class KoboPlayer {
        final PlayerId id;
        Tableau tableau;
        int total;

        KoboPlayer(PlayerId id, Tableau tableau) {
            this.id = id;
            this.tableau = tableau;
        }
    }

    private final GameId id;
    private final Random random;
    private final List<KoboPlayer> players = new ArrayList<>();
    private final Deque<Card> drawPile = new ArrayDeque<>();
    private final Deque<Card> discard = new ArrayDeque<>();
    private final Set<PlayerId> ready = new LinkedHashSet<>();
    private final Map<PlayerId, PrivateReveal> reveals = new LinkedHashMap<>();
    private final Deque<PlayerId> finalQueue = new ArrayDeque<>();

    private Phase phase;
    private PlayerId currentSeat;
    private PlayerId startSeat;
    private int round = 1;
    private int version;
    private Card drawn;
    private Power pendingPower = Power.NONE;
    private SlotRef kingTarget;
    private int kingRevision;
    private boolean firstDrawDone;
    private boolean finalTurns;
    private PlayerId announcer;
    private boolean revealed;
    private RoundResult lastRound;
    private KoboResult result = KoboResult.ONGOING;

    public Kobo(GameId id, int nbPlayers) {
        this(id, nbPlayers, new Random().nextLong());
    }

    public Kobo(GameId id, int nbPlayers, long seed) {
        if (nbPlayers < KoboRules.MIN_PLAYERS || nbPlayers > KoboRules.MAX_PLAYERS) {
            throw new IllegalArgumentException("Kobo is played by 2 to 6 players, not " + nbPlayers);
        }
        this.id = id;
        this.random = new Random(seed);
        for (int i = 0; i < nbPlayers; i++) {
            players.add(new KoboPlayer(new PlayerId(i), new Tableau(List.of())));
        }
        this.startSeat = players.get(0).id;
        deal();
    }

    /** Test constructor: explicit tableaux and piles (top of the piles first), in a given phase. */
    Kobo(GameId id, long seed, List<List<Card>> tableaux, List<Card> drawPileTopFirst,
         List<Card> discardTopFirst, Phase phase, PlayerId currentSeat) {
        this.id = id;
        this.random = new Random(seed);
        for (int i = 0; i < tableaux.size(); i++) {
            players.add(new KoboPlayer(new PlayerId(i), new Tableau(tableaux.get(i))));
        }
        this.startSeat = players.get(0).id;
        this.drawPile.addAll(drawPileTopFirst);
        this.discard.addAll(discardTopFirst);
        this.phase = phase;
        this.currentSeat = currentSeat;
        this.firstDrawDone = phase != Phase.MEMORISING;
    }

    // ---------------------------------------------------------------- set-up

    private void deal() {
        Deck deck = new CardsService().createDeck(DeckType.FRENCH,
                new DeckCreationOptions(Visibility.HIDDEN, random.nextLong()));
        drawPile.clear();
        discard.clear();
        while (deck.getSize() > 0) {
            drawPile.addLast(deck.draw());
        }
        for (KoboPlayer player : players) {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < KoboRules.CARDS_PER_PLAYER; i++) {
                hand.add(drawPile.pollFirst());
            }
            player.tableau = new Tableau(hand);
        }
        drawn = null;
        pendingPower = Power.NONE;
        kingTarget = null;
        announcer = null;
        finalTurns = false;
        finalQueue.clear();
        reveals.clear();
        ready.clear();
        firstDrawDone = false;
        revealed = false;
        phase = Phase.MEMORISING;
        currentSeat = startSeat;
    }

    // ------------------------------------------------------------- barriers

    public synchronized KoboEvent ready(PlayerId seat) throws KoboException {
        requireNotFinished();
        player(seat);
        if (phase != Phase.MEMORISING && phase != Phase.ROUND_OVER) {
            throw new WrongPhaseException(phase, "MEMORISING or ROUND_OVER");
        }
        if (!ready.add(seat)) {
            throw new AlreadyReadyException(seat);
        }
        boolean opened = openBarrierIfComplete();
        version++;
        return new KoboEvent.Ready(seat, opened);
    }

    private boolean openBarrierIfComplete() {
        if (!ready.containsAll(playerIds())) {
            return false;
        }
        ready.clear();
        if (phase == Phase.MEMORISING) {
            phase = Phase.DRAW;
            currentSeat = startSeat;
        } else {
            round++;
            startSeat = nextSeatAfter(startSeat);
            deal();
        }
        return true;
    }

    // ----------------------------------------------------------------- turn

    public synchronized KoboEvent draw(PlayerId seat) throws KoboException {
        requireTurn(seat, Phase.DRAW);
        reveals.remove(seat);
        Optional<Card> card = drawFromPile();
        firstDrawDone = true;
        version++;
        if (card.isEmpty()) {
            phase = Phase.TURN_END;
            return new KoboEvent.DrawSkipped(seat);
        }
        drawn = card.get();
        phase = Phase.DECISION;
        return new KoboEvent.Drawn(seat);
    }

    public synchronized KoboEvent swapDrawn(PlayerId seat, int slot) throws KoboException {
        requireTurn(seat, Phase.DECISION);
        Card old = player(seat).tableau.replace(slot, drawn);
        discard.addFirst(old);
        drawn = null;
        phase = Phase.TURN_END;
        reveals.remove(seat);
        version++;
        return new KoboEvent.Swapped(seat, slot, old);
    }

    public synchronized KoboEvent discardDrawn(PlayerId seat) throws KoboException {
        requireTurn(seat, Phase.DECISION);
        Card card = drawn;
        drawn = null;
        discard.addFirst(card);
        pendingPower = Power.of(card);
        phase = pendingPower == Power.NONE ? Phase.TURN_END : Phase.POWER;
        reveals.remove(seat);
        version++;
        return new KoboEvent.DiscardedDrawn(seat, card, pendingPower);
    }

    public synchronized KoboEvent skipPower(PlayerId seat) throws KoboException {
        requireNotFinished();
        player(seat);
        if (!seat.equals(currentSeat)) {
            throw new NotPlayersTurnException(seat);
        }
        if (phase != Phase.POWER && phase != Phase.KING_DECISION) {
            throw new WrongPhaseException(phase, "POWER or KING_DECISION");
        }
        endPower(seat);
        return new KoboEvent.PowerSkipped(seat);
    }

    public synchronized KoboEvent peek(PlayerId seat, SlotRef target) throws KoboException {
        requireTurn(seat, Phase.POWER);
        if (pendingPower != Power.PEEK_OWN && pendingPower != Power.PEEK_OTHER && pendingPower != Power.KING) {
            throw new WrongPhaseException("The pending power is not a peek");
        }
        boolean own = target.seat().equals(seat);
        if (pendingPower == Power.PEEK_OWN && !own) {
            throw new InvalidSlotException("This power looks at one of your own cards");
        }
        if (pendingPower != Power.PEEK_OWN && own) {
            throw new InvalidSlotException("This power looks at another player's card");
        }
        Tableau tableau = targetTableau(target);
        Card card = tableau.card(target.slot());
        int revision = tableau.revision(target.slot());
        reveals.put(seat, new PrivateReveal(target, card, revision));
        if (pendingPower == Power.KING) {
            kingTarget = target;
            kingRevision = revision;
            phase = Phase.KING_DECISION;
        } else {
            pendingPower = Power.NONE;
            phase = Phase.TURN_END;
        }
        version++;
        return new KoboEvent.Peeked(seat, target);
    }

    public synchronized KoboEvent blindSwap(PlayerId seat, int ownSlot, SlotRef other) throws KoboException {
        requireTurn(seat, Phase.POWER);
        if (pendingPower != Power.BLIND_SWAP) {
            throw new WrongPhaseException("The pending power is not a swap");
        }
        if (other.seat().equals(seat)) {
            throw new InvalidSlotException("Swap with a card of another player");
        }
        Tableau own = player(seat).tableau;
        Tableau theirs = targetTableau(other);
        Card mine = own.card(ownSlot);
        Card theirCard = theirs.card(other.slot());
        own.replace(ownSlot, theirCard);
        theirs.replace(other.slot(), mine);
        endPower(seat);
        return new KoboEvent.BlindSwapped(seat, new SlotRef(seat, ownSlot), other);
    }

    public synchronized KoboEvent kingSwap(PlayerId seat, int ownSlot) throws KoboException {
        requireTurn(seat, Phase.KING_DECISION);
        KoboPlayer owner = findPlayer(kingTarget.seat());
        if (owner == null || !owner.tableau.isOccupied(kingTarget.slot())) {
            throw new EmptySlotException("The card you looked at is gone");
        }
        if (owner.tableau.revision(kingTarget.slot()) != kingRevision) {
            throw new StaleSlotException("The card you looked at has been replaced");
        }
        Tableau own = player(seat).tableau;
        Card mine = own.card(ownSlot);
        Card theirCard = owner.tableau.card(kingTarget.slot());
        own.replace(ownSlot, theirCard);
        owner.tableau.replace(kingTarget.slot(), mine);
        SlotRef target = kingTarget;
        endPower(seat);
        return new KoboEvent.KingSwapped(seat, new SlotRef(seat, ownSlot), target);
    }

    private void endPower(PlayerId seat) {
        pendingPower = Power.NONE;
        kingTarget = null;
        phase = Phase.TURN_END;
        reveals.remove(seat);
        version++;
    }

    public synchronized KoboEvent endTurn(PlayerId seat) throws KoboException {
        requireTurn(seat, Phase.TURN_END);
        reveals.remove(seat);
        version++;
        if (finalTurns) {
            finalQueue.remove(seat);
            if (finalQueue.isEmpty()) {
                resolveRound();
            } else {
                currentSeat = finalQueue.peekFirst();
                phase = Phase.DRAW;
            }
        } else {
            currentSeat = nextSeatAfter(seat);
            phase = Phase.DRAW;
        }
        return new KoboEvent.TurnEnded(seat);
    }

    public synchronized KoboEvent announceKobo(PlayerId seat) throws KoboException {
        requireTurn(seat, Phase.TURN_END);
        if (finalTurns || announcer != null) {
            throw new WrongPhaseException("Kobo has already been announced in this round");
        }
        announcer = seat;
        finalTurns = true;
        finalQueue.clear();
        int start = indexOf(seat);
        for (int i = 1; i < players.size(); i++) {
            finalQueue.addLast(players.get((start + i) % players.size()).id);
        }
        currentSeat = finalQueue.peekFirst();
        phase = Phase.DRAW;
        reveals.remove(seat);
        version++;
        return new KoboEvent.KoboAnnounced(seat);
    }

    // ---------------------------------------------------- matching discard

    /**
     * Tries to put the targeted card (anyone's) on the discard pile. {@code expectedTopRank} is the
     * rank the caller saw on top of the pile: if the top moved meanwhile the attempt is rejected
     * without penalty. A wrong rank costs the caller a penalty card.
     */
    public KoboEvent matchDiscard(PlayerId seat, SlotRef target, Integer giveSlot, Rank expectedTopRank)
            throws KoboException {
        return matchDiscard(seat, target, giveSlot, expectedTopRank, null);
    }

    /**
     * Same, and when {@code expectedRevision} is not null the attempt is also rejected without penalty
     * if the targeted slot changed since the caller saw it (a card was given into it after a match).
     */
    public synchronized KoboEvent matchDiscard(PlayerId seat, SlotRef target, Integer giveSlot, Rank expectedTopRank,
                                               Integer expectedRevision) throws KoboException {
        requireNotFinished();
        if (!LIVE_PHASES.contains(phase)) {
            throw new WrongPhaseException(phase, "a live round phase");
        }
        KoboPlayer actor = player(seat);
        Tableau targetTableau = targetTableau(target);
        Card targetCard = targetTableau.card(target.slot());
        if (expectedRevision != null && targetTableau.revision(target.slot()) != expectedRevision) {
            throw new StaleSlotException("That card changed since you saw it");
        }
        Card top = discard.peekFirst();
        if (top == null) {
            throw new NothingToMatchException();
        }
        if (expectedTopRank == null) {
            throw new IllegalArgumentException("expectedTopRank is required");
        }
        if (!top.getRank().equals(expectedTopRank)) {
            throw new StaleDiscardException();
        }
        if (!targetCard.getRank().equals(top.getRank())) {
            Optional<Card> penalty = drawFromPile();
            penalty.ifPresent(actor.tableau::append);
            version++;
            return new KoboEvent.MatchPenalised(seat, penalty.isPresent());
        }
        boolean own = target.seat().equals(seat);
        if (own) {
            discard.addFirst(targetTableau.remove(target.slot()));
            version++;
            return new KoboEvent.MatchSucceeded(seat, target, targetCard, null);
        }
        boolean mustGive = actor.tableau.handSize() > 0;
        if (mustGive && (giveSlot == null || !actor.tableau.isOccupied(giveSlot))) {
            throw new InvalidGiveSlotException("Choose one of your own cards to give");
        }
        discard.addFirst(targetTableau.remove(target.slot()));
        SlotRef given = null;
        if (mustGive) {
            targetTableau.put(target.slot(), actor.tableau.remove(giveSlot));
            given = new SlotRef(seat, giveSlot);
        }
        version++;
        return new KoboEvent.MatchSucceeded(seat, target, targetCard, given);
    }

    // ------------------------------------------------------- round and game

    private void resolveRound() {
        revealed = true;
        if (announcer != null && RoundScoring.announcerWins(handValues(), announcer)) {
            phase = Phase.GIFT;
            currentSeat = announcer;
        } else {
            finalizeRound(null);
        }
    }

    public synchronized KoboEvent giveTen(PlayerId seat, PlayerId target) throws KoboException {
        requireTurn(seat, Phase.GIFT);
        if (seat.equals(target) || findPlayer(target) == null) {
            throw new InvalidGiftTargetException("Give the points to another player of the table");
        }
        finalizeRound(target);
        version++;
        return new KoboEvent.TenGiven(seat, target);
    }

    private Map<PlayerId, Integer> handValues() {
        Map<PlayerId, Integer> hands = new LinkedHashMap<>();
        players.forEach(p -> hands.put(p.id, p.tableau.points()));
        return hands;
    }

    private void finalizeRound(PlayerId giftTarget) {
        Map<PlayerId, Integer> before = new LinkedHashMap<>();
        players.forEach(p -> before.put(p.id, p.total));
        RoundResult scored = RoundScoring.score(handValues(), announcer, giftTarget, before);
        players.forEach(p -> p.total = scored.totalsAfter().get(p.id));
        lastRound = scored;
        revealed = true;
        finalTurns = false;
        finalQueue.clear();
        reveals.clear();
        ready.clear();
        if (players.stream().anyMatch(p -> p.total > KoboRules.END_SCORE)) {
            phase = Phase.FINISHED;
            result = new KoboResult(lowestTotalWinners(), KoboResult.Reason.SCORE);
        } else {
            phase = Phase.ROUND_OVER;
        }
    }

    private List<PlayerId> lowestTotalWinners() {
        int min = players.stream().mapToInt(p -> p.total).min().orElse(0);
        return players.stream().filter(p -> p.total == min).map(p -> p.id).toList();
    }

    // --------------------------------------------------------------- forfeit

    @Override
    public synchronized void forfeit(PlayerId playerId) {
        if (isFinished()) {
            return;
        }
        KoboPlayer leaving = findPlayer(playerId);
        if (leaving == null) {
            return;
        }
        boolean wasCurrent = playerId.equals(currentSeat);
        boolean wasAnnouncer = playerId.equals(announcer);
        players.remove(leaving);
        leaving.tableau.cards().forEach(drawPile::addLast);
        if (wasCurrent && drawn != null) {
            drawPile.addLast(drawn);
            drawn = null;
        }
        ready.remove(playerId);
        reveals.remove(playerId);
        finalQueue.remove(playerId);
        if (wasAnnouncer) {
            announcer = null;
        }
        version++;

        if (players.size() == 1) {
            phase = Phase.FINISHED;
            result = new KoboResult(List.of(players.get(0).id), KoboResult.Reason.FORFEIT);
            return;
        }
        switch (phase) {
            case MEMORISING, ROUND_OVER -> openBarrierIfComplete();
            case GIFT -> {
                if (wasAnnouncer) {
                    finalizeRound(null);
                }
            }
            case DRAW, DECISION, POWER, KING_DECISION, TURN_END -> {
                if (wasCurrent) {
                    pendingPower = Power.NONE;
                    kingTarget = null;
                    if (finalTurns) {
                        if (finalQueue.isEmpty()) {
                            resolveRound();
                        } else {
                            currentSeat = finalQueue.peekFirst();
                            phase = Phase.DRAW;
                        }
                    } else {
                        currentSeat = nextSeatAfter(playerId);
                        phase = Phase.DRAW;
                    }
                }
            }
            default -> { }
        }
    }

    // --------------------------------------------------------------- helpers

    private Optional<Card> drawFromPile() {
        if (drawPile.isEmpty() && discard.size() > 1) {
            Card top = discard.pollFirst();
            List<Card> rest = new ArrayList<>(discard);
            discard.clear();
            Collections.shuffle(rest, random);
            drawPile.addAll(rest);
            discard.addFirst(top);
        }
        return Optional.ofNullable(drawPile.pollFirst());
    }

    private void requireNotFinished() throws FinishedGameException {
        if (phase == Phase.FINISHED) {
            throw new FinishedGameException();
        }
    }

    private void requireTurn(PlayerId seat, Phase expected) throws KoboException {
        requireNotFinished();
        player(seat);
        if (!seat.equals(currentSeat)) {
            throw new NotPlayersTurnException(seat);
        }
        if (phase != expected) {
            throw new WrongPhaseException(phase, expected.name());
        }
    }

    private KoboPlayer player(PlayerId seat) {
        KoboPlayer player = findPlayer(seat);
        if (player == null) {
            throw new IllegalArgumentException("Unknown player " + seat);
        }
        return player;
    }

    private KoboPlayer findPlayer(PlayerId seat) {
        return players.stream().filter(p -> p.id.equals(seat)).findFirst().orElse(null);
    }

    private Tableau targetTableau(SlotRef ref) throws InvalidSlotException {
        KoboPlayer owner = findPlayer(ref.seat());
        if (owner == null) {
            throw new InvalidSlotException("Unknown player " + ref.seat());
        }
        return owner.tableau;
    }

    private int indexOf(PlayerId seat) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id.equals(seat)) {
                return i;
            }
        }
        return -1;
    }

    /** Next remaining seat in seat order after {@code seat}, which may itself have left. */
    private PlayerId nextSeatAfter(PlayerId seat) {
        return players.stream().map(p -> p.id).filter(p -> p.id() > seat.id()).findFirst()
                .orElse(players.get(0).id);
    }

    private List<PlayerId> playerIds() {
        return players.stream().map(p -> p.id).toList();
    }

    // --------------------------------------------------------------- queries

    @Override
    public GameId getId() {
        return id;
    }

    @Override
    public synchronized List<PlayerId> getPlayerIds() {
        return playerIds();
    }

    @Override
    public synchronized boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    public synchronized int version() {
        return version;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized PlayerId currentSeat() {
        return currentSeat;
    }

    public synchronized int round() {
        return round;
    }

    public synchronized int drawPileSize() {
        return drawPile.size();
    }

    public synchronized int discardSize() {
        return discard.size();
    }

    public synchronized Optional<Card> discardTop() {
        return Optional.ofNullable(discard.peekFirst());
    }

    public synchronized Power pendingPower() {
        return pendingPower;
    }

    public synchronized Optional<PlayerId> announcer() {
        return Optional.ofNullable(announcer);
    }

    public synchronized List<PlayerId> pendingFinalTurns() {
        return List.copyOf(finalQueue);
    }

    public synchronized Set<PlayerId> readySeats() {
        return Set.copyOf(ready);
    }

    public synchronized boolean isRevealed() {
        return revealed;
    }

    public synchronized Optional<RoundResult> lastRoundResult() {
        return Optional.ofNullable(lastRound);
    }

    public synchronized KoboResult result() {
        return result;
    }

    public synchronized int total(PlayerId seat) {
        return player(seat).total;
    }

    public synchronized Map<PlayerId, Integer> totals() {
        Map<PlayerId, Integer> totals = new LinkedHashMap<>();
        players.forEach(p -> totals.put(p.id, p.total));
        return totals;
    }

    public synchronized List<Tableau.Slot> slots(PlayerId seat) {
        return player(seat).tableau.slots();
    }

    public synchronized int handSize(PlayerId seat) {
        return player(seat).tableau.handSize();
    }

    public synchronized int handPoints(PlayerId seat) {
        return player(seat).tableau.points();
    }

    /** Every card on the table (tableaux, piles, held card): always 52 for a live round. */
    public synchronized int cardsInPlay() {
        return players.stream().mapToInt(p -> p.tableau.handSize()).sum()
                + drawPile.size() + discard.size() + (drawn == null ? 0 : 1);
    }

    /** The drawn card, visible to the current player only while they decide what to do with it. */
    public synchronized Optional<Card> drawnCard(PlayerId seat) {
        return seat.equals(currentSeat) && phase == Phase.DECISION ? Optional.ofNullable(drawn) : Optional.empty();
    }

    public synchronized boolean hasDrawn() {
        return drawn != null;
    }

    public synchronized Optional<PrivateReveal> revealFor(PlayerId seat) {
        return Optional.ofNullable(reveals.get(seat));
    }

    /** The viewer's slots 2 and 3 (their top row), only until the round's first draw. */
    public synchronized Map<Integer, Card> startingCards(PlayerId seat) {
        Map<Integer, Card> cards = new LinkedHashMap<>();
        KoboPlayer player = findPlayer(seat);
        if (player == null || firstDrawDone || (phase != Phase.MEMORISING && phase != Phase.DRAW)) {
            return cards;
        }
        for (int slot : new int[]{2, 3}) {
            if (player.tableau.isOccupied(slot)) {
                try {
                    cards.put(slot, player.tableau.card(slot));
                } catch (KoboException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return cards;
    }

    public synchronized List<Action> availableActions(PlayerId seat) {
        List<Action> actions = new ArrayList<>();
        if (isFinished() || findPlayer(seat) == null) {
            return actions;
        }
        boolean current = seat.equals(currentSeat);
        switch (phase) {
            case MEMORISING, ROUND_OVER -> {
                if (!ready.contains(seat)) {
                    actions.add(Action.READY);
                }
            }
            case DRAW -> {
                if (current) {
                    actions.add(Action.DRAW);
                }
            }
            case DECISION -> {
                if (current) {
                    actions.add(Action.SWAP_DRAWN);
                    actions.add(Action.DISCARD_DRAWN);
                }
            }
            case POWER -> {
                if (current) {
                    actions.add(pendingPower == Power.BLIND_SWAP ? Action.BLIND_SWAP : Action.PEEK);
                    actions.add(Action.SKIP_POWER);
                }
            }
            case KING_DECISION -> {
                if (current) {
                    actions.add(Action.KING_SWAP);
                    actions.add(Action.SKIP_POWER);
                }
            }
            case TURN_END -> {
                if (current) {
                    actions.add(Action.END_TURN);
                    if (!finalTurns && announcer == null) {
                        actions.add(Action.ANNOUNCE_KOBO);
                    }
                }
            }
            case GIFT -> {
                if (current) {
                    actions.add(Action.GIVE_TEN);
                }
            }
            default -> { }
        }
        if (LIVE_PHASES.contains(phase) && !discard.isEmpty()) {
            actions.add(Action.MATCH_DISCARD);
        }
        return actions;
    }

    // --------------------------------------------------- test-only mutators

    synchronized void setDrawn(Card card) {
        this.drawn = card;
    }

    synchronized void setTotal(PlayerId seat, int total) {
        player(seat).total = total;
    }

    synchronized void setPendingPower(Power power) {
        this.pendingPower = power;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Kobo) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
