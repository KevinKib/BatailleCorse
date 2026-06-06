# Player Returns — 2-Player Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the prematurely-vanishing "Waiting for opponent" overlay, and show each player's name on both sides of a 2-player game (the share/copy button already exists and only needs the waiting fix to be visible).

**Architecture:** Consolidate per-seat session data (token + claimed + name) into a new `SessionPlayer` object inside `SessionGame`. Expose seat occupancy + names through a new `GET /api/game/{id}/session` endpoint and an enriched `JOIN` broadcast. The frontend resolves the waiting state and opponent name from this session view instead of the buggy unconditional `waiting = false`.

**Tech Stack:** Backend — Java 21 records, Spring Boot, JUnit 5 + Hamcrest (no Mockito on domain). Frontend — Vue 3 `<script setup>` + TypeScript, Pinia, Vitest, Cypress.

**Spec:** `docs/superpowers/specs/2026-06-06-player-returns-2p-design.md`

**Conventions (from project memory):**
- No Mockito on domain classes; use Builders/Fixtures; test names `givenX_whenY_thenZ`.
- Run backend tests with the IntelliJ-bundled Maven (no `mvnw` wrapper exists): `mvn -q test` from `backend/`.
- Frontend unit tests: `npm run test` (Vitest) from `frontend/`. The real type/build gate is `npm run build` (vite), not bare `vue-tsc`.
- `git add` every newly created file immediately after writing it.

---

## File Structure

**Backend — create:**
- `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayer.java` — per-seat token + claimed + name.
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/SessionViewDto.java` — `{ players: [{ id, name, joined }] }`.
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/SeatDto.java` — `{ id, name, joined }`.
- `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayerTest.java`

**Backend — modify:**
- `SessionGame.java` — fold the two maps into `Map<PlayerId, SessionPlayer>`; `claim(PlayerId, String)`; add `seats()`.
- `SessionService.java` — name params + default-name helper + `getSeats`.
- `CreateGamePayload.java` — add `name`.
- `JoinEventData.java` (backend dto/event) — add `players` list.
- `BatailleCorseWebSocketController.java` — pass create name.
- `GameRestController.java` — join accepts name; enriched JOIN broadcast; new session endpoint.
- Tests: `SessionGameTest.java`, `SessionServiceTest.java`, `GameRestControllerIT.java`.

**Frontend — create:**
- `frontend/src/model/SessionSeat.ts` — `{ id, name, joined }` shared type.

**Frontend — modify:**
- `GameEvent.ts` — name-change variants.
- `model/event/JoinEventData.ts` — add `players`.
- `BatailleCorse.store.ts` — `myName` / `opponentName` refs + event wiring.
- `GameSession.ts` — send names; stop forcing waiting; `applySessionView`; JOIN reads players.
- `view/alpha/GameScreen.vue` — fetch session view; render names.
- `view/alpha/StartGame.vue` — pass name to `join`.
- Tests: `GameSession.test.ts`; new Cypress spec.

---

