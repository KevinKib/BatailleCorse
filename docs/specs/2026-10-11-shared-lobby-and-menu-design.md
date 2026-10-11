# Shared game home and lobby for Bataille Corse and Bullshit (issue #122)

Status: revised after the owner's answers (decisions in section 3 are confirmed unless marked
**[proposed]**). Classification: architectural (it changes the Bataille Corse session model, adds a
kernel concept and touches both frontends). Open questions are in section 14.

## 0. Glossary (product term, proposed to the owner, to confirm, and its name in the code)

| Product term | Meaning | Name in the code |
|---|---|---|
| Game selection | The `/games` page where the player picks Bataille Corse or Bullshit | `GamePickerView.vue`, route `games` |
| Game home | The per-game screen with New game / Join game (formerly "entry menu") | new `GameHome.vue`; today `view/alpha/LobbyView.vue` (BC, misnamed) |
| Lobby | The waiting area of a created game before it starts: seats, invite link, bots, game options | backend "room" not yet started: `SessionGame` without a `Game`, `LobbyView`, `LobbyBroadcaster`; frontend `BullshitLobby.vue`, future `GameLobby.vue` |
| Table | The in-game screen while the game runs | `GameScreen.vue` (BC), `BullshitGameScreen.vue` |
| Game options | What the host sets for the whole game (formerly "room options") | `GameOptions`, `SessionGame.options`, `BullshitOptions` |
| Seat options | Per-seat settings: player name; bot tier (Bataille Corse only) | `SessionPlayer` (name, bot, tier), seat descriptors |
| Local preferences | Sound, keyboard shortcuts; stay in the browser | `settingsStore`, `useHotkeySettings`, `localStorage` |

In French UI text and conversations, avoid "salle"/"room". The code keeps `room`, `SessionService`,
`createRoom`, `joinRoom`, etc.

## 1. Problem

Playtest 2 asked to make the two games feel like one product. For this spec:

- The Game home ("New game / Join game") should be one component: no difference justifies two.
- The Bullshit lobby is easy to adapt to Bataille Corse. A seat is filled by a bot or by a player
  invited with a link.
