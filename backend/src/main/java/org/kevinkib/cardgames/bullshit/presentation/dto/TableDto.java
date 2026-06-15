package org.kevinkib.cardgames.bullshit.presentation.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "state")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TableDto.NoClaim.class, name = "NO_CLAIM"),
        @JsonSubTypes.Type(value = TableDto.Claim.class, name = "CLAIM")
})
public sealed interface TableDto permits TableDto.NoClaim, TableDto.Claim {

    record NoClaim() implements TableDto {
    }

    record Claim(String claimantId, String claimedTargetLabel, int count) implements TableDto {
    }

    static TableDto from(Bullshit game) {
        return game.getLastDiscard()
                .<TableDto>map(d -> new Claim(
                        String.valueOf(d.claimant().id()),
                        d.claimedTarget().label(),
                        d.actualCards().size()))
                .orElseGet(NoClaim::new);
    }
}
