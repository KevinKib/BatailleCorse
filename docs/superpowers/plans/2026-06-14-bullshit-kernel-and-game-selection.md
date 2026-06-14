# Bullshit onto the Kernel + Multi-Game Selection (Slice 2b-ii-a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Bullshit a `Game` the generic session can host, and let `SessionService` create either game by a stable game-type slug, with rematch preserving the type — backend only, fully tested, nothing user-visible.

**Architecture:** Bullshit conforms to the shared kernel exactly as BatailleCorse did in Slice 2a (`BullshitId`→`GameId`, its `PlayerId`→`game.PlayerId`, `implements Game`, `getPlayerIds`). `GameFactory` gains a `gameType()` slug; a new `GameFactories` resolver maps slug→factory (mirroring the `GameLifecycleBroadcaster` resolver). `SessionService` takes `GameFactories`, `createGame` takes the slug, `SessionGame` records it, and `rematch` reads it back.

**Tech Stack:** Java 17, Spring Boot, Maven (no wrapper). Run from repo root:
- Full suite: `mvn -f backend/pom.xml test`. Use the IntelliJ-bundled Maven; there is **no** `./mvnw` — never invent one or fabricate output.
- Bundled mvn (if `mvn` not on PATH):
  ```bash
  export JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr"
  MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/plugins/maven/lib/maven3/bin/mvn"
  "$MVN" -f backend/pom.xml test
  ```
- Single test: append `-Dtest=ClassName` (or `-Dtest=ClassName#method`).
- Use `clean test` after the scripted migrations (incremental compiles report stale BUILD SUCCESS).

The whole plan is **one MR**. Baseline before starting: full suite green (run `mvn -f backend/pom.xml test` and note the count, expected ~195).

Spec: `docs/superpowers/specs/2026-06-14-bullshit-kernel-and-game-selection-design.md`.

---

## File structure

**Modified (Bullshit domain — kernel conformance):**
| File | Change |
|---|---|
| `bullshit/domain/BullshitId.java` | deleted (replaced by shared `GameId`) |
| `bullshit/domain/player/PlayerId.java` | deleted (replaced by shared `game.PlayerId`) |
| `bullshit/domain/Bullshit.java` | `implements Game`; `GameId`/`game.PlayerId`; add `getPlayerIds()` |
| other `bullshit/domain/**` + tests | repoint id/playerId references |

**New:**
| File | Responsibility |
|---|---|
| `bullshit/domain/BullshitFactory.java` | `GameFactory` impl, `gameType()` = `"bullshit"` |
| `sessionmanagement/application/GameFactories.java` | resolve a `GameFactory` by slug |

**Modified (selection):** `game/GameFactory.java` (add `gameType()`), `bataillecorse/domain/BatailleCorseFactory.java` (slug), `sessionmanagement/domain/SessionGame.java` (record `gameType`), `sessionmanagement/application/SessionService.java` (slug-based create/rematch over `GameFactories`), `config/AppConfig.java` (factory beans + resolver), plus the `createGame`/`new SessionService(...)` call sites in tests and `BatailleCorseWebSocketController`, and `game/FakeGameFactory.java` (test double gains a slug).

---

## Phase 1 — Bullshit conforms to the kernel

### Task 1.1: Replace `BullshitId` with shared `GameId`

**Files:** the 5 files referencing `BullshitId` (delete `BullshitId.java` + `BullshitIdTest.java`).

- [ ] **Step 1: Delete the id class + its now-redundant test, repoint references**

`GameId` is already covered by `GameIdTest`, so `BullshitIdTest` is redundant.
```bash
cd "$(git rev-parse --show-toplevel)"
git rm backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/BullshitId.java
git rm backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitIdTest.java
# Replace the FQN import (none today — same package) and the bare type name across bullshit sources/tests.
grep -rlZ "\bBullshitId\b" backend/src --include=*.java | xargs -0 -r sed -i \
  -e 's#\bBullshitId\b#GameId#g'
```

