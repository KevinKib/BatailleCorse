package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.bullshit.domain.claim.AscendingRankClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;
import org.kevinkib.cardgames.bullshit.domain.pile.Discard;
import org.kevinkib.cardgames.bullshit.domain.pile.DiscardPile;
import org.kevinkib.cardgames.bullshit.domain.player.Player;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cards.CardsService;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Visibility;
import org.kevinkib.cards.domain.deck.Deck;
import org.kevinkib.cards.domain.deck.DeckCreationOptions;
import org.kevinkib.cards.domain.deck.DeckType;
import org.kevinkib.cards.domain.hand.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Bullshit implements Game {

    private final GameId id;
    private final List<Player> players;
    private final ClaimMode claimMode;
    private final DiscardPile discardPile;
    private ClaimTarget currentTarget;
    private int currentPlayerIndex;
    private Discard lastDiscard;
    private PlayerId pendingWinner;
    private Result result;

    public Bullshit(GameId id, int nbPlayers) {
        this(id, nbPlayers, new AscendingRankClaimMode());
    }

    public Bullshit(GameId id, int nbPlayers, ClaimMode claimMode) {
        this(id, deal(nbPlayers), claimMode, claimMode.initial(), 0);
    }

    Bullshit(GameId id, List<Player> players, ClaimMode claimMode, ClaimTarget currentTarget, int currentPlayerIndex) {
        this.id = id;
        this.players = new ArrayList<>(players);
        this.claimMode = claimMode;
        this.currentTarget = currentTarget;
        this.currentPlayerIndex = currentPlayerIndex;
        this.discardPile = new DiscardPile();
        this.lastDiscard = null;
        this.pendingWinner = null;
        this.result = Result.ONGOING;
    }

    private static List<Player> deal(int nbPlayers) {
        Deck deck = new CardsService().createDeck(DeckType.FRENCH, new DeckCreationOptions(Visibility.HIDDEN));
        List<Hand> hands = deck.distributeAll(nbPlayers);
        List<Player> dealt = new ArrayList<>();
        for (int i = 0; i < nbPlayers; i++) {
            dealt.add(new Player(i, hands.get(i)));
        }
        return dealt;
    }

    public synchronized void discard(PlayerId playerId, List<Card> cards)
            throws FinishedGameException, NotPlayersTurnException, InvalidDiscardCountException, CardsNotInHandException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (!currentPlayer().id().equals(playerId)) {
            throw new NotPlayersTurnException(playerId);
        }
        if (pendingWinner != null) {
            // Reached only by the legitimate next player (turn check above). Choosing to discard
            // rather than call BS is a decline: the unchallenged claim stands and the pending
            // winner wins. The submitted cards are intentionally not applied.
            result = new Result(playerById(pendingWinner));
            return;
        }
        if (cards.isEmpty() || cards.size() > 4) {
            throw new InvalidDiscardCountException(cards.size());
        }
        Player player = currentPlayer();
        if (!player.possessesAll(cards)) {
            throw new CardsNotInHandException(playerId);
        }

        player.discard(cards);
        discardPile.add(cards);
        lastDiscard = new Discard(playerId, currentTarget, List.copyOf(cards));
        currentTarget = claimMode.next(currentTarget);
        advanceTurn();

        if (!player.hasAnyCards()) {
            pendingWinner = playerId;
        }
    }

    public synchronized CallBullshitOutcome callBullshit(PlayerId callerId)
            throws FinishedGameException, CannotCallBullshitException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (lastDiscard == null || callerId.equals(lastDiscard.claimant())) {
            throw new CannotCallBullshitException(callerId);
        }

        boolean truthful = claimMode.matches(lastDiscard.actualCards(), lastDiscard.claimedTarget());
        PlayerId claimantId = lastDiscard.claimant();
        PlayerId pickerId = truthful ? callerId : claimantId;

        playerById(pickerId).addCards(discardPile.takeAll());

        if (truthful && claimantId.equals(pendingWinner)) {
            result = new Result(playerById(claimantId));
        } else {
            if (claimantId.equals(pendingWinner)) {
                pendingWinner = null;
            }
            currentPlayerIndex = players.indexOf(playerById(pickerId));
        }
        lastDiscard = null;

        return new CallBullshitOutcome(truthful, pickerId);
    }

    @Override
    public synchronized void forfeit(PlayerId playerId) {
        if (isFinished()) {
            return;
        }
        int index = indexOf(playerId);
        if (index < 0) {
            return;
        }
        if (playerId.equals(pendingWinner)) {
            pendingWinner = null;
        }

        Player current = players.get(currentPlayerIndex);
        boolean removingCurrent = current.id().equals(playerId);
        players.remove(index);

        if (removingCurrent) {
            // Turn passes to whoever now occupies the removed slot (wrap to first if it was last).
            currentPlayerIndex = players.isEmpty() ? 0 : index % players.size();
        } else {
            // Keep the turn on the same player, whose index shifts down if the removal was before it.
            currentPlayerIndex = players.indexOf(current);
        }

        if (players.size() == 1) {
            result = new Result(players.get(0));
        }
    }

    public List<Action> getAvailableActions(PlayerId playerId) {
        List<Action> actions = new ArrayList<>();
        if (isFinished()) {
            return actions;
        }
        if (currentPlayer().id().equals(playerId)) {
            actions.add(Action.DISCARD);
        }
        if (lastDiscard != null && !playerId.equals(lastDiscard.claimant())) {
            actions.add(Action.CALL_BULLSHIT);
        }
        return actions;
    }

    private void advanceTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    private Player currentPlayer() {
        return players.get(currentPlayerIndex);
    }

    private Player playerById(PlayerId playerId) {
        return players.stream()
                .filter(player -> player.id().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player " + playerId));
    }

    private int indexOf(PlayerId playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(playerId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public GameId getId() {
        return id;
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    @Override
    public List<PlayerId> getPlayerIds() {
        return players.stream().map(Player::id).toList();
    }

    public Player getCurrentPlayer() {
        return currentPlayer();
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public ClaimTarget getCurrentTarget() {
        return currentTarget;
    }

    public Optional<Discard> getLastDiscard() {
        return Optional.ofNullable(lastDiscard);
    }

    public int getDiscardPileSize() {
        return discardPile.size();
    }

    @Override
    public boolean isFinished() {
        return result.isFinished();
    }

    public Player getWinner() {
        return result.winningPlayer();
    }

    public Optional<PlayerId> getPendingWinner() {
        return Optional.ofNullable(pendingWinner);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Bullshit) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
