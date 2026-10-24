# Mangola: sowing direction mode

Plan: `docs/plans/2026-10-24-mangola-direction-plan.md`. Written by Claude Opus 5.5 on 2026-10-24 from `origin/main`
at `9c3deb02`, after the owner's playtest. Base spec: `docs/specs/2026-10-15-mangola-design.md`. Companion findings
of the same playtest: `2026-10-24-mangola-button-colour-findings.md`, `2026-10-24-mangola-speed-findings.md`.

Legend as in the base spec: **[V]** verified by reading the code at `9c3deb02` (file names given); **[C]** computed
by a throwaway script (not committed); **[O]** owner's words; **[Q]** owner question, listed at the end.

## The request

The owner, after playing [O]: "il faudrait un mode pour modifier le sens du jeu ... en priorité implémente l'autre
sens de jeu c'est le plus important". "Le sens du jeu" is the sowing direction: the seeds travel round each
player's loop of 16 pits one way; the owner wants to be able to play the other way round.

## Key decisions

1. **The two directions are the same game seen in a mirror, so the direction is a view, not a rule.** Reversing
   both loops gives exactly the game of the board mirrored left to right (columns `a` and `h` swapped): every
   sowing, capture, relay, endless check, legal move, bot decision and result corresponds pit for pit [V, C].
   The engine, the wire contract and the bots stay as they are; the client draws the board mirrored.
2. **Each player chooses for themselves, on the table** (canvas option B, recommended; the UI choice is the
   owner's [Q1]): a toggle next to the animation speed button, kept in the browser like the speed. Nothing on the
   server, so rematch, reconnect and hydrate are unaffected by construction.
3. **Default: anticlockwise** (the documented Mangola and what the base spec states), the current clockwise view
   one tap away [Q2].
4. **No backend change in the recommended option, so no new rule, no DTO field and no event.** The host option
   (canvas option A) would need a small backend change (an option descriptor and a DTO field), described below
   as the alternative; it does not change the engine either.
5. **Frontend only, in parallel chunks** (plan): a shared contract first, then the model mirror and the
   preference with its button in parallel, then the wiring with one Cypress spec.

## What the app does today [V]

- **Engine** (`mangola/domain/MangolaGeometry.java`): seat A's loop is `a2 b2 ... h2 h1 g1 ... a1` (inner row
  left to right as written, then the outer row back), seat B's `a4 ... h4 h3 ... a3`. `Sowing.sow` follows
  `MangolaGeometry.next`. `facing` and `behind` are the pits of the same column across the middle line.
- **Screen** (`frontend/src/model/mangola/board.ts`, `screenPosition`): each viewer sees their own half at the
  bottom; side A is drawn as written (row 4 on top, columns `a` to `h` left to right), side B turned by half a
  turn. So **every viewer sees every loop run clockwise**: my inner row (second from the bottom) runs to the
  right, my outer row back to the left; the chevrons say so (`board.test.ts`: `arrowOf(8, 'A')` is `right`,
  `arrowOf(15, 'A')` is `down`, `arrowOf(7, 'A')` is `left`, `arrowOf(0, 'A')` is `up`). Screenshot of the
  playtest stack in `2026-10-24-mangola-button-colour-findings.md` shows the same chevrons.
- **The base spec says the opposite**: "Seen from above, both loops run anticlockwise; seen by each player from
  their own side, their inner row runs from their right to their left" (`2026-10-15-mangola-design.md`, Board).
  That would be true with column `a` on the player's right; the frontend put it on the left. The documented
  Mangola is sown anticlockwise (base spec, "Name and origin", rank 1). The owner played the clockwise version.
- The rules panel does not name a direction ("The small arrows show the direction", `locales/en.ts`, `mangola`),
  so no rule text is wrong either way.

## Why the direction is a mirror [V, C]

Let `m(pit)` swap the column (`a` with `h`, `b` with `g` ...) and keep the row. Then:

