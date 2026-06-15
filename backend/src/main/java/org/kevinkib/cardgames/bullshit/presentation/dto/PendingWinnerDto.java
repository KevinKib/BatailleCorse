package org.kevinkib.cardgames.bullshit.presentation.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "state")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PendingWinnerDto.None.class, name = "NONE"),
        @JsonSubTypes.Type(value = PendingWinnerDto.Pending.class, name = "PENDING")
})
public sealed interface PendingWinnerDto permits PendingWinnerDto.None, PendingWinnerDto.Pending {

    record None() implements PendingWinnerDto {
    }

    record Pending(String playerId) implements PendingWinnerDto {
    }

    static PendingWinnerDto from(Bullshit game) {
        return game.getPendingWinner()
                .<PendingWinnerDto>map(p -> new Pending(String.valueOf(p.id())))
                .orElseGet(None::new);
    }
}
