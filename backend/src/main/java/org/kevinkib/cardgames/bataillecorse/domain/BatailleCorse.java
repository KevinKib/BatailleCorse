package org.kevinkib.cardgames.bataillecorse.domain;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.game.GameId;

import org.kevinkib.cardgames.bataillecorse.domain.slaprules.SlapRules;
import org.kevinkib.cardgames.bataillecorse.domain.penality.Penality;
import org.kevinkib.cardgames.bataillecorse.domain.penality.PutCardsUnderPile;
import org.kevinkib.cards.CardsService;
import org.kevinkib.cards.domain.*;
import org.kevinkib.cards.domain.deck.*;
import org.kevinkib.cards.domain.hand.Hand;
import org.kevinkib.cards.domain.hand.NoCardsException;
import org.kevinkib.cards.domain.pile.Pile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BatailleCorse implements Game {

    private final GameId id;
    private List<Player> players;
    private CentralPile pile;
    private SlapRules slapRules;
    private Penality penality;
    private IndexHandler indexHandler;
    private Result result;

    public BatailleCorse(GameId id, int nbPlayers) {
        this.id = id;
        initializePlayersAndHands(nbPlayers);
        initializeData();
    }

    public BatailleCorse(GameId id, List<Player> players, int currentPlayer, CentralPile pile, SlapRules slapRules, Penality penality) {
        this.id = id;
        this.players = players;
        this.pile = pile;
        this.slapRules = slapRules;
        this.penality = penality;
        this.indexHandler = new IndexHandler(currentPlayer, players, pile);
        this.result = Result.update(players, pile, slapRules);
    }

    public synchronized void send(Player player) throws NotPlayersTurnException, FullCentralPileException, FinishedGameException {
        checkIfPlayerCanSend(player);

        try {
            Card card = player.removeCardOnTop();
            pile.add(card, player);

            result = Result.update(players, pile, slapRules);
            indexHandler.update();
        } catch (NoCardsException e) {
            throw new IllegalStateException("A player should always have cards after the no cards check.");
        } catch (FullCentralPileException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized boolean slap(Player player) throws CannotSlapIfNoCardsInPileException, FinishedGameException {
        checkIfPlayerCanSlap(player);

        boolean successfulSlap = slapRules.applies(pile);

        if (successfulSlap) {
            List<Card> cards = pile.clearAndReturnCards();
            player.addCardsFromPile(cards);

            indexHandler.setCurrentPlayer(players.indexOf(player));
        }
        else {
            penality.apply(player, pile);
        }
        result = Result.update(players, pile, slapRules);

        return successfulSlap;
    }

    public synchronized void grab(Player player) throws CannotGrabException, FinishedGameException {
        checkIfPlayerCanGrab(player);

        List<Card> cards = pile.clearAndReturnCards();
        player.addCardsFromPile(cards);

        indexHandler.setCurrentPlayer(players.indexOf(player));
        result = Result.update(players, pile, slapRules);
    }

    // No-op when already finished: a natural win can race the disconnect-forfeit timer.
    @Override
    public synchronized void forfeit(PlayerId loser) {
        if (isFinished()) {
            return;
        }
        if (players.size() != 2) {
            throw new UnsupportedOperationException(
                    "forfeit only defines a winner for 2-player games; got " + players.size());
        }
        Player winner = players.stream()
                .filter(player -> !player.id().equals(loser))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown seat " + loser));
        this.result = new Result(winner);
    }

    private void checkIfPlayerCanSend(Player player) throws NotPlayersTurnException, FullCentralPileException, FinishedGameException {
        if (isFinished()) {
            throw new FinishedGameException();
        }

        if (!getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(player);
        }

        if (pile.isFull()) {
            throw new FullCentralPileException();
        }
    }

    private void checkIfPlayerCanSlap(Player player) throws CannotSlapIfNoCardsInPileException, FinishedGameException {
        if (isFinished()) {
            throw new FinishedGameException();
        }

        if (pile.isEmpty()) {
            throw new CannotSlapIfNoCardsInPileException();
        }
    }

    private void checkIfPlayerCanGrab(Player player) throws CannotGrabException, FinishedGameException {
        if (isFinished()) {
            throw new FinishedGameException();
        }

        if (!pile.isGrabbableByPlayer(player)) {
            throw new CannotGrabException(player);
        }
    }

    public List<Action> getAvailableActions(Player player) {
        List<Action> allowedActions = new ArrayList<>();

        try {
            checkIfPlayerCanSend(player);
            allowedActions.add(Action.SEND);
        } catch (Exception ignored) {}

        try {
            checkIfPlayerCanSlap(player);
            allowedActions.add(Action.SLAP);
        } catch (Exception ignored) {}

        try {
            checkIfPlayerCanGrab(player);
            allowedActions.add(Action.GRAB);
        } catch (Exception ignored) {}

        return allowedActions;
    }

    @Override
    public GameId getId() {
        return id;
    }

    public CentralPile getPile() {
        return pile;
    }

    public Card getPileTopCard() {
        return pile.getCardOnTop();
    }

    public List<Card> getPileCards() {
        return new ArrayList<>(pile.getCards());
    }

    public int getPileSize() {
        return pile.getSize();
    }

    public boolean isPileGrabbable() {
        return pile.isGrabbableByAnyPlayer();
    }

    public Player getWinner() {
        return result.winningPlayer();
    }

    public Player getPlayerByIndex(int index) {
        return players.get(index);
    }

    public Player getCurrentPlayer() {
        return players.get(indexHandler.getCurrentPlayer());
    }

    public int getCurrentPlayerIndex() {
        return indexHandler.getCurrentPlayer();
    }

    public int getNbPlayers() {
        return players.size();
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    @Override
    public List<PlayerId> getPlayerIds() {
        return players.stream().map(Player::id).toList();
    }

    @Override
    public boolean isFinished() {
        return result.isFinished();
    }

    private void initializePlayersAndHands(int nbPlayers) {
        players = new ArrayList<>();
        CardsService cardsService = new CardsService();

        Deck deck = cardsService.createDeck(DeckType.FRENCH, new DeckCreationOptions(Visibility.HIDDEN));

        try {
            List<Hand> hands = deck.distributeAll(nbPlayers, new DistributionOptions(false));

            for (int playerIndex = 0; playerIndex < nbPlayers; ++playerIndex) {
                Hand hand = hands.get(playerIndex);
                Player player = new Player(playerIndex, hand);
                players.add(player);
            }
        } catch (UnevenNumberOfCardsPerPlayerException e) {
            throw new IllegalStateException("Unhandled case");
        }

    }

    private void initializeData() {
        pile = new CentralPile(new Pile(), CentralPileState.NEUTRAL);
        slapRules = SlapRules.DEFAULT;
        penality = new PutCardsUnderPile(2);
        indexHandler = new IndexHandler(0, players, pile);
        result = Result.update(players, pile, slapRules);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        BatailleCorse that = (BatailleCorse) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
