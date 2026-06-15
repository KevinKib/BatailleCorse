package org.kevinkib.cardgames.bullshit.presentation.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OutcomeDto.Ongoing.class, name = "ONGOING"),
        @JsonSubTypes.Type(value = OutcomeDto.Won.class, name = "FINISHED")
})
public sealed interface OutcomeDto permits OutcomeDto.Ongoing, OutcomeDto.Won {

    record Ongoing() implements OutcomeDto {
    }

    record Won(String winnerId) implements OutcomeDto {
    }

    static OutcomeDto from(Bullshit game) {
        if (game.isFinished()) {
            return new Won(String.valueOf(game.getWinner().id().id()));
        }
        return new Ongoing();
    }
}
