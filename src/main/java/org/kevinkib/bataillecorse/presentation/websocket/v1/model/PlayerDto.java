package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

import org.kevinkib.bataillecorse.domain.Player;

import java.util.List;

public class PlayerDto {

    private final Player player;

    public PlayerDto(Player player) {
        this.player = player;
    }

    public String getId() {
        return player.getId().toString();
    }

    public Integer getNbCards() {
        return player.getHandSize();
    }

}
