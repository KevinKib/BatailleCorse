# Shared entry menu and lobby for Bataille Corse and Bullshit (issue #122)

Status: draft for validation. Classification: architectural (it changes the Bataille Corse session
model, adds a kernel concept and touches both frontends). Items marked **[proposed]** are design
decisions the product owner has not confirmed yet; the open questions are listed at the end.

## 1. Problem

Playtest 2 asked to make the two games feel like one product. The owner's request, for the part
this spec covers:

- The entry menu ("New game / Join game") should be one component: no difference justifies two.
- The Bullshit lobby is easy to adapt to Bataille Corse. A seat can be filled by a bot (with a
  difficulty) or by a player invited with a link.
- Room options must be editable in the lobby. Where player options should live is undecided.

Everything else in #122 (end screens, buttons, player tags) is out of scope here (see section 12).

## 2. Current state (verified in the code at `d0c2631`)

### 2.1 What each game does today

| Aspect | Bataille Corse | Bullshit | Real reason for the difference? |
|---|---|---|---|
| Entry from the picker | `/games/bataillecorse` = `LobbyView.vue` ("New Game" / "Join Game" / back), then `StartGame.vue` | `/games/bullshit/create` directly = `BullshitStartGame.vue`; "join" only through an invite link or the same page in join mode | None. Bullshit simply never got the menu page. A player who holds a game id has no button to enter it. |
| Component | `view/alpha/LobbyView.vue` (misnamed: it is the menu) and `view/alpha/StartGame.vue` (create and join in one, switched by the route name) | `view/bullshit/BullshitStartGame.vue` (create and join in one, switched by the route name) | None. Same layout and CSS copied twice. |
| Texts | Hard-coded English in templates | `bullshitUi.start.*` locale keys | None. The project rule is locale keys. |
| Create | `store.create('solo' or 'multiplayer', name)`: WebSocket `/create`, game dealt immediately | `store.create(name, claimMode, deckSize)`: WebSocket `/bullshit/create`, creates a room (lobby), nothing dealt | **Real, but accidental**: BC predates the room model. Bullshit uses `SessionService.createRoom` / `startGame`; BC uses `createGame` with `GameMode`. |
| Opponent choice | On the create page: Human or Computer toggle, plus a 9-tier difficulty slider ("Training" to "Legend") | In the lobby: host adds or removes bots one by one; no difficulty | Different moment, same need. The lobby is the right place (the host sees who is there). |
| Joining | `POST /api/game/{id}/join` claims fixed seat 1 (`SessionService.joinGame`); on the game screen, `WaitingOverlay` shows the invite link until seat 1 is claimed | `POST /api/bullshit/game/{id}/join` claims the next free seat (`joinRoom`) in a lobby, refuses when started or full | Accidental. BC's 2-seat game is the n=2 case of the room model. |
| Lobby / waiting | No lobby: the game exists and is playable-looking while `WaitingOverlay` covers it | `BullshitLobby.vue`: seat list with You / Host / Bot badges, invite link with Copy, host-only Start and bot controls, read-only options | The lobby is the better design; BC should adopt it. |
| Start | Automatic (solo: immediately; multiplayer: when seat 1 joins) | Host presses Start (min 2 players) | Accidental. A host-started game also lets the host change options after the guest arrives. |
| Invite link | `WaitingOverlay.vue` (Copy button, "Copied!") | `BullshitLobby.vue` (Copy button, `aria-live` status, select-all fallback when the clipboard is unavailable) | None. Keep the Bullshit one (more accessible), delete `WaitingOverlay`. |
| Bots | **Client-side** `model/ai/AI.ts`: the creator's browser plays seat 1 with a timer (`reactionTime` from `DIFFICULTY[settingsStore.difficulty]`). In SOLO the creator holds every seat token. Difficulty = reaction time only (2100 ms to 400 ms). The AI never makes a slap mistake. | **Server-side** `BullshitBotCoordinator` + `ProbabilisticBotStrategy` + `BotTuning.DEFAULT`; bot seats are `SessionPlayer.bot` and their tokens never leave the backend. One level only (spec `2026-10-10-bullshit-bots-design.md` explicitly left levels out) | Accidental. The server-side design is the one that keeps the session rules (no token leaks, forfeit, rematch). |
| Game options | None. Slap rules (`SlapRules.DEFAULT`) and penalty (`PutCardsUnderPile(2)`) are hard-coded in `BatailleCorse.initializeData()`. `BatailleCorseFactory` ignores `GameOptions` | `claimMode` (rank / suit cycle / ...) and `deckSize` (32 / 52), chosen on the create page, stored in `SessionGame.options`, shown read-only in the lobby, kept on rematch, parsed by `BullshitOptions` | Real: BC has no option yet. Not real: *where* they are chosen. |
| Player name | `settingsStore.playerName` (persisted in `localStorage`), field on the create page and the join page | `ref('')` local to the page, not persisted | None. |
| Keybindings | Send / Slap keys captured on the create page (`settingsStore.sendKey/slapKey`); no way to change them in the game screen | Gear in the game screen (`HotkeySettings.vue`, `useHotkeySettings`) | Accidental. Bullshit's gear is the right place; removing BC's create page forces the move. |
| Routes | `home`, `create`, `join`, `room` under `/games/bataillecorse` | `bullshit-create`, `bullshit-join`, `bullshit-room` under `/games/bullshit` | Invite links in the wild use both shapes: keep them working. |
| Rematch | Solo: new game; multiplayer: both ask | `playAgain` reopens the room as a lobby with the same options | Bullshit's behaviour falls out of the room model once BC adopts it. |

