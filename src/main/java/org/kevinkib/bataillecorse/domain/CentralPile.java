package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.*;

import java.util.List;

import static org.kevinkib.bataillecorse.domain.CentralPileState.*;


public class CentralPile implements PileSubscriber {

    private final Pile pile;
    private CentralPileState state;
    private Integer nbCardsSinceLastHonourCard;
    private HonourCard lastHonourCard;
    private Player playerThatAddedLastHonourCard;

    public CentralPile(Pile pile, CentralPileState state) {
        super();
        this.pile = pile;
        this.state = state;
        this.nbCardsSinceLastHonourCard = 0;
        this.playerThatAddedLastHonourCard = null;

        pile.subscribe(this);
    }

    public void add(Card card, Player player) throws FullCentralPileException {
        if (state.isFull()) {
            throw new FullCentralPileException();
        }

        pile.add(card, CardPileState.SHOWN);

        if (HonourCard.isHonour(card)) {
            playerThatAddedLastHonourCard = player;
        }
    }

    public void addBelowForPenality(Card card) {
        pile.addBelow(card, CardPileState.HIDDEN);
    }

    public List<Card> clearAndReturnCards() {
        return pile.clearAndReturnCards();
    }

    @Override
    public void onCardAdded(Pile pile, Card addedCard, PilePosition pilePosition) {
        if (PilePosition.BOTTOM == pilePosition) {
            return;
        }

        ++nbCardsSinceLastHonourCard;

        if (HonourCard.isHonour(addedCard)) {
            state = HONOUR_STATE;
            nbCardsSinceLastHonourCard = 0;
            lastHonourCard = HonourCard.from(addedCard);
        }

        if (lastHonourCard != null && lastHonourCard.getNbChances().equals(nbCardsSinceLastHonourCard)) {
            state = FULL;
        }
    }

    @Override
    public void onClear(Pile pile) {
        state = NEUTRAL;
        lastHonourCard = null;
        nbCardsSinceLastHonourCard = 0;
        playerThatAddedLastHonourCard = null;
    }

    public boolean isGrabbableByPlayer(Player player) {
        if (playerThatAddedLastHonourCard == null) {
            return false;
        }

        return isFull() && playerThatAddedLastHonourCard.equals(player);
    }

    public boolean isGrabbableByAnyPlayer() {
        return isFull();
    }

    public boolean isEmpty() {
        return pile.isEmpty();
    }

    public boolean isFull() {
        return state.isFull();
    }

    public int getSize() {
        return pile.getSize();
    }

    public Card getCard(int index) {
        return getCardByIndex(index);
    }

    public Card getCardOnTop() {
        return pile.seeCardOnTop();
    }

    public Card getCardByIndex(int index) {
        return pile.seeCardByIndex(index);
    }

    public List<Card> getCards() {
        return pile.getCards();
    }

    public CentralPileState getState() {
        return state;
    }

    public Integer getNbCardsSinceLastHonourCard() {
        return nbCardsSinceLastHonourCard;
    }

    public HonourCard getLastHonourCard() {
        return lastHonourCard;
    }

    public Player getPlayerThatAddedLastHonourCard() {
        return playerThatAddedLastHonourCard;
    }

    public boolean isLastCardHonourCard() {
        if (isEmpty()) {
            return false;
        }

        return HonourCard.isHonour(getCardOnTop());
    }

    public boolean isHonourState() {
        return state == HONOUR_STATE;
    }
}
