package org.kevinkib.bataillecorse.domain;

import org.kevinkib.bataillecorse.domain.slaprules.SlapRules;
import org.kevinkib.bataillecorse.domain.penality.Penality;
import org.kevinkib.bataillecorse.domain.penality.PutCardsUnderPile;
import org.kevinkib.cards.CardsController;
import org.kevinkib.cards.domain.*;

import java.util.ArrayList;
import java.util.List;

public class BatailleCorse {

    private List<Player> players;
    private CentralPile pile;
    private SlapRules slapRules;
    private Penality penality;
    private IndexHandler indexHandler;
    private Result result;

    public BatailleCorse(int nbPlayers) {
        initializePlayersAndHands(nbPlayers);
        initializeData();
    }

    public BatailleCorse(List<Player> players, int currentPlayer, CentralPile pile, SlapRules slapRules, Penality penality) {
        this.players = players;
        this.pile = pile;
        this.slapRules = slapRules;
        this.penality = penality;
        this.indexHandler = new IndexHandler(currentPlayer, getNbPlayers(), pile);
        this.result = updateGameResult();
    }

    public void send(Player player) throws NotPlayersTurnException, FullCentralPileException {
        if (!getCurrentPlayer().equals(player)) {
            throw new NotPlayersTurnException(player);
        }

        // TODO: If the current player has no cards, it's true that he cannot play, but it mostly means
        // he isn't the current player. That means either the game is finished or the current player failed to update
        // Either way, replace condition with something else

//        if (!getCurrentPlayer().hasAnyCards()) {
//            throw new PlayerCannotPlayException();
//        }

        if (pile.isFull()) {
            throw new FullCentralPileException();
        }

        try {
            Card card = player.removeCardOnTop();
            pile.add(card, player);

            indexHandler.update();
        } catch (NoCardsException e) {
            throw new IllegalStateException("A player should always have cards after the no cards check.");
        } catch (FullCentralPileException e) {
            throw new RuntimeException(e);
        }
    }

    public void slap(Player player) throws CannotSlapIfNoCardsInPileException {
        if (pile.isEmpty()) {
            throw new CannotSlapIfNoCardsInPileException();
        }

        if (slapRules.applies(pile)) {
            List<Card> cards = pile.clearAndReturnCards();
            player.addCardsFromPile(cards);

            indexHandler.setCurrentPlayer(players.indexOf(player));
        }
        else {
            penality.apply(player, pile);
        }
    }

    public void grab(Player player) throws CannotGrabException {
        if (!pile.isGrabbableByPlayer(player)) {
            throw new CannotGrabException(player);
        }

        List<Card> cards = pile.clearAndReturnCards();
        player.addCardsFromPile(cards);
    }

    public Card getPileTopCard() {
        return pile.getCardOnTop();
    }

    public int getPileSize() {
        return pile.getSize();
    }

    public Player getWinner() {
        return result.getWinningPlayer();
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
        pile = new CentralPile(new Pile(), CentralPileState.NEUTRAL);
        slapRules = SlapRules.DEFAULT;
        penality = new PutCardsUnderPile(2);
        indexHandler = new IndexHandler(0, getNbPlayers(), pile);
        result = updateGameResult();
    }

    private Result updateGameResult() {
        if (!pile.isEmpty()) {
            return Result.ONGOING;
        }

        List<Player> playersWithCards = players.stream().filter(Player::hasAnyCards).toList();

        if (playersWithCards.isEmpty()) {
            throw new IllegalStateException("Cannot have no players with no cards when pile is empty");
        }

        if (playersWithCards.size() == 1) {
            Player winningPlayer = playersWithCards.get(0);
            return new Result(winningPlayer);
        }
        else {
            return Result.ONGOING;
        }
    }

}
