package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;

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
