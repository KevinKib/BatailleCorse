package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseId;

public class BatailleCorseIdDto {

    private final BatailleCorseId batailleCorseId;

    public BatailleCorseIdDto(BatailleCorseId batailleCorseId) {
        this.batailleCorseId = batailleCorseId;
    }

    public String getId() {
        if (batailleCorseId == null) {
            return null;
        }
        return batailleCorseId.uuid().toString();
    }

}
