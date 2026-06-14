# Game Abstraction & Session-Core Generalization (Slice 2a) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce a game-agnostic shared kernel (`Game`, `GameId`, `PlayerId`, `GameFactory`) and make the Session bounded context depend on it instead of `BatailleCorse`, with BatailleCorse playing exactly as before.

**Architecture:** New `org.kevinkib.cardgames.game` package holds the kernel. `BatailleCorse` implements `Game` and gets a `GameFactory`. `SessionService`/`SessionGame`/`SessionRepository` are generalized over `Game`/`GameId`/`PlayerId`. Presentation keeps working via explicit, temporary `(BatailleCorse)` casts (removed in Slice 2b). Spec: `docs/superpowers/specs/2026-06-14-game-abstraction-session-core-design.md`.

**Tech Stack:** Java 17, Maven (no wrapper). Run from repo root:
- Full suite: `mvn -f backend/pom.xml test` — use the IntelliJ-bundled Maven; there is no `mvnw`.
- The bundled mvn invocation (if `mvn` is not on PATH):
  ```bash
  export JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr"
  MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/plugins/maven/lib/maven3/bin/mvn"
  "$MVN" -f backend/pom.xml test
  ```
- Single test: append `-Dtest=ClassName` (or `-Dtest=ClassName#method`).

The whole plan is **one MR**. Baseline before starting: **183 tests green**.

---

## File structure

**New (`backend/src/main/java/org/kevinkib/cardgames/game/`):**
| File | Responsibility |
|---|---|
| `GameId.java` | UUID game id (replaces `BatailleCorseId`) |
| `PlayerId.java` | player/seat id (replaces `bataillecorse.domain.PlayerId`) |
| `Game.java` | interface the session depends on |
| `GameFactory.java` | interface to construct a game generically |

**New (`backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/`):**
| File | Responsibility |
|---|---|
| `BatailleCorseFactory.java` | `GameFactory` impl creating `BatailleCorse` |

**New (test):** `game/FakeGame.java`, `game/FakeGameFactory.java` (test doubles proving session genericity).

**Modified:** `BatailleCorse.java` (implements `Game`, `concede`→`forfeit`, add `getPlayerIds`), `AppConfig.java` (factory bean + `SessionService` wiring), the session layer (`SessionService`, `SessionGame`, `SessionRepository`, `InMemorySessionRepository`, `SessionToken`), presentation (`BatailleCorseWebSocketController`, `DisconnectForfeitService`, `GameRestController`, `Seat`, `StompSessionSeatRegistry`, `ForfeitReasonRegistry`, DTOs), and the id/`concede` references across tests — most via the scripted migration in Phase 2.

---

## Phase 1 — Shared kernel

