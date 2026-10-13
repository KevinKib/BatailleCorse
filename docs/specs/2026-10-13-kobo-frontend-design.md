# Kobo frontend: design (issue #149)

Scope: the Vue frontend that lets people play Kobo, whose backend is merged (spec
`docs/specs/2026-10-11-kobo-backend-design.md`, plan `docs/plans/2026-10-11-kobo-backend-plan.md`).
Bots are out of scope. Plan: `docs/plans/2026-10-13-kobo-frontend-plan.md`.

Markers: **[V]** verified in the code on `origin/main` at `671bef2` (file:line given), **[H]** hypothesis
of this spec, **[Q]** open product question (section 12, each with a recommendation).

## 1. Key decisions

1. **The server drives every choice; the client only decides what a click means.** The table renders
   the viewer's `KoboDto` and enables exactly what `availableActions` lists **[V]**
   (`KoboDto.java:44`, `Action.java`). A pure function `koboInteraction(state, mySeat, pending)` maps
   (phase, pending power, local selection) to "which slots are clickable and what a click sends".
   No rule (points, powers, matching) is computed in the frontend: the power comes from
   `pendingPower`, the score from `lastRound`, the match verdict from the server.
2. **Pure memory game: a face-down card is shown only while the server says the viewer may see it**
   (starting cards until "I have memorised", the drawn card, the last peek, the revealed round). The
   client keeps no memory of values **[Q1]**. Slot revisions are used for the race guard
   (`expectedRevision`) and to flag "this card changed" visually, not to remember values.
