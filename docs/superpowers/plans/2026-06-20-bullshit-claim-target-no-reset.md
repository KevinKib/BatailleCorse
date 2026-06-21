# Bullshit claim-target-no-reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop `Bullshit.callBullshit()` from resetting the claim progression to ACE/HEART; the target continues climbing across a call (fixes #59).

**Architecture:** The claim progression is owned by `discard()`, which advances `currentTarget` once per discard. At call time `currentTarget` already equals `next(challengedClaim)`. The fix removes the single reset line in `callBullshit()`'s `else` branch so the picker resumes the next round at that already-advanced target. Mode-agnostic; identical for truthful vs bluff. TDD: tests pinning the new behavior are updated/added first (they fail against the reset), then the line is removed.

**Tech Stack:** Java, JUnit 5 + Hamcrest, domain Builders/Fixtures (no Mockito). Build/test via the IntelliJ-bundled Maven.

---

### Task 1: Pin the new rule in tests, then remove the reset

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Bullshit.java` (the `else` branch of `callBullshit()`, ~line 122)
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitTest.java`

Maven command (single line, run from `backend/`):
`"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=BullshitTest`

- [ ] **Step 1: Update the two reset-baking tests and add a `currentTarget` assertion to the third**

In `BullshitTest.java`:

Replace the existing `givenLie_whenCalled_thenLiarTakesPileAndRoundResets` test (currently asserting `currentTarget` is ACE) with:

```java
    @Test
    void givenLie_whenCalled_thenLiarTakesPileAndClaimProgressionContinues() throws Exception {
        // p0 must claim ACE but actually holds a KING -> a lie. Discarding advances the target to TWO;
        // the call must NOT reset it back to ACE.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards());

        CallBullshitOutcome outcome = game.callBullshit(new PlayerId(1));

        assertThat(outcome.claimWasTruthful(), is(false));
        assertThat(outcome.pilePicker(), is(new PlayerId(0)));
        assertThat(game.getDiscardPileSize(), is(0));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.TWO))); // progression continues, not reset
        assertThat(game.getCurrentPlayerIndex(), is(0));            // liar starts next round
        assertThat(game.getPlayers().get(0).handSize(), is(1));     // took the pile back
    }
```

In `givenThreePlayersTruthfulCall_thenCallerStartsNextRoundAtAce`, rename it and change the target assertion from ACE to TWO:

```java
    @Test
    void givenThreePlayersTruthfulCall_thenCallerStartsNextRoundAtNextRank() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING),
                        playerWithRanks(1, FrenchRank.TWO),
                        playerWithRanks(2, FrenchRank.THREE))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1)); // truthful ACE

        game.callBullshit(new PlayerId(2)); // truthful -> p2 takes pile and starts

        assertThat(game.getCurrentPlayer().id(), is(new PlayerId(2)));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.TWO))); // continues from ACE, not reset
    }
```

In `givenThreePlayersLieCalled_thenLiarStartsNextRoundAndPendingWinnerCleared`, add a `currentTarget` assertion at the end of the existing assertions (the discard of the ACE claim advanced it to TWO):

```java
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.TWO))); // progression continues across the call
```

- [ ] **Step 2: Add two regression tests**

Add these two tests to `BullshitTest.java`:

```java
    @Test
    void givenClaimClimbedBeyondAce_whenCalled_thenProgressionContinuesNotReset() throws Exception {
        // Target starts at THREE; a truthful THREE discard advances it to FOUR. A call must leave it at FOUR.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.THREE, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.THREE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1)); // truthful THREE, keeps KING

        game.callBullshit(new PlayerId(1));

        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.FOUR)));
    }

    @Test
    void givenSuitClaimMode_whenCalled_thenSuitProgressionContinuesNotReset() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withClaimMode(new CyclingSuitClaimMode())
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .build();
        // currentTarget = HEART; discarding the HEART ACE advances it to DIAMOND.
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));
        assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.DIAMOND)));

        game.callBullshit(new PlayerId(1));

        assertThat(game.getCurrentTarget(), is(new SuitTarget(FrenchSuit.DIAMOND))); // not reset to HEART
    }
```

(Imports `CyclingSuitClaimMode`, `SuitTarget`, `FrenchSuit`, `RankTarget`, `FrenchRank` are already present in the file.)

- [ ] **Step 3: Run the tests to verify the new assertions FAIL against the current reset**

Run: `"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=BullshitTest` from `backend/`

Expected: FAIL. The new/updated assertions expect TWO / FOUR / DIAMOND but the reset still produces ACE / ACE / HEART. Confirm the failures are exactly those `getCurrentTarget()` assertions (not compile errors).

- [ ] **Step 4: Remove the reset line in `callBullshit()`**

In `Bullshit.java`, the `else` branch of `callBullshit()` currently reads:

```java
        } else {
            if (claimantId.equals(pendingWinner)) {
                pendingWinner = null;
            }
            currentTarget = claimMode.initial();
            currentPlayerIndex = players.indexOf(playerById(pickerId));
        }
```

Change it to (delete the `currentTarget = claimMode.initial();` line, add a why-comment):

```java
        } else {
            if (claimantId.equals(pendingWinner)) {
                pendingWinner = null;
            }
            // The claim progression is position-independent and continues across a call: only the pile
            // and the turn move to the picker. currentTarget already holds next(challengedClaim) from
            // the discard, so it is intentionally left untouched here (issue #59).
            currentPlayerIndex = players.indexOf(playerById(pickerId));
        }
```

- [ ] **Step 5: Run BullshitTest to verify it passes**

Run: `"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=BullshitTest` from `backend/`
Expected: PASS (all BullshitTest tests green).

- [ ] **Step 6: Run the full backend suite**

Run: `"C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test` from `backend/`
Expected: BUILD SUCCESS, 0 failures. (Confirms no presentation/conformance test relied on the reset.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Bullshit.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitTest.java
git commit -m "fix(bullshit): claim target continues across a Bullshit call (#59)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Production change (remove reset, keep picker-starts, add why-comment) → Task 1 Step 4. ✓
- `ClaimMode` strategies untouched → no task touches them. ✓
- DTO/presentation/frontend unchanged → no task touches them; full-suite run (Step 6) guards regressions. ✓
- Rename + retarget the two reset-baking tests → Step 1. ✓
- Add `currentTarget` assertion to the lie+pending test → Step 1. ✓
- New climbed-position regression test → Step 2. ✓
- New suit-mode regression test → Step 2. ✓
- Full backend suite green before PR → Step 6. ✓

**Placeholder scan:** none — every code/command step shows actual content.

**Type consistency:** `getCurrentTarget()`, `RankTarget`, `SuitTarget`, `FrenchRank`, `FrenchSuit`, `CallBullshitOutcome.claimWasTruthful()/pilePicker()`, builder methods `withPlayers/withCurrentTarget/withClaimMode`, fixture `playerWithRanks` all match the existing codebase as read. `subList(0,1)` + a kept second card avoids the empty-hand win branch in both new truthful-call tests.
