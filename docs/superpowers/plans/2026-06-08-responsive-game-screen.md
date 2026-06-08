# Responsive Game Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the game screen fluid and viewport-aware so it always fits on screen with no scrolling, down to phone-portrait size.

**Architecture:** Approach A — fluid-scale the existing top/middle/bottom vertical structure. Everything sizes off one source (the viewport) via `clamp()` / viewport units and CSS custom properties; the rigid `30/40/30` percentage bands become flex. `PlayingCard`'s JS `size` prop is left intact and overridden by CSS (CSS width/aspect-ratio beat HTML attributes), so no shared component contract changes. The card-flight animations are unaffected because `useCardAnimation.ts` measures live geometry via `getBoundingClientRect()` and never reads the `size` prop.

**Tech Stack:** Vue 3 `<script setup>`, scoped CSS, PrimeVue buttons, Cypress e2e, Vite.

---

## Prerequisites (read before starting)

- The worktree has no `node_modules`. Install once: `cd frontend && npm ci`.
- The real build gate is `npm run build` (Vite). There is no separate type-check script.
- The Cypress e2e specs drive the real app and require the dev server + backend running (see project README "Testing" section). `cy.createGame()` reaches an active solo game board (the existing `turn-indicator.cy.ts` relies on this).
- All edits are in three files:
  - Modify: `frontend/index.html`
  - Modify: `frontend/src/view/alpha/GameScreen.vue`
  - Modify: `frontend/src/components/RulesPanel.vue`
  - Create: `frontend/cypress/specs/responsive-game-screen.cy.ts`

---

## Task 1: Failing responsive regression test

**Files:**
- Create: `frontend/cypress/specs/responsive-game-screen.cy.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/cypress/specs/responsive-game-screen.cy.ts`:

```ts
describe('Responsive game screen (phone portrait)', () => {
  const VIEWPORT_W = 390;
  const VIEWPORT_H = 844;

  beforeEach(() => {
    cy.viewport(VIEWPORT_W, VIEWPORT_H);
    cy.createGame();
    // Wait for the active board (the one-time YOUR TURN hint only renders there).
    cy.get('[data-cy="turn-hint"]', { timeout: 10000 }).should('exist');
  });

  it('fits the whole board on screen with no scrolling', () => {
    cy.document().then((doc) => {
      const el = doc.scrollingElement as Element;
      // +1 tolerance for sub-pixel rounding.
      expect(el.scrollWidth, 'no horizontal overflow').to.be.at.most(el.clientWidth + 1);
      expect(el.scrollHeight, 'no vertical overflow').to.be.at.most(el.clientHeight + 1);
    });
  });

  it('keeps both decks and the pile visible', () => {
    cy.get('[data-cy="opponent-card-count"]').should('be.visible');
    cy.get('[data-cy="pile-card-count"]').should('be.visible');
    cy.get('[data-cy="player-card-count"]').should('be.visible');
  });

  it('keeps Send and Slap fully on-screen and clickable', () => {
    cy.contains('button', 'Send').should('be.visible');
    cy.contains('button', 'Slap').should('be.visible');
    cy.contains('button', 'Slap').then(($b) => {
      const r = $b[0].getBoundingClientRect();
      expect(r.right, 'Slap right edge within viewport').to.be.at.most(VIEWPORT_W + 1);
      expect(r.bottom, 'Slap bottom edge within viewport').to.be.at.most(VIEWPORT_H + 1);
      expect(r.left, 'Slap left edge within viewport').to.be.at.least(-1);
    });
  });
});
```

- [ ] **Step 2: Stage the new file**

```bash
git -C frontend add cypress/specs/responsive-game-screen.cy.ts
```

- [ ] **Step 3: Run the test to verify it fails**

Run (with dev server + backend up):
`cd frontend && npm run cy:run -- --spec cypress/specs/responsive-game-screen.cy.ts`

Expected: FAIL. With no viewport meta tag and fixed-px layout, at least the
overflow and/or Slap-edge assertions fail (board does not fit 390×844).

- [ ] **Step 4: Commit the failing test**

```bash
git -C frontend commit -m "test: add failing responsive game-screen spec"
```

---

## Task 2: Add the viewport meta tag

**Files:**
- Modify: `frontend/index.html`

- [ ] **Step 1: Add the meta tag**

In `frontend/index.html`, inside `<head>`, immediately after the
`<meta charset="UTF-8" />` line, add:

```html
    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover" />
```

- [ ] **Step 2: Verify the build still succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 3: Commit**

```bash
git -C frontend add index.html
git commit -m "fix: add viewport meta tag for mobile rendering"
```

---

