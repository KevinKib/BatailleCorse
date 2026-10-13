# Three Men's Morris frontend: plan

Issue #147. Spec: `docs/specs/2026-10-13-three-men-morris-frontend-design.md` (key decisions, contract,
shared-structure readiness, product questions). Written by Claude Opus 5.5.

**Goal:** play Three Men's Morris from the Game selection to the end screen and Play again, on the shared
Game home and lobby, without a third copy of the session code and without "Add bot".

**Order:** PR0, PR1 and PR2 (shared, no visible change except texts) can run in parallel; PR3 needs PR1;
PR4 needs nothing but PR0's registry types; PR5 needs PR0 to PR4. The game becomes visible only in PR5
(its registry entry), so nothing half-done reaches the picker.

**Tests (every PR):** `cd frontend && npm test && npx vite build`; backend untouched, but the global rule
still runs `cd backend && mvn -s ../deploy/docker/settings.xml test` before each PR. Cypress (`docker
compose -f docker-compose.e2e.yml`, `npm run cy:run`) for PR0, PR2 and PR5, which change what is seen;
waits only on DOM states (`should`), never `cy.wait(ms)`. Dev environment only for PR5 (manual test).

**Conventions:** paths relative to `frontend/src/`. Branches `refactor/...` and `feat/...`, titles in the
conventional style, one draft PR opened at the start of each step. Every new visible text through
`locales/Messages.ts` and `locales/en.ts`.

