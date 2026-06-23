# Host-selectable Bullshit claim mode — design

## Goal

Let the room creator choose the Bullshit claim mode at room creation: default
`AscendingRankClaimMode` (rank) vs `CyclingSuitClaimMode` (suit). Both domain
strategies already exist and work; today nothing selects between them — the
2-arg `Bullshit` constructor hardcodes `new AscendingRankClaimMode()`. A 3-arg
ctor `Bullshit(id, nbPlayers, ClaimMode)` already exists as the injection seam.

## The hard part — and the seam that resolves it

The session core (`sessionmanagement.core`) is deliberately **game-agnostic**
and must never learn about `ClaimMode` (a `bullshit.domain` concept). Yet the
`Game` is instantiated at **start**, not at **create**:

- `SessionService.createRoom(gameType, hostName)` only makes a lobby
  (`SessionGame`); the `Game` does not exist yet.
- `SessionService.startGame(id, hostToken)` instantiates via
  `gameFactories.factoryFor(type).create(id, claimed)`.

So the option chosen at **create** must be **stored on the lobby**
(`SessionGame`) and **read back at start** to pass to the factory — without the
session core importing `ClaimMode`.

### Seam: opaque `GameOptions` map → typed `BullshitOptions` at the Bullshit edge

1. **`GameOptions` — opaque transport** (new value in the game-agnostic `game`
   package). An immutable string-keyed bag the session core stores and forwards
   **without ever reading a key**:

   ```java
   public record GameOptions(Map<String, String> values) {
       private static final GameOptions NONE = new GameOptions(Map.of());
       public GameOptions { values = Map.copyOf(values); }       // immutable + defensive
       public static GameOptions none() { return NONE; }
       public static GameOptions of(Map<String, String> values) {
           return values.isEmpty() ? NONE : new GameOptions(values);
       }
       public Optional<String> get(String key) { return Optional.ofNullable(values.get(key)); }
   }
   ```

   - Each game owns its own key namespace; the core never reads a key.
   - Multiple options later = more map entries; a new game = different keys.
   - Never `null` (`none()` is the empty sentinel); absence of a key means
     "the game's default", decided by the game — no `null` crosses the contract
     carrying business meaning.

2. **`GameFactory` default method — protects BatailleCorse.** The interface
   gains a 3-arg `create` as a **default** that ignores options and delegates to
   the existing 2-arg `create`:

   ```java
   default Game create(GameId id, int nbPlayers, GameOptions options) {
       return create(id, nbPlayers);
   }
   ```

   `BullshitFactory` overrides the 3-arg version. `BatailleCorseFactory` and the
   test `FakeGameFactory` are **untouched** — they inherit the default. Every
   existing `.create(id, n)` call site keeps compiling.

3. **Typed `BullshitOptions` — single translation point** (new value in
   `bullshit.domain.options`). `BullshitFactory` converts the opaque map into a
   typed domain object exactly once; nothing Bullshit-side past this edge touches
   raw strings:

   ```java
   public record BullshitOptions(ClaimModeOption claimMode) {
       static final String CLAIM_MODE_KEY = "claimMode";
       public static final BullshitOptions DEFAULT = new BullshitOptions(ClaimModeOption.RANK);

       public static BullshitOptions from(GameOptions options) {
           return new BullshitOptions(ClaimModeOption.fromKey(options.get(CLAIM_MODE_KEY).orElse(null)));
       }

       public ClaimMode toClaimMode() { return claimMode.create(); }
   }
   ```

4. **`ClaimModeOption` enum — single source of the key↔strategy mapping** (new in
   `bullshit.domain.claim`):

   ```java
   public enum ClaimModeOption {
       RANK("rank", AscendingRankClaimMode::new),
       SUIT("suit", CyclingSuitClaimMode::new);
       // key() -> stable string; create() -> ClaimMode; fromKey(String) -> RANK on null/unknown
   }
   ```

So the flow is: typed + game-specific at the boundary → opaque `Map<String,String>`
through the core → typed `BullshitOptions` again at the Bullshit factory edge.

### Alternatives considered and rejected

- **Raw nullable `String` option** threaded everywhere — minimal, but `null`
  ends up meaning "use default", violating the *no-null-as-a-contract* rule.
- **`GameOptions` wrapping a single `String`** — works for one option, but a
  second option forces each game to invent its own encoding inside that string.
- **Typed marker interface `GameOptions` with per-game impls** held opaquely by
  the core — type-safe, but the core would carry a reference whose only impl
  lives in `bullshit`; awkward for something meaningless to it.

## End-to-end path

### Backend

1. **`game/GameOptions.java`** — new opaque value (above).
2. **`game/GameFactory.java`** — add the 3-arg default `create` (above).
3. **`bullshit/domain/claim/ClaimModeOption.java`** — new enum (above).
4. **`bullshit/domain/options/BullshitOptions.java`** — new typed value (above).
5. **`bullshit/domain/BullshitFactory.java`** — override 3-arg `create`:
   `new Bullshit(id, nbPlayers, BullshitOptions.from(options).toClaimMode())`.