- [ ] **Step 2: Add the `GameId` import to the bullshit files that used `BullshitId` unqualified**

`Bullshit.java` and `BullshitBuilder.java` are in `bullshit.domain`/its test pkg and referenced `BullshitId` without import; after the rename they use `GameId` with no import. Add it:
```bash
for f in backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Bullshit.java \
         backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitBuilder.java \
         backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitTest.java; do
  grep -q "import org.kevinkib.cardgames.game.GameId;" "$f" || \
    sed -i '/^package /a import org.kevinkib.cardgames.game.GameId;' "$f"
done
```
(If a file does not actually reference `GameId` the import is harmless to add but prefer correctness — the grep guard only adds when missing; remove any unused import the compiler flags in Step 3.)

- [ ] **Step 3: Verify and compile**
```bash
grep -rn "\bBullshitId\b" backend/src --include=*.java || echo "CLEAN"
mvn -f backend/pom.xml test-compile
```
Expected: `CLEAN`, BUILD SUCCESS. Fix any "unused import"/"cannot find symbol" in the named bullshit files.

- [ ] **Step 4: Run bullshit tests, expect green** — `mvn -f backend/pom.xml test -Dtest='org.kevinkib.cardgames.bullshit.**'`

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "refactor(bullshit): replace BullshitId with shared GameId"
```

### Task 1.2: Move Bullshit's `PlayerId` into the kernel

**Files:** delete `bullshit/domain/player/PlayerId.java`; repoint the 7 referencing files.

- [ ] **Step 1: Delete Bullshit's PlayerId and repoint references**

Bullshit's `PlayerId` is identical to `game.PlayerId`. Repoint the FQN imports to the kernel type, then delete the class.
```bash
cd "$(git rev-parse --show-toplevel)"
grep -rlZ "org.kevinkib.cardgames.bullshit.domain.player.PlayerId" backend/src --include=*.java | xargs -0 -r sed -i \
  -e 's#org\.kevinkib\.cardgames\.bullshit\.domain\.player\.PlayerId#org.kevinkib.cardgames.game.PlayerId#g'
git rm backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/player/PlayerId.java
```

- [ ] **Step 2: Add the kernel import to same-package users**

`player/Player.java` is in `bullshit.domain.player` and used `PlayerId` unqualified; it now needs the kernel import:
```bash
for f in $(grep -rl "\bPlayerId\b" backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/player); do
  grep -q "import org.kevinkib.cardgames.game.PlayerId;" "$f" || \
    sed -i '/^package /a import org.kevinkib.cardgames.game.PlayerId;' "$f"
done
```

- [ ] **Step 3: Verify no stale references and compile**
```bash
grep -rn "bullshit.domain.player.PlayerId" backend/src --include=*.java || echo "CLEAN"
mvn -f backend/pom.xml test-compile
```
Expected: `CLEAN`, BUILD SUCCESS.

- [ ] **Step 4: Full suite, expect green.** `mvn -f backend/pom.xml clean test`

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "refactor(bullshit): move PlayerId into shared game kernel"
```

### Task 1.3: `Bullshit implements Game`

**Files:**
- Modify: `bullshit/domain/Bullshit.java`
- Test: `bullshit/domain/BullshitGameConformanceTest.java` (new)

- [ ] **Step 1: Write the failing conformance test**

`backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitGameConformanceTest.java`:
```java
package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.PlayerId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class BullshitGameConformanceTest {

    @Test
    void givenBullshit_whenViewedAsGame_thenExposesIdPlayersAndFinished() {
        Bullshit bullshit = new Bullshit(GameId.generate(), 3);
        Game asGame = bullshit;

        assertThat(asGame.getId(), is(bullshit.getId()));
        assertThat(asGame.getPlayerIds(), hasSize(3));
        assertThat(asGame.isFinished(), is(false));
    }

    @Test
    void givenThreePlayers_whenForfeitThroughGame_thenPlayerRemoved() {
        Game game = new Bullshit(GameId.generate(), 3);

        game.forfeit(new PlayerId(0));

        assertThat(game.getPlayerIds(), hasSize(2));
        assertThat(game.isFinished(), is(false));
    }
}
```