Kobo (merged backend, no frontend yet) already follows the room model, so it will plug into the same
lobby for free. That is a strong argument for generalising now rather than copying Bullshit's
lobby a second time.

### 2.2 What the backend already gives us

`sessionmanagement.core` is game-agnostic and already covers most of the lobby:

- `SessionService.createRoom(gameType, hostName, GameOptions)`, `joinRoom`, `addBot`, `removeBot`,
  `startGame`, `playAgain`; host = seat 0 (`SessionGame.HOST_SEAT`).
- `LobbyView` (published, per viewer, no token): `players` with `bot`, `hostSeat`, `mySeat`,
  `minPlayers`, `maxPlayers`, `canStart`, `options` (opaque `Map<String,String>`), `removableBotSeats`.
- `LobbyBroadcaster.broadcast` sends one `LobbyView` per human seat on every lobby change.
- `GameFactory` has `minPlayers/maxPlayers` and `create(id, n, GameOptions)`; `GameOptions` is an
  opaque string map; the session core never reads a key.
- What is missing: options are **write-once** (given to `createRoom`, no command changes them), they
  are **not described** (the frontend hard-codes which keys exist and their values: `CLAIM_MODES`,
  `DECK_SIZES`), bots have **no level**, the **seat name cannot be changed** after joining, and the
  add/remove-bot commands exist only under `/bullshit/*` destinations.

## 3. Decisions

Three categories of options. **[proposed]**

| Category | Examples | Owner | Where it is edited | Stored |
|---|---|---|---|---|
| Room options | Bullshit: claim mode, deck size. BC: slap rules, penalty (when they exist) | The host | Lobby, host only; read-only for others | Backend, `SessionGame.options`, frozen at start, kept on rematch |
| Seat options | Name (occupant), bot level and removal (host) | The seat | Lobby, next to the seat | Backend, `SessionPlayer` |
| Local preferences | Sound, keyboard shortcuts (Bullshit and BC), player name default | The browser | Gear in the game screen, plus name field in the entry menu | `localStorage` |

Rationale for the third category staying out of the lobby: these are per-device (a phone has no
keyboard), they never concern other players, and they must stay reachable mid-game, which the lobby
is not. The player name is the one overlap: it is a local default (prefilled everywhere) *and* a seat
option (editable in the lobby by its occupant).

Key decisions:

1. **Games declare their options as descriptors** on the backend; the shared lobby renders them
   without knowing the game (section 6).
