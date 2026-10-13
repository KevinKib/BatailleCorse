package org.kevinkib.cardgames.presentation.command;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.presentation.support.StartedRooms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class SeatCommandsTest {

    private final SessionTable table = new SessionTable(new FakeGameFactory());
    private final SeatCommands commands = new SeatCommands(table.sessions, table.messaging);
    private final Game game = StartedRooms.start(table.sessions, "fake", 2);
    private final String rawId = game.getId().uuid().toString();
    private final List<PlayerId> ran = new ArrayList<>();

    @Test
    void anAuthenticatedSeatRunsTheActionAndNothingIsSentOnSuccess() {
        commands.handle(rawId, table.token(game, 1), "DRAW", (id, seat) -> ran.add(seat), (id, seat) -> "state");

        assertThat(ran, is(List.of(new PlayerId(1))));
        assertThat(table.messaging.payloads.isEmpty(), is(true));
    }

    @Test
    void aRefusedCommandRepliesToTheSenderAloneWithItsMessageAndState() {
        commands.handle(rawId, table.token(game, 1), "DRAW", (id, seat) -> {
            throw new IOException("Nope");
        }, (id, seat) -> "state of " + seat.id());

        assertThat(table.messaging.seats, is(List.of(new PlayerId(1))));
        Response error = table.messaging.payloads.get(0);
        assertThat(error.isSuccess(), is(false));
        assertThat(error.getEventType(), is("DRAW"));
        assertThat(error.getMessage(), is("Nope"));
        assertThat(error.getState(), is("state of 1"));
    }

    @Test
    void anUnexpectedFailureIsAlsoReportedToTheSender() {
        commands.handle(rawId, table.token(game, 0), "DRAW", (id, seat) -> {
            throw new IllegalStateException("Boom");
        }, (id, seat) -> null);

        assertThat(table.messaging.seats, is(List.of(new PlayerId(0))));
        assertThat(table.messaging.payloads.get(0).getMessage(), is("Boom"));
        assertThat(table.messaging.payloads.get(0).getState(), is(nullValue()));
    }

    @Test
    void aStateThatCannotBeBuiltIsLeftOut() {
        commands.handle(rawId, table.token(game, 0), "START", (id, seat) -> {
            throw new IllegalArgumentException("No game");
        }, (id, seat) -> {
            throw new IllegalStateException("not running");
        });

        assertThat(table.messaging.payloads.get(0).getMessage(), is("No game"));
        assertThat(table.messaging.payloads.get(0).getState(), is(nullValue()));
    }

    @Test
    void anUnknownTokenMalformedIdOrUnknownIdIsIgnored() {
        for (String[] bad : new String[][]{
                {rawId, "not-a-token"}, {rawId, UUID.randomUUID().toString()}, {rawId, null},
                {"not-a-uuid", "token"}, {"", "token"}, {null, "token"}, {UUID.randomUUID().toString(), "token"}}) {
            commands.handle(bad[0], bad[1], "DRAW", (id, seat) -> ran.add(seat), (id, seat) -> "state");
        }

        assertThat(ran.isEmpty(), is(true));
        assertThat(table.messaging.payloads.isEmpty(), is(true));
        assertThat(table.messaging.toGame.isEmpty(), is(true));
    }
}