## Task 3: Fluid sizing source + dvh height + fluid cards/pile

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (the `<style scoped>` block)

- [ ] **Step 1: Make the root full-height fluid and define sizing tokens**

In `GameScreen.vue`, replace the `.gamescreen` rule:

```css
.gamescreen {
  /* Two-layer background: vignette on top, deep felt below */
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, #1e5c30 0%, #0d2e18 48%, #07160d 100%);
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}
```

with:

```css
.gamescreen {
  /* Two-layer background: vignette on top, deep felt below */
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, #1e5c30 0%, #0d2e18 48%, #07160d 100%);
  /* One fluid sizing source: every card/pile/gap derives from these so the
     whole board scales coherently off the viewport. Clamp bounds are tuned
     here; deck:pile width ratio mirrors the original 90:125. */
  --deck-card-w: clamp(48px, 14vmin, 90px);
  --pile-card-w: clamp(70px, 19vmin, 125px);
  --card-aspect: 167.575 / 243.1375; /* matches PlayingCard intrinsic ratio */
  --band-pad: clamp(8px, 2.5vh, 20px);
  --stack-gap: clamp(6px, 1.5vh, 10px);
  width: 100%;
  height: 100vh;     /* fallback for browsers without dvh */
  height: 100dvh;    /* tracks the real visible area as mobile chrome shows/hides */
  display: flex;
  flex-direction: column;
  position: relative;
}
```

- [ ] **Step 2: Drive card dimensions from the tokens via CSS**

In `GameScreen.vue`, immediately after the `.gamescreen { ... }` rule, add:

```css
/* CSS width/aspect-ratio override PlayingCard's px width/height attributes
   (CSS beats HTML attributes — no !important needed). PlayingCard is untouched,
   so shared consumers like TitleCardFan are unaffected. */
.gamescreen :deep(.playing_card) {
  height: auto;
  aspect-ratio: var(--card-aspect);
}

.gamescreen_top :deep(.playing_card),
.gamescreen_bottom :deep(.playing_card) {
  width: var(--deck-card-w);
}

.gamescreen_middle :deep(.playing_card) {
  width: var(--pile-card-w);
}
```

- [ ] **Step 3: Make the pile slot fluid**

Replace the `.pile_slot .card` and `.pile_slot` rules:

```css
.pile_slot .card {
  min-width: 125px;
  min-height: 182px;
}

.pile_slot {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 181px;  /* 125px card + 28px padding × 2 */
  min-height: 222px; /* 182px card + 20px padding × 2 */
  padding: 20px 28px;
  margin: auto;
  border: 2px dashed rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.22);
  box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5);
}
```

with:

```css
.pile_slot .card {
  min-width: var(--pile-card-w);
}

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

- [ ] **Step 4: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 5: Commit**

```bash
git -C frontend add src/view/alpha/GameScreen.vue
git commit -m "feat: fluid card/pile sizing and dvh height on game screen"
```

---

## Task 4: Replace the rigid percentage bands with flex

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (the `<style scoped>` block)

- [ ] **Step 1: Make the vertical bands flex instead of fixed percentages**

Replace the `.gamescreen_top`, `.gamescreen_middle`, and `.gamescreen_bottom`
rules:

```css
.gamescreen_top {
  height: 30%;
  background: rgba(0, 0, 0, 0.10);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  padding-bottom: 20px;

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
    margin: 0;
  }
}

.gamescreen_middle {
  height: 40%;
}

.gamescreen_bottom {
  height: 30%;
  background: rgba(0, 0, 0, 0.10);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 20px;

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    margin: 0;
  }
}
```

with:

```css
.gamescreen_top {
  /* Size to content; the pile row absorbs the slack. min-height:0 lets it
     shrink on short screens so the board still fits without scrolling. */
  flex: 0 0 auto;
  min-height: 0;
  background: rgba(0, 0, 0, 0.10);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  padding-bottom: var(--band-pad);

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;
    gap: var(--stack-gap);
    margin: 0;
  }
}

.gamescreen_middle {
  flex: 1 1 auto;
  min-height: 0;
}

.gamescreen_bottom {
  flex: 0 0 auto;
  min-height: 0;
  background: rgba(0, 0, 0, 0.10);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: var(--band-pad);

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
    gap: var(--stack-gap);
    margin: 0;
  }
}
```

- [ ] **Step 2: Make the horizontal columns flex so the center never gets squeezed**

Replace the `.left_side`, `.middle_side`, and `.right_side` rules:

```css
.left_side {
  width: 30%;
  display: flex;
}

.middle_side {
  width: 40%;
}

