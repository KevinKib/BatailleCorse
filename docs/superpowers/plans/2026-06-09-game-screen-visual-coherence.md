# Game Screen Visual Coherence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the game screen visually coherent and on-brand by giving its accents three distinct semantic roles (gold = attention, green = go/gain, red = loss), demoting the Back button, turning the pile placeholder into a felt well, and restyling the card counters as poker-chip tokens.

**Architecture:** Pure CSS/markup styling pass. Introduce three global semantic colour tokens (RGB channel triplets so alpha is still controllable) in `App.vue`, then point the game screen's cue styles at them and recolour the turn cues from green to gold. Three further self-contained restyles: Back button, pile slot, card counter. No layout, behaviour, or feature changes — selectors and `data-cy` hooks are preserved so existing tests are unaffected.

**Tech Stack:** Vue 3 `<script setup>`, scoped CSS, PrimeVue Button, Vite.

---

## Prerequisites (read before starting)

- The worktree may have no `node_modules`. Install once: `cd frontend && npm ci`.
- The hard gate after every change is `cd frontend && npm run build` (Vite). There is no separate type-check script. CSS/colour changes can't be meaningfully unit-tested; the build gate plus the existing Cypress suite (which keys off `data-cy` and text, not colour) is the regression net.
- This is a visual pass — a manual look at the running app is the real acceptance check (Task 6).
- Files touched:
  - Modify: `frontend/src/App.vue` (global tokens)
  - Modify: `frontend/src/view/alpha/GameScreen.vue` (cues, Back button, pile slot)
  - Modify: `frontend/src/components/CardCounter.vue` (chip restyle)

### Token model (read once — used across tasks)

Three global semantic tokens, defined as RGB channel triplets so they can be used both as solid colours (`rgb(var(--x))`) and with alpha (`rgba(var(--x), 0.5)`):

- `--accent-active-rgb: 245, 200, 66` — brand gold. "Attention / your move": turn glow, YOUR TURN hint, Send halo, live-slap pile flash.
- `--accent-positive-rgb: 74, 222, 128` — green. "Go / gain": positive card delta. (The Send button keeps its PrimeVue `success` green base; only its *halo* turns gold.)
- `--accent-negative-rgb: 248, 113, 113` — red. "Loss": negative card delta, defeat.

These replace the hard-coded `rgba(74, 222, 128, …)` green currently used for the turn cues.

---

## Task 1: Add global semantic colour tokens

**Files:**
- Modify: `frontend/src/App.vue` (the global `<style>` block)

- [ ] **Step 1: Add the tokens at `:root`**

In `App.vue`, inside the `<style>` block, immediately after the three `@import` lines (before `.gabarito-font`), add:

```css
:root {
  /* Semantic accent roles (RGB channel triplets so alpha stays controllable).
     active = attention / "your move" (brand gold); positive = go / gain;
     negative = loss. Single source for the game-screen cue colours. */
  --accent-active-rgb: 245, 200, 66;
  --accent-positive-rgb: 74, 222, 128;
  --accent-negative-rgb: 248, 113, 113;
}
```

- [ ] **Step 2: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/App.vue
git commit -m "feat: add global semantic accent colour tokens"
```

---

## Task 2: Recolour the turn/attention cues to gold (#1)

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (the `<style scoped>` block)

These cues currently use green (`rgba(74, 222, 128, …)`). Point them at `--accent-active-rgb` (gold). The keyframes resolve the variable per animated element; every element here inherits the token from `:root`.

- [ ] **Step 1: Recolour the active name-tag glow**

Replace:

```css
.player_tag--active {
  color: #ffffff;
  border-color: rgba(74, 222, 128, 0.9);
  box-shadow: 0 0 16px 3px rgba(74, 222, 128, 0.55);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

@keyframes turn-glow-pulse {
  0%, 100% { box-shadow: 0 0 12px 2px rgba(74, 222, 128, 0.40); }
  50%      { box-shadow: 0 0 22px 6px rgba(74, 222, 128, 0.70); }
}
```

with:

```css
.player_tag--active {
  color: #ffffff;
  border-color: rgba(var(--accent-active-rgb), 0.9);
  box-shadow: 0 0 16px 3px rgba(var(--accent-active-rgb), 0.55);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

@keyframes turn-glow-pulse {
  0%, 100% { box-shadow: 0 0 12px 2px rgba(var(--accent-active-rgb), 0.40); }
  50%      { box-shadow: 0 0 22px 6px rgba(var(--accent-active-rgb), 0.70); }
}
```

- [ ] **Step 2: Recolour the YOUR TURN hint text and dot**

Replace (the `color` line inside `.turn-hint`):

```css
  letter-spacing: 0.14em;
  color: #4ade80;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.7);
}

.turn-hint__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #4ade80;
  box-shadow: 0 0 8px 2px rgba(74, 222, 128, 0.8);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}
