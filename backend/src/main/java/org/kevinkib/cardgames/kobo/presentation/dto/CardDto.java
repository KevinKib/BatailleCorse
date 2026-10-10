package org.kevinkib.cardgames.kobo.presentation.dto;

import org.kevinkib.cards.domain.Card;

public record CardDto(String rank, String suit, String name) {

    public static CardDto from(Card card) {
        if (card == null) {
            return null;
        }
        String rank = card.getRank() == null ? null : card.getRank().toString();
        String suit = card.getSuit() == null ? null : card.getSuit().toString();
        return new CardDto(rank, suit, (suit == null ? "" : suit + "_") + (rank == null ? "" : rank));
    }
}
