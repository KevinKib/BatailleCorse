package org.kevinkib.bataillecorse.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.CardPileState;
import org.kevinkib.cards.domain.Pile;
import org.kevinkib.cards.domain.PileSubscriber;

import java.util.List;

import static org.kevinkib.bataillecorse.domain.CentralPileState.*;


public class CentralPile implements PileSubscriber {

    private final Pile pile;
    private CentralPileState state;
    private Integer nbCardsSinceLastHonourCard;
    private HonourCard lastHonourCard;

    public CentralPile(Pile pile, CentralPileState state) {
        super();
        this.pile = pile;
        this.state = state;
        this.nbCardsSinceLastHonourCard = 0;

        pile.subscribe(this);
    }

    public void add(Card card) throws FullCentralPileException {
        if (state.isFull()) {
            throw new FullCentralPileException();
        }
        pile.add(card, CardPileState.SHOWN);
    }

    public void addBelowForPenality(Card card) {
        // TODO: handle case where we're adding an honor card for a penality....
        pile.addBelow(card, CardPileState.HIDDEN);
    }

    public List<Card> clearAndReturnCards() {
        return pile.clearAndReturnCards();
    }

    public boolean isEmpty() {
        return pile.isEmpty();
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

    @Override
    public void onCardAdded(Pile pile) {
        Card addedCard = pile.seeCardOnTop();
        ++nbCardsSinceLastHonourCard;
        state = NEUTRAL;

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
    }

    public CentralPileState getState() {
        return state;
    }

}
