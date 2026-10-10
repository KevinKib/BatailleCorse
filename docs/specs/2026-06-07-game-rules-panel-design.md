# Game Rules Panel — Design

**Date:** 2026-06-07
**Status:** Approved (pending spec review)

## Summary

Add a foldable, floating "Rules" panel to the game screen that explains all the
rules of Bataille Corse. The text is written in English now, but organized so a
future translation is a clean, additive change. The translation structure is
built **app-wide-ready** (a general `useI18n` resolver and a namespaced message
tree), but only the `rules` namespace is populated and consumed in this work.

## Scope

**In scope**
- A `RulesPanel.vue` component: a floating toggle that expands a scrollable
  overlay panel explaining the rules, collapsed by default.
- An i18n foundation shaped for the whole app but only filled in for `rules`:
  - A namespaced message-tree type (`Messages`).
  - An English message file (`en.ts`) populating only `rules`.
  - A locale registry with a single `DEFAULT_LOCALE` constant.
  - A general `useI18n()` composable resolver.
- Wiring `<RulesPanel />` into `GameScreen.vue`.
- Unit/component/e2e tests.

**Out of scope (deliberately)**
- Migrating existing app strings (buttons, overlays, lobby, start screen) into
  the message files. The structure is ready for it; the migration is a separate
  follow-up.
- Adding a locale picker / changing `Settings.store`. The default locale is `en`.
- Interpolation / pluralization machinery. Static rules prose does not need it;
  this decision is deferred to the future app-wide migration.
- Any backend change.

## Decisions

These were settled during brainstorming:

- **i18n scope:** App-wide-*ready* foundation, but only the `rules` namespace is
  populated/consumed now (the "middle path").
- **String access:** Direct locale module + a tiny resolver composable. No
  `vue-i18n` dependency (revisit when/if the app-wide migration happens, since
  that brings interpolation needs into play).
- **Placement/interaction:** Floating toggle chip (top-right) + overlay panel,
  collapsed by default. Closes via toggle, ✕ button, or `Esc`.
- **File organization:** One file per locale, holding one structured object.
- **Content:** Concise rules text, "Winning" merged into "Goal".

## Architecture

```
frontend/src/
  locales/
    Messages.ts      # TS type for the whole namespaced message tree
    en.ts            # exports `messagesEn: Messages` — only `rules` filled in
    index.ts         # registry { en: messagesEn } + DEFAULT_LOCALE = 'en'
  composables/
    useI18n.ts       # returns the active locale's messages (defaults to 'en')
  components/
    RulesPanel.vue   # floating toggle + overlay panel
```

### `Messages.ts`
Defines the whole message tree as a namespaced type. Today:

```ts
export interface RulesSection {
  title: string;
  body: string[]; // one entry per paragraph / bullet line
}

export interface RulesMessages {
  toggleLabel: string;       // text on the floating chip
  panelTitle: string;        // header of the expanded panel
  sections: RulesSection[];  // ordered rule sections
}

export interface Messages {
  rules: RulesMessages;
  // future namespaces (game, lobby, ...) added here additively
}
```

Empty namespaces are **not** stubbed (YAGNI); the type is shaped so adding them
later is purely additive.

### `en.ts`
Exports `messagesEn: Messages` with only `rules` populated (content below).

### `index.ts`
```ts
export const DEFAULT_LOCALE = 'en';
export const messages = { en: messagesEn } as const;
export type Locale = keyof typeof messages;
```
The single source of truth for "which locales exist" and "what the default is".

### `useI18n.ts`
A general resolver (not rules-specific). Returns the active locale's `Messages`
object, defaulting to `DEFAULT_LOCALE`; an unknown locale falls back to the
default. Today it always resolves `en`; the locale argument/seam is where a
future locale picker wires in. No `Settings.store` dependency now.

