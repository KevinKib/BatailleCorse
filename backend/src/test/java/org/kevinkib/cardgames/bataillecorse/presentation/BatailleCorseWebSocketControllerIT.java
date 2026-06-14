package org.kevinkib.cardgames.bataillecorse.presentation;
import org.kevinkib.cardgames.presentation.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Disabled("WebSocket layer — STOMP/SockJS transport test, excluded from regular CI runs")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatailleCorseWebSocketControllerIT {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    private StompSession stompSession;

    @BeforeEach
    void setUp() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
        stompSession = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenNoGame_whenCreateGame_thenResponseIsSuccessAndGameStateIsConsistent() throws Exception {
        CompletableFuture<String> responseFuture = new CompletableFuture<>();

        stompSession.subscribe("/topic/game", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                responseFuture.complete((String) payload);
            }
        });

        stompSession.send("/app/create", "");

        String json = responseFuture.get(5, TimeUnit.SECONDS);
        Map<String, Object> response = objectMapper.readValue(json, Map.class);

        assertThat(response.get("success"), is(true));
        assertThat(response.get("eventType"), is("CREATE"));
        assertThat(response.get("message"), is("Game created"));

        Map<String, Object> state = (Map<String, Object>) response.get("state");
        assertThat(state, notNullValue());

        // Two players, each holding half the deck (52 / 2 = 26)
        List<Map<String, Object>> players = (List<Map<String, Object>>) state.get("players");
        assertThat(players, hasSize(2));
        for (Map<String, Object> player : players) {
            assertThat(player.get("nbCards"), is(26));
        }

        // Pile starts empty and cannot be grabbed
        Map<String, Object> pile = (Map<String, Object>) state.get("pile");
        assertThat((List<?>) pile.get("cards"), empty());
        assertThat(pile.get("grabbable"), is(false));
        assertThat(pile.get("nbCardsSinceLastHonourCard"), is(0));
        assertThat(pile.get("playerThatAddedLastHonourCard"), nullValue());

        // First player is current and can only SEND (pile is empty so SLAP/GRAB unavailable)
        Map<String, Object> currentPlayer = (Map<String, Object>) state.get("currentPlayer");
        assertThat((List<String>) currentPlayer.get("availableActions"), contains("SEND"));
    }
}
