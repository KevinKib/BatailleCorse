package org.kevinkib.cardgames.mangola.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.domain.MangolaRules;
import org.kevinkib.cardgames.mangola.domain.MangolaLayouts;
import org.kevinkib.cardgames.mangola.domain.SowingDirection;
import org.kevinkib.cardgames.mangola.domain.SowingStep;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The per-viewer projection. The point of the placement phase is that a seat sees its own draft and nothing of
 * the other's: the opponent's pits are null, whatever was arranged.
 */
class MangolaDtoTest {

    private static final PlayerId SEAT_0 = MangolaTable.SEAT_0;
    private static final PlayerId SEAT_1 = MangolaTable.SEAT_1;
    private static final Map<PlayerId, String> NAMES = Map.of(SEAT_0, "Alice", SEAT_1, "Bob");

    private final ObjectMapper mapper = JsonMapper.builder().build();

    private static Mangola placement() {
        return new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0);
    }

    private static MangolaDto view(Mangola game, PlayerId viewer) {
        return MangolaDto.forViewer(game, viewer, NAMES);
    }

    /** The 16 pits (indexes) of a side's half of the board, rows 1-2 for side A and 3-4 for side B. */
    private static List<Integer> halfOf(int firstPit) {
        List<Integer> pits = new ArrayList<>();
        for (int pit = firstPit; pit < firstPit + 16; pit++) {
            pits.add(pit);
        }
        return pits;
    }

    private static List<Integer> at(List<Integer> board, List<Integer> pits) {
        return pits.stream().map(board::get).toList();
    }

    // ------------------------------------------------------------ direction

    @Test
    void aDefaultGameSaysAnticlockwiseToBothViewersInPlacementAndInPlay() {
        Mangola placement = placement();
        Mangola play = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0,
                MangolaTable.board("a2:3 h4:5"));

        for (PlayerId viewer : List.of(SEAT_0, SEAT_1)) {
            assertThat(view(placement, viewer).direction(), is("anticlockwise"));
            assertThat(view(play, viewer).direction(), is("anticlockwise"));
        }
    }

    @Test
    void theDirectionIsWrittenInTheJson() {
        JsonNode json = mapper.valueToTree(view(placement(), SEAT_0));

        assertThat(json.get("direction").asString(), is("anticlockwise"));
    }

    @Test
    void aClockwiseGameSaysClockwiseToBothViewersInPlacementAndInPlay() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_0, SowingDirection.CLOCKWISE);
        for (PlayerId viewer : List.of(SEAT_0, SEAT_1)) {
            assertThat(view(game, viewer).direction(), is("clockwise"));
        }

        game.arrange(SEAT_0, MangolaTable.twoPerPit());
        game.arrange(SEAT_1, MangolaTable.twoPerPit());
        game.confirmLayout(SEAT_0);
        game.confirmLayout(SEAT_1);

        for (PlayerId viewer : List.of(SEAT_0, SEAT_1)) {
            assertThat(view(game, viewer).phase(), is("PLAY"));
            assertThat(view(game, viewer).direction(), is("clockwise"));
        }
        assertThat(mapper.valueToTree(view(game, SEAT_1)).get("direction").asString(), is("clockwise"));
    }

    // ------------------------------------------------------------ placement

    @Test
    void aNewPlacementShowsEachSeatItsOwnEmptyDraftAndNothingOfTheOther() {
        Mangola game = placement();

        MangolaDto dto = view(game, SEAT_0);

        assertThat(dto.started(), is(true));
        assertThat(dto.gameType(), is("mangola"));
        assertThat(dto.id(), is(game.getId().uuid().toString()));
        assertThat(dto.version(), is(0));
        assertThat(dto.phase(), is("PLACEMENT"));
        assertThat(dto.seatA(), is(0));
        assertThat(dto.board().size(), is(32));
        assertThat(at(dto.board(), halfOf(0)), is(Collections.nCopies(16, 0)));
        assertThat(at(dto.board(), halfOf(16)), is(Collections.nCopies(16, (Integer) null)));
        assertThat(dto.players().get(0).name(), is("Alice"));
        assertThat(dto.players().get(1).name(), is("Bob"));
        assertThat(dto.availableActions(), is(List.of("ARRANGE")));
        assertThat(dto.legalPits(), is(empty()));
        assertThat(dto.lastMove(), is(nullValue()));
        assertThat(dto.outcome(), is(new MangolaDto.Ongoing()));
    }

    @Test
    void theOpponentsDraftIsNeverInTheViewersState() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, MangolaTable.twoPerPit());
        game.arrange(SEAT_1, MangolaTable.allOnFirst());

        MangolaDto first = view(game, SEAT_0);
        MangolaDto second = view(game, SEAT_1);

        assertThat(at(first.board(), halfOf(0)), is(Collections.nCopies(16, 2)));
        assertThat(at(first.board(), halfOf(16)), is(Collections.nCopies(16, (Integer) null)));
        // seat 1 sows from a4 first: its 32 seeds sit on pit 24 (a4), side A's half is hidden from it
        assertThat(second.board().get(24), is(32));
        assertThat(at(second.board(), halfOf(0)), is(Collections.nCopies(16, (Integer) null)));
        assertThat(first.players().get(1).seeds(), is(nullValue()));
        assertThat(first.players().get(0).seeds(), is(32));
    }

    @Test
    void theOpponentsDraftIsNotInTheSerialisedState() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, MangolaTable.twoPerPit());
        game.arrange(SEAT_1, MangolaTable.allOnFirst());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(view(game, SEAT_0)));

        JsonNode board = json.get("board");
        assertThat(board.size(), is(32));
        for (int pit = 16; pit < 32; pit++) {
            assertThat("pit " + pit + " is hidden", board.get(pit).isNull(), is(true));
        }
        assertThat(json.get("players").get(1).get("seeds").isNull(), is(true));
    }

    @Test
    void theOtherSeatsReadinessIsPublicButItsDraftChangesAreNot() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, MangolaTable.twoPerPit());
        MangolaDto before = view(game, SEAT_0);
        game.arrange(SEAT_1, MangolaTable.allOnFirst());
        MangolaDto afterOpponentDraft = view(game, SEAT_0);

        assertThat(afterOpponentDraft.board(), is(before.board()));
        assertThat(afterOpponentDraft.players().get(1).ready(), is(false));

        game.confirmLayout(SEAT_1);
        MangolaDto afterOpponentReady = view(game, SEAT_0);
        assertThat(afterOpponentReady.players().get(1).ready(), is(true));
        assertThat(afterOpponentReady.players().get(0).ready(), is(false));
        assertThat(at(afterOpponentReady.board(), halfOf(16)), is(Collections.nCopies(16, (Integer) null)));
    }

    @Test
    void theAvailableActionsFollowTheDraftThenTheConfirmation() throws Exception {
        Mangola game = placement();
        assertThat(view(game, SEAT_0).availableActions(), is(List.of("ARRANGE")));

        game.arrange(SEAT_0, MangolaTable.twoPerPit());
        assertThat(view(game, SEAT_0).availableActions(), is(List.of("ARRANGE", "CONFIRM_LAYOUT")));

        game.confirmLayout(SEAT_0);
        assertThat(view(game, SEAT_0).availableActions(), is(List.of("EDIT_LAYOUT")));
        assertThat(view(game, SEAT_1).availableActions(), is(List.of("ARRANGE")));

        game.editLayout(SEAT_0);
        assertThat(view(game, SEAT_0).availableActions(), is(List.of("ARRANGE", "CONFIRM_LAYOUT")));
    }

    @Test
    void anOrdinaryDraftIsNotFlaggedEndless() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, MangolaTable.twoPerPit());

        assertThat(view(game, SEAT_0).draftOnlyEndless(), is(false));
        assertThat(view(game, SEAT_1).draftOnlyEndless(), is(false));
    }

    @Test
    void aDraftWithNoFiniteMoveIsFlaggedToItsOwnerOnly() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, new int[]{24, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1});
        game.arrange(SEAT_1, MangolaTable.twoPerPit());

        assertThat(view(game, SEAT_0).draftOnlyEndless(), is(true));
        assertThat(view(game, SEAT_1).draftOnlyEndless(), is(false));
    }

    // ------------------------------------------------------------ the reveal

    @Test
    void afterTheRevealBothHalvesArePublicAndTheStartingSeatPlays() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_0, MangolaTable.twoPerPit());
        game.arrange(SEAT_1, MangolaTable.allOnFirst());
        game.confirmLayout(SEAT_0);
        game.confirmLayout(SEAT_1);

        MangolaDto dto0 = view(game, SEAT_0);
        MangolaDto dto1 = view(game, SEAT_1);

        assertThat(dto0.phase(), is("PLAY"));
        assertThat(dto0.board(), is(dto1.board()));
        assertThat(at(dto0.board(), halfOf(0)), is(Collections.nCopies(16, 2)));
        assertThat(dto0.board().get(24), is(32));
        assertThat(dto0.currentSeat(), is(0));
        assertThat(dto0.players().get(0).current(), is(true));
        assertThat(dto0.players().get(1).seeds(), is(32));
        assertThat(dto0.availableActions(), is(List.of("SOW")));
        assertThat(dto1.availableActions(), is(empty()));
        assertThat(dto0.legalPits().isEmpty(), is(false));
        assertThat(dto1.legalPits(), is(empty()));
        assertThat(dto0.legalPits(), everyItem(org.hamcrest.Matchers.lessThan(16)));
    }

    @Test
    void theSidesNameWhichSeatOwnsWhichRows() throws Exception {
        Mangola game = new Mangola(GameId.generate(), MangolaRules.OWNER, SEAT_1);

        MangolaDto dto = view(game, SEAT_0);

        assertThat(dto.seatA(), is(1));
        assertThat(dto.players().get(0).side(), is("B"));
        assertThat(dto.players().get(1).side(), is("A"));
        // seat 0 owns side B: its draft is on rows 3 and 4
        assertThat(at(dto.board(), halfOf(16)), is(Collections.nCopies(16, 0)));
        assertThat(at(dto.board(), halfOf(0)), is(Collections.nCopies(16, (Integer) null)));
    }

    // ------------------------------------------------------------------ play

    @Test
    void aGameBuiltFromABoardStartsInPlayWithEverythingPublic() {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER,
                SEAT_0, MangolaLayouts.checkerboard());

        MangolaDto dto = view(game, SEAT_1);

        assertThat(dto.phase(), is("PLAY"));
        assertThat(dto.board(), is(MangolaLayouts.checkerboard().toList()));
        assertThat(dto.players().get(0).ready(), is(true));
        assertThat(dto.players().get(1).ready(), is(true));
        assertThat(dto.availableActions(), is(empty()));
    }

    @Test
    void legalPitsAreOnlyTheViewersWhenItIsToMove() {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0,
                MangolaTable.board("a2:3 c2:3 h4:2 g4:2"));

        assertThat(view(game, SEAT_0).legalPits(), contains(8, 10));
        assertThat(view(game, SEAT_1).legalPits(), is(empty()));
        assertThat(view(game, SEAT_0).availableActions(), is(List.of("SOW")));
        assertThat(view(game, SEAT_1).availableActions(), is(empty()));
    }

    @Test
    void lastMoveCarriesTheSeatThePitAndTheTrace() throws Exception {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0,
                MangolaTable.board("a2:3 h4:5"));
        game.sow(SEAT_0, MangolaGeometry.pit("a2"));

        MangolaDto dto = view(game, SEAT_1);

        assertThat(dto.lastMove(), is(notNullValue()));
        assertThat(dto.lastMove().seat(), is(0));
        assertThat(dto.lastMove().pit(), is(MangolaGeometry.pit("a2")));
        assertThat(dto.lastMove().trace().get(0), is(new MangolaDto.StepDto("PICK", 8, 3, null, null)));
        assertThat(dto.lastMove().trace().isEmpty(), is(false));
        assertThat(dto.currentSeat(), is(1));
        assertThat(dto.availableActions(), is(List.of("SOW")));
    }

    @Test
    void everyKindOfStepKeepsItsFieldsOnTheWire() {
        assertThat(MangolaDto.StepDto.from(new SowingStep.Pick(8, 3)), is(new MangolaDto.StepDto("PICK", 8, 3, null, null)));
        assertThat(MangolaDto.StepDto.from(new SowingStep.Drop(9)), is(new MangolaDto.StepDto("DROP", 9, null, null, null)));
        assertThat(MangolaDto.StepDto.from(new SowingStep.SkipEmpty(11)), is(new MangolaDto.StepDto("SKIP_EMPTY", 11, null, null, null)));
        assertThat(MangolaDto.StepDto.from(new SowingStep.Capture(10, 18, 26, 5)), is(new MangolaDto.StepDto("CAPTURE", 10, 5, 18, 26)));
        assertThat(MangolaDto.StepDto.from(new SowingStep.Relay(12, 4)), is(new MangolaDto.StepDto("RELAY", 12, 4, null, null)));
        assertThat(MangolaDto.StepDto.from(new SowingStep.ResumeAfter(8)), is(new MangolaDto.StepDto("RESUME_AFTER", 8, null, null, null)));
    }

    @Test
    void aFinishedGameCarriesItsOutcomeAndNoActions() {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, MangolaTable.board("a2:3 h4:5"));
        game.forfeit(SEAT_1);

        MangolaDto dto = view(game, SEAT_0);

        assertThat(dto.phase(), is("FINISHED"));
        assertThat(dto.outcome(), is(new MangolaDto.Finished(0, "FORFEIT")));
        assertThat(dto.availableActions(), is(empty()));
        assertThat(dto.legalPits(), is(empty()));
    }

    @Test
    void aForfeitDuringPlacementDoesNotRevealTheOpponentsDraft() throws Exception {
        Mangola game = placement();
        game.arrange(SEAT_1, MangolaTable.allOnFirst());
        game.forfeit(SEAT_0);

        MangolaDto dto = view(game, SEAT_0);

        assertThat(dto.outcome(), is(new MangolaDto.Finished(1, "FORFEIT")));
        // the board was never revealed: the opponent's draft stays hidden even after the end
        assertThat(at(dto.board(), halfOf(16)), is(Collections.nCopies(16, (Integer) null)));
    }

    @Test
    void repetitionsAreShownFromTheSecondOccurrence() {
        Mangola game = Mangola.fromLayout(GameId.generate(), MangolaRules.OWNER, SEAT_0, MangolaTable.board("a2:3 h4:5"));

        assertThat(view(game, SEAT_0).repetitions(), is(nullValue()));
    }
}