- Game options must be editable in the lobby; the place of seat options is to decide.
- (New, issue #128) The lobby has no way to leave it.

Other #122 items (end screens, buttons, player tags) are out of scope (section 12).

## 2. Current state (verified in the code, `main` at `c6f4c92`)

### 2.1 What each game does today

| Aspect | Bataille Corse | Bullshit | Real reason for the difference? |
|---|---|---|---|
| Game home | `/games/bataillecorse` = `LobbyView.vue` (New / Join / back), then `StartGame.vue` | `/games/bullshit/create` directly = `BullshitStartGame.vue`; joining only through an invite link or the same page in join mode | None. Bullshit never got the home page: a player holding a game id has no Join button. |
| Component | `view/alpha/LobbyView.vue`, `view/alpha/StartGame.vue` (create and join switched by route name) | `view/bullshit/BullshitStartGame.vue` (same trick) | None. Same layout and CSS copied. |
| Texts | Hard-coded English in templates | `bullshitUi.start.*` locale keys | None. The project rule is locale keys. |
| Create | WebSocket `/create` with `SOLO` or `MULTIPLAYER`; game dealt immediately (`SessionService.createGame`) | WebSocket `/bullshit/create`; creates a lobby, nothing dealt (`createRoom`) | Accidental: BC predates the room model. |
| Opponent choice | On the create page: Human / Computer toggle and a 9-tier slider (Training, Bronze, ... Legend) | In the lobby: host adds or removes bots one by one, no tier | Same need, different moment. The lobby is the right place. |
| Joining | `POST /api/game/{id}/join` claims fixed seat 1 (`joinGame`); `WaitingOverlay` shows the invite link on the Table until seat 1 is taken | `POST /api/bullshit/game/{id}/join` claims the next free seat (`joinRoom`), refuses when started or full | Accidental: 2 players is the n=2 case. |
| Waiting / lobby | No lobby; the game exists behind `WaitingOverlay` | `BullshitLobby.vue`: seats with You / Host / Bot badges, invite with Copy, host-only Start and bot controls, read-only game options | Lobby is the better design. |
| Start | Automatic | Host presses Start (min 2) | Accidental. |
| Invite link | `WaitingOverlay.vue` | `BullshitLobby.vue` (`aria-live`, select-all fallback without clipboard) | None; keep the Bullshit one, delete `WaitingOverlay`. |
| Bots | **Client side**: `model/ai/AI.ts` plays seat 1 from the creator's browser; in SOLO that browser holds both seat tokens. Tier = reaction time (`DIFFICULTY[...]`: 2100, 1800, 1500, 1200, 900, 700, 600, 500, 400 ms) +/-100 ms jitter; slaps when a hard-coded heuristic matches (ten, pair, sandwich, sum of ten); never slaps wrongly. The **auto-grab** of the pile (1500 ms timer in `GameSession`) is also client side | **Server side**: `BullshitBotCoordinator` + `ProbabilisticBotStrategy` + `BotTuning.DEFAULT`; bot tokens never leave the backend; a single strength | Accidental. The server model keeps session rules (no token leak, presence, forfeit, rematch). |
| Game options | None: slap rules (`SlapRules.DEFAULT`) and penalty (`PutCardsUnderPile(2)`) are constants; `BatailleCorseFactory` ignores `GameOptions` | `claimMode` and `deckSize` chosen on the create page, stored in `SessionGame.options`, shown read-only in the lobby, kept on rematch | Real for "BC has none" (stays so, section 9.2); the place of editing is not justified. |
| Player name | `settingsStore.playerName` (persisted) | local `ref`, not persisted | None. |
| Keyboard shortcuts | Captured on the create page only | Gear on the Table (`HotkeySettings.vue`) | Accidental; the gear is the right place. |
| Routes | `home`, `create`, `join`, `room` under `/games/bataillecorse` | `bullshit-create`, `bullshit-join`, `bullshit-room` under `/games/bullshit` | Invite links exist in both shapes: keep them working. |
| Rematch | Solo: new game; multiplayer: both ask | `playAgain` reopens the lobby with the same options | Falls out of the room model. |
| Leaving the lobby | n/a (no lobby) | No button (issue #128) | Missing in both. |

Kobo (backend merged, no frontend) already follows the room model, so it will reuse the lobby by
adding one game definition.

### 2.2 What the backend already gives us

`sessionmanagement.core` is game-agnostic and covers most of the lobby:

- `SessionService.createRoom/joinRoom/addBot/removeBot/startGame/playAgain`; host = seat 0.
- `LobbyView` (per viewer, no token): `players` (with `bot`), `hostSeat`, `mySeat`, `minPlayers`,
  `maxPlayers`, `canStart`, `options` (opaque map), `removableBotSeats`. `LobbyBroadcaster` sends one
  per human seat.
- `GameFactory`: `minPlayers/maxPlayers`, `create(id, n, GameOptions)`.
- Presence (`PresenceService`) only acts on a **started** game (`findGame` is null in a lobby): a
  disconnect in a lobby starts no timer and frees no seat today.
- Missing: game options are write-once and undescribed; seat names cannot change; bots have no
  seat options; there is no "leave lobby" command; add/remove bot exist only under `/bullshit/*`.

## 3. Decisions

Confirmed by the owner:

- **(a)** "New game" creates the lobby immediately; no create page.
- **(b)** Bataille Corse keeps its 9 tiers (Training to Legend) as they are: no Easy/Normal/Hard, no
  new "false slap", no gameplay change.
- **(c)** No difficulty for Bullshit bots for now: no `BotLevel`, no per-level `BotTuning`. A
  Bullshit bot seat has no option.
- **(d)** No game options for Bataille Corse.
- **(e)** The local AI of Bataille Corse is deleted and the bot moves server side (cost in section 8).
- **(f)** The shared lobby gets a Leave button (section 6.3).
- Terminology of section 0 is proposed and awaits confirmation.

Design (the structure is **[proposed]**):

| Category | Examples | Owner | Edited | Stored |
|---|---|---|---|---|
| Game options | Bullshit: claim mode, deck size. Bataille Corse: none | The host | Lobby; host only; read-only for the others | Backend `SessionGame.options`; frozen at start; kept on rematch |
| Seat options | Name (occupant); bot tier (BC only, host) | The seat | Lobby, next to the seat | Backend `SessionPlayer` |
| Local preferences | Sound, shortcuts, default player name | The browser | Gear on the Table; name field on the Game home | `localStorage` |

Local preferences stay out of the lobby: they are per device, concern nobody else and must stay
reachable mid-game. The player name is the one overlap (local default, plus the seat's own option).

Key decisions:

1. **Games declare their game options as descriptors** (backend); the lobby renders them without
   knowing the game (section 6).
2. **Games also declare their seat options as descriptors** (section 7): the lobby renders them per
   seat the same way. Bullshit declares none for bots, Bataille Corse declares the bot tier.
3. **One room model for every game**: Bataille Corse leaves `createGame(SOLO|MULTIPLAYER)` and the
   fixed seat 1 join; a solo game is a lobby whose second seat is a bot.
4. **Bots are server side** in both games.
5. **One Game home and one lobby component**, parameterised by a per-game definition.

## 4. Shared Game home (`GameHome`)

Component naming: `GameHome.vue` (was called `GameEntryMenu` in the first draft of this spec).
Replaces `view/alpha/LobbyView.vue` and the front of both start pages.

- Prop: a `GameDefinition` (key, title locale key, route names, `createLobby` action).
- Content: title block (suits, title, card fan), **name field** (prefilled from
  `settingsStore.playerName`, persisted, and editable in the lobby: working assumption, not asked again), **New game**, **Join
  game**, back to the Game selection.
- **New game** creates the lobby with the name and navigates to it. Nothing is asked: game options
  are defaults and are edited in the lobby; bots and invitations are lobby actions.
- **Join game** opens `JoinGameForm.vue`: game id or full link, name prefilled, error line, back. An
  invite link `/games/<game>/join/<id>` prefills the id.
- A `GameDefinition` registry (`model/games.ts`) feeds the Game selection, the router and the Game
  home; adding Kobo is one entry.
- Routes: `/games/:game`, `/games/:game/join/:id?`, `/games/:game/room/:id`. The old
  `/games/bataillecorse/{create,join,room}` and `/games/bullshit/{create,join,room}` redirect.
  Every "Back" leads to `/games` (issue #123 is fixed independently, but the new components must
  respect it).
- All texts in locales (`home.*`).

## 5. Shared lobby (`GameLobby`)

`components/lobby/GameLobby.vue`, extracted from `BullshitLobby.vue` (which disappears; issue #128
explicitly asks for the leave button to land in the shared lobby, not in `BullshitLobby.vue`).

Sections, top to bottom, all visible at 375 px without page scroll:

1. Title: game title and "Lobby", and **Leave lobby** (secondary text button, top of the panel).
2. **Seats** (`n` of `maxPlayers`): number, name, You / Host / Bot badges.
   - The occupant's own name is an inline editable field (max 20 characters, committed on blur or
     Enter, reverted on Escape).
   - Seat options from descriptors (host only, bots): for Bataille Corse a tier selector; for
     Bullshit nothing is rendered. Remove-bot button next to it.
   - **Free seat** row (host): **Add bot** and the invite section; non-hosts read "Waiting for a
     player".
3. **Game options** from descriptors: editable for the host, read-only text for the others, section
   hidden when the game declares none (Bataille Corse).
4. **Invite**: link and Copy, `aria-live` "Copied!" (existing markup), shown while a seat is free.
5. **Start** (host) with its hint; "Waiting for the host" for the others.

Behaviour:

- Every change (join, leave, bot added/removed, tier changed, name changed, option changed) yields
  one `LobbyView` broadcast to all human seats. No optimistic UI; a refused command shows an alert
  like the existing bot error.
- Game options are **frozen at start**; after `startGame` the commands are refused
  (`GameAlreadyStartedException`). A rematch reopens the lobby with the same options.
- A join racing with an option change is harmless: the command touches options only and the
  broadcast carries the whole lobby.
- Decoupling: `GameLobby` takes `lobby: LobbyView` and emits `start`, `leave`, `add-bot`,
  `remove-bot`, `set-seat-option`, `set-option`, `rename`; a `LobbyActions` composable sends them to
  `/lobby/*`. The component imports no store.
- A11y: option groups are `fieldset`/`legend` with radios; read-only values are plain labelled text;
  the tier selector is a labelled group per bot; remove buttons keep their accessible names; targets
  at least 44 px.

### 5.1 Leaving the lobby (issue #128)

Command `leaveLobby(gameId, token)`, lobby phase only (a game in progress keeps the existing
forfeit/presence flow; after the game, the rematch departure flow).

- **Guest leaves**: the seat is freed (`SessionPlayer.release()`) and the guest goes to the Game
  selection (`/games`); their token stops working. A game is dealt to seats 0..claimed-1, so a gap
  below another human must not remain. `removeBot` already shifts later bots down; for humans the
  seat id is tied to the token (`SessionPlayer` has a final `id` and `token`), so shifting a human
  means moving the token, name and flags between seat records and telling each client its new
  `mySeat` through the next `LobbyView`. That is the only non-trivial part. Bataille Corse (2 seats)
  never has a gap. For Bullshit, v1 option: when the leaver is followed by humans, shift them down
  (cost: a `SessionGame.vacate(seat)` method with tests, plus the frontend re-reading `mySeat` from the
  view, which `Bullshit.store` already does through `seat-change`); fallback if it proves risky:
  refuse to start while a gap exists and show "Seat N is empty" to the host.
- **Host leaves**: **open question** (section 14, question 7). Options and costs:
  1. *Close the lobby*: every other human gets "The host left", is sent to the Game selection;
     the game is removed. Cost: smallest (one command, one broadcast event, one repository
     removal, no seat renumbering, no change to the "host = seat 0" assumption used by
     `SessionService`, `LobbyView`, the Bullshit frontend `isHost = mySeat === 0`). Downside: a
     guest loses the lobby because the host misclicked.
  2. *Transfer the host to the next seat*: needs a movable host (`SessionGame.HOST_SEAT` is a
     constant, `isHost` compares to it, `LobbyView.hostSeat` is already a field but the frontend
     computes `isHost` from seat 0), seat renumbering or an explicit `hostSeat` in `SessionGame`,
     reassignment of bots, and a rematch rule (`playAgain` gives seat 0 to the first caller).
     Cost: medium, with regression risk in every place that assumes seat 0.
  3. *Depends on presence*: close if no other human is left (only bots remain, the lobby is
     garbage), transfer otherwise. Cost: the sum of both, plus two behaviours to test.
  **Recommendation: option 3 implemented in two steps**: ship "close when the host leaves" first
  (option 1, covers the last-human case too); add the transfer when someone asks, since it is the
  only part needing the host-movable refactor. If the owner prefers a single behaviour, option 1.
- **Interaction with presence/forfeit**: they do not apply in a lobby (no `Game`, no timers). So a
  closed tab leaves the seat taken: today's behaviour, now with a reason to fix it. Out of scope for
  v1 except a note: an explicit Leave is the only way to free a seat; a lobby with a dropped host is
  cleaned by the existing idle eviction (`GameCleanupService`). A later improvement could run the
  disconnect grace timer on lobby seats too.
- Leave during the Table is untouched: forfeit (`/forfeit`, RESIGNED) stays the way out.

## 6. Backend model for game options **[proposed]**

### 6.1 Descriptors

In the shared kernel `game`:

```java
public record OptionDescriptor(
        String key,                 // "claimMode", "deckSize"
        OptionType type,            // CHOICE (v1); BOOLEAN / INT_RANGE reserved, not built
        List<String> values,        // permitted values, display order
        String defaultValue,
        String labelKey,            // locale key, e.g. "options.bullshit.claimMode"
        Optional<String> dependsOnKey) {}

public interface GameFactory {
    ...
    default List<OptionDescriptor> optionDescriptors() { return List.of(); }
    /** Domain validation of a full option map; throws InvalidOptionException. */
    default GameOptions validate(GameOptions requested) { return requested; }
}
```

- Value labels come from the frontend locales (`<labelKey>.<value>`), unknown label falls back to the
  raw value. The backend never sends text.
- `BullshitFactory` declares `claimMode` and `deckSize` from `ClaimModeOption` and `DeckSize`
  (single source of truth) and `validate` calls `BullshitOptions.from`. `BatailleCorseFactory` and
  `KoboFactory` keep the empty default.
- `GameFactories` exposes `optionDescriptors(type)` and `validate(type, options)`;
  `sessionmanagement` still depends only on the kernel.

### 6.2 Update command

`SessionService.updateOptions(GameId, hostToken, Map<String,String>)`: refuse if started or not the
host (reuse `openLobbyOwnedBy`); merge, `validate` through the factory, store by saving the lobby with
the new options (`SessionGame` is a record; same seat map), broadcast.

Transport: game-agnostic `LobbyController` (like `LifecycleController`) with `/lobby/setOptions`,
`/lobby/addBot`, `/lobby/removeBot`, `/lobby/setSeatOption`, `/lobby/rename`, `/lobby/leave`.
`/bullshit/addBot` and `/bullshit/removeBot` stay as aliases for one release, then go. `start` stays
per game (it broadcasts the game's own state).

### 6.3 What the lobby view carries

`LobbyView` gains `optionDescriptors`, `seatOptionDescriptors`, `canEditOptions` (host, not started)
and, per `LobbyPlayer`, `seatOptions` (a `Map<String,String>`, empty for humans and for Bullshit
bots). `options` keeps its `Map<String,String>` shape; defaults are filled server side.

### 6.4 Compatibility

- `createRoom(..., GameOptions)` and `BullshitCreatePayload` (`claimMode`, `deckSize` optional) keep
  working for stale clients and tests. The new flow sends neither; defaults equal today's (rank, 32).
- The Bullshit claim mode label depends on the deck size: `dependsOnKey = deckSize`, and the label
  resolver passes the current deck size as a placeholder.
- `playAgain` copies `existing.options()`; unchanged.
- The session core still never interprets a key; `validate` is the game's.

### 6.5 Rule placement

Validation and defaults live in the game's domain types. The session core enforces *who* and *when*,
never *what*. The frontend renders descriptors and sends `{key, value}`.

## 7. Seat options **[proposed]**

- **Name**: `SessionGame.renameSeat(PlayerId, String)`; the occupant only, lobby only, trimmed, 20
  characters max, blank restores "Player N". Bot names stay "Bot N" rendered by the frontend. Fixed
  once the game starts (question 6).
- **Seat descriptors**: `GameFactory.seatOptionDescriptors()` (default empty), same record as game
  options but applying to a bot seat, and `validateSeatOption(key, value)`. Stored on
  `SessionPlayer` as an opaque `Map<String,String>`; the core never reads a key.
  - **Bullshit**: empty. The bot seat shows no control. (This is the confirmed state; a future
    Bullshit tier only needs a descriptor.)
  - **Bataille Corse**: one descriptor `tier`, values = the 9 existing tiers (indexes `0`..`8`, labels
    in locales: Training, Bronze, Silver, Gold, Platinum, Diamond, Champion, Challenger, Legend),
    default `1` (Bronze, the current stored default `bc_difficulty = 1`). A selector for 9 values is a
    labelled `<select>` (a radio group of 9 does not fit 375 px). The current tier colours are
    dropped from the lobby (colour alone carried meaning); the name carries it.
  - `setSeatOption(gameId, hostToken, seat, key, value)`: host only, bot seat only, lobby only.
- `removeBot` keeps its rules (`BotRemovalBlockedException`); a bot shifted down by a removal
  carries its seat options.

## 8. Bataille Corse bot, server side (decision e): cost and risks

### 8.1 What exists today

`model/ai/AI.ts` (54 lines): `play(state, actions)` schedules one action after
`reactionTime + jitter(-100..+99)`; `shouldAttemptSlap` is a copy of the slap rules (marked "NOT
authoritative"); the 9 tiers live in `model/Difficulty.ts`. `GameSession` (frontend) restarts the bot
after each server state, cancels it on rematch/leave, and **also** runs the auto-grab timer (1500 ms)
for the pile in solo; in multiplayer only the rightful grabber's tab arms it. The solo creator's
browser holds both seat tokens ("the creator holds both tokens" hack).

### 8.2 What the port consists of

Backend, mirror of `BullshitBotCoordinator`, but simpler (no memory, no claims):

| Piece | Content | Size (indicative) |
|---|---|---|
| `BatailleCorseBotStrategy` (domain) | Pure: given the visible pile and the game's `SlapRules`, returns `SEND`, `SLAP` or nothing. Uses the *game's* rules object instead of the copied heuristic, so rule changes no longer drift | ~60 lines |
| `BatailleCorseBotTier` (domain) | 9 tiers, reaction time, jitter; ported from `Difficulty.ts` unchanged | ~40 lines |
| `BatailleCorseGameActions` | Extract the send/slap/grab logic out of `BatailleCorseWebSocketController` so a bot and a human use the same use case (as `BullshitGameActions`) | ~120 lines moved |
| `BatailleCorseBotCoordinator` | After each state broadcast: drop stale tasks, schedule the bot's action after its tier delay, re-check the game version and the monitor before acting; stop when no human is left or the game is over; **also run the bot's auto-grab** | ~200 lines |
| Wiring | `AppConfig` beans, `BotSeats` extended to return seat options so the tier reaches the coordinator, lifecycle (forfeit of the human stops the bot, rematch drops the table) | ~60 lines |
| Tests | strategy, tiers, coordinator with `ManualBotScheduler`/`FixedThinkingDelay` (already exist for Bullshit), full game IT with a bot | 5 to 6 classes |

Total: roughly 500 lines of production code and as much test code, two to three working days for
one developer familiar with the Bullshit coordinator, in one PR (PR 7), plus about one day of
integration on the frontend side (PR 9) where the solo branches are removed. Deterministic tests use
the existing ports: `BotScheduler` (manual), `ThinkingDelay` (fixed) and `RandomGenerator`
(seeded) from the Bullshit work, plus a game-state builder for pile situations (the BC domain
already has a constructor taking players, pile, rules and penalty).

### 8.3 Real risks

1. **Auto-grab is client side today** and the solo client does it for the bot. The server bot must
   grab for itself when `getAutoGrabPlayer()` is the bot, with the same 1500 ms delay, otherwise a
   bot that wins a face-card sequence never collects the pile and the game stalls. (Human grabs
   stay in the human's client: moving auto-grab to the server for everybody would be cleaner but is
   a rule change for multiplayer and is out of scope.)
2. **Reaction fidelity at the fast tiers.** Legend is 400 ms (+/- 100). Server timers have a
   jitter of a few milliseconds, which is negligible, but the human sees the state one network
   latency after the server produced it, so a server bot is on average faster than the old local one
   by about half an RTT (tens of milliseconds on a normal connection, more on mobile). It makes the
   top tiers slightly harder in practice; a per-tier correction is possible but is not planned.
3. **Animations are client side.** The old bot started after `GameSession` received the state and
   the client awaited card animations before the next move (`awaitAnimation`). The server does not
   know animation durations; a fast bot (400 ms) may send while a card is still flying. Mitigation:
   verify in the browser that the client queues or tolerates a move during an animation; if not, add
   a floor (minimum delay) to the bot delay or make the Table ignore the animation lock for server
   events. This is the most likely source of visual bugs.
4. **Slap decisions on stale state**: a bot must not slap a pile that changed during its delay. The
   version check of the Bullshit coordinator solves it; to be asserted in a test.
5. **Penalty parity**: the old local AI accepted any penalty from a bad slap; the heuristic never
   slaps wrongly, so this should not change, but the new strategy reads the real rules, and any
   difference with the old heuristic (e.g. a rule present in the domain but not in the heuristic) will
   make the bot slightly stronger. List the differences in the PR.
6. **Session model migration** (PR 9, not PR 7): deleting `SOLO`, the two-token rehydration and
   `restoreTokens` for two seats touches reconnection logic; an in-progress solo game during a
   deploy is lost (state is in memory anyway).
7. **Rematch against a bot** needs a rule (question 1): today a solo rematch is instant; the shared
   `playAgain` empties the lobby.
8. **Thread safety**: coordinator tasks run on the scheduler thread while humans act on websocket
   threads; follow the Bullshit rule (everything under the game's monitor).
9. **Idle games with a bot only**: when the human leaves, the bot must stop (same as Bullshit's
   "no human left, nothing scheduled").

## 9. Impact on Bataille Corse

### 9.1 Session model

- `BatailleCorseFactory` stays 2 to 2 players.
- Create/start/join move to the room model: new create and start handlers (`createRoom`,
  `startGame` then the state broadcast), join via `joinRoom`. Then `SessionService.createGame`,
  `GameMode`, `joinGame` (fixed seat 1), `claimAllSeats`, the old `/create` and the BC join path
  are deleted (PR 9).
- "Play against the computer": New game, Add bot, pick a tier, Start. "Play with a friend": New game,
  copy the link, the friend joins, Start. Same screen.
- Rematch: see question 1.
- Reconnection: a solo game now holds one human token instead of two; client rehydration must stop
  expecting two.
- Cypress specs relying on SOLO must be rewritten: `create-game`, `send-card`, `rehydrate-game`,
  `waiting-and-names`, `bataillecorse-menu-fit`, `turn-indicator`, `responsive-game-screen`,
  `rules-panel`, `visual-direction`.

### 9.2 Game options for Bataille Corse

None (decision d). The Game options section of the lobby is hidden for BC. Slap rules and penalty
stay constants in the domain. (If one day wanted, descriptors are enough; no lobby change.)

### 9.3 Local preferences

BC keyboard shortcuts move from the create page to a gear on the BC Table by generalising
`HotkeySettings.vue`/`useHotkeySettings` (currently Bullshit-only). Storage keys `bc_sendKey` and
`bc_slapKey` are kept. The `bc_difficulty` value is no longer a global setting: the tier is a seat
option chosen in the lobby; the stored value can seed the default tier of a newly added bot (small,
optional: question list does not include it; default is the fixed Bronze).

## 10. Accessibility and mobile

- 375 px: the Game home and the lobby fit with no page scroll and no horizontal scroll (precedents:
  `bataillecorse-menu-fit`, Bullshit lobby specs). The lobby is the tallest screen: the card fan
  hides on short viewports, seat rows are one line, the panel scrolls inside itself if a game
  declares many options, never the page. Worst case to test: Bullshit, 6 seats, 2 options, invite,
  Start and Leave at 375 x 667.
- Keyboard: logical order; Enter commits a name; Escape reverts.
- Screen readers: lobby changes announced in one `aria-live="polite"` region ("Bot 1 added", "The host
  changed the deck size", "Alice left"); option groups with `legend`; Copy keeps `role="status"`.
- Colour never carries meaning alone; the tier is shown by name.
- All strings in locales (`home.*`, `lobby.*`, `options.<game>.<key>[.<value>]`, `seatOptions.<game>.*`).

## 11. Tests

- Backend (TDD, domain first): descriptors per factory (defaults valid, `validate` rules); 
  `updateOptions` (host only, refused after start, invalid refused, merged, kept on `playAgain`);
  `renameSeat`; seat options (host only, bot only, validated by the game, kept when a bot shifts);
  `leaveLobby` (guest frees the seat, host leave per the chosen policy, refused during a game);
  `LobbyView` projection; STOMP ITs with two clients for options, rename, leave; Bataille Corse bot
  (section 8.2) with manual scheduler and fixed delay; BC room flow IT.
- Frontend (Vitest): `GameHome`, `JoinGameForm`, `GameLobby` (host vs guest, options, seat options, rename, leave, Copy fallback),
  label resolver with `dependsOnKey`, `LobbyActions`, router redirects.
- Cypress: Bullshit lobby specs updated; new `shared-lobby.cy.ts` (host edits an option, guest sees it
  live, guest cannot edit, guest leaves and the seat reopens); BC solo with a bot; BC invite flow;
  Game home and lobby fit at 375 px. One e2e stack at a time (`CLAUDE.md`).
- UI PRs follow `ui-change` (inventory before/after, light/dark, 375 and 1280 px).

## 12. Out of scope

- Other #122 items (end screens, buttons, player tags, global settings page).
- Difficulty for Bullshit bots; new Bataille Corse gameplay (false slaps, rule variants); any game
  option for Bataille Corse.
- Moving the human auto-grab to the server.
- Changing names or options while a game runs.
- Disconnect grace timers on lobby seats.
- Extra invitation mechanisms (QR code, short codes).
- Kobo frontend (one `GameDefinition` entry when it exists).
- Boolean/numeric option types (reserved in the descriptor).
- Persistence.

## 13. Changes from the first draft

Removed: `BotLevel`, per-level `BotTuning`, Bullshit bot levels (PR 5), Easy/Normal/Hard, new
Bataille Corse bot behaviours, Bataille Corse game options (PR 10), `BotEndgame` level simulations.
Added: leave-lobby command and button, seat option descriptors, glossary, cost of the server bot.
Renamed: `GameEntryMenu` is now `GameHome`; "room options" are "Game options"; "entry menu" is "Game
home".

## 14. Open questions for the owner

1. **Rematch against a bot**: keep the bot seat and its tier across a rematch (more natural for
   solo play), or reopen an empty lobby as `playAgain` does today?
2. **Renaming**: only in the lobby (this spec) or also during the game?
3. **Bataille Corse players**: stays at 2, or 3 to 4 someday (it would change only `maxPlayers` and
   the dealing rules)?
4. **Old invite links**: redirects forever, or for a limited time?
5. **Gear on the BC Table**: add the keyboard-shortcut gear to the Bataille Corse game screen (needed
   once the create page is gone), and keep sound and shortcuts out of the lobby?
6. **"Join game" on the Game selection**: also offer one place to paste any invite link on `/games`
   (the game is inferred from the id)?
7. **Host leaves the lobby**: close the lobby (cheapest, simple), transfer the host to the next seat
   (needs a movable host and renumbering), or close only when no other human remains (recommended,
   in two steps: close first, transfer later)? Section 5.1 gives the costs.
8. **Terminology**: confirm the glossary of section 0 (Game selection, Game home, Lobby, Table,
   Game options, Seat options, Local preferences), and the French UI words to use.
