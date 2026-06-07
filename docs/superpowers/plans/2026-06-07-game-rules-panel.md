# Game Rules Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a foldable, floating "Rules" panel to the game screen that explains the rules of Bataille Corse, with text organized into dedicated, translation-ready locale files (English only for now).

**Architecture:** A namespaced message tree (`locales/`) with only the `rules` namespace populated, resolved through a general `useI18n()` composable (defaults to `en`). A `RulesPanel.vue` component composes `useI18n()` for content and a generic `useDisclosure()` composable for open/close state, and is dropped once into `GameScreen.vue`. Pure logic is unit-tested with Vitest; rendered behavior is covered by Cypress (the project has no `@vue/test-utils`).

**Tech Stack:** Vue 3 (`<script setup>` + TS), Pinia (not needed here), PrimeVue, Vitest + happy-dom, Cypress.

---

## Environment notes (read first)

- All commands run from the `frontend/` directory.
- This worktree may lack `node_modules`. If any `npx`/`npm` command fails with missing modules, run `npm install` once first. (See the project memory: vite build is the real type/compile gate in worktrees.)
- Vitest config lives in `frontend/vite.config.mjs` (`test.environment: 'happy-dom'`).
- The Cypress task requires the full app running (frontend + backend), e.g. via `./dev.sh` from the repo root, then point Cypress at the dev server. Do not block earlier tasks on Cypress.
- Game route is `/room/:id`; `cy.createGame()` (in `cypress/support/commands.ts`) creates a solo game and lands on it.

## File Structure

- Create `frontend/src/locales/Messages.ts` — TS types for the namespaced message tree (`Messages`, `RulesMessages`, `RulesSection`).
- Create `frontend/src/locales/en.ts` — `messagesEn: Messages`, only `rules` populated.
- Create `frontend/src/locales/index.ts` — locale registry + `DEFAULT_LOCALE`.
- Create `frontend/src/locales/en.test.ts` — content-shape test.
- Create `frontend/src/composables/useI18n.ts` — locale resolver.
- Create `frontend/src/composables/useI18n.test.ts` — resolver test.
- Create `frontend/src/composables/useDisclosure.ts` — open/close state.
- Create `frontend/src/composables/useDisclosure.test.ts` — disclosure test.
- Create `frontend/src/components/RulesPanel.vue` — toggle + overlay panel.
- Modify `frontend/src/view/alpha/GameScreen.vue` — render `<RulesPanel />`.
- Create `frontend/cypress/specs/rules-panel.cy.ts` — e2e acceptance.

---

### Task 1: Message types + English content + registry

**Files:**
- Create: `frontend/src/locales/Messages.ts`
- Create: `frontend/src/locales/en.ts`
- Create: `frontend/src/locales/index.ts`
- Test: `frontend/src/locales/en.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/locales/en.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { messagesEn } from './en';

describe('English messages', () => {
  it('givenEnglishMessages_thenRulesHasToggleAndTitle', () => {
    expect(messagesEn.rules.toggleLabel).toBeTruthy();
    expect(messagesEn.rules.panelTitle).toBeTruthy();
  });

  it('givenEnglishMessages_thenRulesHasSixSectionsEachWithTitleAndBody', () => {
    expect(messagesEn.rules.sections).toHaveLength(6);
    for (const section of messagesEn.rules.sections) {
      expect(section.title).toBeTruthy();
      expect(section.body.length).toBeGreaterThan(0);
    }
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/locales/en.test.ts`
Expected: FAIL — cannot resolve `./en` (module does not exist yet).

- [ ] **Step 3: Create the message types**

Create `frontend/src/locales/Messages.ts`:

```ts
export interface RulesSection {
  title: string;
  body: string[]; // one entry per paragraph / bullet line
}

export interface RulesMessages {
  toggleLabel: string;      // text on the floating chip
  panelTitle: string;       // header of the expanded panel
  sections: RulesSection[]; // ordered rule sections
}

// Whole-app message tree. Only `rules` is populated today; future namespaces
// (game, lobby, ...) are added here additively.
export interface Messages {
  rules: RulesMessages;
}
```

- [ ] **Step 4: Create the English content**

Create `frontend/src/locales/en.ts`:

