---
name: frontend-spacing
description: Spacing rules and a geometry check for this project's Vue frontend. Use for any frontend change that adds a component, a control, a hint or a text block, or touches layout, spacing, margin, padding, gap, alignment, overlap, crowding, wrapping, mobile height or "fits without scrolling" (lobby panels, picker, option rows, game tables, overlays).
---

# Frontend spacing

Spacing defects keep reaching manual play: every one below passed the unit and Cypress suites. This skill is the
rulebook and the check to run before a PR that changes what sits next to what.

Contents:

1. Spacing scale and tokens
2. Rules
3. Verification recipe (`cypress/support/spacing.ts`)
4. Pre-PR checklist
5. Known pitfalls from this repo

## 1. Spacing scale and tokens

The scale already exists, on a 4/8 px grid, in `frontend/src/App.vue` (`:root`), the single place it is defined:

| Token | Value | Typical use |
|---|---|---|
| `--space-1` | 4px | inside one item: label to its own value, icon to text |
| `--space-2` | 8px | a control and its hint; adjacent controls in one group; the legend of a fieldset |
| `--space-3` | 12px | between independent controls in a dense panel (phone); inner padding of a row |
| `--space-4` | 16px | between distinct settings or sections on a phone; button to its status line |
| `--space-5` | 24px | between sections on roomy screens (lobby at 900 px of height and more) |
| `--space-6` | 32px | panel side padding on desktop |
| `--space-7` | 48px | large separations (decorative fan lift) |
| `--space-8` | 64px | rare, page-level |

