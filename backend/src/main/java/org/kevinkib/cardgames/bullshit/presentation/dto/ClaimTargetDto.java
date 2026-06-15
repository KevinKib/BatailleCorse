package org.kevinkib.cardgames.bullshit.presentation.dto;

import org.kevinkib.cardgames.bullshit.domain.claim.ClaimTarget;

public record ClaimTargetDto(String label) {

    public static ClaimTargetDto from(ClaimTarget target) {
        return new ClaimTargetDto(target.label());
    }
}
