package org.kevinkib.cardgames.bataillecorse.presentation.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.sessionmanagement.presence.domain.ForfeitReason;

import java.util.List;
import java.util.Map;

public class BatailleCorseDto {

    private final String id;
    private final List<PlayerDto> players;
    private final PlayerIdDto winner;
    private final PileDto pile;
    private final PlayerDto currentPlayer;

    @JsonCreator
    public BatailleCorseDto(@JsonProperty("id") String id,
                            @JsonProperty("players") List<PlayerDto> players,
                            @JsonProperty("winner") PlayerIdDto winner,
                            @JsonProperty("pile") PileDto pile,
                            @JsonProperty("currentPlayer") PlayerDto currentPlayer) {
        this.id = id;
        this.players = players;
        this.winner = winner;
        this.pile = pile;
        this.currentPlayer = currentPlayer;
    }

    public static BatailleCorseDto from(BatailleCorse batailleCorse) {
        return from(batailleCorse, Map.of());
    }

    public static BatailleCorseDto from(BatailleCorse batailleCorse,
                                        Map<Integer, ForfeitReason> forfeitReasons) {
        List<PlayerDto> players = batailleCorse.getPlayers().stream()
                .map(player -> PlayerDto.from(
                        player,
                        batailleCorse.getAvailableActions(player),
                        forfeitReasons.get(player.id().id())))
                .toList();

        PlayerIdDto winner = batailleCorse.isFinished()
                ? PlayerIdDto.from(batailleCorse.getWinner())
                : null;

        Player current = batailleCorse.getCurrentPlayer();
        PlayerDto currentPlayer = PlayerDto.from(current, batailleCorse.getAvailableActions(current));

        return new BatailleCorseDto(
                batailleCorse.getId().toString(),
                players,
                winner,
                PileDto.from(batailleCorse.getPile()),
                currentPlayer);
    }

    public String getId() {
        return id;
    }

    public List<PlayerDto> getPlayers() {
        return players;
    }

    public PlayerIdDto getWinner() {
        return winner;
    }

    public PileDto getPile() {
        return pile;
    }

    public PlayerDto getCurrentPlayer() {
        return currentPlayer;
    }

}
