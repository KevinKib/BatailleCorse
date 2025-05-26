package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

import org.kevinkib.bataillecorse.domain.CentralPile;
import org.kevinkib.cards.domain.Pile;

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
        return new PlayerIdDto(pile.getPlayerThatAddedLastHonourCard());
    }

}
