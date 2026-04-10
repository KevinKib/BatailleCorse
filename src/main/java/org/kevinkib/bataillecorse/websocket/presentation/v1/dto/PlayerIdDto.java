package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.bataillecorse.core.domain.Player;

public class PlayerIdDto {

    private final Player player;

    public PlayerIdDto(Player player) {
        this.player = player;
    }

    public String getId() {
        if (player == null) {
            return null;
        }
        return player.id().toString();
    }

}
