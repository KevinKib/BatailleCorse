# Shared Game home and lobby: implementation plan

Spec: `docs/specs/2026-10-11-shared-lobby-and-menu-design.md` (issue #122, and #128 for the Leave
button). Vocabulary follows the glossary of the spec (Game selection, Game home, Lobby, Table, Game
options, Seat options, Local preferences); the code keeps `room`, `SessionService`, etc.

Changes from the first draft of this plan: Bullshit bot levels (old PR5) and Bataille Corse game
options (old PR10) are dropped; the Bataille Corse bot keeps its 9 tiers; a Leave-lobby PR is added;
`GameEntryMenu` is renamed `GameHome`; #125, #126 and #127 are merged into `main`.

Changes after the independent architecture review (verified on `main` at `2b8e4b4`, spec section 13):
new PR-P (presence bound by token); PR 4 also hardens the session core (single lobby lock, token
hygiene, addressed and versioned broadcasts); new PR 4b (`playAgain` keeps bots and their seat
options); strict option validation in PR 2; lobby creation through `POST /api/lobby` (PR 6) instead
of `@SendTo("/topic/game")`; PR 7 re-estimated at 3 to 4 days; exits always lead to `/games`;
complete list of e2e files for PR 9; `dependsOnKey` and the `/bullshit/addBot` aliases dropped.

Rules for every PR: own worktree and branch `<type>/<subject>`, target `main`, TDD (failing test
first), all suites green before the PR (`cd backend && mvn -s ../deploy/docker/settings.xml test`,
`cd frontend && npm test && npx vite build`), texts through `frontend/src/locales/`, game rules in the
game's `domain/`. UI PRs follow the `ui-change` skill (before/after inventory of displayed information,
light and dark, 375 and 1280 px) and add or update a Cypress spec. A manual test environment is only
needed for PR 1, 5, 9; never for backend-only PRs. Every backend PR touching `SessionService` runs
`KoboWebSocketControllerIT` (Kobo shares `joinRoom`/`startGame`/`playAgain`).

Single-test commands:

- Backend: `cd backend && mvn -s ../deploy/docker/settings.xml test -Dtest=<ClassName>`
- Frontend: `cd frontend && npx vitest run <path>`
- E2E (one stack at a time, see `CLAUDE.md`): `docker compose -f docker-compose.e2e.yml up --build -d --wait`,
  then `cd frontend && npx cypress run --spec cypress/specs/<file>`

## Overview

```
PR0  shared bot ports + BotSeatOptions port (BE refactor) ─────────── independent
PRP  presence bound by token (BE, presence package only) ──────────── independent
PR1  Game home, exits to /games, restore failures (FE) ────────────── independent
PR2  game option descriptors, strict validation, updateOptions (BE) ─ independent
PR6  BC uses rooms + shared POST /api/lobby (BE, additive) ────────── independent
PR8  shortcuts gear for both games (FE) ────────────────────────────── after PR1 (GameScreen.vue)
PR3  seat options, rename, /lobby/addBot|removeBot (BE + 1 FE file) ─ needs PR2
PR7  BC server bot, 9 tiers (BE) ───────────────────────────────────── needs PR0, PR3, PR6
PR4  session hardening + leave lobby (BE) ──────────────────────────── needs PR3, PRP
PR4b playAgain keeps bots and their seat options (BE) ──────────────── needs PR4
PR5  GameLobby, adopted by Bullshit (FE) ───────────────────────────── needs PR1, PR2, PR3, PR4, PR6
PR9  BC adopts the lobby; local AI and solo deleted ────────────────── needs PR4b, PR5, PR7, PR8
```

Waves (one session each):

- **Wave A (parallel, now)**: PR0, PRP, PR1, PR2, PR6. PR8 starts now but rebases after PR1 (both
  touch `view/alpha/GameScreen.vue`; PR8 merges second). PR7's strategy, tiers, `version()` and
  auto-grab query (domain only) may be written now; they wait for PR0, PR3 and PR6 for the wiring.
- **Wave B**: PR3 (after PR2). PR7 (after PR0, PR3, PR6).
- **Wave C**: PR4 (after PR3 and PRP), then PR4b (after PR4). PR5's frontend can be developed against
  PR4's contract while PR4 is in review.
- **Wave D**: PR5 (after PR1, PR2, PR3, PR4, PR6).
- **Wave E**: PR9 (after PR4b, PR5, PR7, PR8), alone on the Bataille Corse files. E2E runs in series.

Critical path: PR2, PR3, PR4, PR5, PR9. The Bataille Corse bot (PR6, PR7) runs beside it.

## Conflicts

Rebase on `main` and re-check `gh pr list` before each task.