3. **The matching discard is an explicit mode**: a "Match" button (available whenever
   `MATCH_DISCARD` is listed) arms the mode, then one click on any card sends it (plus one click on an
   own card to give, when the target is another player's). `expectedTopRank` and `expectedRevision`
   are captured from the state shown at the moment of the target click **[Q3]**.
4. **Shared bricks first, then a thin Kobo layer.** The Kobo session reuses a generic seat-game
   session extracted from `BullshitSession.ts` instead of a third copy, and the registry
   (`games.ts`) gains what a game without bots and without hotkeys needs (`supportsBots`, optional
   `hotkeys`, picker metadata). These shared changes are the plan's first PRs and must be agreed with
   the Three Men's Morris frontend track (#147), see section 11.
5. **Layout: one table component with two arrangements**, a ring of opponents around a felt ellipse
   at 1280×800 (the Bullshit geometry, extracted) and a wrapped grid of compact opponent tableaux at
   375×667, with every card a 44 px tap target and no horizontal scroll.

## 2. What the backend gives (verified)

- REST `GET /api/kobo/game/{id}?token=` (viewer's `KoboDto`, or the `LobbyView` before the start),
  `POST /api/kobo/game/{id}/join`, `POST /api/kobo/game/{id}/play-again` **[V]**
  (`KoboRestController.java:35-51`). Lobby creation: `POST /api/lobby` `{gameType: "kobo", name}`
  (shared, as Bullshit does at `BullshitSession.ts:62-66`).
- 13 STOMP endpoints, all token-authenticated **[V]** (`KoboWebSocketController.java:48-122`):
  `/app/kobo/start`, `ready`, `draw`, `discardDrawn`, `skipPower`, `endTurn`, `announce`
  (`{gameId, token}`); `swapDrawn`, `kingSwap` (`{slot}`); `peek` (`{seat, slot}`); `blindSwap`
  (`{ownSlot, seat, slot}`); `match` (`{seat, slot, giveSlot?, expectedTopRank, expectedRevision}`,
  both expected values required, `expectedTopRank` a rank label such as `"KING"`, parsed by
  `CardLabels.rank`); `giveTen` (`{seat}`). Plus the shared `/app/presence`, `/app/forfeit`,
  `/app/lobby/*`.
- One private topic per seat, `/topic/game/{id}/seat/{token}` **[V]** (`GameMessagingService.java:47`).
  Every broadcast is a `SuccessResponse(eventType, eventData, message, state)`; a refused command
  answers only the sender with `ErrorResponse(eventType, message, state)` **[V]**
  (`SeatCommands.java:66`). The frontend already handles both shapes (`BullshitSession.ts:246-262`).
- Event types **[V]** (`KoboEventType.java`): `START, ROUND_START, READY, DRAW, SWAP, DISCARD_DRAWN,
  PEEK, BLIND_SWAP, KING_SWAP, SKIP_POWER, END_TURN, KOBO, MATCH, GIVE_TEN, ROUND_END, GAME_OVER`,
  with `KoboEventData(seat, action, slot, targetSeat, targetSlot, card, givenSlot, success,
  barrierOpened)` (`KoboEventData.java:13-22`). A failed match is a *success response* `MATCH` with
  `success: false` (penalty card given); a rejected match (lost race, stale pile) is an
  `ErrorResponse` (no penalty). Lifecycle events reuse `OPPONENT_DISCONNECTED`, `OPPONENT_RECONNECTED`,
  `FORFEIT`.
- `KoboDto` fields **[V]** (`KoboDto.java:23-46`): `started, id, gameType, version, round, phase,
  currentSeat, readySeats, announcer, finalTurnsRemaining, pendingPower, drawPileSize, discardSize,
  discardTop, revealed, players[{seat, name, current, total, handSize, slots[{slot, occupied,
  revision, card}]}], hasDrawn, drawnCard, startingCards[{slot, card}], reveal{seat, slot, revision,
  card}, availableActions, lastRound{players[{seat, handValue, roundPoints, totalBefore, totalAfter,
  reset}], announcer, announcerWon, giftTo}, outcome{status: ONGOING | FINISHED, winners[], reason}`.
  No `mySeat` in the game DTO (the seat comes from the bind and the lobby view, as for Bullshit).
- Cards are `CardDto{rank, suit, name}` with labels `ACE, 2..10, JACK, QUEEN, KING` and upper-case
  suits **[V]** (`CardDto.java`, `sortHand.ts:8`); `PlayingCard.vue` lower-cases them for the image.
- No bots: `KoboFactory` does not override `supportsBots()` (default false, `GameFactory.java:21`) and
  `/lobby/addBot` is refused for it (PR #168) **[V]**. The `LobbyView` sent to the client does not say
  whether bots are supported **[V]** (`LobbyView.java:24-38`).
- No options: the lobby shows no option block (`GameLobby.vue:88` only renders descriptors) **[V]**.
- No deterministic deal for e2e: `Kobo(GameId, int, long seed)` exists (`Kobo.java:97`) but no test
  profile hook selects a seed **[V]**. Section 10 explains how Cypress copes without one.

## 3. What is reused as is, adapted, or created

| Need | Brick | Status |
|------|-------|--------|
| Game selection, Game home, Join form | `GamePickerView`, `GameHome.vue`, `JoinGameForm.vue`, `GameHomeView`, `GameJoinView` | Home and form reused as is; picker and join view need the registry changes of section 11 |
| Lobby (2 to 6, no options, no bots) | `GameLobby.vue`, `LobbySeats.vue`, `LobbyInvite.vue` | Reused; `LobbySeats` must hide Add bot (section 11) |
| Lobby commands | `useLobbyActions.ts` | As is |
| Session plumbing (create, join, bind, hydrate, lobby version, leave, removed, play again) | `BullshitSession.ts:36-262` | Extracted into a generic `SeatGameSession` first (plan PR 2), then reused |
| Bootstrap from the stored token | `useBullshitBootstrap.ts` | Generalised with the session (plan PR 2) |
| Presence, countdown, forfeit banner | `useSeatPresence`, `useSeatDisconnectCountdown`, `SeatDisconnectBadge`, `ForfeitBanner` | As is |
| Leave guard, removed redirect, connection status | `useLeaveGuard`, `useRemovedRedirect`, `useConnectionStatus` | As is, after the leave-guard text moves to the locales |
| Turn cue, timer, rules panel | `YourTurnIndicator`, `GameTimer` (+ `useGameDuration`), `RulesPanel` | As is (rules content added in `en.ts`) |
| End screen | `EndGameOverlay.vue` + `useEndScreen` | Adapted (shared): locale texts and a default slot for the final scores |
| Cards | `PlayingCard.vue` | As is, wrapped in a labelled button (its `<img>` has no `alt`, section 11) |
| Opponent ring geometry | `seatAngles`/`seatPositions` inlined in `BullshitGameScreen.vue:86-102` | Extracted to `model/table/seatRing.ts` |
| Display names | `model/bullshit/displayName.ts` (typed on `BullshitPlayer`) | Generalised to `{seat, name, bot?}` |
| Kobo table, tableau grid, piles, action bar, round scores, event log | — | **Created** |
| Kobo model types, session, store, interaction function | — | **Created** |
| Kobo texts | `locales/Messages.ts`, `locales/en.ts` | **Created**: `gamePicker.kobo`, `kobo` (rules), `koboUi` |

## 4. Routes and registry

- Registry entry `define('kobo', …)` with `supportsBots: false`, no hotkeys **[Q9]**, picker metadata
  (icon, severity). `/games/kobo` and `/games/kobo/join/:id?` then work through the existing
  `knownGame` guard **[V]** (`router.ts:13,19-20`).
- Room route: `{ path: '/games/kobo/room/:id', name: 'kobo-room', component: KoboGameScreen,
  props: route => ({ gameId: route.params.id }) }`, the Bullshit shape (`router.ts:27`). No `/new` or
  `/create` redirects (they never existed for Kobo). Whether rooms move to a generic
  `/games/:game/room/:id` with a component map in the registry is a shared decision (section 11),
  not taken here.
- Token storage key `kobo:tokens:{gameId}` (the Bullshit pattern, `BullshitSession.ts:34`).

## 5. State model

### 5.1 Types (`model/kobo/KoboState.ts`)

TypeScript mirrors of `KoboDto` (section 2), with string unions for `phase`
(`MEMORISING | DRAW | DECISION | POWER | KING_DECISION | TURN_END | GIFT | ROUND_OVER | FINISHED`),
`pendingPower` (`NONE | PEEK_OWN | PEEK_OTHER | BLIND_SWAP | KING`) and `availableActions`.
`KoboView = KoboState | LobbyView`. Event payload `KoboEventData` in `model/kobo/KoboEvents.ts`.

### 5.2 Session (`application/KoboSession.ts`)

A `SeatGameSession<KoboView>` configured with `gameType: 'kobo'`, REST base `/api/kobo`, token key
`kobo:tokens:`, plus one method per command. Each command publishes
`{gameId, token, ...payload}` to `/app/kobo/<name>`. Added to the generic session for Kobo:

- **Version guard**: a started state whose `version` is lower than the held one is dropped (a late
  hydration GET racing the seat topic). The held version resets on every bind and when a lobby view
  is applied, because a rematch reuses the room id and restarts the aggregate at version 0 **[H]**.

### 5.3 Store (`state/Kobo.store.ts`, Pinia setup store)

Common room state (from the shared helper of plan PR 2): `state`, `gameId`, `mySeat`, `roster`,
`lobbyError`, `starting`, `rematch`, `removedByHost`, `lastForfeit`, presence, and the screen
`screen: 'connecting' | 'lobby' | 'playing' | 'finished'` (named `screen`, not `phase`, to avoid a
clash with the game phase).

Kobo state:

- `game` (the `KoboState` or null), `me`, `isMyTurn` (`currentSeat === mySeat`), `can(action)`.
- `pending`: the local selection, a discriminated union
  `none | match-armed | match-give{target, expectedTopRank, expectedRevision} | blind-own{ownSlot}`.
  Cleared when the action that needs it leaves `availableActions`, when the target slot's revision
  changes or empties, on Escape, and on every new round.
- `busy`: a turn command is in flight (set on send, cleared by the next state or error), so a
  double click does not send twice. Match is exempt: it is a race and each attempt is judged alone.
- `events`: the last 20 `{eventType, data}` received, newest first, cleared on a new round; it feeds
  the live log and the short slot highlights.
- `notice`: the last refusal of a turn command, mapped to a localised line by event type
  (`MATCH`: "Too late, nothing happened"; others: "That move is no longer possible").

Commands: `ready, draw, swapDrawn(slot), discardDrawn, skipPower, peek(ref), blindSwap(own, ref),
kingSwap(slot), endTurn, announce, giveTen(seat), armMatch, cancelPending, pickSlot(ref)`.
`pickSlot` is the single entry point of a click on a card: it asks `koboInteraction` what the click
means and sends it or moves `pending` forward.

### 5.4 Interaction function (`model/kobo/interaction.ts`, pure, fully unit-tested)

Input: state, my seat, `pending`. Output: for every slot `{clickable, intent}` and a one-line prompt
key. Rules of the mapping (the server re-checks all of them):

| Situation | Clickable | Click sends |
|-----------|-----------|-------------|
| `pending = match-armed` | every occupied slot of every remaining player | own: `match{seat, slot, expectedTopRank, expectedRevision}`; other's: moves to `match-give` (or sends with no `giveSlot` when I have no card) |
| `pending = match-give` | my occupied slots | `match{…, giveSlot}` |
| `SWAP_DRAWN` listed | my occupied slots | `swapDrawn{slot}` |
| `PEEK` with `PEEK_OWN` | my occupied slots | `peek{mySeat, slot}` |
| `PEEK` with `PEEK_OTHER` or `KING` | others' occupied slots | `peek{seat, slot}` |
| `BLIND_SWAP`, `pending = none` | my occupied slots | moves to `blind-own{ownSlot}` |
| `BLIND_SWAP`, `pending = blind-own` | others' occupied slots | `blindSwap{ownSlot, seat, slot}` |
| `KING_SWAP` listed | my occupied slots | `kingSwap{slot}` |
| otherwise | none | — |

Buttons (action bar) come straight from `availableActions`: Ready / Next round (`READY`), Draw,
Discard (`DISCARD_DRAWN`), Skip (`SKIP_POWER`), End turn, Kobo! (`ANNOUNCE_KOBO`), Match
(`MATCH_DISCARD`, toggles `match-armed`), one "Give 10 to {player}" button per other remaining
player (`GIVE_TEN`), Cancel when `pending` is not `none`.

## 6. The table

### 6.1 Components (`components/kobo/`)

- `KoboTableau.vue`: one player's slot grid. Props: player, viewer-visible faces (a map slot → card
  from `startingCards`, `drawnCard` is not here, `reveal`, revealed round), clickable map, highlight
  map, compact flag. Each occupied slot is a `<button>` wrapping a `PlayingCard`; an empty slot is a
  dashed placeholder with no button.
- `KoboPiles.vue`: draw pile (back + count), discard pile (top face up + count), and the drawn card
  zone (face up for me in `DECISION`, a back for others when `hasDrawn`).
- `KoboSeat.vue`: name, total points, `current` marker, ready check (memorisation / next round),
  announcer badge, final-turn marker, `SeatDisconnectBadge`.
- `KoboActionBar.vue`: the buttons of section 5.4, the prompt line, `YourTurnIndicator`.
- `KoboRoundScores.vue`: the round table from `lastRound` (hand value, round points, total before
  → after, the 75/100 reset flag, the announcer verdict and the gift), used at `GIFT`/`ROUND_OVER`
  and in the end screen slot.
- `KoboEventLog.vue`: always-present polite live region plus a short visible line of the last event.
- `view/kobo/KoboGameScreen.vue`: the screen switch (connecting / lobby / table / end), the shared
  composables, no game rule.

### 6.2 Slot grid

Slot positions are stable server indices **[V]** (backend spec "Tableau and slots"). Placement
(pure function `slotPosition(slot)`, tested): slots 0 and 1 bottom row, 2 and 3 top row (the deal's
square), then each further slot opens a new column on the right, bottom first. Human labels number
slots from 1 ("card 3"). Every player's grid has the same orientation (no mirroring for opponents
across the table) **[H]**. A slot whose `revision` changed since the previous state gets a brief
"changed" highlight; the slots named by the last event (peek target, swapped pair, matched slot,
given slot) get a highlight that the next event replaces (state, not a timer) **[Q5]**.

### 6.3 What is face up for me

- `MEMORISING` and my seat not in `readySeats`: my `startingCards` (slots 2 and 3) face up. After I
  press "I have memorised" they turn face down even though the DTO still carries them until the
  first draw **[H11 of the backend; Q2]**.
- `DECISION` and it is my turn: `drawnCard` face up in the drawn zone.
- `reveal` present: that one slot face up, with a "you looked" marker, until the DTO drops it (my
  next accepted command or the end of my turn) **[Q4]**.
- `revealed` (GIFT, ROUND_OVER, FINISHED): every slot face up, from `slots[].card`.
- Everything else: card backs.

### 6.4 Arrangements

- **1280×800 (and anything ≥ 768 px wide)**: felt ellipse; opponents placed with the Bullshit ring
  (`seatAngles`: 1 opponent at the top, 2 on the top corners, 3+ spread from left over the top to
  right), opponent cards about 48×67; centre: piles and drawn card at about 72×101; bottom: my grid
  at about 80×112 and the action bar; top bar: timer, round number, rules, sound.
- **375×667 (narrow)**: no ring. Opponents in a wrapped grid in play order (1 → one column, 2 → two,
  3 → three, 4 → 2+2, 5 → 3+2), compact tableaux whose cards draw at about 34×48 inside 44×56
  buttons; a 4-card opponent takes about 96×136. Then the piles row, my grid at about 60×84, the
  action bar sticky at the bottom. Height budget about 640 px for 6 players with 4 cards each; a
  tableau that grows (penalty columns) makes the table scroll vertically inside its frame, never
  sideways, and never the page **[H, verified with `ui-change` at 375×667 in the layout PR]**.
- Both: 16 px side gutter, tap targets ≥ 44 px, `prefers-reduced-motion` honoured.

### 6.5 Phases on screen

| Phase | Shown | Main control |
|-------|-------|--------------|
| `MEMORISING` | my two starting cards face up, ready checks on the seats | "I have memorised" (`READY`) |
| `DRAW` | turn cue on the current seat; for me, Draw enabled | Draw; Match always available when listed |
| `DECISION` | drawn card (face up for me, back for others) | click one of my cards to swap, or Discard |
| `POWER` | prompt by `pendingPower` ("Look at one of your cards", "Look at another player's card", "Swap one of your cards with another player's card, blind", "King: look at another player's card") | click per section 5.4, or Skip |
| `KING_DECISION` | the peeked card face up | click one of my cards to swap with it, or Skip (keep) |
| `TURN_END` | — | End turn or Kobo! |
| after a Kobo | announcer badge, "final turns: …" from `finalTurnsRemaining` | — |
| `GIFT` | every card face up, round table, announcer won | announcer: one "Give 10 to …" button per other player; others: "waiting for {announcer}" |
| `ROUND_OVER` | every card face up, round table, ready checks | "Next round" (`READY`) |
| `FINISHED` | `EndGameOverlay` with the final totals in its slot | Play again, Back to home |

### 6.6 End screen

`didIWin` = my seat in `outcome.winners` (ties: several winners **[V]**, `OutcomeDto.java:19`).
Winner line names all winners. Subtitle: by score ("Lowest total: N points") or by forfeit (the
Bullshit wording, from `lastForfeit`). The final totals table goes in the overlay's new default slot.
`useEndScreen(() => screen === 'finished', () => false)` and `revealImmediatelyIfOver()` on reload.

### 6.7 Leave, forfeit, presence

- Back (table footer) leads to `/games`; `useLeaveGuard` confirms while the game runs, which in Kobo
  means every started phase but `FINISHED` (memorisation and round end included: leaving then is a
  forfeit, which the backend handles **[V]** backend spec "Disconnection, forfeit, rematch").
- Lobby Leave as in Bullshit (`BullshitGameScreen.vue:235-238`).
- Disconnected opponents: `SeatDisconnectBadge` on their seat with the countdown; a forfeit shows
  `ForfeitBanner` and the seat disappears from `players` (the roster keeps the name).
- Own connection lost: actions disabled, offline notice, hydrate on reconnect (Bullshit pattern,
  `BullshitGameScreen.vue:173-176`).

## 7. Texts (`frontend/src/locales/`)

New namespaces in `Messages.ts` and `en.ts`: `gamePicker.kobo` (name, description, players
"2–6 players", play), `kobo: RulesMessages` (sections: goal, turn, powers table as text lines,
matching discard, Kobo announcement, scoring with 75/100 → 50 and the end above 100), `koboUi`
(seat labels, phase prompts, buttons, slot accessible names, event log sentences, refusal notices,
round table headers, end screen lines). Every visible string goes there; names are rendered as text
only.

## 8. Accessibility

- Each slot is a `<button>` with an accessible name built from the locales: "{owner}'s card {n},
  face down", "Your card {n}: 7 of hearts" when face up for me, plus "(you can pick it)" state through
  `aria-disabled`/`disabled`; a grid is a `role="group"` named after its owner.
- Keyboard: Tab reaches the action bar and every clickable card, Enter/Space activates, Escape
  cancels `pending`; arrow keys move inside a grid (roving `tabindex`) **[H]**. No single-key
  shortcuts in v1 **[Q9]**.
- Live regions always rendered (the `YourTurnIndicator` pattern, `YourTurnIndicator.vue:4-6`): the
  event log (polite) and refusal notices (assertive only for my own refused command).
- Peeked and starting cards are announced once when they turn face up.
- Colour is never the only signal (highlights also carry an icon or text).

## 9. Animation and sound **[Q5]**

v1: CSS flip on cards turning face up or down, the discard flight of `usePileAnimation` when a card
lands on the discard pile, the existing card sound on discard growth (Bullshit pattern), all off
under `prefers-reduced-motion` (sound keeps its own toggle). The end screen waits for nothing but its
short delay. No animation of swaps beyond the highlight.

## 10. Testing

- **Vitest**: `interaction.ts` (every row of section 5.4), `slotPosition`, the store reducer (version
  guard, pending reset on revision change, busy flag, event log, notices), `KoboSession` (payloads of
  the 13 commands, REST paths, token key), each component (faces shown per phase, buttons per
  `availableActions`, accessible names), the screen switch, the end screen with several winners.
  Fixtures: a `buildKoboState()` builder next to `model/fixtures/index.ts`.
- **Cypress** (`frontend/cypress/specs/kobo-*.cy.ts`), the browser is one player and the others are
  scripted STOMP players (`cypress/support/stompPlayer.ts`, parameterised by game type in plan PR 2),
  no fixed wait: every step waits on the DOM or on a scripted player's frame
  (`waitForFrame(predicate)` added to the helper). Without a seeded deal, the tests learn card values
  from the DTOs: a scripted player swaps its drawn card into its slot 0, so the test knows that card
  (its own `drawnCard`) and the new discard top, and the browser then matches that slot with a known
  outcome. Paths: lobby without Add bot and start with two players; memorisation; a whole turn of
  each kind; a successful and a penalised match; Kobo announcement and final turns to a revealed
  round; next round; forfeit to the end screen and Play again back to the lobby. A full game won on
  points is left to Vitest (too many rounds for e2e).
- **ui-change**: before/after at 375 px and 1280 px, 2 and 6 players, in the layout PR.

## 11. Shared-structure readiness (for a third and a fourth game)

Verdict: **not sufficient yet**. The lobby, home, join form, presence and end-screen timing are
shared and ready, but the registry is not yet the single source of truth, and the per-game session,
store and bootstrap are copied per game. Points (verified at `671bef2`):

Blocking for Kobo or Morris (a functional bug or a missing entry point without a change):

1. `frontend/src/model/games.ts:1` `GameKey` is a closed union; `:18-25` `GameDefinition` has no
   `supportsBots` and `hotkeys` is mandatory (`:24`), so a game without shortcuts must invent some.
2. `frontend/src/components/lobby/LobbySeats.vue:125-136` always shows Add bot to the host; for Kobo
   the server refuses it (PR #168) and the lobby shows an error line. Needs `supportsBots` (from the
   registry, passed by `GameLobby.vue:78-84`, or a server flag in `LobbyView.java:24-38`).
3. `frontend/src/view/GamePickerView.vue:38-51` hard-codes two `GameChoiceCard`s and `:134` a two-column
   grid; `GameChoiceCard.vue:10` limits `severity` to `'success' | 'warn'`. A new game is unreachable
   from `/games` until the picker iterates the registry.
4. `frontend/src/view/GameJoinView.vue:6-7,14-19` chooses the store with a two-way ternary: a Kobo
   invite link would join through the Bataille Corse store (`/api/game/{id}/join`).
5. `frontend/src/composables/useGameHotkeySettings.ts:9` returns the Bataille Corse shortcuts for any
   key that is not `bullshit`, and `LocalPreferences.vue:13,19` always renders the gear (shown on the
   Kobo home and lobby with the wrong settings).
6. `frontend/src/components/EndGameOverlay.vue:5` (`'VICTORY'`/`'DEFEAT'`) and `:21` (`'Back to home'`)
   are hard-coded English, and the overlay has no slot for extra content (Kobo scores).
7. `frontend/src/composables/useLeaveGuard.ts:19` hard-codes an English two-player message ("your
   opponent wins").
8. `frontend/cypress/support/stompPlayer.ts:17` joins through `/api/bullshit/game/{id}/join` only.

Duplication (not blocking, but a third copy otherwise):

9. Session: `application/BullshitSession.ts:60-262` and `application/GameSession.ts:96-330` repeat
   create / join / bind / hydrate / lobby-version / leave / removed-by-host / play-again with only the
   REST base, game type, token key (`BullshitSession.ts:34` vs `GameSession.ts:49`) and start
   destination differing.
10. Store: `state/Bullshit.store.ts:15,122-173` and `state/BatailleCorse.store.ts:15,50-56,114-152`
    repeat `RematchState`, the screen phase, `requestLobbyChange`, `leave`, `startGame`, `playAgain`.
11. Bootstrap: `composables/useBullshitBootstrap.ts:8-40` and `composables/useGameBootstrap.ts:17-56`
    repeat token resolution and the gone/lost redirects.
12. Creator map: `composables/useLobbyCreator.ts:10-15` is a per-game record (type-checked, so it
    fails loudly, but it is one more place per game, like item 4).
13. Router: `router.ts:5-6,22-27` one import and one room route per game (acceptable; a component
    map in the registry is the alternative).
14. Table helpers living under one game: the ring geometry inline in
    `view/bullshit/BullshitGameScreen.vue:86-102`; `model/bullshit/displayName.ts:17` typed on
    `BullshitPlayer`; `model/bullshit/opponentOrder.ts` generic but filed under Bullshit.

Smaller couplings:

15. `locales/Messages.ts:71` the hotkey action names are a closed union of Bullshit and Bataille
    Corse actions; `:129-130` `gamePicker` has one field per game (fine, type-checked); `:228-233`
    the rules namespace of Bataille Corse is the generic name `rules`.
16. `composables/useHotkeySettings.ts:3,5-6,20` is the Bullshit settings under a generic name;
    `composables/useSoundPreference.ts:3` stores the sound toggle under `bullshit.sound`.
17. `service/WebSocketService.ts:3,127` routes the shared game topic to the Bataille Corse store
    (harmless for Kobo, which only uses its seat topic).
18. `components/PlayingCard.vue:2` renders an `<img>` with no `alt`; a card inside a labelled button
    needs `alt=""`.

Points shared with the Three Men's Morris track (#147), **not decided here** and to be settled once
by the owner or the orchestrator: the `GameDefinition` fields (`supportsBots`, optional `hotkeys`,
picker metadata, possibly a room component), the room route shape, the `LobbySeats` Add bot rule,
the `EndGameOverlay` slot and texts, the generic session/store extraction and the `stompPlayer`
parameter. The plan puts them in two shared PRs that either track may own, but only one.

## 12. Open product questions (owner), with a recommendation

- **Q1 Remembered cards.** Pure memory (cards only visible while the rules allow) or a memory aid that
  keeps known cards face up for me until their revision changes? *Recommendation: pure memory, as
  at the real table; a memory aid can come later as a lobby option.*
- **Q2 Starting cards.** Visible until I press "I have memorised", or for a fixed time? *Until I
  press it (the backend has no timer; others see who is still looking).*
- **Q3 Matching gesture.** Explicit Match button then a click on the card, drag-and-drop to the
  discard pile, or "any click on a card outside my turn is a match"? *Match button then click: one
  gesture for mouse, touch and keyboard, no accidental penalty.*
- **Q4 Peeked card.** Shown until I end my turn (what the DTO allows), or until I tap it away?
  *Until the turn ends, with a "Hide" toggle for players who want it gone at once.*
- **Q5 Animation.** Minimal v1 (flips, discard flight, sound) or richer card travel for swaps and
  gifts? *Minimal v1; richer travel in a later polish story.*
- **Q6 Labels.** Button and phase names: "Draw", "Discard", "Swap" (by clicking a card), "Skip",
  "End turn", "Kobo!", "Match", "Give 10 to {player}", "I have memorised", "Next round"; slots
  numbered 1 to n. *Accept these; "Kobo!" keeps the game's name as its call.*
- **Q7 Picker text.** Name "Kobo", "2–6 players", description "Remember your hidden cards and end
  the round with the fewest points." *Accept or rewrite.*
- **Q8 Scores.** Totals on every seat plus the last round's table, or a full round history? *Totals
  and last round only (the DTO carries no history; a client-side history would vanish on reload).*
- **Q9 Shortcuts.** None in v1 (Tab/Enter/Escape and arrows only), or remappable single keys (Draw,
  Match, End turn)? *None in v1; the registry then needs optional hotkeys (shared point).*
- **Q10 Opponent orientation.** Same orientation for every grid, or mirrored for players across the
  table? *Same orientation: slot "3" is always top left, easier to talk about.*
- **Q11 Phone layout with 6 players.** Compact grid with vertical scrolling inside the table, or one
  opponent at a time in a carousel? *Compact grid: every target stays one tap away.*
- **Q12 Kobo confirmation.** Ask "Announce Kobo?" before sending, or not? *No dialog: Kobo and End
  turn are two distinct buttons at the turn's end step, nothing else is pending.*
