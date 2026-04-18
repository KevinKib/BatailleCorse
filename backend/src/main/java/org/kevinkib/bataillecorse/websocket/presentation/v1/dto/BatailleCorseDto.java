package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.Player;

import java.util.List;

public class BatailleCorseDto {

    private final BatailleCorse batailleCorse;

    public BatailleCorseDto(BatailleCorse batailleCorse) {
        this.batailleCorse = batailleCorse;
    }

    public String getId() {
        return batailleCorse.getId().toString();
    }

    public List<PlayerDto> getPlayers() {
        return batailleCorse.getPlayers().stream()
                .map(player -> new PlayerDto(player, batailleCorse.getAvailableActions(player)))
                .toList();
    }

    public PlayerIdDto getWinner() {
        if (!batailleCorse.isFinished()) {
            return null;
        }

        return new PlayerIdDto(batailleCorse.getWinner());
    }

    public PileDto getPile() {
        return new PileDto(batailleCorse.getPile());
    }

    public PlayerDto getCurrentPlayer() {
        Player currentPlayer = batailleCorse.getCurrentPlayer();
        return new PlayerDto(currentPlayer, batailleCorse.getAvailableActions(currentPlayer));
    }

}
