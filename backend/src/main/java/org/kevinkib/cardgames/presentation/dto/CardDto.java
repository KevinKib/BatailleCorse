package org.kevinkib.cardgames.presentation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kevinkib.cards.domain.Card;

public class CardDto {

    private final String rank;
    private final String suit;
    private final String name;

    @JsonCreator
    public CardDto(@JsonProperty("rank") String rank,
                   @JsonProperty("suit") String suit,
                   @JsonProperty("name") String name) {
        this.rank = rank;
        this.suit = suit;
        this.name = name;
    }

    public static CardDto from(Card card) {
        String rank = card.getRank() == null ? null : card.getRank().toString();
        String suit = card.getSuit() == null ? null : card.getSuit().toString();

        String name = "";
        if (card.getSuit() != null) {
            name += card.getSuit().toString() + "_";
        }
        if (card.getRank() != null) {
            name += card.getRank().toString();
        }

        return new CardDto(rank, suit, name);
    }

    public String getRank() {
        return rank;
    }

    public String getSuit() {
        return suit;
    }

    public String getName() {
        return name;
    }

}