## Task 1: `SessionPlayer` domain object

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayer.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class SessionPlayerTest {

    @Test
    public void givenNewSessionPlayer_thenUnclaimedWithNoName() {
        SessionPlayer player = new SessionPlayer(new PlayerId(0), SessionToken.generate());

        assertThat(player.isClaimed(), is(false));
        assertThat(player.name(), is(nullValue()));
        assertThat(player.token(), is(notNullValue()));
    }

    @Test
    public void givenSessionPlayer_whenClaimed_thenClaimedWithName() {
        SessionPlayer player = new SessionPlayer(new PlayerId(0), SessionToken.generate());

        player.claim("Alice");

        assertThat(player.isClaimed(), is(true));
        assertThat(player.name(), is("Alice"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `backend/`): `mvn -q -Dtest=SessionPlayerTest test`
Expected: compilation failure — `SessionPlayer` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.kevinkib.bataillecorse.core.domain.PlayerId;

public class SessionPlayer {

    private final PlayerId id;
    private final SessionToken token;
    private boolean claimed;
    private String name;

    public SessionPlayer(PlayerId id, SessionToken token) {
        this.id = id;
        this.token = token;
        this.claimed = false;
        this.name = null;
    }

    public void claim(String name) {
        this.claimed = true;
        this.name = name;
    }

    public PlayerId id() {
        return id;
    }

    public SessionToken token() {
        return token;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public String name() {
        return name;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=SessionPlayerTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayer.java backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionPlayerTest.java
git commit -m "feat: add SessionPlayer holding per-seat token, claim, name"
```

---

## Task 2: Refactor `SessionGame` onto `SessionPlayer`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGame.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGameTest.java`

- [ ] **Step 1: Update the tests to the new surface**

Replace the body of `SessionGameTest.java` with (claim now takes a name; token reads go through `findTokenByPlayer`; add a name + seats assertion):

```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.kevinkib.bataillecorse.core.domain.PlayerFixtures.createNumberOfPlayers;

class SessionGameTest {

    @Nested
    class CreateTest {

        @Test
        public void givenPlayers_whenCreating_thenEachSeatHasAToken() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            assertThat(sessionGame.findTokenByPlayer(new PlayerId(0)).isPresent(), is(true));
            assertThat(sessionGame.findTokenByPlayer(new PlayerId(1)).isPresent(), is(true));
        }

        @Test
        public void givenPlayers_whenCreating_thenEachSeatHasADistinctToken() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            var token0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();
            var token1 = sessionGame.findTokenByPlayer(new PlayerId(1)).orElseThrow();
            assertThat(token0, is(not(equalTo(token1))));
        }

        @Test
        public void givenPlayers_whenCreating_thenSeatsAreOrderedById() {
            var players = createNumberOfPlayers(2);

            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            var seats = sessionGame.seats();
            assertThat(seats, hasSize(2));
            assertThat(seats.get(0).id(), is(new PlayerId(0)));
            assertThat(seats.get(1).id(), is(new PlayerId(1)));
        }
    }

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
        public void givenSeat_whenClaimedWithName_thenIsClaimedWithThatName() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            sessionGame.claim(new PlayerId(0), "Alice");

            assertThat(sessionGame.isClaimed(new PlayerId(0)), is(true));
            assertThat(sessionGame.isClaimed(new PlayerId(1)), is(false));
            assertThat(sessionGame.seats().get(0).name(), is("Alice"));
        }
    }

    @Nested
    class FindPlayerByTokenTest {

        @Test
        public void givenSessionGame_withToken_whenLookingUp_thenReturnPlayerId() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);
            var tokenForPlayer0 = sessionGame.findTokenByPlayer(new PlayerId(0)).orElseThrow();

            Optional<PlayerId> result = sessionGame.findPlayerByToken(tokenForPlayer0);

            assertThat(result, is(Optional.of(new PlayerId(0))));
        }

        @Test
        public void givenSessionGame_withUnknownToken_whenLookingUp_thenReturnEmpty() {
            var players = createNumberOfPlayers(2);
            var sessionGame = SessionGame.create(BatailleCorseId.generate(), players);

            Optional<PlayerId> result = sessionGame.findPlayerByToken(SessionToken.generate());

            assertThat(result, is(Optional.empty()));
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SessionGameTest test`
Expected: compilation failure — `seats()` missing and `claim(PlayerId, String)` not defined.

- [ ] **Step 3: Rewrite `SessionGame`**

```java
package org.kevinkib.bataillecorse.sessionmanagement.domain;

import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.core.domain.Player;
import org.kevinkib.bataillecorse.core.domain.PlayerId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record SessionGame(BatailleCorseId id, Map<PlayerId, SessionPlayer> players) {

    public static SessionGame create(BatailleCorseId id, List<Player> players) {
        Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
        for (Player player : players) {
            seats.put(player.id(), new SessionPlayer(player.id(), SessionToken.generate()));
        }
        return new SessionGame(id, seats);
    }

    public void claim(PlayerId playerId, String name) {
        SessionPlayer seat = players.get(playerId);
        if (seat == null) {
            throw new IllegalArgumentException("Unknown seat " + playerId.id());
        }
        seat.claim(name);
    }

    public boolean isClaimed(PlayerId playerId) {
        SessionPlayer seat = players.get(playerId);
        return seat != null && seat.isClaimed();
    }

    public Optional<SessionToken> findTokenByPlayer(PlayerId playerId) {
        return Optional.ofNullable(players.get(playerId)).map(SessionPlayer::token);
    }

    public Optional<PlayerId> findPlayerByToken(SessionToken token) {
        return players.values().stream()
                .filter(seat -> seat.token().equals(token))
                .map(SessionPlayer::id)
                .findFirst();
    }

    /** Seats ordered by player id, for presentation. */
    public List<SessionPlayer> seats() {
        return players.values().stream()
                .sorted(Comparator.comparingInt(seat -> seat.id().id()))
                .toList();
    }
}
```

Note: `PlayerId.id()` returns the seat's `int`/`Integer` (used elsewhere as `result.playerId().id()`). If it is `Integer`, `comparingInt(seat -> seat.id().id())` still compiles via unboxing.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=SessionGameTest test`
Expected: PASS. (SessionService still references the old `claim(PlayerId)` — that compile break is fixed in Task 3; this step may fail to compile the module. If so, proceed to Task 3 and run the combined build there.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGame.java backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/domain/SessionGameTest.java
git commit -m "refactor: fold SessionGame seat data into SessionPlayer map"
```

---

## Task 3: `SessionService` — names + defaults + `getSeats`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java`

- [ ] **Step 1: Write the failing tests**

Append these tests inside `SessionServiceTest` (match the existing class's repository/fixture setup — reuse whatever `createGame`/repository wiring the existing tests use):

```java
    @Test
    public void givenMultiplayerCreateWithName_whenCreating_thenSeatZeroClaimedWithName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, "Alice");

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(0).isClaimed(), is(true));
        assertThat(seats.get(0).name(), is("Alice"));
        assertThat(seats.get(1).isClaimed(), is(false));
    }

    @Test
    public void givenMultiplayerCreateWithBlankName_whenCreating_thenSeatZeroGetsDefaultName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, "  ");

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(0).name(), is("Player 1"));
    }

    @Test
    public void givenMultiplayerGame_whenJoiningWithName_thenSeatOneClaimedWithName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, "Alice");

        service.joinGame(game.getId(), "Bob");

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(1).isClaimed(), is(true));
        assertThat(seats.get(1).name(), is("Bob"));
    }

    @Test
    public void givenMultiplayerGame_whenJoiningWithBlankName_thenSeatOneGetsDefaultName() {
        BatailleCorse game = service.createGame(2, GameMode.MULTIPLAYER, null);

        service.joinGame(game.getId(), null);

        List<SessionPlayer> seats = service.getSeats(game.getId());
        assertThat(seats.get(1).name(), is("Player 2"));
    }
```

Add imports as needed: `org.kevinkib.bataillecorse.sessionmanagement.domain.SessionPlayer`, `org.kevinkib.bataillecorse.core.domain.BatailleCorse`, `java.util.List`, and the Hamcrest matchers if not already present. Name the service/repository variables to match the existing test setup (rename `service` references if the existing field is named differently).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=SessionServiceTest test`
Expected: compilation failure — `createGame(int, GameMode, String)`, `joinGame(BatailleCorseId, String)`, and `getSeats` do not exist.

- [ ] **Step 3: Update `SessionService`**

Replace the create/join section and add the seats accessor + default helper:

```java
    public BatailleCorse createGame(int nbPlayers) {
        return createGame(nbPlayers, GameMode.SOLO, null);
    }

    public BatailleCorse createGame(int nbPlayers, GameMode mode) {
        return createGame(nbPlayers, mode, null);
    }

    public BatailleCorse createGame(int nbPlayers, GameMode mode, String creatorName) {
        BatailleCorseId id = BatailleCorseId.generate();
        BatailleCorse batailleCorse = new BatailleCorse(id, nbPlayers);

        SessionGame sessionGame = SessionGame.create(id, batailleCorse.getPlayers());

        if (mode == GameMode.SOLO) {
            for (Player player : batailleCorse.getPlayers()) {
                sessionGame.claim(player.id(), defaultNameFor(player.id()));
            }
        } else {
            PlayerId creatorSeat = new PlayerId(0);
            sessionGame.claim(creatorSeat, resolveName(creatorSeat, creatorName));
        }

        repository.save(batailleCorse, sessionGame);

        return batailleCorse;
    }

    public JoinResult joinGame(BatailleCorseId gameId) {
        return joinGame(gameId, null);
    }

    public JoinResult joinGame(BatailleCorseId gameId, String name) {
        SessionGame sessionGame = repository.loadSessionGame(gameId);

        if (sessionGame.isClaimed(JOINER_SEAT)) {
            throw new SeatUnavailableException(JOINER_SEAT);
        }

        sessionGame.claim(JOINER_SEAT, resolveName(JOINER_SEAT, name));
        SessionToken token = sessionGame.findTokenByPlayer(JOINER_SEAT)
                .orElseThrow(() -> new IllegalStateException("Seat " + JOINER_SEAT.id() + " has no token"));

        return new JoinResult(JOINER_SEAT, token);
    }

    public List<SessionPlayer> getSeats(BatailleCorseId gameId) {
        return repository.loadSessionGame(gameId).seats();
    }

    private String resolveName(PlayerId seat, String provided) {
        if (provided == null || provided.isBlank()) {
            return defaultNameFor(seat);
        }
        return provided.trim();
    }

    private String defaultNameFor(PlayerId seat) {
        return "Player " + (seat.id() + 1);
    }
```

Add imports: `java.util.List` and `org.kevinkib.bataillecorse.sessionmanagement.domain.SessionPlayer`. Keep the existing `createGame(int)` / `createGame(int, GameMode)` overloads (shown above) so current callers and tests still compile.

- [ ] **Step 4: Run the backend module build + targeted tests**

Run: `mvn -q -Dtest=SessionServiceTest,SessionGameTest,SessionPlayerTest test`
Expected: PASS (this also confirms Task 2's module now compiles).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionService.java backend/src/test/java/org/kevinkib/bataillecorse/sessionmanagement/application/SessionServiceTest.java
git commit -m "feat: store player names with seat-based defaults in SessionService"
```

---

## Task 4: `SeatDto` / `SessionViewDto` + `GET /api/game/{id}/session`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/SeatDto.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/SessionViewDto.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java`

- [ ] **Step 1: Write the failing IT tests**

Append to `GameRestControllerIT`:

```java
    @Test
    void givenMultiplayerGame_whenGetSession_thenSeatZeroJoinedWithNameAndSeatOnePending() {
        Response createResponse = wsController.createGame(
                new org.kevinkib.bataillecorse.websocket.presentation.v1.api.CreateGamePayload(
                        org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode.MULTIPLAYER, "Alice"));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<SessionViewDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/session",
                SessionViewDto.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        SessionViewDto body = response.getBody();
        assertThat(body, notNullValue());
        assertThat(body.players(), hasSize(2));
        assertThat(body.players().get(0).joined(), is(true));
        assertThat(body.players().get(0).name(), is("Alice"));
        assertThat(body.players().get(1).joined(), is(false));
    }

    @Test
    void givenUnknownId_whenGetSession_thenReturns404() {
        try {
            restTemplate.getForEntity(
                    "http://localhost:" + port + "/api/game/unknown-id/session",
                    String.class);
            throw new AssertionError("Expected 404 but request succeeded");
        } catch (HttpClientErrorException.NotFound e) {
            assertThat(e.getStatusCode(), is(HttpStatus.NOT_FOUND));
        }
    }
```

Add import: `org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SessionViewDto`. (This test also references the new 2-arg `CreateGamePayload` constructor added in Task 5 — if running this task in isolation before Task 5, temporarily use the existing 1-arg `CreateGamePayload(GameMode.MULTIPLAYER)`; otherwise implement Task 5 first. Recommended order: do Task 5 before running these.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=GameRestControllerIT test`
Expected: compilation failure — `SessionViewDto` and `/session` route do not exist.

- [ ] **Step 3: Create the DTOs**

`SeatDto.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

public record SeatDto(int id, String name, boolean joined) {
}
```

`SessionViewDto.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto;

import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionPlayer;

import java.util.List;

public record SessionViewDto(List<SeatDto> players) {

    public static SessionViewDto from(List<SessionPlayer> seats) {
        List<SeatDto> dtos = seats.stream()
                .map(seat -> new SeatDto(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();
        return new SessionViewDto(dtos);
    }
}
```

- [ ] **Step 4: Add the endpoint to `GameRestController`**

Add imports near the others:

```java
import org.kevinkib.bataillecorse.sessionmanagement.domain.SessionPlayer;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SeatDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SessionViewDto;
import java.util.List;
```

Add the method:

```java
    @GetMapping("/game/{id}/session")
    public ResponseEntity<SessionViewDto> getSession(@PathVariable String id) {
        try {
            BatailleCorseId gameId = new BatailleCorseId(id);
            List<SessionPlayer> seats = sessionService.getSeats(gameId);
            return ResponseEntity.ok(SessionViewDto.from(seats));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -q -Dtest=GameRestControllerIT test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/SeatDto.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/SessionViewDto.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java
git commit -m "feat: add GET /api/game/{id}/session returning seat occupancy and names"
```

---

## Task 5: Create accepts a name; JOIN broadcast carries names

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/CreateGamePayload.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/JoinEventData.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java`
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java`

- [ ] **Step 1: Write the failing test**

Append to `GameRestControllerIT` — assert the JOIN broadcast (captured by joining and reading the session afterward, plus the join still returns seat 1). The broadcast shape is asserted via the WebSocket IT in the suite; here assert the join persists the joiner name end-to-end:

```java
    @Test
    void givenMultiplayerGame_whenJoinWithName_thenSessionShowsBothNames() {
        Response createResponse = wsController.createGame(
                new org.kevinkib.bataillecorse.websocket.presentation.v1.api.CreateGamePayload(
                        org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode.MULTIPLAYER, "Alice"));
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        org.springframework.http.HttpEntity<String> request =
                new org.springframework.http.HttpEntity<>("{\"name\":\"Bob\"}", headers);

        restTemplate.postForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/join",
                request, JoinResponseDto.class);

        SessionViewDto session = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/game/" + gameId + "/session",
                SessionViewDto.class).getBody();
        assertThat(session.players().get(0).name(), is("Alice"));
        assertThat(session.players().get(1).name(), is("Bob"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=GameRestControllerIT test`
Expected: failure — `CreateGamePayload` has no 2-arg constructor, and join ignores the body name (seat 1 name would be the default "Player 2", not "Bob").

- [ ] **Step 3: Add `name` to `CreateGamePayload`**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.api;

import org.kevinkib.bataillecorse.sessionmanagement.domain.GameMode;

public record CreateGamePayload(GameMode mode, String name) {
}
```

- [ ] **Step 4: Update `BatailleCorseWebSocketController.createGame`**

Replace the create body's service call:

```java
    @MessageMapping("/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) CreateGamePayload payload) {
        GameMode mode = (payload != null && payload.mode() != null) ? payload.mode() : GameMode.SOLO;
        String name = (payload != null) ? payload.name() : null;

        BatailleCorse batailleCorse = sessionService.createGame(NB_PLAYERS, mode, name);

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
                BatailleCorseDto.from(batailleCorse));
    }
```

- [ ] **Step 5: Add `players` to `JoinEventData` (backend)**

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event;

import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.PlayerIdDto;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SeatDto;

import java.util.List;

public record JoinEventData(PlayerIdDto player, List<SeatDto> players) implements EventData {
}
```

- [ ] **Step 6: Update `GameRestController.joinGame` to read the body name and enrich the broadcast**

Add imports if missing: `org.springframework.web.bind.annotation.RequestBody`, `org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SessionViewDto` (already added in Task 4), `org.kevinkib.bataillecorse.websocket.presentation.v1.dto.SeatDto`.

Add a small request record (top-level file or nested). Create
`backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/JoinGamePayload.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1.api;

public record JoinGamePayload(String name) {
}
```

Replace `joinGame`:

```java
    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(
            @PathVariable String id,
            @RequestBody(required = false) org.kevinkib.bataillecorse.websocket.presentation.v1.api.JoinGamePayload payload) {
        try {
            BatailleCorseId gameId = new BatailleCorseId(id);
            BatailleCorse game = sessionService.getGame(gameId);
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinGame(gameId, name);

            Player joiner = game.getPlayerByIndex(result.playerId().id());
            SessionViewDto sessionView = SessionViewDto.from(sessionService.getSeats(gameId));
            Response broadcast = new SuccessResponse(
                    EventType.JOIN,
                    new JoinEventData(PlayerIdDto.from(joiner), sessionView.players()),
                    "Player " + result.playerId().id() + " joined.",
                    BatailleCorseDto.from(game));
            gameMessagingService.sendToGame(id, broadcast);

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token().uuid().toString()));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SeatUnavailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
```

Add the `git add` for the new `JoinGamePayload.java` in Step 8.

- [ ] **Step 7: Fix other callers of the changed constructors**

Search the test sources for `new CreateGamePayload(` and `new JoinEventData(` and update them:
- `new CreateGamePayload(GameMode.MULTIPLAYER)` → `new CreateGamePayload(GameMode.MULTIPLAYER, null)`.
- Any `new JoinEventData(playerIdDto)` → `new JoinEventData(playerIdDto, java.util.List.of())`.

Run a search to find them:

Run: `grep -rn "new CreateGamePayload(\|new JoinEventData(" backend/src`
Update each hit (likely in `GameRestControllerIT`, `BatailleCorseWebSocketControllerIT`, and `BatailleCorseWebSocketControllerTest`). For the WebSocket IT that asserts the JOIN payload, update its assertion to expect a non-empty `players` list with the joiner name if it inspects `JoinEventData`.

- [ ] **Step 8: Run the full backend test suite**

Run (from `backend/`): `mvn -q test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/CreateGamePayload.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/api/JoinGamePayload.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/dto/event/JoinEventData.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/BatailleCorseWebSocketController.java backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java backend/src/test
git commit -m "feat: carry player names through create payload and JOIN broadcast"
```

---

## Task 6: Frontend session-seat type + store name state + events

**Files:**
- Create: `frontend/src/model/SessionSeat.ts`
- Modify: `frontend/src/application/GameEvent.ts`
- Modify: `frontend/src/model/event/JoinEventData.ts`
- Modify: `frontend/src/state/BatailleCorse.store.ts`

- [ ] **Step 1: Create the shared seat type**

`frontend/src/model/SessionSeat.ts`:

```typescript
export default interface SessionSeat {
  id: number;
  name: string | null;
  joined: boolean;
}
```

- [ ] **Step 2: Extend `GameEvent` with name variants**

Append to the union in `frontend/src/application/GameEvent.ts`:

```typescript
  | { type: 'my-name-change'; name: string | null }
  | { type: 'opponent-name-change'; name: string | null };
```

(Add after the `waiting-change` line; replace the closing `;` so the union remains valid.)

- [ ] **Step 3: Extend frontend `JoinEventData`**

```typescript
import PlayerId from "../PlayerId";
import EventData from "./EventData";
import type SessionSeat from "../SessionSeat";

export default interface JoinEventData extends EventData {
  player: PlayerId,
  players: SessionSeat[],
}
```

- [ ] **Step 4: Add name refs + event handling to the store**

In `frontend/src/state/BatailleCorse.store.ts`, add refs after `waiting`:

```typescript
  const myName = ref<string | null>(null);
  const opponentName = ref<string | null>(null);
```

Add cases in the `onEvent` switch after `waiting-change`:

```typescript
          case 'my-name-change':       myName.value = event.name; break;
          case 'opponent-name-change': opponentName.value = event.name; break;
```

Add `myName` and `opponentName` to both the returned object's exposed refs (next to `waiting`).

- [ ] **Step 5: Verify type-check / build**

Run (from `frontend/`): `npm run build`
Expected: build succeeds (no type errors). If `node_modules` is missing in this worktree, run `npm ci` first.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/model/SessionSeat.ts frontend/src/application/GameEvent.ts frontend/src/model/event/JoinEventData.ts frontend/src/state/BatailleCorse.store.ts
git commit -m "feat: add session-seat type and name state to frontend store"
```

---

## Task 7: `GameSession` — send names, server-aware waiting, `applySessionView`

**Files:**
- Modify: `frontend/src/application/GameSession.ts`
- Modify: `frontend/src/state/BatailleCorse.store.ts` (expose `applySessionView`, update `join` signature)
- Test: `frontend/src/application/GameSession.test.ts`

- [ ] **Step 1: Write failing tests**

Add to `GameSession.test.ts`:

```typescript
  describe('names and session view', () => {
    it('create("multiplayer", name) sends name in payload', () => {
      const { session, published } = makeSession();
      session.create('multiplayer', 'Alice');
      expect(published).toContainEqual({
        dest: '/app/create',
        body: JSON.stringify({ mode: 'MULTIPLAYER', name: 'Alice' }),
      });
    });

    it('emits my-name-change on create with a name', () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      expect(events).toContainEqual({ type: 'my-name-change', name: 'Alice' });
    });

    it('applySessionView keeps waiting while opponent seat is not joined', () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      session.applySessionView([
        { id: 0, name: 'Alice', joined: true },
        { id: 1, name: null, joined: false },
      ]);
      // last waiting-change should be true
      const waitingEvents = events.filter(e => e.type === 'waiting-change');
      expect(waitingEvents.at(-1)).toEqual({ type: 'waiting-change', waiting: true });
      expect(events).toContainEqual({ type: 'opponent-name-change', name: null });
    });

    it('applySessionView clears waiting and sets opponent name once joined', () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      session.applySessionView([
        { id: 0, name: 'Alice', joined: true },
        { id: 1, name: 'Bob', joined: true },
      ]);
      const waitingEvents = events.filter(e => e.type === 'waiting-change');
      expect(waitingEvents.at(-1)).toEqual({ type: 'waiting-change', waiting: false });
      expect(events).toContainEqual({ type: 'opponent-name-change', name: 'Bob' });
    });
  });
```

- [ ] **Step 2: Run tests to verify they fail**

Run (from `frontend/`): `npm run test -- GameSession`
Expected: FAIL — `applySessionView` undefined; create omits `name`.

- [ ] **Step 3: Update `GameSession.create` to send + announce the name**

Replace the create body's payload section and add a name emit:

```typescript
  create(gameMode: 'solo' | 'multiplayer', playerName?: string): void {
    this.pendingCreate = true;
    this.mode = gameMode;
    this.myPlayerIndex = 0;
    this.waiting = gameMode === 'multiplayer';
    this.myName = playerName ?? null;
    if (gameMode === 'solo') {
      this.ai = this.aiFactory();
    }
    this.emitPerspective();
    this.callbacks.onEvent({ type: 'my-name-change', name: this.myName });
    const serverMode = gameMode === 'solo' ? 'SOLO' : 'MULTIPLAYER';
    const payload: { mode: string; name?: string } = { mode: serverMode };
    if (playerName) payload.name = playerName;
    this.webSocket.publish('/app/create', JSON.stringify(payload));
  }
```

Add the field near the other multiplayer fields:

```typescript
  private myName: string | null = null;
```

Note: the existing test `create("multiplayer") announces waiting and sends MULTIPLAYER` expects body `JSON.stringify({ mode: 'MULTIPLAYER' })` with no name — preserved here because `name` is only added when `playerName` is truthy.

- [ ] **Step 4: Update `join` to send the name and apply the session view**

```typescript
  async join(id: string, playerName?: string): Promise<void> {
    const response = await fetch(`/api/game/${id}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: playerName ?? null }),
    });
    if (!response.ok) {
      throw new Error(`Join failed: ${response.status}`);
    }
    const body = await response.json() as { playerId: number; token: string };

    this.mode = 'multiplayer';
    this.myPlayerIndex = body.playerId;
    this.waiting = false;
    this.myName = playerName ?? null;

    const tokens = { [body.playerId]: body.token };
    this.playerTokens = tokens;
    localStorage.setItem(`tokens:${id}`, JSON.stringify(tokens));

    this.gameId = id;
    this.webSocket.subscribeToGame(id);

    this.emitPerspective();
    this.callbacks.onEvent({ type: 'my-name-change', name: this.myName });
    this.callbacks.onEvent({ type: 'game-id-change', gameId: id });

    const stateResponse = await fetch(`/api/game/${id}`);
    if (stateResponse.ok) {
      const json = await stateResponse.json();
      this.state = BatailleCorse.fromJSON(json as Parameters<typeof BatailleCorse.fromJSON>[0]);
      this.callbacks.onEvent({ type: 'state-update', state: this.state });
    }

    const sessionResponse = await fetch(`/api/game/${id}/session`);
    if (sessionResponse.ok) {
      const view = await sessionResponse.json() as { players: SessionSeat[] };
      this.applySessionView(view.players);
    }
  }
```

Add the import at the top of `GameSession.ts`:

```typescript
import type SessionSeat from '../model/SessionSeat';
```

- [ ] **Step 5: Remove the unconditional `waiting = false` from `restoreSession`**

In `restoreSession`, in the `else` (multiplayer) branch, delete the line `this.waiting = false;`. Leave `this.mode`/`this.myPlayerIndex` assignments intact. Waiting is now resolved by `applySessionView`.

- [ ] **Step 6: Add `applySessionView`**

```typescript
  /** Applies server seat occupancy + names: resolves waiting and both names. */
  applySessionView(players: SessionSeat[]): void {
    const mine = players.find(p => p.id === this.myPlayerIndex);
    const opponent = players.find(p => p.id !== this.myPlayerIndex);

    if (mine && mine.name !== null) {
      this.myName = mine.name;
      this.callbacks.onEvent({ type: 'my-name-change', name: mine.name });
    }

    this.callbacks.onEvent({ type: 'opponent-name-change', name: opponent?.name ?? null });

    this.waiting = this.mode === 'multiplayer' && !(opponent?.joined ?? false);
    this.callbacks.onEvent({ type: 'waiting-change', waiting: this.waiting });
  }
```

- [ ] **Step 7: Read enriched `players` from the JOIN event**

In `processEvent`, replace the JOIN block:

```typescript
    if (response.eventType === 'JOIN') {
      const joinData = response.eventData as unknown as { players?: SessionSeat[] };
      if (joinData?.players) {
        this.applySessionView(joinData.players);
      } else {
        this.waiting = false;
        this.callbacks.onEvent({ type: 'waiting-change', waiting: false });
      }
    }
```

- [ ] **Step 8: Expose `applySessionView` and update `join` in the store**

In `BatailleCorse.store.ts`, update the exposed methods:

```typescript
    join:                 (id: string, name?: string) => session.join(id, name),
    applySessionView:     (players: SessionSeat[]) => session.applySessionView(players),
```

Add the import:

```typescript
import type SessionSeat from '../model/SessionSeat';
```

- [ ] **Step 9: Run frontend tests + build**

Run: `npm run test -- GameSession`
Expected: PASS.
Run: `npm run build`
Expected: build succeeds.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/application/GameSession.ts frontend/src/application/GameSession.test.ts frontend/src/state/BatailleCorse.store.ts
git commit -m "feat: resolve waiting and names from server session view"
```

---

## Task 8: `GameScreen.vue` — fetch session view, render names

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Fetch the session view in `onMounted`**

In `GameScreen.vue` `onMounted`, after `batailleCorseStore.restoreSession(JSON.parse(stored));` and before/after `subscribeToGame`, add:

```typescript
    const sessionResponse = await fetch(`/api/game/${gameId}/session`);
    if (sessionResponse.ok) {
      const view = await sessionResponse.json() as { players: { id: number; name: string | null; joined: boolean }[] };
      batailleCorseStore.applySessionView(view.players);
    }
```

(Place it right after `restoreSession`, before `webSocketService.subscribeToGame(gameId);`.)

- [ ] **Step 2: Bind names in the store refs**

In the `storeToRefs(batailleCorseStore)` destructure, add `myName` and `opponentName`:

```typescript
const { state: batailleCorse, mode, myPlayerIndex, waiting, myName, opponentName,
        lastSend, lastGrab, lastSlap, lastSuccessfulSlap, lastErroneousSlap } = storeToRefs(batailleCorseStore);
```

- [ ] **Step 3: Update the opponent + own labels**

Replace `opponentLabel`:

```typescript
const opponentLabel = computed(() =>
  isSolo.value ? `Computer (${difficultyLabel.value})` : (opponentName.value ?? 'Opponent'));
```

Replace the bottom player tag in the template (`{{ settingsStore.playerName || 'You' }}`) with:

```vue
        <h1 class="player_tag">{{ myName || settingsStore.playerName || 'You' }}</h1>
```

- [ ] **Step 4: Build to verify**

Run (from `frontend/`): `npm run build`
Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: show opponent and own names on the game board"
```

---

## Task 9: `StartGame.vue` — pass name on join

**Files:**
- Modify: `frontend/src/view/alpha/StartGame.vue`

- [ ] **Step 1: Pass the player name into `join`**

In `joinGame()`, update the call:

```typescript
    await batailleCorseStore.join(id, playerName.value || undefined);
```

(`create` already receives `playerName.value || undefined` in `startGame()` — no change needed there.)

- [ ] **Step 2: Build to verify**

Run (from `frontend/`): `npm run build`
Expected: build succeeds.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/view/alpha/StartGame.vue
git commit -m "feat: send joining player's name to the server"
```

---

## Task 10: Cypress — waiting overlay persists; opponent name shows

**Files:**
- Create: `frontend/cypress/specs/waiting-and-names.cy.ts`

Inspect an existing spec (e.g. `frontend/cypress/specs/create-game.cy.ts`) for the project's intercept/visit patterns and reuse them. The spec must:

- [ ] **Step 1: Write the e2e spec**

```typescript
describe('2-player waiting and names', () => {
  it('keeps the waiting overlay visible after creating a Human game', () => {
    cy.visit('/create');
    cy.contains('button', 'Human').click();
    cy.get('#playerName').type('Alice');
    cy.contains('button', 'Create Game').click();

    // Overlay must remain — it must NOT flash and disappear.
    cy.contains('Waiting for opponent', { timeout: 4000 }).should('be.visible');
    cy.contains('button', 'Copy').should('be.visible');
    cy.wait(1500);
    cy.contains('Waiting for opponent').should('be.visible');
  });

  it('shows the opponent name once a second player joins', () => {
    cy.visit('/create');
    cy.contains('button', 'Human').click();
    cy.get('#playerName').type('Alice');
    cy.contains('button', 'Create Game').click();
    cy.contains('Waiting for opponent').should('be.visible');

    cy.location('pathname').then((path) => {
      const id = path.split('/').pop();
      cy.request('POST', `/api/game/${id}/join`, { name: 'Bob' });
    });

    cy.contains('Waiting for opponent').should('not.exist');
    cy.contains('Bob').should('be.visible');
  });
});
```

Adjust selectors (`#playerName`, button labels) to whatever `create-game.cy.ts` already relies on if they differ.

- [ ] **Step 2: Run the spec**

Run (from `frontend/`, against a running dev/prod stack per the repo's run scripts): `npx cypress run --spec cypress/specs/waiting-and-names.cy.ts`
Expected: both tests pass.

- [ ] **Step 3: Commit**

```bash
git add frontend/cypress/specs/waiting-and-names.cy.ts
git commit -m "test: cover waiting overlay persistence and opponent name"
```

---

## Final verification

- [ ] **Backend full suite** — Run (from `backend/`): `mvn -q test` → BUILD SUCCESS.
- [ ] **Frontend unit + build** — Run (from `frontend/`): `npm run test` then `npm run build` → both succeed.
- [ ] **Manual smoke** — Create a Human game in one browser; confirm the overlay stays and the Copy button copies the join URL. Open that URL in a second browser with a different name; confirm both players see each other's names and the overlay clears for the host.

---

## Self-Review notes (spec coverage)

- Bug #1 (premature waiting) → Tasks 7 (remove unconditional `waiting=false`, `applySessionView`) + 8 (fetch session on mount) + 10 (regression test). ✓
- Feature #2 (copy) → no code; covered by making the existing overlay visible (Task 8 fetch + Task 10 assertion that Copy is visible). ✓
- Feature #3 (mutual names) → Tasks 1–5 (backend storage + transport) + 6–9 (frontend send/display). ✓
- `SessionPlayer` consolidation → Tasks 1–2. ✓
- Server-side default names → Task 3. ✓