| File | Touched by | Risk |
|---|---|---|
| `view/alpha/GameScreen.vue` | PR1 (Back link and `EndGameOverlay` to `/games`), PR8 (gear), PR9 (lobby phase) | Medium: PR1, then PR8, then PR9 |
| `components/EndGameOverlay.vue` (+ test) | PR1 (prop removed, default `/games`), PR5 (`BullshitGameScreen` stops passing the prop) | Low: PR1 first |
| `composables/useGameBootstrap.ts` | PR1 (restore failures, spec 4.0), PR9 (one token, lobby phase) | Low: sequential |
| `view/bullshit/BullshitGameScreen.vue` | PR5 (lobby block, prop removal), PR8 (gear import line) | Medium: PR8 before PR5; keep both diffs minimal |
| `application/BullshitSession.ts` | PR3 (`/lobby/addBot`, `/lobby/removeBot` destinations), PR5 (create through REST, `mySeat` from server, Leave) | Low: sequential |
| `components/bullshit/BullshitLobby.vue` (+ test) | PR5 only (deleted) | Low: sole editor |
| `view/bullshit/BullshitStartGame.vue` (+ test) | PR1 (join mode removed), PR5 (deleted) | Medium: sequential |
| `state/Bullshit.store.ts` | PR5 | Low |
| `bullshit/presentation/bot/*` | PR0 (moves ports) | Medium: pure move; do not combine with behaviour changes |
| `sessionmanagement/presence/*` (`ConnectionRegistry`, `InMemoryConnectionRegistry`, `PresenceService`, `Seat`) | PRP only | Low: sole editor |
| `SessionService`, `SessionGame`, `SessionPlayer`, `LobbyView`, `LobbyBroadcaster`, `LobbyController` | PR2, PR3, PR4, PR4b (and PR6 for create/join callers) | High if parallel: PR2, PR3, PR4, PR4b strictly sequential; PR6 only calls existing methods |
| `BatailleCorseWebSocketController`, `BatailleCorseRestController` | PR6 (adds), PR7 (extracts `BatailleCorseGameActions`, new broadcaster), PR9 (removes) | Medium: PR7 after PR6 |
| `KoboWebSocketController` (+ IT) | PR6 (`/kobo/create` removed, IT switched to `POST /api/lobby`) | Low |
| `config/AppConfig.java` | PR0 (imports), PRP (registry bean), PR6 (`LobbyRestController` deps), PR7 (bot beans, properties) | Low: small hunks, rebase |
| `cypress/support/commands.ts` | PR1 (entry steps), PR9 (`createGame` rewritten) | Low: sequential |
| `locales/Messages.ts`, `locales/en.ts` | every frontend PR | Low but constant: one section per PR, rebase often |
| `router.ts` | PR1, PR5, PR9 | Low |

## PR 0: `refactor: share the bot scheduling ports` (backend, no behaviour change)

The Bataille Corse bot (PR7) needs `BotScheduler`, `ThinkingDelay`, `BotSeats` and the test doubles,
which live in `bullshit.presentation.bot`; a game may not depend on another game.

- Move to a shared package (for example `org.kevinkib.cardgames.presentation.bot`): `BotScheduler`,
  `TaskSchedulerBotScheduler`, `ThinkingDelay`, `UniformThinkingDelay`, `ThreadLocalRandomGenerator`,
  `BotSeats`; move the test doubles `ManualBotScheduler` and `FixedThinkingDelay` to a shared test package.
- Add the port `BotSeatOptions` (`Map<String,String> seatOptions(GameId, PlayerId)`, with a `none()`
  factory) next to `BotSeats`; not wired yet (PR3 answers it from the session core, PR7 uses it).
  `BotSeats` keeps its signature so Bullshit is untouched (spec section 7).
- Update imports in `config/AppConfig.java`, `BullshitBotCoordinator` and tests.
- Tests first: none new besides a trivial `BotSeatOptions.none()` test; `BullshitBotCoordinatorTest`,
  `TaskSchedulerBotSchedulerTest`, `UniformThinkingDelayTest`, `BotWiringTest` and the application
  context test stay green. No architecture test exists today (no ArchUnit in the repo); none is added.
- Conflict: none open. Parallel: yes (Wave A).

## PR P: `fix: bind presence to the seat token, not the seat number` (backend, presence only)

Spec 5.1 ("Presence" row). Needed before PR4 can shift seats; also fixes the leaver's stale binding.

- Modify (only `sessionmanagement/presence` and `LifecycleController`):
  - `ConnectionRegistry` binds `connectionId -> (gameId, token)` (new value type, e.g. `SeatClaim`).
  - `PresenceService.onPresence(connectionId, gameId, token)`; `onDisconnect` resolves the token to
    a `PlayerId` through `GameDirectory` at disconnect time (add
    `Optional<PlayerId> findPlayerIdByToken(GameId, String)` to `GameDirectory`, already implemented
    by `SessionService`); an unresolved token does nothing. Pending forfeits stay keyed by
    `Seat(gameId, playerId)` resolved at that moment; reconnect cancels by the resolved seat.
  - `LifecycleController.presence` passes the token instead of resolving it first.
