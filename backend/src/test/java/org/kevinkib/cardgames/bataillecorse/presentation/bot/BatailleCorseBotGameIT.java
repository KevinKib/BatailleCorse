package org.kevinkib.cardgames.bataillecorse.presentation.bot;

import org.kevinkib.cardgames.presentation.command.SeatCommands;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.domain.Player;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseGameActions;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseStateBroadcaster;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseWebSocketController;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.SendEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * A host and a computer seat play a real room from START to the end with no client: the host sends and
 * grabs like the front end would, the bot reacts through the coordinator. Time is driven by a manual
 * scheduler so the test does not wait for the tier reaction times.
 */
class BatailleCorseBotGameIT {

    private static final int MAX_STEPS = 200_000;

    private final SessionService sessions = new SessionService(
            new InMemorySessionRepository(Clock.systemUTC()), new GameFactories(List.of(new BatailleCorseFactory())));
    private final RecordingMessaging messaging = new RecordingMessaging();
    private final BatailleCorseStateBroadcaster broadcaster = new BatailleCorseStateBroadcaster(messaging);
    private final BatailleCorseGameActions actions = new BatailleCorseGameActions(sessions, broadcaster);
    private final BatailleCorseWebSocketController ws =
            new BatailleCorseWebSocketController(sessions, new SeatCommands(sessions, messaging), actions, broadcaster);
    private final ManualBotScheduler scheduler = new ManualBotScheduler();

    @Test
    void aHostAndABotPlayARoomToTheEndWithNoClient() throws Exception {
        broadcaster.addListener(new BatailleCorseBotCoordinator(
                sessions::botSeats, sessions::seatOptions, sessions, actions, scheduler,
                new FixedThinkingDelay(Duration.ofMillis(100)), Duration.ZERO));
        RoomCreated room = sessions.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice");
        GameId id = new GameId(room.gameId());
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 1, "tier", "8");

        ws.start(new GameActionPayload(room.gameId(), room.hostToken()));

        BatailleCorse game = sessions.getGame(id, BatailleCorse.class);
        PlayerId host = new PlayerId(0);
        PlayerId bot = new PlayerId(1);
        int steps = 0;
        while (!game.isFinished() && steps++ < MAX_STEPS) {
            Player hostPlayer = game.getPlayerByIndex(host.id());
            if (game.autoGrabPlayer().filter(p -> p.id().equals(host)).isPresent()) {
                actions.grab(id, host); // the client grabs for the host after its own delay
            } else if (game.getAvailableActions(hostPlayer).contains(org.kevinkib.cardgames.bataillecorse.domain.Action.SEND)) {
                actions.send(id, host);
            } else if (!scheduler.pending().isEmpty()) {
                scheduler.runAllPending(); // time passes: the bot reacts
            } else {
                throw new AssertionError("The table is stuck: nobody can act and the bot has nothing scheduled");
            }
        }

        assertThat("the game ended", game.isFinished(), is(true));
        long botMoves = messaging.toGame.stream().filter(r -> isBy(r, bot)).count();
        assertThat(botMoves, greaterThan(0L));
        assertThat(messaging.toGame.stream().noneMatch(r -> !r.isSuccess()), is(true));
        assertThat(scheduler.pending().isEmpty(), is(true));
    }

    private static boolean isBy(Response response, PlayerId seat) {
        Object data = response.getEventData();
        if (data instanceof SendEventData send) {
            return send.player().getId().equals(String.valueOf(seat.id()));
        }
        if (data instanceof org.kevinkib.cardgames.bataillecorse.presentation.dto.event.SlapEventData slap) {
            return slap.player().getId().equals(String.valueOf(seat.id()));
        }
        if (data instanceof org.kevinkib.cardgames.bataillecorse.presentation.dto.event.GrabEventData grab) {
            return grab.player().getId().equals(String.valueOf(seat.id()));
        }
        return false;
    }
}
