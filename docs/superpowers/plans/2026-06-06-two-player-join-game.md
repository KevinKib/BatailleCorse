# Two-Player "Join Game" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the game playable by two humans on two separate browser tabs via a shareable game link, while keeping the existing solo-vs-AI mode fully intact.

**Architecture:** A `GameMode` (`SOLO` | `MULTIPLAYER`) chosen at creation encodes intent via *seat claims*. Solo claims both seats `{0,1}` at creation (so join is auto-rejected and the frontend AI puppets player 1). Multiplayer claims only seat `{0}`, leaving seat 1 open for a second human who claims it through a new REST `POST /api/game/{id}/join`. The join returns the joiner's token privately in the HTTP body, then broadcasts a `JOIN` event to `/topic/game/{id}` so the waiting creator's board reveals. The frontend AI and the 1500ms auto-grab timer are gated to solo (or to the rightful grabber) so multiplayer tabs don't fight each other. Each tab renders the board from its own `myPlayerIndex` so every player sees their own hand at the bottom.

**Tech Stack:** Backend — Java 17, Spring Boot, STOMP/WebSocket, JUnit 5 + Hamcrest. Frontend — Vue 3 `<script setup>` + TypeScript, Pinia, vue-router, PrimeVue, `@stomp/stompjs`.

**Testing note:** The frontend has **no unit-test runner** (only Cypress E2E). Per YAGNI we do **not** add Vitest. Frontend tasks are verified with `npx vue-tsc --noEmit` plus the manual two-tab flow in Task 11. Backend follows the repo's testing rules: no Mockito on domain classes, Builders/Fixtures, `givenX_whenY_thenZ` naming, Hamcrest `assertThat`.

---

## File Structure

**Backend — create:**
- `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/GameMode.java`
- `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SeatUnavailableException.java`
- `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/JoinResult.java`
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/JoinEventData.java`
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/CreateGamePayload.java`
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/JoinResponseDto.java`

**Backend — modify:**
- `sessionmanagement/domain/SessionGame.java` — add `claimedSeats` + `claim`/`isClaimed`
- `sessionmanagement/application/SessionService.java` — `createGame(int, GameMode)`, `joinGame`, `isSeatClaimed`
- `websocket/presentation/v1/dto/event/EventType.java` — add `JOIN`
- `websocket/presentation/v1/BatailleCorseWebSocketController.java` — mode-aware `createGame(payload)`
- `websocket/presentation/v1/GameRestController.java` — `POST /game/{id}/join`

**Backend — test callers to update (signature change):**
- `BatailleCorseControllerIT.java:25`, `GameRestControllerIT.java:46,68` — `createGame()` → `createGame(null)`

**Frontend — create:**
- `frontend/src/model/event/JoinEventData.ts`

**Frontend — modify:**
- `frontend/src/model/Response.ts` — add `"JOIN"`
- `frontend/src/application/GameEvent.ts` — add `join` variant
- `frontend/src/state/BatailleCorse.store.ts` — `mode`/`myPlayerIndex`/`waiting`, `create(mode)`, `join`, gating, JOIN handling, `restoreSession`
- `frontend/src/main.ts` — add `/join/:id?` route
- `frontend/src/view/alpha/LobbyView.vue` — enable Join button
- `frontend/src/view/alpha/StartGame.vue` — Computer/Human toggle + join mode + Game ID field
- `frontend/src/view/alpha/GameScreen.vue` — perspective rendering + waiting overlay + share link

---

## Task 1: GameMode enum + SessionGame seat-claim (domain, TDD)

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/GameMode.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGame.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGameTest.java`

- [ ] **Step 1: Create the GameMode enum**

`GameMode.java`:
```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

public enum GameMode {
    SOLO,
    MULTIPLAYER;
}
```

- [ ] **Step 2: Write the failing tests**

Add a new `@Nested` class inside `SessionGameTest` (keep existing imports; add `import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;` is already present):

```java
    @Nested
    class ClaimTest {

        @Test
        public void givenNewSessionGame_whenCreated_thenNoSeatsAreClaimed() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(false));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
        }

        @Test
        public void givenSeat_whenClaimed_thenIsClaimed() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            sessionGame.claim(new PlayerId(0));

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(true));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
        }
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q -Dtest=SessionGameTest test`
Expected: FAIL — `claim`/`isClaimed` not defined (compile error).

- [ ] **Step 4: Add seat-claim to SessionGame**

Replace the whole `SessionGame.java` with:

