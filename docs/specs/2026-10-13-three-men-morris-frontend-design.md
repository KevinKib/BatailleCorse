# Three Men's Morris frontend: design

Issue #147. Scope: the frontend of "La petite marelle à trois pions" (Three Men's Morris, board B with
the diagonals through the centre). The backend is merged (#146, then the backend mutualization #159 to
#175). Plan: `docs/plans/2026-10-13-three-men-morris-frontend-plan.md`.

Legend: **[V]** verified by reading the code on `origin/main` (2026-10-13, after #175); **[H]** hypothesis
to confirm by a test in the PR that relies on it. **[Q]** points are product questions, listed at the end
with a recommendation.

## Key decisions

1. **Make the shared frontend structures generic first, then add the game.** Today the picker, the join
   view, the lobby creator, the local preferences and the lobby seats name Bataille Corse and Bullshit
   explicitly (see "Shared-structure readiness"). Two small refactors without visible change come first
   (registry-driven screens with a `supportsBots` flag; one seat-game session); Kobo (#149) needs the same
   ones, so they are written once for both.
2. **One generic seat-game session, Morris as a thin configuration.** `BullshitSession` is already the
   right shape for every per-seat game (seat topic, lobby versioning, leave, removed, play again,
   hydrate). It becomes `SeatGameSession<View>` parameterised by the game type (`/api/<type>`, start
   destination, token key); Bullshit moves onto it with its existing tests unchanged as proof, Morris
   only adds `place` and `move`. Bataille Corse keeps its own `GameSession` (public topic, animation
   queue): out of scope.
3. **The server is the only rule source.** The board, whose turn it is, the free points I may place on
   (`availableActions` contains `PLACE`), the moves I may play (`legalMoves`), the winner and the
   `winningLine` all come from `ThreeMenMorrisDto`. The client only keeps a UI selection (the piece I
   picked before choosing its destination). No optimistic update: the board is redrawn from the next
   state.
4. **An accessible board made of real buttons.** Nine HTML `<button>`s laid on a square CSS grid, over a
   decorative SVG that draws the 8 lines (`aria-hidden`). The square scales with the viewport
   (`min(...vw, ...vh)`), points stay at least 44 px at 375×667. Keyboard: one tab stop for the board
   (roving `tabindex`), arrows move between points, Enter/Space acts; each point has an accessible name
   ("Centre, white piece, can move"), the selected piece is `aria-pressed`, and a polite live region
   announces the opponent's move. No remappable single-key shortcuts, so no settings gear.
5. **Shared chrome and end screen reused as they are.** Duration chip top left, rules panel top right,
   Back bottom left with the leave guard (`/app/forfeit`), `YourTurnIndicator` active while I have an
   action, `DisconnectOverlay` for the 2-player grace period, `EndGameOverlay` with a winner line and a
   subtitle per reason (`LINE`, `BLOCKED`, `FORFEIT` + resigned/disconnected). The winning line is
   highlighted on the board and held briefly before the overlay (`useEndScreen` with a hold).

## Backend contract used [V]

- `POST /api/lobby` `{gameType:"three-men-morris", name}` gives `{gameId, token}` (host, seat 0).
- `GET /api/three-men-morris/game/{id}?token=`: the `LobbyView` before the start, else the
  `ThreeMenMorrisDto` of the viewer; 404 game gone, 403 token unknown (`SeatGameRest`).
- `POST /api/three-men-morris/game/{id}/join` and `/play-again` `{name}` give `{playerId, token}`.
- STOMP `/app/three-men-morris/start` `{gameId, token}`, `/place` `{gameId, token, point}`, `/move`
  `{gameId, token, from, to}`; shared `/app/lobby/*`, `/app/presence`, `/app/forfeit`.
- Seat topic `/topic/game/{id}/seat/{token}`: `SuccessResponse(eventType, eventData, message, state)`
  with `START`, `PLACE`, `MOVE`, `GAME_OVER` (data `{seat, action, point, from, to}`), the lobby events
  and the lifecycle events (`OPPONENT_DISCONNECTED`, `OPPONENT_RECONNECTED`, `FORFEIT`) through the same
  `SeatLifecycleBroadcaster` as Bullshit (`config/ThreeMenMorrisConfig.java:41`, `BullshitConfig.java:50`),
  so `useSeatPresence` applies unchanged. A refused command answers the sender alone with its state.
- `ThreeMenMorrisDto`: `started`, `id`, `gameType`, `version`, `phase` (`PLACEMENT`, `MOVEMENT`,
  `FINISHED`), `currentSeat`, `board` (9 entries, row by row, owner seat or null), `players` (`seat`,
  `name`, `current`, `piecesToPlace`), `legalMoves` (`{from,to}`, only for the current seat in
  `MOVEMENT`), `availableActions` (`PLACE` or `MOVE`, only for the current seat), `winningLine` (empty
  unless won by a line), `outcome` (`{status:"ONGOING"}` or `{status:"FINISHED", winner, reason}` with
  `winner` a number, unlike Bullshit's string `winnerId`).
- `GameFactory.supportsBots()` is false for Morris and `/lobby/addBot` is refused (#168).

## What is reused as is [V]

| Piece | File | Note |
|-------|------|------|
| Game home, join form | `components/GameHome.vue`, `components/JoinGameForm.vue`, `components/TitlePanel.vue` | driven by `GameDefinition` once PR0 removes the per-game switches |
| Lobby | `components/lobby/GameLobby.vue`, `LobbySeats.vue`, `LobbyInvite.vue` | no game or seat option (descriptors empty), no Add bot once `supportsBots` exists |
| Lobby commands | `composables/useLobbyActions.ts` | `/app/lobby/*`, game-agnostic |
| Presence | `composables/useSeatPresence.ts`, `useSeatDisconnectCountdown.ts`, `components/DisconnectOverlay.vue` | 2 seats: the BC overlay fits better than the Bullshit seat badge |
| Leave | `composables/useLeaveGuard.ts`, `useRemovedRedirect.ts` | forfeit through `/app/forfeit` |
| End | `components/EndGameOverlay.vue`, `composables/useEndScreen.ts`, `model/RematchButton.ts` | `winnerLine` + `subtitle` |
| Chrome | `components/GameTimer.vue` + `useGameDuration.ts`, `components/RulesPanel.vue` (text sections), `components/YourTurnIndicator.vue`, `composables/useConnectionStatus.ts` | same corners as the two other tables (`cypress/specs/game-screens-homogeneity.cy.ts`) |
| Route fade | `composables/routeFade.ts` | nothing per game |
| Tokens | `composables/readStoredTokens.ts` | key `three-men-morris:tokens:<id>` |

## What is created

- `model/threemenmorris/MorrisState.ts`: DTO types (`MorrisView = LobbyView | MorrisState`).
- `model/threemenmorris/board.ts` (pure): the 9 points (row, column), the 8 line segments to draw,
  the point names for screen readers, the arrow-key neighbour of a point, and a selection reducer
  (`selectable(state)`, `targets(state, selected)`, `onPoint(state, selected, point)` returning
  `place(p)`, `select(p)`, `move(from,to)` or `none`) computed from `availableActions`, `board` and
  `legalMoves` only.
- `application/threemenmorris/MorrisSession.ts`: `SeatGameSession` configured for
  `three-men-morris` plus `place(point)` and `move(from, to)`.
- `state/ThreeMenMorris.store.ts`: Pinia store (same shape as the Bullshit store after PR1): `state`,
  `game`, `lobby`, `phase` (`connecting`, `lobby`, `playing`, `finished`), `mySeat`, `selected`,
  `lobbyError`, `starting`, `rematch`, `lastForfeit`, `lastMove` (for the live region), presence;
  actions `place`, `move`, `select`, `forfeit`, `playAgain` and the lobby actions. The selection is
  dropped whenever a new state arrives.
- `components/threemenmorris/MorrisBoard.vue`: the board (decision 4). Props: `board`, `mySeat`,
  `selectable`, `targets`, `selected`, `winningLine`, `lastMove`, `disabled`. Emits `point`. Pieces are
  CSS discs, ivory for White and ebony for Black **[Q2]**; legal points get a soft
  ring, the selected piece a gold ring, the winning line a gold stroke on the SVG.
- `components/threemenmorris/MorrisPlayers.vue`: two player tags (name, colour, pieces left to place as
  small discs, active glow on the current seat), mine at the bottom, the opponent at the top.
- `view/threemenmorris/ThreeMenMorrisGameScreen.vue`: one root for lobby / connecting / table / end,
  like `BullshitGameScreen.vue`.
- Locales: `gamePicker['three-men-morris']`, `threeMenMorris` (rules, `RulesMessages` text sections),
  `threeMenMorrisUi` (labels, phase hint "Place a piece" / "Move a piece", point names, announcements,
  end texts).
- Route `/games/three-men-morris/room/:id` and the registry entry (`supportsBots: false`, no hotkeys).

## Layout

Phone 375×667: opponent tag at the top, the board as large as the width allows (about 320 px with the
16 px gutters), my tag with the "Your turn" cue and the phase hint below, Back bottom left; no scroll.
Desktop 1280×800: the same column centred, board about 480 px, capped by the height. Timer top left and
rules top right as on the other tables. The board never overlaps the chrome (asserted like the
homogeneity spec).

## Interaction

- Placement: on my turn, free points are highlighted; a click (or Enter) places. Nothing to select.
- Movement: my movable pieces (those with a `legalMoves` entry) are highlighted; a click selects one,
  its legal destinations are highlighted, a click on one moves. A click on another of my movable pieces
  changes the selection, a click elsewhere or Escape clears it.
- Not my turn, offline, or game over: the board is inert (`aria-disabled`), still readable.
- A refused command (race) changes nothing visible: the state it carries is applied.
- Accessibility: the live region reads "Black placed on the centre" / "White moved from top left to
  centre" from the event data; `YourTurnIndicator` reads "Your turn"; focus stays on the board after a
  move; reduced motion removes every transition.

## Out of scope

No bot (backend refuses it), no game or seat option, no draw offer or repetition rule (none in the
backend), no drag and drop, no piece sliding animation and no sound in v1, no hotkey settings, no change
to Bataille Corse's session, no backend change (the `OutcomeDto` unification stays postponed).

## Shared-structure readiness

Verdict: **the backend is ready, the frontend is not quite.** The lobby, presence, end screen and chrome
are reusable, but adding a third game today means editing six shared files that switch on the two
existing games, copying a 260-line session class and a bootstrap composable, and the lobby would offer
"Add bot". Points, by severity:

Blocking (must change before or with the first Morris PR):

1. `frontend/src/components/lobby/LobbySeats.vue:125-136`: the host always sees "Add bot" (only `isFull`
   disables it); `GameLobby.vue:78-84` passes no game. The server refuses it (#168), so the button would
   only produce an error. Needs `GameDefinition.supportsBots` (or a backend `canAddBot` in the lobby view).
2. `frontend/src/model/games.ts:1` closed `GameKey` union; `:24` `hotkeys` is required and `:27` `define`
   takes no other trait (`supportsBots`, picker icon/severity). `model/games.test.ts:6,30` pin the two games
   and the hotkeys shape.
3. `frontend/src/composables/useLobbyCreator.ts:2-3,12-15` and `frontend/src/view/GameJoinView.vue:6-7,14-19`:
   create and join are hard-wired to the two stores (a ternary falls back to Bataille Corse for any other
   key).
4. `frontend/src/composables/useGameHotkeySettings.ts:52` falls back to the Bataille Corse settings for
   any game that is not Bullshit, and `components/LocalPreferences.vue:13,19` always renders the gear
   (rendered by `GameHome.vue:51` and `GameLobby.vue:60`): a game without shortcuts would show Bataille
   Corse's Send/Slap keys.
5. `frontend/src/view/GamePickerView.vue:37-52` lists the two games by hand instead of iterating `GAMES`;
   `:134` a two-column grid; `components/GameChoiceCard.vue:10` `severity: 'success' | 'warn'`;
   `locales/Messages.ts:129-130` `GamePickerMessages` has one fixed field per game.

Duplication (a third copy if nothing is done):

6. `frontend/src/application/BullshitSession.ts` (263 lines) vs `GameSession.ts` (456 lines): create
   (`:60-73` vs `:96-`), join (`:75-87` vs `:111-`), hydrate (`:113-126` vs `:177-`), lobby versioning
   (`:131-154`), leave/removed (`:199-229` vs `:298-`, `:315-`), play again (`:231-244` vs `:130-`); the
   game type and URLs are literals (`:65`, `:77`, `:117`, `:170`, `:234`) and token keys differ
   (`bullshit:tokens:` `:34`, `tokens:` in `GameSession.ts:49`).
7. `frontend/src/composables/useBullshitBootstrap.ts` vs `useGameBootstrap.ts:17-60`: the same
   restore/hydrate/gone/lost redirect, per game.
8. Stores: `RematchState` is declared three times (`model/RematchButton.ts:7`,
   `state/Bullshit.store.ts:15`, `state/BatailleCorse.store.ts:15`); `lobbyError`, `starting`,
   `requestLobbyChange`, `removedByHost`, `leave` are re-implemented in each store
   (`Bullshit.store.ts:31-37,134-162`).
9. `frontend/src/router.ts:5-6,9,21-27`: one hand-written room route and two legacy redirects per game
   (acceptable, one line per game; a generic room route would make the model import views).

Not ready for a non-card game or not translated (visible, not blocking):

10. Card-only decoration on every shared panel: `components/TitlePanel.vue:16-22` and
    `GameLobby.vue:61,64-69` (card fan and the four suits), `GamePickerView.vue:21,24-29`, `GameHome.vue:46`
    and `GamePickerView.vue:15` preload all card images; the picker title is "Card Games"
    (`locales/en.ts:157`). **[Q5]**
11. Hard-coded English in shared components the Morris table would reuse:
    `components/EndGameOverlay.vue:5` (`VICTORY`/`DEFEAT`), `:21` ("Back to home");
    `components/DisconnectOverlay.vue:4-8`; `composables/useLeaveGuard.ts:19` (confirm text);
    `model/endGameMessage.ts:14-22`. The project rule says new visible text goes through `locales/`.
12. `locales/Messages.ts:71` the hotkey action names are a closed union; `:228-233` the per-game
    namespaces are named ad hoc (`rules` is Bataille Corse's).
13. `composables/useTurnIndicator.ts:4-6` is tied to Bataille Corse's `canSend` (Morris uses
    `YourTurnIndicator` directly, as Bullshit does: no change needed).
14. Tests: `cypress/support/stompPlayer.ts:18` joins through `/api/bullshit/...` only;
    `cypress/specs/game-screens-homogeneity.cy.ts:40-45` drives Bullshit only. Open PR #167 (flaky Cypress
    specs) touches the same area.

Items 1 to 8 are planned before the game (plan PR0 and PR1); 10 and 11 are cheap and planned (PR2, and
the decoration as an owner question); 9, 12, 13 are left as they are.

## Risks

- Kobo's frontend (#149) needs the same PR0 and PR1; if both plans run at once, they conflict on
  `games.ts`, `Messages.ts`, `en.ts`, the picker. Mitigation: PR0 and PR1 are owned by whichever plan
  starts first, the other waits for them.
- Moving Bullshit onto `SeatGameSession` (PR1) touches a working game; its 412-line session test and
  530-line store test must stay green unchanged.
- A game can last forever (no draw in the rules retained, [A4] of the backend): only Leave ends it.
- Cypress flakiness (#167 open); every wait is on a DOM state, never a fixed delay.
- **[H1]** The lobby GET answers with `mySeat` and `lobbyVersion` as for Bullshit (`SeatGameRest.java:58`
  calls the shared `lobbyView`): confirmed by the session test of PR3.
- **[H2]** The `FORFEIT` lifecycle event carries `reason` (`RESIGNED`/`DISCONNECTED`) for Morris as for
  Bullshit (same broadcaster class): confirmed by the Cypress forfeit case.

## Product questions (owner) with a recommendation

- **Q1 Colour names and who starts.** Seat 0 is White and always starts (backend [A2]). Recommend:
  show "White" / "Black" next to each name, a line "You play White, White starts" at the first turn; no
  colour choice in the lobby.
- **Q2 Look of the pieces and board.** Recommend: board drawn on the existing felt, lines in the gold
  token, pieces ivory and ebony discs with a thin rim; no wooden-board theme.
- **Q3 Blocked rule.** Practically unreachable on board B ([A3]). Recommend: one line in the rules panel
  and an end subtitle ("Black cannot move: White wins"), nothing on the board.
- **Q4 Animation.** Recommend: v1 only a short fade/scale when a piece appears or lands (off under reduced
  motion), the winning line highlighted for about 1.2 s before the end overlay; sliding pieces and sounds
  later if wanted.
- **Q5 Shared decoration and picker title.** Recommend: rename "Card Games" to "Games", keep the card fan
  on the picker (brand), and let a game declare its decoration in the registry so the Morris home and
  lobby show a small board glyph instead of the suits; or, cheaper, keep the cards everywhere in v1.
- **Q6 Highlights.** Recommend: on my turn highlight the free points (placement) or my movable pieces
  (movement), then the destinations of the selected piece; never show the opponent's options.
- **Q7 Shortcuts.** Recommend: no hotkey gear; the board is fully operable with Tab, arrows,
  Enter/Space and Escape.
- **Q8 Endless games.** Recommend: no draw offer in v1; revisit after playtests (a draw would need a
  backend rule).
- **Q9 Game name.** Recommend: "Three Men's Morris" in the English UI, picker pitch "Line up your three
  pieces. Place, then slide." and "2 players".
