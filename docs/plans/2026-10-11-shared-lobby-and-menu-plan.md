# Shared entry menu and lobby: implementation plan

Spec: `docs/specs/2026-10-11-shared-lobby-and-menu-design.md` (issue #122). Read its section 13 first:
the open questions change PR 5, 7, 9 and 10. PR 0 to 4 and 6 are not affected by any open question
except question 2 (PR 4).

Rules for every PR: separate worktree and branch `<type>/<subject>`, target `main`, TDD (failing test
first, then code), all suites green before the PR (`cd backend && mvn -s ../deploy/docker/settings.xml test`,
`cd frontend && npm test && npx vite build`), texts through `frontend/src/locales/`, game rules in
the game's `domain/`. UI PRs follow the `ui-change` skill (before/after inventory of displayed
information, light and dark, 375 px and 1280 px) and add or update a Cypress spec. A manual test
environment is only needed for PR 1, 4, 5, 9 (user-visible), never for the backend-only PRs.

Single-test commands used below:

- Backend: `cd backend && mvn -s ../deploy/docker/settings.xml test -Dtest=<ClassName>`
- Frontend: `cd frontend && npx vitest run <path>`
- E2E (one stack at a time, see `CLAUDE.md`): `docker compose -f docker-compose.e2e.yml up --build -d --wait`, then
  `cd frontend && npx cypress run --spec cypress/specs/<file>`

## Overview and ordering

```
PR0 refactor: bot ports to shared package ──┐
PR1 entry menu (FE) ────────────────────────┼──────────────┐
PR2 option descriptors + updateOptions (BE) ┤              │
PR3 seat options: rename, BotLevel (BE) ────┤ (after PR2)  │
PR4 GameLobby adopted by Bullshit (FE) ─────┴ needs 1,2,3 ─┤
PR5 Bullshit bot levels (BE+FE) ───────────── needs 0,3,4  │
PR6 BC rooms backend (BE) ───── independent (needs nothing)│
PR7 BC server-side bot + levels (BE) ───────── needs 0,3,6 │
PR8 hotkey gear for both games (FE) ── independent         │
PR9 BC adopts the lobby, old solo deleted (FE+BE) ── needs 1,4,6,7,8
PR10 BC room options (BE+FE) ── optional, needs 2,9 and owner's answer to question 4
```

Parallel waves (one session each, see "Conflicts" for files that collide):

- Wave A, all at once: PR0, PR1, PR2, PR6, PR8.
- Wave B: PR3 (after PR2 merged), then PR4 (after PR1, PR2, PR3).
- Wave C: PR5 and PR7 (after PR0, PR3; PR7 also PR6; PR5 also PR4). PR5 and PR7 are
  independent of each other once PR0 is merged.
- Wave D: PR9, then PR10.

## Conflicts with work in flight

Open PRs at the time of writing (to re-check with `gh pr list` before starting each task):
#125 `fix/bullshit-rematch-names`, #126 `fix/bullshit-seat-order`, #127 `feat/bullshit-bot-delay`.

| File | Touched by | Risk |
|---|---|---|
| `view/bullshit/BullshitGameScreen.vue` | PR4 (lobby props and actions), PR8 (gear, if the component name changes), #125, #126 | High: merge #125 and #126 first; keep the diff to the lobby block and the gear import |
| `components/bullshit/BullshitLobby.vue` | PR4 (turns into a wrapper, then deleted in PR9 or a cleanup), anything touching bots in the lobby | High: PR4 is the only task that edits it |
| `view/bullshit/BullshitStartGame.vue` | PR1 (join part removed), PR4 (create part removed), deleted at the end of PR4 | Medium: PR1 and PR4 must be sequential; PR1 keeps the create form untouched |
| `state/Bullshit.store.ts` | PR4 (lobby actions), PR5 (bot level) | Medium: sequential |
| `bullshit/presentation/bot/*`, `BotTuning`, `BullshitBotCoordinator` | PR0 (moves ports), PR5, #127 (delay) | High: merge #127 before PR0; PR0 is a pure move |
| `SessionService`, `SessionGame`, `LobbyView`, `SessionPlayer` | PR2, PR3, PR6 (removal of `createGame`, last, in PR9) | Medium: PR2 then PR3; PR6 adds methods only |
| `BatailleCorseWebSocketController`, `BatailleCorseRestController` | PR6 (adds), PR9 (removes old paths) | Low |
| `view/alpha/GameScreen.vue`, `state/BatailleCorse.store.ts`, `application/GameSession.ts` | PR8 (gear), PR9 (solo removal) | Medium: sequential |
| `locales/Messages.ts`, `locales/en.ts` | every FE PR | Low but constant: add keys in a new section per PR, rebase often |
| `router.ts` | PR1, PR4, PR9 | Low |

