package org.kevinkib.cardgames.draughts.presentation.bot;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.draughts.domain.Draughts;
import org.kevinkib.cardgames.draughts.domain.DraughtsFactory;
import org.kevinkib.cardgames.draughts.domain.DraughtsMove;
import org.kevinkib.cardgames.draughts.domain.DraughtsResult;
import org.kevinkib.cardgames.draughts.domain.bot.DraughtsBotLevel;
import org.kevinkib.cardgames.draughts.domain.bot.ExpertDraughtsBot;
import org.kevinkib.cardgames.draughts.presentation.DraughtsGameActions;
import org.kevinkib.cardgames.draughts.presentation.dto.DraughtsDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;

/**
 * Whole games through the real lobby, session core, broadcaster, coordinator and use cases, with a manual scheduler
 * and seeded randomness: a "human" who plays at random against a computer seat of every level. The bot seat must
 * never be messaged, never forfeit and never stall the table.
 */
class DraughtsBotGameIT {

    private static final int STEP_BOUND = 2_000;
    private static final int PLY_BOUND = 150;
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    private static final class Table {
        final SessionService sessions;
        final ManualBotScheduler scheduler = new ManualBotScheduler();
        final DraughtsGameActions actions;
        final Random human;
        final Draughts game;

        Table(DraughtsBotLevel level, long seed) throws Exception {
            sessions = new SessionTable(new DraughtsFactory(new Random(seed * 13))).sessions;
            RecordingMessaging messaging = new RecordingMessaging() {
                @Override
                public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
                    if (sessions.botSeats(gameId).contains(seat)) {
                        throw new AssertionError("A bot seat was messaged: " + seat);
                    }
                    super.sendToSeat(gameId, seat, payload);
                }
            };
            SeatStateBroadcaster<Draughts, DraughtsDto> broadcaster = new SeatStateBroadcaster<>(messaging,
                    sessions::botSeats, sessions::seatNames,
                    (g, viewer, names, bots) -> DraughtsDto.forViewer(g, viewer, names));
            actions = new DraughtsGameActions(sessions, broadcaster);
            Random botRandom = new Random(seed);
            human = new Random(seed * 31 + 7);
            ExpertDraughtsBot.Budget budget = new ExpertDraughtsBot.Budget(0, 6, 20_000);
            broadcaster.addListener(new DraughtsBotCoordinator(sessions::botSeats, sessions::seatOptions, actions,
                    scheduler, new FixedThinkingDelay(Duration.ZERO), l -> l.bot(botRandom, budget)));

            RoomCreated room = sessions.createRoom(DraughtsFactory.GAME_TYPE, "Alice");
            GameId id = new GameId(room.gameId());
            sessions.addBot(id, room.hostToken());
            sessions.setSeatOption(id, room.hostToken(), 1, DraughtsFactory.LEVEL_KEY, level.key());
            game = (Draughts) sessions.startGame(id, room.hostToken());
            broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
        }
    }

    /** The human plays a random legal move on its turn. */
    private static void play(Table table) throws Exception {
        Draughts game = table.game;
        int steps = 0;
        while (!game.isFinished() && game.history().size() < PLY_BOUND && steps++ < STEP_BOUND) {
            if (!table.scheduler.pending().isEmpty()) {
                table.scheduler.runNext();
            } else if (game.currentSeat().equals(HUMAN)) {
                List<DraughtsMove> legal = game.legalMoves(HUMAN);
                DraughtsMove move = legal.get(table.human.nextInt(legal.size()));
                table.actions.move(game.getId(), HUMAN, move.from(), move.to(), move.captures());
            } else {
                throw new AssertionError("The table stalled at step " + steps + " with the bot on turn");
            }
        }
    }

    @Test
    void everyLevelPlaysOnWithoutStallingForfeitingOrBeingMessaged() throws Exception {
        for (DraughtsBotLevel level : DraughtsBotLevel.values()) {
            for (long seed = 1; seed <= 3; seed++) {
                Table table = new Table(level, seed);

                play(table);

                String context = level + " seed " + seed;
                assertThat(context + " made progress", table.game.history().size() > 2, is(true));
                if (table.game.isFinished()) {
                    assertThat(context, table.game.result().reason(), is(not(DraughtsResult.Reason.FORFEIT)));
                }
                assertThat(context, table.sessions.botSeats(table.game.getId()).contains(BOT), is(true));
            }
        }
    }
}
