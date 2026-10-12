# Shared game home and lobby for Bataille Corse and Bullshit (issue #122)

Status: revised after the owner's answers (decisions in section 3 are confirmed unless marked
**[proposed]**). Classification: architectural (it changes the Bataille Corse session model, adds a
kernel concept and touches both frontends). Open questions are in section 14.

Implementation status: see the table at the top of `docs/plans/2026-10-11-shared-lobby-and-menu-plan.md`.

## 0. Glossary (product term, validated by the owner, and its name in the code)

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
| Rematch | In place: `/rematch` then `SessionService.requestRematch`/`rematch` keep the seats (bots included, they never vote) and deal again; solo is instant | `playAgain` reopens an **empty** lobby with the same options (bots are dropped: `SessionGame.create` from scratch) | Falls out of the room model; "Play again" now leads to the lobby for both (section 4.1, 9.1). |
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
- Terminology of section 0 is validated.
- **(g)** Host leaving the lobby: the lobby closes only when no human remains; otherwise the next human
  becomes host (section 5.1).

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
  `.../join/:id?` and `.../room/:id` paths of both games already have this exact shape, so they keep
  resolving without a redirect; only `/games/bataillecorse/create`, `/games/bullshit/create` and the old
  route *names* (`home`, `create`, `join`, `room`, `bullshit-*`, used in code) need redirects or
  replacing. `/games/:game` with a key absent from the registry (e.g. `kobo` before its frontend)
  redirects to `/games`. Every "Back" from the Game home leads to the Game selection `/games`.
- **Creating a lobby** goes through REST: `POST /api/lobby` (`{gameType, name}`) returns `{gameId, token}` to the
  caller only (like join). Today `/bullshit/create` and `/create` answer with `@SendTo("/topic/game")`:
  every connected client receives the host token of every new game, and `BullshitSession.onLobby`
  adopts the first `CREATE` of the right type, so two simultaneous creations can swap games
  (verified). The new flow sends no token on a shared topic.

### 4.0 Arriving on a room link that cannot be restored (decided here)

Not an exit (the player did not choose to leave), so it does not follow section 4.1:

- Room URL opened without a stored token (someone shared the room URL instead of the invite link):
  go to the join form of that game with the id prefilled (`/games/<game>/join/<id>`).
- Stored token but the game no longer exists or the token is refused: go to the Game home of that
  game (`/games/<game>`) with a "This game no longer exists" notice, so New game / Join game are one
  click away. Today `useGameBootstrap.ts` (lines 29 and 35) sends both cases to the BC `home` route,
  whose name disappears in PR 1; Bullshit gets the same rule.
- A failed join from `JoinGameForm` stays on the form with its error line.

### 4.1 Where leaving a game leads (decided by the owner)

