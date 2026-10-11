package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseScenario;
import org.kevinkib.cardgames.bataillecorse.domain.CannotGrabException;
import org.kevinkib.cardgames.bataillecorse.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.GrabEventData;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.SendEventData;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.event.SlapEventData;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.domain.SessionGame;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kevinkib.cards.domain.deck.french.FrenchRank.*;

class BatailleCorseGameActionsTest {

    private static final PlayerId SEAT_0 = new PlayerId(0);
    private static final PlayerId SEAT_1 = new PlayerId(1);

    private final InMemorySessionRepository repository = new InMemorySessionRepository(Clock.systemUTC());
    private final SessionService sessions =
            new SessionService(repository, new GameFactories(List.of(new BatailleCorseFactory())));
    private final RecordingMessaging messaging = new RecordingMessaging();
    private final BatailleCorseGameActions actions =
            new BatailleCorseGameActions(sessions, new BatailleCorseStateBroadcaster(messaging));

    private BatailleCorse stored(BatailleCorse game) {
        repository.save(game, SessionGame.create(game.getId(), game.getPlayerIds(), BatailleCorseFactory.GAME_TYPE));
        return game;
    }

    @Test
    void sendPlaysTheCardAndBroadcastsASendEvent() throws Exception {
        BatailleCorse game = stored(BatailleCorseScenario.game(List.of(TWO, THREE), List.of(FOUR, FIVE)));

        actions.send(game.getId(), SEAT_0);

        assertThat(game.getPileSize(), is(1));
        assertThat(messaging.toGame, hasSize(1));
        assertThat(messaging.last().getEventType(), is("SEND"));
        assertThat(messaging.last().getEventData(), instanceOf(SendEventData.class));
        assertThat(((SendEventData) messaging.last().getEventData()).player().getId(), is("0"));
    }

    @Test
    void aRefusedSendBroadcastsNothingAndThrows() {
        BatailleCorse game = stored(BatailleCorseScenario.game(List.of(TWO, THREE), List.of(FOUR, FIVE)));

        assertThrows(NotPlayersTurnException.class, () -> actions.send(game.getId(), SEAT_1));

        assertThat(messaging.toGame, hasSize(0));
    }

    @Test
    void slapBroadcastsTheOutcome() throws Exception {
        BatailleCorse game = stored(BatailleCorseScenario.game(List.of(TEN, THREE), List.of(FOUR, FIVE)));
        actions.send(game.getId(), SEAT_0);

        boolean successful = actions.slap(game.getId(), SEAT_1);

        assertThat(successful, is(true));
        assertThat(messaging.last().getEventType(), is("SLAP"));
        SlapEventData data = (SlapEventData) messaging.last().getEventData();
        assertThat(data.isSuccessful(), is(true));
        assertThat(data.player().getId(), is("1"));
    }

    @Test
    void grabBroadcastsAGrabEvent() throws Exception {
        BatailleCorse game = stored(BatailleCorseScenario.game(List.of(JACK, THREE), List.of(FOUR, FIVE)));
        actions.send(game.getId(), SEAT_0);
        actions.send(game.getId(), SEAT_1);

        actions.grab(game.getId(), SEAT_0);

        assertThat(messaging.last().getEventType(), is("GRAB"));
        assertThat(((GrabEventData) messaging.last().getEventData()).player().getId(), is("0"));
    }

    @Test
    void aRefusedGrabBroadcastsNothingAndThrows() throws Exception {
        BatailleCorse game = stored(BatailleCorseScenario.game(List.of(TWO, THREE), List.of(FOUR, FIVE)));
        actions.send(game.getId(), SEAT_0);

        assertThrows(CannotGrabException.class, () -> actions.grab(game.getId(), SEAT_0));

        assertThat(messaging.toGame, hasSize(1));
    }
}