```

with:

```css
  letter-spacing: 0.14em;
  color: rgb(var(--accent-active-rgb));
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.7);
}

.turn-hint__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: rgb(var(--accent-active-rgb));
  box-shadow: 0 0 8px 2px rgba(var(--accent-active-rgb), 0.8);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}
```

- [ ] **Step 3: Recolour the Send button halo**

Replace:

```css
@keyframes send-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(74, 222, 128, 0); }
  50%      { box-shadow: 0 0 16px 3px rgba(74, 222, 128, 0.65); }
}
```

with:

```css
@keyframes send-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(var(--accent-active-rgb), 0); }
  50%      { box-shadow: 0 0 16px 3px rgba(var(--accent-active-rgb), 0.65); }
}
```

- [ ] **Step 4: Align the pile-flash glow to the active accent**

Replace:

```css
@keyframes pile-flash {
  0%   { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
  35%  { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5), 0 0 32px 10px rgba(255, 210, 40, 0.45); }
  100% { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
}
```

with:

```css
@keyframes pile-flash {
  0%   { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
  35%  { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5), 0 0 32px 10px rgba(var(--accent-active-rgb), 0.45); }
  100% { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
}
```

(Note: Task 4 rewrites the inset part of these shadows; the glow colour set here is preserved there.)

- [ ] **Step 5: Token-ise the card-delta colours (keep green/red meaning)**

The card-delta indicator renders outside `.gamescreen`, so it inherits the tokens from `:root` directly. Replace:

```css
.card-delta {
  position: fixed;
  pointer-events: none;
  z-index: 1001;
  font-size: 1.6rem;
  font-weight: 800;
  color: #4ade80;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.85);
  animation: card-delta-float 1.4s ease-out forwards;
}

.card-delta--negative {
  color: #f87171;
}
```

with:

```css
.card-delta {
  position: fixed;
  pointer-events: none;
  z-index: 1001;
  font-size: 1.6rem;
  font-weight: 800;
  color: rgb(var(--accent-positive-rgb));
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.85);
  animation: card-delta-float 1.4s ease-out forwards;
}

.card-delta--negative {
  color: rgb(var(--accent-negative-rgb));
}
```

- [ ] **Step 6: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: recolour turn/attention cues to brand gold via accent tokens"
```

---

## Task 3: Demote the Back button (#2)

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (template, around line 47)

- [ ] **Step 1: Change the Back button from danger-red to a quiet ghost secondary**

Replace:

```html
        <RouterLink to="/" class="back_button">
          <Button severity="danger" label="Back" icon="pi pi-undo" variant="" rounded />
        </RouterLink>
```

with:

```html
        <RouterLink to="/" class="back_button">
          <Button severity="secondary" label="Back" icon="pi pi-undo" variant="text" rounded />
        </RouterLink>
```

- [ ] **Step 2: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "fix: demote Back button from danger red to ghost secondary"
```

---

## Task 4: Turn the pile slot into a recessed felt well (#3)

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (the `<style scoped>` block)

- [ ] **Step 1: Replace the dashed-placeholder slot with a felt well**

Replace:

```css
.pile_slot {
  display: flex;
  align-items: center;
  justify-content: center;
  /* Size around the fluid pile card instead of fixed px so it never overflows. */
  padding: clamp(10px, 2.5vmin, 20px) clamp(14px, 3.5vmin, 28px);
  margin: auto;
  border: 2px dashed rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.22);
  box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5);
}
```

with:

```css
.pile_slot {
  display: flex;
  align-items: center;
  justify-content: center;
  /* Size around the fluid pile card instead of fixed px so it never overflows. */
  padding: clamp(10px, 2.5vmin, 20px) clamp(14px, 3.5vmin, 28px);
  margin: auto;
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  /* Recessed felt well: inner gradient slightly darker than the felt plus a
     deep inset shadow, so it reads as a carved card spot, not a placeholder. */
  background: radial-gradient(ellipse at 50% 45%, rgba(0, 0, 0, 0.28) 0%, rgba(0, 0, 0, 0.5) 100%);
  box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35);
}
```

- [ ] **Step 2: Match the pile-flash resting shadow to the new well**

So the slap flash returns cleanly to the new resting state, replace:

```css
@keyframes pile-flash {
  0%   { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
  35%  { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5), 0 0 32px 10px rgba(var(--accent-active-rgb), 0.45); }
  100% { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
}
```

with:

```css
@keyframes pile-flash {
  0%   { box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35); }
  35%  { box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35), 0 0 32px 10px rgba(var(--accent-active-rgb), 0.45); }
  100% { box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35); }
}
```

- [ ] **Step 3: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: render pile slot as a recessed felt well instead of dashed placeholder"
```