Other layout tokens in the same block: `--hud-inset` (10px, gutter of a game table's top row), `--hud-chip-h` (28px),
`--turn-dot-size`. Game screens define their own local size variables (`--kobo-gap`, `--stack-gap`, `--card-w`...).

State of adoption (October 2026): the game screens (Bullshit first, then Kobo, President, Mangola, Morris) use
`var(--space-n)`; the lobby and picker (`GameLobby.vue`, `LobbyGameOptions.vue`, `LobbyPrivateOption.vue`,
`LobbyCustomise.vue`, `GamePickerView.vue`) still write the same values in raw px (`gap: 8px`, `16px`), plus a few
off-scale values (`6px`, `10px`, `14px`, `18px`, `20px`, `28px`, `44px`) tuned to fit short phones. Tailwind and
`tailwindcss-primeui` are loaded but components lay themselves out with scoped CSS, not utility classes.

For new or touched CSS: use `var(--space-n)`; an off-scale value needs a comment that says why (usually "fits 360x640").
Do not refactor untouched components to tokens in an unrelated change.

## 2. Rules

**Parent owns spacing.** The container spaces its children with `display: flex | grid` and `gap`. A child carries no
outer margin, and never on its first or last child (`margin-top` on the first, `margin-bottom` on the last leaks out of
the panel padding). Exception: a `<legend>` is not a flex item, so `gap` does not apply to it; give it
`display: block; margin-bottom: var(--space-2)` (as `LobbyGameOptions.vue` does).

**Proximity shows grouping.** The space inside a group must be clearly smaller than the space between groups, or two
settings read as one:

- a control and its own hint or error: `--space-1` or `--space-2`;
- two distinct settings (switch + hint, then the next option): at least twice the inner gap, so `--space-4` on a phone
  when the inner gap is `--space-2`. Wrap each setting (control + hint) in its own element so the parent gap falls
  between settings, not between a control and its hint;
- sections of a panel: `--space-4` on a phone, `--space-5` on roomy screens.

**Minimum separation.** Two independent controls never touch: at least 8 px (`--space-2`) between their boxes, 12 px
when one is not visually boxed (a bare text button, a link). Nothing overlaps, including absolutely positioned items
(legends over a board, a footer link in a panel padding, HUD chips).

**Associate and group hints.** A hint describing a control is tied to it with `aria-describedby` (unique id) and sits
right under it, in the same wrapper. A set of radios is a `<fieldset>` with a `<legend>`. Between unrelated blocks with
the same text style, add a separator: a divider (`.panel-divider`), a border or background on one of them, or a larger
gap. Never stack two blocks of the same muted small text with the inner gap between them.

**Touch targets.** Every control is at least 44x44 px (`min-height: 44px`, and width for icon buttons). PrimeVue buttons
get it through a scoped override (`.start-button.p-button { min-height: 44px; }`). A 44 px target does not replace the
visual gap: two 44 px buttons still need the separation above.

**Long text and wrapping.** Test with the longest locale string and long names (bot names, player names). Flex children
that hold text need `min-width: 0` to shrink; choose between wrapping (`overflow-wrap: anywhere`) and one line with
`text-overflow: ellipsis` (with a `title` or accessible full text). A wrapped hint must not push a control off screen.

**Safe areas.** `index.html` sets `viewport-fit=cover`, so content near a screen edge adds the inset:
`padding-bottom: calc(var(--space-2) + env(safe-area-inset-bottom, 0px))`, same for top and left on game tables. Full
height screens use `height: 100vh; height: 100dvh;` (fallback first).

**Mobile heights.** The reference sizes are 375x667 (short phone) and 1280x720 (short laptop); also look at 390x844
(tall phone) for anything placed by height, and at 360x640 for the picker. Screens that must fit without scrolling
(picker, game tables, lobby with default options) are checked for `scrollHeight <= clientHeight` and no horizontal
scroll; a lobby panel may scroll inside its screen, the page never does. Tighten gaps by media query on height
(`@media (max-height: 700px)`), never by letting elements collide.

## 3. Verification recipe

`frontend/cypress/support/spacing.ts` measures bounding boxes:

- `expectGap(selectorA, selectorB, minPx)`: at least `minPx` of clear space between the first visible matches
  (half a pixel of slack for fractional layout);
- `expectNoOverlap(selectorA, selectorB)`: the two boxes share no area (1 px tolerance);
- `atSpacingViewports(check)`: runs `check(width, height)` at 375x667 then 1280x720 (`SPACING_VIEWPORTS`);
- `gapBetween(a, b)` and `overlaps(a, b)`: the pure geometry, unit-tested in `spacing.test.ts` (runs with `npm test`).

Use it inside the spec of the feature you are already writing or updating (no spacing-only spec, nominal cases only):

```ts
import { atSpacingViewports, expectGap, expectNoOverlap } from '../support/spacing';

it('keeps the private game hint apart from the next setting', () => {
  // ...open the lobby as host, open Customise...
  atSpacingViewports(() => {
    expectGap('[data-test="private-switch"]', '[data-test="private-hint"]', 2);   // hint stays close to its control
    expectGap('[data-test="private-hint"]', '[data-test="invite-link"]', 16);     // the next setting is clearly apart
    expectNoOverlap('[data-test="customise"]', '[data-test="private-switch"]');
  });
});
```

Pick the pairs from the checklist below: each element you added or moved, against each neighbour. Prefer `data-test`
selectors; add one to the template when a neighbour has none. For a whole zone list (game tables), see the pairwise
loop in `cypress/support/koboGeometry.ts`. Run only the specs of your feature, once (CLAUDE.md, Tests).

Without Cypress (quick look on a running stack, for example from the built-in browser), the same check in the console:

```js
const r = (s) => document.querySelector(s).getBoundingClientRect();
const gap = (a, b) => Math.max(Math.max(a.left, b.left) - Math.min(a.right, b.right),
                               Math.max(a.top, b.top) - Math.min(a.bottom, b.bottom));
gap(r('[data-test="private-hint"]'), r('[data-test="invite-link"]'));
```

## 4. Pre-PR checklist

Run it for every component you added or whose layout you touched:

1. Read the template and list adjacent siblings: for each pair, which rule above sets the space between them (a parent
   `gap`, a wrapper, a divider)? A pair with no answer is a bug.
2. Look for margins on children of a gap container, margins on a first or last child, a `<legend>` in a flex column,
   an always-rendered empty element (it still takes a gap slot) and absolutely positioned items near the edges.
3. Screenshot the screen at 375x667 and 1280x720 (`cy.viewport` + `cy.screenshot`, or the built-in browser), plus 390x844
   when height matters; look at each pair from step 1. Put them in `docs/screenshots/<topic>/` when the PR is visual.
4. Add `expectGap` / `expectNoOverlap` for the new pairs in the feature's spec, at both viewports.
5. Check 44 px targets, the longest locale string, and "fits without scrolling" for the screens that must.

## 5. Known pitfalls from this repo

- **Picker footer link** (`GamePickerView.vue`, fix `c5bfda78`): the Privacy link sat under the panel at 1000x660, then
  too close to the bottom edge of the card. An absolutely placed item needs its own reserved padding and a gap check
  against the panel edge.
- **Private game hint** (`LobbyPrivateOption.vue`): the hint sat 4 px under the switch and the next option followed at
  the same 8 px parent gap, so hint and next option read as one text. Inner gap must be clearly smaller than the gap
  between settings, and same-style muted texts need a separator.
- **Mangola speed selector** (`MangolaBoard.vue`): the speed button touched the "Your side" legend under the board.
  Siblings stacked under a board need a parent gap, and legends positioned around a board are checked with
  `expectNoOverlap`.
- **Fieldset legend** (`LobbyGameOptions.vue`): a legend ignores the flex `gap`; its spacing comes from its own margin.
- **Crowded tables and short phones** (Kobo `4d923609`, President `7aac1e1a`, picker `3106ea10`, Bullshit lobby
  `0c00f4d0`, lobby bot name `43c9b6fe`): content that fits at 1280x800 collides at 375x667 or with more players; check
  both reference viewports and the largest player count.
