package org.kevinkib.cardgames.bataillecorse.presentation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kevinkib.cardgames.bataillecorse.domain.Action;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;

import java.util.List;

public class PlayerDto {

    private final String id;
    private final Integer nbCards;
    private final List<String> availableActions;
    private final String forfeitReason;

    @JsonCreator
    public PlayerDto(@JsonProperty("id") String id,
                     @JsonProperty("nbCards") Integer nbCards,
                     @JsonProperty("availableActions") List<String> availableActions,
                     @JsonProperty("forfeitReason") String forfeitReason) {
        this.id = id;
        this.nbCards = nbCards;
        this.availableActions = availableActions;
        this.forfeitReason = forfeitReason;
    }

    public static PlayerDto from(Player player, List<Action> availableActions) {
        return from(player, availableActions, null);
    }

    public static PlayerDto from(Player player, List<Action> availableActions, ForfeitReason forfeitReason) {
        return new PlayerDto(
                player.id().toString(),
                player.getHandSize(),
                availableActions.stream().map(Action::toString).toList(),
                forfeitReason == null ? null : forfeitReason.name());
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

    public String getForfeitReason() {
        return forfeitReason;
    }

}
