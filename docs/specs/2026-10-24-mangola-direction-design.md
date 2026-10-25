# Mangola: sowing direction, a host option

Status: implemented in #322 (contract), #324 (backend option), #326 (board drawn in the direction) and #327 (lobby
end to end, Cypress `mangola-direction.cy.ts`).

Plan: `docs/plans/2026-10-24-mangola-direction-plan.md`. Written by Claude Opus 5.5 on 2026-10-24 from `origin/main`
at `9c3deb02`, after the owner's playtest; **rewritten on 2026-10-25 from `origin/main` at `8b8a33ad`** after the
owner's decision on the first version (a per-player toggle, canvas option B, recommended then and now dropped).
Base spec: `docs/specs/2026-10-15-mangola-design.md`. Companion findings of the same playtest:
`2026-10-24-mangola-button-colour-findings.md`, `2026-10-24-mangola-speed-findings.md`.

Legend as in the base spec: **[V]** verified by reading the code at `8b8a33ad` (file names given); **[C]** computed
by a throwaway script (not committed); **[O]** owner's words or decision.

## The request and the owner's decision

The owner, after playing [O]: "il faudrait un mode pour modifier le sens du jeu ... en priorité implémente l'autre
sens de jeu c'est le plus important". "Le sens du jeu" is the sowing direction: the seeds travel round each
player's loop of 16 pits one way; the owner wants to be able to play the other way round.

Decision on the first version of this spec [O]:

- **The direction is the same for both players**: it is a **host option in the lobby** (canvas option A,
  https://claude.ai/artifact/7vo1Ky6GsTFdiZEhBttvd7), not a button each player flips on the table.
- **The default is anticlockwise.** Today the app shows clockwise: the default changes for everyone, existing
  rooms, unit tests and Cypress specs included.

## Key decisions

1. **The engine and the bots do not change; the direction is how the board is laid out on screen.** Reversing
   both loops is exactly the board mirrored left to right (proof below, 381,660 sowings, 0 mismatches), and the
   engine has no notion of left or right. The engine as written, read with column `a` on side A's **right**, *is*
   the anticlockwise game the base spec describes; the frontend draws `a` on the left, which is why the app shows
   clockwise today. So `Sowing`, `MangolaGeometry`, `MangolaRules`, the bots, the `arrange` order and the `sow` pit
   numbers stay as they are.
2. **The server is the single source of truth for both clients.** A host option `direction`
   (`anticlockwise` | `clockwise`, default `anticlockwise`) declared by `MangolaFactory`, held by the `Mangola`
   aggregate as a value `SowingDirection` (not a rule switch: it is not in `MangolaRules`), and sent to every viewer
   as `MangolaDto.direction`. Both clients draw from that field, so both always see the same direction; rematch,
   reconnect and hydrate follow from the existing plumbing (options kept by `playAgain`, state re-read from the DTO).
3. **The lobby control is the shared option row, no new component.** "Sowing direction: Anticlockwise /
   Clockwise" under "First player"; only the host changes it, the guest reads it; it locks when the game starts and
   unlocks in the lobby reopened by Play again, keeping its value.
4. **Each player still sees their own half at the bottom; both see the chosen direction.** A half turn keeps a
   rotation's sense, so anticlockwise seen from above is anticlockwise from either seat. The client only swaps the
   column order of its existing screen mapping.
5. **Mangola domain touched, so every implementation PR needs the owner's explicit merge accord** (the plan's
   four chunks: a short shared contract, then backend and frontend in parallel, then the integration with Cypress).

## What the app does today [V]

- **Engine** (`mangola/domain/MangolaGeometry.java`): seat A's loop is `a2 b2 ... h2 h1 g1 ... a1` (inner row,
  then the outer row back), seat B's `a4 ... h4 h3 ... a3`. `Sowing.sow` follows `MangolaGeometry.next` (its only
  three calls to the geometry's order). `facing` and `behind` are the pits of the same column across the middle line.
- **Screen** (`frontend/src/model/mangola/board.ts`, `screenPosition`): each viewer sees their own half at the
  bottom; side A is drawn as written (row 4 on top, columns `a` to `h` left to right), side B turned by half a
  turn. So **every viewer sees every loop run clockwise** (`board.test.ts`: `arrowOf(8, 'A')` is `right`,
  `arrowOf(15, 'A')` is `down`, `arrowOf(7, 'A')` is `left`, `arrowOf(0, 'A')` is `up`).
