# Mangola sowing direction (host option): plan

Spec: `docs/specs/2026-10-24-mangola-direction-design.md`. **Produced by Opus 5.5** on 2026-10-24, **rewritten on
2026-10-25** from `origin/main` at `8b8a33ad` after the owner's decision (host option, default anticlockwise).
Canvas: https://claude.ai/artifact/7vo1Ky6GsTFdiZEhBttvd7 (option A).

## Key decisions (from the spec)

1. **Engine and bots unchanged; the direction is the board's orientation.** Both loops reversed = the board
   mirrored left to right (381,660 sowings, 0 mismatches; committed as a property test in H1). The client swaps the
   column order; `Sowing`, `MangolaGeometry`, `MangolaRules`, the bots, the `arrange` order and the `sow` pits stay.
2. **Server is the single source of truth.** Host option `direction` (`anticlockwise` | `clockwise`) in
   `MangolaFactory`, held by the `Mangola` aggregate as `SowingDirection` (not in `MangolaRules`), sent as
   `MangolaDto.direction`; both clients draw from it. Rematch, reconnect and hydrate come from existing plumbing.
3. **Lobby: the shared option row** "Sowing direction" under "First player", host only, locked at Start,
   editable again in the lobby reopened by Play again (value kept). No new lobby component.
4. **Default anticlockwise for everyone**: existing geometry-dependent unit tests pin `clockwise` in their
   fixtures, new cases cover the default; `mangola-game.cy.ts` follows the new default.
5. **Merge rule: every implementation PR of this plan (H0 to H3) needs the owner's explicit merge accord**: it
   touches the Mangola backend domain and its session options (H2 is frontend only but ships the switch of default
   that the owner must see). The orchestrator merges none of them on its own.

**Tests (every PR):** `cd backend && mvn -s ../deploy/docker/settings.xml test`; `cd frontend && npm test && npx
vite build`. Cypress only in H3 (issue #312 rule: only the specs named there, once; a failing one rerun alone at
most once), on a shifted-port e2e stack with its own compose project name. Dev environment for H3 only (manual
check of the lobby choice and of both tables), on shifted ports, URL given to the owner.

**Conventions:** branches `feat/mangola-direction-...`; a draft PR with its real number at the start of each
chunk; test-first visible in git (`test:` commit before `feat:`); frontend paths relative to `frontend/src/`,
backend paths relative to `backend/src/main/java/org/kevinkib/cardgames/`.

## Status

| Chunk | Title | Cost | Needs | Runs |
|-------|-------|------|-------|------|
| H0 | `feat: Mangola sowing direction contract` | S (< 30 min) | this plan merged | first, alone |
| H1 | `feat: Mangola sowing direction host option (backend)` | M | H0 merged | parallel with H2 |
| H2 | `feat: draw the Mangola board in the game's direction` | M | H0 merged | parallel with H1 |
| H3 | `feat: Mangola direction chosen in the lobby, end to end` | M | H1, H2 merged | last (integration) |

## Files at risk of conflict

| File | Chunks | Handling |
|------|--------|----------|
| `mangola/domain/Mangola.java`, `mangola/presentation/dto/MangolaDto.java` | H0 (field, accessor, DTO field), H1 (constructor with a direction) | series H0 then H1; H2 does not touch the backend |
| `mangola/domain/MangolaFactory.java`, `MangolaFactoryTest.java` | H1 only | |
| `locales/Messages.ts`, `locales/en.ts`, `locales/en.test.ts` | H0 only (option texts and the rules sentence) | H1, H2, H3 do not touch locales |
| `model/mangola/MangolaState.ts`, `model/mangola/direction.ts` | H0 only | |
| `model/mangola/board.ts`, `board.test.ts` | H0 (signatures, parameter ignored), H2 (behaviour) | series H0 then H2 |
| `state/Mangola.store.ts`, `Mangola.store.test.ts`, `components/mangola/MangolaBoard.test.ts`, `MangolaPlacementEditor.test.ts` | H2 only | |
| `cypress/specs/mangola-game.cy.ts`, `mangola-lobby.cy.ts`, new `mangola-direction.cy.ts` | H3 only | |
| `components/mangola/MangolaBoard.vue`, `model/mangola/trace.ts` | none here (H2 needs no change in the board component: it draws `rows`) | the speed follow-up (`2026-10-24-mangola-speed-findings.md`, no PR yet) touches them: no overlap |
| `docs/specs/2026-10-15-mangola-design.md` | H3 only (Board paragraph) | |