- [ ] **Step 2: Run, expect FAIL** — `Bullshit` does not implement `Game` / no `getPlayerIds`.
```bash
mvn -f backend/pom.xml test -Dtest=BullshitGameConformanceTest
```

- [ ] **Step 3: Implement `Game` on `Bullshit`**

In `Bullshit.java`, add the import and `implements Game`:
```java
import org.kevinkib.cardgames.game.Game;
```
```java
public class Bullshit implements Game {
```
Add `@Override` to the existing `getId()`, `isFinished()`, and `forfeit(PlayerId)` (signatures already match `Game`). Add `getPlayerIds()` near `getPlayers()`:
```java
@Override
public List<PlayerId> getPlayerIds() {
    return players.stream().map(Player::id).toList();
}
```
(`Player::id` returns `game.PlayerId` after Task 1.2; `List` and `PlayerId` are already imported.)

- [ ] **Step 4: Run, expect PASS**, then full suite green.
```bash
mvn -f backend/pom.xml test -Dtest=BullshitGameConformanceTest
mvn -f backend/pom.xml clean test
```

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "feat(bullshit): implement Game (getId/isFinished/forfeit + getPlayerIds)"
```

---

## Phase 2 — Factories declare a game-type slug

### Task 2.1: `GameFactory.gameType()` + BatailleCorse slug + FakeGameFactory slug

**Files:**
- Modify: `game/GameFactory.java`, `bataillecorse/domain/BatailleCorseFactory.java`, `game/FakeGameFactory.java` (test)

- [ ] **Step 1: Add `gameType()` to the interface**

`game/GameFactory.java`:
```java
package org.kevinkib.cardgames.game;

public interface GameFactory {

    /** Stable identifier used to select this game when creating a session. */
    String gameType();

    Game create(GameId id, int nbPlayers);
}
```

- [ ] **Step 2: Implement it on `BatailleCorseFactory`** (expose the slug as a constant)

`bataillecorse/domain/BatailleCorseFactory.java`:
```java
package org.kevinkib.cardgames.bataillecorse.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class BatailleCorseFactory implements GameFactory {

    public static final String GAME_TYPE = "bataille-corse";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new BatailleCorse(id, nbPlayers);
    }
}
```

- [ ] **Step 3: Implement it on the `FakeGameFactory` test double**

`game/FakeGameFactory.java` (test):
```java
package org.kevinkib.cardgames.game;

public class FakeGameFactory implements GameFactory {

    public static final String GAME_TYPE = "fake";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new FakeGame(id, nbPlayers);
    }
}
```

- [ ] **Step 4: Compile** — `mvn -f backend/pom.xml test-compile`. (Other `GameFactory` impls don't exist yet; BullshitFactory is added next.) Expected BUILD SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add -A
git commit -m "feat(game): GameFactory declares a stable gameType() slug"
```

### Task 2.2: `BullshitFactory`

**Files:**
- Create: `bullshit/domain/BullshitFactory.java`
- Test: `bullshit/domain/BullshitFactoryTest.java`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactoryTest.java`:
```java
package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameId;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class BullshitFactoryTest {

    @Test
    void givenId_whenCreate_thenReturnsPlayableBullshitWithThatId() {
        GameId id = GameId.generate();

        Game game = new BullshitFactory().create(id, 3);

        assertThat(game, instanceOf(Bullshit.class));
        assertThat(game.getId(), is(id));
        assertThat(game.getPlayerIds(), hasSize(3));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void gameType_isBullshit() {
        assertThat(new BullshitFactory().gameType(), is("bullshit"));
    }
}
```

- [ ] **Step 2: Run, expect FAIL.** `mvn -f backend/pom.xml test -Dtest=BullshitFactoryTest`

- [ ] **Step 3: Implement**

`bullshit/domain/BullshitFactory.java`:
```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;

public class BullshitFactory implements GameFactory {

    public static final String GAME_TYPE = "bullshit";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new Bullshit(id, nbPlayers);
    }
}
```

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactory.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactoryTest.java
git commit -m "feat(bullshit): add BullshitFactory (gameType=bullshit)"
```

