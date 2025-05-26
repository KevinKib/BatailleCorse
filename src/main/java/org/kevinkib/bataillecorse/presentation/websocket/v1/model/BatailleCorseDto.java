package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

import org.kevinkib.bataillecorse.domain.BatailleCorse;
import org.kevinkib.bataillecorse.domain.Player;

import java.util.List;

public class BatailleCorseDto {

    private final BatailleCorse batailleCorse;

    public BatailleCorseDto(BatailleCorse batailleCorse) {
        this.batailleCorse = batailleCorse;
    }

    public List<PlayerDto> getPlayers() {
        return batailleCorse.getPlayers().stream()
                .map(player -> new PlayerDto(player, batailleCorse.getAvailableActions(player)))
                .toList();
    }

    public List<CardDto> getPile() {
        return batailleCorse.getPileCards().stream()
                .map(CardDto::new)
                .toList();
    }

    public PlayerDto getCurrentPlayer() {
        Player currentPlayer = batailleCorse.getCurrentPlayer();
        return new PlayerDto(currentPlayer, batailleCorse.getAvailableActions(currentPlayer));
    }

}