```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record SessionGame(BatailleCorseId id, Map<PlayerId, SessionToken> tokensByPlayer, Set<PlayerId> claimedSeats) {

    public static SessionGame create(BatailleCorseId id, List<Player> players) {
        Map<PlayerId, SessionToken> tokensByPlayer = new HashMap<>();

        for (Player player : players) {
            tokensByPlayer.put(player.id(), SessionToken.generate());
        }

        return new SessionGame(id, tokensByPlayer, new HashSet<>());
    }

    public void claim(PlayerId playerId) {
        claimedSeats.add(playerId);
    }

    public boolean isClaimed(PlayerId playerId) {
        return claimedSeats.contains(playerId);
    }

    public Optional<SessionToken> findTokenByPlayer(PlayerId playerId) {
        return Optional.ofNullable(tokensByPlayer.get(playerId));
    }

    public Optional<PlayerId> findPlayerByToken(SessionToken token) {
        return tokensByPlayer.entrySet().stream()
                .filter(e -> e.getValue().equals(token))
                .map(Map.Entry::getKey)
                .findFirst();
    }

}
```

Note: `claim` mutates the in-place `HashSet`. Because `InMemorySessionRepository` stores the same `SessionGame` reference, a later `joinGame` that claims seat 1 is visible without a repository update call.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=SessionGameTest test`
Expected: PASS (all `SessionGameTest` tests green).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/GameMode.java backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGame.java backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGameTest.java
git commit -m "feat: add GameMode and seat-claim tracking to SessionGame"
```

---

## Task 2: SessionService createGame(mode) + joinGame (TDD)

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SeatUnavailableException.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/JoinResult.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java`

- [ ] **Step 1: Create the exception and result types**

`SeatUnavailableException.java`:
```java
package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.PlayerId;

public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(PlayerId playerId) {
        super("Seat " + playerId.id() + " is already claimed.");
    }
}
```

`JoinResult.java`:
```java
package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

public record JoinResult(PlayerId playerId, SessionToken token) {
}
```

- [ ] **Step 2: Write the failing tests**

Add two `@Nested` classes to `SessionServiceTest` (add imports: `import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;`, `import static org.junit.jupiter.api.Assertions.assertThrows;`, `import static org.hamcrest.Matchers.notNullValue;`):

```java
    @Nested
    class CreateGameTest {

        @Test
        void givenSoloMode_whenCreateGame_thenBothSeatsClaimed() {
            var game = service.createGame(2, GameMode.SOLO);

            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
        }

        @Test
        void givenMultiplayerMode_whenCreateGame_thenOnlySeatZeroClaimed() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);

            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(0)), is(true));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(false));
        }
    }

    @Nested
    class JoinGameTest {

        @Test
        void givenMultiplayerGame_whenJoin_thenSeatOneClaimedAndTokenReturned() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);

            JoinResult result = service.joinGame(game.getId());

            assertThat(result.playerId(), is(new PlayerId(1)));
            assertThat(result.token(), is(notNullValue()));
            assertThat(service.isSeatClaimed(game.getId(), new PlayerId(1)), is(true));
        }

        @Test
        void givenSoloGame_whenJoin_thenThrowsSeatUnavailable() {
            var game = service.createGame(2, GameMode.SOLO);

            assertThrows(SeatUnavailableException.class, () -> service.joinGame(game.getId()));
        }

        @Test
        void givenAlreadyJoinedGame_whenJoinAgain_thenThrowsSeatUnavailable() {
            var game = service.createGame(2, GameMode.MULTIPLAYER);
            service.joinGame(game.getId());

            assertThrows(SeatUnavailableException.class, () -> service.joinGame(game.getId()));
        }
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q -Dtest=SessionServiceTest test`
Expected: FAIL — `createGame(int, GameMode)`, `joinGame`, `isSeatClaimed` not defined.

- [ ] **Step 4: Implement in SessionService**

Replace `SessionService.java` with:

```java
package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import java.util.Optional;

