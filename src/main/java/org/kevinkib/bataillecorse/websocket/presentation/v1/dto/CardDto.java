package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.cards.domain.Card;

public class CardDto {

    private final Card card;

    public CardDto(Card card) {
        this.card = card;
    }

    public String getRank() {
        if (card.getRank() == null) {
            return null;
        }

        return card.getRank().toString();
    }

    public String getSuit() {
        if (card.getSuit() == null) {
            return null;
        }

        return card.getSuit().toString();
    }

    public String getName() {
        String name = "";

        if (card.getSuit() != null) {
            name += card.getSuit().toString() + "_";
        }

        if (card.getRank() != null) {
            name += card.getRank().toString();
        }

        return name;
    }


}