6. **`SessionGame`** — add a `GameOptions options` record component.
   - Existing `create(id, playerIds, gameType)` and `create(id, seatCount, gameType)`
     overloads default `options` to `GameOptions.none()` (keeps all current call
     sites/tests compiling).
   - New overload `create(id, seatCount, gameType, GameOptions options)` for the
     create-room path. Add an `options()` accessor (record gives it for free).
7. **`SessionService`**:
   - `createRoom(gameType, hostName)` kept (delegates with `GameOptions.none()`);
     new `createRoom(gameType, hostName, GameOptions options)` stores options on
     the lobby via the new `SessionGame.create(...)` overload.
   - `startGame` → `factory.create(id, claimed, lobby.options())`.
   - `rematch` → `factory.create(id, session.claimedCount(), session.options())`
     (the session already carries the options, so a rematch keeps the mode).
   - `playAgain` (reopen) → read the old lobby's `options()` **before** dropping
     it, and pass them into the fresh `SessionGame.create(...)` so the chosen
     mode is **preserved on reopen**.
8. **`BullshitCreatePayload`** — add a typed field `String claimMode` (the unused
   `nbPlayers`/`mode` fields are left as-is; out of scope to remove).
9. **`BullshitWebSocketController.createGame`** — assemble the map at the edge:
   when `claimMode` is present, `GameOptions.of(Map.of("claimMode", claimMode))`,
   else `GameOptions.none()`; pass into `createRoom`.
10. **`LobbyView`** — add a generic `Map<String, String> options` field, sourced
    from `lobby.options().values()`. **It exposes the opaque map, not a
    `claimMode` field** — `LobbyView` lives in the game-agnostic core and must not
    know the `"claimMode"` key; the frontend (game-specific) reads
    `options.claimMode`. `LobbyView.forViewer` is the **single** construction
    path, so both the WS lobby broadcast and the GET rehydration endpoint pick it
    up automatically (satisfies the state-rehydration rule). **Rendering is
    deferred** — see Out of scope.

### Frontend

11. **New single-source key module** (e.g. `model/bullshit/claimMode.ts`):
    exports the `'rank'`/`'suit'` keys, the default (`'rank'`), and the labelled
    option list (`By rank (A→K)` / `By suit (♥→♠)`). Keys must match
    `ClaimModeOption`.
12. **`BullshitStartGame.vue`** — add two radio buttons (rank pre-selected);
    `onCreate` passes the selected key to the store.
13. **`Bullshit.store.ts`** — `create(name?, claimMode?)` forwards to the session.
14. **`BullshitSession.ts`** — `create(name?, claimMode?)` publishes
    `{ name, claimMode }` to `/app/bullshit/create`.
15. **`model/bullshit/LobbyView.ts`** — mirror the DTO: add
    `options: Record<string, string>`. Type only this batch; the follow-up reads
    `options.claimMode` when rendering.

## Testing

Backend (Builders/Fixtures; **no Mockito on domain**):

- `ClaimModeOption.fromKey`: `"rank"`→RANK, `"suit"`→SUIT, `null`/unknown→RANK.
- `BullshitOptions.from`: suit key → SUIT; absent/unknown → RANK (`DEFAULT`).
- `BullshitFactory`: `create(id, n, GameOptions.of(Map.of("claimMode","suit")))`
  → `Bullshit` whose `getCurrentTarget()` is `HEART`; `create(id, n)` (default)
  → `ACE`.
- `SessionService` integration (real `SessionService` + `InMemorySessionRepository`
  + `GameFactories`): `createRoom` with the suit option then `startGame` →
  `Bullshit` initial target `HEART`; default `createRoom` → `ACE`.
- Reopen preserves the mode: `createRoom(suit)` → `startGame` → `playAgain` →
  `startGame` → still `HEART`.
- `LobbyView.options` carries the claim-mode entry from the lobby options
  (e.g. `createRoom(suit)` → `lobbyView(...).options()` contains
  `"claimMode" -> "suit"`).

Frontend:

- Extend the `BullshitStartGame` test: default create sends `claimMode: 'rank'`;
  selecting the suit radio sends `claimMode: 'suit'`.

Gates: backend `mvn` suite green + frontend `npm run build`.

## Out of scope (follow-up)

- **Render the chosen claim mode in the lobby** waiting block of
  `BullshitGameScreen.vue`. The data is already exposed via `LobbyView.claimMode`;
  the rendering edit is deferred because `BullshitGameScreen.vue` is owned by a
  concurrent sibling task this batch (DO NOT TOUCH). File a follow-up.
- Removing the unused `nbPlayers`/`mode` fields on `BullshitCreatePayload`.
- Any BatailleCorse changes (the `GameFactory` default method means none are
  needed).