- **The base spec says the opposite**: "Seen from above, both loops run anticlockwise; seen by each player from
  their own side, their inner row runs from their right to their left" (`2026-10-15-mangola-design.md`, Board,
  from `plateau.js`). That is the same engine with column `a` on the player's right. The documented Mangola is
  sown anticlockwise (base spec, "Name and origin", rank 1).
- **Options**: `MangolaFactory.optionDescriptors()` declares one host option, `firstPlayer`; `create` reads it.
  `SessionService.updateOptions` (host only, `openLobbyOwnedBy`, before the start) validates the merged map with
  `GameFactory.validate` (defaults filled); `SessionService.playAgain` re-creates the lobby with
  `existing.options()`. The session core never reads a key (`GameOptions` is opaque).
- **Once the game starts the client has no lobby view**: `Mangola.store.ts` keeps `MangolaState` only (its comment:
  a reload mid-game has no lobby data). So a value both clients need during play must be in the game DTO. The Three
  Men's Morris `variant` is the precedent: host option, carried by the game, written in `ThreeMenMorrisDto`.
- **Lobby UI**: `GameLobby.vue` renders every descriptor through `LobbyGameOptions.vue` (radio chips
  `option-<key>-<value>` for the host when `canEditOptions`, plain text for the others); labels come from the
  descriptor's `labelKey` (`options.mangola.firstPlayer` in `locales/en.ts`).
- **Rules panel** (`locales/en.ts`, `mangola`, "The board"): "The small arrows show the direction." No direction
  is named, so no rule text is wrong either way. **README**: no Mangola rule text (checked: no match for Mangola,
  sow, direction).

## Why the direction is a mirror [V, C]

Let `m(pit)` swap the column (`a` with `h`, `b` with `g` ...) and keep the row. Then:

- `m` maps seat A's loop onto itself **reversed** (`a2 b2 ... h2` becomes `h2 g2 ... a2`, the outer row likewise),
  and seat B's too; it keeps each pit's owner and row.
- `facing(m(p)) = m(facing(p))` and `behind(m(p)) = m(behind(p))`: both are the same column across the middle.
- Every other rule (two seeds to move, the empty-pit rule, capture, relay, the lap start, endless detection by
  state repetition, the repetition draw, `NO_MOVE`) is stated in terms of "next pit", "facing", "behind", counts
  and positions, never in terms of a column letter.

So sowing pit `p` the other way round on board `B` gives `m` of sowing `m(p)` the usual way on `m(B)`.
**Computed check [C]:** a reversed-loop port of the sowing against the app's own `sowingFixtures.sow` on the
mirrored board: 20,000 random boards of 64 seeds, every pit with two seeds or more, **381,660 sowings,
0 mismatches**. The backend chunk turns this into a committed property test, so that a future rule depending on a
column letter (none today) breaks a test instead of silently breaking the option.

Consequences:

- Drawing the board mirrored plays the other direction **exactly**; the engine does not need to know.
- The bots are unchanged and exactly as strong in both directions: they play the same game in pit terms (their
  evaluation and layout search use counts, mobility and same-column exposure, all mirror-invariant; their layout
  catalogue is in loop order, which the mirror preserves pit for pit).
- The pit names (`c2` ...) stay attached to the same pits: the live region ("Bob sowed c3"), the accessible names
  and the `data-test` hooks are right in both directions. Under anticlockwise, side A's viewer has `h` on the left
  and `a` on the right; the names are not printed on the board, only spoken.

## Where the option lives (options weighed)

| | **A1. Aggregate value, engine untouched (chosen)** | A2. Rule switch, engine follows it | Option only, no aggregate field |
|---|---|---|---|
| Domain | `SowingDirection` held by `Mangola`, read by nothing in the engine | `MangolaRules.direction`, `MangolaGeometry.next(pit, direction)`, `Sowing` and every bot path read it | none |
| Who tells the clients | `MangolaDto.direction` | `MangolaDto.direction` | nobody once the game has started: the client has no lobby view in play (see above) |
| Engine, bots, `arrange` order | unchanged | `next` per direction; the `arrange` payload order must be pinned to one direction or the contract moves; the layout catalogue becomes direction-dependent | unchanged |
| Screen | the client swaps the column order by direction | the client must still pick a column order, the same one for both directions (else it shows the mirror of the mirror) | — |
| Risk | a domain value the engine ignores: documented as the board's orientation, guarded by the mirror property test | a change in the core of a protected domain for no observable difference | does not work for reloads and hydrate |

