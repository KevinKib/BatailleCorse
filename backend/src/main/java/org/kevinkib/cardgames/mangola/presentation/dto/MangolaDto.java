package org.kevinkib.cardgames.mangola.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.LastMove;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaResult;
import org.kevinkib.cardgames.mangola.domain.Phase;
import org.kevinkib.cardgames.mangola.domain.Side;
import org.kevinkib.cardgames.mangola.domain.SowingStep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One player's view of a Mangola game, the only projection of the aggregate. {@code board} holds the 32 pits
 * ({@code row * 8 + column}, rows 1 and 2 are side A's). During the placement phase it shows the viewer its own
 * draft and {@code null} for each of the opponent's 16 pits: the opponent's draft is never sent, only whether
 * the opponent is {@code ready}. Once both layouts are revealed everything is public.
 * {@code legalPits} and {@code availableActions} depend on the viewer; {@code draftOnlyEndless} warns the
 * placement editor about a draft with no finite move.
 */
public record MangolaDto(
        boolean started,
        String id,
        String gameType,
        int version,
        String phase,
        int currentSeat,
        int seatA,
        String direction,
        List<Integer> board,
        List<PlayerDto> players,
        List<Integer> legalPits,
        List<String> availableActions,
        @JsonInclude(JsonInclude.Include.NON_NULL) LastMoveDto lastMove,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer repetitions,
        Outcome outcome,
        boolean draftOnlyEndless) {

    /** {@code seeds} is the seeds on the seat's side, null while that side is hidden from the viewer. */
    public record PlayerDto(int seat, String name, boolean current, String side, Integer seeds, boolean ready) {
    }

    /** The last move played, so that a client reconnecting mid-animation can still show it. */
    public record LastMoveDto(int seat, int pit, List<StepDto> trace) {
    }

    /**
     * One step of a trace: {@code type} is PICK, DROP, SKIP_EMPTY, CAPTURE, RELAY or RESUME_AFTER. {@code pit} is
     * the pit concerned (for a capture, the capturing pit), {@code count} the seeds taken (PICK, RELAY, CAPTURE),
     * {@code facing} and {@code behind} the captured pits.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepDto(String type, Integer pit, Integer count, Integer facing, Integer behind) {

        public static StepDto from(SowingStep step) {
            if (step instanceof SowingStep.Pick s) {
                return new StepDto("PICK", s.pit(), s.count(), null, null);
            }
            if (step instanceof SowingStep.Drop s) {
                return new StepDto("DROP", s.pit(), null, null, null);
            }
            if (step instanceof SowingStep.SkipEmpty s) {
                return new StepDto("SKIP_EMPTY", s.pit(), null, null, null);
            }
            if (step instanceof SowingStep.Capture s) {
                return new StepDto("CAPTURE", s.fromPit(), s.count(), s.facing(), s.behind());
            }
            if (step instanceof SowingStep.Relay s) {
                return new StepDto("RELAY", s.pit(), s.count(), null, null);
            }
            if (step instanceof SowingStep.ResumeAfter s) {
                return new StepDto("RESUME_AFTER", s.pit(), null, null, null);
            }
            throw new IllegalArgumentException("Unknown step " + step);
        }
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

    /** {@code winner} is null for a draw. */
    public record Finished(Integer winner, String reason) implements Outcome {
    }

    public static final String ARRANGE = "ARRANGE";
    public static final String CONFIRM_LAYOUT = "CONFIRM_LAYOUT";
    public static final String EDIT_LAYOUT = "EDIT_LAYOUT";
    public static final String SOW = "SOW";

    public static MangolaDto forViewer(Mangola game, PlayerId viewer) {
        return forViewer(game, viewer, Map.of());
    }

    public static MangolaDto forViewer(Mangola game, PlayerId viewer, Map<PlayerId, String> names) {
        synchronized (game) {
            List<PlayerId> seats = game.getPlayerIds();
            // both ready is the reveal: a game that ends during the placement never reveals anything
            boolean revealed = seats.stream().allMatch(game::isReady);

            List<Integer> board = new ArrayList<>(Collections.nCopies(MangolaGeometry.PITS, (Integer) null));
            List<PlayerDto> players = new ArrayList<>();
            for (PlayerId seat : seats) {
                Side side = game.sideOf(seat);
                Integer seeds = null;
                if (revealed) {
                    for (int pit : MangolaGeometry.loop(side)) {
                        board.set(pit, game.board().seeds(pit));
                    }
                    seeds = game.board().sideTotal(side);
                } else if (seat.equals(viewer)) {
                    List<Integer> draft = game.layoutOf(seat).orElse(Collections.nCopies(MangolaGeometry.LOOP_LENGTH, 0));
                    List<Integer> loop = MangolaGeometry.loop(side);
                    for (int i = 0; i < loop.size(); i++) {
                        board.set(loop.get(i), draft.get(i));
                    }
                    seeds = draft.stream().mapToInt(Integer::intValue).sum();
                }
                players.add(new PlayerDto(seat.id(), names.get(seat),
                        game.phase() == Phase.PLAY && seat.equals(game.currentSeat()), side.name(), seeds,
                        game.isReady(seat)));
            }

            MangolaResult result = game.result();
            Outcome outcome = result.isFinished()
                    ? new Finished(result.winner() == null ? null : result.winner().id(), result.reason().name())
                    : new Ongoing();
            LastMove last = game.lastMove();
            LastMoveDto lastMove = last == null ? null : new LastMoveDto(last.seat().id(), last.pit(),
                    last.trace().stream().map(StepDto::from).toList());

            return new MangolaDto(
                    true,
                    game.getId().uuid().toString(),
                    MangolaFactory.GAME_TYPE,
                    game.version(),
                    game.phase().name(),
                    game.currentSeat().id(),
                    game.seatA().id(),
                    game.direction().key(),
                    board,
                    players,
                    game.legalPits(viewer),
                    availableActions(game, viewer),
                    lastMove,
                    game.repetitions() >= 2 ? game.repetitions() : null,
                    outcome,
                    game.draftOnlyEndless(viewer));
        }
    }

    private static List<String> availableActions(Mangola game, PlayerId viewer) {
        if (game.isFinished()) {
            return List.of();
        }
        if (game.phase() == Phase.PLACEMENT) {
            if (game.isReady(viewer)) {
                return List.of(EDIT_LAYOUT);
            }
            Optional<List<Integer>> draft = game.layoutOf(viewer);
            return draft.isPresent() ? List.of(ARRANGE, CONFIRM_LAYOUT) : List.of(ARRANGE);
        }
        return viewer.equals(game.currentSeat()) ? List.of(SOW) : List.of();
    }
}
