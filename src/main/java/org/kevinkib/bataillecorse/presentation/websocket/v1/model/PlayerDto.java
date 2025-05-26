package org.kevinkib.bataillecorse.presentation.websocket.v1.model;

import org.kevinkib.bataillecorse.domain.Action;
import org.kevinkib.bataillecorse.domain.Player;

import java.util.List;

public class PlayerDto {

    private final Player player;
    private final List<Action> availableActions;

    public PlayerDto(Player player, List<Action> availableActions) {
        this.player = player;
        this.availableActions = availableActions;
    }

    public String getId() {
        return player.getId().toString();
    }

    public Integer getNbCards() {
        return player.getHandSize();
    }

    public List<String> getAvailableActions() {
        return availableActions.stream().map(Action::toString).toList();
    }

}
