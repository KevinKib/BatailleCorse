# GameScreen.vue de-bloat — design

**Date:** 2026-06-13
**Goal:** Decompose `frontend/src/view/alpha/GameScreen.vue` (a ~480-line `<script setup>` +
~620-line `<style>` doing ~6 distinct jobs) into focused composables and presentational
components, with no change in observable behavior.

## Motivation

`GameScreen.vue` has become the frontend's largest file and the one most often edited.
It mixes animation orchestration, a disconnect countdown, a leave/forfeit guard, the
turn indicator, session bootstrap, and three inline overlays. Size makes it hard to read,
hard to review, and a place where bugs hide. This is the maintainability item now topping
issue #30 (after N°5 and N°6 shipped). It is behavior-preserving refactoring only — no new
features, no UX changes.

## Scope

In scope (one PR):

- 5 composables: `useGameAnimations`, `useDisconnectCountdown`, `useLeaveGuard`,
  `useTurnIndicator`, `useGameBootstrap`.
- 3 presentational components: `WaitingOverlay`, `DisconnectOverlay`, `EndGameOverlay`
  (each takes its scoped CSS with it).

Explicitly out of scope:

- **No `AnimationLayer` component.** The ghost-card / slap-ghost / card-delta fixed-position
  layers stay inline; they are tightly coupled to `useGameAnimations` and extracting them
  buys little.
- **No `PlayerDeck` dedup** of the top/bottom card blocks. It would force template-ref
  forwarding (`defineExpose`) that the animation code depends on via `getBoundingClientRect`,
  adding regression risk for modest gain. Revisit separately if desired.
- No behavior, timing, or styling changes. No backend changes.

## Conventions

Follow the existing composable style (`useCardAnimation`, `useEndScreen`, `useHotkeys`):
composables take **getter functions / callbacks** as inputs and return refs + methods; each
composable owns its own lifecycle cleanup (`onBeforeUnmount`) rather than leaving it to the
view. Components are `<script setup lang="ts">` with typed `defineProps` / `defineEmits`.

Additional rules adopted from the project's `vue-best-practices` skill (validated against its
`composables.md` / `component-data-flow.md` references):

- **Options-object inputs.** Composables taking more than one input receive a single typed
  options object (`useGameAnimations({ pile, opponentCard, centerPile, centerPileArea })`),
  not positional getters — keeps call sites readable and order-proof.
- **`readonly` returns.** State a consumer only reads (e.g. `slapImpact`, `secondsRemaining`,
  `showMyTurn`, the animation refs) is returned `readonly` so mutation flows through the
  composable, not from the template. (Where `useCardAnimation` already owns the ref's
  mutation internally, re-export it `readonly` from `useGameAnimations`.)
- **Named TypeScript contracts.** Component props/emits use named `interface`s with
  `defineProps<Props>()` / `defineEmits<Emits>()` — no inline object shapes. The
  `rematchButton` prop gets a shared `RematchButton { label: string; disabled: boolean }`
  type.
- **SFC section order: `<template>` → `<script>` → `<style>`,** matching the project's
  dominant convention (4/5 components + all views). Note: the skill itself prefers
  `<script>`-first; project consistency wins here per "match the surrounding code." Existing
  `useTemplateRef` usage confirms the project is on Vue 3.5+.

### Alignment notes (already conformant — do not regress)

- GameScreen is a **route-level view**; the refactor keeps it a thin composition surface, per
  the skill's entry/root/view rule.
- The remaining template refs (`pile`, `opponentCard`, `centerPile`, `centerPileArea`) are
  **DOM element refs** used for `getBoundingClientRect` measurement — not component-instance
  refs. Skipping the `PlayerDeck` dedup deliberately avoids `defineExpose` component-ref
  forwarding, which the skill discourages ("prefer props/emit over component refs").
- Animations are preserved as-is: `<Transition>` for the turn-hint enter/leave, class-based
  for slap juice, state-driven for the ghost/delta layers — all matching the skill's
  animation guidance.

## Composable contracts

### `useGameAnimations`
The largest extraction. Owns the card-animation lifecycle and all reactive wiring to store
action events.

- **Input:** the four template-ref getters — `pile`, `opponentCard`, `centerPile`,
  `centerPileArea`.
- **Internally:** constructs `useCardAnimation(...)`; sets up the 6 watchers currently in the
  view (pile-card swap → `onNewPileCard`; `lastSend`; `lastGrab`; `lastSlap` → `flashPile`;
  `lastSuccessfulSlap`; `lastErroneousSlap`); owns the `slapImpact` flash + its timer; calls
  `batailleCorseStore.notifyAnimationComplete()` at the same points as today; reads store refs
  (`lastSend`, `lastGrab`, …, `myPlayerIndex`) directly; cancels all animations on unmount.
- **Returns:** `{ ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard,
  cardDeltaIndicator, slapImpact }` — exactly what the template and `useEndScreen` consume.

