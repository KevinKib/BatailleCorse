package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.bataillecorse.core.domain.CentralPile;

import java.util.List;

public class PileDto {

    private final CentralPile pile;

    public PileDto(CentralPile pile) {
        this.pile = pile;
    }

    public List<CardDto> getCards() {
        return pile.getCards().stream()
                .map(CardDto::new)
                .toList();
    }

    public boolean isGrabbable() {
        return pile.isGrabbableByAnyPlayer();
    }

    public Integer getNbCardsSinceLastHonourCard() {
        return pile.getNbCardsSinceLastHonourCard();
    }

    public PlayerIdDto getPlayerThatAddedLastHonourCard() {
        if (pile.getPlayerThatAddedLastHonourCard() == null) {
            return null;
        }

        return new PlayerIdDto(pile.getPlayerThatAddedLastHonourCard());
    }

}
