# Kobo frontend: implementation plan (issue #149)

Spec: `docs/specs/2026-10-13-kobo-frontend-design.md`. Backend: merged (`/api/kobo`, `/app/kobo/*`,
`KoboDto`), nothing to change there. Written with Opus on `origin/main` at `671bef2`.

## Key decisions (from the spec)

1. The server drives every choice: buttons and clickable cards come from `availableActions`,
   `phase` and `pendingPower`; a pure `koboInteraction` function only decides what a click means.
   No game rule in the frontend.
2. Pure memory game: a face-down value is shown only while the DTO allows it (starting cards until
   "I have memorised", drawn card, last peek, revealed round); no client-side memory of values (Q1).
3. Matching discard as an explicit mode (Match button, then the target, then the card to give);
   `expectedTopRank` and `expectedRevision` captured at the target click.
4. Shared bricks first: registry fields (`supportsBots`, optional `hotkeys`, picker metadata), a
   generic seat-game session/store/bootstrap extracted from Bullshit, then a thin Kobo layer. These
   shared PRs must be agreed with the Three Men's Morris track (#147) and done once.
5. One table, two arrangements: ring of opponents at 1280×800, wrapped compact grid at 375×667, 44 px
   targets, vertical scroll inside the table only.

## Rules for every PR

- Own worktree and branch `<type>/<subject>` from `origin/main`, PR to `main`, TDD (failing test
  first), Composition API with `<script setup lang="ts">`, every visible text in
  `frontend/src/locales/`, no game rule in components.
- All suites before the PR: `cd frontend && npm test && npx vite build`, and the backend suite
  `cd backend && mvn -s ../deploy/docker/settings.xml test` (untouched, but part of the CI).
- UI PRs follow the `ui-change` skill (inventory before/after, 375 and 1280 px, light and dark) and
  add or update a Cypress spec. E2E: one stack at a time (`docker-compose.e2e.yml`, project
  `CLAUDE.md`), Cypress specs in `frontend/cypress/specs/`, no `cy.wait(<ms>)`: wait on the DOM or on
  a scripted player's frame.
- A manual test environment only for the PRs with visible play (K3 to K7).
- When a PR is launched, open it as a draft with its real number rather than referring to it by its
  label here.

## Order and cost

| # | PR | Type | Depends on | Cost | Shared with #147 |
|---|----|------|------------|------|------------------|
| S1 | `refactor: the game registry drives the picker, join, creators, hotkeys and bots` | shared | — | M (1 day) | yes |
| S2 | `refactor: one seat-game session, store helper and bootstrap` | shared | S1 | M-L (1.5 days) | yes |
| K1 | `feat: Kobo model, session and store` | Kobo | S2 | M (1 day) | no |
| K2 | `feat: Kobo in the game selection, home and lobby` | Kobo | S1, K1 | S (0.5 day) | no |
| K3 | `feat: Kobo table layout for 2 to 6 players` | Kobo | K2 | L (1.5-2 days) | no |
| K4 | `feat: Kobo memorisation and turn actions` | Kobo | K3 | L (1.5-2 days) | no |
| K5 | `feat: Kobo matching discard` | Kobo | K4 | M (1 day) | no |
| K6 | `feat: Kobo round end, gift, scores and end screen` | Kobo | K4 (K5 not needed) | M (1 day) | end-screen slot only |
| K7 | `chore: Kobo accessibility and motion pass` | Kobo | K5, K6 | S (0.5 day) | no |

Total about 8 to 10 days of agent work. S1 and S2 are the shared prerequisite: whichever track
starts first owns them, the other rebases on them (orchestrator decision). K5 and K6 can run in
parallel after K4 (different components; both touch `Kobo.store.ts` and `en.ts`, rebase the second).

## S1 `refactor: the game registry drives the picker, join, creators, hotkeys and bots` (shared)

Readiness items 1 to 8, 15, 18 of the spec, section 11. No visible change for Bataille Corse and
Bullshit except the locale texts of the end screen (same English wording).