---

## Task 5: Restyle the card counters as poker-chip tokens (#4)

**Files:**
- Modify: `frontend/src/components/CardCounter.vue` (the `<style>` block)

- [ ] **Step 1: Replace the white/black debug disc with a gold-rimmed felt chip**

Replace:

```css
.counter {
  /* width: auto; */
  height: 32px;
  width: 32px;
  clip-path: circle(16px);
  background-color: white;
  color: black;
  border: 2px solid black;
  border-radius:50%;
}
```

with:

```css
.counter {
  height: 32px;
  width: 32px;
  /* Poker-chip token: dark felt disc, thin gold rim, light text. The
     clip-path is dropped so the drop shadow can render (clip-path would clip
     it); border-radius:50% keeps the circle. */
  background-color: rgba(0, 0, 0, 0.6);
  color: rgba(255, 255, 255, 0.92);
  border: 2px solid rgba(var(--accent-active-rgb), 0.55);
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.5);
}
```

(The `.count` rule and the component template are unchanged, so the count text, its centering, and the `data-cy` hooks on the wrapping elements are preserved.)

- [ ] **Step 2: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/CardCounter.vue
git commit -m "feat: restyle card counters as gold-rimmed poker-chip tokens"
```

---

## Task 6: Verification (build + regression + manual review)

**Files:** none (verification only)

- [ ] **Step 1: Final build gate**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 2: Existing Cypress suite (no regression)**

In the live e2e environment (backend + dev server up, per the README "End-to-end tests" section):
Run: `cd frontend && npm run cy:run`
Expected: all specs pass. They key off `data-cy` and text, which are unchanged, so colour/markup restyles must not break them. (If the e2e stack is unavailable, note this as deferred — the build gate plus manual review below still apply.)

- [ ] **Step 3: Manual visual review**

Start the app (`sh dev.sh`), open a game, and confirm:
- The active player's name-tag glow, the YOUR TURN hint/dot, and the Send button halo are **gold** and clearly readable against the green felt.
- The Send button base is still green; positive deltas (`+N`) are green; penalties (`−N`) and defeat are red.
- The **Back button** is quiet/ghost and no longer dominates the board.
- The **empty pile** reads as a deliberate recessed well, not a dashed box; a slap still flashes a gold rim that settles cleanly.
- The **card counters** read as gold-rimmed chips with legible numbers.
- Toggle OS "reduce motion" and confirm the glows/pulses stop (the existing `prefers-reduced-motion` rule still applies).

---

## Self-Review notes

- **Spec coverage:** tokens (Task 1) → spec "Tokens"; #1 gold cues incl. Send-halo-gold/Send-base-green and green/red deltas (Task 2) → spec #1; #2 Back button (Task 3); #3 pile well + flash baseline (Task 4); #4 counter chip (Task 5); build + Cypress-no-regression + manual + reduced-motion (Task 6) → spec "Testing & verification". All spec sections map to tasks.
- **Deviation from spec, intentional:** the spec sketched a `--brand-gold` value plus a per-screen `.gamescreen` semantic mapping. The plan collapses this to three global semantic channel-triplet tokens at `:root`. Same single-source intent, less indirection, and it also covers the card-delta indicator which renders *outside* `.gamescreen` (so a `.gamescreen`-scoped mapping wouldn't have reached it).
- **Open decisions** from the spec are resolved to the recommended options: Send = green base + gold halo; counters = gold-rimmed dark disc; Back = ghost/text secondary.
- **Token-name consistency:** `--accent-active-rgb`, `--accent-positive-rgb`, `--accent-negative-rgb` are defined once in Task 1 and referenced unchanged in Tasks 2, 4, and 5.
- **No placeholders; every step shows the exact before/after CSS or markup.**