public class SessionService {

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
    }

    public BatailleCorse createGame(int nbPlayers) {
        return createGame(nbPlayers, GameMode.SOLO);
    }

    public BatailleCorse createGame(int nbPlayers, GameMode mode) {
        BatailleCorseId id = BatailleCorseId.generate();
        BatailleCorse batailleCorse = new BatailleCorse(id, nbPlayers);

        SessionGame sessionGame = SessionGame.create(id, batailleCorse.getPlayers());

        if (mode == GameMode.SOLO) {
            for (Player player : batailleCorse.getPlayers()) {
                sessionGame.claim(player.id());
            }
        } else {
            sessionGame.claim(new PlayerId(0));
        }

        repository.save(batailleCorse, sessionGame);

        return batailleCorse;
    }

    public JoinResult joinGame(BatailleCorseId gameId) throws SeatUnavailableException {
        SessionGame sessionGame = repository.loadSessionGame(gameId);
        PlayerId seat = new PlayerId(1);

        if (sessionGame.isClaimed(seat)) {
            throw new SeatUnavailableException(seat);
        }

        sessionGame.claim(seat);
        SessionToken token = sessionGame.findTokenByPlayer(seat)
                .orElseThrow(() -> new IllegalStateException("Seat 1 has no token"));

        return new JoinResult(seat, token);
    }

    public boolean isSeatClaimed(BatailleCorseId gameId, PlayerId playerId) {
        return repository.loadSessionGame(gameId).isClaimed(playerId);
    }

    public BatailleCorse getGame(BatailleCorseId id) throws InvalidGameIdException {
        try {
            return repository.load(id);
        } catch (IllegalArgumentException e) {
            throw new InvalidGameIdException(id);
        }
    }

    public SessionToken loadTokenByPlayerId(BatailleCorseId batailleCorseId, PlayerId playerId) {
        return repository.loadSessionToken(batailleCorseId, playerId);
    }

    public Optional<PlayerId> findPlayerIdByToken(BatailleCorseId gameId, SessionToken token) {
        return repository.loadSessionGame(gameId).findPlayerByToken(token);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=SessionServiceTest test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SeatUnavailableException.java backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/JoinResult.java backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java
git commit -m "feat: add mode-aware createGame and joinGame to SessionService"
```

---

## Task 3: JOIN EventType + JoinEventData DTO

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/EventType.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/JoinEventData.java`

- [ ] **Step 1: Add JOIN to EventType**

Edit `EventType.java` enum body:
```java
    CREATE,
    SEND,
    SLAP,
    GRAB,
    JOIN;
```

- [ ] **Step 2: Create JoinEventData**

`JoinEventData.java` (mirrors `SendEventData`):
```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;

public record JoinEventData(PlayerIdDto player) implements EventData {
}
```

- [ ] **Step 3: Compile**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/EventType.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/JoinEventData.java
git commit -m "feat: add JOIN event type and JoinEventData dto"
```

---

## Task 4: Mode-aware create over WebSocket + CreateGamePayload (TDD)

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/CreateGamePayload.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java`
- Modify (callers): `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseControllerIT.java:25`, `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java:46,68`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java`

- [ ] **Step 1: Create the payload record**

`CreateGamePayload.java`:
```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.api;

import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;

public record CreateGamePayload(GameMode mode) {
}
```

- [ ] **Step 2: Write the failing test**

Add to `GameRestControllerIT` (add `import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;` and `import org.kevinkib.bataillecorse.websocket.presentation.v1.api.CreateGamePayload;`):

```java
    @Test
    void givenMultiplayerCreate_whenCreateGame_thenResponseIncludesOnlyTokenForSeatZero() {
        Response createResponse = wsController.createGame(new CreateGamePayload(GameMode.MULTIPLAYER));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();

        assertThat(createData.tokens(), hasKey(0));
        assertThat(createData.tokens(), not(hasKey(1)));
    }
```

- [ ] **Step 3: Run to verify failure**

Run: `cd backend && ./mvnw -q -Dtest=GameRestControllerIT test`
Expected: FAIL — `createGame(CreateGamePayload)` not defined (compile error).

- [ ] **Step 4: Make createGame mode-aware**

In `BatailleCorseWebSocketController.java`, add imports:
```java
import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.CreateGamePayload;
import org.springframework.messaging.handler.annotation.Payload;
```

Replace the `createGame()` method (lines 37-53) with:
```java
    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) CreateGamePayload payload) {
        GameMode mode = (payload != null && payload.mode() != null) ? payload.mode() : GameMode.SOLO;

        BatailleCorse batailleCorse = sessionService.createGame(NB_PLAYERS, mode);

        int seatsToReturn = (mode == GameMode.SOLO) ? NB_PLAYERS : 1;
        Map<Integer, String> tokens = new HashMap<>();
        for (int i = 0; i < seatsToReturn; i++) {
            SessionToken token = sessionService.loadTokenByPlayerId(batailleCorse.getId(), new PlayerId(i));
            tokens.put(i, token.uuid().toString());
        }

        return new SuccessResponse(
                EventType.CREATE,
                new CreateEventData(new BatailleCorseIdDto(batailleCorse.getId()), tokens),
                GAME_CREATED_MESSAGE,
                new BatailleCorseDto(batailleCorse));
    }
```

- [ ] **Step 5: Update the three direct callers**

In `BatailleCorseControllerIT.java:25`: `controller.createGame()` → `controller.createGame(null)`.
In `GameRestControllerIT.java:46`: `wsController.createGame()` → `wsController.createGame(null)`.
In `GameRestControllerIT.java:68`: `wsController.createGame()` → `wsController.createGame(null)`.

(`null` payload ⇒ SOLO ⇒ both tokens, preserving the existing assertions.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=GameRestControllerIT,BatailleCorseControllerIT test`
Expected: PASS — existing solo assertions still green; new multiplayer-token test green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/CreateGamePayload.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseControllerIT.java backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java
git commit -m "feat: accept GameMode in websocket create and gate returned tokens"
```

---

## Task 5: REST POST /api/game/{id}/join + JOIN broadcast (TDD)

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/JoinResponseDto.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java`

- [ ] **Step 1: Create the response DTO**

`JoinResponseDto.java`:
```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

public record JoinResponseDto(int playerId, String token) {
}
```