## PR 0: `refactor: share the bot scheduling ports` (backend, no behaviour change)

Why: the Bataille Corse bot (PR7) reuses `BotScheduler`, `ThinkingDelay`, `BotSeats`, `RandomGenerator`
adapters, which live in `bullshit.presentation.bot`. A game may not depend on another game.

- Move to a shared package (for example `org.kevinkib.cardgames.presentation.bot`):
  `BotScheduler`, `TaskSchedulerBotScheduler`, `ThinkingDelay`, `UniformThinkingDelay`,
  `ThreadLocalRandomGenerator`, `BotSeats`.
- Update imports in `config/AppConfig.java`, `BullshitBotCoordinator`, and the existing tests.
- Tests first: none new; the point is that `BullshitBotCoordinatorTest`, `BotEndgameSimulationTest` and
  the application context test stay green. Add an ArchUnit-style or package test only if the repo
  already has the pattern (check `backend/src/test/.../ArchitectureTest*`; if it exists, extend it so
  `bullshit` and `bataillecorse` do not import each other).
- Command: `mvn -s ../deploy/docker/settings.xml test`.
- Conflict: merge after #127.
- Parallel: yes (Wave A), but PR5 and PR7 wait for it.

## PR 1: `feat: shared entry menu for every game` (frontend)

- Create:
  - `frontend/src/model/games.ts`: `GameDefinition { key, titleKey, createRoute, joinRoute, roomRoute }` and
    the registry (`bataillecorse`, `bullshit`).
  - `frontend/src/components/GameEntryMenu.vue`: title block, name field (persisted in
    `settingsStore.playerName`), New game, Join game, Back. Navigates to the game's create route
    (the existing create pages stay for now).
  - `frontend/src/components/JoinGameForm.vue`: game id or link (reuses `extractGameId` from
    `model/bullshit/gameId.ts`, moved to `model/gameId.ts`), name, error line, Back.
  - Locale keys `menu.*`, `join.*` in `Messages.ts` / `en.ts`.
- Modify:
  - `router.ts`: add `/games/:game` (menu) and `/games/:game/join/:id?` (join form); the existing
    `bataillecorse/*` and `bullshit/*` paths keep resolving (redirects for the join paths).
  - `view/GamePickerView.vue`: both cards open the menu; `GamePickerView.test.ts` updated.
  - `view/alpha/StartGame.vue`: remove the `join` mode (the form moved); `view/alpha/LobbyView.vue` deleted.
  - `view/bullshit/BullshitStartGame.vue`: remove the join mode.
- Tests first: `GameEntryMenu.test.ts` (shows New and Join for both definitions, name persisted,
  navigation targets), `JoinGameForm.test.ts` (link or bare id accepted, prefilled from route, error shown, button disabled
  on empty), `router.test.ts` (legacy join links still land on the right form, unknown game redirects to the picker),
  update `GamePickerView.test.ts`.
- Cypress: update `game-picker.cy.ts`, `bataillecorse-menu-fit.cy.ts`, `bullshit-table-and-lobby.cy.ts`
  (entry steps); add a 375 px fit check for the new menu.
- UI inventory: BC menu (title, New, Join, Back) and Bullshit create page (title, name, options,
  create, back) before/after: nothing may disappear, the Bullshit join entry is new.
- Conflict: `BullshitStartGame.vue` (see table). Parallel: yes.
- Merge value: both games share one menu; nothing in the backend changes.

## PR 2: `feat: games declare their room options and the host edits them` (backend)

