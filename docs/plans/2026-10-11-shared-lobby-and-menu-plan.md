# Shared Game home and lobby: implementation plan

Spec: `docs/specs/2026-10-11-shared-lobby-and-menu-design.md` (issue #122, and #128 for the Leave
button). Vocabulary follows the glossary of the spec (Game selection, Game home, Lobby, Table, Game
options, Seat options, Local preferences); the code keeps `room`, `SessionService`, etc.

Changes from the first draft of this plan: Bullshit bot levels (old PR5) and Bataille Corse game
options (old PR10) are dropped; the Bataille Corse bot keeps its 9 tiers; a Leave-lobby PR is added;
`GameEntryMenu` is renamed `GameHome`; #125, #126 and #127 are merged into `main`.

Rules for every PR: own worktree and branch `<type>/<subject>`, target `main`, TDD (failing test
first), all suites green before the PR (`cd backend && mvn -s ../deploy/docker/settings.xml test`,
`cd frontend && npm test && npx vite build`), texts through `frontend/src/locales/`, game rules in the
game's `domain/`. UI PRs follow the `ui-change` skill (before/after inventory of displayed information,
light and dark, 375 and 1280 px) and add or update a Cypress spec. A manual test environment is only
needed for PR 1, 5, 9; never for backend-only PRs.

Single-test commands:

- Backend: `cd backend && mvn -s ../deploy/docker/settings.xml test -Dtest=<ClassName>`
- Frontend: `cd frontend && npx vitest run <path>`
- E2E (one stack at a time, see `CLAUDE.md`): `docker compose -f docker-compose.e2e.yml up --build -d --wait`,
  then `cd frontend && npx cypress run --spec cypress/specs/<file>`

## Overview

```
PR0 shared bot ports (BE refactor) ───────────────────────────────┐
PR1 Game home (FE) ───────────────────────────────────────────────┤
PR2 Game option descriptors + updateOptions (BE) ──┐              │
PR3 Seat options + rename (BE) ───── needs PR2 ────┤              │
PR4 Leave lobby (BE) ─────────────── needs PR3 ────┤              │
PR5 GameLobby, adopted by Bullshit (FE) needs 1,2,3,4             │
PR6 BC uses rooms (BE, additive) ── independent ──────────────────┤
PR7 BC server bot, 9 tiers (BE) ──── needs 0,3,6 ─────────────────┤
PR8 Shortcuts gear for both games (FE) ── independent ────────────┤
PR9 BC adopts the lobby; local AI and solo deleted ── needs 1,5,6,7,8
```

