# Host-selectable Bullshit Claim Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the room creator choose the Bullshit claim mode (rank vs suit) at room creation, threading the choice from the create form through the game-agnostic session core to the Bullshit factory.

**Architecture:** An opaque `GameOptions` string-keyed map is stored on the lobby (`SessionGame`) at create and read back at start; it travels through the session core without the core interpreting any key. `BullshitFactory` translates the map into a typed `BullshitOptions` domain value exactly once at the Bullshit edge, via a `ClaimModeOption` enum that is the single source of the key↔strategy mapping. A `GameFactory` default method keeps BatailleCorse untouched.

**Tech Stack:** Java 17 records + sealed types, JUnit 5 + Hamcrest (no Mockito on domain), Spring WebSocket; Vue 3 `<script setup>` + TypeScript, Pinia, Vitest, Vite.

**Spec:** `docs/superpowers/specs/2026-06-24-bullshit-host-selectable-claim-mode-design.md`

---

## File Structure

**Backend — create:**
- `backend/src/main/java/org/kevinkib/cardgames/game/GameOptions.java` — opaque options bag (game-agnostic transport)
- `backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/claim/ClaimModeOption.java` — key↔ClaimMode enum (single source)
- `backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/options/BullshitOptions.java` — typed Bullshit options value
- Test mirrors under `backend/src/test/...`

**Backend — modify:**
- `game/GameFactory.java` — add 3-arg `create` default
- `bullshit/domain/BullshitFactory.java` — override 3-arg `create`
- `sessionmanagement/core/domain/SessionGame.java` — add `GameOptions options` component + overloads
- `sessionmanagement/core/application/SessionService.java` — thread options through create/start/rematch/playAgain
- `sessionmanagement/core/application/LobbyView.java` — expose opaque `options` map
- `bullshit/presentation/api/BullshitCreatePayload.java` — add `claimMode`
- `bullshit/presentation/BullshitWebSocketController.java` — assemble `GameOptions` at the edge

**Frontend — create:**
- `frontend/src/model/bullshit/claimMode.ts` — single-source keys + labelled options
- `frontend/src/application/BullshitSession.test.ts` — publish-payload test
- `frontend/src/view/bullshit/BullshitStartGame.test.ts` — selector test

**Frontend — modify:**
- `frontend/src/view/bullshit/BullshitStartGame.vue` — radio selector
- `frontend/src/state/Bullshit.store.ts` — `create(name?, claimMode?)`
- `frontend/src/application/BullshitSession.ts` — `create(name?, claimMode?)` payload
- `frontend/src/model/bullshit/LobbyView.ts` — optional `options` field

---