2. **One room model for every game**: Bataille Corse leaves `createGame(SOLO|MULTIPLAYER)` and the
   fixed "seat 1" join, and uses `createRoom` / `joinRoom` / `startGame` like Bullshit (section 9).
3. **A solo game is a room whose second seat is a bot**; there is no separate "solo mode" any more.
4. **Bots are always server-side**, with a shared `BotLevel` (EASY / NORMAL / HARD) stored on the
   bot seat (section 8).
5. **One frontend lobby component and one entry menu component**, parameterised by a small per-game
   definition, with the game-specific store behind an interface (sections 4 and 5).

## 4. Shared entry menu **[proposed]**

`components/GameEntryMenu.vue` replaces `view/alpha/LobbyView.vue` and the front of both start pages.

- Props: a `GameDefinition` (key, title locale key, route names for create / join / back) and
  nothing else. Emits nothing: it navigates.
- Content: title block (suit row, game title, card fan), **name field** (prefilled from
  `settingsStore.playerName`, persisted), **New game** button, **Join game** button, back to the
  picker.
- **New game** creates the room right away (name from the field) and navigates to the lobby: the
  per-game create page disappears, because everything it asked (opponent type, difficulty, claim
  mode, deck size, keys) now lives in the lobby or the gear.
- **Join game** goes to `components/JoinGameForm.vue` (game id or full link, name prefilled, error
  line, back). With an invite link `/games/<game>/join/<id>` the id is prefilled.
- A `GameDefinition` registry (`model/games.ts`) feeds the picker, the router and the menu, so
  adding Kobo means adding one entry.
- Routes become `/games/:game`, `/games/:game/join/:id?`, `/games/:game/room/:id`. The old
  `/games/bataillecorse/{create,join,room}` and `/games/bullshit/{create,join,room}` paths stay as
  redirects (invite links already shared).
- Every text moves to locale keys (`menu.*`), the Bataille Corse hard-coded English disappears.

## 5. Shared lobby **[proposed]**

`components/lobby/GameLobby.vue`, extracted from `BullshitLobby.vue` (which keeps working as a thin
wrapper until it is deleted).

Sections, top to bottom, all reachable at 375 px without page scroll:

1. Title: game title and "Lobby".
2. **Seats** (`n` of `maxPlayers`): for each joined seat: number, name, You / Host / Bot badges.
   - Occupant's own name is an inline editable field (max 20 characters, committed on blur or Enter).
   - Bot seat (host only): a level selector (3 segments: Easy / Normal / Hard) and the remove button.
   - **Free seat** row (host only) with two actions: **Add bot** and **Invite with a link** (the
     invite section below). For non-hosts a free seat reads "Waiting for a player".
3. **Room options** (from descriptors): editable controls for the host, read-only text rows for the
   others, hidden entirely when the game declares none.
4. **Invite**: link field and Copy button, `aria-live` "Copied!" (the existing Bullshit markup),
   visible while a seat is free.
5. **Start** (host) with the "waiting for N more players" hint, "Waiting for the host" for others.

Behaviour:

- Every change (join, bot added, level changed, name changed, option changed) produces one
  `LobbyView` broadcast to all human seats (existing `LobbyBroadcaster`). No optimistic UI: the
  controls reflect the last broadcast, a refused command shows the existing `botError`-style alert.
- Options are **frozen at start**: after `startGame` there is no lobby, and the command is refused
  with `GameAlreadyStartedException`. A rematch reopens the lobby (`playAgain`) with the same options
  and the host can change them again.
- A race (the guest joins while the host is changing an option) is harmless: the command only
  touches options and the broadcast carries the whole lobby.
- Frontend decoupling: `GameLobby` receives `lobby: LobbyView` and emits `start`, `add-bot`,
  `remove-bot`, `set-bot-level`, `set-option`, `rename`. A small `LobbyActions` interface, implemented
  by each game store (or by one shared lobby composable, see the plan), sends them on the right
  destinations. The component imports no store.