### `RulesPanel.vue`
- **Collapsed:** a small floating toggle chip in the top-right corner — icon
  (`pi pi-info-circle` / `pi pi-question`) + the `rules.toggleLabel` text,
  styled to match the existing felt/dark theme (cf. `.player_tag` and the
  PrimeVue buttons already on the screen).
- **Expanded:** a scrollable panel floating over the table
  (`position: absolute`), with a header (`rules.panelTitle` + ✕ close button)
  and the sections rendered from `rules.sections` — each section's `title`
  followed by its `body` lines.
- **z-index:** above the table but **below** the waiting/end overlays
  (which use `z-index: 2000`), so it never covers the win/lose screen.
- **State:** a single local `ref` `isOpen`, collapsed by default on every load.
  No store.
- **Close affordances:** clicking the toggle again, the ✕, or pressing `Esc`.
- **Accessibility:** toggle is a real `<button>` with `aria-expanded`; panel has
  `role="dialog"` and an `aria-label`. Honors the existing
  `prefers-reduced-motion` pattern for any expand animation.
- **Content source:** consumes `useI18n()` — no hardcoded strings in the
  template.

### `GameScreen.vue`
One `<RulesPanel />` added inside the root, sibling to the existing waiting/end
overlays. No change to the top/middle/bottom layout blocks.

## Rules content (English draft)

Stored in `en.ts` under `rules`. Wording is a starting point and may be tuned;
the sectioning is the contract.

- `toggleLabel`: "Rules"
- `panelTitle`: "How to play"
- `sections`:
  - **Goal** — "Win all the cards. The game ends when one player has them all."
  - **Send** — "On your turn, send your top card to the central pile. Play then
    passes to your opponent."
  - **Slap** — "Either player can slap when the pile shows:"
    - "Doubles — top two cards same rank."
    - "Sandwich — top card matches the card two below."
    - "Sum of ten — top two cards add up to ten."
    - "Tens — top card is a ten."
    - "First to slap a valid pile takes it all."
  - **Wrong slap** — "Slap with no valid pattern and two of your cards go under
    the pile."
  - **Honour cards (J Q K A)** — "Send one and your opponent must answer with an
    honour card within a few cards: J → 1, Q → 2, K → 3, A → 4. If they don't,
    the pile is yours to grab."
  - **Grab** — "When the pile becomes grabbable, grab it to take every card."

These numbers (2-card penalty; 1/2/3/4 honour chances) and patterns mirror
backend constants but live here as **plain prose** — the frontend has no access
to those Java values, so the rules text is descriptive, not derived. If the
backend rules change, this text must be updated by hand.

## Testing

Following project conventions (Vitest unit/component tests, Cypress e2e):

- **`useI18n` (unit):** resolves to `en` by default; unknown locale falls back to
  `DEFAULT_LOCALE`. Naming: `givenUnknownLocale_thenFallsBackToEnglish`, etc.
- **`Messages` shape (compile-time):** `en.ts` typed as `Messages`; a missing or
  renamed key fails the build — no runtime test needed.
- **`RulesPanel.vue` (component, Vitest + happy-dom):** collapsed by default;
  toggle opens it; ✕ / `Esc` / re-click closes it; renders one block per section
  with titles from the messages object. Naming:
  `givenPanelClosed_whenToggleClicked_thenPanelOpens`, etc.
- **e2e (Cypress):** on the game screen the Rules toggle is visible, opens the
  panel, and the panel does not obscure the end-game overlay. Add `data-cy`
  hooks on the toggle and panel.

No backend changes, so no Java tests.

## Future translation (how `fr` gets added later)

1. Add `frontend/src/locales/fr.ts` exporting `messagesFr: Messages` — the type
   forces it to match the shape.
2. Register it in `index.ts` (`{ en: messagesEn, fr: messagesFr }`).
3. Wire a locale value into `useI18n()` (e.g. a new `Settings.store` field +
   picker). That is the single seam.

Migrating the rest of the app's strings into other namespaces (`game`, `lobby`,
…) is the separate follow-up, at which point the interpolation/`vue-i18n`
decision is revisited.