- `m` maps seat A's loop onto itself **reversed** (`a2 b2 ... h2` becomes `h2 g2 ... a2`, the outer row likewise),
  and seat B's too; it keeps each pit's owner and row.
- `facing(m(p)) = m(facing(p))` and `behind(m(p)) = m(behind(p))`: both are the same column across the middle.
- Every other rule (two seeds to move, the empty-pit rule, capture, relay, the lap start, endless detection by
  state repetition, the repetition draw, `NO_MOVE`) is stated in terms of "next pit", "facing", "behind", counts
  and positions, never in terms of a column letter.

So sowing pit `p` the other way round on board `B` gives `m` of sowing `m(p)` the usual way on `m(B)`.
**Computed check [C]:** a reversed-loop port of the sowing (loops built independently, each pit's next being its
predecessor) against the app's own `sowingFixtures.sow` on the mirrored board: 20,000 random boards of 64 seeds,
every pit with two seeds or more, **381,660 sowings, 0 mismatches**. The domain PR of the alternative (if ever
built) would turn this into a property test.

Consequences:

- A player who sees the board mirrored plays the other direction **exactly**; nothing on the server needs to know.
- The bots are unchanged and exactly as strong: they play the same game (their evaluation and the layout search
  use counts, mobility and same-column exposure, all mirror-invariant).
- Two players may even see different directions at the same table: they still play one game, the pit names
  (`c2`, ...) stay attached to the same pits on both screens.

## Options weighed