---

## Phase 3 — Game-factory resolver

### Task 3.1: `GameFactories`

**Files:**
- Create: `sessionmanagement/application/GameFactories.java`
- Test: `sessionmanagement/application/GameFactoriesTest.java`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/GameFactoriesTest.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.GameId;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameFactoriesTest {

    private final GameFactories factories = new GameFactories(
            List.of(new BatailleCorseFactory(), new BullshitFactory()));

    @Test
    void givenBullshitSlug_whenFactoryFor_thenCreatesBullshit() {
        assertThat(factories.factoryFor("bullshit").create(GameId.generate(), 3),
                instanceOf(Bullshit.class));
    }

    @Test
    void givenBatailleCorseSlug_whenFactoryFor_thenCreatesBatailleCorse() {
        assertThat(factories.factoryFor("bataille-corse").create(GameId.generate(), 2),
                instanceOf(BatailleCorse.class));
    }

    @Test
    void givenUnknownSlug_whenFactoryFor_thenThrows() {
        assertThrows(IllegalArgumentException.class, () -> factories.factoryFor("chess"));
    }
}
```

- [ ] **Step 2: Run, expect FAIL.**

- [ ] **Step 3: Implement**

`sessionmanagement/application/GameFactories.java`:
```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.kevinkib.cardgames.game.GameFactory;

import java.util.List;

public class GameFactories {

    private final List<GameFactory> factories;

    public GameFactories(List<GameFactory> factories) {
        this.factories = factories;
    }

