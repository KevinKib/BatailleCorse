# Mangola: black button text on some laptops (findings)

Written by Claude Opus 5.5 on 2026-10-24 from `origin/main` at `9c3deb02`, after the owner's playtest: "sur certains
portables la couleur du texte des boutons est noire alors qu'elle devrait être blanche". Findings only: **nothing
is fixed in this PR**. Related: `2026-10-24-mangola-direction-design.md`, `2026-10-24-mangola-speed-findings.md`.

## Cause in one paragraph

The app is drawn dark only (green felt, dark panels) but **never sets a text colour on the page**: no `color` on
`html`, `body`, `#app`, `.app-root` (`App.vue`) or the Mangola screen root (`.mangola-screen`). Text that does not
set its own colour inherits the browser default, `CanvasText`, whose value follows the page's `color-scheme`. That
`color-scheme` is written by PrimeVue's theme: `main.ts` configures Aura with `darkModeSelector: 'system'`, and
PrimeVue's styled engine (`@primeuix/styled`) emits `:root { color-scheme: light }` plus, inside
`@media (prefers-color-scheme: dark)`, `color-scheme: dark`. So **on a computer set to a dark theme the inherited
text is white; on a computer set to a light theme it is black.** The Mangola buttons all say `color: inherit`, so
they turn black on light-theme laptops. The owner's own machine is presumably dark, so the screens looked right
there and in every reviewed screenshot.

## Evidence

**Reproduction** on an e2e stack of this branch (Spring `test` profile, own compose project `bc-mgpt`, ports
5191/8191, torn down afterwards), Microsoft Edge driven by Cypress, the colour scheme emulated through the
DevTools protocol (`Emulation.setEmulatedMedia`), host against an Easy bot. Computed `color` (throwaway spec, not
committed):

| Element | `prefers-color-scheme: dark` | `light` | `light` + `forced-colors: active` |
|---------|------------------------------|---------|-----------------------------------|
| `html`, `body` (`color-scheme`) | white (`dark`) | **black** (`light`) | black (`light dark`) |
| Placement title "Place your seeds", "All seeds placed" | white | **black** | black |
| Placement buttons Add, Remove, Two per pit, Confirm layout (`.mg-editor__btn`; Edit layout has the same class, not captured) | white | **black** | black (system colours) |
| Speed button under the board (`AnimationSpeedButton`) | white | **black** | black |
| Board legends "Opponent's side", "Your side" | white | **black** | black |
| Pit buttons (`.mg-pit`): own colour inherited, but their visible parts set theirs (number `--gold-soft`, count `#fff`, chevrons `#fff`) | unaffected | unaffected | system |
| Player tags, phase hint, "Your turn", top bar (explicit `rgba(255,255,255,…)`) | white | white | system |
| Back button (PrimeVue `Button` text, secondary) | zinc `rgb(161,161,170)` | slate `rgb(100,116,139)` | system |
| Lobby "Start game" (PrimeVue, success) | dark green on light green | white on green | system |

Screenshots (scratchpad of the session, not committed): in light, the placement editor's buttons and the speed
button read black on the dark felt; in dark, all white.

**Code [V]:**

- `frontend/src/main.ts`: `theme: { preset: Aura, options: { darkModeSelector: 'system', ... } }`.
- `frontend/index.html`: `<html class="my-app-dark">`: a class meant for a class-based dark selector, which the
  configuration no longer uses (present since the initial scaffold).
- `@primeuix/styled` (`dist/index.mjs`): the global CSS is built with `color-scheme:light` for the light tokens and
  `color-scheme:dark` for the dark ones; with `'system'` the dark part is wrapped in
  `@media (prefers-color-scheme: dark)`.
- `frontend/src/App.vue`: global styles set fonts, spacing, the felt and PrimeVue button radius; **no `color`**.
- `color: inherit` on buttons: `components/mangola/MangolaPlacementEditor.vue` (`.mg-editor__btn`, line 220),
  `components/mangola/MangolaBoard.vue` (`.mg-board__skip`, line 187), `components/mangola/MangolaPit.vue`
  (`.mg-pit`, line 130), `animation/vue/AnimationSpeedButton.vue` (`.speed-button`, line 47), and outside Mangola
  `components/lobby/LobbySeats.vue` (line 198), `components/kobo/KoboPiles.vue` (line 87),
  `components/president/PresidentScores.vue` (line 53).

Candidates ruled out: a button that does not inherit `color` (the UA's `ButtonText` would be black everywhere, not
"on some laptops"; these buttons do inherit); hover, focus or disabled states (the colour is wrong at rest);
a PrimeVue token fallback on Mangola's own buttons (they are plain `<button>`s, not PrimeVue; only Back is
PrimeVue, and its colour flips too, from zinc to slate, which is a second symptom of the same cause). Forced
colours (Windows high contrast) also give black text, but there the system paints the backgrounds light too, so
it reads; it is not the reported bug.

## Every Mangola button and where its colour comes from

| Button | File | Text colour today |
|--------|------|-------------------|
| Pit (board and placement editor) | `MangolaPit.vue` | inherits (invisible: children set theirs) |
| Add / Remove, Checkerboard / Two per pit / Random / Clear, Confirm layout, Edit layout | `MangolaPlacementEditor.vue` | **inherits: black in light** |
| Skip (during a sowing) | `MangolaBoard.vue` | **inherits: black in light** (same parent as the speed button; not captured, the run used the Instant speed) |
| Animation speed | `animation/vue/AnimationSpeedButton.vue` (shared with President) | **inherits: black in light** |
| Back | `MangolaGameScreen.vue`, PrimeVue `Button` severity secondary, text | PrimeVue token, light or dark per system |
| Lobby buttons (Start, Add bot, Copy, Leave, option choices) | `components/lobby/*` | mostly explicit or PrimeVue tokens; the seat row control inherits |
| End screen (Play again) | `EndGameOverlay.vue` | PrimeVue / explicit |
| Rules toggle | `RulesPanel.vue` | explicit |

## Other games

The root cause is app-wide. Every screen that lets text inherit is affected on a light-theme computer:
President's score button (`PresidentScores.vue`) and its speed button (shared `AnimationSpeedButton`), Kobo's
piles (`KoboPiles.vue`, a button whose content is mostly cards), the lobby seat row, and any heading or paragraph
without its own colour. Mangola is the most visible because its placement editor and board footer are plain
buttons and text on the felt. Every PrimeVue component also switches between its light and dark tokens with the
system theme, so the same screen looks slightly different on two laptops even where it stays readable.

## Minimal fix (for a later PR, not this one)

1. `main.ts`: `darkModeSelector: '.my-app-dark'` (the class `index.html` already puts on `<html>`), so PrimeVue
   always uses its dark tokens and always emits `color-scheme: dark`. Every computer then shows what dark-theme
   computers show today: the look that was designed and reviewed.
2. `App.vue`: `:root { color-scheme: dark; }` and `.app-root { color: #fff; }` as a safety net, so inherited text
   never depends on a third party's CSS again.
3. Optional hardening: `color: #fff` (not `inherit`) on `AnimationSpeedButton` and the editor's buttons.

Tests for that PR: a Vitest check that the PrimeVue config uses the class selector; one Cypress check (emulated
`prefers-color-scheme: light`, as in the reproduction) that a placement button and the speed button compute to
white, run on the Mangola and President specs that the PR touches only. Because it changes every game's PrimeVue
colours for light-theme users (to what dark-theme users see), the owner's accord is asked before merging.