- [ ] **Step 2: Write the failing tests**

Add to `GameRestControllerIT` (add imports: `import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.JoinResponseDto;`, `import org.springframework.http.HttpMethod;`):

```java
    @Test
    void givenMultiplayerGame_whenJoin_thenReturnsJoinerTokenForSeatOne() {
        Response createResponse = wsController.createGame(
                new org.kevinkib.bataillecorse.websocket.presentation.v1.api.CreateGamePayload(
                        org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode.MULTIPLAYER));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<JoinResponseDto> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/join",
                null, JoinResponseDto.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody(), notNullValue());
        assertThat(response.getBody().playerId(), is(1));
        assertThat(response.getBody().token(), notNullValue());
    }

    @Test
    void givenSoloGame_whenJoin_thenReturns409() {
        Response createResponse = wsController.createGame(null);
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        try {
            restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/game/" + gameId + "/join",
                    null, JoinResponseDto.class);
            throw new AssertionError("Expected 409 but request succeeded");
        } catch (HttpClientErrorException.Conflict e) {
            assertThat(e.getStatusCode(), is(HttpStatus.CONFLICT));
        }
    }

    @Test
    void givenUnknownGame_whenJoin_thenReturns404() {
        try {
            restTemplate.postForEntity(
                    "http://localhost:" + port + "/api/game/unknown-id/join",
                    null, JoinResponseDto.class);
            throw new AssertionError("Expected 404 but request succeeded");
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode(), is(HttpStatus.NOT_FOUND));
        }
    }
```

- [ ] **Step 3: Run to verify failure**

Run: `cd backend && ./mvnw -q -Dtest=GameRestControllerIT test`
Expected: FAIL — `/join` endpoint returns 405/404, assertions fail.

- [ ] **Step 4: Implement the endpoint with broadcast**