Observationally A1 and A2 are the same game: both deliver "the seeds go the other way round". A2 would rewrite the
sowing core of a domain the owner protects, to obtain what a column swap gives; the "option only" variant cannot
reach a reloaded client. **A1 is chosen.** The value is not put in `MangolaRules` because that record is the rule
set the engine and the bots consume (`LayoutRequest` carries it); a switch none of them reads would mislead.

## The design

### Backend (Mangola context only; the session core is untouched)

- **`mangola/domain/SowingDirection.java`**: `enum SowingDirection { ANTICLOCKWISE("anticlockwise"),
  CLOCKWISE("clockwise") }` with `key()`, `fromKey(String)` (unknown key: `IllegalArgumentException`, as
  `FirstPlayer`), `DEFAULT = ANTICLOCKWISE`. Javadoc: the way both loops run seen from above with side A at the
  bottom; the pits and their loops are the same in both, the board is mirrored (column `a` on side A's right when
  anticlockwise).
- **`MangolaFactory`**: `DIRECTION_KEY = "direction"`; a second descriptor after `firstPlayer`:
  `new OptionDescriptor("direction", CHOICE, ["anticlockwise", "clockwise"], "anticlockwise",
  "options.mangola.direction")`; `create` reads it (`map(SowingDirection::fromKey).orElse(DEFAULT)`) and passes it to
  the aggregate. `GameFactory.validate` then refuses an unknown value and fills the default, with no new code.
- **`Mangola`**: a final field `direction` and `direction()`; the existing constructor `(id, rules, seatA)` and the
  `fromLayout` overloads keep their signatures and use `SowingDirection.DEFAULT` (44 test call sites stay as they
  are); a new constructor `(id, rules, seatA, direction)` for the factory. Nothing else in the aggregate reads it.
- **`MangolaDto`**: a field `String direction` (the key, always written, after `seatA`), `game.direction().key()`.
- **Unchanged, on purpose**: `Sowing`, `MangolaGeometry`, `MangolaRules`, `MangolaBoard`, the bots and
  `MangolaBotCoordinator`, `MangolaEventData` (each broadcast already carries the full DTO), the payloads, the
  session core (`SessionService`, `LobbyView`, `GameOptions`).

### Lobby

- The shared option row, rendered by `LobbyGameOptions` from the descriptor: label "Sowing direction", values
  "Anticlockwise" (first, the default) and "Clockwise". Locale keys `options.mangola.direction`
  (`label`, `anticlockwise`, `clockwise`) in `Messages.ts` and `en.ts`. Test hooks come for free:
  `option-direction`, `option-direction-anticlockwise`, `option-direction-clockwise`.
- **Who**: the host only (`canEditOptions`); the guest sees the value as text and gets every change live (the
  lobby broadcast). A forged `setOptions` from the guest is refused as for `firstPlayer`.
