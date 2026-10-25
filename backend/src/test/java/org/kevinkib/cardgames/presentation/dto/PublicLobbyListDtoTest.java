package org.kevinkib.cardgames.presentation.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.presentation.PublicLobbyTopics;
import org.kevinkib.cardgames.presentation.api.CreateLobbyPayload;
import org.kevinkib.cardgames.presentation.api.LobbyPrivatePayload;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobby;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyList;
import org.kevinkib.cardgames.sessionmanagement.core.application.PublicLobbyState;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** The published JSON shape of the public game list, and the payloads that go with it. */
class PublicLobbyListDtoTest {

    private final ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();

    @Test
    void theListIsPublishedWithoutOpenedAtAndWithoutAnySecret() {
        PublicLobbyList list = new PublicLobbyList("kobo",
                List.of(new PublicLobby("g1", "kobo", "Alice", 2, 1, 4, PublicLobbyState.OPEN,
                        Instant.parse("2026-10-26T10:00:00Z"))),
                7, true, 3L);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(PublicLobbyListDto.from(list)));

        List<String> fields = new java.util.ArrayList<>();
        fields.addAll(json.propertyNames());
        assertThat(fields, contains("gameType", "lobbies", "total", "truncated", "version"));
        assertThat(json.get("gameType").asText(), is("kobo"));
        assertThat(json.get("total").asInt(), is(7));
        assertThat(json.get("truncated").asBoolean(), is(true));
        assertThat(json.get("version").asLong(), is(3L));
        JsonNode row = json.get("lobbies").get(0);
        List<String> rowFields = new java.util.ArrayList<>();
        rowFields.addAll(row.propertyNames());
        assertThat(rowFields, contains("gameId", "gameType", "hostName", "joined", "bots", "maxPlayers", "state"));
        assertThat(row.get("state").asText(), is("OPEN"));
        assertThat(row.get("hostName").asText(), is("Alice"));
        assertThat(row.get("joined").asInt(), is(2));
        assertThat(row.get("bots").asInt(), is(1));
        assertThat(row.get("maxPlayers").asInt(), is(4));
    }

    @Test
    void theTopicIsPerGameType() {
        assertThat(PublicLobbyTopics.destination("kobo"), is("/topic/lobbies/kobo"));
    }

    @Test
    void thePrivatePayloadReadsGameIdTokenAndPrivateGame() {
        LobbyPrivatePayload payload = mapper.readValue(
                "{\"gameId\":\"g1\",\"token\":\"t\",\"privateGame\":true}", LobbyPrivatePayload.class);

        assertThat(payload, is(new LobbyPrivatePayload("g1", "t", true)));
    }

    @Test
    void theCreatePayloadReadsAnOptionalPrivateGame() {
        CreateLobbyPayload with = mapper.readValue(
                "{\"gameType\":\"kobo\",\"name\":\"A\",\"privateGame\":true}", CreateLobbyPayload.class);
        CreateLobbyPayload without = mapper.readValue(
                "{\"gameType\":\"kobo\",\"name\":\"A\"}", CreateLobbyPayload.class);

        assertThat(with.privateGame(), is(true));
        assertThat(without.privateGame(), nullValue());
    }
}
