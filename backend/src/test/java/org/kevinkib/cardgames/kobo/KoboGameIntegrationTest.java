package org.kevinkib.cardgames.kobo;

import org.kevinkib.cardgames.kobo.domain.turn.Action;
import org.kevinkib.cardgames.kobo.domain.turn.Phase;
import org.kevinkib.cardgames.kobo.domain.tableau.Tableau;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.kobo.domain.Kobo;
import org.kevinkib.cardgames.kobo.presentation.KoboGameActions;
import org.kevinkib.cardgames.kobo.presentation.KoboStateBroadcaster;
import org.kevinkib.cardgames.kobo.presentation.dto.CardDto;
import org.kevinkib.cardgames.kobo.presentation.dto.KoboDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.RoomCreated;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.presence.application.GameLifecycleBroadcasters;
import org.kevinkib.cardgames.sessionmanagement.presence.port.GameLifecycleBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.instanceOf;

/**
 * Whole Kobo matches through the real Spring wiring: session core, lifecycle broadcaster, presence
 * forfeit and rematch, with a scripted driver that plays by the rules and checks the invariants.
 */
@SpringBootTest
class KoboGameIntegrationTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private KoboGameActions actions;

    @Autowired
    private KoboStateBroadcaster broadcaster;

    @Autowired
    private GameLifecycleBroadcasters lifecycleBroadcasters;

    @Autowired
    private org.kevinkib.cardgames.sessionmanagement.presence.application.PresenceService presence;

    @Autowired
    private ObjectMapper mapper;

    private Kobo startRoom(int players) {
        RoomCreated room = sessionService.createRoom("kobo", "P0");
        GameId id = new GameId(room.gameId());
        for (int i = 1; i < players; i++) {
            sessionService.joinRoom(id, "P" + i);
        }
        return (Kobo) sessionService.startGame(id, room.hostToken());
    }

    private void assertNoCrossSeatLeak(Kobo game) throws Exception {
        if (game.isRevealed() || game.isFinished()) {
            return;
        }
        for (PlayerId viewer : game.getPlayerIds()) {
            String json = mapper.writeValueAsString(broadcaster.stateFor(game, viewer));
            for (PlayerId other : game.getPlayerIds()) {
                if (other.equals(viewer)) {
                    continue;
                }
                for (Tableau.Slot slot : game.slots(other)) {
                    if (slot.occupied()) {
                        String name = CardDto.from(slot.card()).name();
                        assertThat("seat " + viewer + " sees " + name + " of seat " + other,
                                json.contains("\"name\":\"" + name + "\""), is(false));
                    }
                }
            }
        }
    }

    /** Plays one full step of the scripted driver; returns false when the game is over. */
    private boolean step(Kobo game, int counter) throws Exception {
        PlayerId current = game.currentSeat();
        switch (game.phase()) {
            case MEMORISING, ROUND_OVER -> {
                for (PlayerId p : game.getPlayerIds()) {
                    if (game.availableActions(p).contains(Action.READY)) {
                        actions.ready(game.getId(), p);
                    }
                }
            }
            case DRAW -> actions.draw(game.getId(), current);
            case DECISION -> {
                if (counter % 3 == 0) {
                    actions.discardDrawn(game.getId(), current);
                } else {
                    int slot = game.slots(current).stream().filter(Tableau.Slot::occupied).findFirst()
                            .map(Tableau.Slot::index).orElse(-1);
                    if (slot < 0) {
                        actions.discardDrawn(game.getId(), current);
                    } else {
                        actions.swapDrawn(game.getId(), current, slot);
                    }
                }
            }
            case POWER, KING_DECISION -> actions.skipPower(game.getId(), current);
            case TURN_END -> {
                if (game.availableActions(current).contains(Action.ANNOUNCE_KOBO) && counter % 7 == 0) {
                    actions.announceKobo(game.getId(), current);
                } else {
                    actions.endTurn(game.getId(), current);
                }
            }
            case GIFT -> {
                PlayerId target = game.getPlayerIds().stream().filter(p -> !p.equals(current)).findFirst().get();
                actions.giveTen(game.getId(), current, target);
            }
            default -> throw new IllegalStateException(game.phase().name());
        }
        return !game.isFinished();
    }

    @Test
    void aScriptedMatchRunsToTheEndKeepingEveryInvariant() throws Exception {
        Kobo game = startRoom(4);
        int guard = 0;
        int lastVersion = -1;
        while (!game.isFinished() && guard < 20_000) {
            assertThat(game.version() >= lastVersion, is(true));
            lastVersion = game.version();
            if (!game.isFinished()) {
                assertThat(game.cardsInPlay(), is(52));
                assertNoCrossSeatLeak(game);
            }
            step(game, guard++);
        }
        assertThat(game.isFinished(), is(true));
        assertThat(game.result().winners().isEmpty(), is(false));
        assertThat(game.totals().values().stream().anyMatch(t -> t > 100), is(true));
        KoboDto dto = broadcaster.stateFor(game, new PlayerId(0));
        assertThat(dto.outcome(), instanceOf(org.kevinkib.cardgames.kobo.presentation.dto.OutcomeDto.Finished.class));
    }

    @Test
    void forfeitsThroughPresenceKeepTheGameGoingThenEndItAndTheRoomCanBeReopened() throws Exception {
        Kobo game = startRoom(3);
        GameId id = game.getId();
        for (int i = 0; i < 6; i++) {
            step(game, i);
        }

        presence.forfeit(id, new PlayerId(2), org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason.RESIGNED);
        assertThat(game.getPlayerIds().size(), is(2));
        assertThat(game.isFinished(), is(false));
        step(game, 99);

        presence.forfeit(id, new PlayerId(1), org.kevinkib.cardgames.sessionmanagement.presence.port.ForfeitReason.DISCONNECTED);
        assertThat(game.isFinished(), is(true));
        assertThat(game.result().winners(), is(List.of(new PlayerId(0))));

        var again = sessionService.playAgain(id, "P0");
        assertThat(again.playerId().id(), is(0));
        Optional<Game> reopened = sessionService.findGame(id);
        assertThat(reopened.isPresent(), is(false));
        sessionService.joinRoom(id, "P1");
        Kobo fresh = (Kobo) sessionService.startGame(id, again.token());
        assertThat(fresh.isFinished(), is(false));
        assertThat(fresh.getPlayerIds().size(), is(2));
        assertThat(fresh.phase(), is(Phase.MEMORISING));
    }

    @Test
    void theLifecycleBroadcasterIsWiredForKobo() {
        Kobo game = startRoom(2);

        GameLifecycleBroadcaster found = lifecycleBroadcasters.broadcasterFor(game);

        assertThat(found, notNullValue());
        assertThat(found.supports(game), is(true));
    }
}
