# Opponent Send Animation Fix — Design

**Date:** 2026-06-07
**Status:** Approved

## Problem

In 2-player multiplayer mode, the card-send animation only plays for the action
the local player performs. When the *other* player sends a card, the pile updates
to show the new card but no send animation is shown.

## Root Cause

The send animation is driven by a `send` `GameEvent`, which sets `lastSend` in the
store and is watched in `GameScreen.vue` to run `animation.animateSend(...)`.

There are two ways a `send` action reaches a client:

1. **Local action (optimistic):** `GameSession.send()` emits the `send` GameEvent
   itself before publishing to the server. This is why your own send animates.
2. **Remote action (server broadcast):** the opponent's send arrives only as a
   server `Response` with `eventType: 'SEND'`.

`GameSession.processEvent` has branches for `CREATE`, `JOIN`, `GRAB`, and `SLAP`,
but **none for `SEND`**. A `SEND` response therefore falls straight through to the
`state-update` emission — the pile silently updates, but no `send` event is emitted,
so no animation plays. `GRAB` and `SLAP` animate for both players precisely because
they *are* handled in `processEvent`; `SEND` is the only action missing that path.

## Fix

Add a `SEND` branch in `processEvent`, before the `state-update` emission, that
emits a `send` GameEvent for the opponent's send.

```ts
if (response.eventType === 'SEND') {
  const sendData = response.eventData as SendEventData;
  const senderIndex = Number(sendData.player?.id);
  // Animate only the opponent's send during normal play. The local player's
  // send is already animated optimistically in send(); solo's AI send likewise
  // emits via send(1). Skip while catching up to avoid a flood of ghost cards.
  if (
    this.mode === 'multiplayer' &&
    !skipAnimation &&
    !isNaN(senderIndex) &&
    senderIndex !== this.myPlayerIndex
  ) {
    const topCard = this.state?.pile.cards.at(0);
    this.callbacks.onEvent({
      type: 'send',
      playerIndex: senderIndex,
      seq: ++this.sendSeq,
      topCard,
    });
  }
}
```

Requires importing `SendEventData` (`../model/event/SendEventData`) alongside the
existing `GrabEventData` / `SlapEventData` imports.

### Why each guard matters

- **`mode === 'multiplayer'`** — In solo, the AI's send already emits optimistically
  via `this.send(1)`; re-emitting on the response would double-animate. Solo behaviour
  must stay unchanged.
- **`senderIndex !== this.myPlayerIndex`** — In multiplayer, the local player's send
  is already emitted optimistically in `send()`. Without this guard, the echoed
  `SEND` response would fire a second animation for the local player.
- **`!skipAnimation`** — During catch-up (queue backed up, ≥3 events) the existing
  `skipAnimation` flag suppresses GRAB/SLAP animations. The opponent send respects
  the same flag so a lag/reconnect burst doesn't stack many ghost-card animations.

### Why `topCard` is correct here

At the point `processEvent` runs the `SEND` branch, `this.state` still holds the
*pre-send* pile (the `state-update` to the new state happens later, at the end of
`processEvent`). So `this.state?.pile.cards.at(0)` snapshots the previous top card —
exactly the semantics `send()` uses for the optimistic path. The existing
`onNewPileCard` watcher swaps the ghost image to the newly-landed card once the
state-update arrives.

### Non-blocking

Send is non-blocking in the event queue (no `awaitAnimation` / `needsAnimationWait`),
consistent with `GameScreen.vue` ("SEND is non-blocking in the queue"). The new
branch must not set `needsAnimationWait`.

## Testing

Unit tests in `GameSession.test.ts`:

- **Multiplayer, opponent send → emits a `send` event** with `playerIndex` =
  opponent and the pre-send `topCard` snapshot.
- **Multiplayer, own send response → does NOT emit a second `send` event** (only the
  optimistic one from `send()` exists).
- **Solo, AI/player-1 send response → does NOT emit a `send` event** from the
  response path (only the optimistic `send(1)` one).
- **Catch-up (queue ≥ 3) → opponent send response does NOT emit** a `send` event.

Follow project testing rules: no Mockito on domain classes, use the existing
builders/fixtures (`buildResponse`, `buildGame`, `buildPile`, `buildCard`).

## Scope / Out of Scope

- In scope: the missing `SEND` handler in `GameSession.processEvent` and its tests.
- Out of scope: any change to the animation composable, GameScreen watchers, or the
  optimistic `send()` path — all already work correctly.
