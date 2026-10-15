package org.kevinkib.cardgames.presentation;

import org.kevinkib.cardgames.presentation.support.StartedRooms;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bataillecorse.presentation.BatailleCorseWebSocketController;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.bullshit.presentation.BullshitWebSocketController;
import org.kevinkib.cardgames.bullshit.presentation.api.BullshitDiscardPayload;
import org.kevinkib.cardgames.kobo.domain.KoboFactory;
import org.kevinkib.cardgames.kobo.presentation.KoboWebSocketController;
import org.kevinkib.cardgames.kobo.presentation.api.KoboGiftPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboMatchPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboSlotPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboSwapPayload;
import org.kevinkib.cardgames.kobo.presentation.api.KoboTargetPayload;
import org.kevinkib.cardgames.presentation.api.GameActionPayload;
import org.kevinkib.cardgames.presentation.api.LobbyOptionsPayload;
import org.kevinkib.cardgames.presentation.api.LobbyRenamePayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatOptionPayload;
import org.kevinkib.cardgames.presentation.api.LobbySeatPayload;
import org.kevinkib.cardgames.presentation.api.PresencePayload;
import org.kevinkib.cardgames.sessionmanagement.core.application.SessionService;
import org.kevinkib.cardgames.threemenmorris.domain.ThreeMenMorrisFactory;
import org.kevinkib.cardgames.threemenmorris.presentation.ThreeMenMorrisWebSocketController;
import org.kevinkib.cardgames.threemenmorris.presentation.api.ThreeMenMorrisMovePayload;
import org.kevinkib.cardgames.threemenmorris.presentation.api.ThreeMenMorrisPlacePayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A malformed seat token is an unknown token: 403 over REST, ignored over WebSocket, never a 500 or an
 * exception escaping the controller. Every endpoint that takes a token is exercised with each bad value.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MalformedTokenIT {

    private static final List<String> BAD_TOKENS = List.of(
            "stale-token", "", "a".repeat(1500), UUID.randomUUID().toString());

    @LocalServerPort
    private int port;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("sessionService")
    private SessionService sessions;
    @Autowired
    private BatailleCorseWebSocketController batailleCorse;
    @Autowired
    private BullshitWebSocketController bullshit;
    @Autowired
    private KoboWebSocketController kobo;
    @Autowired
    private ThreeMenMorrisWebSocketController threeMenMorris;
    @Autowired
    private org.kevinkib.cardgames.mangola.presentation.MangolaWebSocketController mangola;
    @Autowired
    private org.kevinkib.cardgames.president.presentation.PresidentWebSocketController president;
    @Autowired
    private LobbyController lobby;
    @Autowired
    private LifecycleController lifecycle;

    private static String label(String token) {
        return token.length() > 20 ? "long(" + token.length() + ")" : "[" + token + "]";
    }

    private Stream<DynamicTest> forEachBadToken(String name, Consumer<String> check) {
        return BAD_TOKENS.stream()
                .map(token -> DynamicTest.dynamicTest(name + " " + label(token), () -> check.accept(token)));
    }

    private int statusOfGet(String path, String token) {
        RestTemplate rest = new RestTemplate();
        rest.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        return rest.getForEntity("http://localhost:" + port + path + "?token={t}", String.class, token)
                .getStatusCode().value();
    }

    private Stream<DynamicTest> restViewAnswers403(String prefix, String gameType) {
        String started = StartedRooms.start(sessions, gameType, 2).getId().uuid().toString();
        String room = sessions.createRoom(gameType, "Alice").gameId();
        return Stream.of(started, room).flatMap(id ->
                forEachBadToken("GET " + prefix + " " + (id.equals(room) ? "lobby" : "started"),
                        t -> assertEquals(403, statusOfGet(prefix + id, t))));
    }

    @TestFactory
    Stream<DynamicTest> batailleCorseRest() {
        return restViewAnswers403("/api/game/", BatailleCorseFactory.GAME_TYPE);
    }

    @TestFactory
    Stream<DynamicTest> bullshitRest() {
        return restViewAnswers403("/api/bullshit/game/", BullshitFactory.GAME_TYPE);
    }

    @TestFactory
    Stream<DynamicTest> koboRest() {
        return restViewAnswers403("/api/kobo/game/", KoboFactory.GAME_TYPE);
    }

    @TestFactory
    Stream<DynamicTest> threeMenMorrisRest() {
        return restViewAnswers403("/api/three-men-morris/game/", ThreeMenMorrisFactory.GAME_TYPE);
    }

    @TestFactory
    Stream<DynamicTest> presidentRest() {
        String prefix = "/api/president/game/";
        String type = org.kevinkib.cardgames.president.domain.PresidentFactory.GAME_TYPE;
        String started = StartedRooms.start(sessions, type, 3).getId().uuid().toString();
        String room = sessions.createRoom(type, "Alice").gameId();
        return Stream.of(started, room).flatMap(id ->
                forEachBadToken("GET " + prefix + " " + (id.equals(room) ? "lobby" : "started"),
                        t -> assertEquals(403, statusOfGet(prefix + id, t))));
    }

    @TestFactory
    Stream<DynamicTest> mangolaRest() {
        return restViewAnswers403("/api/mangola/game/", org.kevinkib.cardgames.mangola.domain.MangolaFactory.GAME_TYPE);
    }

    @TestFactory
    Stream<DynamicTest> mangolaWebSocket() {
        String gameType = org.kevinkib.cardgames.mangola.domain.MangolaFactory.GAME_TYPE;
        String id = StartedRooms.start(sessions, gameType, 2).getId().uuid().toString();
        String room = sessions.createRoom(gameType, "Alice").gameId();
        return Stream.of(
                forEachBadToken("mangola start", t -> assertDoesNotThrow(() -> mangola.start(new GameActionPayload(room, t)))),
                forEachBadToken("mangola arrange", t -> assertDoesNotThrow(() -> mangola.arrange(
                        new org.kevinkib.cardgames.mangola.presentation.api.MangolaArrangePayload(id, t, List.of(2, 2))))),
                forEachBadToken("mangola confirmLayout", t -> assertDoesNotThrow(() -> mangola.confirmLayout(new GameActionPayload(id, t)))),
                forEachBadToken("mangola editLayout", t -> assertDoesNotThrow(() -> mangola.editLayout(new GameActionPayload(id, t)))),
                forEachBadToken("mangola sow", t -> assertDoesNotThrow(() -> mangola.sow(
                        new org.kevinkib.cardgames.mangola.presentation.api.MangolaSowPayload(id, t, 8)))))
                .flatMap(s -> s);
    }

    @TestFactory
    Stream<DynamicTest> batailleCorseWebSocket() {
        String id = StartedRooms.start(sessions, BatailleCorseFactory.GAME_TYPE, 2).getId().uuid().toString();
        String room = sessions.createRoom(BatailleCorseFactory.GAME_TYPE, "Alice").gameId();
        return Stream.of(
                forEachBadToken("bc start", t -> assertDoesNotThrow(() -> batailleCorse.start(new GameActionPayload(room, t)))),
                forEachBadToken("bc send", t -> assertDoesNotThrow(() -> batailleCorse.send(new GameActionPayload(id, t)))),
                forEachBadToken("bc slap", t -> assertDoesNotThrow(() -> batailleCorse.slap(new GameActionPayload(id, t)))),
                forEachBadToken("bc grab", t -> assertDoesNotThrow(() -> batailleCorse.grab(new GameActionPayload(id, t)))))
                .flatMap(s -> s);
    }

    @TestFactory
    Stream<DynamicTest> bullshitWebSocket() {
        String id = StartedRooms.start(sessions, BullshitFactory.GAME_TYPE, 2).getId().uuid().toString();
        String room = sessions.createRoom(BullshitFactory.GAME_TYPE, "Alice").gameId();
        return Stream.of(
                forEachBadToken("bullshit start", t -> assertDoesNotThrow(() -> bullshit.start(new GameActionPayload(room, t)))),
                forEachBadToken("bullshit discard", t -> assertDoesNotThrow(() -> bullshit.discard(new BullshitDiscardPayload(id, t, List.of())))),
                forEachBadToken("bullshit callBullshit", t -> assertDoesNotThrow(() -> bullshit.callBullshit(new GameActionPayload(id, t)))))
                .flatMap(s -> s);
    }

    @TestFactory
    Stream<DynamicTest> koboWebSocket() {
        String id = StartedRooms.start(sessions, KoboFactory.GAME_TYPE, 2).getId().uuid().toString();
        String room = sessions.createRoom(KoboFactory.GAME_TYPE, "Alice").gameId();
        return Stream.of(
                forEachBadToken("kobo start", t -> assertDoesNotThrow(() -> kobo.start(new GameActionPayload(room, t)))),
                forEachBadToken("kobo ready", t -> assertDoesNotThrow(() -> kobo.ready(new GameActionPayload(id, t)))),
                forEachBadToken("kobo draw", t -> assertDoesNotThrow(() -> kobo.draw(new GameActionPayload(id, t)))),
                forEachBadToken("kobo swapDrawn", t -> assertDoesNotThrow(() -> kobo.swapDrawn(new KoboSlotPayload(id, t, 0)))),
                forEachBadToken("kobo discardDrawn", t -> assertDoesNotThrow(() -> kobo.discardDrawn(new GameActionPayload(id, t)))),
                forEachBadToken("kobo skipPower", t -> assertDoesNotThrow(() -> kobo.skipPower(new GameActionPayload(id, t)))),
                forEachBadToken("kobo peek", t -> assertDoesNotThrow(() -> kobo.peek(new KoboTargetPayload(id, t, 0, 0)))),
                forEachBadToken("kobo blindSwap", t -> assertDoesNotThrow(() -> kobo.blindSwap(new KoboSwapPayload(id, t, 0, 1, 0)))),
                forEachBadToken("kobo kingSwap", t -> assertDoesNotThrow(() -> kobo.kingSwap(new KoboSlotPayload(id, t, 0)))),
                forEachBadToken("kobo endTurn", t -> assertDoesNotThrow(() -> kobo.endTurn(new GameActionPayload(id, t)))),
                forEachBadToken("kobo announce", t -> assertDoesNotThrow(() -> kobo.announce(new GameActionPayload(id, t)))),
                forEachBadToken("kobo match", t -> assertDoesNotThrow(() -> kobo.match(new KoboMatchPayload(id, t, 0, 0, null, null, null)))),
                forEachBadToken("kobo giveTen", t -> assertDoesNotThrow(() -> kobo.giveTen(new KoboGiftPayload(id, t, 1)))))
                .flatMap(s -> s);
    }

    @TestFactory
    Stream<DynamicTest> threeMenMorrisWebSocket() {
        String id = StartedRooms.start(sessions, ThreeMenMorrisFactory.GAME_TYPE, 2).getId().uuid().toString();
        String room = sessions.createRoom(ThreeMenMorrisFactory.GAME_TYPE, "Alice").gameId();
        return Stream.of(
                forEachBadToken("tmm start", t -> assertDoesNotThrow(() -> threeMenMorris.start(new GameActionPayload(room, t)))),
                forEachBadToken("tmm place", t -> assertDoesNotThrow(() -> threeMenMorris.place(new ThreeMenMorrisPlacePayload(id, t, 0)))),
                forEachBadToken("tmm move", t -> assertDoesNotThrow(() -> threeMenMorris.move(new ThreeMenMorrisMovePayload(id, t, 0, 1)))))
                .flatMap(s -> s);
    }

    @TestFactory
    Stream<DynamicTest> presidentWebSocket() {
        String type = org.kevinkib.cardgames.president.domain.PresidentFactory.GAME_TYPE;
        String id = StartedRooms.start(sessions, type, 3).getId().uuid().toString();
        String room = sessions.createRoom(type, "Alice").gameId();
        List<org.kevinkib.cardgames.presentation.dto.CardDto> none = List.of();
        return Stream.of(
                forEachBadToken("president start", t -> assertDoesNotThrow(() -> president.start(new GameActionPayload(room, t)))),
                forEachBadToken("president play", t -> assertDoesNotThrow(() -> president.play(new org.kevinkib.cardgames.president.presentation.api.PresidentCardsPayload(id, t, none)))),
                forEachBadToken("president pass", t -> assertDoesNotThrow(() -> president.pass(new GameActionPayload(id, t)))),
                forEachBadToken("president giveBack", t -> assertDoesNotThrow(() -> president.giveBack(new org.kevinkib.cardgames.president.presentation.api.PresidentCardsPayload(id, t, none)))),
                forEachBadToken("president ready", t -> assertDoesNotThrow(() -> president.ready(new GameActionPayload(id, t)))))
                .flatMap(s -> s);
    }

    @TestFactory
    Stream<DynamicTest> lobbyWebSocket() {
        String id = sessions.createRoom(BullshitFactory.GAME_TYPE, "Alice").gameId();
        return Stream.of(
                forEachBadToken("lobby setOptions", t -> assertDoesNotThrow(() -> lobby.setOptions(new LobbyOptionsPayload(id, t, Map.of())))),
                forEachBadToken("lobby addBot", t -> assertDoesNotThrow(() -> lobby.addBot(new GameActionPayload(id, t)))),
                forEachBadToken("lobby removeBot", t -> assertDoesNotThrow(() -> lobby.removeBot(new LobbySeatPayload(id, t, 1)))),
                forEachBadToken("lobby leave", t -> assertDoesNotThrow(() -> lobby.leave(new GameActionPayload(id, t)))),
                forEachBadToken("lobby rename", t -> assertDoesNotThrow(() -> lobby.rename(new LobbyRenamePayload(id, t, "Bob")))),
                forEachBadToken("lobby setSeatOption", t -> assertDoesNotThrow(() -> lobby.setSeatOption(new LobbySeatOptionPayload(id, t, 0, "k", "v")))))
                .flatMap(s -> s);
    }

    @TestFactory
    Stream<DynamicTest> lifecycleWebSocket() {
        String id = StartedRooms.start(sessions, BullshitFactory.GAME_TYPE, 2).getId().uuid().toString();
        return Stream.of(
                forEachBadToken("presence", t -> assertDoesNotThrow(() ->
                        lifecycle.presence(new PresencePayload(id, t), SimpMessageHeaderAccessor.create()))),
                forEachBadToken("forfeit", t -> assertDoesNotThrow(() -> lifecycle.forfeit(new GameActionPayload(id, t)))))
                .flatMap(s -> s);
    }
}
