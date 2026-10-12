package org.kevinkib.cardgames.bataillecorse.presentation;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.presentation.dto.BatailleCorseDto;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.EventData;

import java.util.ArrayList;
import java.util.List;
import org.kevinkib.cardgames.presentation.support.RecordingMessaging;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

class BatailleCorseStateBroadcasterTest {

    private final RecordingMessaging messaging = new RecordingMessaging();
    private final BatailleCorseStateBroadcaster broadcaster = new BatailleCorseStateBroadcaster(messaging);
    private final BatailleCorse game = new BatailleCorse(GameId.generate(), 2);

    @Test
    void sendsTheEventAndTheStateToTheGameTopic() {
        broadcaster.broadcast(game, "START", new EmptyEventData(), "Game started.");

        assertThat(messaging.toGame, hasSize(1));
        assertThat(messaging.last().getEventType(), is("START"));
        assertThat(messaging.last().getMessage(), is("Game started."));
        assertThat(messaging.last().getState(), instanceOf(BatailleCorseDto.class));
    }

    @Test
    void sendsTheGivenStateWhenOneIsProvided() {
        BatailleCorseDto state = BatailleCorseDto.from(game);

        broadcaster.broadcast(game, "FORFEIT", new EmptyEventData(), "Forfeited.", state);

        assertThat(messaging.last().getState(), is(sameInstance(state)));
    }

    @Test
    void tellsEveryListenerAfterTheSend() {
        List<String> calls = new ArrayList<>();
        broadcaster.addListener((g, type, data) -> calls.add("first:" + type + ":" + messaging.toGame.size()));
        broadcaster.addListener((g, type, data) -> calls.add("second:" + type));

        broadcaster.broadcast(game, "SEND", new EmptyEventData(), "sent");

        assertThat(calls, contains("first:SEND:1", "second:SEND"));
    }

    @Test
    void handsTheGameAndTheEventDataToTheListeners() {
        List<Object> seen = new ArrayList<>();
        EventData data = new EmptyEventData();
        broadcaster.addListener((g, type, d) -> {
            seen.add(g);
            seen.add(d);
        });

        broadcaster.broadcast(game, "SEND", data, "sent");

        assertThat(seen.get(0), is(sameInstance(game)));
        assertThat(seen.get(1), is(sameInstance(data)));
    }

    @Test
    void aFailingListenerDoesNotStopTheOthersNorTheCaller() {
        List<String> calls = new ArrayList<>();
        broadcaster.addListener((g, type, data) -> {
            throw new IllegalStateException("boom");
        });
        broadcaster.addListener((g, type, data) -> calls.add(type));

        broadcaster.broadcast(game, "SLAP", new EmptyEventData(), "slapped");

        assertThat(calls, contains("SLAP"));
    }
}
