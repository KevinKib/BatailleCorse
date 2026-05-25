# Player Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace blind trust in client-supplied `playerIndex` with `SessionToken`-based identity — every action payload carries a token, the backend resolves it to a player seat, and tokens survive reconnection automatically.

**Architecture:** Tokens are already generated per-player in `SessionGame` at game creation. This plan wires them into the protocol: expose them through `SessionRepository`, surface them in the CREATE response, validate them in every action handler, and store them in the frontend for reuse across reconnects.

**Tech Stack:** Java 21 / Spring Boot / STOMP WebSocket (backend); Vue 3 / TypeScript / Pinia (frontend)

**Maven:** `"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd"`

---

## File Map

| File | Change |
|---|---|
| `backend/src/main/java/.../sessionmanagement/application/InvalidTokenException.java` | **Create** — new exception |
| `backend/src/main/java/.../sessionmanagement/application/port/SessionRepository.java` | **Modify** — add `loadSessionGame` |
| `backend/src/main/java/.../sessionmanagement/infrastructure/InMemorySessionRepository.java` | **Modify** — expose `loadSessionGame` (already exists privately) |
| `backend/src/main/java/.../sessionmanagement/application/SessionService.java` | **Modify** — add `findPlayerIdByToken` |
| `backend/src/main/java/.../websocket/presentation/v1/dto/event/CreateEventData.java` | **Modify** — add `tokens` field |
| `backend/src/main/java/.../websocket/presentation/v1/api/GameActionPayload.java` | **Modify** — replace `playerIndex` with `token` |
| `backend/src/main/java/.../websocket/presentation/v1/BatailleCorseWebSocketController.java` | **Modify** — CREATE includes tokens; SEND/SLAP/GRAB resolve token |
| `backend/src/test/java/.../sessionmanagement/infrastructure/InMemorySessionRepositoryTest.java` | **Modify** — add `loadSessionGame` test |
| `backend/src/test/java/.../sessionmanagement/application/SessionServiceTest.java` | **Create** — tests for `findPlayerIdByToken` |
| `backend/src/test/java/.../websocket/presentation/v1/BatailleCorseWebSocketControllerTest.java` | **Create** — token validation unit tests |
| `backend/src/test/java/.../websocket/presentation/v1/GameRestControllerIT.java` | **Modify** — assert tokens in CREATE response |
| `frontend/src/model/event/CreateEventData.ts` | **Modify** — add `tokens` field |
| `frontend/src/state/BatailleCorse.store.ts` | **Modify** — store tokens, `restoreTokens`, token-based payloads |
| `frontend/src/view/alpha/GameScreen.vue` | **Modify** — restore tokens from `localStorage` in `onMounted` |

---

### Task 1: InvalidTokenException + SessionRepository.loadSessionGame

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/InvalidTokenException.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/port/SessionRepository.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepository.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

Add a new `@Nested` class to `InMemorySessionRepositoryTest`:

```java
@Nested
class LoadSessionGameTest {

    @Test
    public void givenExistingSessionGame_whenLoadingSessionGame_thenReturnSessionGame() {
        var repository = new InMemorySessionRepository();
        var game = aBatailleCorse().withId(BatailleCorseId.generate()).withNbPlayers(2).buildAndInitialize();
        var players = createNumberOfPlayers(2);
        var sessionGame = SessionGame.create(game.getId(), players);

        repository.save(game, sessionGame);

        assertThat(repository.loadSessionGame(game.getId()), is(sessionGame));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test -Dtest=InMemorySessionRepositoryTest
```

Expected: compile error — `loadSessionGame` not found on `InMemorySessionRepository`.

- [ ] **Step 3: Create InvalidTokenException**

Create `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/InvalidTokenException.java`:

```java
package org.kevinkib.bataillecorse.sessionmanagement.application;

public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException() {
        super("Invalid token");
    }
}
```

- [ ] **Step 4: Add loadSessionGame to the SessionRepository port**

In `SessionRepository.java`, add after `loadSessionToken`:

```java
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;

SessionGame loadSessionGame(BatailleCorseId id);
```