    public GameFactory factoryFor(String gameType) {
        return factories.stream()
                .filter(f -> f.gameType().equals(gameType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown game type " + gameType));
    }
}
```

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Commit**
```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/application/GameFactories.java backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/GameFactoriesTest.java
git commit -m "feat(session): GameFactories resolves a GameFactory by slug"
```

---

## Phase 4 — Session selects + remembers the game type

### Task 4.1: `SessionGame` records the game type

**Files:**
- Modify: `sessionmanagement/domain/SessionGame.java`
- Modify test: `sessionmanagement/domain/SessionGameTest.java`

- [ ] **Step 1: Add `gameType` to the record + `create` signature**

In `SessionGame.java`, change the record header and `create`:
```java
public record SessionGame(GameId id, String gameType, Map<PlayerId, SessionPlayer> players) {

    public static SessionGame create(GameId id, List<PlayerId> playerIds, String gameType) {
        Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
        for (PlayerId playerId : playerIds) {
            seats.put(playerId, new SessionPlayer(playerId, SessionToken.generate()));
        }
        return new SessionGame(id, gameType, seats);
    }
```
(The rest of the record body is unchanged; the canonical constructor now takes `gameType` between `id` and `players`.)

- [ ] **Step 2: Update `SessionGameTest`** — its `playerIds(...)`-based `create` calls now pass a game type. Replace each `SessionGame.create(GameId.generate(), players)` / `SessionGame.create(GameId.generate(), playerIds(2))` with the same call plus a slug literal, e.g.:
```bash
sed -i -E 's#SessionGame\.create\((GameId\.generate\(\)), (players|playerIds\(2\))\)#SessionGame.create(\1, \2, "bataille-corse")#g' \
  backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/domain/SessionGameTest.java
```
Confirm with `grep -n "SessionGame.create(" backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/domain/SessionGameTest.java` that every call now has three args.

- [ ] **Step 3: Defer running until 4.2** (the `create` signature ripples into `SessionService`). Proceed to 4.2, then run.

### Task 4.2: `SessionService` selects by slug; `rematch` preserves it; `AppConfig` wiring

**Files:**
- Modify: `sessionmanagement/application/SessionService.java`
- Modify: `config/AppConfig.java`
- Modify: `bataillecorse/presentation/BatailleCorseWebSocketController.java`
- Modify call sites: the test files listed in Step 5.

- [ ] **Step 1: Rework `SessionService`**

Replace the `GameFactory` field with `GameFactories`, and make the create overloads slug-first:
```java
    private final SessionRepository repository;
    private final GameFactories gameFactories;

    public SessionService(SessionRepository repository, GameFactories gameFactories) {
        this.repository = repository;
        this.gameFactories = gameFactories;
    }

    public Game createGame(String gameType, int nbPlayers) {
        return createGame(gameType, nbPlayers, GameMode.SOLO, null);
    }

    public Game createGame(String gameType, int nbPlayers, GameMode mode) {
        return createGame(gameType, nbPlayers, mode, null);
    }

    public Game createGame(String gameType, int nbPlayers, GameMode mode, String creatorName) {
        GameId id = GameId.generate();
        Game game = gameFactories.factoryFor(gameType).create(id, nbPlayers);

        SessionGame sessionGame = SessionGame.create(id, game.getPlayerIds(), gameType);

        if (mode == GameMode.SOLO) {
            for (PlayerId playerId : game.getPlayerIds()) {
                sessionGame.claim(playerId, defaultNameFor(playerId));
            }
        } else {
            PlayerId creatorSeat = new PlayerId(0);
            sessionGame.claim(creatorSeat, resolveName(creatorSeat, creatorName));
        }

        repository.save(game, sessionGame);
        return game;
    }
```
Rework `rematch` to read the stored type:
```java
    public Game rematch(GameId id) {
        SessionGame session = repository.loadSessionGame(id);
        Game fresh = gameFactories.factoryFor(session.gameType()).create(id, session.seats().size());
        session.clearRematch();
        repository.save(fresh, session);
        return fresh;
    }
```
Update the import: remove `import org.kevinkib.cardgames.game.GameFactory;`, add nothing (`GameFactories` is same package).

- [ ] **Step 2: Wire `AppConfig`**

Replace the single `gameFactory()` bean with both factories + a resolver, and update `sessionService()`:
```java
    @Bean
    public BatailleCorseFactory batailleCorseFactory() {
        return new BatailleCorseFactory();
    }

    @Bean
    public BullshitFactory bullshitFactory() {
        return new BullshitFactory();
    }

    @Bean
    public GameFactories gameFactories() {
        return new GameFactories(java.util.List.of(batailleCorseFactory(), bullshitFactory()));
    }

    @Bean
    public SessionService sessionService() {
        return new SessionService(sessionRepository(), gameFactories());
    }
```
Update imports: drop `import org.kevinkib.cardgames.game.GameFactory;`; add `import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;` and `import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;` (keep the `BatailleCorseFactory` import).

- [ ] **Step 3: Update the BatailleCorse `/create` handler to pass its slug**

In `BatailleCorseWebSocketController.java`, change:
```java
BatailleCorse batailleCorse = (BatailleCorse) sessionService.createGame(NB_PLAYERS, mode, name);
```
to:
```java
BatailleCorse batailleCorse = (BatailleCorse) sessionService.createGame(BatailleCorseFactory.GAME_TYPE, NB_PLAYERS, mode, name);
```
Add `import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;` (the controller already imports `bataillecorse.domain.BatailleCorse`).

- [ ] **Step 4: Update the `new SessionService(...)` construction sites**

Wrap each test's factory argument in `GameFactories`. Run from repo root:
```bash
cd "$(git rev-parse --show-toplevel)"
# BatailleCorse-factory sites
grep -rlZ "new SessionService(new InMemorySessionRepository" backend/src/test --include=*.java | xargs -0 -r sed -i \
  -E 's#new SessionService\((new InMemorySessionRepository\([^)]*\)), (new [A-Za-z.]*BatailleCorseFactory\(\))\)#new SessionService(\1, new org.kevinkib.cardgames.sessionmanagement.application.GameFactories(java.util.List.of(\2)))#g'
# FakeGameFactory site (SessionServiceGenericGameTest)
sed -i -E 's#new SessionService\((new InMemorySessionRepository\([^)]*\)), (new FakeGameFactory\(\))\)#new SessionService(\1, new GameFactories(java.util.List.of(\2)))#g' \
  backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionServiceGenericGameTest.java
```
`SessionServiceGenericGameTest` needs `import org.kevinkib.cardgames.sessionmanagement.application.GameFactories;` — it is already in that package, so no import is needed. Verify each edited line compiles in Step 6; if a `BatailleCorseFactory` import is missing in a test that previously referenced it only inline, the inline FQN in the sed output keeps it resolvable.

- [ ] **Step 5: Update the `createGame(...)` call sites to pass a slug**

Only `SessionService` calls change (not the WS controller's own `createGame(payload)` methods). Insert the BatailleCorse slug for the BatailleCorse/session tests, and the fake slug for the genericity test:
```bash
cd "$(git rev-parse --show-toplevel)"
# bataillecorse + generic SessionServiceTest: sessionService./service.createGame(2 ... ) -> slug-first
grep -rlZ -E "(sessionService|service)\.createGame\(2" backend/src/test --include=*.java | xargs -0 -r sed -i \
  -E 's#(sessionService|service)\.createGame\(2#\1.createGame("bataille-corse", 2#g'
# SessionServiceGenericGameTest uses the FakeGameFactory -> fake slug (fix the two it just got wrong above)
sed -i -E 's#sessionService\.createGame\("bataille-corse", 2#sessionService.createGame("fake", 2#g' \
  backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionServiceGenericGameTest.java
```
Confirm no `createGame(2` remains for session calls: `grep -rn -E "(sessionService|service)\.createGame\(2" backend/src/test --include=*.java || echo CLEAN`.

- [ ] **Step 6: Compile + full clean suite**
```bash
mvn -f backend/pom.xml clean test
```
Expected: BUILD SUCCESS, full suite green (prior count + Phase 1–3 additions). Fix any remaining compile error by following the slug-first / `GameFactories`-wrapping pattern; do not change game behaviour. (`DisconnectForfeitServiceTest` and `BatailleCorseRestControllerTest`/`...WebSocketControllerTest` use `(BatailleCorse) sessionService.createGame(2, ...)` — the sed above slug-prefixes them; their casts are unchanged.)

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(session): select game by slug via GameFactories; rematch preserves type"
```

---

## Phase 5 — Selection/rematch tests + final gate + MR

### Task 5.1: Session-level selection + rematch-type tests

**Files:**
- Create: `sessionmanagement/application/SessionServiceGameSelectionTest.java`

- [ ] **Step 1: Write the test**

```java
package org.kevinkib.cardgames.sessionmanagement.application;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse;
import org.kevinkib.cardgames.bataillecorse.domain.BatailleCorseFactory;
import org.kevinkib.cardgames.bullshit.domain.Bullshit;
import org.kevinkib.cardgames.bullshit.domain.BullshitFactory;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.kevinkib.cardgames.sessionmanagement.infrastructure.InMemorySessionRepository;

import java.time.Clock;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;

class SessionServiceGameSelectionTest {

    private final SessionService service = new SessionService(
            new InMemorySessionRepository(Clock.systemUTC()),
            new GameFactories(List.of(new BatailleCorseFactory(), new BullshitFactory())));

    @Test
    void givenBullshitSlug_whenCreateGame_thenHostsABullshit() {
        Game game = service.createGame("bullshit", 3, GameMode.SOLO);

        assertThat(game, instanceOf(Bullshit.class));
        assertThat(service.getGame(game.getId()), instanceOf(Bullshit.class));
    }

    @Test
    void givenBatailleCorseSlug_whenCreateGame_thenHostsABatailleCorse() {
        Game game = service.createGame("bataille-corse", 2, GameMode.SOLO);

        assertThat(game, instanceOf(BatailleCorse.class));
    }

    @Test
    void givenBullshitSession_whenRematch_thenStaysABullshit() {
        Game game = service.createGame("bullshit", 3, GameMode.SOLO);

        Game fresh = service.rematch(game.getId());

        assertThat(fresh, instanceOf(Bullshit.class));
    }
}
```

- [ ] **Step 2: Run, expect PASS** — `mvn -f backend/pom.xml test -Dtest=SessionServiceGameSelectionTest`. This proves the session hosts Bullshit and rematch preserves the game type.

- [ ] **Step 3: Commit**
```bash
git add backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/application/SessionServiceGameSelectionTest.java
git commit -m "test(session): create Bullshit by slug + rematch preserves game type"
```

### Task 5.2: Final gate + MR

- [ ] **Step 1: Full suite** — `mvn -f backend/pom.xml clean test` → BUILD SUCCESS, all green (prior baseline + Bullshit conformance/factory, GameFactories, selection/rematch tests; BatailleCorse unchanged in behaviour).

- [ ] **Step 2: Push and open the MR**

Push the branch and open a merge request titled `feat: Bullshit onto kernel + multi-game selection (Slice 2b-ii-a)`. Body: summarise Bullshit conforming to the kernel (+ `BullshitFactory`), the `gameType()` slug + `GameFactories` resolver, `SessionService`/`SessionGame` selecting and remembering the type, rematch type-preservation; note BatailleCorse plays identically, no presentation/transport changes, full suite green.

---

## Self-review notes

- **Spec coverage:** Bullshit kernel conformance — `BullshitId`→`GameId` (1.1), `PlayerId`→kernel (1.2), `implements Game`+`getPlayerIds` (1.3) ✓; `BullshitFactory` (2.2) ✓; `GameFactory.gameType()` (2.1) ✓; `GameFactories` resolver (3.1) ✓; `SessionService` slug create + `SessionGame.gameType` + rematch-preserve (4.1–4.2) ✓; `AppConfig` both-game wiring (4.2 Step 2) ✓; BatailleCorse `/create` passes slug (4.2 Step 3) ✓; `FakeGameFactory` gains slug (2.1 Step 3) ✓; conformance/selection/rematch tests (1.3, 2.2, 3.1, 5.1) ✓.
- **Out of scope held:** no Bullshit presentation/DTOs/controllers/broadcaster, no generic create endpoint, no claim-mode/variant selection, no frontend.
- **Type consistency:** `GameFactory.{gameType(),create(GameId,int)}`, `GameFactories.factoryFor(String)`, `SessionService(SessionRepository, GameFactories)`, `SessionService.createGame(String, int[, GameMode[, String]])`, `SessionGame.create(GameId, List<PlayerId>, String)` + `gameType()`, slugs `"bataille-corse"`/`"bullshit"`/`"fake"` used consistently across tasks.
- **Migration safety:** Phases 1.1/1.2 are behaviour-preserving scripted renames gated on compile + tests; Bullshit's `forfeit` already matched `Game` (no logic change). BatailleCorse identity untouched (one shared kernel type). The `createGame`/`new SessionService` call-site seds are mechanical and gated on the full clean suite.
- **No BC privilege in the session:** `SessionService` has no BatailleCorse default — every caller passes a slug; the `"bataille-corse"` constant lives on `BatailleCorseFactory`, named by BatailleCorse's own controller/tests.