- Create (kernel `game`): `OptionDescriptor`, `OptionType`, `InvalidOptionException`.
- Modify:
  - `game/GameFactory.java`: `optionDescriptors()` and `validate(GameOptions)` defaults.
  - `bullshit/domain/BullshitFactory.java`: descriptors for `claimMode` and `deckSize` taken from
    `ClaimModeOption` and `DeckSize`; `validate` via `BullshitOptions.from`.
  - `sessionmanagement/core/application/GameFactories.java`: `optionDescriptors(type)`, `validate(type, options)`.
  - `SessionService.java`: `updateOptions(GameId, hostToken, Map<String,String>)` (reuse `openLobbyOwnedBy`).
  - `SessionGame` / `SessionRepository` (`saveLobby`): replace the options by saving a lobby sharing the seat map.
  - `LobbyView.java`: `optionDescriptors`, `canEditOptions`; `options` filled with descriptor defaults for missing keys.
  - New `presentation/LobbyController.java` (`@Controller`, like `LifecycleController`): `/lobby/setOptions`
    with payload `LobbyOptionsPayload(gameId, token, values)`; sends the shared lobby broadcast; errors go to the actor seat only.
- Tests first:
  - `BullshitFactoryTest`: descriptors list both keys, defaults pass `validate`, an unknown value is refused by the domain.
  - `GameFactoriesTest`: delegation.
  - `SessionServiceOptionsTest`: host updates; guest refused (`NotHostException`); after start refused
    (`GameAlreadyStartedException`); invalid value refused and options unchanged; two successive updates merge;
    options survive `playAgain`.
  - `LobbyViewTest`: descriptors present; `canEditOptions` true for the host only; defaults filled.
  - `LobbyOptionsIT` (two STOMP clients, pattern of the existing Bullshit lobby ITs): the guest receives the
    new options without refreshing.
- Commands: `mvn ... test -Dtest='SessionServiceOptionsTest,BullshitFactoryTest,LobbyViewTest,LobbyOptionsIT'`, then the full suite.
- Backward compatibility to assert in a test: `createRoom(..., GameOptions)` and `BullshitCreatePayload` with
  `claimMode` / `deckSize` still work.
- Conflict: `SessionService` / `LobbyView` with PR3, so PR3 starts after this merges. Parallel: yes (Wave A).

## PR 3: `feat: seat options in the lobby (rename, bot level)` (backend)

- Create: kernel `game/BotLevel.java` (`EASY`, `NORMAL`, `HARD`).
- Modify:
  - `SessionPlayer`: `BotLevel level` (default NORMAL, only meaningful for bots), cleared by `release()`.
  - `SessionGame`: `renameSeat(PlayerId, String)`, `setBotLevel(PlayerId, BotLevel)` (`NotABotSeatException` for a human seat);
    `claimBot` takes an optional level; `removeBot` shifting keeps the level of the shifted bot.
  - `SessionService`: `renameSeat(GameId, token, name)` (occupant, lobby only), `setBotLevel(GameId, hostToken, seat, level)`,
    `addBot(id, hostToken, level)` overload.
  - `LobbyView.LobbyPlayer`: `botLevel`.
  - `LobbyController`: `/lobby/rename`, `/lobby/setBotLevel`, `/lobby/addBot`, `/lobby/removeBot`;
    `BullshitWebSocketController` `/bullshit/addBot` and `/bullshit/removeBot` delegate to the same service methods and
    are marked deprecated.
  - `SeatView` and `BotSeats` stay unchanged here (PR5 and PR7 change the bot port).
- Tests first: `SessionGameBotsTest` (level kept through `removeBot` shifting, default NORMAL, human seat refused),
  `SessionServiceBotsTest` (host-only, lobby-only), `SessionServiceRenameTest` (occupant only, blank restores default,
  trimmed, 20 characters max, refused after start), `LobbyViewTest` (`botLevel`), `LobbyControllerIT` (rename and level reach all seats;
  deprecated Bullshit aliases still work).
- Conflict: after PR2 (same classes). Parallel: Wave B.

## PR 4: `feat: shared lobby component, adopted by Bullshit` (frontend)