### Task 1.1: `GameId`

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/game/GameId.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/game/GameIdTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.cardgames.game;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class GameIdTest {

    @Test
    void givenUuidString_whenConstructed_thenRoundTrips() {
        UUID uuid = UUID.randomUUID();
        assertThat(new GameId(uuid.toString()).uuid(), is(uuid));
    }

    @Test
    void whenGenerated_thenHasUuid() {
        assertThat(GameId.generate().uuid(), notNullValue());
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `mvn -f backend/pom.xml test -Dtest=GameIdTest` → `GameId` not found.

- [ ] **Step 3: Implement**

`GameId.java`:
```java
package org.kevinkib.cardgames.game;

import java.util.UUID;

public record GameId(UUID uuid) {

    public GameId(String id) {
        this(UUID.fromString(id));
    }

    public static GameId generate() {
        return new GameId(UUID.randomUUID());
    }
}
```

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/org/kevinkib/cardgames/game/GameId.java backend/src/test/java/org/kevinkib/cardgames/game/GameIdTest.java
git commit -m "feat(game): add shared GameId"
```

### Task 1.2: `PlayerId`, `Game`, `GameFactory`

**Files:**
- Create: `game/PlayerId.java`, `game/Game.java`, `game/GameFactory.java`

> Value/interface types with no branching logic; exercised by later tasks. Create directly.

- [ ] **Step 1: Create the three types**

`PlayerId.java`:
```java
package org.kevinkib.cardgames.game;

public record PlayerId(Integer id) {

    @Override
    public String toString() {
        return id.toString();
    }
}
```

`Game.java`:
```java
package org.kevinkib.cardgames.game;

import java.util.List;

/** What the session/lifecycle layer needs from any game. Presentation concerns stay per-game. */
public interface Game {

    GameId getId();

    boolean isFinished();

    List<PlayerId> getPlayerIds();

    void forfeit(PlayerId loser);
}
```

`GameFactory.java`:
```java
package org.kevinkib.cardgames.game;

public interface GameFactory {

    Game create(GameId id, int nbPlayers);
}
```

- [ ] **Step 2: Compile** — `mvn -f backend/pom.xml test-compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit**
```bash
git add backend/src/main/java/org/kevinkib/cardgames/game/PlayerId.java backend/src/main/java/org/kevinkib/cardgames/game/Game.java backend/src/main/java/org/kevinkib/cardgames/game/GameFactory.java
git commit -m "feat(game): add Game, GameFactory, shared PlayerId"
```

---

## Phase 2 — Migrate identity to the shared kernel (mechanical)

> Behaviour-preserving rename: `BatailleCorseId` → `GameId`, and `bataillecorse.domain.PlayerId` → `game.PlayerId`. The existing suite is the safety net (must stay at 183 green). Bullshit's own `PlayerId`/`BullshitId` are NOT touched. IntelliJ Move/Rename refactors are the preferred equivalent; the scripted procedure below is the headless version.

### Task 2.1: Replace `BatailleCorseId` with `GameId`

**Files:** all `*.java` under `backend/src` referencing `BatailleCorseId` (29 files).

- [ ] **Step 1: Delete the old id class and rewrite references**

```bash
cd "$(git rev-parse --show-toplevel)"
git rm backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/BatailleCorseId.java
# Replace the FQN import first, then the bare type name, across all java files.
grep -rlZ "BatailleCorseId" backend/src --include=*.java | xargs -0 sed -i \
  -e 's#import org\.kevinkib\.cardgames\.bataillecorse\.domain\.BatailleCorseId;#import org.kevinkib.cardgames.game.GameId;#g' \
  -e 's#\bBatailleCorseId\b#GameId#g'
```

- [ ] **Step 2: Add the `GameId` import where it was only referenced via same-package (the `bataillecorse.domain` files)**

`BatailleCorse.java` is in `bataillecorse.domain`; after the rename it uses `GameId` but has no import. Add it. Verify which `bataillecorse/domain` files now use `GameId` without importing it:
```bash
grep -rl "GameId" backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain backend/src/test/java/org/kevinkib/cardgames/bataillecorse/domain | while read f; do grep -q "import org.kevinkib.cardgames.game.GameId;" "$f" || echo "NEEDS IMPORT: $f"; done
```
For each file printed, add `import org.kevinkib.cardgames.game.GameId;` after its `package` line. (Expected: `BatailleCorse.java`, `BatailleCorseBuilder.java`, and any test using it.)

- [ ] **Step 3: Verify no stale references and compile**
```bash
grep -rn "BatailleCorseId" backend/src --include=*.java || echo "CLEAN"
mvn -f backend/pom.xml test-compile
```
Expected: `CLEAN`, then BUILD SUCCESS. (`BatailleCorseIdDto` is a different identifier — the `\b` word boundary leaves it intact; confirm it still compiles.)

- [ ] **Step 4: Run full suite, expect 183 green.** `mvn -f backend/pom.xml test`

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "refactor: replace BatailleCorseId with shared GameId"
```

### Task 2.2: Move `PlayerId` into the kernel

**Files:** `bataillecorse/domain/PlayerId.java` (deleted) + all `*.java` referencing it (excluding Bullshit).

- [ ] **Step 1: Delete BatailleCorse's PlayerId and repoint references**

```bash
cd "$(git rev-parse --show-toplevel)"
git rm backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/PlayerId.java
# Repoint explicit imports of the bataillecorse PlayerId to the kernel one.
grep -rlZ "org.kevinkib.cardgames.bataillecorse.domain.PlayerId" backend/src --include=*.java | xargs -0 sed -i \
  -e 's#org\.kevinkib\.cardgames\.bataillecorse\.domain\.PlayerId#org.kevinkib.cardgames.game.PlayerId#g'
```

- [ ] **Step 2: Add the import to same-package users**

`bataillecorse.domain` files that use `PlayerId` unqualified (e.g. `Player.java`, `BatailleCorse.java`) now need the kernel import:
```bash
for f in backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/*.java backend/src/test/java/org/kevinkib/cardgames/bataillecorse/domain/*.java; do
  grep -q "\bPlayerId\b" "$f" && ! grep -q "import org.kevinkib.cardgames.game.PlayerId;" "$f" && echo "NEEDS IMPORT: $f"
done
```
For each printed file, add `import org.kevinkib.cardgames.game.PlayerId;` after the `package` line.

- [ ] **Step 3: Verify and compile**
```bash
grep -rn "bataillecorse.domain.PlayerId" backend/src --include=*.java || echo "CLEAN"
mvn -f backend/pom.xml test-compile
```
Expected: `CLEAN`, BUILD SUCCESS. (Bullshit's `org.kevinkib.cardgames.bullshit.domain.PlayerId` is untouched — confirm `grep -rn "bullshit.domain.PlayerId"` still shows its usages intact.)

- [ ] **Step 4: Full suite, expect 183 green.**

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "refactor: move BatailleCorse PlayerId into shared game kernel"
```

---

## Phase 3 — BatailleCorse conforms to `Game`

### Task 3.1: Implement `Game` on `BatailleCorse` (rename `concede`→`forfeit`, add `getPlayerIds`)

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/BatailleCorse.java`
- Modify: callers of `concede` — `presentation/DisconnectForfeitService.java`, `bataillecorse/domain/BatailleCorseConcedeTest.java`
- Test: `bataillecorse/domain/BatailleCorseConcedeTest.java` (rename method calls)

- [ ] **Step 1: Add the conformance test**

Append to `BatailleCorseConcedeTest.java` (it already builds 2-player games; mirror its existing setup). Add:
```java
    @Test
    void implementsGame_exposesIdPlayersAndFinished() {
        BatailleCorse game = BatailleCorseBuilder.aBatailleCorse().withNumberOfPlayers(2).build();
        org.kevinkib.cardgames.game.Game asGame = game;

        assertThat(asGame.getId(), is(game.getId()));
        assertThat(asGame.getPlayerIds(), is(game.getPlayers().stream().map(Player::id).toList()));
        assertThat(asGame.isFinished(), is(false));
    }
```
(Use the builder API actually present in `BatailleCorseBuilder` — check its method names; adjust `withNumberOfPlayers` to the real one if different. Ensure `import static org.hamcrest.Matchers.is;` and `org.kevinkib.cardgames.game.Game` resolve.)

- [ ] **Step 2: Run, expect FAIL** — `BatailleCorse` does not implement `Game` / no `getPlayerIds`.

- [ ] **Step 3: Implement on `BatailleCorse`**

Change the class declaration:
```java
public class BatailleCorse implements org.kevinkib.cardgames.game.Game {
```
(or add `import org.kevinkib.cardgames.game.Game;` and `implements Game`.)

Rename the existing `concede` method to `forfeit` (signature and body unchanged otherwise):
```java
// was: public synchronized void concede(PlayerId loser) {
@Override
public synchronized void forfeit(PlayerId loser) {
    if (isFinished()) {
        return;
    }
    if (players.size() != 2) {
        throw new UnsupportedOperationException(
                "forfeit only defines a winner for 2-player games; got " + players.size());
    }
    Player winner = players.stream()
            .filter(player -> !player.id().equals(loser))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown seat " + loser));
    this.result = new Result(winner);
}
```

Add `getPlayerIds()` (place near `getPlayers()`):
```java
@Override
public List<PlayerId> getPlayerIds() {
    return players.stream().map(Player::id).toList();
}
```
`getId()` and `isFinished()` already satisfy the interface (now returning `GameId` after Phase 2).

- [ ] **Step 4: Update the `concede` caller in `DisconnectForfeitService`**

In `DisconnectForfeitService.forfeit(...)`, change `game.concede(seat.playerId());` to `game.forfeit(seat.playerId());`.

- [ ] **Step 5: Update `BatailleCorseConcedeTest`** — replace every `.concede(` with `.forfeit(`:
```bash
sed -i 's#\.concede(#.forfeit(#g' backend/src/test/java/org/kevinkib/cardgames/bataillecorse/domain/BatailleCorseConcedeTest.java
```
(Confirm no other production/test references to `concede` remain: `grep -rn "concede" backend/src || echo CLEAN`.)

- [ ] **Step 6: Run, expect PASS** — `mvn -f backend/pom.xml test -Dtest=BatailleCorseConcedeTest`, then full suite green.

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(bataillecorse): implement Game (concede->forfeit, add getPlayerIds)"
```

### Task 3.2: `BatailleCorseFactory`

**Files:**
- Create: `bataillecorse/domain/BatailleCorseFactory.java`
- Test: `bataillecorse/domain/BatailleCorseFactoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.cardgames.bataillecorse.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class BatailleCorseFactoryTest {

    @Test
    void createsAPlayableBatailleCorseWithTheGivenId() {
        GameId id = GameId.generate();

        Game game = new BatailleCorseFactory().create(id, 2);

        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), hasSize(2));
        assertThat(game.isFinished(), is(false));
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement**

```java
package org.kevinkib.cardgames.bataillecorse.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class BatailleCorseFactory implements GameFactory {

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new BatailleCorse(id, nbPlayers);
    }
}
```
(Confirm `BatailleCorse` has a `(GameId, int)` constructor — it does, formerly `(BatailleCorseId, int)`, renamed in Phase 2.)

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/org/kevinkib/cardgames/bataillecorse/domain/BatailleCorseFactory.java backend/src/test/java/org/kevinkib/cardgames/bataillecorse/domain/BatailleCorseFactoryTest.java
git commit -m "feat(bataillecorse): add BatailleCorseFactory"
```

---

## Phase 4 — Generalize the session over `Game`

> After this phase the session core holds `Game`, not `BatailleCorse`. Presentation casts `Game`→`BatailleCorse` at each fetch site. Existing tests stay green.

### Task 4.1: `SessionGame.create` takes player ids

**Files:**
- Modify: `sessionmanagement/domain/SessionGame.java`
- Modify caller: `sessionmanagement/application/SessionService.java`
- Modify test: `sessionmanagement/domain/SessionGameTest.java`

- [ ] **Step 1: Change `SessionGame.create` signature**

In `SessionGame.java`, change:
```java
public static SessionGame create(GameId id, List<Player> players) {
    Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
    for (Player player : players) {
        seats.put(player.id(), new SessionPlayer(player.id(), SessionToken.generate()));
    }
    return new SessionGame(id, seats);
}
```
to:
```java
public static SessionGame create(GameId id, List<PlayerId> playerIds) {
    Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
    for (PlayerId playerId : playerIds) {
        seats.put(playerId, new SessionPlayer(playerId, SessionToken.generate()));
    }
    return new SessionGame(id, seats);
}
```
Remove the now-unused `import ...bataillecorse.domain.Player;` (keep `game.PlayerId`).

- [ ] **Step 2: Update `SessionGameTest`** — wherever it calls `SessionGame.create(id, players)`, pass a `List<PlayerId>` instead of `List<Player>` (e.g. `List.of(new PlayerId(0), new PlayerId(1))`). Adjust imports.

- [ ] **Step 3: Update `SessionService.createGame`** (compiles after 4.2; for now change the two call sites): replace `SessionGame.create(batailleCorse.getId(), batailleCorse.getPlayers())` with `SessionGame.create(game.getId(), game.getPlayerIds())` — finalised in Task 4.2.

- [ ] **Step 4: Defer running until 4.2** (signature change ripples into SessionService). Proceed to 4.2, then run.

### Task 4.2: Generalize `SessionService`, `SessionRepository`, `InMemorySessionRepository`

**Files:**
- Modify: `sessionmanagement/application/SessionService.java`
- Modify: `sessionmanagement/application/port/SessionRepository.java`
- Modify: `sessionmanagement/infrastructure/InMemorySessionRepository.java`
- Modify: `config/AppConfig.java`

- [ ] **Step 1: `SessionRepository` — type over `Game`/`GameId`**

Rewrite the interface to use `Game` (import `org.kevinkib.cardgames.game.Game`, `GameId`, `PlayerId`; drop the `bataillecorse` imports):
```java
void save(Game game, SessionGame sessionGame);
Game load(GameId id);
SessionToken loadSessionToken(GameId gameId, PlayerId playerId);
SessionGame loadSessionGame(GameId id);
void touch(GameId id);
void remove(GameId id);
List<GameId> evictStale(Duration finishedGrace, Duration idleTtl);
```

- [ ] **Step 2: `InMemorySessionRepository` — store `Game`**

Change the maps and method signatures: `Map<GameId, Game> games`, `Map<GameId, SessionGame> sessionGames`, `Map<GameId, Instant> lastActivityAt`. `save(Game game, ...)` uses `game.getId()`. `evictStale` uses `entry.getValue().isFinished()`. Replace `BatailleCorse`/`BatailleCorseId` imports with `Game`/`GameId`.

- [ ] **Step 3: `SessionService` — inject `GameFactory`, return `Game`**

- Add field `private final GameFactory gameFactory;` and constructor param.
- `createGame(int nbPlayers, GameMode mode, String creatorName)`:
  ```java
  GameId id = GameId.generate();
  Game game = gameFactory.create(id, nbPlayers);
  SessionGame sessionGame = SessionGame.create(game.getId(), game.getPlayerIds());
  // ... SOLO/MULTIPLAYER seat claiming unchanged, iterate game.getPlayerIds() ...
  repository.save(game, sessionGame);
  return game;
  ```
  The SOLO branch that did `for (Player player : batailleCorse.getPlayers()) sessionGame.claim(player.id(), ...)` becomes `for (PlayerId playerId : game.getPlayerIds()) sessionGame.claim(playerId, ...)`.
- `rematch(GameId id)`: `Game fresh = gameFactory.create(id, session.seats().size());`
- `getGame(GameId id)`: return type `Game`.
- Replace all `BatailleCorse`/`BatailleCorseId` with `Game`/`GameId`; import the kernel types.

- [ ] **Step 4: `AppConfig` — wire the factory**

```java
@Bean
public GameFactory gameFactory() {
    return new BatailleCorseFactory();
}

@Bean
public SessionService sessionService() {
    return new SessionService(sessionRepository(), gameFactory());
}
```
Add imports for `GameFactory` and `BatailleCorseFactory`.

- [ ] **Step 5: Presentation casts** — at each site that needs the concrete game, cast:
  - `BatailleCorseWebSocketController` (`send`/`slap`/`grab`/`rematch`/`createGame`): `BatailleCorse batailleCorse = (BatailleCorse) sessionService.getGame(gameId);` and `(BatailleCorse) sessionService.createGame(...)` / `(BatailleCorse) sessionService.rematch(...)`.
  - `DisconnectForfeitService.findGame`: `return (BatailleCorse) sessionService.getGame(gameId);` (keep its return type `BatailleCorse`).
  - `GameRestController`: cast the fetched game to `BatailleCorse` before building the DTO.
  Add `import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;` where needed. These casts are temporary (Slice 2b removes them).

- [ ] **Step 6: Update session/cleanup tests** — `SessionServiceTest` and `GameCleanupServiceTest` construct `new SessionService(repository)` / rely on `BatailleCorse` returns. Inject a factory: `new SessionService(repository, new BatailleCorseFactory())`. Where a test asserts on the concrete returned game, cast to `BatailleCorse`. `InMemorySessionRepositoryTest` — store via a `BatailleCorseFactory().create(...)` or a `FakeGame` (Task 5.1).

- [ ] **Step 7: Compile + full suite**
```bash
mvn -f backend/pom.xml test
```
Expected: BUILD SUCCESS, 183 (+ the Phase 1/3 additions) green. Fix compile errors by following the cast pattern; do not change game behaviour.

- [ ] **Step 8: Commit**
```bash
git add -A
git commit -m "refactor(session): generalize SessionService/repository over Game + GameFactory"
```

---

## Phase 5 — Prove genericity with a fake game

### Task 5.1: `FakeGame` + `FakeGameFactory` test doubles

**Files:**
- Create (test): `backend/src/test/java/org/kevinkib/cardgames/game/FakeGame.java`
- Create (test): `backend/src/test/java/org/kevinkib/cardgames/game/FakeGameFactory.java`

- [ ] **Step 1: Create the doubles**

`FakeGame.java`:
```java
package org.kevinkib.cardgames.game;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** Minimal Game test double for exercising the session layer without a real game. */
public class FakeGame implements Game {

    private final GameId id;
    private final List<PlayerId> playerIds;
    private boolean finished = false;

    public FakeGame(GameId id, int nbPlayers) {
        this.id = id;
        this.playerIds = new ArrayList<>(
                IntStream.range(0, nbPlayers).mapToObj(PlayerId::new).toList());
    }

    public void finish() {
        this.finished = true;
    }

    @Override
    public GameId getId() {
        return id;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public List<PlayerId> getPlayerIds() {
        return new ArrayList<>(playerIds);
    }

    @Override
    public void forfeit(PlayerId loser) {
        this.finished = true;
    }
}
```

`FakeGameFactory.java`:
```java
package org.kevinkib.cardgames.game;

public class FakeGameFactory implements GameFactory {

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new FakeGame(id, nbPlayers);
    }
}
```

- [ ] **Step 2: Compile test sources** — `mvn -f backend/pom.xml test-compile` → BUILD SUCCESS.

- [ ] **Step 3: Commit**
```bash
git add backend/src/test/java/org/kevinkib/cardgames/game/FakeGame.java backend/src/test/java/org/kevinkib/cardgames/game/FakeGameFactory.java
git commit -m "test(game): add FakeGame/FakeGameFactory doubles"
```

### Task 5.2: Session genericity test

**Files:**
- Create (test): `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionServiceGenericGameTest.java`

- [ ] **Step 1: Write the test**

```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.FakeGameFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;

import java.time.Clock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class SessionServiceGenericGameTest {

    private final SessionService sessionService =
            new SessionService(new InMemorySessionRepository(Clock.systemUTC()), new FakeGameFactory());

    @Test
    void createsAndLoadsAGameWithoutKnowingItsConcreteType() {
        Game created = sessionService.createGame(2, GameMode.SOLO, null);

        Game loaded = sessionService.getGame(created.getId());
        assertThat(loaded.getId(), is(created.getId()));
        assertThat(loaded.getPlayerIds(), hasSize(2));
    }

    @Test
    void createsSessionSeatsFromTheGamesPlayerIds() {
        Game created = sessionService.createGame(2, GameMode.SOLO, null);

        assertThat(sessionService.getSeats(created.getId()), hasSize(2));
        assertThat(sessionService.isSeatClaimed(created.getId(), new PlayerId(0)), is(true));
    }
}
```
(Adjust method names to the real `SessionService` API — `getSeats`, `isSeatClaimed`, `getGame`, `createGame` all exist today; `createGame` now returns `Game`.)

- [ ] **Step 2: Run, expect PASS** — `mvn -f backend/pom.xml test -Dtest=SessionServiceGenericGameTest`. This proves the session works against a game that is not BatailleCorse.

- [ ] **Step 3: Commit**
```bash
git add backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionServiceGenericGameTest.java
git commit -m "test(session): prove session is game-agnostic via FakeGame"
```

### Task 5.3: Final gate + MR

- [ ] **Step 1: Full suite** — `mvn -f backend/pom.xml test` → BUILD SUCCESS, all green (183 baseline + new kernel/factory/genericity tests; BatailleCorse unchanged in behaviour).

- [ ] **Step 2: Push and open the MR**

Push `worktree-game-session-abstraction` and open a merge request titled `refactor: Game abstraction + session-core generalization (Slice 2a)`. Body: summarise the shared kernel, BatailleCorse conforming, the generic session proven with `FakeGame`, and that presentation is unchanged behaviourally (transitional casts, removed in 2b). Note BatailleCorse plays identically and the full suite is green.

---

## Self-review notes

- **Spec coverage:** kernel (Task 1.1–1.2) ✓; BatailleCorse conforms + factory (3.1–3.2) ✓; generic session (4.1–4.2) ✓; transitional presentation casts (4.2 Step 5) ✓; FakeGame-driven genericity tests (5.1–5.2) ✓; 183 regression green (every phase gate) ✓.
- **Out of scope held:** no presentation split, no Bullshit migration, no Bullshit transport, no multi-game selection, no frontend.
- **Naming:** `Game` uses existing getter names (`getId`/`isFinished`/`getPlayerIds`/`forfeit`) to minimise churn — a deliberate refinement over the spec's illustrative `id()`/`playerIds()`.
- **Type consistency:** `GameId`, `PlayerId` (kernel), `Game.getId/isFinished/getPlayerIds/forfeit`, `GameFactory.create(GameId,int)`, `SessionGame.create(GameId, List<PlayerId>)`, `SessionService(SessionRepository, GameFactory)` used consistently across tasks.
- **Migration safety:** Phase 2 is behaviour-preserving and gated on the full suite; `\b` word boundaries protect `BatailleCorseIdDto`; Bullshit identity untouched (verified by grep).
