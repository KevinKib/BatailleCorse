# Mangola sowing direction: plan

Spec: `docs/specs/2026-10-24-mangola-direction-design.md`. **Produced by Opus 5.5** on 2026-10-24 from
`origin/main` at `9c3deb02`. Canvas for the UI choice: https://claude.ai/artifact/7vo1Ky6GsTFdiZEhBttvd7

## Key decisions (from the spec)

1. **The other direction is the same game in a mirror** (both loops reversed = the board mirrored left to right;
   checked on 381,660 sowings, 0 mismatches). So the direction is drawn by the client: the engine, the contract
   and the bots do not change.
2. **Each player picks it on the table** (canvas option B, recommended, waits for the owner [Q1]): a button next
   to the speed button, kept in the browser like the speed.
3. **Default anticlockwise** (documented Mangola, base spec), clockwise one tap away [Q2].
4. **Frontend only**: three chunks, the middle two in parallel; one new Cypress spec.
5. **Merge rule:** this is Mangola. **Every PR of this plan needs the owner's explicit merge accord**, the
   docs-only one included (the orchestrator merges only after that accord).

This plan is executed only after the owner answers Q1 and Q2. If the answer to Q1 is A or C, the section
"If the owner picks A or C" replaces chunk D2's scope and adds a backend chunk.

**Tests (every PR):** `cd backend && mvn -s ../deploy/docker/settings.xml test` (unchanged code, still run);
`cd frontend && npm test && npx vite build`. Cypress only in D3 (issue #312 rule: only the specs named there,
once; a failing one rerun alone at most once), on a shifted-port e2e stack with its own compose project name.
Dev environment for D3 only (manual check of the toggle).

**Conventions:** branches `feat/mangola-direction-...`; a draft PR with its real number at the start of each
chunk; test-first visible in git (`test:` commit before `feat:`); frontend paths relative to `frontend/src/`.

## Status

| Chunk | Title | Cost | Needs | Runs |
|-------|-------|------|-------|------|
| D0 | `feat: Mangola direction contract (types and locale keys)` | S (< 30 min) | Q1, Q2 answered | first, alone |
| D1 | `feat: mirror the Mangola board for the anticlockwise view` | S | D0 merged | parallel with D2 |
| D2 | `feat: Mangola direction preference and button` | S | D0 merged | parallel with D1 |
| D3 | `feat: choose the Mangola sowing direction on the table` | M | D1, D2 merged | last (integration) |

## Files at risk of conflict

| File | Chunks | Handling |
|------|--------|----------|
| `locales/Messages.ts`, `locales/en.ts`, `locales/en.test.ts` | D0 (keys and texts), D3 (rules sentence only if D0 left it out) | D0 writes every key; D1 and D2 do not touch locales |
| `model/mangola/board.ts`, `board.test.ts` | D0 (signature with a defaulted parameter), D1 (behaviour) | series D0 then D1 |
| `components/mangola/MangolaBoard.vue` | D3 only (footer) | the speed follow-up PR also touches it (hand slide): rebase, footer lines vs style lines |
| `state/Mangola.store.ts`, `view/mangola/MangolaGameScreen.vue` | D3 only | |
| `cypress/specs/mangola-game.cy.ts` | D3 only (pin the direction for the keyboard test) | |
| `model/mangola/trace.ts`, `composables/useSowingAnimation.ts` | none here | the speed follow-up only |
| `model/mangola/board.ts`, `MangolaBoard.vue` (outside this plan) | D0, D1, D3 | the "visible seeds" work (`docs/specs/2026-10-24-mangola-seeds-visible-notes.md`, option A) touches `seedDisplay` in `board.ts`, `MangolaPit.vue` and the hand in `MangolaBoard.vue`: different functions and lines; whichever merges second rebases |

## D0 `feat: Mangola direction contract` (S, under 30 minutes)

Shared interfaces, no behaviour change, nothing deleted.

- `model/mangola/direction.ts`: `export type MangolaDirection = 'clockwise' | 'anticlockwise'`,
  `export const MANGOLA_DIRECTIONS`, `export const DEFAULT_MANGOLA_DIRECTION: MangolaDirection` (the Q2 answer),
  `export const MANGOLA_DIRECTION_KEY = 'mangolaDirection'`, `export function otherDirection(d)`.
- `model/mangola/board.ts`: `arrowOf(pit, viewer, direction = 'clockwise')` and
  `viewerRows(state, seat, direction = 'clockwise')` accept the parameter and ignore it (D1 implements it).