## H0 `feat: Mangola sowing direction contract` (S, under 30 minutes)

Shared interfaces, no visible behaviour change, nothing deleted.

- Backend: `mangola/domain/SowingDirection.java` (`ANTICLOCKWISE("anticlockwise")`, `CLOCKWISE("clockwise")`,
  `key()`, `fromKey`, `DEFAULT = ANTICLOCKWISE`, Javadoc: the board's orientation, the loops are the same pits);
  `Mangola`: field `direction`, `direction()`, the existing constructors and `fromLayout` overloads set
  `SowingDirection.DEFAULT` (call sites untouched); `MangolaDto.direction` (`String`, after `seatA`, always written,
  `game.direction().key()`); `MangolaFactory.DIRECTION_KEY = "direction"` (constant only, no descriptor yet).
- Frontend: `model/mangola/direction.ts` (`MangolaDirection`, `MANGOLA_DIRECTIONS`, `DEFAULT_MANGOLA_DIRECTION`,
  `directionOf(state)`: absent or unknown reads as the default); `MangolaState.direction?: MangolaDirection`
  (optional, so the existing fixtures compile; the server always sends it); `board.ts`:
  `arrowOf(pit, viewer, direction: MangolaDirection = 'clockwise')`, parameter ignored for now.
- Locales (`Messages.ts`, `en.ts`): `options.mangola.direction` (`label`: "Sowing direction", `anticlockwise`:
  "Anticlockwise", `clockwise`: "Clockwise"); the rules sentence in `mangola` "The board": "The small arrows show the
  direction: anticlockwise unless the host chose clockwise in the lobby." `en.test.ts`: no empty leaf.
- Tests first: `SowingDirectionTest` (keys, `fromKey` both ways and unknown, default), a DTO test (`direction` is
  `anticlockwise` for a default game, both viewers), `direction.test.ts` (`directionOf`).

## H1 `feat: Mangola sowing direction host option (backend)` (M, parallel with H2)

- `MangolaFactory`: the descriptor `direction` after `firstPlayer` (`CHOICE`, `["anticlockwise", "clockwise"]`,
  default `anticlockwise`, `options.mangola.direction`); `create` reads it and calls
  `new Mangola(id, MangolaRules.OWNER, seatA, direction)` (new constructor in `Mangola`).
- `MangolaMirrorTest` (domain test package): reference reversed-loop sowing written in the test (each pit's next
  is its predecessor in `MangolaGeometry.loop(side)`, same rules as `Sowing`), a seeded generator, a few thousand
  random boards; for every legal pit, `Sowing.sow` on the mirrored board at the mirrored pit equals the mirror of the
  reference (board, endless flag, trace pits mapped by `m`). Guards the claim that the option needs no engine
  change.
- Tests first: `MangolaFactoryTest` (descriptor list now two entries; `validate(none)` fills both; an unknown
  direction is refused; `create` gives `CLOCKWISE` and `ANTICLOCKWISE`), DTO (`clockwise` written for a clockwise
  game, placement and play), `MangolaRestAndLifecycleTest` (line 80: keys `["firstPlayer", "direction"]`; the host
  sets `clockwise`, the started game's DTO says so for both seats; the guest's `setOptions` refused; Play again keeps
  `clockwise` in the reopened lobby and the next game).
- Out of scope, and the review checks it: `Sowing`, `MangolaGeometry`, `MangolaRules`, `domain/bot/*`,
  `MangolaBotCoordinator`, `sessionmanagement/*`, the frontend.

## H2 `feat: draw the Mangola board in the game's direction` (M, parallel with H1)

- `board.ts`: `screenPosition(pit, viewer, direction)`: clockwise unchanged; anticlockwise
  `[ROWS - 1 - row, COLUMNS - 1 - column]` for viewer A, `[row, column]` for viewer B; `arrowOf` uses it;
  `viewerRows(state, seat)` reads `directionOf(state)` (signature unchanged).
- `Mangola.store.ts`: `arrowOf(pit)` passes `directionOf(game)`.
- Default switch in the existing tests: fixtures of `board.test.ts`, `Mangola.store.test.ts` (rows lines 91 and
  95, arrows 101 and 103), `MangolaBoard.test.ts` (keyboard edges line 74, hand positions 159 to 172) pin
  `direction: 'clockwise'` so their assertions keep their meaning; `MangolaPlacementEditor.test.ts` comments say
  which direction the fixture draws.
- Tests first (new): `board.test.ts` anticlockwise cases for both viewers (cells and arrows of every pit; viewer A:
  `a2` at the bottom half's right points left, `h2` at its left points down, `h1` right, `a1` up; viewer B and side
  B likewise; every pit once; a missing `direction` draws anticlockwise); store (rows and arrows follow
  `game.direction`, a new state with the other direction redraws); `MangolaBoard.test.ts` (the right arrow from
  `a2` reaches nothing at the edge for viewer A anticlockwise, the left arrow reaches `b2`; the hand on `b2` sits
  at the mirrored left); placement editor (anticlockwise: clicking `a2` adds to loop index 0, and `a2` is the last
  cell of its row); `MangolaGameScreen.test.ts` (the rules sentence is shown).
- Out of scope: the backend, locales, Cypress, `MangolaBoard.vue` (no change expected; if one is needed, say why
  in the PR).

## H3 `feat: Mangola direction chosen in the lobby, end to end` (M, integration)

- Rebase on H1 and H2; make `MangolaState.direction` required (the server always sends it) if every fixture
  compiles, else leave it optional and say so.
- Lobby unit test (`GameLobby.test.ts` or `MangolaGameScreen.test.ts`): a Mangola lobby with both descriptors shows
  "Sowing direction" with "Anticlockwise" selected for the host, plain text for the guest.
- Cypress, issue #312 rule, **only these three specs, once each**, total time reported:
  - new `mangola-direction.cy.ts`: default anticlockwise on both tables (host's `h2` chevron points down, the
    guest's view likewise mirrored); the host picks clockwise, the guest's lobby shows it before Start; both tables
    draw clockwise; a reload keeps it (hydrate); Play again keeps it; a sowing on the mirrored board ends on the pits
    the server frame says.
  - `mangola-game.cy.ts`: the keyboard test (line 154) takes `{leftarrow}` from `a2` to `b2` and `{rightarrow}` back
    (the real default, not pinned).
  - `mangola-lobby.cy.ts`: the direction row (host chips, default Anticlockwise, a guest's forged `setOptions`
    refused, read-only text for the guest).
- Dev stack on shifted ports (own compose project) for the owner's manual check, URL in the PR.
- Docs: base spec Board paragraph (the app draws the anticlockwise board by default; clockwise is the mirrored
  board, a host option, see this spec); this spec's status line ("implemented in #..."); roadmap issue #1 comment
  after merge.

## Other playtest items (separate PRs, not part of this plan)

- **Button text colour** (`2026-10-24-mangola-button-colour-findings.md`): one small PR, `fix: always use the dark
  colour scheme`, independent of H0 to H3 (touches `main.ts`, `App.vue`). Not Mangola domain, but it changes every
  game's colours: owner's accord before merge. (The first version's direction button, which this finding also
  concerned, is dropped.)
- **Speed** (`2026-10-24-mangola-speed-findings.md`): nothing before the owner agrees on the "current / proposed"
  table; then one PR on `model/mangola/trace.ts`, `traceSpeed.test.ts` and `MangolaBoard.vue`'s slide time. No
  overlap with H0 to H3 (H2 leaves `MangolaBoard.vue` alone).
- **Visible seeds**: merged (#318); its seed code in `board.ts` (`seedLayout`, `showsCount`) and `MangolaPit.vue` is not touched here.