- [ ] `model/games.ts`: add `supportsBots: boolean`, make `hotkeys` optional, add picker metadata
      (`icon`, `severity`); `games.test.ts` asserts both existing entries (BC and Bullshit: `true`).
- [ ] `GamePickerView.vue`: iterate `GAMES` (one `GameChoiceCard` per entry, `router.push(game.gameHomeRoute)`),
      grid `repeat(auto-fit, minmax(…))`; widen `GameChoiceCard` `severity`. `GamePickerView.test.ts`
      and `game-picker.cy.ts` stay green.
- [ ] `GameJoinView.vue` and `useLobbyCreator.ts`: one `Record<GameKey, {create, join}>` map
      (`composables/useGameRooms.ts`), so a new key fails to compile until wired.
- [ ] `useGameHotkeySettings.ts`: return `null` for a game without hotkeys; `LocalPreferences.vue`
      renders nothing then.
- [ ] `LobbySeats.vue`: Add bot only when `game.supportsBots` (prop from `GameLobby.vue`); Vitest for
      both cases. Alternative to raise with the owner: a server `canAddBots` in `LobbyView`.
- [ ] `EndGameOverlay.vue`: title and home label from the locales (`end.victory`, `end.defeat`,
      `end.backToHome`), a default `<slot />` under the subtitle; `EndGameOverlay.test.ts` covers the slot.
- [ ] `useLeaveGuard.ts`: message from the locales, worded for any number of players.
- [ ] `PlayingCard.vue`: `alt` prop, default `""`.
- [ ] `cypress/support/stompPlayer.ts`: `joinAsScriptedPlayer(gameType, gameId, name)` and
      `waitForFrame(predicate)`; update the Bullshit specs that call it.
- [ ] Suites; Cypress `game-picker`, `game-home`, `shared-lobby`, `bullshit-lobby-bots`,
      `bullshit-end-screen`, `bataillecorse-lobby` green.

## S2 `refactor: one seat-game session, store helper and bootstrap` (shared)

Readiness items 9 to 11, 14 and 16. Bullshit adopts it; Bataille Corse keeps its `GameSession`
(event queue and animations) unless the owner wants it migrated too (not needed for Kobo).

- [ ] `application/SeatGameSession.ts`: generic class parameterised by `{gameType, restBase,
      tokenKeyPrefix, startDestination}` with `create, join, restore, hydrate, leave, playAgain,
      onResponse, publishCommand(name, payload)`, the lobby-version rule and the removed-by-host
      teardown, moved from `BullshitSession.ts:36-262`. Tests moved from `BullshitSession.test.ts` and
      run against both configurations.
- [ ] `BullshitSession.ts` becomes a thin subclass (discard, callBullshit); its public API and token key
      `bullshit:tokens:` unchanged.
- [ ] `composables/useSeatRoom.ts`: the shared store part (`state`, `screen`, `roster`, `lobbyError`,
      `starting`, `rematch`, `removedByHost`, `lastForfeit`, presence, `requestLobbyChange`, `leave`,
      `startGame`, `playAgain`), used by `Bullshit.store.ts`.
- [ ] `composables/useSeatGameBootstrap.ts(game, gameId, store)`: from `useBullshitBootstrap.ts`
      (Bullshit keeps a one-line wrapper).
- [ ] Move `opponentOrder.ts` and a generalised `displayName.ts` (`{seat, name, bot?}`) to
      `model/table/`; extract `seatAngles`/`seatPositions` to `model/table/seatRing.ts` with tests.
- [ ] All Bullshit Vitest and Cypress specs green unchanged.

## K1 `feat: Kobo model, session and store`

No visible change. Files: `model/kobo/KoboState.ts`, `model/kobo/KoboEvents.ts`,
`model/kobo/interaction.ts`, `model/kobo/slotPosition.ts`, `application/KoboSession.ts`,
`state/Kobo.store.ts`, `model/fixtures/kobo.ts` (`buildKoboState`, `buildKoboPlayer`).

