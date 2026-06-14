package org.kevinkib.cardgames.bataillecorse.presentation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kevinkib.cardgames.bataillecorse.domain.CentralPile;

import java.util.List;

public class PileDto {

    private final List<CardDto> cards;
    private final boolean grabbable;
    private final Integer nbCardsSinceLastHonourCard;
    private final PlayerIdDto playerThatAddedLastHonourCard;

    @JsonCreator
    public PileDto(@JsonProperty("cards") List<CardDto> cards,
                   @JsonProperty("grabbable") boolean grabbable,
                   @JsonProperty("nbCardsSinceLastHonourCard") Integer nbCardsSinceLastHonourCard,
                   @JsonProperty("playerThatAddedLastHonourCard") PlayerIdDto playerThatAddedLastHonourCard) {
        this.cards = cards;
        this.grabbable = grabbable;
        this.nbCardsSinceLastHonourCard = nbCardsSinceLastHonourCard;
        this.playerThatAddedLastHonourCard = playerThatAddedLastHonourCard;
    }

    public static PileDto from(CentralPile pile) {
        List<CardDto> cards = pile.getCards().stream()
                .map(CardDto::from)
                .toList();

        return new PileDto(
                cards,
                pile.isGrabbableByAnyPlayer(),
                pile.getNbCardsSinceLastHonourCard(),
                PlayerIdDto.from(pile.getPlayerThatAddedLastHonourCard()));
    }

    public List<CardDto> getCards() {
        return cards;
    }

    public boolean isGrabbable() {
        return grabbable;
    }

    public Integer getNbCardsSinceLastHonourCard() {
        return nbCardsSinceLastHonourCard;
    }

    public PlayerIdDto getPlayerThatAddedLastHonourCard() {
        return playerThatAddedLastHonourCard;
    }

}