- Create:
  - `components/lobby/GameLobby.vue` (extracted from `BullshitLobby.vue`), `components/lobby/LobbySeats.vue`,
    `components/lobby/LobbyOptions.vue` (renders descriptors: radio groups for the host, text rows for others),
    `components/lobby/LobbyInvite.vue` (link, Copy, `aria-live`, select-all fallback).
  - `model/lobby/LobbyView.ts` (generalises `model/bullshit/LobbyView.ts`: `optionDescriptors`, `canEditOptions`,
    `botLevel`), `model/lobby/optionLabel.ts` (resolves `options.<game>.<key>.<value>` with fallback to the raw value
    and the `dependsOn` parameter, replaces `claimModeLabel`).
  - `composables/useLobbyActions.ts`: the `LobbyActions` implementation over `webSocketService` for destinations `/lobby/*`
    (used by every game store).
  - Locale keys `lobby.*` (neutral), `options.bullshit.*` (moved from `bullshitUi.start`).
- Modify:
  - `state/Bullshit.store.ts`: `setOption`, `rename`, forwards to `useLobbyActions`; `application/BullshitSession.ts` publish helpers.
  - `view/bullshit/BullshitGameScreen.vue`: render `GameLobby` instead of `BullshitLobby` (lobby block and imports only).
  - `view/bullshit/BullshitStartGame.vue`: deleted; the entry menu's New game creates the room straight away
    (`store.create(name)`, defaults from the server), the join form is PR1's.
  - `components/bullshit/BullshitLobby.vue` and its test: deleted (tests moved).
  - Add to `GameDefinition` the `createRoom` callback (store action) so the menu can create without a per-game page.
- Tests first: `GameLobby.test.ts` (port the whole `BullshitLobby.test.ts` suite, then add: host sees editable options
  built from descriptors, guest sees read-only text, a change emits `set-option` with key and value, nothing renders when
  descriptors are empty, own name editable and others not, bot level group only for the host, free seat shows Add bot and
  invite for the host), `optionLabel.test.ts` (fallback, dependency on deck size), `Bullshit.store.test.ts` additions,
  `useLobbyActions.test.ts`.
- Cypress: `bullshit-table-and-lobby.cy.ts` and `bullshit-lobby-bots.cy.ts` updated; new `shared-lobby.cy.ts` with two
  browsers (host picks 52 cards, the guest sees it live; the guest has no editable control) and a 375 x 667 fit check
  with 6 seats.