- [ ] Test then implement the DTO types (fixtures shaped exactly like `KoboDto.java:23-64`).
- [ ] `KoboSession` on `SeatGameSession`: one test per command asserting destination and body
      (13 endpoints: start, ready, draw, swapDrawn, discardDrawn, skipPower, peek, blindSwap, kingSwap,
      endTurn, announce, match, giveTen); `match` always carries `expectedTopRank` and
      `expectedRevision`, `giveSlot` only for another player's card.
- [ ] Version guard: an older `version` is dropped; reset on bind and on a lobby view (rematch).
- [ ] `interaction.ts`: one test per row of spec section 5.4, plus: no clickable slot when I am not
      in `availableActions`, empty slots never clickable, `match-give` sends no `giveSlot` when I have
      no card.
- [ ] `slotPosition`: slots 0-3 in the square, 4 and up open columns on the right, bottom first.
- [ ] Store: `pending` reset rules (action gone, revision changed, slot emptied, Escape, new round),
      `busy` set on send and cleared by state or error (not for match), event log capped at 20 and
      cleared on `ROUND_START`, refusal notices mapped by event type.

## K2 `feat: Kobo in the game selection, home and lobby`

- [ ] Registry entry `kobo` (`supportsBots: false`, no hotkeys, icon and severity); `games.test.ts`.
- [ ] Route `/games/kobo/room/:id` (`kobo-room`) to `view/kobo/KoboGameScreen.vue`; `router.test.ts`.
- [ ] Locales: `gamePicker.kobo`, `kobo` rules (`RulesMessages`), first `koboUi` keys (connecting,
      back); `en.test.ts` stays green.
- [ ] `KoboGameScreen.vue`: connecting, `GameLobby`, a placeholder table showing round and players,
      Back, `useSeatGameBootstrap`, `useRemovedRedirect`, `useLeaveGuard`.
- [ ] Cypress `kobo-lobby.cy.ts`: Kobo on `/games`, New game opens the lobby, no Add bot, a scripted
      player joins (`joinAsScriptedPlayer('kobo', …)`), Start enabled at 2, the table replaces the lobby.

## K3 `feat: Kobo table layout for 2 to 6 players`

Read-only rendering, no action yet. `ui-change` before/after.

- [ ] `KoboTableau.vue` (grid from `slotPosition`, face/back per spec 6.3, empty placeholders,
      compact variant), `KoboSeat.vue` (name, total, current, ready, announcer, final turn, presence
      badge), `KoboPiles.vue` (draw count, discard top and count, drawn card zone).
- [ ] `KoboGameScreen.vue`: ring arrangement from `seatRing.ts` at ≥ 768 px, wrapped grid below;
      top bar (timer, round, rules, sound), footer (Back), offline notice, forfeit banner.
- [ ] Vitest: faces shown per phase (memorising, decision, reveal, revealed round), accessible names
      of slots, 2 and 6 players render every seat, penalty slots open a column.
- [ ] Cypress `kobo-table-layout.cy.ts`: at 375×667 and 1280×800 with 2 and 6 players (5 scripted),
      no horizontal scroll (`scrollWidth <= clientWidth`), every slot button at least 44 px, my grid
      visible without scrolling at 2 players.

## K4 `feat: Kobo memorisation and turn actions`

- [ ] `KoboActionBar.vue` (buttons from `availableActions`, prompt from `koboInteraction`,
      `YourTurnIndicator`, Cancel), slot clicks through `store.pickSlot`.
- [ ] Memorisation: starting cards face up until I press "I have memorised", ready checks on seats.
- [ ] Turn: draw, swap by clicking my card, discard, each power (peek own, peek other, blind swap in
      two clicks, king peek then swap or skip), skip, end turn, Kobo!; final-turn markers.
- [ ] `KoboEventLog.vue`: live region and the last-event line; highlights of the slots named by the
      last event and of slots whose revision moved.
- [ ] Keyboard: Tab order, Enter/Space, Escape cancels, arrows inside a grid.
- [ ] Vitest per component and per phase; screen test that a refused command shows the notice and
      re-enables the bar.