Replace `GameRestController.java` with:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.bataillecorse.sessionmanagement.application.JoinResult;
import org.kevinkib.bataillecorse.sessionmanagement.application.SeatUnavailableException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.SuccessResponse;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.JoinResponseDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.EventType;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.JoinEventData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameRestController {

    private final SessionService sessionService;
    private final GameMessagingService gameMessagingService;

    public GameRestController(SessionService sessionService, GameMessagingService gameMessagingService) {
        this.sessionService = sessionService;
        this.gameMessagingService = gameMessagingService;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BatailleCorseDto> getGame(@PathVariable String id) {
        try {
            BatailleCorse game = sessionService.getGame(new BatailleCorseId(id));
            return ResponseEntity.ok(new BatailleCorseDto(game));
        } catch (InvalidGameIdException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id) {
        BatailleCorseId gameId = new BatailleCorseId(id);
        try {
            BatailleCorse game = sessionService.getGame(gameId);
            JoinResult result = sessionService.joinGame(gameId);

            Player joiner = game.getPlayerByIndex(result.playerId().id());
            Response broadcast = new SuccessResponse(
                    EventType.JOIN,
                    new JoinEventData(new PlayerIdDto(joiner)),
                    "Player " + result.playerId().id() + " joined.",
                    new BatailleCorseDto(game));
            gameMessagingService.sendToGame(id, broadcast);

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token().uuid().toString()));
        } catch (InvalidGameIdException e) {
            return ResponseEntity.notFound().build();
        } catch (SeatUnavailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q -Dtest=GameRestControllerIT test`
Expected: PASS (join happy path + 409 solo + 404 unknown, plus the existing tests).

- [ ] **Step 6: Run the full backend suite**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/JoinResponseDto.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java
git commit -m "feat: add REST join endpoint with JOIN broadcast"
```

---

## Task 6: Frontend event models

**Files:**
- Create: `frontend/src/model/event/JoinEventData.ts`
- Modify: `frontend/src/model/Response.ts`
- Modify: `frontend/src/application/GameEvent.ts`

- [ ] **Step 1: Create JoinEventData model**

`JoinEventData.ts` (mirrors `SendEventData.ts`):
```typescript
import PlayerId from "../PlayerId";
import EventData from "./EventData";

export default interface JoinEventData extends EventData {
  player: PlayerId,
}
```

- [ ] **Step 2: Add JOIN to the Response union**

In `Response.ts`, change the `eventType` line to:
```typescript
  eventType: "CREATE" | "SEND" | "SLAP" | "GRAB" | "JOIN",
```

- [ ] **Step 3: Add a join variant to GameEvent**

In `frontend/src/application/GameEvent.ts`, append to the union (after the `erroneous-slap` line):
```typescript
  | { type: 'join'; playerIndex: number };
```
(Remove the trailing `;` from the previous last line and re-add it on the new last line so the union stays valid — i.e. the `erroneous-slap` line ends with no semicolon and the `join` line ends the union with `;`.)

- [ ] **Step 4: Type-check**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: no new errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/model/event/JoinEventData.ts frontend/src/model/Response.ts frontend/src/application/GameEvent.ts
git commit -m "feat: add JOIN to frontend event models"
```

---

## Task 7: Store — mode/myPlayerIndex/waiting, create(mode), join, gating, JOIN handling

**Files:**
- Modify: `frontend/src/state/BatailleCorse.store.ts`

- [ ] **Step 1: Add mode/perspective/waiting refs**

After the `gameId`/`playerTokens` refs (around line 51-52) add:
```typescript
  const mode = ref<'solo' | 'multiplayer'>('solo');
  const myPlayerIndex = ref<number>(0);
  const waiting = ref<boolean>(false);
```

- [ ] **Step 2: Make `create` mode-aware**

Replace the existing `create` (lines 59-63) with:
```typescript
  function create(gameMode: 'solo' | 'multiplayer', playerName?: string) {
    pendingCreate = true;
    mode.value = gameMode;
    myPlayerIndex.value = 0;
    waiting.value = gameMode === 'multiplayer';
    if (gameMode === 'solo') {
      player1Ai = new AI(1, DIFFICULTY[settingsStore.difficulty].reactionTime);
    }
    const serverMode = gameMode === 'solo' ? 'SOLO' : 'MULTIPLAYER';
    webSocketService.publish('/app/create', JSON.stringify({ mode: serverMode }));
  }
```
(`playerName` is accepted to keep the call-site shape but is intentionally not sent — names stay browser-local per the spec.)

- [ ] **Step 3: Add the `join` action**

Add after `create` (and after `hydrate`/`restoreTokens` is fine too — place it before `send`):
```typescript
  async function join(id: string) {
    const response = await fetch(`/api/game/${id}/join`, { method: 'POST' });
    if (!response.ok) {
      throw new Error(`Join failed: ${response.status}`);
    }
    const body = await response.json() as { playerId: number; token: string };

    mode.value = 'multiplayer';
    myPlayerIndex.value = body.playerId;
    waiting.value = false;

    const tokens = { [body.playerId]: body.token };
    playerTokens.value = tokens;
    localStorage.setItem(`tokens:${id}`, JSON.stringify(tokens));

    gameId.value = id;
    webSocketService.subscribeToGame(id);

    const stateResponse = await fetch(`/api/game/${id}`);
    if (stateResponse.ok) {
      state.value = await stateResponse.json() as BatailleCorse;
    }
  }
```

- [ ] **Step 4: Add `restoreSession` (perspective + mode from token keys)**

This makes GameScreen robust to navigation/reload: solo stores both tokens `{0,1}`; multiplayer stores exactly one. Add after `restoreTokens`:
```typescript
  function restoreSession(tokens: Record<number, string>) {
    playerTokens.value = tokens;
    const seats = Object.keys(tokens).map(Number);
    if (seats.length >= 2) {
      mode.value = 'solo';
      myPlayerIndex.value = 0;
    } else {
      mode.value = 'multiplayer';
      myPlayerIndex.value = seats[0] ?? 0;
      waiting.value = false;
    }
  }
```

- [ ] **Step 5: Gate the AI to solo**

Replace the unconditional `player1Ai.play();` (line 163) with:
```typescript
    if (mode.value === 'solo') {
      player1Ai.play();
    }
```

- [ ] **Step 6: Gate auto-grab so multiplayer tabs don't double-grab**

Replace the auto-grab arming block (lines 155-160) with:
```typescript
    const rightfulGrabber = Number(state.value.pile.playerThatAddedLastHonourCard?.id);
    const mayAutoGrab = mode.value === 'solo' || rightfulGrabber === myPlayerIndex.value;
    if (autoGrabEnabled && state.value.pile.grabbable && mayAutoGrab) {
      autoGrabTimeoutId = setTimeout(() => {
        autoGrabTimeoutId = null;
        autoGrab();
      }, 1500);
    }
```

- [ ] **Step 7: Handle the JOIN event**

In `processEvent`, after the `CREATE` block (after line 124) add:
```typescript
    if (response.eventType === 'JOIN') {
      waiting.value = false;
    }
```

- [ ] **Step 8: Export new refs/actions**

In the returned object (lines 192-209) add `mode`, `myPlayerIndex`, `waiting`, `join`, `restoreSession`:
```typescript
  return {
    state,
    gameId,
    mode,
    myPlayerIndex,
    waiting,
    lastSend,
    lastGrab,
    lastSlap,
    lastSuccessfulSlap,
    lastErroneousSlap,
    create,
    join,
    hydrate,
    restoreTokens,
    restoreSession,
    send,
    slap,
    grab,
    onResponse,
    notifyAnimationComplete,
    cancelAutoGrab,
  }
```

- [ ] **Step 9: Type-check**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: errors only at the not-yet-updated `StartGame.vue` call site (`create()` arity). That is fixed in Task 9. If you run this task in isolation, the store file itself should be internally consistent.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/state/BatailleCorse.store.ts
git commit -m "feat: add multiplayer mode, join, and perspective to game store"
```

---

## Task 8: Router /join route + enable Lobby Join button

**Files:**
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/view/alpha/LobbyView.vue`

- [ ] **Step 1: Add the join route**

In `main.ts`, change the `routes` array to:
```typescript
const routes = [
  { path: '/', component: LobbyView },
  { path: '/create', component: StartGame },
  { path: '/join/:id?', component: StartGame },
  { path: '/room/:id', component: GameScreen },
  { path: '/debug', component: Debug },
]
```

- [ ] **Step 2: Enable the Join Game button**

In `LobbyView.vue`, replace the Join Game `Button` (lines 35-43) — remove `disabled`, add the click handler:
```vue
      <Button
        class="menu-button"
        label="Join Game"
        icon="pi pi-users"
        severity="secondary"
        size="large"
        rounded
        @click="router.push('/join')"
      />
```

- [ ] **Step 3: Type-check**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: no new errors from these two files.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/main.ts frontend/src/view/alpha/LobbyView.vue
git commit -m "feat: add /join route and enable lobby Join Game button"
```

---

## Task 9: StartGame — Computer/Human toggle + join mode + Game ID field

**Files:**
- Modify: `frontend/src/view/alpha/StartGame.vue`

`StartGame.vue` serves three URLs: `/create` (New Game) and `/join/:id?` (Join). Derive `screenMode` from the route path.

- [ ] **Step 1: Add script state for screen mode, opponent toggle, and game id**

In the `<script setup>` block, after `const settingsStore = useSettingsStore();` and the `storeToRefs` line, add:
```typescript
import { useRoute } from 'vue-router';

const route = useRoute();
const screenMode = computed<'create' | 'join'>(() =>
  route.path.startsWith('/join') ? 'join' : 'create');

// New Game toggle: true = solo vs computer, false = 2-player vs human
const vsComputer = ref(true);

// Join: the game id to join (pre-filled from the share link)
const joinGameId = ref<string>((route.params.id as string) ?? '');
```
Add `ref` to the existing `vue` import if not present (it already imports `ref`).

- [ ] **Step 2: Replace `startGame` with mode-aware submit**

Replace `startGame` (lines 183-191) with:
```typescript
function startGame() {
  if (screenMode.value === 'join') {
    joinGame();
    return;
  }
  const gameMode = vsComputer.value ? 'solo' : 'multiplayer';
  batailleCorseStore.create(gameMode, playerName.value || undefined);
  const unwatch = watch(() => batailleCorseStore.gameId, (id) => {
    if (id) {
      unwatch();
      router.push(`/room/${id}`);
    }
  });
}

async function joinGame() {
  const id = joinGameId.value.trim();
  if (!id) return;
  try {
    await batailleCorseStore.join(id);
    router.push(`/room/${id}`);
  } catch (e) {
    joinError.value = 'Could not join this game. Check the ID and try again.';
  }
}
```
Add near the other refs:
```typescript
const joinError = ref<string>('');
```

- [ ] **Step 3: Add the Computer/Human toggle (create mode) to the template**

Immediately after the `panel-divider` div (line 24) and before the name `field-group`, add:
```vue
      <div v-if="screenMode === 'create'" class="field-group">
        <label class="field-label">Opponent</label>
        <div class="opponent-toggle">
          <button
            class="opponent-option"
            :class="{ 'opponent-option--active': vsComputer }"
            @click="vsComputer = true"
          >Computer</button>
          <button
            class="opponent-option"
            :class="{ 'opponent-option--active': !vsComputer }"
            @click="vsComputer = false"
          >Human</button>
        </div>
      </div>
```

- [ ] **Step 4: Add the Game ID field (join mode)**

After the toggle block from Step 3, add:
```vue
      <div v-if="screenMode === 'join'" class="field-group">
        <label class="field-label" for="gameId">Game ID</label>
        <InputText
          id="gameId"
          v-model="joinGameId"
          placeholder="Paste game ID or link..."
          class="name-input"
        />
        <p v-if="joinError" class="conflict-warning">{{ joinError }}</p>
      </div>
```

- [ ] **Step 5: Gate the difficulty slider to solo create**

Wrap the difficulty `field-group` (lines 72-89) with a `v-if`:
```vue
      <div class="field-group" v-if="screenMode === 'create' && vsComputer">
```
(That is, add `v-if="screenMode === 'create' && vsComputer"` to the existing difficulty `field-group` div; do not add a second wrapper.)

- [ ] **Step 6: Make the action button label dynamic**

Replace the start `Button` `label` (line 93) so it reads:
```vue
        :label="screenMode === 'join' ? 'Join Game' : (vsComputer ? 'Deal Cards' : 'Create Game')"
```
(Replace the static `label="Deal Cards"` attribute with the bound `:label` above.)

- [ ] **Step 7: Add minimal toggle styles**

In the `<style scoped>` block, add:
```css
.opponent-toggle {
  display: flex;
  gap: 8px;
}

.opponent-option {
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  border: 1.5px solid rgba(255, 255, 255, 0.18);
  background: rgba(0, 0, 0, 0.35);
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.opponent-option--active {
  background: rgba(232, 201, 109, 0.14);
  border-color: rgba(232, 201, 109, 0.55);
  color: #f5c842;
}
```

- [ ] **Step 8: Type-check**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: no errors.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/view/alpha/StartGame.vue
git commit -m "feat: add opponent toggle and join mode to StartGame"
```

---

## Task 10: GameScreen — perspective rendering + waiting overlay + share link

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

The board currently hardcodes player 0 at the bottom and player 1 (Computer) at the top. Parameterize by `myPlayerIndex`. Solo (`myPlayerIndex===0`) renders identically to today.

- [ ] **Step 1: Pull perspective + waiting from the store**

In `<script setup>`, extend the `storeToRefs` destructure (line 129) to include `mode`, `myPlayerIndex`, `waiting`:
```typescript
const { state: batailleCorse, mode, myPlayerIndex, waiting,
        lastSend, lastGrab, lastSlap, lastSuccessfulSlap, lastErroneousSlap } = storeToRefs(batailleCorseStore);
```

Add computeds (after `difficultyLabel`, around line 227):
```typescript
const opponentIndex = computed(() => 1 - myPlayerIndex.value);
const isSolo = computed(() => mode.value === 'solo');
const isWaiting = computed(() => waiting.value);
const opponentLabel = computed(() =>
  isSolo.value ? `Computer (${difficultyLabel.value})` : 'Opponent');
const shareLink = computed(() =>
  `${window.location.origin}/join/${route.params.id}`);
```

- [ ] **Step 2: Render the opponent (top) from `opponentIndex`**

In the top section, replace the player tag (line 7) and the opponent card count (line 17):
```vue
        <h1 class="player_tag">{{ opponentLabel }}</h1>
```
```vue
            <CardCounter :count="batailleCorse?.players.at(opponentIndex)?.nbCards"/>
```

- [ ] **Step 3: Render me (bottom) from `myPlayerIndex`**

In the bottom section, replace the bottom card count (line 60):
```vue
            <CardCounter :count="batailleCorse?.players.at(myPlayerIndex)?.nbCards"/>
```
And the action buttons (lines 64-67) act for `myPlayerIndex`:
```vue
          <Button class="action_button" icon="pi pi-arrow-up" severity="success" label="Send" rounded
            @click="send(myPlayerIndex)" :disabled="isButtonDisabled(myPlayerIndex, 'send')"/>
          <Button class="action_button" icon="pi pi-hammer" severity="warn" label="Slap" rounded
            @click="slap(myPlayerIndex)" :disabled="isButtonDisabled(myPlayerIndex, 'slap')"/>
```

- [ ] **Step 4: Fix animation perspective**

In each animation watcher, the bottom deck (`pile`) is "me" and the top deck (`opponentCard`) is the opponent. Replace the `=== 0` comparisons:

`lastSend` watcher (line 156):
```typescript
  const sourceEl = event.playerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
```
`lastGrab` watcher (line 170):
```typescript
  const destEl = winnerPlayerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
```
`lastSuccessfulSlap` watcher (line 190):
```typescript
  const destEl = winnerPlayerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
```
`lastErroneousSlap` watcher (line 203):
```typescript
  const srcEl = event.playerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
```

- [ ] **Step 5: Hotkeys act for `myPlayerIndex`**

Replace the `useHotkeys` call (lines 229-234):
```typescript
useHotkeys(
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'send')) send(myPlayerIndex.value); },
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'slap')) slap(myPlayerIndex.value); },
  () => [settingsStore.sendKey],
  () => [settingsStore.slapKey],
);
```

- [ ] **Step 6: Restore perspective on mount**

In `onMounted` (line 253), replace `batailleCorseStore.restoreTokens(JSON.parse(stored));` with:
```typescript
  batailleCorseStore.restoreSession(JSON.parse(stored));