.right_side {
  width: 30%;
}
```

with:

```css
.left_side {
  /* Side columns flex and may collapse; the center sizes to its content so the
     Send + Slap buttons can never be squeezed into wrapping or clipping. */
  flex: 1 1 0;
  min-width: 0;
  display: flex;
}

.middle_side {
  flex: 0 0 auto;
}

.right_side {
  flex: 1 1 0;
  min-width: 0;
}
```

- [ ] **Step 3: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 4: Commit**

```bash
git -C frontend add src/view/alpha/GameScreen.vue
git commit -m "refactor: flex layout bands on game screen, drop fixed percentages"
```

---

## Task 5: Compact breakpoint + safe-area insets for chrome

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue` (the `<style scoped>` block)
- Modify: `frontend/src/components/RulesPanel.vue` (the `<style scoped>` block)

- [ ] **Step 1: Add the compact breakpoint and safe-area handling in GameScreen**

In `GameScreen.vue`, at the end of the `<style scoped>` block (just before the
closing `</style>`), add:

```css
/* --- Narrow-screen (phone) adjustments --- */
@media (max-width: 480px) {
  /* Slightly larger floors relative to width so cards stay legible on phones. */
  .gamescreen {
    --deck-card-w: clamp(44px, 18vw, 72px);
    --pile-card-w: clamp(64px, 26vw, 104px);
  }

  /* Tighten the action buttons so Send + Slap stay side-by-side and on-screen. */
  .action_button {
    margin-left: 4px;
    margin-right: 4px;
  }

  .action_buttons :deep(.p-button) {
    padding: 0.45rem 0.7rem;
    font-size: 0.85rem;
  }

  /* Take Back out of the bottom flow so it never competes with the action
     buttons for width; pin it to the safe bottom-left corner. */
  .back_button {
    position: absolute;
    left: 0;
    bottom: 0;
    margin: 8px;
    margin-left: calc(8px + env(safe-area-inset-left, 0px));
    margin-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
    z-index: 1500;
  }
}
```

- [ ] **Step 2: Add safe-area inset to the rules toggle**

In `RulesPanel.vue`, replace the `.rules-toggle` positioning. Change:

```css
.rules-toggle {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 1500;
```

to:

```css
.rules-toggle {
  position: absolute;
  top: calc(16px + env(safe-area-inset-top, 0px));
  right: calc(16px + env(safe-area-inset-right, 0px));
  z-index: 1500;
```

- [ ] **Step 3: Verify the build succeeds**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

- [ ] **Step 4: Commit**

```bash
git -C frontend add src/view/alpha/GameScreen.vue src/components/RulesPanel.vue
git commit -m "feat: compact phone breakpoint and safe-area insets for game chrome"
```

---

## Task 6: Verify the regression test passes and re-check desktop

**Files:** none (verification only)

- [ ] **Step 1: Run the responsive spec — expect GREEN**

Run (dev server + backend up):
`cd frontend && npm run cy:run -- --spec cypress/specs/responsive-game-screen.cy.ts`
Expected: all three assertions PASS (fits, decks/pile visible, Send/Slap on-screen).

- [ ] **Step 2: Run the full Cypress suite to catch regressions**

Run: `cd frontend && npm run cy:run`
Expected: all specs pass — confirms the layout change did not break
turn-indicator, send-card, rules-panel, waiting/names, create-game, or
rehydrate-game flows.

- [ ] **Step 3: Manual resize check**

Start the app (`npm run dev` + backend). In the browser, open a game and resize
the window from desktop width down through tablet (~768px) to phone-portrait
(~390px). Confirm at every width: the whole board fits with no scrollbars, both
decks + pile + Send/Slap are visible, and a Send/grab/slap animation still lands
on the correct cards.

- [ ] **Step 4: Final build gate**

Run: `cd frontend && npm run build`
Expected: build completes with no errors.

---

## Self-Review notes

- **Spec coverage:** viewport meta (Task 2), dvh height + fluid tokens + fluid
  cards + fluid pile slot (Task 3), flex bands replacing 30/40/30 vertical &
  horizontal (Task 4), compact breakpoint + safe-area chrome (Task 5), Cypress
  fit/visibility/clickable spec + manual resize + build gate (Tasks 1 & 6). All
  spec sections map to tasks.
- **No PlayingCard contract change:** sizing overridden via `:deep(.playing_card)`
  CSS only, honoring the YAGNI guardrail.
- **Animation safety:** no change to `useCardAnimation.ts`; it measures live
  rects, verified in the spec.
- **Token name consistency:** `--deck-card-w`, `--pile-card-w`, `--card-aspect`,
  `--band-pad`, `--stack-gap` are defined in Task 3 and reused unchanged in
  Tasks 4 and 5.
