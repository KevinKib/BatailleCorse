package org.kevinkib.cardgames.kobo.presentation.bot;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.domain.KoboFixtures;
import org.kevinkib.cardgames.kobo.domain.RandomDeckSource;
import org.kevinkib.cardgames.kobo.domain.ScriptedDeckSource;
import org.kevinkib.cardgames.kobo.domain.bot.HeuristicKoboBot;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.presentation.bot.FixedThinkingDelay;
import org.kevinkib.cardgames.presentation.bot.ManualBotScheduler;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.state.SeatStateBroadcaster;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;
import org.kevinkib.cardgames.presentation.support.SessionTable;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cards.domain.Card;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.kobo.domain.KoboBuilder.seat;

/**
 * A whole round through the real lobby, session core, broadcaster, coordinator and use cases, with a manual scheduler
 * and zero delays: two scripted humans and one bot at {@code hard} on a fixed deal. The bot has to ready, play its
 * turns, match the 5 it saw at the start when a human discards a 5, and the round has to reach {@code ROUND_OVER}.
 */
class KoboBotGameIT {

    private static final int STEP_BOUND = 3_000;
    private static final PlayerId ALICE = seat(0);
    private static final PlayerId BOB = seat(1);
    private static final PlayerId BOT = seat(2);

    private static List<Card> cards(String... codes) {
        return Stream.of(codes).map(KoboFixtures::card).toList();
    }

    private final ScriptedDeckSource decks = new ScriptedDeckSource(new RandomDeckSource());
    private final ManualBotScheduler scheduler = new ManualBotScheduler();
    private final SessionService sessions = new SessionTable(new KoboFactory(decks)).sessions;
    private final Set<PlayerId> messaged = new java.util.HashSet<>();
    private KoboGameActions actions;
    private SeatStateBroadcaster<Kobo, KoboDto> broadcaster;
    private Kobo game;
    private GameId id;
    private int humanTurnsEnded;

    private void openTable() throws Exception {
        RecordingMessaging messaging = new RecordingMessaging() {
            @Override
            public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
                if (sessions.botSeats(gameId).contains(seat)) {
                    throw new AssertionError("A bot seat was messaged: " + seat);
                }
                messaged.add(seat);
                super.sendToSeat(gameId, seat, payload);
            }
        };
        broadcaster = new SeatStateBroadcaster<>(messaging, sessions::botSeats, sessions::seatNames,
                (g, viewer, names, bots) -> KoboDto.forViewer(g, viewer, names, bots));
        actions = new KoboGameActions(sessions, broadcaster);
        Random botRandom = new Random(7);
        broadcaster.addListener(new KoboBotCoordinator(sessions::botSeats, sessions::seatOptions, actions, scheduler,
                new FixedThinkingDelay(Duration.ZERO), tuning -> Duration.ZERO,
                level -> new HeuristicKoboBot(level.tuning()), botRandom));

        RoomCreated room = sessions.createRoom(KoboFactory.GAME_TYPE, "Alice");
        id = new GameId(room.gameId());
        sessions.joinRoom(id, "Bob");
        sessions.addBot(id, room.hostToken());
        sessions.setSeatOption(id, room.hostToken(), 2, KoboFactory.LEVEL_KEY, "hard");
        // Alice and Bob hold plain cards, the bot starts with a 5 and a king in its top row; a 5 comes off the pile
        decks.fix(id, 1, cards("AH", "2H", "3H", "4H", "AD", "2D", "3D", "4D", "9S", "8S", "5H", "KS", "5C"));
        game = (Kobo) sessions.startGame(id, room.hostToken());
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");
    }

    @Test
    void theBotReadiesMatchesItsStartingFiveAndTheRoundReachesRoundOver() throws Exception {
        openTable();
        actions.ready(id, ALICE);
        actions.ready(id, BOB);
        scheduler.runNext(); // the bot says ready: the barrier opens
        assertThat(game.phase(), is(Phase.DRAW));
        assertThat(game.currentSeat(), is(ALICE));

        actions.draw(id, ALICE);
        actions.discardDrawn(id, ALICE); // a 5 on the pile: the bot remembers the 5 it started with
        scheduler.runNext();

        assertThat(game.handSize(BOT), is(3));
        assertThat(game.discardTop().get(), is(KoboFixtures.card("5H")));

        playUntil(Phase.ROUND_OVER);

        assertThat(game.phase(), is(Phase.ROUND_OVER));
        assertThat(game.lastRoundResult().isPresent(), is(true));
        assertThat(game.getPlayerIds().contains(BOT), is(true)); // never forfeited
        assertThat(messaged.contains(BOT), is(false));
        assertThat(messaged, is(Set.of(ALICE, BOB)));
    }

    @Test
    void playAgainKeepsTheBotAndItsLevel() throws Exception {
        openTable();

        sessions.playAgain(id, "Alice");

        // the reopened room re-seats the bot on the lowest free seat after the host, with its level
        assertThat(sessions.botSeats(id), is(Set.of(BOB)));
        assertThat(sessions.seatOptions(id, BOB).get(KoboFactory.LEVEL_KEY), is("hard"));
    }

    /** The bot's tasks run first; otherwise the scripted humans play a quiet game, Alice announcing Kobo on her 3rd turn. */
    private void playUntil(Phase target) throws Exception {
        int steps = 0;
        while (game.phase() != target && steps++ < STEP_BOUND) {
            if (!scheduler.pending().isEmpty()) {
                scheduler.runNext();
            } else if (!aHumanActs()) {
                throw new AssertionError("The table stalled at step " + steps + " in " + game.phase());
            }
        }
    }

    private boolean aHumanActs() throws Exception {
        PlayerId seat = game.currentSeat();
        boolean human = !seat.equals(BOT);
        switch (game.phase()) {
            case MEMORISING, ROUND_OVER -> {
                for (PlayerId player : List.of(ALICE, BOB)) {
                    if (!game.readySeats().contains(player)) {
                        actions.ready(id, player);
                        return true;
                    }
                }
                return false;
            }
            case DRAW -> {
                if (!human) {
                    return false;
                }
                actions.draw(id, seat);
            }
            case DECISION -> {
                if (!human) {
                    return false;
                }
                actions.discardDrawn(id, seat);
            }
            case POWER, KING_DECISION -> {
                if (!human) {
                    return false;
                }
                actions.skipPower(id, seat);
            }
            case TURN_END -> {
                if (!human) {
                    return false;
                }
                if (seat.equals(ALICE) && ++humanTurnsEnded >= 3 && game.announcer().isEmpty()
                        && game.availableActions(seat).contains(org.kevinkib.cardgames.kobo.domain.turn.Action.ANNOUNCE_KOBO)) {
                    actions.announceKobo(id, seat);
                } else {
                    actions.endTurn(id, seat);
                }
            }
            case GIFT -> {
                if (!human) {
                    return false;
                }
                actions.giveTen(id, seat, seat.equals(ALICE) ? BOB : ALICE);
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
