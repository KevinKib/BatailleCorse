package org.kevinkib.cardgames.bullshit.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.domain.CannotCallBullshitException;
import org.kevinkib.cardgames.bullshit.domain.NotPlayersTurnException;
import org.kevinkib.cardgames.bullshit.presentation.dto.BullshitDto;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.CallBullshitEventData;
import org.kevinkib.cardgames.bullshit.presentation.dto.event.DiscardEventData;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.api.Response;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameFactories;
import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.sessionmanagement.core.infrastructure.InMemorySessionRepository;
import org.kevinkib.cards.domain.Card;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BullshitGameActionsTest {

    static final class RecordingMessaging extends GameMessagingService {
        final List<PlayerId> seats = new ArrayList<>();
        final List<Response> payloads = new ArrayList<>();

        RecordingMessaging() {
            super(null, null);
        }

        @Override
        public void sendToSeat(GameId gameId, PlayerId seat, Object payload) {
            seats.add(seat);
            payloads.add((Response) payload);
        }
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private InMemorySessionRepository repository;
    private SessionService sessionService;
    private RecordingMessaging messaging;
    private BullshitGameActions actions;
    private Bullshit game;
    private GameId id;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        repository = new InMemorySessionRepository(clock);
        sessionService = new SessionService(repository, new GameFactories(List.of(new BullshitFactory())));
        messaging = new RecordingMessaging();
        actions = new BullshitGameActions(sessionService, new BullshitStateBroadcaster(messaging));
        game = (Bullshit) sessionService.createGame("bullshit", 3, GameMode.SOLO);
        id = game.getId();
    }

    @Test
    void givenValidDiscard_whenDiscard_thenMoveAppliedAndEveryVisibleSeatNotified() throws Exception {
        Card card = game.getPlayers().get(0).getCards().get(0);

        actions.discard(id, new PlayerId(0), List.of(card));

        assertThat(game.getDiscardPileSize(), is(1));
        assertThat(messaging.seats.size(), is(3));
        Response response = messaging.payloads.get(0);
        assertThat(response.getEventType(), is("DISCARD"));
        assertThat(response.getState(), instanceOf(BullshitDto.class));
        DiscardEventData data = (DiscardEventData) response.getEventData();
        assertThat(data.count(), is(1));
        assertThat(data.claimantSeat(), is(0));
        assertThat(data.claimedTargetLabel(), is("ACE"));
    }

    @Test
    void givenDiscard_thenSessionIsTouched() throws Exception {
        clock.now = clock.now.plus(Duration.ofMinutes(20));
        Card card = game.getPlayers().get(0).getCards().get(0);

        actions.discard(id, new PlayerId(0), List.of(card));

        assertThat(repository.evictStale(Duration.ofMinutes(5), Duration.ofMinutes(10)), is(empty()));
    }

    @Test
    void givenNotYourTurn_whenDiscard_thenDomainExceptionPropagatesAndNothingIsSent() {
        Card card = game.getPlayers().get(1).getCards().get(0);

        assertThrows(NotPlayersTurnException.class, () -> actions.discard(id, new PlayerId(1), List.of(card)));

        assertThat(messaging.seats, is(empty()));
    }

    @Test
    void givenClaimOnTable_whenCallBullshit_thenRevealBroadcastWithCards() throws Exception {
        actions.discard(id, new PlayerId(0), List.of(game.getPlayers().get(0).getCards().get(0)));
        messaging.seats.clear();
        messaging.payloads.clear();

        actions.callBullshit(id, new PlayerId(1));

        assertThat(messaging.seats.size(), is(3));
        Response response = messaging.payloads.get(0);
        assertThat(response.getEventType(), is("CALL_BULLSHIT"));
        CallBullshitEventData data = (CallBullshitEventData) response.getEventData();
        assertThat(data.revealedCards().size(), is(1));
        assertThat(data.callerSeat(), is(1));
    }

    @Test
    void givenNoClaim_whenCallBullshit_thenDomainExceptionPropagates() {
        assertThrows(CannotCallBullshitException.class, () -> actions.callBullshit(id, new PlayerId(1)));

        assertThat(messaging.seats, is(empty()));
    }
}
