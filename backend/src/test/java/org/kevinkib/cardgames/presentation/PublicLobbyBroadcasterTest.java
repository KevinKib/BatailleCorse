package org.kevinkib.cardgames.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.dto.PublicLobbyListDto;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobby;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyList;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyState;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicLobbyBroadcasterTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private final Map<String, Long> versions = new HashMap<>();
    private final Map<String, List<PublicLobby>> current = new HashMap<>();
    private final List<String> sentTypes = new ArrayList<>();
    private final List<Object> sentPayloads = new ArrayList<>();
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<Instant> scheduledAt = new ArrayList<>();
    private boolean failing;
    private PublicLobbyBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            scheduled.add(invocation.getArgument(0));
            scheduledAt.add(invocation.getArgument(1));
            return null;
        });
        broadcaster = new PublicLobbyBroadcaster(this::listOf,
                (type, payload) -> {
                    sentTypes.add(type);
                    sentPayloads.add(payload);
                },
                scheduler, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private PublicLobbyList listOf(String type) {
        if (failing) {
            throw new IllegalStateException("boom");
        }
        long version = versions.merge(type, 1L, Long::sum);
        List<PublicLobby> lobbies = current.getOrDefault(type, List.of());
        return new PublicLobbyList(type, lobbies, lobbies.size(), false, version);
    }

    private static PublicLobby lobby(String id, String type, String host) {
        return new PublicLobby(id, type, host, 1, 0, 4, PublicLobbyState.OPEN, NOW);
    }

    private void runScheduled() {
        List<Runnable> toRun = List.copyOf(scheduled);
        scheduled.clear();
        toRun.forEach(Runnable::run);
    }

    @Test
    void givenTenChangesWithinTheWindow_whenTheScheduledSendRuns_thenExactlyOneListIsSentWithTheLatestState() {
        for (int i = 0; i < 10; i++) {
            current.put("kobo", List.of(lobby("g" + i, "kobo", "Host " + i)));
            broadcaster.changed("kobo");
        }

        assertThat(sentPayloads, is(empty()));
        assertThat(scheduled.size(), is(1));
        runScheduled();

        assertThat(sentTypes, contains("kobo"));
        PublicLobbyListDto sent = (PublicLobbyListDto) sentPayloads.get(0);
        assertThat(sent.lobbies().get(0).gameId(), is("g9"));
    }

    @Test
    void givenAChange_whenScheduled_thenTheSendIsFiveHundredMillisecondsAway() {
        broadcaster.changed("kobo");

        assertThat(scheduledAt, contains(NOW.plusMillis(500)));
    }

    @Test
    void givenTwoTypesChanging_whenScheduled_thenEachGetsItsOwnSend() {
        broadcaster.changed("kobo");
        broadcaster.changed("bullshit");
        broadcaster.changed("kobo");

        assertThat(scheduled.size(), is(2));
        runScheduled();

        assertThat(sentTypes.stream().sorted().toList(), contains("bullshit", "kobo"));
    }

    @Test
    void givenASendHasRun_whenAnotherChangeComes_thenANewSendIsScheduled() {
        broadcaster.changed("kobo");
        runScheduled();
        broadcaster.changed("kobo");
        runScheduled();

        assertThat(sentTypes, contains("kobo", "kobo"));
        List<Long> versionsSent = sentPayloads.stream().map(p -> ((PublicLobbyListDto) p).version()).toList();
        assertThat(versionsSent, contains(1L, 2L));
    }

    @Test
    void givenAListWithOpenedAt_whenSerialised_thenThePayloadHasNeitherTimestampNorToken() throws Exception {
        current.put("kobo", List.of(lobby("g1", "kobo", "Alice")));
        broadcaster.changed("kobo");
        runScheduled();

        String json = new ObjectMapper().writeValueAsString(sentPayloads.get(0));

        assertThat(json, containsString("\"hostName\":\"Alice\""));
        assertThat(json, not(containsString("openedAt")));
        assertThat(json, not(containsString("token")));
    }

    @Test
    void givenTheSourceFails_whenTheScheduledSendRuns_thenNothingIsSentAndTheNextChangeStillSchedules() {
        failing = true;
        broadcaster.changed("kobo");
        runScheduled();

        assertThat(sentPayloads, is(empty()));

        failing = false;
        broadcaster.changed("kobo");
        assertThat(scheduled.size(), is(1));
        runScheduled();
        assertThat(sentTypes, contains("kobo"));
    }
}