- Tests first: `InMemoryConnectionRegistryTest` (bind/unbind by token), `PresenceServiceTest`
  (a token that no longer resolves starts no timer; a seat number that changed between bind and
  disconnect forfeits the new seat number of the same token), `PresenceRematchTest` unchanged and
  green. The full "shift then start then disconnect" scenario is asserted again in PR4.
- Parallel: yes (Wave A). Blocks PR4.

## PR 1: `feat: shared Game home for every game` (frontend)

- Create:
  - `frontend/src/model/games.ts`: `GameDefinition { key, titleKey, gameHomeRoute, joinRoute, roomRoute, createLobby }` and the registry. Exits from a game never use it: they always lead to `/games` (spec 4.1). `gameHomeRoute` is used by the Game selection and by the "game no longer exists" restore failure (spec 4.0).
  - `frontend/src/components/GameHome.vue`: title block, name field (persisted in `settingsStore.playerName`), New game, Join game, Back to the Game selection.
  - `frontend/src/components/JoinGameForm.vue`: game id or link (`extractGameId`, moved to `model/gameId.ts`), name, error line (a failed join stays on the form), Back.
  - Locale keys `home.*`, `join.*`.
- Modify:
  - `router.ts`: add `/games/:game` and `/games/:game/join/:id?`; the legacy `join`/`room` paths of both games already have this shape and keep resolving; redirect `/games/bataillecorse/create` and `/games/bullshit/create` to the Game home; replace every use of the old route names (`home`, `create`, `join`, `room`, `bullshit-*`) in code; a `:game` absent from the registry redirects to `/games` (spec 4, M4).
  - `GamePickerView.vue` (+ test): both cards open the Game home.
  - `view/alpha/StartGame.vue`: remove join mode; `view/alpha/LobbyView.vue`: deleted.
  - `view/bullshit/BullshitStartGame.vue`: remove join mode.
  - `components/EndGameOverlay.vue`: remove the `homeRoute` prop added by #129 and the default `{ name: 'home' }`; the home button always goes to `/games`; Play again is unchanged. `view/alpha/GameScreen.vue` stops passing a route and its Back link (`{ name: 'home' }`) goes to `/games`.
  - `composables/useGameBootstrap.ts` (lines 29 and 35, today `router.replace({ name: 'home' })`): no stored token goes to the join form of that game with the id prefilled; a failed fetch goes to the Game home of that game with a "This game no longer exists" notice (spec 4.0). The same rule for the Bullshit room screen.
  - Until PR5 and PR9, "New game" navigates to the game's existing create page (behaviour unchanged).
- Tests first: `GameHome.test.ts` (New and Join for both definitions, name persisted, targets), `JoinGameForm.test.ts` (bare id or full link, prefilled from route, error stays on the form, button disabled when empty), `router.test.ts` (legacy links resolve, `create` paths redirect, unknown game goes to `/games`, every Back reaches `/games`), `useGameBootstrap.test.ts` (both restore failures), `EndGameOverlay.test.ts` (home button targets `/games`; Play again does not navigate away), updated `GamePickerView.test.ts`.
- Cypress: update `game-picker.cy.ts`, `bataillecorse-menu-fit.cy.ts`, `font-fit.cy.ts` (visits `/games/bataillecorse`), the entry steps of `bullshit-table-and-lobby.cy.ts` and of `support/commands.ts` `createGame` (it navigates through the old menu); `bullshit-exit-to-games.cy.ts` stays unchanged and green; 375 px fit of the Game home.
- UI inventory: BC home and Bullshit create page before/after; nothing disappears, Bullshit gains a Join game button.
- Parallel: yes (Wave A). Merge value: both games share one home; no backend change.

## PR 2: `feat: games declare their game options and the host edits them` (backend)

