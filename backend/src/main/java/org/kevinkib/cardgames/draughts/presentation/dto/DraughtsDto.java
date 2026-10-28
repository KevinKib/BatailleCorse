package org.kevinkib.cardgames.draughts.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.kevinkib.cardgames.draughts.domain.Action;
import org.kevinkib.cardgames.draughts.domain.BoardGeometry;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsResult;
import org.kevinkib.cardgames.draughts.domain.DrawTracker;
import org.kevinkib.cardgames.draughts.domain.Piece;
import org.kevinkib.cardgames.game.PlayerId;

/**
 * One player's view of a draughts game, the only projection of the aggregate. Nothing is hidden in draughts: the
 * viewer only scopes its own actions, its legal moves and the capture hint. {@code board} holds the 50 squares,
 * index {@code square - 1}: {@code "w"} a white man, {@code "W"} a white king, {@code "b"} and {@code "B"} for
 * Black, null for a free square. The draw counters and the repetition count are left out until they matter.
 */
public record DraughtsDto(
        boolean started,
        String id,
        String gameType,
        int version,
        String phase,
        int currentSeat,
        int whiteSeat,
        List<String> board,
        List<PlayerDto> players,
        List<String> availableActions,
        List<MoveDto> legalMoves,
        boolean captureRequired,
        int maxCaptures,
        @JsonInclude(JsonInclude.Include.NON_NULL) LastMoveDto lastMove,
        @JsonInclude(JsonInclude.Include.NON_NULL) DrawOfferDto drawOffer,
        @JsonInclude(JsonInclude.Include.NON_NULL) DrawCountersDto drawCounters,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer repetitions,
        List<String> history,
        Outcome outcome) {

    /** From this many kings-only plies (of 50) the state carries the count, so the client can warn before the draw. */
    static final int KINGS_ONLY_WARNING_FROM = 30;
    /** The repetition count is written from the second occurrence. */
    static final int REPETITIONS_SHOWN_FROM = 2;

    /** {@code bot} and {@code level} are left out for a human seat; {@code name} is null for a bot or nameless seat. */
    public record PlayerDto(int seat, String name, String colour, boolean current, int men, int kings, int captured,
                            @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean bot,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String level) {
    }

    /** A legal move: {@code path} the landing squares after {@code from}, {@code captures} in capture order. */
    public record MoveDto(int from, int to, List<Integer> path, List<Integer> captures, boolean promotes) {
    }

    public record LastMoveDto(int seat, int from, int to, List<Integer> path, List<Integer> captures,
                              boolean promotes) {
    }

    /** The pending draw offer: who made it. */
    public record DrawOfferDto(@JsonProperty("by") int by) {
    }

    /** Each part is present only once it matters. */
    public record DrawCountersDto(
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer kingOnlyPlies,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer kingOnlyLimit,
            @JsonInclude(JsonInclude.Include.NON_NULL) EndgameDto endgame) {
    }

    /** The 6.3 / 6.4 count: {@code case} is SIXTEEN or FIVE, {@code plies} and {@code limit} are in plies. */
    public record EndgameDto(@JsonProperty("case") String endgameCase, int plies, int limit) {
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

    public static DraughtsDto forViewer(Draughts game, PlayerId viewer) {
        return forViewer(game, viewer, Map.of());
    }

    public static DraughtsDto forViewer(Draughts game, PlayerId viewer, Map<PlayerId, String> names) {
        return forViewer(game, viewer, names, Set.of(), Map.of());
    }

    /** {@code bots} are the computer seats and {@code levels} the level key of those that have one. */
    public static DraughtsDto forViewer(Draughts game, PlayerId viewer, Map<PlayerId, String> names,
                                        Set<PlayerId> bots, Map<PlayerId, String> levels) {
        synchronized (game) {
            List<PlayerDto> players = game.getPlayerIds().stream()
                    .map(p -> new PlayerDto(p.id(), names.get(p), game.colourOf(p).name(), p.equals(game.currentSeat())
                            && !game.isFinished(), game.men(p), game.kings(p), game.captured(p), bots.contains(p),
                            bots.contains(p) ? levels.get(p) : null))
                    .toList();
            DraughtsResult result = game.result();
            Outcome outcome = result.isFinished()
                    ? new Finished(result.winner() == null ? null : result.winner().id(), result.reason().name())
                    : new Ongoing();
            Draughts.PlayedMove last = game.lastMove();
            int repetitions = game.repetitions();
            return new DraughtsDto(
                    true,
                    game.getId().uuid().toString(),
                    DraughtsFactory.GAME_TYPE,
                    game.version(),
                    game.phase().name(),
                    game.currentSeat().id(),
                    game.whiteSeat().id(),
                    board(game),
                    players,
                    game.availableActions(viewer).stream().map(Action::name).toList(),
                    game.legalMoves(viewer).stream().map(DraughtsDto::move).toList(),
                    game.captureRequired(viewer),
                    game.maxCaptures(viewer),
                    last == null ? null : new LastMoveDto(last.seat().id(), last.move().from(), last.move().to(),
                            last.move().path(), last.move().captures(), last.move().promotes()),
                    game.drawOffer() == null ? null : new DrawOfferDto(game.drawOffer().id()),
                    game.isFinished() ? null : counters(game.draws()),
                    repetitions >= REPETITIONS_SHOWN_FROM ? repetitions : null,
                    game.history(),
                    outcome);
        }
    }

    private static MoveDto move(DraughtsMove m) {
        return new MoveDto(m.from(), m.to(), m.path(), m.captures(), m.promotes());
    }

    private static List<String> board(Draughts game) {
        List<String> squares = new ArrayList<>(BoardGeometry.SQUARES);
        for (int square = 1; square <= BoardGeometry.SQUARES; square++) {
            Piece piece = game.position().pieceAt(square);
            squares.add(piece == null ? null : String.valueOf(piece.symbol()));
        }
        return squares;
    }

    /** The counters worth showing now, or null when none matters yet. */
    private static DrawCountersDto counters(DrawTracker tracker) {
        boolean kingsOnly = tracker.kingOnlyPlies() >= KINGS_ONLY_WARNING_FROM;
        boolean endgame = tracker.endgameLimitPlies() > 0;
        if (!kingsOnly && !endgame) {
            return null;
        }
        return new DrawCountersDto(
                kingsOnly ? tracker.kingOnlyPlies() : null,
                kingsOnly ? DrawTracker.KINGS_ONLY_PLIES : null,
                endgame ? new EndgameDto(
                        tracker.endgameLimitPlies() == DrawTracker.THREE_AGAINST_ONE_PLIES ? "SIXTEEN" : "FIVE",
                        tracker.endgamePlies(), tracker.endgameLimitPlies()) : null);
    }
}