- UI inventory: everything the Bullshit lobby shows today (title, player count, rows with You / Bot / Host, remove button,
  claim mode, deck size, invite and Copy, Start and hint, waiting for host, bot error) must still be there; added: editable
  options for the host, name field, bot level (levels visible only after PR5 if the owner wants them hidden until then;
  default: shown from PR3's data, always NORMAL).
- Depends on PR1, PR2, PR3 and on the answer to question 2 (if the owner wants a create page, keep it with name only).
- Conflict: high (see table). Parallel: no (Wave B).

## PR 5: `feat: Bullshit bots have a difficulty level` (backend, small frontend)

- Modify:
  - `BotTuning`: `EASY`, `NORMAL` (= the old `DEFAULT`), `HARD` constants and `BotTuning.of(BotLevel)`; keep `DEFAULT` as an alias of NORMAL.
  - Shared `BotSeats`: `Map<PlayerId, BotLevel> botLevels(GameId)`; `SessionService.botSeats` adapted.
  - `BotStrategyFactory`: `forSeat(Bullshit game, BotLevel level)`; `BullshitBotCoordinator.Table` keeps a strategy per seat.
  - Frontend `GameLobby` bot level group is enabled and wired (`set-bot-level`).
- Tests first: `BotTuningTest` (invariants of each level: call probability below 1, probabilities sum to at most 1),
  `ProbabilisticBotStrategyTest` per level with a seeded generator (HARD challenges more often than EASY on the same
  scripted claims), `BotEndgameSimulationTest` parametrised over the 3 levels (the game must end), `BotLevelOrderingSimulationTest`
  (HARD beats NORMAL beats EASY over a fixed number of seeded games; tune the numbers of spec section 8.1 until true),
  `BullshitBotCoordinatorTest` (two bots, two levels, each uses its own strategy).
- Commands: `mvn ... test -Dtest='BotTuningTest,ProbabilisticBotStrategyTest,BotEndgameSimulationTest,BotLevelOrderingSimulationTest,BullshitBotCoordinatorTest'`.
- Open question to settle before coding: question 3 (levels and values). Update the spec table with the tuned values.
- Conflict: bot files (see table); merge after PR0 and #127. Parallel with PR7.

## PR 6: `feat: Bataille Corse uses rooms` (backend, additive)

- Modify:
  - `BatailleCorseWebSocketController`: new `/bataillecorse/create` (`createRoom`), `/bataillecorse/start`
    (`startGame` then state broadcast, like `BullshitWebSocketController.start`), keep `/create` for now.
  - `BatailleCorseRestController`: `GET /api/game/{id}` returns the lobby view while not started (needs the token, like
    Bullshit, with the existing tokenless access kept for the old client); `POST /api/game/{id}/join` uses `joinRoom`
    when the room is not started.
  - `BatailleCorseFactory`: unchanged (2 to 2 players).
  - Lobby broadcast after join, add bot, remove bot (shared `LobbyBroadcaster`).
- Tests first (extend the stale `BatailleCorseWebSocketControllerIT` or add `BatailleCorseRoomIT`, the old one is `@Disabled`):
  create a room, join, `canStart` false until two seats are claimed, start deals 2 players, a third join is refused (room full),
  join after start refused, add bot fills seat 1.
- Old solo and fixed-seat paths remain until PR9, so the current frontend keeps working.
- Conflict: low. Parallel: Wave A.

## PR 7: `feat: Bataille Corse bot, driven by the server, with levels` (backend)

- Create (`bataillecorse/domain/bot/`): `BatailleCorseBotStrategy` (pure: decides `SEND`, `SLAP` or nothing from the visible pile
  and the `SlapRules` in force; takes a `RandomGenerator`), `BatailleCorseBotTuning` (`EASY`, `NORMAL`, `HARD`: reaction, notice
  probability, false slap probability; spec section 8.2).
- Create (`bataillecorse/presentation/bot/`): `BatailleCorseBotCoordinator` (after each state change, schedules the bot action with
  jitter, drops stale tasks by game version, runs under the game monitor), `BatailleCorseGameActions` (extract the send and slap
  logic out of `BatailleCorseWebSocketController` so a bot uses the same use cases as a human).
- Modify: `AppConfig` (beans); `BatailleCorseLifecycleBroadcaster` (forfeit of the human stops the bot).
- Tests first: `BatailleCorseBotStrategyTest` (slaps a ten, a pair, a sandwich, a sum of ten; does not slap an ordinary pile;
  probabilities honoured with a seeded generator; NORMAL reaction near today's Gold 1200 ms), `BatailleCorseBotTuningTest`
  (HARD faster than NORMAL faster than EASY), `BatailleCorseBotCoordinatorTest` (fake scheduler: bot sends on its turn only,
  stale task dropped, no action after game end or when no human is left), `BatailleCorseBotGameIT` (host plus bot plays to the end
  without any client).
- Depends on PR0, PR3, PR6; open question 3 (false slap on or off, levels). Mirrors `BullshitBotCoordinator`; do not generalise the two
  coordinators in this PR.
- Conflict: low if PR0 is merged. Parallel with PR5.

## PR 8: `feat: keyboard shortcuts gear for both games` (frontend)

- Generalise `components/bullshit/HotkeySettings.vue` and `composables/useHotkeySettings.ts` to a list of actions given by the game
  (move to `components/HotkeySettings.vue`; Bullshit keeps its actions and storage keys; BC provides Send and Slap on
  `settingsStore.sendKey` / `slapKey`, same `bc_sendKey` / `bc_slapKey` storage keys).
- Add the gear to the BC game screen (`view/alpha/GameScreen.vue`), no other change there. The create page's keybinding block is removed
  in PR9, not here (the page still exists until then), which makes this PR purely additive for BC.
- Tests first: `HotkeySettings.test.ts` and `useHotkeySettings.test.ts` moved and extended with the BC action set (conflicting keys refused,
  persistence in the existing keys), `GameScreen.test.ts` (gear present), Cypress `bullshit-hotkey-settings.cy.ts` unchanged plus a BC case.
- Conflict: `BullshitGameScreen.vue` import line only; `GameScreen.vue` small. Parallel: Wave A.

## PR 9: `feat: Bataille Corse adopts the shared lobby; solo becomes a bot` (frontend + backend cleanup)

- Frontend:
  - BC store and `GameSession`: create a room, hold a single seat token, lobby phase handled like Bullshit's (`phase`),
    start action, rematch through `playAgain`; remove the solo branches (`mode`, `waiting`, both-tokens rehydration, the local `AI`).
  - Delete: `view/alpha/StartGame.vue`, `components/WaitingOverlay.vue`, `model/ai/AI.ts` (+ test), `model/Difficulty.ts`,
    the `difficulty` field of `Settings.store.ts`, the keybinding block and its CSS.
  - `view/alpha/GameScreen.vue`: render `GameLobby` in the lobby phase; opponent name from the seat list ("Bot 1" or the guest) rather than
    `Computer (<tier>)`; rematch button labels from the new model (`model/RematchButton.ts` updated).
  - Add the BC entry to `GameDefinition` (create and start through the BC destinations).
- Backend cleanup: delete `SessionService.createGame` overloads, `GameMode`, `joinGame` (fixed seat 1), `claimAllSeats`, `/create`,
  the BC `join` path, `CreateGamePayload.mode`; enable or delete the stale `BatailleCorseWebSocketControllerIT`. Dead-code check with
  `grep` before the PR.
- Tests first: BC store and session tests rewritten around the room flow; `GameScreen.test.ts`; Cypress specs listed in spec section 9.1
  rewritten (`create-game`, `send-card`, `rehydrate-game`, `waiting-and-names`, `turn-indicator`, `responsive-game-screen`,
  `rules-panel`, `visual-direction`, `bataillecorse-menu-fit`) plus a new `bataillecorse-lobby.cy.ts` (solo with a bot at each level
  reaches the table; invite flow with two browsers; 375 px fit).
- UI inventory: all that the BC create page, waiting overlay and game screen show today (opponent toggle, difficulty, name, keys, invite link,
  opponent label, rematch) must be found elsewhere or consciously removed; list each in the PR description.
- Depends on PR1, 4, 6, 7, 8 and on questions 5 and 6. Conflict: medium; no other task should edit BC files at the same time. Parallel: no.

## PR 10 (optional): `feat: Bataille Corse room options` (backend + frontend)

Only after the owner answers question 4.

- Backend: `bataillecorse/domain/options/BatailleCorseOptions` (parsing and defaults), descriptors in `BatailleCorseFactory`,
  `BatailleCorse` built with the chosen `SlapRules` and `Penality`; the bot strategy reads the rules in force (PR7).
- Frontend: locale keys `options.bataillecorse.*`; `RulesPanel.vue` shows the rules in force from the lobby options.
- Tests first: `BatailleCorseOptionsTest` (defaults equal `SlapRules.DEFAULT` and `PutCardsUnderPile(2)`, invalid refused), slap tests per rule on/off,
  `SessionServiceOptionsTest` with the BC factory, Cypress: host disables a rule in the lobby, the guest sees it, the table respects it.
- No changes to `GameLobby`: this is the proof that descriptors are enough.

## Definition of done for the whole feature

- One entry menu and one lobby component serve Bataille Corse and Bullshit (and Kobo with a `GameDefinition` entry).
- The host edits room options in the lobby, everyone sees them live, they freeze at start and survive a rematch.
- A bot seat has a level in both games; solo Bataille Corse is a room with a bot.
- Old invite links still land on the right screen.
- Dead code removed (`WaitingOverlay`, `StartGame`, `AI`, `Difficulty`, `GameMode`, create-time option fields only if the owner agrees).
- `docs/harnesses.md` unchanged unless a process change comes out of it; roadmap issue #122 commented after each merge.