```

- [ ] **Step 7: Add the waiting overlay**

Just before the closing `</div>` of `.gamescreen` (after the bottom section, before line 73's closing `</div>` for `.gamescreen`), add the overlay markup inside `.gamescreen`:
```vue
    <div v-if="isWaiting" class="waiting-overlay">
      <div class="waiting-card">
        <h2 class="waiting-title">Waiting for opponent…</h2>
        <p class="waiting-sub">Share this link to invite a player</p>
        <div class="share-row">
          <InputText :value="shareLink" readonly class="share-input" />
          <Button label="Copy" icon="pi pi-copy" rounded @click="copyShareLink" />
        </div>
        <p v-if="copied" class="waiting-copied">Copied!</p>
      </div>
    </div>
```
Add `InputText` to the PrimeVue import (line 115):
```typescript
import { Button, InputText } from 'primevue';
```
Add the copy state + handler in script (near other refs):
```typescript
import { ref } from 'vue';
const copied = ref(false);
async function copyShareLink() {
  await navigator.clipboard.writeText(shareLink.value);
  copied.value = true;
  setTimeout(() => { copied.value = false; }, 1500);
}
```
(`ref` is part of the existing `vue` import list — add it there rather than a second import if `vue` is already imported. The file currently imports `{ computed, nextTick, onBeforeUnmount, onMounted, useTemplateRef, watch }` from `'vue'`; add `ref` to that list.)

- [ ] **Step 8: Add waiting-overlay styles**

In `<style scoped>`, add:
```css
.waiting-overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.78);
  backdrop-filter: blur(3px);
}

