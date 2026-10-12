package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cards.domain.Card;

/**
 * A card as the frontend sees it: its rank and suit labels, and a name ({@code SUIT_RANK}) naming its
 * image. Shared by every game that deals French cards; the games' domains never see it.
 */
public record CardDto(String rank, String suit, String name) {

    /** The DTO of a card, or null for no card. A missing rank or suit stays null and leaves the name. */
    public static CardDto from(Card card) {
        if (card == null) {
            return null;
        }
        String rank = card.getRank() == null ? null : card.getRank().toString();
        String suit = card.getSuit() == null ? null : card.getSuit().toString();
        return new CardDto(rank, suit, (suit == null ? "" : suit + "_") + (rank == null ? "" : rank));
    }
}