- Locales (`Messages.ts`, `en.ts`): `mangolaUi.board.direction` ("Sowing direction: {direction}"),
  `mangolaUi.board.directions` (`clockwise`: "Clockwise", `anticlockwise`: "Anticlockwise"), and the rules
  sentence in `mangola` "The board" ("The button under the board turns the direction you see; the game is the
  same either way."). `en.test.ts`: no empty leaf.
- Tests: `direction.test.ts` (`otherDirection` both ways); the locale test.

## D1 `feat: mirror the Mangola board for the anticlockwise view` (S, parallel with D2)

- `board.ts`: `screenPosition(pit, viewer, direction)`: anticlockwise uses `COLUMNS - 1 - column` for viewer A
  and `column` for viewer B (rows unchanged); `arrowOf` and `viewerRows` pass the direction.
- Test first (`board.test.ts`): for both viewers, anticlockwise puts `h2` at the bottom half's left and `a2` at
  its right (viewer A), the chevrons are today's mirrored (`arrowOf(8, 'A', 'anticlockwise')` is `left`,
  `arrowOf(15, 'A', ...)` `down`, `arrowOf(7, 'A', ...)` `right`, `arrowOf(0, 'A', ...)` `up`; the B side and
  viewer B likewise); every pit appears exactly once; the clockwise cases unchanged.
- Out of scope: the store, components, locales.

## D2 `feat: Mangola direction preference and button` (S, parallel with D1)

- `composables/useMangolaDirection.ts`: `{ direction: Ref<MangolaDirection>, toggle(): void }`, storage injected
  (default `localStorage`, tolerant of a missing or failing storage), key `MANGOLA_DIRECTION_KEY`, default
  `DEFAULT_MANGOLA_DIRECTION`, an unknown stored value falls back to the default. Shape and tests copied from
  `animation/vue/useAnimationSpeed.ts`.
- `components/mangola/MangolaDirectionButton.vue`: props `direction`, `names`, `label`; emits `toggle`; a 44 px
  round button like `AnimationSpeedButton` (a stroke loop icon, the direction's short name), its own polite live
  region that reads the new direction after a tap, a visible focus ring. **Explicit `color: #fff`** (do not
  inherit: see `2026-10-24-mangola-button-colour-findings.md`).
- Tests first: composable (default, toggle, persisted, storage throwing), button (name, emits, live text).
- Out of scope: wiring into the board or the store.

## D3 `feat: choose the Mangola sowing direction on the table` (M, integration)

- Rebase on D1 and D2. `MangolaGameScreen.vue` calls `useMangolaDirection()`; the store's `rows` and `arrowOf`
  use it (the store gets a `direction` ref or setter; keep `store.rows` the single source so the placement editor
  and the board stay mirrored together). `MangolaBoard.vue` footer: the direction button beside the speed button
  (left group), Skip keeps the right, the legend centred; at 375 px nothing overlaps (as the speed spec checks).
- Rules panel sentence visible (from D0).
- `cypress/specs/mangola-game.cy.ts`: pin `clockwise` through storage for the keyboard test (line 154), or change
  the key; no other existing spec depends on columns.
- New `cypress/specs/mangola-direction.cy.ts`: default anticlockwise (the chevron of `h2` points down for the
  host), toggle to clockwise and back, the choice survives a reload, the placement editor is mirrored, a sowing
  from a mirrored board ends on the pits the server says (board frame vs the DOM's pit counts).
- Cypress run (issue #312): **only** `mangola-direction.cy.ts` and `mangola-game.cy.ts`, once each; report the
  time. Dev stack on shifted ports for a manual check, URL given to the owner.
- Docs: base spec "Board" paragraph corrected (today's screen is clockwise; both directions offered), this spec's
  UI section filled with the owner's choice, `docs/harnesses.md` not concerned.

## If the owner picks A or C

- **D2 becomes** `feat: Mangola direction host option (backend)` (M): `SowingDirection` enum in `mangola/domain`,
  host option `direction` (`CHOICE`, values `clockwise|anticlockwise`, default from Q2, label key
  `options.mangola.direction`) in `MangolaFactory.optionDescriptors`, carried by `Mangola` and `MangolaDto.direction`;
  engine: A1 (unchanged, client mirrors) or A2 (follows it: `MangolaGeometry.next(pit, direction)`, `Sowing`
  reads `rules.direction()`, `arrange` order kept clockwise, a JUnit property test of the mirror). Recommended A1
  only if the owner accepts a display-only domain value; else A2 (L instead of M).
  Tests: factory options, DTO, `PerGameConfigTest`/lobby descriptor tests, rematch keeps the option.
- **D3** reads the direction from the DTO instead of the browser (A) or uses the DTO value as the default of the
  player's preference (C); the lobby needs no code (`GameLobby` renders the descriptor), only the locale labels.
- Files at risk added: `MangolaFactory.java`, `Mangola.java`, `MangolaDto.java`, frontend `MangolaState.ts`.

## Other playtest items (separate PRs, not part of this plan)

- **Button text colour** (`2026-10-24-mangola-button-colour-findings.md`): one small PR, `fix: always use the dark
  colour scheme`, independent of D0 to D3 (touches `main.ts`, `App.vue`; D2's button is already explicit).
  Not Mangola domain, but it changes every game's colours: owner's accord before merge.
- **Speed** (`2026-10-24-mangola-speed-findings.md`): nothing before the owner agrees on the "current / proposed"
  table; then one PR on `model/mangola/trace.ts` and `traceSpeed.test.ts` (and `MangolaBoard.vue`'s slide time),
  which conflicts with D3 only in `MangolaBoard.vue` (rebase).
