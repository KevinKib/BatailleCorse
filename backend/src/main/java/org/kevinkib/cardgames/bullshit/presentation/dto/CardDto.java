package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.kevinkib.cards.domain.Card;

public record CardDto(String rank, String suit, String name) {

    public static CardDto from(Card card) {
        String rank = card.getRank() == null ? null : card.getRank().toString();
        String suit = card.getSuit() == null ? null : card.getSuit().toString();

        String name = "";
        if (card.getSuit() != null) {
            name += card.getSuit() + "_";
        }
        if (card.getRank() != null) {
            name += card.getRank();
        }
        return new CardDto(rank, suit, name);
    }
}
