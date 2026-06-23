# Bullshit Rules Panel — Design

**Date:** 2026-06-23
**Scope:** Frontend only. Add an in-game rules reference to the Bullshit game
screen, reusing BatailleCorse's `RulesPanel` pattern.

## Problem

Bullshit players have no in-game rules reference. BatailleCorse already has a
self-contained `RulesPanel.vue` (floating toggle chip + slide-out panel,
top-right, `useDisclosure` + Esc-to-close). We want the same affordance on
`BullshitGameScreen.vue` without duplicating the component.

## Decision: generalize `RulesPanel` via a `:rules` prop

`RulesPanel.vue` currently reads `messages.rules` directly from `useI18n()`,
hardcoding BatailleCorse content. We make the rules content an **input** rather
than a hardcoded dependency:

```
<RulesPanel :rules="messages.rules" />      <!-- BatailleCorse GameScreen -->
<RulesPanel :rules="messages.bullshit" />   <!-- BullshitGameScreen        -->
```

- `RulesPanel` gains one required prop: `rules: RulesMessages`.
- Inside, every `messages.rules.X` reference becomes `props.rules.X`.
- The `slap` section kind (BC's card-chip examples, driven by the hardcoded
  language-independent `SLAP_EXAMPLES`) stays fully supported. Bullshit simply
  doesn't use it — its rules are all `text` sections.
- **BC's rendered output is byte-for-byte unchanged** — same data, just passed
  in as a prop instead of read from the global tree. This is the one shared
  file touched; sibling C edits BC's `GameScreen` for disconnect UI, not
  `RulesPanel`, so no collision.

`useDisclosure`, the Esc keydown handler, all CSS (toggle chip + panel at
z-index 1500), and safe-area insets are reused as-is.

### Why a required prop over a defaulted one

A required prop forces both call sites to be explicit and keeps the component's
contract obvious. BC's mount changes from `<RulesPanel />` to
`<RulesPanel :rules="messages.rules" />` — a one-line edit in
`GameScreen.vue`, which is acceptable (it's the alpha BC screen, not the
disconnect-owned region).

## i18n: add a `bullshit` rules namespace

`Messages` currently exposes only `rules: RulesMessages`. We add:

```ts
export interface Messages {
  rules: RulesMessages;     // BatailleCorse
  bullshit: RulesMessages;  // Bullshit (text sections only)
}
```

Populate `messages.bullshit` in `locales/en.ts` with `text` sections covering
the rules from the core-hexagon design spec. No new section kind is needed.

### Rules content (text sections, in order)

1. **Goal** — "Empty your hand before everyone else. The first player to play
   their last card and survive the call on it wins."
2. **The deal** — "The whole deck is dealt out among the players."
3. **Your turn** — "Discard 1 to 4 cards face-down and claim they are the
   current rank. You choose how many cards and which ones — you may bluff."
4. **The claim advances itself** — "The claimed rank is fixed each turn and
   advances automatically: A, 2, 3, … K, then it wraps and keeps cycling. You
   only choose how many cards to discard and which — never the rank."
   *(A note line covers the suit variant generically: "Some variants cycle
   suits instead of ranks — ♥, ♦, ♣, ♠ — but the claim still advances on its
   own.")*
5. **Call Bullshit** — "Any other player can call Bullshit on the most recent
   discard — but never your own. The cards are revealed: if they don't match
   the claim, the player who discarded them takes the whole pile. If they do
   match, the caller takes it."
6. **Winning** — "Play your last card and survive the call window on that final
   discard to win."

Content is variant-aware (mentions the suit variant) but **not** store-coupled —
it's static text, so it never reads game state and won't collide with a sibling
adding suit-variant selection.

## Mount

Add a bare `<RulesPanel :rules="messages.bullshit" />` to the PLAYING branch of
`BullshitGameScreen.vue` (alongside the `.table-frame`). The top-right corner is
uncontested; the panel positions itself absolutely at z-index 1500 — above cards
(1000/1001), below overlays (2000+). The reveal overlay lives in `.pile-well`'s
local stacking context, so no collision. `BullshitGameScreen` imports
`RulesPanel` and calls `useI18n()` to get `messages`.

## Testing

- **Component test** for the panel toggle: mounting `RulesPanel` with a `rules`
  prop renders the toggle; clicking it opens the panel; clicking close hides it.
  (Mirror whatever existing RulesPanel/Bataille test pattern exists, if any.)
- `npm run build` (vite) is the real gate — it runs `vue-tsc`, catching the new
  prop type and the `Messages.bullshit` addition.

## Out of scope / do not touch

- Backend, store, session web-adapters.
- `BullshitStartGame.vue` (create screen — sibling owns suit-variant selection).
- BatailleCorse store/session.
- Any locale other than `en.ts` (no other locale files exist for this tree yet).