## Task 1: `GameOptions` opaque value

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/game/GameOptions.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/game/GameOptionsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.cardgames.game;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameOptionsTest {

    @Test
    void givenNone_whenGetAnyKey_thenEmpty() {
        assertThat(GameOptions.none().get("claimMode"), is(Optional.empty()));
    }

    @Test
    void givenEmptyMap_whenOf_thenReturnsNoneSingleton() {
        assertThat(GameOptions.of(Map.of()), is(GameOptions.none()));
    }

    @Test
    void givenKey_whenGet_thenValuePresent() {
        GameOptions options = GameOptions.of(Map.of("claimMode", "suit"));
        assertThat(options.get("claimMode"), is(Optional.of("suit")));
    }

    @Test
    void givenConstructed_whenSourceMapMutated_thenOptionsUnchanged() {
        java.util.HashMap<String, String> source = new java.util.HashMap<>();
        source.put("claimMode", "suit");
        GameOptions options = GameOptions.of(source);
        source.put("claimMode", "rank");
        assertThat(options.get("claimMode"), is(Optional.of("suit")));
    }

    @Test
    void givenValues_whenMutateReturnedMap_thenThrows() {
        GameOptions options = GameOptions.of(Map.of("claimMode", "suit"));
        assertThrows(UnsupportedOperationException.class, () -> options.values().put("x", "y"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run the `GameOptionsTest` class via the IntelliJ-bundled Maven (`mvn -Dtest=GameOptionsTest test`).
Expected: FAIL — `GameOptions` does not exist (compilation error).

- [ ] **Step 3: Write minimal implementation**

```java
package org.kevinkib.cardgames.game;

import java.util.Map;
import java.util.Optional;

/** Opaque, game-agnostic creation options. The session core stores and forwards this without
 *  interpreting any key; each game owns its own key namespace and reads only its own keys. */
public record GameOptions(Map<String, String> values) {

    private static final GameOptions NONE = new GameOptions(Map.of());

    public GameOptions {
        values = Map.copyOf(values);
    }

    public static GameOptions none() {
        return NONE;
    }

    public static GameOptions of(Map<String, String> values) {
        return values.isEmpty() ? NONE : new GameOptions(values);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=GameOptionsTest test`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/game/GameOptions.java \
        backend/src/test/java/org/kevinkib/cardgames/game/GameOptionsTest.java
git commit -m "feat(game): add opaque GameOptions value for game-agnostic creation options"
```

---

## Task 2: `GameFactory` 3-arg default method

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/game/GameFactory.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/GameFactoriesTest.java`

- [ ] **Step 1: Write the failing test**

Append this test inside the existing `GameFactoriesTest` class (it proves a factory that does NOT override the 3-arg `create` still works via the default — BatailleCorse is untouched):

```java
    @Test
    void givenFactoryWithoutOptionsOverride_whenCreateWithOptions_thenDelegatesToTwoArg() {
        GameFactories factories = new GameFactories(java.util.List.of(new BatailleCorseFactory()));

        var game = factories.factoryFor("bataille-corse")
                .create(GameId.generate(), 2, org.kevinkib.cardgames.game.GameOptions.none());

        assertThat(game, instanceOf(org.kevinkib.cardgames.bataillecorse.domain.BatailleCorse.class));
    }
```

If `instanceOf`/imports are missing, add `import static org.hamcrest.Matchers.instanceOf;` and `import org.kevinkib.cardgames.game.GameId;` (check the file's existing imports first and only add what's absent).

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=GameFactoriesTest test`
Expected: FAIL — `create(GameId, int, GameOptions)` does not exist (compilation error).

- [ ] **Step 3: Add the default method to `GameFactory`**

In `GameFactory.java`, add the import and the default method:

```java
package org.kevinkib.cardgames.game;

public interface GameFactory {

    /** Stable identifier used to select this game when creating a session. */
    String gameType();

    /** Fewest players the game can start with. */
    int minPlayers();

    /** Most players the game can seat. */
    int maxPlayers();

    Game create(GameId id, int nbPlayers);

    /** Creates the game with host-selected options. Games that ignore options inherit this default. */
    default Game create(GameId id, int nbPlayers, GameOptions options) {
        return create(id, nbPlayers);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=GameFactoriesTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/game/GameFactory.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/GameFactoriesTest.java
git commit -m "feat(game): add GameFactory.create(id, n, options) default that ignores options"
```

---

## Task 3: `ClaimModeOption` enum (single source of key↔strategy)

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/claim/ClaimModeOption.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/claim/ClaimModeOptionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.cardgames.bullshit.domain.claim;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ClaimModeOptionTest {

    @Test
    void givenRankKey_whenFromKey_thenRank() {
        assertThat(ClaimModeOption.fromKey("rank"), is(ClaimModeOption.RANK));
    }

    @Test
    void givenSuitKey_whenFromKey_thenSuit() {
        assertThat(ClaimModeOption.fromKey("suit"), is(ClaimModeOption.SUIT));
    }

    @Test
    void givenNull_whenFromKey_thenRankDefault() {
        assertThat(ClaimModeOption.fromKey(null), is(ClaimModeOption.RANK));
    }

    @Test
    void givenUnknownKey_whenFromKey_thenRankDefault() {
        assertThat(ClaimModeOption.fromKey("bogus"), is(ClaimModeOption.RANK));
    }

    @Test
    void givenSuit_whenCreate_thenInitialTargetIsHeart() {
        assertThat(ClaimModeOption.SUIT.create().initial(), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenRank_whenCreate_thenInitialTargetIsAce() {
        assertThat(ClaimModeOption.RANK.create().initial(), is(new RankTarget(FrenchRank.ACE)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=ClaimModeOptionTest test`
Expected: FAIL — `ClaimModeOption` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package org.kevinkib.cardgames.bullshit.domain.claim;

import java.util.function.Supplier;

/** The single source of truth mapping a stable claim-mode key to its {@link ClaimMode} strategy. */
public enum ClaimModeOption {

    RANK("rank", AscendingRankClaimMode::new),
    SUIT("suit", CyclingSuitClaimMode::new);

    private final String key;
    private final Supplier<ClaimMode> factory;

    ClaimModeOption(String key, Supplier<ClaimMode> factory) {
        this.key = key;
        this.factory = factory;
    }

    public String key() {
        return key;
    }

    public ClaimMode create() {
        return factory.get();
    }

    /** Resolves a key to its option; unknown or {@code null} keys fall back to {@link #RANK}. */
    public static ClaimModeOption fromKey(String key) {
        for (ClaimModeOption option : values()) {
            if (option.key.equals(key)) {
                return option;
            }
        }
        return RANK;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=ClaimModeOptionTest test`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/claim/ClaimModeOption.java \
        backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/claim/ClaimModeOptionTest.java
git commit -m "feat(bullshit): add ClaimModeOption enum as single-source key to ClaimMode mapping"
```

---

## Task 4: `BullshitOptions` typed value

**Files:**
- Create: `backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/options/BullshitOptions.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/options/BullshitOptionsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.kevinkib.cardgames.bullshit.domain.options;

import org.junit.jupiter.api.Test;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.game.GameOptions;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class BullshitOptionsTest {

    @Test
    void givenSuitKey_whenFrom_thenSuitClaimMode() {
        BullshitOptions options = BullshitOptions.from(GameOptions.of(Map.of("claimMode", "suit")));
        assertThat(options.claimMode(), is(ClaimModeOption.SUIT));
    }

    @Test
    void givenNoOptions_whenFrom_thenRankDefault() {
        BullshitOptions options = BullshitOptions.from(GameOptions.none());
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
    }

    @Test
    void givenUnknownKey_whenFrom_thenRankDefault() {
        BullshitOptions options = BullshitOptions.from(GameOptions.of(Map.of("claimMode", "bogus")));
        assertThat(options.claimMode(), is(ClaimModeOption.RANK));
    }

    @Test
    void givenSuit_whenToClaimMode_thenInitialTargetIsHeart() {
        BullshitOptions options = new BullshitOptions(ClaimModeOption.SUIT);
        assertThat(options.toClaimMode().initial().label(), is("HEART"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=BullshitOptionsTest test`
Expected: FAIL — `BullshitOptions` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package org.kevinkib.cardgames.bullshit.domain.options;

import org.kevinkib.cardgames.bullshit.domain.claim.ClaimMode;
import org.kevinkib.cardgames.bullshit.domain.claim.ClaimModeOption;
import org.kevinkib.cardgames.game.GameOptions;

/** Typed Bullshit creation options, parsed once from the opaque {@link GameOptions} map at the
 *  Bullshit edge. Future options extend this record and its {@link #from} parsing. */
public record BullshitOptions(ClaimModeOption claimMode) {

    static final String CLAIM_MODE_KEY = "claimMode";

    public static final BullshitOptions DEFAULT = new BullshitOptions(ClaimModeOption.RANK);

    public static BullshitOptions from(GameOptions options) {
        return new BullshitOptions(ClaimModeOption.fromKey(options.get(CLAIM_MODE_KEY).orElse(null)));
    }

    public ClaimMode toClaimMode() {
        return claimMode.create();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=BullshitOptionsTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/options/BullshitOptions.java \
        backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/options/BullshitOptionsTest.java
git commit -m "feat(bullshit): add typed BullshitOptions parsed from opaque GameOptions"
```

---

## Task 5: `BullshitFactory` honours options

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactory.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactoryTest.java`

- [ ] **Step 1: Write the failing test**

Append to the existing `BullshitFactoryTest` class. Add these imports if absent:
`import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;`,
`import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;`,
`import org.kevinkib.cardgames.game.GameOptions;`,
`import org.kevinkib.cards.domain.deck.french.FrenchRank;`,
`import org.kevinkib.cards.domain.deck.french.FrenchSuit;`,
`import java.util.Map;`.

```java
    @Test
    void givenSuitOption_whenCreate_thenInitialTargetIsHeart() {
        Bullshit game = (Bullshit) new BullshitFactory()
                .create(GameId.generate(), 3, GameOptions.of(Map.of("claimMode", "suit")));

        assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));
    }

    @Test
    void givenNoOptions_whenCreate_thenInitialTargetIsAce() {
        Bullshit game = (Bullshit) new BullshitFactory().create(GameId.generate(), 3);

        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=BullshitFactoryTest test`
Expected: FAIL — the suit test sees an `AscendingRankClaimMode` target (`ACE`), not `HEART` (3-arg `create` still uses the inherited default).

- [ ] **Step 3: Override the 3-arg `create`**

Replace the body of `BullshitFactory.java` with (keep the existing 2-arg `create` for the default-rank path):

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cardgames.bullshit.domain.options.BullshitOptions;
import org.kevinkib.cardgames.game.Game;
import org.kevinkib.cardgames.game.GameFactory;
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.game.GameOptions;

public class BullshitFactory implements GameFactory {

    public static final String GAME_TYPE = "bullshit";

    @Override
    public String gameType() {
        return GAME_TYPE;
    }

    @Override
    public int minPlayers() {
        return 2;
    }

    @Override
    public int maxPlayers() {
        return 6;
    }

    @Override
    public Game create(GameId id, int nbPlayers) {
        return new Bullshit(id, nbPlayers);
    }

    @Override
    public Game create(GameId id, int nbPlayers, GameOptions options) {
        return new Bullshit(id, nbPlayers, BullshitOptions.from(options).toClaimMode());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=BullshitFactoryTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactory.java \
        backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitFactoryTest.java
git commit -m "feat(bullshit): BullshitFactory builds claim mode from GameOptions"
```

---

## Task 6: `SessionGame` carries `GameOptions`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SessionGame.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SessionGameTest.java`

- [ ] **Step 1: Write the failing test**

Append to `SessionGameTest`. Add imports if absent: `import org.kevinkib.cardgames.game.GameOptions;`, `import java.util.Map;`.

```java
    @Test
    void givenNoOptionsOverload_whenCreate_thenOptionsAreNone() {
        var sessionGame = SessionGame.create(GameId.generate(), 2, "bullshit");

        assertThat(sessionGame.options(), is(GameOptions.none()));
    }

    @Test
    void givenOptions_whenCreate_thenOptionsRetained() {
        GameOptions options = GameOptions.of(Map.of("claimMode", "suit"));

        var sessionGame = SessionGame.create(GameId.generate(), 2, "bullshit", options);

        assertThat(sessionGame.options(), is(options));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SessionGameTest test`
Expected: FAIL — `options()` accessor and 4-arg `create` do not exist.

- [ ] **Step 3: Add the `options` component and overloads**

Edit `SessionGame.java`. Change the record header and the two `create` factories (the canonical constructor is record-generated; only the static factories call it):

```java
public record SessionGame(GameId id, String gameType, GameOptions options, Map<PlayerId, SessionPlayer> players) {

    public static final PlayerId HOST_SEAT = new PlayerId(0);

    public static SessionGame create(GameId id, List<PlayerId> playerIds, String gameType) {
        return create(id, playerIds, gameType, GameOptions.none());
    }

    public static SessionGame create(GameId id, List<PlayerId> playerIds, String gameType, GameOptions options) {
        Map<PlayerId, SessionPlayer> seats = new LinkedHashMap<>();
        for (PlayerId playerId : playerIds) {
            seats.put(playerId, new SessionPlayer(playerId, SessionToken.generate()));
        }
        return new SessionGame(id, gameType, options, seats);
    }

    /** Creates a session with {@code seatCount} seats numbered 0..seatCount-1. */
    public static SessionGame create(GameId id, int seatCount, String gameType) {
        return create(id, seatCount, gameType, GameOptions.none());
    }

    public static SessionGame create(GameId id, int seatCount, String gameType, GameOptions options) {
        return create(id, IntStream.range(0, seatCount).mapToObj(PlayerId::new).toList(), gameType, options);
    }
```

Add the import `import org.kevinkib.cardgames.game.GameOptions;` near the other `game.*` imports. Leave the rest of the file unchanged.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -Dtest=SessionGameTest test`
Expected: PASS (including all pre-existing `SessionGame` tests, which use the 3-arg `create` overloads).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SessionGame.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/domain/SessionGameTest.java
git commit -m "feat(session): SessionGame carries opaque GameOptions"
```

---

## Task 7: `SessionService` threads options through create/start/rematch/playAgain

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionService.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionServiceTest.java`

- [ ] **Step 1: Write the failing test**

Add a new nested class inside `SessionServiceTest`. It uses a service wired with the real `BullshitFactory`. Add imports if absent:
`import org.kevinkib.cardgames.bullshit.domain.Bullshit;`,
`import org.kevinkib.cardgames.bullshit.domain.claim.RankTarget;`,
`import org.kevinkib.cardgames.bullshit.domain.claim.SuitTarget;`,
`import org.kevinkib.cardgames.game.GameId;`,
`import org.kevinkib.cardgames.game.GameOptions;`,
`import org.kevinkib.cards.domain.deck.french.FrenchRank;`,
`import org.kevinkib.cards.domain.deck.french.FrenchSuit;`,
`import java.util.Map;`.

```java
    @Nested
    class ClaimModeOptionTest {

        private SessionService bullshitService;

        @BeforeEach
        void setUpBullshit() {
            bullshitService = new SessionService(
                    new InMemorySessionRepository(Clock.systemUTC()),
                    new GameFactories(List.of(new BullshitFactory())));
        }

        private GameId startWithTwoPlayers(GameOptions options) {
            RoomCreated room = bullshitService.createRoom("bullshit", "Alice", options);
            GameId id = new GameId(room.gameId());
            bullshitService.joinRoom(id, "Bob");
            bullshitService.startGame(id, room.hostToken());
            return id;
        }

        @Test
        void givenSuitOption_whenStart_thenInitialTargetIsHeart() {
            GameId id = startWithTwoPlayers(GameOptions.of(Map.of("claimMode", "suit")));

            Bullshit game = (Bullshit) bullshitService.getGame(id);
            assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));
        }

        @Test
        void givenNoOption_whenStart_thenInitialTargetIsAce() {
            GameId id = startWithTwoPlayers(GameOptions.none());

            Bullshit game = (Bullshit) bullshitService.getGame(id);
            assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
        }

        @Test
        void givenSuitOption_whenReopenedAndRestarted_thenStillHeart() {
            RoomCreated room = bullshitService.createRoom(
                    "bullshit", "Alice", GameOptions.of(Map.of("claimMode", "suit")));
            GameId id = new GameId(room.gameId());
            bullshitService.joinRoom(id, "Bob");
            bullshitService.startGame(id, room.hostToken());

            // Reopen the room (drops the game, resets the lobby) then start again.
            bullshitService.playAgain(id, "Alice");
            bullshitService.joinRoom(id, "Bob");
            String hostToken = bullshitService.tokenForSeat(id, new PlayerId(0));
            bullshitService.startGame(id, hostToken);

            Bullshit game = (Bullshit) bullshitService.getGame(id);
            assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.HEART)));
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=SessionServiceTest test`
Expected: FAIL — `createRoom(String, String, GameOptions)` does not exist (compilation error).

- [ ] **Step 3: Thread options through `SessionService`**

In `SessionService.java`:

a) Add the import `import org.kevinkib.cardgames.game.GameOptions;`.

b) Replace `createRoom` with a 2-arg overload that delegates and a 3-arg that stores options:

```java
    public RoomCreated createRoom(String gameType, String hostName) {
        return createRoom(gameType, hostName, GameOptions.none());
    }

    public RoomCreated createRoom(String gameType, String hostName, GameOptions options) {
        GameId id = GameId.generate();
        SessionGame lobby = SessionGame.create(id, gameFactories.maxPlayers(gameType), gameType, options);
        lobby.claimHost(hostName);
        repository.saveLobby(lobby);
        SessionToken hostToken = lobby.findTokenByPlayer(new PlayerId(0))
                .orElseThrow(() -> new IllegalStateException("Host seat has no token"));
        return new RoomCreated(id.uuid().toString(), hostToken.uuid().toString());
    }
```

c) In `startGame`, pass the lobby options to the factory:

```java
        Game game = gameFactories.factoryFor(lobby.gameType()).create(id, claimed, lobby.options());
```

d) In `rematch`, pass the session options:

```java
        Game fresh = gameFactories.factoryFor(session.gameType()).create(id, session.claimedCount(), session.options());
```

e) In `playAgain`, preserve options across reopen. Replace the method body's reopen branch:

```java
    public synchronized JoinResult playAgain(GameId id, String name) {
        SessionGame existing = repository.loadSessionGame(id);
        String type = existing.gameType();
        GameOptions options = existing.options();
        if (repository.findGame(id).isPresent()) {
            repository.remove(id);
            repository.saveLobby(SessionGame.create(id, gameFactories.maxPlayers(type), type, options));
        }
        return joinRoom(id, name);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -Dtest=SessionServiceTest test`
Expected: PASS (new nested class + all pre-existing tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionService.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/SessionServiceTest.java
git commit -m "feat(session): thread GameOptions through create, start, rematch, and reopen"
```

---

## Task 8: `LobbyView` exposes the opaque options map

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/LobbyView.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/LobbyViewTest.java`

- [ ] **Step 1: Write the failing test**

Append to `LobbyViewTest`. Add imports if absent: `import org.kevinkib.cardgames.game.GameOptions;`, `import java.util.Map;`.

```java
    @Test
    void givenLobbyWithSuitOption_whenForViewer_thenOptionsCarryClaimMode() {
        SessionGame lobby = SessionGame.create(
                GameId.generate(), 2, "bullshit", GameOptions.of(Map.of("claimMode", "suit")));

        LobbyView view = LobbyView.forViewer(lobby, 2, 6, new PlayerId(0));

        assertThat(view.options(), is(Map.of("claimMode", "suit")));
    }
```

If the existing test file references seats via a different `create` overload, keep using whatever overload it already uses for its other tests; only this new test needs the 4-arg form.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=LobbyViewTest test`
Expected: FAIL — `options()` does not exist on `LobbyView`.

- [ ] **Step 3: Add the `options` field**

Edit `LobbyView.java`. Add `Map<String, String> options` as the last record component and populate it from `lobby.options().values()`:

```java
import java.util.Map;
```

```java
public record LobbyView(
        boolean started,
        String gameId,
        List<LobbyPlayer> players,
        int hostSeat,
        int mySeat,
        int minPlayers,
        int maxPlayers,
        boolean canStart,
        Map<String, String> options) {

    public record LobbyPlayer(int seat, String name, boolean joined) {
    }

    static LobbyView forViewer(SessionGame lobby, int minPlayers, int maxPlayers, PlayerId viewer) {
        List<LobbyPlayer> players = lobby.seats().stream()
                .map(seat -> new LobbyPlayer(seat.id().id(), seat.name(), seat.isClaimed()))
                .toList();

        boolean canStart = lobby.isHost(viewer) && lobby.claimedCount() >= minPlayers;

        return new LobbyView(
                false,
                lobby.id().uuid().toString(),
                players,
                SessionGame.HOST_SEAT.id(),
                viewer.id(),
                minPlayers,
                maxPlayers,
                canStart,
                lobby.options().values());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -Dtest=LobbyViewTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/sessionmanagement/core/application/LobbyView.java \
        backend/src/test/java/org/kevinkib/cardgames/sessionmanagement/core/application/LobbyViewTest.java
git commit -m "feat(session): expose opaque options map on LobbyView (no key leak into core)"
```

---

## Task 9: Create payload + WebSocket controller wiring

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/api/BullshitCreatePayload.java`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketController.java`

No new unit test: the controller method has no return value to assert on and requires Spring messaging collaborators; its behaviour (suit → HEART) is already covered end-to-end by Task 7's `SessionService` tests, and compilation is covered by the full build (Task 13). This is an intentional, logged scope choice — the mapping below is a one-line translation.

- [ ] **Step 1: Add `claimMode` to the payload**

Edit `BullshitCreatePayload.java`:

```java
package org.kevinkib.cardgames.bullshit.presentation.api;

import org.kevinkib.cardgames.sessionmanagement.core.application.GameMode;

public record BullshitCreatePayload(Integer nbPlayers, GameMode mode, String name, String claimMode) {
}
```

- [ ] **Step 2: Assemble `GameOptions` in the controller**

In `BullshitWebSocketController.java`, add the import:

```java
import org.kevinkib.cardgames.game.GameOptions;
```

Replace the body of `createGame`. The controller owns the wire contract and writes the `"claimMode"` key literally once; `BullshitOptions.CLAIM_MODE_KEY` (Task 4) is the matching read-key, and the Task 7 integration test proves the round trip:

```java
    @MessageMapping("/bullshit/create")
    @SendTo("/topic/game")
    public Response createGame(@Payload(required = false) BullshitCreatePayload payload) {
        String name = (payload != null) ? payload.name() : null;
        String claimMode = (payload != null) ? payload.claimMode() : null;
        GameOptions options = (claimMode != null)
                ? GameOptions.of(Map.of("claimMode", claimMode))
                : GameOptions.none();
        RoomCreated room = sessionService.createRoom(BullshitFactory.GAME_TYPE, name, options);
        Map<Integer, String> tokens = Map.of(0, room.hostToken());

        return new SuccessResponse(
                LifecycleEventType.CREATE.toString(),
                new BullshitCreateEventData(room.gameId(), BullshitFactory.GAME_TYPE, tokens),
                "Room created",
                null);
    }
```

- [ ] **Step 3: Compile-check**

Run: `mvn -o -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/api/BullshitCreatePayload.java \
        backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketController.java
git commit -m "feat(bullshit): accept claimMode on create and forward as GameOptions"
```

---

## Task 10: Frontend single-source claim-mode keys

**Files:**
- Create: `frontend/src/model/bullshit/claimMode.ts`

- [ ] **Step 1: Create the module**

```typescript
export const CLAIM_MODE_RANK = 'rank';
export const CLAIM_MODE_SUIT = 'suit';

export type ClaimMode = typeof CLAIM_MODE_RANK | typeof CLAIM_MODE_SUIT;

export const DEFAULT_CLAIM_MODE: ClaimMode = CLAIM_MODE_RANK;

export interface ClaimModeOption {
  key: ClaimMode;
  label: string;
}

export const CLAIM_MODE_OPTIONS: ClaimModeOption[] = [
  { key: CLAIM_MODE_RANK, label: 'By rank (A→K)' },
  { key: CLAIM_MODE_SUIT, label: 'By suit (♥→♠)' },
];
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/model/bullshit/claimMode.ts
git commit -m "feat(frontend): single-source Bullshit claim-mode keys and labels"
```

---

## Task 11: Session + store carry the claim mode

**Files:**
- Modify: `frontend/src/application/BullshitSession.ts`
- Modify: `frontend/src/state/Bullshit.store.ts`
- Test: `frontend/src/application/BullshitSession.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, it, expect, vi } from 'vitest';
import BullshitSession from './BullshitSession';
import type { BullshitWebSocketPort } from './BullshitSession';

function fakePort(): BullshitWebSocketPort & { publish: ReturnType<typeof vi.fn> } {
  return {
    publish: vi.fn(),
    subscribeToSeat: vi.fn(),
    setLobbyListener: vi.fn(),
    setPresence: vi.fn(),
  };
}

describe('BullshitSession.create', () => {
  it('publishes the chosen claim mode in the create payload', () => {
    const port = fakePort();
    const session = new BullshitSession(port, { onEvent: () => {} });

    session.create('Alice', 'suit');

    expect(port.publish).toHaveBeenCalledWith(
      '/app/bullshit/create',
      JSON.stringify({ name: 'Alice', claimMode: 'suit' }),
    );
  });

  it('defaults the claim mode to rank when omitted', () => {
    const port = fakePort();
    const session = new BullshitSession(port, { onEvent: () => {} });

    session.create();

    expect(port.publish).toHaveBeenCalledWith(
      '/app/bullshit/create',
      JSON.stringify({ name: null, claimMode: 'rank' }),
    );
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/application/BullshitSession.test.ts`
Expected: FAIL — payload lacks `claimMode`.

- [ ] **Step 3: Update `BullshitSession.create`**

In `BullshitSession.ts`, add the import and update `create`:

```typescript
import { DEFAULT_CLAIM_MODE, type ClaimMode } from '../model/bullshit/claimMode';
```

```typescript
  create(name?: string, claimMode: ClaimMode = DEFAULT_CLAIM_MODE): void {
    this.pendingCreate = true;
    this.webSocket.setLobbyListener(r => this.onLobby(r));
    this.webSocket.publish('/app/bullshit/create', JSON.stringify({ name: name ?? null, claimMode }));
  }
```

- [ ] **Step 4: Update the store passthrough**

In `Bullshit.store.ts`, add the import and update the `create` action in the returned object:

```typescript
import { type ClaimMode } from '../model/bullshit/claimMode';
```

```typescript
    create: (name?: string, claimMode?: ClaimMode) => session.create(name, claimMode),
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/application/BullshitSession.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add frontend/src/application/BullshitSession.ts \
        frontend/src/state/Bullshit.store.ts \
        frontend/src/application/BullshitSession.test.ts
git commit -m "feat(frontend): carry claim mode through session and store create"
```

---

## Task 12: Create form selector

**Files:**
- Modify: `frontend/src/view/bullshit/BullshitStartGame.vue`
- Test: `frontend/src/view/bullshit/BullshitStartGame.test.ts`

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import BullshitStartGame from './BullshitStartGame.vue';
import { useBullshitStore } from '../../state/Bullshit.store';

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'bullshit-create', component: { template: '<div/>' } },
    { path: '/join/:id?', name: 'bullshit-join', component: { template: '<div/>' } },
    { path: '/games/bullshit/room/:id', component: { template: '<div/>' } },
  ],
});

async function mountCreate() {
  await router.push('/');
  await router.isReady();
  return mount(BullshitStartGame, { global: { plugins: [router] } });
}

describe('BullshitStartGame claim mode', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('creates with rank by default', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.find('button.primary').trigger('click');

    expect(create).toHaveBeenCalledWith('Alice', 'rank');
  });

  it('creates with suit when the suit radio is selected', async () => {
    const wrapper = await mountCreate();
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockImplementation(() => {});

    await wrapper.find('input[type="text"]').setValue('Alice');
    await wrapper.find('input[type="radio"][value="suit"]').setValue();
    await wrapper.find('button.primary').trigger('click');

    expect(create).toHaveBeenCalledWith('Alice', 'suit');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitStartGame.test.ts`
Expected: FAIL — no radio inputs; `create` called with one argument.

- [ ] **Step 3: Add the selector to the component**

Edit `BullshitStartGame.vue`. Update the script and template:

```vue
<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBullshitStore } from '../../state/Bullshit.store';
import { CLAIM_MODE_OPTIONS, DEFAULT_CLAIM_MODE, type ClaimMode } from '../../model/bullshit/claimMode';

const route = useRoute();
const router = useRouter();
const store = useBullshitStore();

const name = ref('');
const claimMode = ref<ClaimMode>(DEFAULT_CLAIM_MODE);
const joinId = ref((route.params.id as string) ?? '');
const isJoin = ref(route.name === 'bullshit-join');

watch(() => store.gameId, (id) => {
  if (id) router.push(`/games/bullshit/room/${id}`);
});

function onCreate() {
  store.create(name.value || undefined, claimMode.value);
}

async function onJoin() {
  await store.join(joinId.value, name.value || undefined);
  router.push(`/games/bullshit/room/${joinId.value}`);
}
</script>

<template>
  <div class="start">
    <h1>Bullshit</h1>
    <label>Your name <input v-model="name" type="text" /></label>

    <template v-if="!isJoin">
      <fieldset class="claim-mode">
        <legend>Claim mode</legend>
        <label v-for="option in CLAIM_MODE_OPTIONS" :key="option.key">
          <input v-model="claimMode" type="radio" name="claimMode" :value="option.key" />
          {{ option.label }}
        </label>
      </fieldset>
      <button type="button" class="btn primary" @click="onCreate">Create game</button>
    </template>
    <template v-else>
      <label>Game ID <input v-model="joinId" type="text" /></label>
      <button type="button" class="btn primary" :disabled="!joinId" @click="onJoin">Join game</button>
    </template>
  </div>
</template>

<style scoped>
.start { display: flex; flex-direction: column; gap: 1rem; padding: 2rem; max-width: 28rem; margin: 0 auto; }
.claim-mode { display: flex; flex-direction: column; gap: 0.4rem; border: 1px solid var(--p-primary-color); border-radius: 0.5rem; padding: 0.75rem 1rem; }
.claim-mode legend { padding: 0 0.4rem; }
.claim-mode label { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; }
.btn { padding: 0.6rem 1.4rem; border-radius: 0.5rem; border: 1px solid var(--p-primary-color); font-size: 1rem; cursor: pointer; }
.btn.primary { background: var(--p-primary-color); color: var(--p-primary-contrast-color, #fff); }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
</style>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitStartGame.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/view/bullshit/BullshitStartGame.vue \
        frontend/src/view/bullshit/BullshitStartGame.test.ts
git commit -m "feat(frontend): claim-mode radio selector on Bullshit create form"
```

---

## Task 13: Mirror the options field on the frontend LobbyView type

**Files:**
- Modify: `frontend/src/model/bullshit/LobbyView.ts`

The field is optional so the sibling-owned `BullshitGameScreen.test.ts` `lobbyView()` factory keeps compiling without edits. Rendering is a deferred follow-up.

- [ ] **Step 1: Add the optional field**

Edit `LobbyView.ts`:

```typescript
export interface LobbyView {
  started: false;
  gameId: string;
  players: LobbyPlayer[];
  hostSeat: number;
  mySeat: number;
  minPlayers: number;
  maxPlayers: number;
  canStart: boolean;
  options?: Record<string, string>;
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/model/bullshit/LobbyView.ts
git commit -m "feat(frontend): mirror opaque options map on LobbyView type"
```

---

## Task 14: Full verification gates

**Files:** none (verification only)

- [ ] **Step 1: Run the full backend suite**

Run the entire backend test suite via the IntelliJ-bundled Maven (`mvn test`). Do NOT use `./mvnw` (no wrapper exists in this repo).
Expected: BUILD SUCCESS, zero failures.

- [ ] **Step 2: Run the full frontend test suite**

Run: `cd frontend && npx vitest run`
Expected: all test files pass (new `BullshitSession` + `BullshitStartGame` tests plus pre-existing).

- [ ] **Step 3: Run the frontend build gate**

Run: `cd frontend && npm run build`
Expected: vite build succeeds with no type errors. (This is the real type-check gate — a bare `vue-tsc` can give a false pass; `node_modules` must be installed in this worktree first via `npm install` if absent.)

- [ ] **Step 4: Final commit (if any incidental fixes were needed)**

```bash
git add -A
git commit -m "test: green backend suite + frontend build for host-selectable claim mode"
```

---

## Self-Review Notes

- **Spec coverage:** `GameOptions` (T1), `GameFactory` default (T2), `ClaimModeOption` single-source (T3), `BullshitOptions` typed edge (T4), factory honours options (T5), `SessionGame` storage (T6), service threading incl. reopen-preserve and rematch (T7), `LobbyView` opaque map exposure (T8), payload + controller (T9), frontend single-source (T10), session/store (T11), selector + test (T12), frontend type mirror (T13), gates (T14). All spec sections mapped.
- **No-leak invariant:** the core (`SessionGame`, `SessionService`, `LobbyView`) only ever touches `GameOptions`/`Map<String,String>`; the string key `"claimMode"` is read only inside `BullshitOptions` and written only at the controller edge. The Task 7 integration test proves the write-key/read-key round trip.
- **BatailleCorse untouched:** confirmed via the `GameFactory` default method; T2 test exercises the inherited path. No BatailleCorse source or test files are modified.
- **DO NOT TOUCH respected:** `BullshitGameScreen.vue` and its test are not modified; the optional `options?` field (T13) avoids forcing edits to the sibling-owned `lobbyView()` factory.
