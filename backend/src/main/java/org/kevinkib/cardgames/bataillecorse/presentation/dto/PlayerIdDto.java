package org.kevinkib.cardgames.bataillecorse.presentation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kevinkib.cardgames.bataillecorse.domain.Player;

public class PlayerIdDto {

    private final String id;

    @JsonCreator
    public PlayerIdDto(@JsonProperty("id") String id) {
        this.id = id;
    }

    public static PlayerIdDto from(Player player) {
        if (player == null) {
            return null;
        }
        return new PlayerIdDto(player.id().toString());
    }

    public String getId() {
        return id;
    }

}
