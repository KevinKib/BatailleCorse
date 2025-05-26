package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

import org.kevinkib.bataillecorse.domain.Action;
import org.kevinkib.bataillecorse.domain.Player;

import java.util.List;

public class PlayerIdDto {

    private final Player player;

    public PlayerIdDto(Player player) {
        this.player = player;
    }

    public String getId() {
        if (player == null) {
            return null;
        }
        return player.getId().toString();
    }

}