- A11y: option groups are `fieldset`/`legend` with radio inputs (as in `BullshitStartGame.vue`);
  read-only values are plain text with the label, never disabled-looking inputs; bot level is a radio
  group named "Level of <bot>"; each remove button keeps its accessible name; hit targets at least
  44 px.

## 6. Backend model for options **[proposed]**

### 6.1 Descriptors

In the shared kernel `game`:

```java
public record OptionDescriptor(
        String key,                 // "claimMode", "deckSize"
        OptionType type,            // CHOICE (v1). BOOLEAN and INT_RANGE reserved, not built yet.
        List<String> values,        // permitted values, in display order ("rank", "suit-cycle"...)
        String defaultValue,
        String labelKey,            // locale key, e.g. "bullshit.option.claimMode"
        Optional<Dependency> dependsOn) {}   // value labels may depend on another option (see 6.4)

public interface GameFactory {
    ...
    default List<OptionDescriptor> optionDescriptors() { return List.of(); }
    /** Domain validation of a full option map; throws InvalidOptionException. */
    default GameOptions validate(GameOptions requested) { return requested; }
}
```

- Value labels are **not** sent by the backend: the frontend resolves `<labelKey>.<value>` in
  `locales/`. The backend never sends text, which keeps the project rule (texts in locales) and keeps
  the API stable across languages. An unknown label falls back to the raw value, never to a crash.
- `BullshitFactory` declares `claimMode` and `deckSize` (values from `ClaimModeOption` and
  `DeckSize`, defaults from the same types, so there is a single source of truth) and implements
  `validate` by calling `BullshitOptions.from`, which already rejects unknown keys through the
  domain. `BatailleCorseFactory` and `KoboFactory` keep the empty default.
- `GameFactories` exposes `optionDescriptors(gameType)` and `validate(gameType, options)`;
  `sessionmanagement` still depends only on the kernel.

### 6.2 Update command

`SessionService.updateOptions(GameId, hostToken, Map<String,String> changes)`:

1. Refuse when a game exists (`GameAlreadyStartedException`) or the actor is not the host
   (`NotHostException`), as `addBot` / `removeBot` already do (`openLobbyOwnedBy`).
2. Merge `changes` over the current options, ask the factory to `validate`, store the result.
   `SessionGame` is a record with a final `options`; the lobby is saved again with the new options
   (`repository.saveLobby(new SessionGame(id, type, merged, players))`, sharing the same seat map),
   rather than making the record mutable.
3. Broadcast the lobby (`LobbyBroadcaster`).

Transport: a new game-agnostic `LobbyController` (like `LifecycleController`) with
`/lobby/setOptions`, `/lobby/addBot`, `/lobby/removeBot`, `/lobby/setBotLevel`, `/lobby/rename`.
`/bullshit/addBot` and `/bullshit/removeBot` stay as deprecated aliases for one release so a tab
opened before a deploy keeps working, then they are deleted. `start` stays per game because it
broadcasts the game's own state.

### 6.3 What the lobby view carries

`LobbyView` gains `optionDescriptors` (list of descriptors), `canEditOptions` (host and not started)
and, per `LobbyPlayer`, `botLevel` (null for humans). `options` stays a `Map<String,String>`
(unchanged for existing clients); the descriptor default is applied server-side when a key is
missing, so the frontend never invents a default.

### 6.4 Compatibility