Full file after change:

```java
package org.kevinkib.bataillecorse.sessionmanagement.application.port;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

public interface SessionRepository {

    void save(BatailleCorse batailleCorse, SessionGame sessionGame);

    BatailleCorse load(BatailleCorseId id);

    SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId);

    SessionGame loadSessionGame(BatailleCorseId id);
}
```

- [ ] **Step 5: Implement loadSessionGame in InMemorySessionRepository**

`InMemorySessionRepository` already has a private `loadSessionGame` method. Make it public and add `@Override`:

```java
package org.kevinkib.bataillecorse.sessionmanagement.infrastructure;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import java.util.ArrayList;
import java.util.List;

public class InMemorySessionRepository implements SessionRepository {

    private final List<BatailleCorse> games;
    private final List<SessionGame> sessionGames;

    public InMemorySessionRepository() {
        this.games = new ArrayList<>();
        this.sessionGames = new ArrayList<>();
    }

    @Override
    public void save(BatailleCorse batailleCorse, SessionGame sessionGame) {
        games.add(batailleCorse);
        sessionGames.add(sessionGame);
    }

    @Override
    public BatailleCorse load(BatailleCorseId id) {
        return games.stream()
                .filter(game -> game.getId().equals(id))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public SessionToken loadSessionToken(BatailleCorseId batailleCorseId, PlayerId playerId) {
        return loadSessionGame(batailleCorseId)
                .findTokenByPlayer(playerId)
                .orElseThrow(IllegalArgumentException::new);
    }

    @Override
    public SessionGame loadSessionGame(BatailleCorseId id) {
        return sessionGames.stream()
                .filter(session -> session.id().equals(id))
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test -Dtest=InMemorySessionRepositoryTest
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/InvalidTokenException.java
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/port/SessionRepository.java
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepository.java
git add backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/infrastructure/InMemorySessionRepositoryTest.java
git commit -m "feat: add InvalidTokenException and expose loadSessionGame through SessionRepository port"
```

---