Waves (one session each; PR0 can start now because #127 is merged):

- **Wave A (parallel)**: PR0, PR1, PR2, PR6, PR8.
- **Wave B (parallel, after their dependencies merge)**: PR3 (after PR2); PR7 (after PR0, PR6, and PR3 for the seat option descriptor).
  PR7's strategy and coordinator can be written in Wave A against a stub tier source and wired at the end; only the final wiring waits for PR3.
- **Wave C**: PR4 (after PR3), then PR5 (after PR1, PR2, PR3, PR4; its frontend can be developed against PR4's contract while PR4 is in review).
- **Wave D**: PR9 (after everything), alone on the Bataille Corse files.

Critical path: PR2, PR3, PR4, PR5, PR9. The Bataille Corse bot (PR6, PR7) runs beside it.

## Conflicts

#125, #126, #127 are merged, so the Bullshit seat-order, rematch-names and bot-delay changes are in
`main`; rebase on `main` and re-check `gh pr list` before each task.

| File | Touched by | Risk |
|---|---|---|
| `view/bullshit/BullshitGameScreen.vue` | PR5 (lobby block and imports), PR8 (gear import line) | Medium: PR8 changes one import line, PR5 the lobby block; keep both diffs minimal |
| `components/bullshit/BullshitLobby.vue` (+ test) | PR5 only (deleted) | Low: sole editor |
| `view/bullshit/BullshitStartGame.vue` (+ test) | PR1 (join mode removed), PR5 (deleted) | Medium: sequential |
| `state/Bullshit.store.ts`, `application/BullshitSession.ts` | PR5 | Low |
| `bullshit/presentation/bot/*` | PR0 (moves ports) | Medium: pure move; do not combine with behaviour changes |
| `SessionService`, `SessionGame`, `SessionPlayer`, `LobbyView`, `LobbyController` | PR2, PR3, PR4, PR6 (additive) | Medium: PR2, PR3, PR4 strictly sequential |
| `BatailleCorseWebSocketController`, `BatailleCorseRestController` | PR6 (adds), PR7 (extracts `BatailleCorseGameActions`), PR9 (removes) | Medium: PR7 touches the controller to extract the actions; do it after PR6 merges |
| `view/alpha/GameScreen.vue`, `state/BatailleCorse.store.ts`, `application/GameSession.ts` | PR8 (gear), PR9 | Medium: sequential |
| `locales/Messages.ts`, `locales/en.ts` | every frontend PR | Low but constant: add a section per PR, rebase often |
| `router.ts` | PR1, PR5, PR9 | Low |

## PR 0: `refactor: share the bot scheduling ports` (backend, no behaviour change)

Still useful: the Bataille Corse bot (PR7) needs `BotScheduler`, `ThinkingDelay`, `BotSeats` and the
test doubles, which live in `bullshit.presentation.bot`; a game may not depend on another game.

- Move to a shared package (for example `org.kevinkib.cardgames.presentation.bot`): `BotScheduler`,
  `TaskSchedulerBotScheduler`, `ThinkingDelay`, `UniformThinkingDelay`, `ThreadLocalRandomGenerator`,
  `BotSeats`; move the test doubles `ManualBotScheduler` and `FixedThinkingDelay` to a shared test package.
- Update imports in `config/AppConfig.java`, `BullshitBotCoordinator` and tests.
- Tests first: none new; `BullshitBotCoordinatorTest`, `TaskSchedulerBotSchedulerTest`, `UniformThinkingDelayTest`
  and the application context test stay green. If an architecture test exists, extend it so `bullshit` and
  `bataillecorse` never import each other.
- Command: full backend suite.
- Conflict: none open. Parallel: yes.

## PR 1: `feat: shared Game home for every game` (frontend)

- Create:
  - `frontend/src/model/games.ts`: `GameDefinition { key, titleKey, homeRoute, joinRoute, roomRoute, createLobby }` and the registry.
  - `frontend/src/components/GameHome.vue`: title block, name field (persisted in `settingsStore.playerName`), New game, Join game, Back to the Game selection.
  - `frontend/src/components/JoinGameForm.vue`: game id or link (`extractGameId`, moved to `model/gameId.ts`), name, error line, Back.
  - Locale keys `home.*`, `join.*`.
- Modify:
  - `router.ts`: add `/games/:game` and `/games/:game/join/:id?`; legacy `bataillecorse/*` and `bullshit/*` paths keep resolving.
  - `GamePickerView.vue` (+ test): both cards open the Game home.
  - `view/alpha/StartGame.vue`: remove join mode and the opponent toggle's dependency on the home; `view/alpha/LobbyView.vue`: deleted.
  - `view/bullshit/BullshitStartGame.vue`: remove join mode.
  - Until PR5 and PR9, "New game" navigates to the game's existing create page (behaviour unchanged).
- Tests first: `GameHome.test.ts` (New and Join for both definitions, name persisted, targets), `JoinGameForm.test.ts` (bare id or full link, prefilled from route, error, button disabled when empty), `router.test.ts` (legacy links, unknown game goes to `/games`, every Back reaches `/games`), updated `GamePickerView.test.ts`.
- Cypress: update `game-picker.cy.ts`, `bataillecorse-menu-fit.cy.ts`, the entry steps of `bullshit-table-and-lobby.cy.ts`; 375 px fit of the Game home.
- UI inventory: BC home and Bullshit create page before/after; nothing disappears, Bullshit gains a Join game button.
- Parallel: yes. Merge value: both games share one home; no backend change.

## PR 2: `feat: games declare their game options and the host edits them` (backend)

- Create (kernel `game`): `OptionDescriptor`, `OptionType`, `InvalidOptionException`.
- Modify:
  - `GameFactory`: `optionDescriptors()` and `validate(GameOptions)` defaults (empty / identity).
  - `BullshitFactory`: descriptors for `claimMode` and `deckSize` from `ClaimModeOption` and `DeckSize`; `validate` through `BullshitOptions.from`.
  - `GameFactories`: `optionDescriptors(type)`, `validate(type, options)`.
  - `SessionService.updateOptions(GameId, hostToken, Map)` (reuse `openLobbyOwnedBy`); store by saving the lobby with the new options.
  - `LobbyView`: `optionDescriptors`, `canEditOptions`, defaults filled for missing keys.
  - New `presentation/LobbyController` with `/lobby/setOptions` (`LobbyOptionsPayload(gameId, token, values)`), shared broadcast, errors to the actor seat only.
- Tests first: `BullshitFactoryTest` (descriptors, defaults valid, unknown value refused by the domain), `GameFactoriesTest`, `SessionServiceOptionsTest` (host only, refused after start, invalid unchanged, merge, kept by `playAgain`), `LobbyViewTest`, `LobbyOptionsIT` (two STOMP clients: the guest sees the change live), backward compatibility test (`createRoom` with options and `BullshitCreatePayload` still work).
- Commands: `mvn ... test -Dtest='SessionServiceOptionsTest,BullshitFactoryTest,LobbyViewTest,LobbyOptionsIT'`, then full suite.
- Parallel: yes (Wave A). Blocks PR3.

## PR 3: `feat: seat options and player rename in the lobby` (backend)

- Modify:
  - `GameFactory`: `seatOptionDescriptors()` (default empty) and `validateSeatOption(key, value)`.
  - `SessionPlayer`: opaque `Map<String,String> options` (cleared by `release()`, carried when `removeBot` shifts a bot).
  - `SessionGame`: `renameSeat(PlayerId, String)`, `setSeatOption(PlayerId, key, value)` (bot seat only, `NotABotSeatException` otherwise).
  - `SessionService`: `renameSeat(GameId, token, name)` (occupant, lobby only), `setSeatOption(GameId, hostToken, seat, key, value)` (host, lobby, validated by the game's factory).
  - `LobbyView`: `seatOptionDescriptors`, per-player `seatOptions`.
  - `LobbyController`: `/lobby/rename`, `/lobby/setSeatOption`, `/lobby/addBot`, `/lobby/removeBot`; `BullshitWebSocketController` `/bullshit/addBot` and `/bullshit/removeBot` delegate to the shared methods and are marked deprecated.
- No Bullshit seat descriptor (empty), no Bataille Corse descriptor yet (declared in PR7).
- Tests first: `SessionGameBotsTest` (options carried through shifting, human seat refused), `SessionServiceBotsTest` (host only, lobby only, unknown key refused by the game), `SessionServiceRenameTest` (occupant only, blank restores default, trimmed, 20 characters, refused after start), `LobbyViewTest`, `LobbyControllerIT` (rename reaches all seats; deprecated Bullshit aliases still work).
- Conflict: after PR2 (same classes). Parallel: Wave B.

## PR 4: `feat: leave the lobby` (backend, issue #128)

- Modify:
  - `SessionGame.vacate(PlayerId)` (frees a human seat, shifts later humans and bots down so seats stay contiguous; tokens move with the person) and tests for every gap case.
  - `SessionService.leaveLobby(GameId, token)`: lobby phase only (`GameAlreadyStartedException` otherwise); guest: vacate and broadcast; host: per the owner's answer to question 7. Recommended first step: close the lobby (remove it from the repository, broadcast a `LOBBY_CLOSED` event to every other human seat, no transfer).
  - `LobbyController`: `/lobby/leave`; a new lifecycle event type `LOBBY_CLOSED` in `presentation/dto/event`.
- Tests first: `SessionGameVacateTest` (guest in the middle, last seat, bots after, humans after), `SessionServiceLeaveLobbyTest` (guest frees the seat and can be replaced, host leave closes, bots-only remainder cleaned, refused during a game, unknown token refused), `LobbyLeaveIT` (two clients: the guest leaves, the host sees the seat free; the host leaves, the guest receives the closing event).
- Presence interaction (spec 5.1): none in a lobby; no change to `PresenceService`.
- If the owner picks host transfer (question 7), add a follow-up PR 4b: movable host in `SessionGame`, `isHost` and `LobbyView.hostSeat` driven by it, `playAgain` rule.
- Conflict: after PR3. Parallel: no (Wave C).

## PR 5: `feat: shared lobby component, adopted by Bullshit` (frontend)

- Create:
  - `components/lobby/GameLobby.vue` (extracted from `BullshitLobby.vue`), `LobbySeats.vue`, `LobbyGameOptions.vue` (renders descriptors: radio groups for the host, text rows otherwise), `LobbySeatOptions.vue` (renders seat descriptors; nothing for Bullshit), `LobbyInvite.vue` (link, Copy, `aria-live`, select-all fallback), a Leave button.
  - `model/lobby/LobbyView.ts` (generalises `model/bullshit/LobbyView.ts`), `model/lobby/optionLabel.ts` (resolves `options.<game>.<key>.<value>`, falls back to the raw value, handles `dependsOnKey`; replaces `claimModeLabel`).
  - `composables/useLobbyActions.ts`: sends `/lobby/*` for every game store.
  - Locale keys `lobby.*`, `options.bullshit.*` (moved from `bullshitUi.start`).
- Modify:
  - `state/Bullshit.store.ts` and `BullshitSession.ts`: `setOption`, `rename`, `leave` via `useLobbyActions`; handle `LOBBY_CLOSED` (navigate to `/games` with a notice).
  - `view/bullshit/BullshitGameScreen.vue`: render `GameLobby` (lobby block and imports only).
  - Delete `view/bullshit/BullshitStartGame.vue` and `components/bullshit/BullshitLobby.vue` (+ tests, moved). The Game home's New game creates the lobby straight away (`createLobby`, name only, server defaults).
- Tests first: `GameLobby.test.ts` (port the whole `BullshitLobby.test.ts` first, then: editable options from descriptors for the host, read-only for guests, a change emits `set-option`, no section when descriptors are empty, own name editable and others not, free seat shows Add bot and invite for the host, Leave emits `leave`), `optionLabel.test.ts`, `Bullshit.store.test.ts`, `useLobbyActions.test.ts`.
- Cypress: update `bullshit-table-and-lobby.cy.ts`, `bullshit-lobby-bots.cy.ts`; new `shared-lobby.cy.ts` (two browsers: host picks 52 cards and the guest sees it live; the guest cannot edit; the guest leaves and the seat reopens; the host leaves and the guest returns to `/games`); 375 x 667 fit with 6 seats.
- UI inventory: every item the Bullshit lobby shows today (title, player count, rows with You / Bot / Host, remove button, claim mode, deck size, invite and Copy, Start and hint, waiting for host, bot error) stays; added: editable options, editable name, Leave.
- Depends on PR1, 2, 3, 4. Conflict: see table. Parallel: no.

## PR 6: `feat: Bataille Corse uses rooms` (backend, additive)

- Modify:
  - `BatailleCorseWebSocketController`: new `/bataillecorse/create` (`createRoom`) and `/bataillecorse/start` (`startGame` then the state broadcast, like Bullshit); the old `/create` stays until PR9.
  - `BatailleCorseRestController`: `GET /api/game/{id}` returns the lobby view (with token) while not started; `POST /api/game/{id}/join` uses `joinRoom` when the room is not started; the tokenless access for the old client stays.
  - Lobby broadcasts after join, add bot, remove bot (shared `LobbyBroadcaster`).
- Tests first (new `BatailleCorseRoomIT`; the old controller IT is `@Disabled` and stale): create, join, `canStart` false until two seats are taken, start deals two players, a third join refused, join after start refused, add bot fills seat 1.
- Old solo and seat-1 paths stay until PR9, so the current frontend keeps working.
- Parallel: yes (Wave A).

## PR 7: `feat: Bataille Corse bot driven by the server, 9 tiers kept` (backend)

Estimate and risks: spec section 8 (about 500 lines of production code and as many of tests, two to
three days, plus the frontend removal in PR9).

- Create (`bataillecorse/domain/bot/`): `BatailleCorseBotTier` (the 9 tiers and reaction times of `Difficulty.ts`, unchanged, plus the +/-100 ms jitter), `BatailleCorseBotStrategy` (pure: decides send, slap or nothing from the visible pile and the game's `SlapRules`).
- Create (`bataillecorse/presentation/`): `BatailleCorseGameActions` (send, slap and grab logic extracted from the WebSocket controller so humans and bots share it), `bot/BatailleCorseBotCoordinator` (after each state broadcast: drop stale tasks, schedule the action after the tier delay, check game version under the monitor, stop when no human is left or the game is over, run the bot's own **auto-grab** after 1500 ms when the pile is the bot's).
- Modify: `BatailleCorseFactory.seatOptionDescriptors()` declares `tier` (values `0`..`8`, default `1`) and validates it; `BotSeats` extended to expose seat options; `AppConfig` beans; lifecycle (a human forfeit stops the bot; a rematch drops the table).
- Tests first: `BatailleCorseBotStrategyTest` (slaps ten, pair, sandwich, sum of ten; ignores an ordinary pile; uses the rules in force), `BatailleCorseBotTierTest` (the 9 values equal the frontend table), `BatailleCorseBotCoordinatorTest` (with the shared `ManualBotScheduler` and `FixedThinkingDelay`: acts on its turn only, stale task dropped, auto-grab fires once, no action after the game ends or when no human is left), `BatailleCorseFactoryTest` (descriptor, validation), `BatailleCorseBotGameIT` (host plus bot plays to the end with no client).
- Before merging, check by hand (needs the frontend on PR9 or a temporary client) risks 2 and 3 of spec section 8.3: reaction fidelity at fast tiers and moves arriving during card animations.
- Depends on PR0, PR3, PR6. Parallel: Wave B.

## PR 8: `feat: keyboard shortcuts gear for both games` (frontend)

- Generalise `components/bullshit/HotkeySettings.vue` and `composables/useHotkeySettings.ts` to a list of actions provided by the game (move to `components/HotkeySettings.vue`); Bullshit keeps its actions and storage keys; BC provides Send and Slap on `settingsStore.sendKey/slapKey` (`bc_sendKey`, `bc_slapKey` kept).
- Add the gear to `view/alpha/GameScreen.vue` (answer to question 5 needed before merging). The create page keeps its key block until PR9.
- Tests first: moved and extended `HotkeySettings.test.ts` and `useHotkeySettings.test.ts` (BC action set, conflicting keys refused, persistence in existing keys), `GameScreen.test.ts` (gear present), Cypress: `bullshit-hotkey-settings.cy.ts` unchanged plus a BC case.
- Conflict: one import line in `BullshitGameScreen.vue`. Parallel: Wave A.

## PR 9: `feat: Bataille Corse adopts the shared lobby; solo is a bot` (frontend + backend cleanup)

- Frontend:
  - BC store and `GameSession`: create a lobby, one seat token, lobby phase like Bullshit, Start action, rematch through `playAgain` (rule from question 1); remove the solo branches (`mode`, `waiting`, two-token rehydration, local `AI`, the client-side auto-grab for the bot).
  - Delete `view/alpha/StartGame.vue`, `components/WaitingOverlay.vue`, `model/ai/AI.ts` (+ test), the `difficulty` field of `Settings.store.ts`, the key-capture block and its CSS. `model/Difficulty.ts` stays only as the source of tier names and colours for the locale/labels (the 9 tiers are kept), or moves to locales.
  - `view/alpha/GameScreen.vue`: render `GameLobby` in the lobby phase; the opponent label comes from the seat list ("Bot 1 (Gold)" or the guest's name) instead of `Computer (<tier>)`; `model/RematchButton.ts` updated.
  - BC entry in the `GameDefinition` registry (create and start through the BC destinations).
- Backend cleanup: delete `SessionService.createGame` overloads, `GameMode`, `joinGame` (fixed seat 1), `claimAllSeats`, `/create`, the BC `join` path, `CreateGamePayload.mode`; delete or rewrite the stale disabled controller IT. `grep` for dead code first.
- Tests first: BC store/session tests rewritten around the lobby flow; `GameScreen.test.ts`; Cypress specs listed in spec 9.1 rewritten, plus `bataillecorse-lobby.cy.ts` (solo with a bot at a chosen tier reaches the Table; invite flow in two browsers; Leave; 375 px fit).
- UI inventory: everything the BC create page, waiting overlay and Table show today (opponent toggle, tier, name, keys, invite link, opponent label, rematch) is found elsewhere or consciously removed, listed in the PR.
- Depends on PR1, 5, 6, 7, 8. Parallel: no.

## Definition of done

- One Game home and one lobby component serve Bataille Corse and Bullshit (Kobo needs a `GameDefinition` entry).
- The host edits game options in the lobby, everyone sees them live, they freeze at start and survive a rematch.
- A guest can leave the lobby; the host-leaves behaviour follows the owner's answer.
- A Bataille Corse solo game is a lobby with a server bot at one of the 9 tiers; no local AI remains.
- Old invite links still land on the right screen.
- Dead code removed (`WaitingOverlay`, `StartGame`, `AI`, `GameMode`, create pages).
- Issue #122 and #128 commented after each merge.
