package org.kevinkib.cardgames.kobo.presentation.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.kobo.domain.Kobo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
@JsonSubTypes({
        @JsonSubTypes.Type(value = OutcomeDto.Ongoing.class, name = "ONGOING"),
        @JsonSubTypes.Type(value = OutcomeDto.Finished.class, name = "FINISHED")
})
public sealed interface OutcomeDto permits OutcomeDto.Ongoing, OutcomeDto.Finished {

    record Ongoing() implements OutcomeDto {
    }

    record Finished(List<Integer> winners, String reason) implements OutcomeDto {
    }

    static OutcomeDto from(Kobo game) {
        if (!game.result().isFinished()) {
            return new Ongoing();
        }
        return new Finished(
                game.result().winners().stream().map(p -> p.id()).toList(),
                game.result().reason().name());
    }
}