- `BullshitCreatePayload` keeps its optional `claimMode` / `deckSize` and `createRoom(..., options)`
  keeps its signature, so existing tests, Cypress specs and a stale client still work. The new
  create flow sends neither: the room starts with descriptor defaults (rank claim mode, 32 cards,
  which equal today's defaults) and the host edits in the lobby.
- The Bullshit claim mode label depends on the deck size (`claimModeLabel(ui, mode, deckSize)`). The
  descriptor carries `dependsOn = deckSize`, and the frontend passes the current deck size as a
  parameter to the label resolver (`{deckSize}` placeholder in the locale value).
- `playAgain` already copies `existing.options()` into the reopened lobby; unchanged.
- Opaque storage is unchanged: the session core still never interprets a key. Only `validate` (in
  the game's own factory) does.

### 6.5 Rule placement

Validation and defaults live with the game's domain types (`ClaimModeOption`, `DeckSize`,
`BullshitOptions`). The session core enforces *who* and *when* (host, not started), never *what*.
The frontend only renders descriptors and sends `{key, value}`; it does not decide whether a value is
legal.

## 7. Seat options

- **Name**: `SessionGame.renameSeat(PlayerId, String)`; allowed to the occupant only (token check),
  lobby only, trimmed, max 20 characters, a blank name restores the default "Player N". Bot names stay
  the "Bot N" convention rendered by the frontend. During a game the name is fixed (the game
  screens read it once; changing it mid-game would need a state refresh per game, out of scope).
- **Bot level and removal**: host only (`setBotLevel(gameId, hostToken, seat, level)`), stored on
  `SessionPlayer` (`BotLevel level`, default NORMAL). `removeBot` keeps today's rules
  (`BotRemovalBlockedException`).

## 8. Bot levels for both games **[proposed]**

A common vocabulary in the kernel: `enum BotLevel { EASY, NORMAL, HARD }`, locale labels
Easy / Normal / Hard. The lobby selector is identical for both games; what each level changes is
the game's business.

### 8.1 Bullshit (exists in part)

`BotTuning` already holds every constant. Today there is `BotTuning.DEFAULT = (0.05, 0.30, 0.15,
0.70, 0.25, 4)`. Proposed: one `BotTuning` per level, `NORMAL` = today's `DEFAULT` (no behaviour
change for existing games).

| Constant | EASY | NORMAL (today) | HARD |
|---|---|---|---|
| `minCallProbability` / `maxCallProbability` (how often it challenges a doubtful claim) | 0.02 / 0.15 | 0.05 / 0.30 | 0.10 / 0.45 |
| `honestBluffProbability` (plays honestly while pretending to bluff, i.e. unreadable) | 0.30 | 0.15 | 0.10 |
| `lieOneCardProbability` / `lieTwoCardsProbability` | 0.85 / 0.10 | 0.70 / 0.25 | 0.55 / 0.35 |
| `maxCardsPerPlay` | 2 | 4 | 4 |

These values are a **starting point, not measured**. The constraint that must hold for every level
is the one documented on `BotTuning.DEFAULT`: the call probability stays well below 1, otherwise a
game between bots never ends. `BotEndgameSimulationTest` must be run for each level (it exists), and
a win-rate simulation (HARD vs NORMAL vs EASY over many seeded games) must show the ordering
HARD > NORMAL > EASY before the numbers are kept. Also to be tuned later: thinking delay per level
(PR #127 currently slows bots down globally; per-level delay is a follow-up, not part of this work).

Plumbing: `BotSeats` (port) currently returns `Set<PlayerId>`; it becomes `Map<PlayerId, BotLevel>`;
`BotStrategyFactory.forGame(game)` becomes `forSeat(game, level)`; the coordinator's `Table` keeps a
strategy per bot seat instead of one per game.

### 8.2 Bataille Corse (does not exist yet)

What exists: a client-side `AI` that waits `reactionTime` (+/-100 ms) then slaps when the pile
matches a hard-coded heuristic (ten, pair, sandwich, sum of ten) and otherwise sends. The 9 tiers
(Training..Legend, 2100..400 ms) only change the delay. The AI never slaps wrongly and never misses a
slap.

Proposed server-side `BatailleCorseBotCoordinator` (mirror of the Bullshit one, sharing the
`BotScheduler` / `ThinkingDelay` ports, which move from `bullshit.presentation.bot` to a shared
package) and a pure-domain `BatailleCorseBotStrategy` with per-level tuning:

| | EASY | NORMAL | HARD |
|---|---|---|---|
| Reaction time before acting (ms, +/- jitter) | 1800 | 1200 | 600 |
| Probability to notice a valid slap | 0.60 | 0.85 | 1.00 |
| Probability of a false slap on a non-slappable pile | 0.03 | 0.01 | 0.00 |

These map onto today's tiers (Bronze 1800, Gold 1200, Champion 600), so the middle level feels like
what players know. "Notice a slap" and "false slap" **do not exist today** and are new behaviour (the
second one makes a bot beatable by patience; it is optional and can ship at 0 for all levels first).
The 9-tier slider and `Difficulty.ts` are removed; the stored `bc_difficulty` localStorage value is
no longer read, so it needs no migration.

The strategy must use only what a human sees (the pile and the slap rules in force), not hidden
cards. When BC slap rules become room options (section 9.2), the bot's "valid slap" check must
come from the game's rules object, not from a copy (today's `AI.shouldAttemptSlap` is such a copy and
is explicitly marked "NOT authoritative").

