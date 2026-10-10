# Responsive Game Screen — Design

**Date:** 2026-06-08
**Status:** Approved for planning
**Scope:** `frontend/src/view/alpha/GameScreen.vue` and the viewport setup it depends on.

## Problem

The game screen is built for a single desktop size and breaks on anything smaller.
On phones it is unusable. Five concrete causes:

1. **No viewport meta tag.** `frontend/index.html` is missing
   `<meta name="viewport" ...>`, so mobile browsers render against a ~980px
   virtual viewport and shrink everything. This is the single biggest cause of
   the broken-on-phone behaviour.
2. **Fixed-pixel sizing.** Cards are hard-coded (`:size="90"`, `:size="125"`)
   and the pile slot has fixed `min-width: 181px` / `min-height: 222px`. Nothing
   scales down, so on a narrow/short screen the board overflows.
3. **Rigid percentage bands.** Vertical `30% / 40% / 30%` rows and horizontal
   `30% / 40% / 30%` columns. The bands compress on small screens but the
   fixed-px cards inside them do not, so content spills out. The center column
   cannot hold the side-by-side Send + Slap buttons when narrow.
4. **Zero breakpoints.** The only `@media` rule in the component is
   `prefers-reduced-motion`. No mobile/tablet handling at all.
5. **Absolutely-positioned chrome.** The rules toggle (top-right) and Back
   button (bottom-left) can overlap the cards once the table gets narrow, and
   ignore phone notches / safe areas.

## Decisions

- **Target:** phone-portrait and down-scaling for all smaller screens. **No
  landscape layout** (would need a genuinely different arrangement) and no
  4-player rework.
- **Overflow behaviour:** the board always **shrinks to fit** — no scrolling.
  In a reaction game the player must see both hands and the pile at once.
- **Approach:** **fluid-scale the existing structure** (chosen over a CSS Grid
  rebuild or container-query components). Everything sizes off one source — the
  viewport — via `clamp()` / viewport units, so the board stays coherent.
  Rejected alternatives:
  - *CSS Grid rebuild:* larger rewrite of working markup for no benefit this
    screen needs yet.
  - *Container queries:* would force converting `PlayingCard`'s JS prop-driven
    sizing to CSS (rippling to shared consumers like `TitleCardFan`), and
    container queries size each element off *its own* container — the opposite
    of the coherent, single-source scaling a balanced board needs.

### Animation safety (verified)

`useCardAnimation.ts` reads all geometry at runtime via
`getBoundingClientRect()`, and ghost element sizes come from the measured
source/destination rects. It never reads the `size` prop. Therefore *how* cards
are sized does not affect the animations — they measure whatever is on screen.
No approach endangers them; the fluid CSS changes are safe.

## Design

### 1. Viewport foundation

- Add to `frontend/index.html`:
  `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">`
- Switch the full-height chain on `.gamescreen` from `height: 100%` to
  `100dvh` so the board tracks the real visible area as mobile browser chrome
  shows/hides. Keep `100vh` as a fallback for browsers without `dvh`.

### 2. One fluid sizing source

Define CSS custom properties on `.gamescreen` that everything derives from:

```css
--deck-card-w: clamp(56px, 14vmin, 90px);
--pile-card-w: clamp(78px, 19vmin, 125px);
```

(Clamp floors/ceilings are starting values to be tuned during implementation;
the 90:125 ratio between deck and pile cards is preserved.)

- **Cards:** override the px `width`/`height` attributes emitted from
  `PlayingCard`'s `size` prop using `:deep(.playing_card)` in the game screen's
  scoped styles — `width: var(--…)` plus `aspect-ratio` for height. CSS wins
  over HTML attributes, so no `!important` is needed. The `size` prop is left in
  place as the intrinsic/default fallback and aspect-ratio source. **`PlayingCard`
  itself is not modified**, so there is no ripple to other consumers.
- **Pile slot:** replace the fixed `min-width: 181px` / `min-height: 222px` and
  `padding: 20px 28px` with values derived from `--pile-card-w` plus fluid
  `clamp()` padding.

### 3. Layout: drop the rigid bands

- **Vertical:** change the `30% / 40% / 30%` rows so the **pile row flexes
  (`flex: 1`)** and the top/bottom bands size to their content with fluid
  `clamp()` padding, instead of three hard percentages that compress the cards.
- **Horizontal:** replace the fixed `30% / 40% / 30%` columns. The center column
  sizes to its content and the side columns absorb the remaining space, so the
  **Send + Slap buttons can never be squeezed into wrapping or clipping**.
- Gaps and the `player_tag` / card-counter spacing become fluid `clamp()`
  values.

### 4. Controls + chrome on narrow widths

- Add one **compact breakpoint** (`max-width: ~480px`) that tightens button
  padding/font and gaps so Send + Slap stay comfortably side-by-side and
  thumb-friendly.
- Verify the rules panel (`width: min(360px, calc(100vw - 32px))`,
  `max-height: 70vh`) behaves correctly under `dvh`.
- Add `env(safe-area-inset-*)` padding so the Back button and rules toggle clear
  notches and rounded corners on phones.

### 5. Testing & verification

- **Cypress e2e:** add a small-viewport spec (e.g. `cy.viewport(390, 844)`)
  asserting the board fits — both decks, the pile, and the Send/Slap buttons are
  visible with no overflow, and the buttons are clickable.
- **Manual:** run the app and resize through desktop → tablet → phone-portrait,
  confirming the board always fits and animations still land on the cards.
- **Build gate:** `vite build` must pass (project convention), plus the Cypress
  run.

## Scope guardrails (YAGNI)

No landscape layout, no 4-player rework, no `PlayingCard` contract change, no
CSS-framework swap. The change is pure CSS in `GameScreen.vue`, one HTML meta
line in `index.html`, and one Cypress spec.