### Task 2: SessionService.findPlayerIdByToken

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java`
- Create: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Create `SessionServiceTest.java`:

```java
package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class SessionServiceTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(new InMemorySessionRepository());
    }

    @Nested
    class FindPlayerIdByTokenTest {

        @Test
        void givenValidToken_whenFindPlayerIdByToken_thenReturnsPlayerId() {
            var game = service.createGame(2);
            SessionToken token = service.loadTokenByPlayerId(game.getId(), new PlayerId(0));

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), token);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        void givenInvalidToken_whenFindPlayerIdByToken_thenReturnsEmpty() {
            var game = service.createGame(2);

            Optional<PlayerId> result = service.findPlayerIdByToken(game.getId(), SessionToken.generate());

            assertThat(result, is(Optional.empty()));
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test -Dtest=SessionServiceTest
```

Expected: compile error — `findPlayerIdByToken` not found.

- [ ] **Step 3: Implement findPlayerIdByToken in SessionService**

Add the method and required imports to `SessionService.java`:

```java
package org.kevinkib.bataillecorse.sessionmanagement.application;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.port.SessionRepository;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;

import java.util.Optional;

public class SessionService {

    private final SessionRepository repository;

    public SessionService(SessionRepository repository) {
        this.repository = repository;
    }

    public BatailleCorse createGame(int nbPlayers) {
        BatailleCorseId id = BatailleCorseId.generate();
        BatailleCorse batailleCorse = new BatailleCorse(id, nbPlayers);
        repository.save(batailleCorse, org.kevinkib.bataillecorse.sessionmanagement.domain.SessionGame.create(id, batailleCorse.getPlayers()));
        return batailleCorse;
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

- [ ] **Step 4: Run tests to verify they pass**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test -Dtest=SessionServiceTest
```

Expected: 2 tests pass.

- [ ] **Step 5: Run full test suite to check no regressions**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java
git add backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java
git commit -m "feat: add SessionService.findPlayerIdByToken"
```

---

### Task 3: CreateEventData tokens + CREATE handler

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/CreateEventData.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java`
- Modify: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java`

- [ ] **Step 1: Write the failing test**

Add to `GameRestControllerIT.java`:

```java
@Test
void givenCreate_whenCreateGame_thenResponseIncludesTokensForBothPlayers() {
    Response createResponse = wsController.createGame();
    CreateEventData createData = (CreateEventData) createResponse.getEventData();

    assertThat(createData.tokens(), hasKey(0));
    assertThat(createData.tokens(), hasKey(1));
    assertThat(createData.tokens().get(0), notNullValue());
    assertThat(createData.tokens().get(1), notNullValue());
    assertThat(createData.tokens().get(0), not(equalTo(createData.tokens().get(1))));
}
```

Add the missing import at the top of `GameRestControllerIT.java`:
```java
import java.util.Map;
```

- [ ] **Step 2: Run test to verify it fails**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test -Dtest=GameRestControllerIT
```

Expected: compile error — `tokens()` not found on `CreateEventData`.

- [ ] **Step 3: Add tokens field to CreateEventData**

Replace `CreateEventData.java` entirely:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseIdDto;

import java.util.Map;

public record CreateEventData(BatailleCorseIdDto game, Map<Integer, String> tokens) implements EventData {
}
```

- [ ] **Step 4: Update createGame() in BatailleCorseWebSocketController**

Replace the `createGame()` method. Also add imports for `HashMap`, `PlayerId`, and `SessionToken`:

```java
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
import java.util.HashMap;
import java.util.Map;
```

Updated method:

```java
@MessageMapping("/create")
@SendTo("/topic/game")
public Response createGame() {
    BatailleCorse batailleCorse = sessionService.createGame(NB_PLAYERS);

    Map<Integer, String> tokens = new HashMap<>();
    for (int i = 0; i < NB_PLAYERS; i++) {
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

- [ ] **Step 5: Run tests to verify they pass**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test
```

Expected: all tests pass (existing `BatailleCorseControllerIT` is unaffected — it only checks `instanceof CreateEventData`).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/CreateEventData.java
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java
git add backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java
git commit -m "feat: include player tokens in CREATE response"
```

---

### Task 4: GameActionPayload token + controller SEND/SLAP/GRAB

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/GameActionPayload.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java`
- Create: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketControllerTest.java`

- [ ] **Step 1: Write the failing tests**

Create `BatailleCorseWebSocketControllerTest.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.PlayerId;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
import org.kevinkib.bataillecorse.sessionmanagement.infrastructure.InMemorySessionRepository;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.GameActionPayload;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BatailleCorseWebSocketControllerTest {

    private SessionService sessionService;
    private SimpMessagingTemplate template;
    private BatailleCorseWebSocketController controller;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(new InMemorySessionRepository());
        template = mock(SimpMessagingTemplate.class);
        controller = new BatailleCorseWebSocketController(sessionService, new GameMessagingService(template));
    }

    @Nested
    class SendTest {

        @Test
        void givenValidToken_whenSend_thenBroadcastsSuccessResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().toString();
            SessionToken token = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(0));

            controller.send(new GameActionPayload(gameId, token.uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenSend_thenBroadcastsErrorResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().toString();

            controller.send(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    argThat(r -> !((Response) r).isSuccess())
            );
        }
    }

    @Nested
    class SlapTest {

        @Test
        void givenValidToken_whenSlap_thenBroadcastsSuccessResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().toString();
            SessionToken token = sessionService.loadTokenByPlayerId(game.getId(), new PlayerId(0));

            controller.slap(new GameActionPayload(gameId, token.uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    argThat(r -> ((Response) r).isSuccess())
            );
        }

        @Test
        void givenInvalidToken_whenSlap_thenBroadcastsErrorResponse() {
            var game = sessionService.createGame(2);
            String gameId = game.getId().toString();

            controller.slap(new GameActionPayload(gameId, SessionToken.generate().uuid().toString()));

            verify(template).convertAndSend(
                    eq("/topic/game/" + gameId),
                    argThat(r -> !((Response) r).isSuccess())
            );
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test -Dtest=BatailleCorseWebSocketControllerTest
```

Expected: compile error — `GameActionPayload` constructor mismatch (still has `playerIndex`).

- [ ] **Step 3: Replace playerIndex with token in GameActionPayload**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.api;

public record GameActionPayload(String gameId, String token) {
}
```

- [ ] **Step 4: Update SEND, SLAP, and GRAB handlers in BatailleCorseWebSocketController**

Add imports at the top of the controller:

```java
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidTokenException;
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionToken;
```

Replace the `send()` method:

```java
@MessageMapping("/send")
public void send(GameActionPayload payload) {
    EventType eventType = EventType.SEND;

    BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
    BatailleCorse batailleCorse = sessionService.getGame(gameId);
    BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

    Response response;
    try {
        PlayerId playerId = sessionService
                .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                .orElseThrow(InvalidTokenException::new);
        Player player = batailleCorse.getPlayerByIndex(playerId.id());

        CardDto cardDto = new CardDto(player.getCardOnTop());
        batailleCorse.send(player);

        String message = "Player " + player.id() + " sent " + cardDto.getName() + ".";
        SendEventData eventData = new SendEventData(new PlayerIdDto(player));
        response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

    } catch (Exception e) {
        System.err.println(e.getMessage());
        response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
    }

    gameMessagingService.sendToGame(payload.gameId(), response);
}
```

Replace the `slap()` method:

```java
@MessageMapping("/slap")
public void slap(GameActionPayload payload) {
    EventType eventType = EventType.SLAP;

    BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
    BatailleCorse batailleCorse = sessionService.getGame(gameId);
    BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

    Response response;
    try {
        PlayerId playerId = sessionService
                .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                .orElseThrow(InvalidTokenException::new);
        Player player = batailleCorse.getPlayerByIndex(playerId.id());

        boolean successfulSlap = batailleCorse.slap(player);
        String message = successfulSlap
                ? "Player " + player.id() + " slapped and won."
                : "Player " + player.id() + " slapped, lost, and received a penality.";

        SlapEventData eventData = new SlapEventData(successfulSlap, new PlayerIdDto(player));
        response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

    } catch (Exception e) {
        System.err.println(e.getMessage());
        response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
    }

    gameMessagingService.sendToGame(payload.gameId(), response);
}
```

Replace the `grab()` method:

```java
@MessageMapping("/grab")
public void grab(GameActionPayload payload) {
    EventType eventType = EventType.GRAB;

    BatailleCorseId gameId = new BatailleCorseId(payload.gameId());
    BatailleCorse batailleCorse = sessionService.getGame(gameId);
    BatailleCorseDto batailleCorseDto = new BatailleCorseDto(batailleCorse);

    Response response;
    try {
        PlayerId playerId = sessionService
                .findPlayerIdByToken(gameId, new SessionToken(payload.token()))
                .orElseThrow(InvalidTokenException::new);
        Player player = batailleCorse.getPlayerByIndex(playerId.id());

        batailleCorse.grab(player);

        String message = "Player " + player.id() + " grabbed the pile. ";
        GrabEventData eventData = new GrabEventData(new PlayerIdDto(player));
        response = new SuccessResponse(eventType, eventData, message, batailleCorseDto);

    } catch (Exception e) {
        System.err.println(e.getMessage());
        response = new ErrorResponse(eventType, e.getMessage(), batailleCorseDto);
    }

    gameMessagingService.sendToGame(payload.gameId(), response);
}
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
"C:\Users\kevin\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd" -f backend/pom.xml test
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/GameActionPayload.java
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java
git add backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketControllerTest.java
git commit -m "feat: validate SessionToken in SEND/SLAP/GRAB handlers"
```

---

### Task 5: Frontend — token storage and token-based action payloads

**Files:**
- Modify: `frontend/src/model/event/CreateEventData.ts`
- Modify: `frontend/src/state/BatailleCorse.store.ts`

- [ ] **Step 1: Add tokens to the CreateEventData TypeScript model**

Replace `frontend/src/model/event/CreateEventData.ts`:

```typescript
import EventData from "./EventData";
import GameId from "../GameId";

export default interface CreateEventData extends EventData {
  game: GameId;
  tokens: Record<number, string>;
}
```

- [ ] **Step 2: Update the store**

In `BatailleCorse.store.ts`, make the following changes:

**Add `playerTokens` ref** after the `gameId` ref:

```typescript
const playerTokens = ref<Record<number, string>>({});
```

**Add `restoreTokens` function** after `hydrate`:

```typescript
function restoreTokens(tokens: Record<number, string>) {
  playerTokens.value = tokens;
}
```

**Update the CREATE block** in `processEvent` to store and persist tokens:

```typescript
if (response.eventType === 'CREATE') {
  const createData = response.eventData as CreateEventData;
  gameId.value = createData.game.id;
  playerTokens.value = createData.tokens;
  localStorage.setItem(`tokens:${gameId.value}`, JSON.stringify(createData.tokens));
  webSocketService.subscribeToGame(gameId.value);
}
```

**Replace `send()`** — drop `playerIndex` from the payload, use token:

```typescript
function send(playerIndex: number) {
  const topCard = state.value?.pile.cards.at(0);
  lastSend.value = { playerIndex, seq: ++sendSeq, topCard };
  webSocketService.publish(`/app/send`, JSON.stringify({
    gameId: gameId.value,
    token: playerTokens.value[playerIndex],
  }));
}
```

**Replace `slap()`**:

```typescript
function slap(playerIndex: number) {
  lastSlap.value = { seq: ++slapSeq };
  webSocketService.publish(`/app/slap`, JSON.stringify({
    gameId: gameId.value,
    token: playerTokens.value[playerIndex],
  }));
}
```

**Replace `grab()`**:

```typescript
function grab(playerIndex: number) {
  webSocketService.publish(`/app/grab`, JSON.stringify({
    gameId: gameId.value,
    token: playerTokens.value[playerIndex],
  }));
}
```

**Add `restoreTokens` to the return block** (alongside `hydrate`):

```typescript
return {
  state,
  gameId,
  lastSend,
  lastGrab,
  lastSlap,
  lastSuccessfulSlap,
  lastErroneousSlap,
  create,
  hydrate,
  restoreTokens,
  send,
  slap,
  grab,
  onResponse,
  notifyAnimationComplete,
  cancelAutoGrab,
}
```

- [ ] **Step 3: Verify TypeScript compiles**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: build succeeds with no type errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/model/event/CreateEventData.ts
git add frontend/src/state/BatailleCorse.store.ts
git commit -m "feat: store and use SessionToken in frontend action payloads"
```

---

### Task 6: Frontend — restore tokens from localStorage on mount

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Update onMounted in GameScreen.vue**

In the `<script setup>` section, replace the `onMounted` block:

```typescript
onMounted(async () => {
  const gameId = route.params.id as string;

  const stored = localStorage.getItem(`tokens:${gameId}`);
  if (!stored) {
    router.replace('/');
    return;
  }

  const response = await fetch(`/api/game/${gameId}`);
  if (!response.ok) {
    router.replace('/');
    return;
  }

  const gameState = await response.json() as BatailleCorse;
  batailleCorseStore.hydrate(gameId, gameState);
  batailleCorseStore.restoreTokens(JSON.parse(stored));
  webSocketService.subscribeToGame(gameId);
});
```

Note: the `localStorage` check happens first — no point fetching game state if we can't authenticate.

- [ ] **Step 2: Verify TypeScript compiles**

```bash
cd frontend && npm run build 2>&1 | tail -20
```

Expected: build succeeds.

- [ ] **Step 3: Manual smoke test**

Start the app (`./run-dev.sh`), create a game, play a card, then hard-refresh the page. Verify:
- The game screen reloads correctly
- Playing a card still works (token was restored from `localStorage`)
- Checking browser DevTools Network tab: SEND payload contains `token` instead of `playerIndex`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: restore player tokens from localStorage on GameScreen mount"
```
