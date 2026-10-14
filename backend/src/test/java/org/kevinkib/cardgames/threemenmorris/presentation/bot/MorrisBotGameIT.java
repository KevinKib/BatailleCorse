package org.kevinkib.cardgames.threemenmorris.presentation.bot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.Action;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisResult;
import org.kevinkib.cardgames.threemenmorris.domain.MorrisVariant;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorris;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.domain.bot.MorrisBotLevel;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisGameActions;
import org.kevinkib.cardgames.threemenmorris.presentation.dto.ThreeMenMorrisDto;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Whole games through the real lobby, session core, broadcaster, coordinator and use cases, with a manual
 * scheduler and seeded randomness: a "human" who plays at random and offers draws now and then, against a
 * computer seat of every level, in every variant and for both colours.
 */
class MorrisBotGameIT {

    private static final int STEP_BOUND = 5_000;
    private static final PlayerId HUMAN = new PlayerId(0);
    private static final PlayerId BOT = new PlayerId(1);

    private static final class Table {
        final SessionService sessions;
        final ManualBotScheduler scheduler = new ManualBotScheduler();
        final ThreeMenMorrisGameActions actions;
        final ThreeMenMorris game;
        final Random human;

        Table(MorrisVariant variant, String hostColour, MorrisBotLevel level, long seed) throws Exception {
            sessions = new SessionTable(new ThreeMenMorrisFactory()).sessions;
            RecordingMessaging messaging = new RecordingMessaging() {
                @Override
                public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
                    if (sessions.botSeats(gameId).contains(seat)) {
                        throw new AssertionError("A bot seat was messaged: " + seat);
                    }
                    super.sendToSeat(gameId, seat, payload);
                }
            };
            SeatStateBroadcaster<ThreeMenMorris, ThreeMenMorrisDto> broadcaster = new SeatStateBroadcaster<>(
                    messaging, sessions::botSeats, sessions::seatNames,
                    (g, viewer, names, bots) -> ThreeMenMorrisDto.forViewer(g, viewer, names));
            actions = new ThreeMenMorrisGameActions(sessions, broadcaster);
            Random botRandom = new Random(seed);
            human = new Random(seed * 31 + 7);
            broadcaster.addListener(new MorrisBotCoordinator(sessions::botSeats, sessions::seatOptions, actions,
                    scheduler, new FixedThinkingDelay(Duration.ZERO), l -> l.bot(botRandom)));

            RoomCreated room = sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice", GameOptions.of(Map.of(
                    ThreeMenMorrisFactory.VARIANT_KEY, variant.key(),
                    ThreeMenMorrisFactory.HOST_COLOUR_KEY, hostColour)));
            GameId id = new GameId(room.gameId());
            sessions.addBot(id, room.hostToken());
            sessions.setSeatOption(id, room.hostToken(), 1, "level", level.key());
            game = (ThreeMenMorris) sessions.startGame(id, room.hostToken());
            broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
        }
    }

    /** The human plays a random legal move, and offers a draw about one turn in five when it may. */
    private static boolean humanActs(Table table) throws Exception {
        ThreeMenMorris game = table.game;
        if (game.drawActions(HUMAN).contains(Action.OFFER_DRAW) && table.human.nextInt(5) == 0) {
            table.actions.offerDraw(game.getId(), HUMAN);
            return true;
        }
        if (!game.currentSeat().equals(HUMAN)) {
            return false;
        }
        List<Integer> placements = game.legalPlacements(HUMAN);
        if (!placements.isEmpty()) {
            table.actions.place(game.getId(), HUMAN, placements.get(table.human.nextInt(placements.size())));
        } else {
            var moves = game.legalMoves(HUMAN);
            var move = moves.get(table.human.nextInt(moves.size()));
            table.actions.move(game.getId(), HUMAN, move.from(), move.to());
        }
        return true;
    }

    private static void play(Table table) throws Exception {
        int steps = 0;
        while (!table.game.isFinished() && steps++ < STEP_BOUND) {
            if (!table.scheduler.pending().isEmpty()) {
                table.scheduler.runNext();
            } else if (!humanActs(table)) {
                throw new AssertionError("The table stalled at step " + steps + " with nobody able to act");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MorrisVariant.class)
    void everyLevelFinishesEveryGameWithoutStallingOrForfeitingTheBot(MorrisVariant variant) throws Exception {
        for (MorrisBotLevel level : MorrisBotLevel.values()) {
            for (String colour : List.of("white", "black")) {
                for (long seed = 1; seed <= 12; seed++) {
                    Table table = new Table(variant, colour, level, seed);

                    play(table);

                    String context = variant + " " + level + " host " + colour + " seed " + seed;
                    assertThat(context + " finished", table.game.isFinished(), is(true));
                    assertThat(context, table.game.result().reason(), is(not(MorrisResult.Reason.FORFEIT)));
                    assertThat(context, table.game.result().reason(), in(List.of(MorrisResult.Reason.LINE,
                            MorrisResult.Reason.BLOCKED, MorrisResult.Reason.DRAW_AGREED,
                            MorrisResult.Reason.DRAW_REPETITION)));
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(MorrisVariant.class)
    void thePerfectBotPlayingWhiteWinsEveryGameEvenWhenTheHumanKeepsOfferingADraw(MorrisVariant variant)
            throws Exception {
        for (long seed = 1; seed <= 25; seed++) {
            Table table = new Table(variant, "black", MorrisBotLevel.PERFECT, seed); // the bot is white

            play(table);

            assertThat(variant + " seed " + seed, table.game.result().winner(), is(BOT));
        }
    }

    @Test
    void theBotAcceptsADrawOfferItCannotWin() throws Exception {
        // Tapatan, the bot is black: after white's corner opening black holds a draw at best, so it accepts.
        for (long seed = 1; seed <= 40; seed++) {
            Table table = new Table(MorrisVariant.TAPATAN, "white", MorrisBotLevel.PERFECT, seed);
            play(table);
            if (table.game.result().reason() == MorrisResult.Reason.DRAW_AGREED) {
                return; // at least one game was drawn by the bot's acceptance
            }
        }
        throw new AssertionError("The perfect bot never accepted a draw in 40 games of Tapatan as black");
    }
}