.waiting-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  padding: 36px 40px;
  max-width: 460px;
}

.waiting-title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.6rem;
  font-weight: 700;
  color: #f5c842;
  margin: 0;
}

.waiting-sub {
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

.share-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.share-input {
  flex: 1;
}

.waiting-copied {
  font-size: 0.72rem;
  color: #4ade80;
  margin: 0;
}
```

- [ ] **Step 9: Type-check**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: no errors.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: render board from player perspective and add waiting room"
```

---

## Task 11: End-to-end two-tab manual verification

**Files:** none (manual).

- [ ] **Step 1: Start backend and frontend**

Backend: `cd backend && ./mvnw spring-boot:run`
Frontend: `cd frontend && npm run dev`
(Or use the project's docker/dev workflow if that is the established way.)

- [ ] **Step 2: Solo regression**

Tab 1 → New Game → **Computer** toggle (default) → adjust difficulty → "Deal Cards". Confirm: board starts immediately, AI plays player 1, send/slap/grab and animations behave exactly as before. This is the critical regression check.

- [ ] **Step 3: Multiplayer create → waiting room**

Tab 1 → New Game → **Human** toggle → difficulty slider hidden → "Create Game". Confirm: lands on `/room/{id}` with a "Waiting for opponent…" overlay and a copyable share link `…/join/{id}`. No AI moves.

- [ ] **Step 4: Join from a second tab**

Tab 2 → open the share link (or Lobby → Join Game → paste the game ID) → setup screen with the Game ID pre-filled (when via link) → enter name/keys → "Join Game". Confirm: Tab 1's overlay disappears (JOIN broadcast), both tabs are on the board.

- [ ] **Step 5: Perspective + sync**

Confirm: each tab shows **its own** hand at the bottom and the opponent ("Opponent") on top. Send/slap/grab from either tab stays in sync across both. Auto-grab does not double-fire (only the rightful grabber's tab arms it).

- [ ] **Step 6: Full game cannot be joined**

Open a third tab → Join Game → paste the same game ID → confirm it is rejected (409 surfaced as the "Could not join this game" message).

- [ ] **Step 7: Final full backend test run**

Run: `cd backend && ./mvnw -q test`
Expected: BUILD SUCCESS.

---

## Self-Review

**Spec coverage:**
- Shareable link join → Task 8 (`/join/:id?`), Task 10 (share link in waiting room). ✓
- Setup screen before board for joiner → Task 9 (join mode reuses StartGame). ✓
- Lobby New Game + Join Game; New Game has Computer/Human toggle → Tasks 8, 9. ✓
- Multiplayer create = wait, no AI → Task 7 (`waiting`, AI gated), Task 10 (overlay). ✓
- Token handoff via REST + JOIN broadcast → Task 5. ✓
- Each player's hand at bottom → Task 10 perspective. ✓
- Solo kept intact → one-arg `createGame(int)` retained (Task 2); `null` payload ⇒ SOLO (Task 4); AI/auto-grab gated by `mode==='solo'` (Task 7); solo perspective `myPlayerIndex===0` unchanged (Task 10). ✓
- Out of scope (persistence across restart, disconnect/leave, opponent name) → not implemented. ✓

**Signature-break check:** `SessionGame` canonical constructor gained `claimedSeats`; the only direct constructor call is in `SessionGame.create`, updated in Task 1. `createGame(int)` retained, so all `service.createGame(2)` test callers compile. The controller `createGame()` → `createGame(payload)` change updates its three direct test callers in Task 4.

**Type consistency:** `JoinResult(PlayerId, SessionToken)`; `JoinResponseDto(int playerId, String token)`; frontend `join` reads `{ playerId, token }` and stores `{ [playerId]: token }`; `restoreSession` derives `myPlayerIndex`/`mode` from token-key count (solo = 2 keys, multiplayer = 1). `PlayerIdDto` takes a `Player` (Task 5 passes `game.getPlayerByIndex(...)`). `EventType.JOIN` mirrored in `Response.ts` union.

**Testing reconciliation:** No Vitest added (repo has no FE unit runner); frontend verified via `vue-tsc --noEmit` + manual two-tab flow. Backend follows repo testing rules.
