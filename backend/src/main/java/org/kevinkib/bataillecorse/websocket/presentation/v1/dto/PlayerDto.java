package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kevinkib.bataillecorse.core.domain.Action;
import org.kevinkib.bataillecorse.core.domain.Player;

import java.util.List;

public class PlayerDto {

    private final String id;
    private final Integer nbCards;
    private final List<String> availableActions;

    @JsonCreator
    public PlayerDto(@JsonProperty("id") String id,
                     @JsonProperty("nbCards") Integer nbCards,
                     @JsonProperty("availableActions") List<String> availableActions) {
        this.id = id;
        this.nbCards = nbCards;
        this.availableActions = availableActions;
    }

    public static PlayerDto from(Player player, List<Action> availableActions) {
        return new PlayerDto(
                player.id().toString(),
                player.getHandSize(),
                availableActions.stream().map(Action::toString).toList());
    }

    public String getId() {
        return id;
    }

    public Integer getNbCards() {
        return nbCards;
    }

    public List<String> getAvailableActions() {
        return availableActions;
    }

}
