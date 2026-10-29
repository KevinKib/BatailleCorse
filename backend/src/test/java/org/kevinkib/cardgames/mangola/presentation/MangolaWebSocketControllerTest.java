package org.kevinkib.cardgames.mangola.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.mangola.domain.Mangola;
import org.kevinkib.cardgames.mangola.domain.MangolaFactory;
import org.kevinkib.cardgames.mangola.domain.MangolaGeometry;
import org.kevinkib.cardgames.mangola.presentation.api.MangolaArrangePayload;
import org.kevinkib.cardgames.mangola.presentation.api.MangolaSowPayload;
import org.kevinkib.cardgames.mangola.presentation.dto.MangolaDto;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class MangolaWebSocketControllerTest {

    private static final org.kevinkib.cardgames.game.PlayerId SEAT_0 = MangolaTable.SEAT_0;
    private static final org.kevinkib.cardgames.game.PlayerId SEAT_1 = MangolaTable.SEAT_1;

    private final MangolaTable table = new MangolaTable();

    private static List<Integer> counts(int[] counts) {
        return Arrays.stream(counts).boxed().toList();
    }

    private RoomCreated createRoom() {
        return table.sessions.createRoom(MangolaFactory.GAME_TYPE, "Alice");
    }

    // ------------------------------------------------------------------ start

    @Test
    void start_byTheHostBroadcastsThePlacementToBothSeats() {
        RoomCreated room = createRoom();
        table.sessions.joinRoom(new GameId(room.gameId()), "Bob");

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.payloads, hasSize(2));
        Response first = table.messaging.payloads.get(0);
        assertThat(first.getEventType(), is("START"));
        MangolaDto state = (MangolaDto) first.getState();
        assertThat(state.phase(), is("PLACEMENT"));
        assertThat(state.availableActions(), is(List.of("ARRANGE")));
        assertThat(((MangolaDto) table.messaging.payloads.get(1).getState()).availableActions(), is(List.of("ARRANGE")));
    }

    @Test
    void start_withOnlyOnePlayerOrByANonHost_repliesAnErrorToTheSenderOnly() {
        RoomCreated room = createRoom();
        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));
        assertThat(table.messaging.payloads, hasSize(1));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.seats.get(0), is(SEAT_0));

        table.messaging.clear();
        String guestToken = table.sessions.joinRoom(new GameId(room.gameId()), "Bob").token();
        table.ws.start(new GameActionPayload(room.gameId(), guestToken));
        assertThat(table.messaging.seats, is(List.of(SEAT_1)));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void start_onAnotherGameTypesRoom_isRejectedAndDoesNotStartThatGame() {
        SessionService both = new SessionService(table.repository,
                new GameFactories(List.of(new MangolaFactory(MangolaTable.FIXED), new BullshitFactory())));
        MangolaWebSocketController ws = new MangolaWebSocketController(both, table.broadcaster,
                new SeatCommands(both, table.messaging), new MangolaGameActions(both, table.broadcaster));
        RoomCreated room = both.createRoom(BullshitFactory.GAME_TYPE, "Alice");
        both.joinRoom(new GameId(room.gameId()), "Bob");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(both.findGame(new GameId(room.gameId())).isPresent(), is(false));
        assertThat(table.messaging.payloads, hasSize(1));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
    }

    @Test
    void aRefusedStartOfALobby_carriesNoState() {
        RoomCreated room = createRoom();

        table.ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        assertThat(table.messaging.seats, is(List.of(SEAT_0)));
        assertThat(table.messaging.payloads.get(0).getState(), is(nullValue()));
    }

    // -------------------------------------------------------------- placement

    @Test
    void arrange_confirmAndEdit_areAuthenticatedByTheSeatToken() {
        Mangola game = table.placementGame();
        String gameId = game.getId().uuid().toString();

        table.ws.arrange(new MangolaArrangePayload(gameId, table.token(game, 0), counts(MangolaTable.twoPerPit())));
        assertThat(game.layoutOf(SEAT_0).orElseThrow(), is(counts(MangolaTable.twoPerPit())));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("LAYOUT_CHANGED"));

        table.ws.arrange(new MangolaArrangePayload(gameId, "not-a-token", counts(MangolaTable.allOnFirst())));
        assertThat(game.layoutOf(SEAT_1).isPresent(), is(false));
        assertThat(table.messaging.payloads, hasSize(2));

        table.ws.confirmLayout(new GameActionPayload(gameId, table.token(game, 0)));
        assertThat(game.isReady(SEAT_0), is(true));
        assertThat(table.messaging.payloads.get(2).getEventType(), is("LAYOUT_CONFIRMED"));

        table.ws.editLayout(new GameActionPayload(gameId, table.token(game, 0)));
        assertThat(game.isReady(SEAT_0), is(false));
        assertThat(table.messaging.payloads.get(4).getEventType(), is("LAYOUT_WITHDRAWN"));
    }

    @Test
    void theOtherSeatsFramesNeverCarryTheDraftThroughTheWholePlacement() {
        Mangola game = table.placementGame();
        String gameId = game.getId().uuid().toString();

        table.ws.arrange(new MangolaArrangePayload(gameId, table.token(game, 1), counts(MangolaTable.allOnFirst())));
        table.ws.confirmLayout(new GameActionPayload(gameId, table.token(game, 1)));

        for (int i = 0; i < table.messaging.payloads.size(); i++) {
            if (table.messaging.seats.get(i).equals(SEAT_0)) {
                MangolaDto toSeat0 = (MangolaDto) table.messaging.payloads.get(i).getState();
                assertThat(toSeat0.board().stream().filter(c -> c != null && c > 0).count(), is(0L));
            }
        }
    }

    @Test
    void aRefusedConfirmationRepliesTheLocaleKeyToTheSenderOnlyWithoutShowingTheOtherDraft() {
        Mangola game = table.placementGame();
        String gameId = game.getId().uuid().toString();
        List<Integer> stuckStart = List.of(24, 1, 0, 1, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 1);
        List<Integer> facing = List.of(2, 1, 1, 1, 1, 2, 3, 0, 3, 0, 1, 5, 2, 2, 3, 5);
        table.ws.arrange(new MangolaArrangePayload(gameId, table.token(game, 0), stuckStart));
        table.ws.arrange(new MangolaArrangePayload(gameId, table.token(game, 1), facing));
        table.ws.confirmLayout(new GameActionPayload(gameId, table.token(game, 1)));
        table.messaging.clear();

        table.ws.confirmLayout(new GameActionPayload(gameId, table.token(game, 0)));

        assertThat(table.messaging.seats, is(List.of(SEAT_0)));
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("LAYOUT_CONFIRMED"));
        assertThat(error.getMessage(), is("mangola.errors.stuckStart"));
        MangolaDto state = (MangolaDto) error.getState();
        assertThat(state.phase(), is("PLACEMENT"));
        // its own draft, and nothing of the other half
        assertThat(state.board().subList(0, 16).stream().mapToInt(c -> c).sum(), is(32));
        assertThat(state.board().subList(16, 32).stream().allMatch(c -> c == null), is(true));
        assertThat(game.isReady(SEAT_0), is(false));
    }

    @Test
    void sow_isRefusedDuringPlacementToTheSenderOnly() {
        Mangola game = table.placementGame();

        table.ws.sow(new MangolaSowPayload(game.getId().uuid().toString(), table.token(game, 0), 8));

        assertThat(table.messaging.seats, is(List.of(SEAT_0)));
        assertThat(table.messaging.payloads.get(0).isSuccess(), is(false));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("SOWN"));
        assertThat(game.version(), is(0));
    }

    // ------------------------------------------------------------------- play

    @Test
    void sow_isAuthenticatedByTheSeatToken() {
        Mangola game = table.playGame(MangolaTable.board("a2:3 h1:2 h4:5"));
        String gameId = game.getId().uuid().toString();

        table.ws.sow(new MangolaSowPayload(gameId, table.token(game, 0), MangolaGeometry.pit("a2")));

        assertThat(game.board().seeds(MangolaGeometry.pit("a2")), is(0));
        assertThat(game.currentSeat(), is(SEAT_1));
        assertThat(table.messaging.payloads.get(0).getEventType(), is("SOWN"));
    }

    @Test
    void aRejectedCommandRepliesToTheSenderOnlyWithItsCurrentState() {
        Mangola game = table.playGame(MangolaTable.board("a2:3 h4:5"));
        String gameId = game.getId().uuid().toString();

        table.ws.sow(new MangolaSowPayload(gameId, table.token(game, 1), MangolaGeometry.pit("h4")));

        assertThat(table.messaging.seats, is(List.of(SEAT_1)));
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("SOWN"));
        assertThat(((MangolaDto) error.getState()).version(), is(0));
        assertThat(game.version(), is(0));
    }

    @Test
    void commandsWithAMissingFieldAreRejectedWithoutAnyEffect() {
        Mangola placement = table.placementGame();
        Mangola play = table.playGame(MangolaTable.board("a2:3 h4:5"));

        table.ws.arrange(new MangolaArrangePayload(placement.getId().uuid().toString(), table.token(placement, 0), null));
        table.ws.arrange(new MangolaArrangePayload(placement.getId().uuid().toString(), table.token(placement, 0),
                Arrays.asList(2, null, 2)));
        table.ws.sow(new MangolaSowPayload(play.getId().uuid().toString(), table.token(play, 0), null));

        assertThat(table.messaging.payloads, hasSize(3));
        assertThat(table.messaging.payloads.stream().noneMatch(Response::isSuccess), is(true));
        assertThat(placement.version(), is(0));
        assertThat(play.version(), is(0));
    }

    @Test
    void aMalformedOrUnknownGameId_isIgnoredWithoutAnyException() {
        for (String rawId : new String[]{"not-a-uuid", "", UUID.randomUUID().toString()}) {
            table.ws.arrange(new MangolaArrangePayload(rawId, "token", counts(MangolaTable.twoPerPit())));
            table.ws.confirmLayout(new GameActionPayload(rawId, "token"));
            table.ws.editLayout(new GameActionPayload(rawId, "token"));
            table.ws.sow(new MangolaSowPayload(rawId, "token", 0));
            table.ws.start(new GameActionPayload(rawId, "token"));
        }

        assertThat(table.messaging.payloads.isEmpty(), is(true));
    }
}