## 9. Impact on Bataille Corse **[proposed]**

### 9.1 Session model

- `BatailleCorseFactory` stays at `minPlayers = maxPlayers = 2`.
- The create path moves to rooms: new `/create` handler calls `createRoom`, start is the shared
  host-started `startGame` followed by a `BatailleCorseStateBroadcaster` call (as Bullshit does),
  join uses `joinRoom`. `SessionService.createGame(...)`, `GameMode`, `joinGame` (fixed seat 1),
  `claimAllSeats` and `BatailleCorseRestController.joinGame` can then be deleted (dead code
  check at the end of the PR).
- "Play against the computer" is now: New game, Add bot, (choose level), Start. "Play with a friend":
  New game, copy the link, friend joins, Start. Both are the same screen.
- Rematch: the shared `playAgain` reopens the lobby for both modes. Today it resets the lobby to
  empty, bot seats included, which is clumsy for solo play. Decision to confirm (question 6): keep
  that behaviour, or keep bot seats and their level across rematches.
- Reconnection and token restore (`restoreTokens`, `restoreSession`) are keyed by game id and
  seat; a solo game now holds a single human token instead of two. The client rehydration path
  must stop expecting two tokens.
- Tests and Cypress specs that rely on SOLO (`create-game`, `send-card`, `rehydrate-game`,
  `waiting-and-names`, `bataillecorse-menu-fit`, `turn-indicator`, `responsive-game-screen`,
  `rules-panel`, `visual-direction`) must be updated: they start from the menu and now need
  "Add bot" and "Start".

### 9.2 Room options for Bataille Corse

None exist (`SlapRules.DEFAULT` and `PutCardsUnderPile(2)` are constants). This spec does **not**
invent rules. Two steps:

1. The shared lobby ships with BC declaring no options (the section is hidden). No gameplay change.
2. **Optional, separate PR, after the owner picks the options** (question 4): the rules already
   present in the domain become descriptors, for instance one `CHOICE` per slap rule
   (`tens`, `sameAsBelow`, `sandwich`, `sumOfTen`: `on` / `off`) and `penaltyCards` (`1` / `2` / `3`),
   parsed by a `BatailleCorseOptions` record in the domain and applied in `BatailleCorse` through
   `SlapRules` / `Penality`. The domain already composes `SlapRules` from a list of `SlapRule`, so
   this is small. The frontend RulesPanel must then show the rules *in force* rather than
   hard-coded text (note for that PR).

### 9.3 Local preferences

BC keybindings move from the create page to a gear in the BC game screen, by generalising
`HotkeySettings.vue` / `useHotkeySettings` (currently Bullshit-only) over a list of actions, so both
games share it. `settingsStore.sendKey/slapKey` keep their `localStorage` keys (`bc_sendKey`,
`bc_slapKey`) so players keep their bindings.

## 10. Accessibility and mobile

- 375 px wide: the menu and the lobby must fit without page scroll and without horizontal scroll
  (existing Cypress specs `bataillecorse-menu-fit` and the Bullshit lobby ones set the precedent).
  The lobby is the tallest screen: title block shrinks (card fan hidden below a height threshold),
  seat rows are single-line, option groups wrap to a second line only, and the panel scrolls
  *inside* itself (`overflow-y: auto` on the panel) if a game declares many options, never the
  page. A viewport at 375 x 667 with Bullshit (6 seats, 2 options, invite, Start) is the worst case
  to test.
