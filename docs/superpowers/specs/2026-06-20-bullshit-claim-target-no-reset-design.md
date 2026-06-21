# Bullshit: claim target should not reset after a Bullshit call

Fixes [#59](https://github.com/KevinKib/BatailleCorse/issues/59) — "claim rank shouldn't reset after a bullshit call."

## Problem

In Bullshit, each turn the current player must claim a forced target that climbs a
fixed progression (rank mode: ACE→TWO→…→KING→ACE; suit mode:
HEART→DIAMOND→CLUB→SPADE→HEART). The progression is *position-independent* — it
advances exactly once per discard and is shared by the whole table.

Today, `Bullshit.callBullshit()` unconditionally runs
`currentTarget = claimMode.initial();`, slamming the progression back to ACE/HEART
whenever anyone calls Bullshit — no matter how far it had climbed. That is the bug
reported in #59: the claim target should keep climbing across a call, not reset.

### Grounded current behavior

- `Bullshit.discard()` records `lastDiscard` (whose `claimedTarget` is the *current*
  target at discard time), then advances `currentTarget = claimMode.next(currentTarget)`.
  So at the moment any call is made, `currentTarget == next(challengedClaim)` always.
- `Bullshit.callBullshit()` `else` branch: `currentTarget = claimMode.initial();`
  resets the progression, then `currentPlayerIndex = players.indexOf(playerById(pickerId));`
  hands the next round to the picker (caller if truthful, claimant if a lie).

## Decision

**The claim progression continues across a Bullshit call.** A call resolution moves
only the pile (to the picker) and the turn (to the picker); it does **not** touch
`currentTarget`. The picker starts the next round at whatever `currentTarget` already
holds, i.e. `next(challengedClaim)`.

Because `discard()` already advanced the target, "keep where the progression was" and
"advance from the last claim" are the **same value** — there is no separate branch to
write. The rule is:

- **Identical for a truthful call and a bluff.** Only *who* picks up the pile and
  starts differs (already handled); the target does not.
- **Mode-agnostic.** Works for `AscendingRankClaimMode` and `CyclingSuitClaimMode`
  with no mode-specific code.
- The picker who starts the next round does not influence the target — the target is
  owned by the progression, not by player position.

`claimMode.initial()` remains correct for its two legitimate uses: dealing a fresh
game (constructor) and any future explicit "new round from scratch" need. It is simply
no longer invoked by `callBullshit()`.

## Changes

### Production (`bullshit.domain` only)

`Bullshit.callBullshit()` — remove the `currentTarget = claimMode.initial();` line from
the `else` branch. Keep the picker-starts assignment. Add a short *why* comment that the
claim progression intentionally continues across a call (only pile and turn move). No
other production change.

`ClaimMode` / `AscendingRankClaimMode` / `CyclingSuitClaimMode` — **untouched**. Their
`initial()` / `next()` / `matches()` are still correct.

### DTO / presentation seam — no change (confirmed)

`BullshitDto.forViewer(...)` and `BullshitWebSocketController` only *read*
`game.getCurrentTarget()`; they reflect the new value automatically. `ClaimTargetDto`
mapping is unchanged. No frontend change.

### Tests (`BullshitTest.java`, builder/fixtures — no Mockito on the aggregate)

Update the three tests that bake in the old reset, and add two regression tests:

1. `givenLie_whenCalled_thenLiarTakesPileAndRoundResets` — **rename** (drop
   "RoundResets"; e.g. `…thenLiarTakesPileAndClaimProgressionContinues`). After p0
   claims ACE, `currentTarget` is TWO; assert it **stays TWO** after the lie call
   (was asserting ACE). Update the inline comment.
2. `givenThreePlayersTruthfulCall_thenCallerStartsNextRoundAtAce` — **rename**
   `…AtAce` → `…AtNextRank`; assert `currentTarget` is **TWO**, not ACE.
3. `givenThreePlayersLieCalled_thenLiarStartsNextRoundAndPendingWinnerCleared` —
   **add** a `currentTarget` assertion (stays TWO) to lock the rule on the
   lie + pending-winner path.
4. **New** regression test — proves no reset from a climbed position: builder
   `withCurrentTarget(THREE)`, p0 discards claiming THREE (→ FOUR), p1 calls
   Bullshit; assert `currentTarget` is **FOUR** (decisively not ACE).
5. **New** suit-mode test — `CyclingSuitClaimMode`, discard claiming HEART
   (→ DIAMOND), call Bullshit; assert `currentTarget` is **DIAMOND** (not reset to
   HEART). Proves mode-agnosticism.

The `ClaimMode` unit tests (`AscendingRankClaimModeTest`, `CyclingSuitClaimModeTest`)
test `initial()`/`next()`/`matches()` directly and need **no** change — the rule lives
in the aggregate, not the strategy.

## Verification

Full backend suite green via the IntelliJ-bundled Maven before opening the PR. PR
closes #59.

## Out of scope / YAGNI

- No new `ClaimMode` method or "round" concept — the existing `next()`-per-discard
  progression already expresses the rule once the reset is removed.
- No frontend, DTO, session, or presentation change.
