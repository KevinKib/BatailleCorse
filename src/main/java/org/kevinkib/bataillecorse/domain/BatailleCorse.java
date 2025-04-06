package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.CardsController;
import org.kevinkib.cards.domain.*;

import java.util.ArrayList;
import java.util.List;

public class BatailleCorse {

    private List<Player> players;
    private int currentPlayer;
    private Pile pile;

    public BatailleCorse(int nbPlayers) {
        initializePlayersAndHands(nbPlayers);
        initializeData();
    }

    public BatailleCorse(List<Player> players) {
        this.players = players;
        initializeData();
    }

    public void send(Player player) throws NotPlayersTurnException, PlayerCannotPlayException {
        if (!getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(player);
        }

        if (!getCurrentPlayer().hasAnyCards()) {
            throw new PlayerCannotPlayException();
        }

        try {
            Card card = player.removeCardOnTop();
            card.showInPile();
            pile.add(card);

            increaseCurrentPlayerIndex();
        } catch (NoCardsException e) {
            throw new IllegalStateException("A player should always have cards after the no cards check.");
        }
    }

    public Card getPileTopCard() {
        return pile.seeCardOnTop();
    }

    public int getPileSize() {
        return pile.getSize();
    }

    public Player getPlayerByIndex(int index) {
        return players.get(index);
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayer);
    }

    public int getCurrentPlayerIndex() {
        return currentPlayer;
    }

    public int getNbPlayers() {
        return players.size();
    }

    private void initializePlayersAndHands(int nbPlayers) {
        players = new ArrayList<>();
        CardsController cardsController = new CardsController();

        Deck deck = cardsController.createDeck(DeckType.FRENCH, new DeckCreationOptions(CardHandState.HIDDEN_IN_HAND));

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
        currentPlayer = 0;
        pile = new Pile();
    }

    private void increaseCurrentPlayerIndex() {
        currentPlayer += 1;
        if (currentPlayer == getNbPlayers()) {
            currentPlayer = 0;
        }
    }

}