```ts
import type { Messages } from './Messages';

export const messagesEn: Messages = {
  rules: {
    toggleLabel: 'Rules',
    panelTitle: 'How to play',
    sections: [
      {
        title: 'Goal',
        body: ['Win all the cards. The game ends when one player has them all.'],
      },
      {
        title: 'Send',
        body: ['On your turn, send your top card to the central pile. Play then passes to your opponent.'],
      },
      {
        title: 'Slap',
        body: [
          'Either player can slap when the pile shows:',
          'Doubles — top two cards same rank.',
          'Sandwich — top card matches the card two below.',
          'Sum of ten — top two cards add up to ten.',
          'Tens — top card is a ten.',
          'First to slap a valid pile takes it all.',
        ],
      },
      {
        title: 'Wrong slap',
        body: ['Slap with no valid pattern and two of your cards go under the pile.'],
      },
      {
        title: 'Honour cards (J Q K A)',
        body: ['Send one and your opponent must answer with an honour card within a few cards: J → 1, Q → 2, K → 3, A → 4. If they don’t, the pile is yours to grab.'],
      },
      {
        title: 'Grab',
        body: ['When the pile becomes grabbable, grab it to take every card.'],
      },
    ],
  },
};
```

- [ ] **Step 5: Create the locale registry**

Create `frontend/src/locales/index.ts`:

```ts
import { messagesEn } from './en';

export const messages = { en: messagesEn } as const;
export type Locale = keyof typeof messages;
export const DEFAULT_LOCALE: Locale = 'en';
```

- [ ] **Step 6: Run test to verify it passes**

Run: `npx vitest run src/locales/en.test.ts`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/locales/Messages.ts frontend/src/locales/en.ts frontend/src/locales/index.ts frontend/src/locales/en.test.ts
git commit -m "feat: add translation-ready rules message files (English)"
```

---

### Task 2: `useI18n` resolver composable

**Files:**
- Create: `frontend/src/composables/useI18n.ts`
- Test: `frontend/src/composables/useI18n.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/composables/useI18n.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { useI18n } from './useI18n';
import { messagesEn } from '../locales/en';

