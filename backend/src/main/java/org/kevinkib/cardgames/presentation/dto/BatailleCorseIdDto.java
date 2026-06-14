package org.kevinkib.cardgames.presentation.dto;

import org.kevinkib.cardgames.game.GameId;

public class BatailleCorseIdDto {

    private final GameId batailleCorseId;

    public BatailleCorseIdDto(GameId batailleCorseId) {
        this.batailleCorseId = batailleCorseId;
    }

    public String getId() {
        if (batailleCorseId == null) {
            return null;
        }
        return batailleCorseId.uuid().toString();
    }

}