- **When it locks**: at Start (a started room's options cannot change). **Play again** reopens the lobby with the
  same options: the next game keeps the direction, and the host may change it before starting again.

### Table (frontend)

- **`model/mangola/direction.ts`**: `type MangolaDirection = 'anticlockwise' | 'clockwise'`,
  `DEFAULT_MANGOLA_DIRECTION = 'anticlockwise'`, `directionOf(state)` (the DTO's value; an absent or unknown one
  reads as the default).
- **`MangolaState.direction: MangolaDirection`** (always sent by the server).
- **`board.ts`**: `screenPosition(pit, viewer, direction)`: clockwise is today's mapping; anticlockwise swaps the
  column, `COLUMNS - 1 - column` for viewer A and `column` for viewer B (rows unchanged). `arrowOf(pit, viewer,
  direction)` passes it on; `viewerRows(state, seat)` reads `directionOf(state)`, so its callers do not change.
  `nextPit`, `loopOf` and the layout order (`draft.ts`) do **not** change.
- **Store**: `arrowOf` passes `directionOf(game)`; `rows` follows by itself.
- **What follows by itself** (all keyed by pit, not by screen position): the placement editor (it draws
  `store.rows`, so it is mirrored with the board, as it must be: a seed placed on screen lands on the pit under the
  finger), the travelling hand and its slides (`handStyle` finds the pit's cell in `rows`), the captured and
  chosen-pit marks, the live region, the pits' accessible names, the keyboard arrows (they move on the screen grid),
  the trace replay (`replay.ts`, explicit pits).
- **What each player sees**: the same direction (one value from the server); each their own half at the bottom.
  With anticlockwise, from either seat: my inner row runs from my right to my left, my outer row back to the right;
  the opponent's loop likewise seen from above. The chevrons show it.
- **Rules panel** (`mangola`, "The board"): "The small arrows show the direction." becomes "The small arrows show
  the direction: anticlockwise unless the host chose clockwise in the lobby."

### Default switch to anticlockwise: impact

- **Existing rooms and running games**: state is in memory, so no migration; a room created without the option
  gets the default. Players used to the playtest's clockwise see the board mirrored: the host can pick clockwise.
- **Backend tests**: `MangolaFactoryTest` (the descriptor list and `validate(none)` gain `direction`),
  `MangolaRestAndLifecycleTest` (line 80, descriptor keys `["firstPlayer", "direction"]`). The engine and bot tests
  do not change.
- **Frontend unit tests** whose assertions depend on columns, today written for clockwise with fixtures that carry
  no direction: `board.test.ts`, `Mangola.store.test.ts` (rows order, lines 91 and 95; arrows, lines 101 and 103),
  `MangolaBoard.test.ts` (keyboard edges, line 74; hand positions, lines 159 to 172), `MangolaPlacementEditor.test.ts`
  (comments on left and right; it clicks by name). Rule: an existing test keeps its meaning by pinning
  `direction: 'clockwise'` in its fixture; the default (anticlockwise) gets its own new cases.
- **Cypress** (the e2e server gives the default): `mangola-game.cy.ts` line 154 steps from `a2` to `b2` with the
  right arrow; under anticlockwise `b2` is to the **left** of `a2` for the host: the test takes the left arrow
  then the right one (it tests the real default, it does not pin clockwise). `mangola-lobby.cy.ts` asserts only
  `option-firstPlayer`: unaffected, gains the direction cases. The other Mangola specs click pits by name or
  measure the footer: unaffected.
- **Docs**: the base spec's Board paragraph gains one sentence (the app draws it this way by default; clockwise is
  the mirrored board, a host option, this spec); README: nothing.

## Tests

- **Backend**: factory (descriptor list, default, `validate` fills and refuses, `create` reads both values),
  aggregate (default direction, constructor with a direction), DTO (`direction` written for both values, for both
  viewers, in placement and play), lobby and rematch through the REST and lifecycle test (the option is listed,
  set by the host, refused from the guest, kept by Play again), and **`MangolaMirrorTest`**: a reversed-loop
  reference sowing written in the test, random boards (seeded generator), `sow(m(B), m(p))` equals `m` of the
  reversed sowing of `(B, p)` (final board, endless flag, trace pits) on every legal pit.
- **Frontend**: `direction.test.ts` (`directionOf`), `board.test.ts` (every pit's cell and arrow, both
  directions, both viewers; for viewer A anticlockwise: `a2` at the bottom half's right points left, `h2` at its
  left points down, `h1` right, `a1` up; every pit once), store (rows and arrows follow `game.direction`, and a new
  state with another direction redraws), board (keyboard and hand positions in both directions), screen (rules
  sentence), lobby (the two-value row from a descriptor, host and guest).
- **Cypress**: a new `mangola-direction.cy.ts` (default anticlockwise for both players: the host's and the
  guest's chevrons; the host picks clockwise in the lobby, the guest's lobby shows it, both tables show it; a
  reload keeps it; Play again keeps it; a sowing on the mirrored board lands on the pits the server says);
  `mangola-game.cy.ts` (keyboard test updated) and `mangola-lobby.cy.ts` (the new row) run once each.

## Dropped from the first version

The per-player toggle on the table (`useMangolaDirection`, `MangolaDirectionButton`, the `localStorage` key, the
footer layout checks for a second round button) and option C. The mirror proof, the file list of what follows by
itself and the test ideas are kept above.

## Owner questions

None open: the owner chose the host option and the anticlockwise default. The labels ("Sowing direction",
"Anticlockwise", "Clockwise") are proposals the owner can reword at review.
