package org.kevinkib.cardgames.threemenmorris.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.threemenmorris.domain.Action;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisResult;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisVariant;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;

import java.util.List;
import java.util.Map;

/**
 * One player's view of a Three Men's Morris game, the only projection of the aggregate. Everything
 * is public; only the actions and the legal moves depend on the viewer. {@code board} holds the
 * owner seat of each of the nine points (row by row), null for a free point. {@code variant} and
 * {@code legalPlacements} are additive and left out of the JSON for Tapatan, so the v1 wire is unchanged:
 * a client reads an absent {@code variant} as {@code tapatan}.
 */
public record ThreeMenMorrisDto(
        boolean started,
        String id,
        String gameType,
        int version,
        String phase,
        int currentSeat,
        List<Integer> board,
        List<PlayerDto> players,
        List<MoveDto> legalMoves,
        List<String> availableActions,
        List<Integer> winningLine,
        Outcome outcome,
        @JsonInclude(JsonInclude.Include.NON_NULL) String variant,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Integer> legalPlacements,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer whiteSeat) {

    /** {@code colour} (WHITE or BLACK) is left out while seat 0 plays White, like {@code whiteSeat}. */
    public record PlayerDto(int seat, String name, boolean current, int piecesToPlace,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String colour) {
    }

    public record MoveDto(int from, int to) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Ongoing.class, name = "ONGOING"),
            @JsonSubTypes.Type(value = Finished.class, name = "FINISHED")
    })
    public sealed interface Outcome permits Ongoing, Finished {
    }

    public record Ongoing() implements Outcome {
    }

    public record Finished(Integer winner, String reason) implements Outcome {
    }

    public static ThreeMenMorrisDto forViewer(ThreeMenMorris game, PlayerId viewer) {
        return forViewer(game, viewer, Map.of());
    }

    public static ThreeMenMorrisDto forViewer(ThreeMenMorris game, PlayerId viewer, Map<PlayerId, String> names) {
        synchronized (game) {
            List<Integer> board = game.board().stream().map(owner -> owner == null ? null : owner.id()).toList();
            boolean seatZeroWhite = game.whiteSeat().id() == 0;
            List<PlayerDto> players = game.getPlayerIds().stream()
                    .map(p -> new PlayerDto(p.id(), names.get(p), p.equals(game.currentSeat()),
                            game.piecesToPlace(p), seatZeroWhite ? null : game.colourOf(p).name()))
                    .toList();
            MorrisResult result = game.result();
            Outcome outcome = result.isFinished()
                    ? new Finished(result.winner().id(), result.reason().name())
                    : new Ongoing();
            MorrisVariant variant = MorrisVariant.of(game.rules());
            boolean v1 = variant == MorrisVariant.TAPATAN;
            return new ThreeMenMorrisDto(
                    true,
                    game.getId().uuid().toString(),
                    ThreeMenMorrisFactory.GAME_TYPE,
                    game.version(),
                    game.phase().name(),
                    game.currentSeat().id(),
                    board,
                    players,
                    game.legalMoves(viewer).stream().map(m -> new MoveDto(m.from(), m.to())).toList(),
                    game.availableActions(viewer).stream().map(Action::name).toList(),
                    game.winningLine(),
                    outcome,
                    v1 ? null : variant.key(),
                    v1 ? null : game.legalPlacements(viewer),
                    seatZeroWhite ? null : game.whiteSeat().id());
        }
    }
}