### `useDisconnectCountdown`
- **Input:** getters `mode`, `opponentConnection`, `myPlayerIndex`, `isGameOver`.
- **Owns:** the `now` ref + `setInterval` timer (start on disconnect, clear on reconnect/end),
  cleanup on unmount.
- **Returns:** `{ opponentDisconnected, secondsRemaining }`.

### `useLeaveGuard`
- **Input:** `{ isInProgress: () => boolean, mode: () => GameMode, forfeit: () => void }`.
- **Owns:** `onBeforeRouteLeave` (confirm + forfeit in multiplayer) and the `beforeunload`
  listener; adds/removes the listener.
- **Returns:** nothing.

### `useTurnIndicator`
- **Input:** getters `state` (the `BatailleCorse` model), `myPlayerIndex`, `opponentIndex`,
  `isWaiting`, `showEndOverlay`.
- **Owns:** the first-turn-hint watch + consumed flag.
- **Returns:** `{ showMyTurn, showOpponentTurn, showFirstTurnHint, YOUR_TURN_LABEL }`.

### `useGameBootstrap`
- **Input:** `{ route, router, revealImmediatelyIfOver: () => void }` (and uses the store +
  `webSocketService` directly).
- **Owns:** the `onMounted` sequence (token check → `fetch /api/game/{id}` → `hydrate` →
  `revealImmediatelyIfOver` → `restoreSession` → `loadSessionView` → `subscribeToGame`) and the
  matching unmount cleanup (`cancelAutoGrab`, `unsubscribeFromGame`). Must be invoked **after**
  `useEndScreen` so `revealImmediatelyIfOver` exists.
- **Returns:** nothing.

## Component contracts

### `WaitingOverlay.vue`
- Rendered by parent `v-if="isWaiting"`.
- Self-contained: computes `shareLink` from `useRoute()`, owns `copied` + `copyShareLink`.
- Moves `.waiting-*` CSS.

### `DisconnectOverlay.vue`
- Rendered by parent `v-if="opponentDisconnected"`.
- **Props:** `opponentLabel: string`, `secondsRemaining: number`.
- Moves `.disconnect-*` CSS.

### `EndGameOverlay.vue`
- Rendered by parent `v-if="showEndOverlay"`.
- **Props:** `didIWin: boolean`, `subtitle: string`, `rematchButton: RematchButton` (named type).
- **Emits:** typed `defineEmits<{ playAgain: [] }>` (`@play-again` in the template).
- Contains the trophy/victory/defeat markup + the "Back to home" `RouterLink`.
- Moves `.end-*` CSS.

## Resulting view

After extraction, `GameScreen.vue` keeps: layout markup, template refs, the inline animation
layers (ghosts/delta), the action helpers (`send`/`slap`/`isButtonDisabled`), the `useHotkeys`
wiring, and a handful of view-model computeds (`opponentLabel`, `opponentIndex`, `isSolo`,
`isWaiting`, `pileIsEmpty`, `isGameOver`, `didIWin`, `endSubtitle`, `rematchButton`,
`onPlayAgain`) that wire composables to components. Target: ~120-line script, CSS roughly halved.

## Slicing (implementation order — riskiest last)

Each slice must build cleanly (`vite build`) and is independently reviewable.

1. **Overlay components** — extract `WaitingOverlay`, `DisconnectOverlay`, `EndGameOverlay`
   (pure template + CSS move, props in). Safest.
2. **Self-contained composables** — `useDisconnectCountdown`, `useLeaveGuard`,
   `useTurnIndicator`, `useGameBootstrap`.
3. **`useGameAnimations`** — the animation orchestration. Highest regression risk; done last
   and verified carefully.

## Testing & verification

Behavior-preserving refactor, so the gates are:

- **Primary gate:** `vite build` succeeds (per project convention — bare `vue-tsc` gives false
  passes in worktrees lacking `node_modules`).
- **Unit tests** for the two pure-ish composables where they add value, following the existing
  `*.test.ts` convention (`useDisclosure.test.ts`, `useEndScreen.test.ts`): `useTurnIndicator`
  (glow/hint logic off getters) and `useDisconnectCountdown` (seconds math + start/stop). The
  DOM/lifecycle-heavy composables (`useGameAnimations`, `useLeaveGuard`, `useGameBootstrap`)
  rely on the build + manual smoke rather than brittle DOM unit tests.
- **Manual smoke:** play a solo game — send/grab/slap animations, slap success/error juice,
  turn glow + first-turn hint, end overlay + play-again, leave confirmation, and (multiplayer)
  the waiting + disconnect overlays — behave exactly as before.

## Risks

- **Animation timing regressions** in `useGameAnimations` — mitigated by doing it last, keeping
  the watcher bodies byte-for-byte where possible, and manual smoke of every animation path.
- **Composable ordering** — `useGameBootstrap` depends on `useEndScreen`'s
  `revealImmediatelyIfOver`; the view must instantiate them in the right order.
