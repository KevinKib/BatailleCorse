package org.kevinkib.bataillecorse.domain;

import org.kevinkib.bataillecorse.domain.hitrules.HitRules;
import org.kevinkib.bataillecorse.domain.penality.Penality;
import org.kevinkib.bataillecorse.domain.penality.PutCardsUnderPile;
import org.kevinkib.cards.CardsController;
import org.kevinkib.cards.domain.*;

import java.util.ArrayList;
import java.util.List;

public class BatailleCorse {

    private List<Player> players;
    private int currentPlayer;
    private CentralPile pile;
    private HitRules hitRules;
    private Penality penality;

    public BatailleCorse(int nbPlayers) {
        initializePlayersAndHands(nbPlayers);
        initializeData();
    }

    public BatailleCorse(List<Player> players, int currentPlayer, CentralPile pile, HitRules hitRules, Penality penality) {
        this.players = players;
        this.currentPlayer = currentPlayer;
        this.pile = pile;
        this.hitRules = hitRules;
        this.penality = penality;
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
            pile.add(card);

            increaseCurrentPlayerIndex();
        } catch (NoCardsException e) {
            throw new IllegalStateException("A player should always have cards after the no cards check.");
        } catch (FullCentralPileException e) {
            throw new RuntimeException(e);
        }
    }

    public void hit(Player player) {
        if (hitRules.applies(pile)) {
            List<Card> cards = pile.clearAndReturnCards();
            player.addCards(cards);

            currentPlayer = players.indexOf(player);
        }
        else {
            penality.apply(player, pile);
        }
    }

    public Card getPileTopCard() {
        return pile.getCardOnTop();
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
        pile = new CentralPile(new Pile(), CentralPileState.NEUTRAL);
        hitRules = HitRules.DEFAULT;
        penality = new PutCardsUnderPile(2);
    }

    private void increaseCurrentPlayerIndex() {
        currentPlayer += 1;
        if (currentPlayer == getNbPlayers()) {
            currentPlayer = 0;
        }
    }

}