- [ ] Cypress `kobo-turn.cy.ts`: memorisation then a full turn of the browser player for each branch
      reachable from the DTO (swap; discard of a powerless card; a power when the drawn card has one,
      else skip), scripted players play their turns by reading their own frames; Kobo announcement
      with final turns until the round is revealed.

## K5 `feat: Kobo matching discard`

- [ ] Match button (toggle, `aria-pressed`), target click captures `discardTop.rank` and the slot
      revision, give-slot step for another player's card, Cancel/Escape.
- [ ] Outcomes: success (`MATCH`, `success: true`) and penalty (`success: false`, a new slot appears)
      in the log; a refused match shows "Too late, nothing happened".
- [ ] Vitest: payloads with both expected values; stale target (revision moved before the give click)
      resets `pending`; match stays possible while another player is in `POWER`.
- [ ] Cypress `kobo-match.cy.ts` (no seed): a scripted player swaps its drawn card into its slot 0
      (value known from its own frame), the browser matches that slot when the top has the same rank
      (success) or a different rank (penalty card appears in the browser's grid). Both outcomes are
      reached by choosing which scripted move to make from the known values.

## K6 `feat: Kobo round end, gift, scores and end screen`

- [ ] `KoboRoundScores.vue` from `lastRound` (hand value, round points, total before → after, reset
      to 50 flag, announcer verdict, gift target).
- [ ] `GIFT`: announcer gets one "Give 10 to …" button per other player; others wait.
- [ ] `ROUND_OVER`: all cards face up, Next round (`READY`) with ready checks; `ROUND_START` clears
      the log and `pending`.
- [ ] `FINISHED`: `EndGameOverlay` with several winners, subtitle by score or forfeit, final totals in
      the slot, Play again back to the lobby (`rematch` state), reload into a finished game shows it.
- [ ] Vitest: round table rows, 75/100 reset flag, tie with several winners, forfeit subtitle.
- [ ] Cypress `kobo-round-and-end.cy.ts`: Kobo announced, round revealed, gift or next round depending
      on `announcerWon` read from the browser's DTO, next round starts in memorisation; then a scripted
      player forfeits (`/app/forfeit`) to reach the end screen, Play again lands in the lobby.

## K7 `chore: Kobo accessibility and motion pass`

- [ ] Flip transitions and the discard flight (`usePileAnimation`), card sound on discard growth, all
      off under `prefers-reduced-motion` (Vitest with the media query mocked).
- [ ] Axe-style checks in Cypress on the lobby, table and end screen (labels, focus order, live
      regions present before their text).
- [ ] Final `ui-change` report at 375 and 1280 px, 2 and 6 players, light and dark.
- [ ] Comment on #149 with the merged PRs.

## Risks

- **Phone height with 6 players**: the budget is tight (spec 6.4); mitigated by the compact variant
  and vertical scroll inside the table, verified in K3 before any action is wired.
- **No deterministic deal in e2e**: tests read card values from DTOs; branches that need a specific
  card (a given power) are taken only when the drawn card allows it, others are covered in Vitest. If
  this proves flaky, a test-profile seed for `KoboFactory` is a small backend change to propose to
  the owner, not planned.
- **Race feedback**: the losing side of a match sees only "too late"; if players find it confusing,
  the backend message could carry a reason code (backend change, not planned).
- **Shared PRs collide with #147**: both tracks touch `games.ts`, `LobbySeats.vue`,
  `EndGameOverlay.vue`, `stompPlayer.ts`. Mitigation: S1 and S2 done once, by one track, before
  either game's K1.
- **Stale hydration vs seat topic**: covered by the version guard (K1).

## Not done

- No Kobo bot, no Add bot in the Kobo lobby.
- No game options (target score), no round history beyond the last round, no spectators, no chat,
  no turn timer.
- No client-side memory aid (Q1), no single-key shortcuts (Q9).
- No backend change.
- No migration of Bataille Corse's `GameSession` to the generic session.