- Keyboard: every control reachable in a logical order; Enter commits a name; Escape reverts it.
- Screen readers: lobby changes announced through one `aria-live="polite"` region ("Bot 1 added",
  "Host changed deck size to 52 cards"); option groups with `legend`; the Copy confirmation keeps its
  `role="status"`.
- Colour is never the only carrier: the bot level shows its text label.
- All strings in locales (`menu.*`, `lobby.*`, `options.<game>.<key>[.<value>]`).

## 11. Tests

- Backend (TDD, domain first): `OptionDescriptor` contract per factory (defaults valid, values
  accepted by `validate`); `SessionService.updateOptions` (host only, refused after start, invalid
  value rejected by the game, merged, options kept on `playAgain`); `renameSeat` (occupant only);
  `setBotLevel`; `LobbyView` projection (descriptors, `canEditOptions` host-only, `botLevel`);
  broadcast IT with two STOMP clients for options and rename; `BotTuning` per level and
  `BotEndgameSimulationTest` for each level; level ordering simulation; BC bot strategy
  (deterministic `RandomGenerator`, fake clock); BC room flow IT (create, join, add bot, start).
- Frontend (Vitest): `GameEntryMenu`, `JoinGameForm`, `GameLobby` (host vs guest rendering, option
  controls from descriptors, read-only mode, bot level, rename, Copy fallback), the label resolver
  with `dependsOn`, `LobbyActions` per game store, router redirects of legacy paths.
- Cypress: update the Bullshit lobby specs; new `shared-lobby.cy.ts` (two browsers: host changes an
  option, guest sees it live; guest cannot edit); BC solo flow; BC invite flow; menu fit at 375 px.
  The e2e stack rules in `CLAUDE.md` apply (one stack on port 5173).
- Before any UI PR: the `ui-change` skill checklist (information displayed before and after, light
  and dark, 375 px and 1280 px).

## 12. Out of scope

- Other #122 items: end screens, in-game buttons, player tags, global settings page.
- New Bullshit or BC rules; a full BC rule-variant catalogue.
- Per-bot names, bot personalities, thinking-delay-per-level (follow-up to #127).
- Changing options or names while a game is running.
- More than one human-invite mechanism (QR code, room codes).
- Kobo frontend (it should only need a `GameDefinition` entry and its own state view).
- Boolean and numeric option types (the descriptor leaves room for them; the first game that needs
  one adds it).
- Server persistence (state stays in memory).

## 13. Open questions for the owner

1. **Name placement**: name field on the entry menu (this spec), only in the lobby, or both? The
   spec assumes the menu field is the persisted default and the lobby field edits the seat.
2. **New game creates the room immediately** (no create page): acceptable? The alternative keeps a
   create page but it would hold nothing the lobby does not.
3. **Bot levels**: three levels (Easy / Normal / Hard) for both games, replacing BC's nine tiers?
   Or keep more tiers for BC? And are the Bullshit tuning numbers and the BC "false slap" behaviour
   welcome, or should level 1 only change speed for BC?
4. **Bataille Corse room options**: which rules should the host be able to change (slap rules one by
   one, penalty size, others such as the number of cards dealt)? Or none for now?
5. **Bot token model**: bots always server-side for BC (this spec) means the old client-driven AI is
   deleted. Any reason to keep a purely local "practice" mode that needs no server game?
6. **Rematch with a bot**: keep the bot seat (and its level) across a rematch, or reopen an empty
   lobby as today?
7. **Who can rename**: only before the start (this spec) or also mid-game?
8. **Max players in BC**: stays 2. Is a 3-4 player Bataille Corse wanted at some point (it changes
   the lobby only through `maxPlayers`)?
9. **Old routes**: keep redirects for old invite links indefinitely, or a limited time?
10. **Local preferences**: confirm sound and shortcuts stay in the gear, outside the lobby, for both
    games, and that BC gets a gear in its game screen.
11. **Entry from the picker**: both game cards open the same menu. Should "Join game" also be
    offered directly on the picker (one place to paste any invite link, the game being inferred from
    the id)?
