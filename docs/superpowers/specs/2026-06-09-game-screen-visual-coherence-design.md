# Game Screen Visual Coherence — Design

**Date:** 2026-06-09
**Status:** Draft for review
**Scope:** Purely visual/styling pass over the game screen. No layout, behaviour, or feature changes.
**Files in scope:** `frontend/src/view/alpha/GameScreen.vue`, `frontend/src/components/CardCounter.vue`, and a small set of global palette tokens in `frontend/src/App.vue`.

## Problem

The game screen is structurally coherent (mirrored you-vs-opponent layout, a consistent dark-pill component language, and a unified green motion system), but several elements read as functional placeholders rather than parts of one designed surface, and the screen does not share a visual identity with the much more polished, gold-forward title screen. Four specific issues:

1. **Green is overloaded.** The felt is green, "success/Send" is green, the turn cue is green (`#4ade80`), and positive deltas are green. Cues built from the background hue have low contrast against the felt. Meanwhile the brand/identity colour — the gold/amber (`#f5c842`) from the title screen, rules panel, and victory overlay — barely appears during play, so the game and title screens feel like different products.
2. **The Back button is red** (`severity="danger"`). Red signals destructive/stop, but "Back to home" is navigation. It is the highest-saturation element on the board and pulls attention to a corner, out-shouting the actual game actions.
3. **The pile slot reads as a wireframe.** Its dashed border is the universal visual for an empty drop-zone/placeholder, which undercuts the literal focal point of the game on a "premium felt" table.
4. **The card counters look like debug badges.** Stark white discs with a black border and black text — high-contrast and legible, but they speak neither felt nor gold nor the pill language, and they sit directly on the decks (the heroes).

## Design principles for the pass

- **Three semantic accent roles, one colour each:**
  - **Active / attention → gold** (`#f5c842`, the brand colour). "It is your move", a live slap, anything asking for attention on the felt.
  - **Positive / go → green** (`#4ade80`). The Send action and positive card-count gains.
  - **Negative / loss → red** (`#f87171`). Penalties, defeat.
- **Gold against green felt has far more contrast than green-on-green**, so moving the *turn cue* to gold fixes both the contrast problem (#1) and the title↔game identity gap at once, while green keeps its intuitive "go/gain" meaning.
- Centralise these as tokens (single source of truth) rather than scattering hex values.

## Design

### Tokens (centralisation)

Add a small global palette in `App.vue`'s (non-scoped) `<style>`:

```css
:root {
  --brand-gold: #f5c842;
  --positive-green: #4ade80;
  --negative-red: #f87171;
}
```

On `.gamescreen`, map semantic roles to those (so the game screen reads in roles, not hexes):

```css
.gamescreen {
  --accent-active: var(--brand-gold);
  --accent-positive: var(--positive-green);
  --accent-negative: var(--negative-red);
}
```

The title screen already hard-codes `#f5c842` in several places; unifying it onto `--brand-gold` is a sensible **follow-up**, but is out of scope here to keep the pass contained.

### #1 — Recolour the turn/attention cues to gold

Currently the active-player cue is green and the Send button pulses green in sync. Move the **attention** cues to gold, keep the **action** semantics green:

- **Name-tag active glow** (`.player_tag--active` + `turn-glow-pulse` keyframes): green → `--accent-active` (gold). Same 1.8s cadence.
- **"YOUR TURN" hint** text + dot (`.turn-hint`, `.turn-hint__dot`): green → gold.
- **Send button pulse ring** (`.action_button--my-turn` / `send-pulse`): green → gold. This makes a green (go) button wear a gold (your-move) halo — legible and on-brand.
- **Send button base colour stays green** (`severity="success"`). It is a positive/go action; green is the correct semantic. Only its *attention halo* is gold.
- **Pile flash on slap** (`pile-flash`) already flashes warm gold (`rgba(255,210,40,…)`) — keep it, and align its hue to `--accent-active` for consistency.
- **Card-delta indicators** keep their meaning: `+N` stays `--accent-positive` (green), `−N` stays `--accent-negative` (red).

Net effect: during play, gold marks "attention/your move", green marks "go/gain", red marks "loss". No two roles share a hue, and none of them is the felt's hue.

### #2 — Demote the Back button

Change the Back button from `severity="danger"` (red) to a quiet, low-emphasis control consistent with the dark-pill language:

- Use `severity="secondary"` with the `text` variant (ghost), keeping the `pi pi-undo` icon and "Back" label.
- It should sit visually below the game actions in the hierarchy — present but not attention-grabbing. Red is reserved for genuinely negative states (penalty deltas, defeat).

### #3 — Turn the pile slot into a recessed well

Replace the dashed-placeholder treatment with a felt depression that looks intentional:

- Drop the `2px dashed` border. Use either no border or a hairline `1px solid rgba(255,255,255,0.05)`.
- Deepen the inset: a darker inner radial gradient (slightly darker than the surrounding felt) plus a stronger `inset` box-shadow so it reads as a carved-out card spot.
- Keep the rounded corners and the existing `margin: auto` centering and fluid sizing from the responsive pass untouched.
- When a slap is live, the existing `pile-flash` gives the well a brief gold rim — that now doubles as the well's "hot" state and ties to `--accent-active`.

This is styling-only; the slot keeps the empty-state height floor (`--pile-card-h`) and dimensions from the prior responsive work.

### #4 — Restyle the card counters as poker-chip tokens

Restyle `CardCounter.vue` (used only on the game screen) from the white/black debug disc to a chip that belongs on the table:

- Dark felt-toned disc — e.g. `background: rgba(0,0,0,0.6)` — with a thin gold rim (`border: 2px solid rgba(245,200,66,0.55)`) and off-white bold text.
- Keep the 32px size and circular shape so existing positioning (bottom-right of each deck) is unchanged.
- Subtle drop shadow for a token-on-felt feel.
- Keep the `data-cy` hooks and the count text untouched so the Cypress specs and counts are unaffected.

### Out of scope (noted, not done here)

- Radius-scale unification (999px pills / 14px well / 16px overlays / 6% cards) and heading-font consistency (name tags use the body font; the hint uses Gabarito). Low-impact polish; defer.
- Re-pointing the title screen's hard-coded gold onto `--brand-gold`.
- Any layout, copy, or behaviour change.

## Testing & verification

- **Build gate:** `npm run build` must pass (project convention; no separate type-check).
- **No-regression:** the existing Cypress specs key off `data-cy` and text, not colour, so they must still pass — selectors, counts, and `data-cy` hooks are deliberately left unchanged. Run the suite in the live e2e environment.
- **Manual visual review:** open a game and confirm — the active-player cue and Send halo are gold and clearly readable against the felt; the Back button no longer dominates; the empty pile looks like a deliberate well, not a dashed box; counters read as chips. Verify `prefers-reduced-motion` still suppresses the pulses.
- **Animation safety:** colour-only changes; no geometry, so the card-flight animations are unaffected.

## Open decisions for review

1. **Send halo gold, button green** (recommended) vs. making the whole Send treatment gold. The recommendation keeps green = "go".
2. **Counter chip style:** gold-rimmed dark disc (recommended) vs. a flat felt-dark disc with no rim. Rim ties to brand but is busier.
3. **Back button:** ghost/text secondary (recommended) vs. a solid secondary pill.