**Coordination with Kobo (#149):** PR0 and PR1 serve both games. Whichever plan starts first owns them;
the other plan drops them and rebases on them.

## Status

| PR | State |
|----|-------|
| PR0 | [ ] to do |
| PR1 | [ ] to do |
| PR2 | [ ] to do |
| PR3 | [ ] to do |
| PR4 | [ ] to do |
| PR5 | [ ] to do |

## Conflicts

| File | PRs | Handling |
|------|-----|----------|
| `model/games.ts`, `model/games.test.ts` | PR0, PR5 (and Kobo) | PR5 only adds one entry |
| `locales/Messages.ts`, `locales/en.ts` | PR0, PR2, PR5 | additive, rebase |
| `state/Bullshit.store.ts`, `application/BullshitSession.ts` | PR1 only | |
| `components/lobby/*` | PR0 only | |
| `cypress/support/stompPlayer.ts` | PR5 (and #167 if still open) | one optional parameter |

## PR0: `refactor: registry-driven game screens` (frontend, ~1 day)

No visible change for the two existing games. Fixes readiness items 1 to 5.

- `model/games.ts`: `GameKey` gains nothing yet (PR5 adds the key); `GameDefinition` gains
  `supportsBots: boolean`, `hotkeys?: GameHotkeys` (optional), `picker: { icon: string; severity: ... }`.
  `define(key, traits)` takes an object. Bataille Corse and Bullshit: `supportsBots: true`.
- `components/lobby/LobbySeats.vue`: prop `canAddBot`; the Add bot button only when the host and
  `canAddBot`; `GameLobby.vue` passes `game.supportsBots`. Without it a guest still sees "Waiting for a
  player" while a seat is free.
- `components/LocalPreferences.vue`: renders nothing when `game.hotkeys` is absent;
  `composables/useGameHotkeySettings.ts`: an explicit map per key, no fallback to Bataille Corse.
- A game-client registry `composables/useGameClient.ts`: `Record<GameKey, { create(name), join(id, name) }>`
  built from the stores; `useLobbyCreator.ts` and `view/GameJoinView.vue` use it (no ternary).
- `view/GamePickerView.vue`: `v-for` over `GAMES` with `game.picker`; grid `repeat(auto-fit,
  minmax(220px, 1fr))` (one column at 375 px, two or three at 1280 px). `GameChoiceCard.vue`: severity
  type widened to PrimeVue's.
- Tests first: `model/games.test.ts` (traits of each game, `supportsBots` true for both),
  `components/lobby/GameLobby.test.ts` (no Add bot when `supportsBots` false, guest text unchanged),
  `components/LocalPreferences.test.ts` (no gear without hotkeys), `view/GamePickerView.test.ts` (one card
  per registered game), a `useGameClient` test. Cypress `game-picker.cy.ts`, `bullshit-lobby-bots.cy.ts`,
  `game-screens-homogeneity.cy.ts` stay green.

## PR1: `refactor: one seat-game session for Bullshit and the next games` (frontend, ~1.5 days)

No visible change. Fixes readiness items 6 to 8 for the per-seat games.

- `application/SeatGameSession.ts`: the body of `BullshitSession` made generic over `View extends
  { started: boolean }` with a config `{ gameType, apiBase, startDestination, tokenKey }`: create, join,
  restore, bind (seat topic + presence), hydrate (`ok`/`gone`/`lost`/`unavailable`), lobby versioning,
  seat move, leave, removed by host, play again, forfeit, `onResponse`, lobby commands, and a protected
  `publishAction(destination, extra)` for game commands.
- `application/BullshitSession.ts`: extends it, keeps `discard` and `callBullshit`; the storage key
  `bullshit:tokens:<id>` is kept (no lost seats after deploy).
- `composables/useSeatGameBootstrap.ts`: the generic `useBullshitBootstrap` (resolve stored seat, restore,
  hydrate, redirect to join / Game home with `gone` or `lost`), taking the game definition, the token key
  and the store; `useBullshitBootstrap` becomes a one-line wrapper or is replaced at its call site.
- `state/seatRoom.ts`: a helper used inside a setup store for the shared room state (`lobbyError`,
  `starting`, `rematch`, `removedByHost`, `lastForfeit`, `requestLobbyChange`, `leave`, `startGame`,
  `playAgain`, presence wiring); `RematchState` only in `model/RematchButton.ts`.
- `Bullshit.store.ts` uses it. Bataille Corse's `GameSession` and store are not touched.
- Tests: `BullshitSession.test.ts`, `Bullshit.store.test.ts`, `useBullshitBootstrap.test.ts` unchanged and
  green (the proof); new `SeatGameSession.test.ts` with a fake game type (URLs and destinations built from
  the config, token key used, start destination) and `useSeatGameBootstrap.test.ts`.

## PR2: `fix: shared end, disconnect and leave texts through the locales` (frontend, ~0.5 day)

Visible only as identical English text. Fixes readiness item 11.

- `locales/Messages.ts`, `en.ts`: `endScreen` (`victory`, `defeat`, `backToHome`), `disconnect`
  (`title` with `{player}`, `waiting`, `countdown` with `{n}`), `leaveGuard.confirm`, and the four
  `endGameMessage` sentences.
- `components/EndGameOverlay.vue`, `components/DisconnectOverlay.vue`, `composables/useLeaveGuard.ts`,
  `model/endGameMessage.ts` read them.
- Tests: `EndGameOverlay.test.ts`, `endGameMessage.test.ts`, `locales/en.test.ts` updated to the
  locale values; Cypress `bullshit-end-screen.cy.ts` and `turn-indicator.cy.ts` unchanged.
- Optional, if the owner agrees (Q5): picker title "Card Games" to "Games" (`en.ts:157`) and a
  `GameDefinition.decoration: 'cards' | 'board'` read by `TitlePanel.vue` and `GameLobby.vue`. Otherwise
  left out.

## PR3: `feat: Three Men's Morris model, session and store` (frontend, ~1 day, after PR1)

Nothing routed yet.

- `model/threemenmorris/MorrisState.ts`: `MorrisState` (the DTO), `MorrisView = LobbyView | MorrisState`,
  `MorrisOutcome`, `MorrisEventData`.
- `model/threemenmorris/board.ts` (pure): `POINTS` (row, col), `LINES` (8 segments to draw),
  `neighbourByArrow(point, key)` (grid moves, no wrap), `pointName(point, texts)`,
  `selectable(state, mySeat)`, `targets(state, selected)`, `onPoint(state, mySeat, selected, point)`
  returning `{kind:'place',point} | {kind:'select',point} | {kind:'move',from,to} | {kind:'clear'} |
  {kind:'none'}`. Only `availableActions`, `board` and `legalMoves` are read: no adjacency table in the
  client.
- `application/threemenmorris/MorrisSession.ts`: `SeatGameSession` with `gameType: 'three-men-morris'`,
  `apiBase: '/api/three-men-morris'`, `startDestination: '/app/three-men-morris/start'`, token key
  `three-men-morris:tokens:<id>`, `place(point)` and `move(from, to)`.
- `state/ThreeMenMorris.store.ts`: room state from `seatRoom`, plus `game`, `phase`, `selected`,
  `lastMove` (from `PLACE`/`MOVE`/`GAME_OVER` data), `isMyTurn`, `iWon`, `myColour`; `pointClicked(point)`
  applies `onPoint`; the selection clears on every state.
- Tests first: `board.test.ts` (placement: free points only on my turn; movement: movable pieces from
  `legalMoves`, targets of a selection, re-select, clear; nothing when it is not my turn or the game is
  over; arrows stop at the edges), `MorrisSession.test.ts` (REST URLs, STOMP payloads `{gameId, token,
  point}` and `{gameId, token, from, to}`, token key, lobby view with `mySeat` applied [H1]),
  `ThreeMenMorris.store.test.ts` (phase from state, selection dropped on a new state, `iWon` from
  `outcome.winner` as a number, `lastMove`, forfeit reason kept).

## PR4: `feat: Three Men's Morris board` (frontend, ~1 day, after PR0)

A presentational component, not routed yet.

- `components/threemenmorris/MorrisBoard.vue`: square container sized `min(88vw, 52vh, 480px)`; SVG
  `viewBox="0 0 100 100"` with the 8 lines (`aria-hidden`), the winning line stroked in gold; nine
  `<button>`s on a 3x3 grid (`data-test="point-<n>"`, `data-owner`, `data-state="legal|selected|..."`),
  roving `tabindex`, arrows via `neighbourByArrow`, Enter/Space click, Escape emits `clear`; accessible
  name from `pointName` + owner + "can move" / "free"; `aria-pressed` on the selected piece;
  `aria-disabled` when inert; a 150 ms fade/scale on a newly placed piece, removed under
  `prefers-reduced-motion`. Emits `point` and `clear`.
- `components/threemenmorris/MorrisPlayers.vue` (or two `MorrisPlayerTag`s): name (text only, user
  input), colour chip, pieces left to place, active glow.
- Tests: `MorrisBoard.test.ts` (renders 9 buttons with names, owner classes, legal and selected states,
  emits on click and Enter, arrows move focus, inert when disabled, winning line drawn),
  `MorrisPlayers.test.ts`.

## PR5: `feat: play Three Men's Morris` (frontend, ~2 days, after PR0 to PR4)

The game appears in the picker. Manual test on the dev environment; `ui-change` before/after for the
picker, the Game home and the lobby (375 and 1280 px).

- `model/games.ts`: `GameKey` adds `'three-men-morris'`; entry `define('three-men-morris', {
  supportsBots: false, picker: { icon: 'pi pi-th-large', severity: 'info' } })`, no hotkeys.
  `useGameClient` gets its create/join.
- `router.ts`: `/games/three-men-morris/room/:id` to `ThreeMenMorrisGameScreen` with the `gameId` prop,
  and the `/new`, `/create` redirects for symmetry.
- `view/threemenmorris/ThreeMenMorrisGameScreen.vue`: one root; `GameLobby` (lobby phase), connecting
  screen, table (`GameTimer` top left, `RulesPanel` top right, opponent tag, `MorrisBoard`, my tag with
  `YourTurnIndicator` active on `isMyTurn` and the phase hint, Back bottom left), `DisconnectOverlay`
  from `useSeatDisconnectCountdown`, offline notice and re-hydrate on reconnect (as Bullshit),
  `useLeaveGuard` (`phase === 'playing'`, forfeit), `useRemovedRedirect`, live region for `lastMove`,
  `EndGameOverlay` through `useEndScreen(isOver, () => holdingWinningLine, undefined, 3000)` with
  `winnerLine` ("Winner: {player}") and a subtitle per reason (`LINE`: "You lined up three" / "{player}
  lined up three"; `BLOCKED`; `FORFEIT` with `RESIGNED`/`DISCONNECTED` from `lastForfeit`).
- Locales: `gamePicker['three-men-morris']`, `threeMenMorris` (rules: goal, board, placement, movement,
  blocked, forfeit), `threeMenMorrisUi` (colours, phase hints, point names, announcements, end texts,
  connecting, offline, back).
- `cypress/support/stompPlayer.ts`: `joinAsScriptedPlayer(gameId, name, gameType = 'bullshit')`.
- Tests first: `ThreeMenMorrisGameScreen.test.ts` (phases, end subtitle per reason, leave guard only while
  playing, live region text, Back), `games.test.ts`, `GamePickerView.test.ts` (three cards), `router.test.ts`.
- Cypress `cypress/specs/three-men-morris.cy.ts`, the browser as White (host) and a scripted Black over
  STOMP (`/app/three-men-morris/place|move`), every wait on a DOM state (`[data-test="point-4"]` with
  `data-owner="1"`):
  1. picker card leads to the Game home; New game opens the lobby; no Add bot; Start disabled until Black
     joins;
  2. a full game won by White on a line during placement (A1): highlights, "Your turn", winning line, end
     overlay "Victory", winner line; Play again leads back to the lobby;
  3. movement phase: select, legal destinations only, move, re-select, Escape;
  4. keyboard only: Tab to the board, arrows, Enter places;
  5. Leave during play: confirm, Black's end screen says White resigned [H2];
  6. 375×667 and 1280×800: board, tags, timer, rules, Back inside the screen, no overlap, no scroll.
  `game-picker.cy.ts` and `game-home.cy.ts` gain the third game.
- After merge: tick #147, comment the roadmap issue #1.

## Not done

No bot, no options, no draw, no drag and drop, no sliding animation or sound (possible PR6 after
playtest, owner question Q4), no hotkey gear, no Bataille Corse session refactor, no backend change.

## Estimated cost

About 7 days: PR0 1, PR1 1.5, PR2 0.5, PR3 1, PR4 1, PR5 2. With PR0 and PR1 already done for Kobo,
about 4.5 days.