| | A. Host option in the lobby | **B. Player toggle on the table (recommended)** | C. Host default + player toggle |
|---|---|---|---|
| Who chooses | the host, for both | each player, for themselves | host for the room, each player may flip |
| Where | a "Sowing direction" row under "First player" in the shared lobby | a button beside the speed button under the board | both places |
| Backend | option descriptor `direction` in `MangolaFactory`, carried by the aggregate and `MangolaDto.direction`; engine untouched (or made to follow it, see below) | none | as A |
| Rematch, reconnect | the options are kept by `SessionService.playAgain`; the DTO carries it | kept in the browser (`localStorage`), like the speed | both |
| Two players see | the same | each their own | the room's unless flipped |
| Cost (chunks) | 4 (one backend PR needing the owner's merge accord) | 3, frontend only | 5 |
| Risk | a domain field that the engine ignores is misleading; making the engine follow it costs a geometry per direction and a layout-order rule | none on the server; the two screens can differ (harmless, names stay right) | two settings for one thing |

Recommendation: **B**. It delivers the owner's priority with the smallest change, cannot break a game on the
server, and lets each player keep the habit they have (the owner and a guest who learnt it the other way can
both be at ease). A is the right choice if the owner wants the room to impose one direction "like a real board";
C only if both needs are real.

Canvas (Claude Design, 3 options on the real lobby and table, plus a board showing both directions):
https://claude.ai/artifact/7vo1Ky6GsTFdiZEhBttvd7 . **The UI section below waits for the owner's choice [Q1].**

## What changes (option B)

- **Model** (`model/mangola/board.ts`): `screenPosition(pit, viewer, direction)` mirrors the column for
  `anticlockwise` (`COLUMNS - 1 - column` for side A, `column` for side B, i.e. the current B formula swapped);
  `arrowOf(pit, viewer, direction)` and `viewerRows(state, seat, direction)` take the direction and pass it on.
  `nextPit`, `loopOf` and the layout order (`draft.ts`) do **not** change: the server's pits and the `arrange`
  payload are the same in both directions.
- **Preference**: `type MangolaDirection = 'clockwise' | 'anticlockwise'`; a composable `useMangolaDirection()`
  shaped like `useAnimationSpeed` (injected storage, `toggle()`, survives storage errors), key `mangolaDirection`,
  default `anticlockwise` [Q2].
- **Store** (`state/Mangola.store.ts`): `rows` and `arrowOf` read the direction (the store receives it, or the
  screen passes it; the plan fixes one).
- **Screen and board**: a direction button next to `AnimationSpeedButton` in `MangolaBoard.vue`'s footer, with its
  own live announcement; the placement editor follows `store.rows`, so it is mirrored too (it must be, or a seed
  placed on screen would land on the other pit).
- **What follows by itself** (all keyed by pit, not by screen position): the travelling hand and its slides
  (`handStyle` finds the pit's cell in `rows`), the captured and chosen-pit marks, the live-region sentence
  ("Bob sowed c3"), the pits' accessible names, the keyboard arrows (they move on screen), the trace replay
  (`replay.ts`, explicit pits).
- **Rules panel**: one sentence in `mangola` "The board" section: seeds go the way of the small arrows; the button
  under the board turns the direction you see, and the game is the same either way.
- **Locales**: `mangolaUi.board.direction` (accessible name with `{direction}`), `mangolaUi.board.directions`
  (`clockwise`, `anticlockwise`: the button's short names), the rules sentence.

## What stays the same

The engine (`Sowing`, `MangolaGeometry`, `MangolaRules`), the wire contract (DTO, events, `arrange` order, `sow`
pit numbers), the placement phase and its hidden information, the stuck-start check, endless detection, the bots
and their timing, the lobby options, rematch, presence and forfeit, and the speed setting.

## Alternative A, if chosen (for the plan, not recommended)

- **Domain**: an enum `SowingDirection { CLOCKWISE, ANTICLOCKWISE }` read by `MangolaFactory` from a host option
  `direction` (default `anticlockwise`), held by the aggregate and exposed as `MangolaDto.direction`. Two ways:
  (A1) the engine ignores it and the client mirrors (cheap, but a domain value with no domain effect); (A2) the
  engine follows it: `MangolaGeometry.next(pit, direction)`, `Sowing` reads `rules.direction()`, the bots get it
  through the rules they already receive; `arrange` keeps the **clockwise** loop order as its payload order (so
  the contract does not move) and the frontend's `nextPit` and arrows take the direction from the DTO. A2 is the
  honest model; it costs the geometry tables per direction and a Java property test of the mirror.
- **Lobby**: nothing to build; `GameLobby` renders a new `CHOICE` descriptor (labels `options.mangola.direction`).
- **Rematch** keeps the option (`SessionService.playAgain`); **reconnect and hydrate** read it from the DTO.
- Every backend PR on Mangola needs the owner's explicit merge accord.

## Tests

- Model: `board.test.ts` gains the anticlockwise cases (every pit's screen cell and arrow for both sides and both
  viewers: the mirror of today's assertions; for viewer A, `a2` (now at the right end) points left, `h2` (left
  end) down, `h1` right, `a1` up); `viewerRows`
  keeps 4 rows of 8 with the same pits.
- Preference: default, toggle, storage read and write, a broken storage (as `useAnimationSpeed.test.ts`).
- Board and screen: the button's name and live announcement, the footer layout (no overlap with the legend,
  with Skip, at 375 px), the placement editor mirrored.
- Cypress: one new spec `mangola-direction.cy.ts` (toggle, chevrons reversed, column order reversed, the choice
  survives a reload, a sowing still lands on the right pits). The existing spec `mangola-game.cy.ts` steps from
  `a2` to `b2` with the right arrow (line 154): with an anticlockwise default it must pin `clockwise` through
  storage or take the other key. Other Mangola specs click pits by name and are unaffected.

## Owner questions

- **Q1 Where is the direction chosen?** Recommended: B, each player on the table. Alternatives: A, the host in the
  lobby for both; C, both. Canvas: https://claude.ai/artifact/7vo1Ky6GsTFdiZEhBttvd7
- **Q2 Default direction.** Recommended: anticlockwise (the documented Mangola and the base spec). Alternative:
  keep clockwise, the direction of the playtest. The answer may also tell which way the owner played as a child.