- Create (kernel `game`): `OptionDescriptor(key, type, values, defaultValue, labelKey)` (no `dependsOnKey`), `OptionType` (`CHOICE` only), `InvalidOptionException`.
- Modify:
  - `GameFactory`: `optionDescriptors()` (default empty) and a **strict** default `validate(GameOptions)`: unknown key refused, value outside the descriptor refused, missing keys take their default.
  - Bullshit domain: `BullshitOptions.parseStrict(GameOptions)` throwing `InvalidOptionException`; `BullshitOptions.from` stays lenient (verified: `ClaimModeOption.fromKey` and `DeckSize.fromKey` fall back to defaults) for `createRoom` and stale clients.
  - `BullshitFactory`: descriptors for `claimMode` and `deckSize` from `ClaimModeOption` and `DeckSize`; `validate` through `parseStrict`.
  - `GameFactories`: `optionDescriptors(type)`, `validate(type, options)`.
  - `SessionService.updateOptions(GameId, hostToken, Map)` (reuse `openLobbyOwnedBy`); merge, validate the merged map, store by saving the lobby with the new options; a refused update leaves the options unchanged.
  - `LobbyView`: `optionDescriptors`, `canEditOptions`, defaults filled for missing keys.
  - New `presentation/LobbyController` with `/lobby/setOptions` (`LobbyOptionsPayload(gameId, token, values)`), shared broadcast, errors to the actor seat only.
- Tests first: `BullshitOptionsTest` (`parseStrict` refuses unknown key and value; `from` still lenient), `BullshitFactoryTest` (descriptors, defaults valid), `GameFactoryTest` or `GameFactoriesTest` (default strict validation: Bataille Corse and Kobo refuse any key), `SessionServiceOptionsTest` (host only, refused after start, invalid refused and unchanged, merge, kept by `playAgain`), `LobbyViewTest`, `LobbyOptionsIT` (two STOMP clients: the guest sees the change live), backward compatibility test (`createRoom` with options and `BullshitCreatePayload` still work, invalid values still default there).
- Commands: `mvn ... test -Dtest='SessionServiceOptionsTest,BullshitFactoryTest,BullshitOptionsTest,LobbyViewTest,LobbyOptionsIT'`, then full suite.
- Parallel: yes (Wave A). Blocks PR3.

## PR 3: `feat: seat options and player rename in the lobby` (backend, one frontend file)