At the end of a game, **Play again** leads back to the **Lobby** (current behaviour, kept: `playAgain`
reopens the lobby with the same options), and the other button (Back to home / leave) leads to the
**Game selection `/games`**. This holds for both games, Bataille Corse included. The Lobby's Leave
button (#128), Back during a game and a forfeit also lead to `/games`. There is no per-game exit route:
`GameDefinition` carries no `homeRoute` for exits.

Consequences: Bullshit stays as it is since #129 (`/games`); Bataille Corse's end screen and Back, which
today lead to its old `home` menu (default `{ name: 'home' }` of `EndGameOverlay`, and the Back link
`<RouterLink :to="{ name: 'home' }">` in `view/alpha/GameScreen.vue`), switch to `/games`.
`cypress/specs/bullshit-exit-to-games.cy.ts` (#129) must stay green unchanged.
The `homeRoute` prop that #129 added to `EndGameOverlay` becomes unnecessary: the default becomes
`/games` for everyone and the prop is removed in PR 1. Tests: one per exit path per game asserting
`/games`, and one per game asserting that Play again lands in the Lobby.

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
- **One lock for every lobby mutation** (section 5.2). Today only `playAgain` and the rematch methods
  are `synchronized`; `joinRoom`, `addBot`, `removeBot` and `startGame` are not, while
  `SessionGame.players` is a plain `LinkedHashMap` of mutable `SessionPlayer` (verified). Two
  simultaneous joins can already claim the same seat and get the same token, and a join between
  `claimedCount()` and `repository.save` in `startGame` gets a seat absent from the dealt game.
- **Ordered, correctly addressed broadcasts**: the per-viewer views are computed *with the token of
  each viewer* inside the lock, then sent by token (not by seat number, which a later shift may give
  to someone else). `LobbyView` carries a `lobbyVersion` incremented by every mutation; the client
  drops a view older than the last one it applied. Today `LobbyBroadcaster` computes the views, then
  `sendToSeat` re-reads the seat's token at send time, outside any lock (verified).
- Decoupling: `GameLobby` takes `lobby: LobbyView` and emits `start`, `leave`, `add-bot`,
  `remove-bot`, `set-seat-option`, `set-option`, `rename`; a `LobbyActions` composable sends them to
  `/lobby/*`. The component imports no store.
- A11y: option groups are `fieldset`/`legend` with radios; read-only values are plain labelled text;
  the tier selector is a labelled group per bot; remove buttons keep their accessible names; targets
  at least 44 px.

### 5.1 Leaving the lobby (issue #128)

Command `leaveLobby(gameId, token)`, lobby phase only. A game in progress keeps the forfeit/presence
flow; after the game, the rematch departure flow.

**Rule (decided by the owner)**: the lobby is never closed while a human remains. A guest who leaves
frees their seat. If the host leaves and at least one human remains, the next human becomes the host.
The lobby is removed only when no human remains (bots alone). Every leaver goes to the Game selection
(section 4.1).

**Mechanism, without a variable host**: `SessionGame.HOST_SEAT` stays the constant seat 0,
`isHost(playerId)` stays `HOST_SEAT.equals(playerId)`. A new `SessionGame.vacate(PlayerId)` keeps the
seats contiguous (a game is dealt to seats 0..claimed-1), so the host's departure naturally makes the
next human seat 0. One rule is needed so that seat 0 is never a bot: when seat 0 is vacated, the first
*human* by seat order moves to seat 0, and the remaining occupants (humans and bots) follow in their
existing order. When any other seat is vacated, later occupants shift down one seat, as `removeBot`
already does for bots.

**Token hygiene (blocking, verified on `main`)**: a `SessionPlayer` token is `final` and belongs to
the seat *record*, not to the claim; `release()` keeps it; `SessionGame.findPlayerByToken` does not
check `isClaimed()`, so a freed seat's token still resolves; `claimNextFreeSeat` hands out
`claimed.token()`. Without a fix, a guest who leaves keeps a working token, and the next newcomer on
that record receives the *same* token: the leaver's still-open STOMP subscription (the connection is
an app-wide singleton that survives SPA navigation) then receives the newcomer's private messages
(the Bullshit hand), and the leaver can act for them. Rules:

- `vacate` rebuilds the seat map: each occupant keeps its own token on its new seat; **every seat
  left free gets a fresh `SessionToken.generate()`** (a new `SessionPlayer`).
- `findPlayerByToken` and `findTokenByPlayer` resolve **claimed** seats only.
- Tests: the leaver's token resolves to nothing; a newcomer's token differs from the leaver's; every
  shifted occupant still resolves to its new seat.

**Default names (M1)**: `defaultName()` is `"Player " + (id + 1)` frozen at claim time; after a shift
"Player 3" may sit at seat 1 and a newcomer at seat 2 also be "Player 3". Default human names are
renumbered after a shift, exactly as `renumberBots` does for "Bot N" (only names matching
`Player \d+`; a chosen name is kept).

**Does it hold? Verified against `main`**

| Concern | Finding |
|---|---|
| Tokens / `PlayerId` of shifted players | `SessionPlayer` has a final `id` and a final `token`, and `SessionGame.players` maps seat id to record. The shift rebuilds the entries (`new SessionPlayer(newSeatId, sameToken)` plus copied state) inside `SessionGame`; freed seats get fresh tokens (token hygiene above). The token stays with the person, so authentication still works. Main new code and main test target. |
| Messages reach the right client | Seat channels are addressed by **token** (`GameMessagingService.sendToSeat` looks up the token of the seat; `SeatSubscriptionInterceptor` checks the token at subscribe time). A shifted player keeps their subscription and receives their lobby view with no re-subscription. The broadcaster must send by the token captured under the lock (section 5, behaviour), not re-read it by seat number. |
| Local seat on the client | The server is the source of truth for `mySeat`: every lobby view and every game state sets it. `Bullshit.store.mySeat` is set today only by the `seat-change` event at join/restore (`BullshitSession.bind`), and the restore takes the seat from the key of the stored entry (`bullshit:tokens:<id>` = `{seat: token}`; BC `tokens:<id>` likewise). The stored seat key is only a hint; after a restore the client takes `mySeat` from the GET response. Stored entries are rewritten when the server reports another seat. |
| Bot seats | Bots carry no identity: they shift with the order (existing behaviour of `removeBot`, including renumbering default names "Bot N"). Their seat options (Bataille Corse tier) move with them. |
| Names and seat options | Move with the occupant (same rebuild); default human names renumbered (M1). |
| Presence | **Real risk, solved by PR-presence.** `PresenceService.onPresence` binds `connectionId -> Seat(gameId, playerId)` in `InMemoryConnectionRegistry` even in a lobby (`/presence` is sent and re-sent on reconnect by `WebSocketService`). After a shift the bindings of the shifted players are stale, and the **leaver's** binding is too: the leaver goes back to `/games` with the connection still open; once the game started, closing that tab would start the 60 s forfeit of whoever now holds the leaver's old seat number. A "rebind" inside `leaveLobby` is not allowed: `sessionmanagement.core` must not depend on `presence` (`package-info`; `presence` already depends on `core` through `GameDirectory`). **Fix**: the registry binds `connectionId -> (gameId, token)` and the seat is resolved through `GameDirectory` when the disconnect happens; a token that no longer resolves (the leaver's) does nothing, a shifted player is followed automatically. Change confined to `presence`. |
| Rematch | `playAgain` reopens a lobby, clears rematch flags and gives seat 0 to the first caller; leaving only happens in the lobby phase. A leave during the finished-game phase keeps using `leaveRematch`. |
| `LobbyView.hostSeat` | Already a field; stays 0. |

**Alternative considered and rejected (option B)**: stable seat numbers during the lobby (gaps
allowed), host designated by token, and a single compaction to seats 0..k-1 inside `startGame` under
the lock. Gains: no `mySeat` change during the lobby, `removeBot` loses its
`BotRemovalBlockedException` rule. Costs: `SessionGame` (a record) needs a mutable host; compaction at
start still moves tokens and seat numbers, client side included (BC derives its index from the stored
seat key); the Bullshit lobby visibly changes (gaps in the seat list, seat order of #125); `startGame`
changes for three games. Same size (about 1.5 to 2 days either way) and it removes none of the four
fixes that make the shift safe (token hygiene, presence by token, single lock, ordered broadcasts).
`vacate` is kept.

**Real risks of the transfer rule**

1. The data move in `SessionGame` (final `id` and `token`): easy to get subtly wrong (a token left on
   the wrong seat authenticates the wrong person). Needs exhaustive tests: every position of the
   leaver, with humans and bots before and after, plus the token hygiene tests.
2. Presence bindings: solved by binding tokens (PR-presence), tested with a shift then a start then a
   disconnect, and with a leaver who disconnects after the start.
3. The client must take `mySeat` from the server and rewrite its stored tokens; a missed update shows
   a guest as non-host (the token still protects the server). `lobbyVersion` prevents a late, older
   view from undoing a newer one.
4. Play order changes when the host leaves (the first human jumps ahead of bots): acceptable in a
   lobby, but visible; the lobby live region announces "You are now the host".
5. Races, see edge cases 3, 4 and 7, all under the single lobby lock (section 5.2).

**Client on Leave**: after `/lobby/leave` is sent, the client calls
`webSocketService.unsubscribeFromSeat()`, `unsubscribeFromGame()` and `clearPresence()`, removes its
stored token entry for the game, then navigates to `/games`. The server-side token hygiene makes this
a courtesy, not the protection.

**Edge cases**

1. *Host leaves and only bots would remain*: no human is left, so the lobby is removed through the
   same path as eviction (`GameCleanupService` notifying every `GameEvictionListener`: presence
   registry and forfeit log cleanup, bot coordinators), not a bare `repository.remove`. No broadcast
   target. A late request on that id gets a 404.
2. *Host leaves while other humans and bots are present*: the first human by seat order becomes seat 0
   and host; everything else follows; one broadcast. Bots never become host.
3. *Two simultaneous departures*: `leaveLobby` takes the single lobby lock (section 5.2) and
   resolves the seat from the token inside the lock, so the second call sees the shifted seats; if the
   second leaver was the last human the lobby is removed. A token that no longer resolves (already
   left) returns an "invalid token" error that the client ignores.
4. *Leave at the moment of Start*: `startGame` and `leaveLobby` take the same lock; whichever runs
   first wins. If Start wins, the leave is refused (`GameAlreadyStartedException`) and the leaver is
   handled by the forfeit flow like any player of a running game. If Leave wins, `startGame`
   re-checks `claimedCount` against `minPlayers` and the host gets the usual "not enough players"
   error. The deal always uses the post-shift seats.
5. *Invite link used after the host left*: the link is the game id, not a seat, so it still works
   while a human remains: the newcomer takes the lowest free seat and the lobby has its new host. If
   the lobby was removed, the join returns 404 and the join form shows its error.
6. *Closed tab instead of Leave*: unchanged (no timer in a lobby, section 2.2). A ghost host would
   block the lobby: noted as a follow-up (disconnect grace timer on lobby seats), not part of v1.
7. *Join while someone leaves*: both take the lobby lock; the join sees either the seats before the
   shift or after it, never half a shift, and always gets a token nobody else holds.

**Presence and forfeit, summary**: neither applies to the lobby phase; Leave is the only way to free a
seat before the start; the connection registry follows seat shifts because it binds tokens, not seat
numbers.

### 5.2 Concurrency of the session core

- Every operation that reads or mutates a lobby's seats or options runs under one lock: `joinRoom`,
  `addBot`, `removeBot`, `startGame`, `playAgain`, `updateOptions`, `renameSeat`, `setSeatOption`,
  `leaveLobby`, and the computation of the per-viewer views with their tokens. v1 uses the service
  monitor (`synchronized`, as `playAgain` already does): traffic is tiny and a per-game lock can
  replace it later without changing callers.
- The use case returns the views with their tokens (`List<AddressedLobbyView>`), computed inside the
  lock; the controller sends them by token. `lobbyVersion` is a counter on the lobby, incremented by
  every mutation, carried by each view.
- Tests: two threads joining the last free seat (exactly one wins, distinct tokens); join versus leave;
  join versus start (the joiner either is dealt in or is refused with "already started"); leave versus
  start in both orders; a burst of mutations ends with every client holding the highest version.
- Kobo uses the same `joinRoom`/`startGame`/`playAgain`: its IT (`KoboWebSocketControllerIT`) is part of
  the regression run.

## 6. Backend model for game options **[proposed]**

### 6.1 Descriptors

In the shared kernel `game`:

```java
public record OptionDescriptor(
        String key,                 // "claimMode", "deckSize"
        OptionType type,            // CHOICE (v1); BOOLEAN / INT_RANGE reserved, not built
        List<String> values,        // permitted values, display order
        String defaultValue,
        String labelKey) {}         // locale key, e.g. "options.bullshit.claimMode"

public interface GameFactory {
    ...
    default List<OptionDescriptor> optionDescriptors() { return List.of(); }
    /**
     * Strict domain validation of a full option map; throws InvalidOptionException.
     * Default: any key absent from optionDescriptors() is refused, any value outside its
     * descriptor's values is refused, missing keys take their default.
     */
    default GameOptions validate(GameOptions requested) { ... }
}
```

- Value labels come from the frontend locales (`<labelKey>.<value>`), unknown label falls back to the
  raw value. The backend never sends text. A label that depends on another option (the Bullshit
  claim mode label quotes the deck size) is resolved by the frontend, which receives the whole option
  map: no `dependsOnKey` in the kernel (presentation concern).
- **Strict validation (verified gap)**: `BullshitOptions.from` never refuses anything,
  `ClaimModeOption.fromKey` and `DeckSize.fromKey` fall back to the default on an unknown value. So
  `validate` cannot be "call `from`". `BullshitFactory.validate` uses a new strict parse in the domain
  (`BullshitOptions.parseStrict`, throwing `InvalidOptionException` on an unknown key or value);
  `from` stays lenient for `createRoom` and stale clients. With the default `validate`, a Bataille
  Corse or Kobo host can no longer store arbitrary keys (today any map would be stored and
  broadcast).
- `BullshitFactory` declares `claimMode` and `deckSize` from `ClaimModeOption` and `DeckSize`
  (single source of truth). `BatailleCorseFactory` and `KoboFactory` declare none, so every key is
  refused for them.
- `GameFactories` exposes `optionDescriptors(type)` and `validate(type, options)`;
  `sessionmanagement` still depends only on the kernel.

### 6.2 Update command

`SessionService.updateOptions(GameId, hostToken, Map<String,String>)`: refuse if started or not the
host (reuse `openLobbyOwnedBy`), under the lobby lock (section 5.2); merge, `validate` through the
factory (the whole merged map, so an unknown key is refused), store by saving the lobby with the new
options (`SessionGame` is a record; same seat map), bump `lobbyVersion`, return the addressed views.
A refused update leaves the options unchanged.

Transport: game-agnostic `LobbyController` (like `LifecycleController`) with `/lobby/setOptions`,
`/lobby/addBot`, `/lobby/removeBot`, `/lobby/setSeatOption`, `/lobby/rename`, `/lobby/leave`. The token
proves membership of the game, so no game-type check is needed there. `/bullshit/addBot` and
`/bullshit/removeBot` are **deleted** in the PR that adds `/lobby/addBot` and `/lobby/removeBot`
(PR 3), with the Bullshit client switched in the same PR: the SPA is served by the same deploy as
the backend, so an alias would only serve a tab left open across a deploy, which loses its in-memory
game anyway. `start` stays per game (it broadcasts the game's own state). Per-game REST endpoints
(create, join, play-again, GET) keep checking the game type (as the Bullshit ones do; the BC
`/api/game/{id}` ones do not today).

Lobby creation: one shared `LobbyRestController`, `POST /api/lobby` (body `{gameType, name}`, type checked against `GameFactories`), returns `{gameId, token}` to the caller only
(section 4). The WebSocket `/bullshit/create` and `/create` answers on `/topic/game`
are removed once their clients have switched (PR 5 for Bullshit, PR 9 for Bataille Corse). Kobo's
`/kobo/create` is gone: PR 6 (#136) removed it when it added the shared endpoint, its IT switched.

### 6.3 What the lobby view carries

`LobbyView` gains `lobbyVersion`, `optionDescriptors`, `seatOptionDescriptors`, `canEditOptions` (host, not started)
and, per `LobbyPlayer`, `seatOptions` (a `Map<String,String>`, empty for humans and for Bullshit
bots). `options` keeps its `Map<String,String>` shape; defaults are filled server side.

### 6.4 Compatibility

- `createRoom(..., GameOptions)` and `BullshitCreatePayload` (`claimMode`, `deckSize` optional) keep
  working for stale clients and tests. The new flow sends neither; defaults equal today's (rank, 32).
- The Bullshit claim mode label depends on the deck size: the frontend label resolver receives the
  whole option map and passes the current deck size as a placeholder.
- `playAgain` copies `existing.options()` and, from PR 4b, the bot seats with their seat options
  (section 9.1).
- The session core still never interprets a key; `validate` is the game's.

### 6.5 Rule placement

Validation and defaults live in the game's domain types. The session core enforces *who* and *when*,
never *what*. The frontend renders descriptors and sends `{key, value}`.

## 7. Seat options **[proposed]**

- **Name**: `SessionGame.renameSeat(PlayerId, String)`; the occupant only, lobby only, trimmed, 20
  characters max, blank restores "Player N". Bot names stay "Bot N" rendered by the frontend. Fixed
  once the game starts (question 2).
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
  - `setSeatOption(gameId, hostToken, seat, key, value)`: host only, bot seat only, lobby only,
    under the lobby lock; unknown key or value refused by the game (same strict rule as game
    options).
- `removeBot` keeps its rules (`BotRemovalBlockedException`); a bot shifted down by a removal
  carries its seat options.
- **Reaching the bot driver**: the existing port `BotSeats` (`Set<PlayerId> botSeats(GameId)`) is used
  by `BullshitBotCoordinator`. Rather than change its signature (which would make PR 7 edit Bullshit
  code), a second port `BotSeatOptions` (`Map<String,String> seatOptions(GameId, PlayerId)`) is added
  next to it in the shared bot package (PR 0) and answered by the session core. Bullshit keeps
  `BotSeats` untouched.

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
| `BatailleCorse.version()` (domain) | A counter incremented by `send`, `slap`, `grab` and `forfeit`, read under the game's monitor (as `Bullshit.version()` and `Kobo.version()`). Does not exist today (verified) | ~15 lines |
| Auto-grab query (domain) | `BatailleCorse.autoGrabPlayer()`: the player entitled to grab, or empty. Today only the frontend has it (`Pile.getAutoGrabPlayer()` from `grabbable` and `playerThatAddedLastHonourCard`); the backend has `isGrabbableByPlayer` only | ~15 lines |
| `BatailleCorseStateBroadcaster` + listener | Today the controller builds `BatailleCorseDto` inline and calls `sendToGame`: there is nothing a coordinator can listen to. New broadcaster (as `BullshitStateBroadcaster`) with a `BatailleCorseStateListener`, fired after every send, slap, grab, start (PR 6), forfeit and lobby reopening | ~80 lines |
| `BatailleCorseGameActions` | Extract the send/slap/grab logic out of `BatailleCorseWebSocketController` so a bot and a human use the same use case (as `BullshitGameActions`), broadcasting through the new broadcaster | ~120 lines moved |
| `BatailleCorseBotCoordinator` | After each state broadcast: drop stale tasks, schedule the bot's action after its tier delay, re-check the game version under the monitor before acting; stop when no human is left or the game is over; **run the bot's auto-grab** after 1500 ms when `autoGrabPlayer()` is the bot. Lifecycle: starts on START, stops on forfeit and on game end, drops its table on eviction (`GameEvictionListener`) and when the lobby reopens (Play again), restarts on the next START | ~220 lines |
| Wiring | `AppConfig` beans, `BotSeatOptions` port so the tier reaches the coordinator, `bataillecorse.bots.*` properties (below) | ~60 lines |
| Tests | strategy, tiers, version, auto-grab query, broadcaster, coordinator with `ManualBotScheduler`/`FixedThinkingDelay` (already exist for Bullshit), full game IT with a bot | 7 to 8 classes |

**Test profile**: the `test` profile pins the Bullshit bot delays to 0, which for Bataille Corse
would make the bot slap and grab instantly and every e2e spec racy. PR 7 adds
`bataillecorse.bots.extra-delay-ms` (added to the tier delay, `0` by default and in the `test`
profile) and `bataillecorse.bots.enabled` (`true`); the `test` profile does **not** zero the tier
delays: the BC e2e specs passed until now against the local AI playing at its real tier delays, and
keeping them gives the same timing. Backend tests never depend on these properties: they use
`ManualBotScheduler` and `FixedThinkingDelay`. If a rewritten spec proves flaky, the e2e stack can set
`extra-delay-ms` without touching the code.

Total: roughly 600 lines of production code and as much test code, **three to four working days** for
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
   know animation durations; a fast bot (400 ms) may send while a card is still flying. In
   multiplayer the client already receives the opponent's moves from the server with no regard for
   its own animations, so this path exists today (hypothesis, not checked in the browser: the risk is
   probably smaller than first thought). Mitigation: verify in the browser; if needed, use
   `extra-delay-ms` as a floor or make the Table tolerate server events during an animation.
4. **Slap decisions on stale state**: a bot must not slap a pile that changed during its delay. The
   version check of the Bullshit coordinator solves it once `BatailleCorse.version()` exists; to be
   asserted in a test. Human actions already go through the `synchronized` domain methods; the
   coordinator re-checks the version inside `synchronized (game)`.
5. **Penalty parity**: the old local AI accepted any penalty from a bad slap; the heuristic never
   slaps wrongly, so this should not change, but the new strategy reads the real rules, and any
   difference with the old heuristic (e.g. a rule present in the domain but not in the heuristic) will
   make the bot slightly stronger. List the differences in the PR.
6. **Session model migration** (PR 9, not PR 7): deleting `SOLO`, the two-token rehydration and
   `restoreTokens` for two seats touches reconnection logic; an in-progress solo game during a
   deploy is lost (state is in memory anyway).
7. **Rematch against a bot**: Play again leads to the lobby (section 4.1). Today BC's in-place
   `/rematch` keeps the bot and the shared `playAgain` drops every bot; section 9.1 makes `playAgain`
   keep the bot seats and their seat options.
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
- **Rematch = Play again, through the lobby** (section 4.1). Today BC uses its own in-place rematch
  (`/rematch`, `SessionService.requestRematch`/`rematch`, unanimity of humans, seats and bots kept);
  Bullshit and Kobo use `playAgain`, which reopens an empty lobby and drops the bots. Decision:
  - `playAgain` **keeps the bot seats with their seat options** (tier) when it reopens the lobby: the
    fresh `SessionGame` re-claims the former bots, in order, right after the first caller's seat 0
    (and keeps their default names "Bot N"). Humans still come back one by one. Applies to every game,
    Bullshit included (proposed, question 1). Done in a small core PR (PR 4b) with its tests.
  - BC switches its client to `play-again` in PR 9. The BC `/rematch` endpoint and, if nothing else
    uses them, `SessionService.requestRematch`/`rematch`/`isRematchReady` and
    `SessionGame.requestRematch`/`isRematchUnanimous` become dead. **Check before deleting**:
    `PresenceService.forfeit` on a finished game calls `GameDirectory.leaveRematch`, which itself
    calls `isRematchReady`/`rematch`; it must be reduced to "nothing to do" (no in-place rematch any
    more) or kept, and `PresenceRematchTest` adjusted accordingly.
- Reconnection: a solo game now holds one human token instead of two; client rehydration must stop
  expecting two.
- Cypress specs relying on SOLO or on the old BC routes must be rewritten: `create-game`,
  `send-card`, `rehydrate-game`, `waiting-and-names`, `bataillecorse-menu-fit`, `turn-indicator`,
  `responsive-game-screen`, `rules-panel`, `visual-direction`, `font-fit` (visits
  `/games/bataillecorse`), `game-picker`, and above all the shared command
  `cypress/support/commands.ts` `createGame`, which drives the solo "Computer" flow used by most of
  them. `bullshit-exit-to-games` stays as is.

### 9.2 Game options for Bataille Corse

None (decision d). The Game options section of the lobby is hidden for BC. Slap rules and penalty
stay constants in the domain. (If one day wanted, descriptors are enough; no lobby change.)

### 9.3 Local preferences

BC keyboard shortcuts move from the create page to a gear on the BC Table by generalising
`HotkeySettings.vue`/`useHotkeySettings` (currently Bullshit-only). Storage keys `bc_sendKey` and
`bc_slapKey` are kept. The `bc_difficulty` value is no longer a global setting: the tier is a seat
option chosen in the lobby; the stored value can seed the default tier of a newly added bot (small,
optional: question list does not include it; default is the fixed Bronze).

### 9.4 What removing "solo mode" means, exactly

**In plain words.** Today "solo" is a special way of creating a game: the server creates the game
already dealt, gives *one browser* the keys (tokens) to *both* seats, and that browser plays the second
seat itself with a small program (the local AI). After the change there is no special mode: a "solo"
game is an ordinary lobby where the host adds a bot to the second seat. The bot runs on the server and
has no key at all. Nothing about how a human plays changes; what changes is how the game is set up and
who moves the opponent's cards.

**What is deleted** (all verified in the code):

| Where | What | Why it goes |
|---|---|---|
| `sessionmanagement/core/application/GameMode.java` | the enum `SOLO` / `MULTIPLAYER` | no more modes |
| `SessionService.createGame(type, n, mode, name)` | the `if (mode == SOLO) sessionGame.claimAllSeats()` branch, and the whole `createGame` family | rooms are created by `createRoom` |
| `SessionGame.claimAllSeats()` | one owner claims every seat (all tokens held by one client) | no one owns several seats any more |
| `SessionService.joinGame` | joins the fixed seat 1 | replaced by `joinRoom` (next free seat) |
| `presentation/api/CreateGamePayload.mode` | the `mode` field (`GameMode`) | nothing to choose at creation |
| `BatailleCorseWebSocketController` `/create` | `GameMode mode` defaulting to `SOLO`, and `seatsToReturn = (mode == SOLO) ? NB_PLAYERS : 1` (returns one token per seat in solo) | the create response returns only the host's token, like Bullshit |
| `BatailleCorseRestController.joinGame` | the fixed-seat join | same |
| Frontend `GameSession.ts` | `create('solo'|'multiplayer')`, `serverMode = 'SOLO' | 'MULTIPLAYER'`, `mode` and `waiting` state, `isLocalSeat` (solo controls every seat), the `ai.play(...)` block "puppet AI only drives player 1 in solo", the solo rematch path, `restoreSession` inferring the mode from the number of stored tokens | the browser controls exactly one seat |
| `model/ai/AI.ts` (+ test) | the local AI that plays seat 1 using the second token | replaced by the server bot (section 8) |
| Persisted tokens in `localStorage` | `tokens:<gameId>` holding `{0: t0, 1: t1}` in solo | one entry from now on |
| `StartGame.vue` | the Human / Computer toggle and tier slider | the lobby has Add bot and the tier selector |
| `WaitingOverlay.vue` | the invite overlay | the lobby shows the invite |

**What changes (and is kept)**

- Creating: New game opens the lobby. To play alone: Add bot, optionally pick a tier, Start. To play
  a friend: copy the link, wait for them, Start. The **host presses Start in both cases** (one rule
  for every game; the old "starts immediately" for solo and "starts when seat 1 joins" for friends are
  both gone). A one-click shortcut ("Play vs computer" on the Game home) is possible later but is not
  planned; see question 7.
- Tokens: a solo game now has one human token; the bot's token never leaves the backend (the existing
  rule for Bullshit bots). The hack "the creator's browser holds both tokens" disappears for good, and
  with it the possibility for a client to act as the bot's seat.
- Auto-grab: the pile is collected for the bot by the server (section 8.3 risk 1).
- Rematch: shared `playAgain` through the lobby, keeping the bot and its tier (section 9.1).

**Tests to rewrite**

- `BatailleCorseWebSocketControllerIT` (currently `@Disabled`, stale): its header says `/app/create`
  answered "with one token per seat in SOLO mode" and it has `givenSoloGame_whenCreateGame_thenOneTokenPerSeatIsReturned`
  and a helper sending `{"mode":"SOLO"}`. It is rewritten around the lobby flow (create, join or add
  bot, start, then send/slap with each seat's own token). The "distinct token per seat" assertion
  becomes "each human seat gets its own token, a bot seat has none".
- Backend: `SessionServiceTest`, `SessionServiceGenericGameTest`, `SessionGameTest` cases using
  `createGame`, `GameMode` or `claimAllSeats`; `GameCleanupServiceTest` if it creates games that way
  (grep before the PR).
- Frontend: `GameSession.test.ts` (solo and AI cases), `BatailleCorse.store` tests, `GameScreen.test.ts`
  (computer label), `AI.test.ts` removed; Cypress: the shared command `support/commands.ts`
  `createGame` first, then `create-game`, `send-card`, `rehydrate-game`, `waiting-and-names`,
  `turn-indicator`, `responsive-game-screen`, `rules-panel`, `visual-direction`,
  `bataillecorse-menu-fit`, `font-fit`, `game-picker`.
- Backend rematch: `PresenceRematchTest` and the BC `/rematch` tests if the in-place rematch is
  removed (section 9.1).

**Compatibility**

- No data migration: sessions are in memory only (no database), so a deploy already drops every game.
  No stored server data refers to the mode.
- Browsers with old tokens: `tokens:<gameId>` entries from earlier solo games may remain in
  `localStorage`. They point at games that no longer exist on the server; restoring them already ends
  with the existing "game not found" handling and a new game. The restore code must tolerate (ignore)
  a two-token entry rather than crash (a test). Old entries can be left to expire or be cleaned when
  the Game home loads.
- A solo game in progress during the deploy is lost, as any game is.
- Invite links of two-player games: unchanged (`/games/bataillecorse/join/<id>` already matches
  `/games/:game/join/:id?`).

**What disappears for good**: the ability to play a game without a server-side bot or room (a purely
local practice mode), the 9-tier slider on a create page (the tiers survive as a seat option), the
"Computer (Gold)" label wording (the seat is "Bot 1" with its tier), and any code path where one client
holds several seats.

## 10. Accessibility and mobile

- 375 px: the Game home and the lobby fit with no page scroll and no horizontal scroll (precedents:
  `bataillecorse-menu-fit`, Bullshit lobby specs). The lobby is the tallest screen: the card fan
  hides on short viewports, seat rows are one line, the panel scrolls inside itself if a game
  declares many options, never the page. Worst case to test: Bullshit, 6 seats, 2 options, invite,
  Start and Leave at 375 x 667. This case very probably needs the panel's inner scroll (hypothesis:
  six one-line seats with an editable name, two radio groups, the invite, Start and Leave exceed
  667 px). The Cypress spec asserts "no page scroll, no horizontal scroll, Start reachable by
  scrolling the panel", not "everything visible without scrolling".
- Keyboard: logical order; Enter commits a name; Escape reverts.
- Screen readers: lobby changes announced in one `aria-live="polite"` region ("Bot 1 added", "The host
  changed the deck size", "Alice left"); option groups with `legend`; Copy keeps `role="status"`.
- Colour never carries meaning alone; the tier is shown by name.
- All strings in locales (`home.*`, `lobby.*`, `options.<game>.<key>[.<value>]`, `seatOptions.<game>.*`).

## 11. Tests

- Backend (TDD, domain first): descriptors per factory (defaults valid, strict `validate`: unknown
  key and unknown value refused, `from` still lenient);
  `updateOptions` (host only, refused after start, invalid refused and options unchanged, merged,
  kept on `playAgain`);
  `renameSeat` (default names renumbered after a shift); seat options (host only, bot only,
  validated by the game, kept when a bot shifts, kept by `playAgain`);
  `leaveLobby` (guest frees the seat, host leave per the rule, refused during a game);
  **token hygiene** (the leaver's token resolves to nothing; a newcomer gets a token different from
  the leaver's; shifted occupants resolve to their new seats; a released seat's token never resolves);
  **concurrency** (section 5.2: join/join, join/leave, join/start, leave/start, version ordering);
  **presence by token** (shift then start then disconnect forfeits the right seat; a leaver who
  disconnects after the start forfeits nobody); lobby removal notifies the eviction listeners;
  `LobbyView` projection with `lobbyVersion`; STOMP ITs with two clients for options, rename, leave;
  shared `POST /api/lobby` (token only to the caller, unknown type refused); Bataille Corse bot
  (section 8.2) with manual scheduler and fixed delay; BC room flow IT; Kobo IT unchanged and green.
- Frontend (Vitest): `GameHome`, `JoinGameForm`, `GameLobby` (host vs guest, options, seat options,
  rename, leave, Copy fallback), label resolver receiving the whole option map, `LobbyActions`
  (older `lobbyVersion` ignored; `mySeat` taken from every view; on Leave: unsubscribe from seat and
  game, clear presence, remove the stored token), router (legacy create paths and names, unknown game
  key, section 4.0 restore failures).
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

After the independent architecture review (all points verified on `main` at `2b8e4b4`):
token hygiene on leave (fresh token for freed seats, claimed-only token lookup); presence bound by
token instead of seat number, in `presence` only (no rebind from the core); one lock for every lobby
mutation and addressed, versioned lobby broadcasts (`lobbyVersion`); strict option validation
(unknown keys refused; `BullshitOptions.from` stays lenient); lobby creation through
`POST /api/lobby` instead of `@SendTo("/topic/game")`; Bataille Corse bot estimate raised to 3 to 4
days with `version()`, auto-grab query, state broadcaster and lifecycle; `playAgain` keeps bots and
their seat options; restore failures (section 4.0); full list of e2e files to rewrite and the
`bataillecorse.bots.*` properties; default human names renumbered; lobby removal through the eviction
listeners; `dependsOnKey` and the `/bullshit/addBot` aliases removed; `BotSeatOptions` port instead of
changing `BotSeats`. Option B (stable seats, host by token) considered and rejected (section 5.1).

## 14. Open questions for the owner

1. **Rematch against a bot**: this spec now proposes that `playAgain` keeps the bot seats and their
   tier for every game (Bullshit included: today Bullshit's Play again drops its bots). Confirm, or
   keep bots only for Bataille Corse, or reopen an empty lobby as today.
2. **Renaming**: only in the lobby (this spec) or also during the game?
3. **Bataille Corse players**: stays at 2, or 3 to 4 someday (it would change only `maxPlayers` and
   the dealing rules)?
4. **Old invite links**: they already match the new routes (section 4), so they keep working at no
   cost; only `/create` paths and route names need redirects. Nothing to decide unless the owner wants
   the `create` redirects dropped later.
5. **Gear on the BC Table**: add the keyboard-shortcut gear to the Bataille Corse game screen (needed
   once the create page is gone), and keep sound and shortcuts out of the lobby?
6. **"Join game" on the Game selection**: also offer one place to paste any invite link on `/games`
   (the game is inferred from the id)?
7. **Start for a game against a bot**: the host always presses Start (this spec). Is a one-click
   "Play vs computer" shortcut on the Game home wanted later?
8. **Restore failures** (section 4.0, decided here, can be overridden): a room URL without token goes
   to the join form; a game that no longer exists goes to the Game home of that game with a notice.