describe('useI18n', () => {
  it('givenNoLocale_thenReturnsEnglish', () => {
    expect(useI18n()).toBe(messagesEn);
  });

  it('givenEnLocale_thenReturnsEnglish', () => {
    expect(useI18n('en')).toBe(messagesEn);
  });

  it('givenUnknownLocale_thenFallsBackToEnglish', () => {
    expect(useI18n('zz')).toBe(messagesEn);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/composables/useI18n.test.ts`
Expected: FAIL — cannot resolve `./useI18n`.

- [ ] **Step 3: Write the resolver**

Create `frontend/src/composables/useI18n.ts`:

```ts
import { messages, DEFAULT_LOCALE, type Locale } from '../locales';
import type { Messages } from '../locales/Messages';

// Returns the active locale's messages, defaulting to English. An unknown locale
// falls back to DEFAULT_LOCALE. Locale is constant for now; when a locale picker
// is added later this is the single seam to make reactive.
export function useI18n(locale: string = DEFAULT_LOCALE): Messages {
  return messages[locale as Locale] ?? messages[DEFAULT_LOCALE];
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/composables/useI18n.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useI18n.ts frontend/src/composables/useI18n.test.ts
git commit -m "feat: add useI18n locale resolver"
```

---

### Task 3: `useDisclosure` composable

**Files:**
- Create: `frontend/src/composables/useDisclosure.ts`
- Test: `frontend/src/composables/useDisclosure.test.ts`

- [ ] **Step 1: Write the failing test**

Create `frontend/src/composables/useDisclosure.test.ts`:

```ts
import { describe, it, expect } from 'vitest';
import { useDisclosure } from './useDisclosure';

describe('useDisclosure', () => {
  it('givenNoArg_thenStartsClosed', () => {
    expect(useDisclosure().isOpen.value).toBe(false);
  });

  it('givenClosed_whenOpen_thenOpen', () => {
    const { isOpen, open } = useDisclosure();
    open();
    expect(isOpen.value).toBe(true);
  });

  it('givenOpen_whenClose_thenClosed', () => {
    const { isOpen, close } = useDisclosure(true);
    close();
    expect(isOpen.value).toBe(false);
  });

  it('givenClosed_whenToggle_thenOpen', () => {
    const { isOpen, toggle } = useDisclosure();
    toggle();
    expect(isOpen.value).toBe(true);
  });

  it('givenOpen_whenToggle_thenClosed', () => {
    const { isOpen, toggle } = useDisclosure(true);
    toggle();
    expect(isOpen.value).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run src/composables/useDisclosure.test.ts`
Expected: FAIL — cannot resolve `./useDisclosure`.

- [ ] **Step 3: Write the composable**

Create `frontend/src/composables/useDisclosure.ts`:

```ts
import { ref } from 'vue';

// Minimal open/close state for collapsible UI. Lifecycle-free so it can be
// unit-tested directly; DOM wiring (e.g. Esc key) lives in the consuming component.
export function useDisclosure(initial = false) {
  const isOpen = ref(initial);
  const open = () => { isOpen.value = true; };
  const close = () => { isOpen.value = false; };
  const toggle = () => { isOpen.value = !isOpen.value; };
  return { isOpen, open, close, toggle };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run src/composables/useDisclosure.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useDisclosure.ts frontend/src/composables/useDisclosure.test.ts
git commit -m "feat: add useDisclosure composable"
```

---

### Task 4: `RulesPanel.vue` component

**Files:**
- Create: `frontend/src/components/RulesPanel.vue`

No unit test (project has no `@vue/test-utils`); rendered behavior is verified by Cypress in Task 6. The gate for this task is a successful build.

- [ ] **Step 1: Create the component**

Create `frontend/src/components/RulesPanel.vue`:

```vue
<template>
  <button
    type="button"
    class="rules-toggle"
    :aria-expanded="isOpen"
    aria-controls="rules-panel"
    data-cy="rules-toggle"
    @click="toggle"
  >
    <i class="pi pi-info-circle" />
    <span>{{ messages.rules.toggleLabel }}</span>
  </button>

  <div
    v-if="isOpen"
    id="rules-panel"
    class="rules-panel"
    role="dialog"
    :aria-label="messages.rules.panelTitle"
    data-cy="rules-panel"
  >
    <div class="rules-panel__header">
      <h2 class="rules-panel__title">{{ messages.rules.panelTitle }}</h2>
      <button
        type="button"
        class="rules-panel__close"
        aria-label="Close"
        data-cy="rules-close"
        @click="close"
      >
        <i class="pi pi-times" />
      </button>
    </div>

    <div class="rules-panel__body">
      <section
        v-for="(section, i) in messages.rules.sections"
        :key="i"
        class="rules-section"
      >
        <h3 class="rules-section__title">{{ section.title }}</h3>
        <p
          v-for="(line, j) in section.body"
          :key="j"
          class="rules-section__line"
        >{{ line }}</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue';
import { useI18n } from '../composables/useI18n';
import { useDisclosure } from '../composables/useDisclosure';

const messages = useI18n();
const { isOpen, close, toggle } = useDisclosure();

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) close();
}

onMounted(() => document.addEventListener('keydown', handleKeydown));
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown));
</script>

<style scoped>
/* Toggle chip — top-right, themed to match the felt table. Sits above the
   cards (z 1000/1001) but below the waiting/end overlays (z 2000). */
.rules-toggle {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
}

.rules-toggle:hover {
  background: rgba(0, 0, 0, 0.6);
}

.rules-panel {
  position: absolute;
  top: 56px;
  right: 16px;
  z-index: 1500;
  width: min(360px, calc(100vw - 32px));
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: rgba(0, 0, 0, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.rules-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.rules-panel__title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: #f5c842;
  margin: 0;
}

.rules-panel__close {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  font-size: 1rem;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
}

.rules-panel__close:hover {
  color: #fff;
}

.rules-panel__body {
  overflow-y: auto;
  padding: 8px 16px 16px;
}

.rules-section {
  margin-top: 14px;
}

.rules-section__title {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.55);
  margin: 0 0 4px;
}

.rules-section__line {
  font-size: 0.9rem;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.88);
  margin: 0 0 4px;
}
</style>
```

- [ ] **Step 2: Verify it compiles**

Run: `npm run build`
Expected: build succeeds with no type errors. (RulesPanel is not yet rendered anywhere; this confirms it compiles.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/RulesPanel.vue
git commit -m "feat: add RulesPanel component"
```

---

### Task 5: Wire `RulesPanel` into the game screen

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Add the import**

In `frontend/src/view/alpha/GameScreen.vue`, add the import alongside the existing component imports. Find:

```ts
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
```

Replace with:

```ts
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import RulesPanel from '../../components/RulesPanel.vue';
```

- [ ] **Step 2: Render the panel**

In the same file, find the opening of the root template element:

```html
  <div class="gamescreen flex">

    <div class="gamescreen_top flex">
```

Replace with:

```html
  <div class="gamescreen flex">

    <RulesPanel />

    <div class="gamescreen_top flex">
```

- [ ] **Step 3: Verify it compiles**

Run: `npm run build`
Expected: build succeeds with no type errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: show rules panel on the game screen"
```

---

### Task 6: Cypress e2e acceptance

**Files:**
- Create: `frontend/cypress/specs/rules-panel.cy.ts`

Requires the app running (frontend + backend). Start it (e.g. `./dev.sh` from repo root) before running Cypress.

- [ ] **Step 1: Write the e2e spec**

Create `frontend/cypress/specs/rules-panel.cy.ts`:

```ts
describe('Rules panel', () => {
  it('opens from the toggle and closes with the close button', () => {
    cy.createGame();

    // Collapsed by default.
    cy.get('[data-cy="rules-panel"]').should('not.exist');

    // Open and show content.
    cy.get('[data-cy="rules-toggle"]').click();
    cy.get('[data-cy="rules-panel"]')
      .should('be.visible')
      .and('contain.text', 'How to play')
      .and('contain.text', 'Doubles');

    // Close via the close button.
    cy.get('[data-cy="rules-close"]').click();
    cy.get('[data-cy="rules-panel"]').should('not.exist');
  });

  it('closes with the Escape key', () => {
    cy.createGame();

    cy.get('[data-cy="rules-toggle"]').click();
    cy.get('[data-cy="rules-panel"]').should('be.visible');

    cy.get('body').type('{esc}');
    cy.get('[data-cy="rules-panel"]').should('not.exist');
  });
});
```

- [ ] **Step 2: Run the e2e spec**

Run: `npx cypress run --spec "cypress/specs/rules-panel.cy.ts"`
Expected: PASS (2 tests).

- [ ] **Step 3: Commit**

```bash
git add frontend/cypress/specs/rules-panel.cy.ts
git commit -m "test: add e2e for rules panel"
```

---

### Task 7: Full verification

- [ ] **Step 1: Run all unit tests**

Run: `npm test`
Expected: all Vitest suites pass (including the three new ones).

- [ ] **Step 2: Build**

Run: `npm run build`
Expected: build succeeds.

- [ ] **Step 3 (final, per standing preference): open a pull request**

Push the branch and open a PR with a self-authored description summarizing the rules panel and the translation-ready locale structure. End the PR body with the required Claude Code attribution line.

---

## Self-Review

**Spec coverage:**
- Floating toggle + overlay panel, collapsed by default, Esc/✕/toggle to close → Task 4 (+ Task 6 verifies).
- App-wide-ready i18n, only `rules` populated; `Messages` type, `en.ts`, registry with `DEFAULT_LOCALE`, general `useI18n` → Tasks 1–2.
- One file per locale, structured object → `en.ts` (Task 1).
- z-index below the 2000 overlays → Task 4 CSS (`z-index: 1500`).
- Concise rules content, "Winning" merged into "Goal", 6 sections → Task 1 content.
- Tests: `useI18n` fallback, content shape, disclosure logic, e2e → Tasks 1, 2, 3, 6.
- Wire into `GameScreen.vue` once, no layout change → Task 5.
- No backend changes → confirmed; no Java tasks.

**Placeholder scan:** No TBD/TODO; every code/test step has full content.

**Type consistency:** `Messages` / `RulesMessages` / `RulesSection` defined in Task 1 and used identically in `en.ts`, `useI18n.ts`, and `RulesPanel.vue`. `messages` / `Locale` / `DEFAULT_LOCALE` from `locales/index.ts` used consistently in `useI18n.ts`. `useDisclosure` returns `{ isOpen, open, close, toggle }`, all used as defined in `RulesPanel.vue`. `data-cy` hooks (`rules-toggle`, `rules-panel`, `rules-close`) match between Task 4 and Task 6.