- Modify:
  - `GameFactory`: `seatOptionDescriptors()` (default empty) and `validateSeatOption(key, value)` (strict by default, same rule as game options).
  - `SessionPlayer`: opaque `Map<String,String> options` (cleared by `release()`, carried when `removeBot` shifts a bot).
  - `SessionGame`: `renameSeat(PlayerId, String)`, `setSeatOption(PlayerId, key, value)` (bot seat only, `NotABotSeatException` otherwise).
  - `SessionService`: `renameSeat(GameId, token, name)` (occupant, lobby only), `setSeatOption(GameId, hostToken, seat, key, value)` (host, lobby, validated by the game's factory); `seatOptions(GameId, PlayerId)` answering the `BotSeatOptions` port of PR0.
  - `LobbyView`: `seatOptionDescriptors`, per-player `seatOptions`.
  - `LobbyController`: `/lobby/rename`, `/lobby/setSeatOption`, `/lobby/addBot`, `/lobby/removeBot`.
  - `BullshitWebSocketController`: **delete** `/bullshit/addBot` and `/bullshit/removeBot` (no alias: the SPA ships with the backend, spec 6.2) and switch `application/BullshitSession.ts` to `/app/lobby/addBot` and `/app/lobby/removeBot` in the same PR; `bullshit-lobby-bots.cy.ts` stays green.
  - `AppConfig`: `BotSeatOptions` bean from `sessionService()::seatOptions`.
- No Bullshit seat descriptor (empty), no Bataille Corse descriptor yet (declared in PR7).
- Tests first: `SessionGameBotsTest` (options carried through shifting, human seat refused), `SessionServiceBotsTest` (host only, lobby only, unknown key or value refused by the game), `SessionServiceRenameTest` (occupant only, blank restores default, trimmed, 20 characters, refused after start), `LobbyViewTest`, `LobbyControllerIT` (rename reaches all seats; `/lobby/addBot` and `/lobby/removeBot` on a Bullshit and on a Bataille Corse lobby), Vitest for the new destinations in `BullshitSession`.
- Conflict: after PR2 (same classes). Parallel: Wave B.

## PR 4: `feat: leave the lobby, the host passes to the next human` (backend, issue #128)

Rule (spec 5.1): the lobby is removed only when no human remains; if the host leaves with a human
left, the next human becomes seat 0 and host. The host stays the constant seat 0 (no variable host;
option B rejected in spec 5.1). This PR first hardens the session core (spec 5.2), then adds the leave.

- Hardening (commit 1, tests first):
  - **Single lobby lock**: `joinRoom`, `addBot`, `removeBot`, `startGame`, `playAgain`, `updateOptions`, `renameSeat`, `setSeatOption` and the new `leaveLobby` run under the service monitor (`synchronized`), as do the view computations.
  - **Token hygiene**: `findPlayerByToken` and `findTokenByPlayer` resolve claimed seats only; every seat freed in a lobby gets a fresh `SessionToken` (new `SessionPlayer` record).
  - **Addressed, versioned broadcasts**: `SessionGame` (or the lobby record) carries a `lobbyVersion` incremented by every mutation; the use cases return `List<AddressedLobbyView>` (view + token) computed under the lock; `LobbyBroadcaster` sends by token (no `sendToSeat` lookup by seat number); `LobbyView.lobbyVersion`.
- Leave (commit 2, tests first):
  - `SessionGame.vacate(PlayerId)`: rebuilds the seat map; each occupant keeps its token, name, bot flag, seat options and rematch flags on its new seat; freed seats get fresh tokens. Vacating seat 0 first moves the first *human* by seat order to seat 0 (a bot is never host), then the others follow in order; any other seat shifts the later occupants down by one. Default human names (`Player \d+`) are renumbered like `renumberBots` (spec M1).
  - `SessionService.leaveLobby(GameId, token)`: under the lock, lobby phase only (`GameAlreadyStartedException` otherwise); token resolved inside the lock; vacate; if no human is left, remove the lobby **through the eviction path** (every `GameEvictionListener` notified: presence registry, forfeit log, bot coordinators), else return the addressed views. `startGame` re-checks `claimedCount >= minPlayers` under the same lock.
  - No presence rebind: PR-P already binds tokens (the core must not depend on `presence`).
  - `LobbyController`: `/lobby/leave`. No `LOBBY_CLOSED` event: nobody is left to notify.
- Tests first:
  - `SessionGameTokenHygieneTest`: a released seat's token resolves to nothing; a newcomer's token differs from the previous occupant's.
  - `SessionGameVacateTest`: every position of the leaver (host, middle, last), with humans only, bots between humans, bots after, a bot at seat 1 when the host leaves (the human at seat 2 becomes seat 0, never the bot); tokens, names, bot flags and seat options follow their occupant; the leaver's token resolves to nothing; default bot and human names renumbered; contiguous seats afterwards.
  - `SessionServiceConcurrencyTest` (threads with a latch): two joins on the last free seat (one wins, distinct tokens); join versus leave; join versus start; leave versus start in both orders; a burst of mutations ends with strictly increasing `lobbyVersion`.
  - `SessionServiceLeaveLobbyTest`: guest frees the seat and a newcomer can take it; host leaves with a human left (new host, `canStart` and `removableBotSeats` for the new host), host leaves with bots only (lobby removed, eviction listeners called, later join returns not found); refused after the start; unknown or already used token refused.
  - `PresenceAfterShiftIT` (or a `PresenceService` test with the real `SessionService`): after a shift and a start, a disconnect of the shifted player forfeits their new seat; the leaver disconnecting after the start forfeits nobody.
  - `LobbyLeaveIT` (STOMP, two or three clients): the guest leaves and the host sees the free seat; the host leaves and the next client receives a lobby view with `mySeat = 0`, host badge and Start available, over the same subscription (token channel, no re-subscribe); the leaver's old subscription receives nothing after a newcomer joins.
  - `KoboWebSocketControllerIT` green.
- Conflict: after PR3 and PR-P. Parallel: no (Wave C).

## PR 4b: `feat: play again keeps the bots and their seat options` (backend)

Spec 9.1. Small, after PR4 (same lock and seat model).

- `SessionService.playAgain`: when it reopens the lobby, the fresh `SessionGame` re-claims the former
  bots in order, with their seat options, right after the first caller's seat 0; default names
  "Bot N" kept; options kept as today. Humans come back one by one as today.
- Tests first: `SessionServicePlayAgainTest` (bots and tiers kept, order kept, first caller is host,
  a second human joins after the bots, options kept, fresh tokens for everyone); Bullshit and Kobo
  play-again ITs green.
- Depends on PR4. Parallel: Wave C, after PR4. Subject to question 1 of the spec (if the owner
  prefers empty lobbies, this PR is dropped).

## PR 5: `feat: shared lobby component, adopted by Bullshit` (frontend)

- Create:
  - `components/lobby/GameLobby.vue` (extracted from `BullshitLobby.vue`), `LobbySeats.vue`, `LobbyGameOptions.vue` (renders descriptors: radio groups for the host, text rows otherwise), `LobbySeatOptions.vue` (renders seat descriptors; nothing for Bullshit), `LobbyInvite.vue` (link, Copy, `aria-live`, select-all fallback), a Leave button.
  - `model/lobby/LobbyView.ts` (generalises `model/bullshit/LobbyView.ts`, with `lobbyVersion`), `model/lobby/optionLabel.ts` (resolves `options.<game>.<key>.<value>`, falls back to the raw value, receives the whole option map for labels that quote another option; replaces `claimModeLabel`).
  - `composables/useLobbyActions.ts`: sends `/lobby/*` for every game store; applies a lobby view only if its `lobbyVersion` is newer than the last applied one.
  - Locale keys `lobby.*`, `options.bullshit.*` (moved from `bullshitUi.start`).
- Modify:
  - `state/Bullshit.store.ts` and `BullshitSession.ts`: create through `POST /api/lobby` (PR6) instead of `/app/bullshit/create` and the `/topic/game` listener; `setOption`, `rename`, `leave` via `useLobbyActions`; **`mySeat` comes from the server** (every lobby view and every game state; the stored `bullshit:tokens:<id>` seat key is only a hint at restore and is rewritten when the server reports another seat); announce "You are now the host" in the live region; **on Leave**: `unsubscribeFromSeat()`, `unsubscribeFromGame()`, `clearPresence()`, remove the stored token entry, navigate to `/games`.
  - `view/bullshit/BullshitGameScreen.vue`: render `GameLobby` (lobby block and imports only); remove the now useless `:home-route` prop (exits already go to `/games` since #129).
  - Backend: delete `/bullshit/create` (its `@SendTo("/topic/game")` leaked host tokens to every client) and `BullshitCreatePayload` once the client is switched; adjust its tests.
  - Delete `view/bullshit/BullshitStartGame.vue` and `components/bullshit/BullshitLobby.vue` (+ tests, moved). The Game home's New game creates the lobby straight away (`createLobby`, name only, server defaults).
- Tests first: `GameLobby.test.ts` (port the whole `BullshitLobby.test.ts` first, then: editable options from descriptors for the host, read-only for guests, a change emits `set-option`, no section when descriptors are empty, own name editable and others not, free seat shows Add bot and invite for the host, Leave emits `leave`), `optionLabel.test.ts`, `Bullshit.store.test.ts`, `useLobbyActions.test.ts` (a lobby update changing `mySeat` makes the store host or not; an older `lobbyVersion` is ignored; Leave unsubscribes, clears presence and the stored token), `BullshitSession.test.ts` (create through REST, no `/topic/game` listener).
- Exits (spec 4.1): nothing changes for Bullshit (`/games` since #129); the lobby Leave also goes to `/games`; Play again leads to the lobby. Tests: one per exit path (Back during the game, end screen home button, forfeit, lobby Leave) asserting `/games`, and one asserting that Play again leads to the lobby.
- Cypress: update `bullshit-table-and-lobby.cy.ts`, `bullshit-lobby-bots.cy.ts`; `bullshit-exit-to-games.cy.ts` unchanged and green; new `shared-lobby.cy.ts` (two browsers: host picks 52 cards and the guest sees it live; the guest cannot edit; the guest leaves and the seat reopens; the host leaves and the guest becomes host, sees Start and the editable options without reloading; a guest leaving returns to `/games`); 375 x 667 with 6 seats: no page scroll, no horizontal scroll, Start reachable by scrolling the panel (spec 10).
- UI inventory: every item the Bullshit lobby shows today (title, player count, rows with You / Bot / Host, remove button, claim mode, deck size, invite and Copy, Start and hint, waiting for host, bot error) stays; added: editable options, editable name, Leave.
- Depends on PR1, 2, 3, 4, 6. Conflict: see table. Parallel: no (Wave D).

## PR 6: `feat: Bataille Corse uses rooms, lobbies are created over REST` (backend, additive)

- Create: shared `presentation/LobbyRestController` with `POST /api/lobby` (`{gameType, name}`, type checked against `GameFactories`, unknown type 404) returning `{gameId, token}` to the caller only (spec 4 and 6.2).
- Modify:
  - `BatailleCorseWebSocketController`: new `/bataillecorse/start` (`startGame` then the state broadcast, like Bullshit); the old `/create` stays until PR9.
  - `BatailleCorseRestController`: `GET /api/game/{id}?token=` returns the lobby view while not started (403 without a valid token), the game state otherwise; `POST /api/game/{id}/join` uses `joinRoom` when the room is not started; both **check the game type** (404 for a Bullshit or Kobo id, as the Bullshit endpoints do); the tokenless GET for the old client stays until PR9.
  - Lobby broadcasts after join (shared `LobbyBroadcaster`).
  - `KoboWebSocketController`: delete `/kobo/create` (no client yet); `KoboWebSocketControllerIT` creates through `POST /api/lobby`.
- Tests first (new `BatailleCorseRoomIT`; the old controller IT is `@Disabled` and stale): create through `POST /api/lobby` (the token never appears on `/topic/game`), join, `canStart` false until two seats are taken, start deals two players, a third join refused, join after start refused, a Bullshit id refused on the BC endpoints. The bot seat is tested at the **service level** (`SessionService.addBot` on a BC lobby, then start deals two players); the transport test for adding a bot comes with `/lobby/addBot` in PR3. `LobbyRestControllerIT` (unknown type, name, token returned only to the caller).
- Old solo and seat-1 paths stay until PR9, so the current frontend keeps working.
- Parallel: yes (Wave A).

## PR 7: `feat: Bataille Corse bot driven by the server, 9 tiers kept` (backend)

Estimate and risks: spec section 8 (about 600 lines of production code and as many of tests,
**three to four days**, plus the frontend removal in PR9).

- Create (`bataillecorse/domain/`):
  - `BatailleCorse.version()`: counter incremented by `send`, `slap`, `grab`, `forfeit`, read under the monitor (absent today).
  - `BatailleCorse.autoGrabPlayer()`: the player entitled to grab, or empty (backend twin of the frontend `Pile.getAutoGrabPlayer()`; today the backend has only `isGrabbableByPlayer`).
  - `bot/BatailleCorseBotTier` (the 9 tiers and reaction times of `Difficulty.ts`, unchanged, plus the +/-100 ms jitter), `bot/BatailleCorseBotStrategy` (pure: decides send, slap or nothing from the visible pile and the game's `SlapRules`).
- Create (`bataillecorse/presentation/`):
  - `BatailleCorseStateBroadcaster` + `BatailleCorseStateListener` (today the controller builds the DTO inline and calls `sendToGame`; nothing to listen to).
  - `BatailleCorseGameActions` (send, slap and grab logic extracted from the WebSocket controller so humans and bots share it, broadcasting through the new broadcaster).
  - `bot/BatailleCorseBotCoordinator`: after each state broadcast, drop stale tasks, schedule the action after the tier delay plus `extra-delay-ms`, re-check `version()` inside `synchronized (game)`; run the bot's own **auto-grab** after 1500 ms when `autoGrabPlayer()` is the bot; lifecycle: start on START, stop on forfeit and game end, drop the table on eviction (`GameEvictionListener`) and when the lobby reopens (Play again), restart on the next START; nothing scheduled when no human is left.
- Modify: `BatailleCorseFactory.seatOptionDescriptors()` declares `tier` (values `0`..`8`, default `1`) and validates it; `AppConfig` beans (coordinator, `BotSeatOptions`); properties `bataillecorse.bots.enabled` (`true`) and `bataillecorse.bots.extra-delay-ms` (`0`, also in the `test` profile; the tier delays are **not** zeroed in the `test` profile, unlike Bullshit's, so the e2e timing matches the old local AI).
- Tests first: `BatailleCorseVersionTest`, `BatailleCorseAutoGrabTest`, `BatailleCorseBotStrategyTest` (slaps ten, pair, sandwich, sum of ten; ignores an ordinary pile; uses the rules in force), `BatailleCorseBotTierTest` (the 9 values equal the frontend table), `BatailleCorseStateBroadcasterTest` (listeners called after each action), `BatailleCorseBotCoordinatorTest` (with the shared `ManualBotScheduler` and `FixedThinkingDelay`: acts on its turn only, stale task dropped after a human slap, auto-grab fires once, no action after the game ends, after a forfeit or when no human is left, restarts after Play again then START), `BatailleCorseFactoryTest` (descriptor, validation), `BatailleCorseBotGameIT` (host plus bot plays to the end with no client).
- Before merging, check by hand (needs the frontend on PR9 or a temporary client) risks 2 and 3 of spec section 8.3: reaction fidelity at fast tiers and moves arriving during card animations.
- Depends on PR0, PR3, PR6. Parallel: Wave B (domain parts can start in Wave A).

## PR 8: `feat: keyboard shortcuts gear for both games` (frontend)

- Generalise `components/bullshit/HotkeySettings.vue` and `composables/useHotkeySettings.ts` to a list of actions provided by the game (move to `components/HotkeySettings.vue`); Bullshit keeps its actions and storage keys; BC provides Send and Slap on `settingsStore.sendKey/slapKey` (`bc_sendKey`, `bc_slapKey` kept).
- Add the gear to `view/alpha/GameScreen.vue` (answer to question 5 needed before merging). The create page keeps its key block until PR9.
- Tests first: moved and extended `HotkeySettings.test.ts` and `useHotkeySettings.test.ts` (BC action set, conflicting keys refused, persistence in existing keys), `GameScreen.test.ts` (gear present), Cypress: `bullshit-hotkey-settings.cy.ts` unchanged plus a BC case.
- Conflict: one import line in `BullshitGameScreen.vue`; `view/alpha/GameScreen.vue` with PR1 (merge after PR1). Parallel: Wave A, merged after PR1.

## PR 9: `feat: Bataille Corse adopts the shared lobby; solo is a bot` (frontend + backend cleanup)

- Frontend:
  - BC store and `GameSession`: create a lobby through `POST /api/lobby`, one seat token, lobby phase like Bullshit, Start action, **Play again through `/api/game/{id}/play-again` leading to the lobby** (the bot and its tier come back, PR4b); `mySeat` from the server (lobby views and game state), stored `tokens:<gameId>` rewritten to the single seat reported by the server; ignore (do not crash on) an old two-token entry; on Leave the same cleanup as Bullshit (unsubscribe, `clearPresence`, stored token removed); remove the solo branches (`mode`, `waiting`, two-token rehydration, local `AI`, the client-side auto-grab for the bot).
  - Delete `view/alpha/StartGame.vue`, `components/WaitingOverlay.vue`, `model/ai/AI.ts` (+ test), the `difficulty` field of `Settings.store.ts`, the key-capture block and its CSS. `model/Difficulty.ts` stays only as the source of tier names for the locale/labels (the 9 tiers are kept), or moves to locales.
  - `view/alpha/GameScreen.vue`: render `GameLobby` in the lobby phase; the opponent label comes from the seat list ("Bot 1 (Gold)" or the guest's name) instead of `Computer (<tier>)`; `model/RematchButton.ts` updated.
  - BC entry in the `GameDefinition` registry (create and start through the BC destinations).
  - Exits: Back during a BC game, the end screen's home button and forfeit lead to `/games` (already done in PR1 for Back and the end screen); the lobby Leave too; Play again leads to the lobby. One test per path.
- Backend:
  - BC `POST /api/game/{id}/play-again` (as Bullshit's, game type checked).
  - Delete `SessionService.createGame` overloads, `GameMode`, `joinGame` (fixed seat 1), `claimAllSeats`, `/create` (and its `@SendTo("/topic/game")`), the BC fixed-seat `join` path and tokenless GET, `CreateGamePayload.mode`; delete or rewrite the stale disabled controller IT.
  - In-place rematch: delete BC `/rematch`. Then **check before deleting** `SessionService.requestRematch`/`rematch`/`isRematchReady` and `SessionGame.requestRematch`/`isRematchUnanimous`: `PresenceService.forfeit` on a finished game calls `GameDirectory.leaveRematch`, which calls `isRematchReady`/`rematch`. Either reduce `leaveRematch` to a no-op contract (no in-place rematch exists any more) and delete the rest, or keep it if another caller remains; adjust `PresenceRematchTest`. `grep` for dead code first and list the result in the PR.
- Tests first: BC store/session tests rewritten around the lobby flow; `GameScreen.test.ts`; backend play-again IT with a bot (bot and tier kept); Cypress, in this order: the shared command `support/commands.ts` `createGame` (New game, Add bot, tier, Start), then `create-game`, `send-card`, `rehydrate-game`, `waiting-and-names`, `turn-indicator`, `responsive-game-screen`, `rules-panel`, `visual-direction`, `bataillecorse-menu-fit`, `font-fit`, `game-picker`; plus `bataillecorse-lobby.cy.ts` (solo with a bot at a chosen tier reaches the Table; invite flow in two browsers; Leave; Play again keeps the bot; 375 px fit). The e2e stack keeps the real tier delays (spec 8.2); if a spec proves flaky, raise `bataillecorse.bots.extra-delay-ms` for the e2e stack.
- UI inventory: everything the BC create page, waiting overlay and Table show today (opponent toggle, tier, name, keys, invite link, opponent label, rematch) is found elsewhere or consciously removed, listed in the PR.
- Depends on PR4b, PR5, PR7, PR8 (and transitively PR1, PR4, PR6). Parallel: no (Wave E).

## Definition of done

- One Game home and one lobby component serve Bataille Corse and Bullshit (Kobo needs a `GameDefinition` entry).
- The host edits game options in the lobby, everyone sees them live, they freeze at start and survive a rematch; unknown keys and values are refused by the game's domain.
- A guest can leave the lobby; when the host leaves, the next human becomes host, and the lobby disappears only when no human is left.
- A player who left has no valid token any more; nobody else ever receives their token; a later disconnect of the leaver forfeits nobody.
- Every lobby mutation runs under one lock; every client ends on the latest `lobbyVersion`.
- No token is sent on a shared topic: lobbies are created through `POST /api/lobby`.
- Leaving any game (Back, end screen, forfeit, lobby Leave) returns to `/games`; Play again returns to the lobby, with the bots and their tiers.
- A room link that cannot be restored leads to the join form (no token) or to the game's Game home with a notice (game gone).
- A Bataille Corse solo game is a lobby with a server bot at one of the 9 tiers; no local AI remains.
- Old invite links still land on the right screen.
- Dead code removed (`WaitingOverlay`, `StartGame`, `AI`, `GameMode`, create pages, WebSocket create endpoints, BC in-place rematch if unused).
- Issue #122 and #128 commented after each merge.
