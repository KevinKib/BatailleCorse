# Bullshit Core Hexagon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the card game *Bullshit* to the backend as a new, fully unit-tested game-rules hexagon (`org.kevinkib.cardgames.bullshit.domain`), after upgrading the shared `frenchcards` library to 0.2.0 and restructuring the backend into a generic multi-game package layout.

**Architecture:** Three sequential phases, **each merged as its own MR**: (A) mechanical `frenchcards` 0.1.0→0.2.0 upgrade of existing code; (0) pure package restructure to `org.kevinkib.cardgames`; (1) the new Bullshit domain — a pure aggregate reusing `frenchcards`, with a pluggable `ClaimMode` strategy so a future suit-based variant is a new implementation rather than a rewrite.

**Tech Stack:** Java 21, Maven (no wrapper — use the IntelliJ-bundled or system `mvn`; never `./mvnw`), JUnit 5 + Hamcrest (via `spring-boot-starter-test`), `frenchcards` 0.2.0 (main + test-jar). Spec: `docs/superpowers/specs/2026-06-13-bullshit-core-hexagon-design.md`.

**Verification commands** (run from repo root):
- Full suite: `mvn -f backend/pom.xml test`
- Single test: `mvn -f backend/pom.xml test -Dtest=ClassName#methodName`

---

## Phase A — Upgrade frenchcards to 0.2.0  → MR #1

> This is a mechanical API migration, not new behaviour. The **existing test suite is the safety net**: the phase is done when `mvn -f backend/pom.xml test` is fully green on 0.2.0. No new tests are written here.

### Task A1: Bump the dependency and add the test-jar

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Bump the frenchcards version and add the test-jar dependency**

In `backend/pom.xml`, change the existing `frenchcards` dependency version `0.1.0` → `0.2.0`, and add a second dependency for the test helpers (which moved to a separate test-jar in 0.2.0). The existing block is:

```xml
<dependency>
    <groupId>org.kevinkib</groupId>
    <artifactId>frenchcards</artifactId>
    <version>0.1.0</version>
</dependency>
```

Replace it with:

```xml
<dependency>
    <groupId>org.kevinkib</groupId>
    <artifactId>frenchcards</artifactId>
    <version>0.2.0</version>
</dependency>
<dependency>
    <groupId>org.kevinkib</groupId>
    <artifactId>frenchcards</artifactId>
    <version>0.2.0</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Confirm dependency resolution**

Run: `mvn -f backend/pom.xml dependency:resolve -q`
Expected: completes without "Could not find artifact org.kevinkib:frenchcards:...:0.2.0". (0.2.0 is already in the local Maven repo. If authentication errors appear, the GitHub Packages credentials in `~/.m2/settings.xml` are needed — this is environment setup, not a plan change.)

### Task A2: Migrate production code to `Visibility`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/core/domain/BatailleCorse.java:215`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/core/domain/CentralPile.java:35,43`

- [ ] **Step 1: Replace `CardHandState` in BatailleCorse**

In `BatailleCorse.java`, line ~215, replace:

```java
Deck deck = cardsService.createDeck(DeckType.FRENCH, new DeckCreationOptions(CardHandState.HIDDEN_IN_HAND));
```

with:

```java
Deck deck = cardsService.createDeck(DeckType.FRENCH, new DeckCreationOptions(Visibility.HIDDEN));
```

No import change is needed — the file already has `import org.kevinkib.cards.domain.*;`, which now resolves `Visibility`.

- [ ] **Step 2: Replace `CardPileState` in CentralPile**

In `CentralPile.java`, replace the two usages:

```java
pile.add(card, CardPileState.SHOWN);
```
→
```java
pile.add(card, Visibility.SHOWN);
```

and

```java
pile.addBelow(card, CardPileState.HIDDEN);
```
→
```java
pile.addBelow(card, Visibility.HIDDEN);
```

No import change is needed — the file already has `import org.kevinkib.cards.domain.*;`.

- [ ] **Step 3: Compile main sources**

Run: `mvn -f backend/pom.xml compile`
Expected: BUILD SUCCESS, no references to `CardHandState`/`CardPileState`/`CardState` remain.

### Task A3: Migrate the one affected test

**Files:**
- Modify: `backend/src/test/java/org/kevinkib/bataillecorse/core/domain/BatailleCorseTest.java:12,134`

- [ ] **Step 1: Remove the stale import and fix the assertion**

Delete the import line:

```java
import org.kevinkib.cards.domain.CardPileState;
```

Replace the assertion (line ~134) that used the removed `Card.getState()`:

```java
assertThat(batailleCorse.getPileTopCard().getState(), is(CardPileState.SHOWN));
```

with the 0.2.0 equivalent (binary visibility):

```java
assertThat(batailleCorse.getPileTopCard().isShown(), is(true));
```

> Note: the many `.withState(...)` calls elsewhere in the test suite refer to the *local* `CentralPileBuilder`/`CentralPileState` enum, not frenchcards — leave them unchanged.

- [ ] **Step 2: Run the full suite (the safety net for this phase)**

Run: `mvn -f backend/pom.xml test`
Expected: BUILD SUCCESS, all existing tests green. This confirms the testhelpers test-jar resolves and the `Visibility` migration is behaviour-preserving.

### Task A4: Commit and open MR #1

- [ ] **Step 1: Commit**

```bash
git add backend/pom.xml backend/src/main/java/org/kevinkib/bataillecorse/core/domain/BatailleCorse.java backend/src/main/java/org/kevinkib/bataillecorse/core/domain/CentralPile.java backend/src/test/java/org/kevinkib/bataillecorse/core/domain/BatailleCorseTest.java
git commit -m "build(deps): upgrade frenchcards to 0.2.0 and migrate to Visibility"
```

- [ ] **Step 2: Open MR #1**

Push the branch and open a merge request titled `build(deps): upgrade frenchcards to 0.2.0`. Body: summarise the breaking changes handled (CardState→Visibility, test-jar split) and that the full backend suite is green. **Stop here for review/merge before Phase 0.**

---

## Phase 0 — Restructure to `org.kevinkib.cardgames`  → MR #2

> Pure rename/move, **no logic change**. The existing suite is again the safety net. Preferred execution is IntelliJ's automated *Move/Rename Package* refactorings (which update declarations, imports, and directories atomically). The scripted procedure below is the headless-equivalent for agentic execution. Either way, the gate is a fully green suite.

**Package mapping (each applied as a literal string replacement across all `.java` files):**

| From | To |
|---|---|
| `org.kevinkib.bataillecorse.websocket.presentation.v1` | `org.kevinkib.cardgames.presentation` |
| `org.kevinkib.bataillecorse.core` | `org.kevinkib.cardgames.bataillecorse` |
| `org.kevinkib.bataillecorse.sessionmanagement` | `org.kevinkib.cardgames.sessionmanagement` |
| `org.kevinkib.bataillecorse.config` | `org.kevinkib.cardgames.config` |

(`core.domain.X` becomes `bataillecorse.domain.X` automatically under the second mapping. The four source prefixes are disjoint, so replacement order does not matter.)

### Task 0.1: Rewrite package declarations and imports

**Files:** all `*.java` under `backend/src`.

- [ ] **Step 1: Apply the four string replacements**

Run from repo root (Bash tool):

```bash
grep -rlZ "org.kevinkib.bataillecorse" backend/src --include=*.java | xargs -0 sed -i \
  -e 's#org\.kevinkib\.bataillecorse\.websocket\.presentation\.v1#org.kevinkib.cardgames.presentation#g' \
  -e 's#org\.kevinkib\.bataillecorse\.core#org.kevinkib.cardgames.bataillecorse#g' \
  -e 's#org\.kevinkib\.bataillecorse\.sessionmanagement#org.kevinkib.cardgames.sessionmanagement#g' \
  -e 's#org\.kevinkib\.bataillecorse\.config#org.kevinkib.cardgames.config#g'
```

- [ ] **Step 2: Verify no old references remain in source**

Run: `grep -rn "org.kevinkib.bataillecorse" backend/src --include=*.java || echo "CLEAN"`
Expected: `CLEAN` (no matches). Also check non-Java config: `grep -rn "org.kevinkib.bataillecorse" backend/src/main/resources || echo "CLEAN"` → expected `CLEAN` (logging config etc. references none).

### Task 0.2: Move the directories to match the new packages

**Files:** directory moves under `backend/src/main/java/org/kevinkib` and `backend/src/test/java/org/kevinkib`.

- [ ] **Step 1: Move main + test trees**

Run from repo root:

```bash
for base in backend/src/main/java/org/kevinkib backend/src/test/java/org/kevinkib; do
  mkdir -p "$base/cardgames"
  git mv "$base/bataillecorse/core"               "$base/cardgames/bataillecorse"
  git mv "$base/bataillecorse/sessionmanagement"  "$base/cardgames/sessionmanagement"
  git mv "$base/bataillecorse/config"             "$base/cardgames/config"
  git mv "$base/bataillecorse/websocket/presentation/v1" "$base/cardgames/presentation"
  rmdir "$base/bataillecorse/websocket/presentation" "$base/bataillecorse/websocket" "$base/bataillecorse" 2>/dev/null || true
done
```

This yields `org/kevinkib/cardgames/bataillecorse/domain/**`, `.../cardgames/sessionmanagement/**`, `.../cardgames/config/**`, `.../cardgames/presentation/**`. (`config` exists only in the main tree; the test-tree `git mv` for it is a harmless no-op error suppressed by the script — confirm in Step 2.)

- [ ] **Step 2: Verify the directory layout**

Run: `find backend/src -path "*org/kevinkib/bataillecorse*" -type f | head` → expected: no output (old tree gone).
Run: `find backend/src/main/java/org/kevinkib/cardgames -maxdepth 1 -type d` → expected: `bataillecorse`, `sessionmanagement`, `config`, `presentation`.

### Task 0.3: Verify the Spring context and full suite

> The `@SpringBootApplication` main class (`Application`) now lives in `org.kevinkib.cardgames.presentation`. Its default component scan covers the controllers (also in `cardgames.presentation`), and `AppConfig` is wired via `@Import` — so **no `scanBasePackages` change is required**. `ApplicationContextTest` is the proof.

- [ ] **Step 1: Compile**

Run: `mvn -f backend/pom.xml test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Run the full suite**

Run: `mvn -f backend/pom.xml test`
Expected: BUILD SUCCESS, all tests green — including `ApplicationContextTest` (context loads, proving component scan + `@Import` still resolve under the new packages).

### Task 0.4: Update architecture docs, commit, open MR #2

**Files:**
- Modify: `docs/superpowers/architecture/context-map.md`
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bataillecorse/ARCHITECTURE.md` (moved with the tree)

- [ ] **Step 1: Refresh package names in the architecture docs**

In `context-map.md`, update any `org.kevinkib.bataillecorse.*` package references to the new `org.kevinkib.cardgames.*` names (the bounded-context narrative is unchanged; only package labels move). Verify: `grep -rn "org.kevinkib.bataillecorse" docs || echo "CLEAN"` → `CLEAN`.

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: restructure backend into org.kevinkib.cardgames multi-game layout"
```

- [ ] **Step 3: Open MR #2**

Push and open a merge request titled `refactor: restructure to org.kevinkib.cardgames`. Body: explain the move (each game = its own bounded context owning domain + presentation; shared transport plumbing at top-level `presentation`), note it is a pure rename with the full suite green, and that the `bataillecorse-backend` Maven artifactId is intentionally unchanged. **Stop here for review/merge before Phase 1.**

---

## Phase 1 — Bullshit domain  → MR #3

> TDD throughout. New package only — touches no existing code. Package: `org.kevinkib.cardgames.bullshit.domain` (main + test). Reuses `frenchcards` 0.2.0 and its testhelpers test-jar.

**File structure (all under `backend/src/.../org/kevinkib/cardgames/bullshit/domain/`):**

| File | Responsibility |
|---|---|
| `Action.java` | enum: `DISCARD`, `CALL_BULLSHIT` |
| `BullshitId.java` | aggregate id (UUID) |
| `PlayerId.java` | seat id |
| `ClaimTarget.java` | sealed interface — what a turn claims |
| `RankTarget.java` | rank-based claim target |
| `ClaimMode.java` | strategy: initial / next / matches |
| `AscendingRankClaimMode.java` | the only Slice-1 mode (A→2→…→K→A) |
| `DiscardPile.java` | the face-down stack of played cards |
| `Discard.java` | record of one play (claimant, target, actual cards) |
| `Result.java` | winner / ongoing |
| `CallBullshitOutcome.java` | result of a call (truthful?, who took the pile) |
| `Player.java` | a seat + its `Hand` |
| `Bullshit.java` | aggregate root |
| 5 exception classes | `FinishedGameException`, `NotPlayersTurnException`, `InvalidDiscardCountException`, `CardsNotInHandException`, `CannotCallBullshitException` |
| *(test)* `PlayerBuilder`, `BullshitBuilder`, `BullshitFixtures` | test data |
| *(test)* `AscendingRankClaimModeTest`, `DiscardPileTest`, `PlayerTest`, `BullshitTest` | behaviour coverage |

### Task 1.1: `Action`, `BullshitId`, `PlayerId`

**Files:**
- Create: `.../bullshit/domain/Action.java`
- Create: `.../bullshit/domain/BullshitId.java`
- Create: `.../bullshit/domain/PlayerId.java`
- Test: `.../bullshit/domain/BullshitIdTest.java`

- [ ] **Step 1: Write the failing test**

`BullshitIdTest.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class BullshitIdTest {

    @Test
    void givenUuidString_whenConstructed_thenRoundTrips() {
        UUID uuid = UUID.randomUUID();
        assertThat(new BullshitId(uuid.toString()).uuid(), is(uuid));
    }

    @Test
    void whenGenerated_thenHasUuid() {
        assertThat(BullshitId.generate().uuid(), notNullValue());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -f backend/pom.xml test -Dtest=BullshitIdTest`
Expected: FAIL — `BullshitId` does not exist.

- [ ] **Step 3: Create the three types**

`Action.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public enum Action {
    DISCARD,
    CALL_BULLSHIT;
}
```

`BullshitId.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import java.util.UUID;

public record BullshitId(UUID uuid) {

    public BullshitId(String id) {
        this(UUID.fromString(id));
    }

    public static BullshitId generate() {
        return new BullshitId(UUID.randomUUID());
    }
}
```

`PlayerId.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public record PlayerId(Integer id) {

    @Override
    public String toString() {
        return id.toString();
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -f backend/pom.xml test -Dtest=BullshitIdTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Action.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/BullshitId.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/PlayerId.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitIdTest.java
git commit -m "feat(bullshit): add Action, BullshitId, PlayerId value types"
```

### Task 1.2: `ClaimMode` / `ClaimTarget` / `AscendingRankClaimMode`

**Files:**
- Create: `.../bullshit/domain/ClaimTarget.java`
- Create: `.../bullshit/domain/RankTarget.java`
- Create: `.../bullshit/domain/ClaimMode.java`
- Create: `.../bullshit/domain/AscendingRankClaimMode.java`
- Test: `.../bullshit/domain/AscendingRankClaimModeTest.java`

- [ ] **Step 1: Write the failing test**

`AscendingRankClaimModeTest.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

class AscendingRankClaimModeTest {

    private final AscendingRankClaimMode mode = new AscendingRankClaimMode();

    @Test
    void givenNewGame_whenInitial_thenAce() {
        assertThat(mode.initial(), is(new RankTarget(FrenchRank.ACE)));
    }

    @Test
    void givenAce_whenNext_thenTwo() {
        assertThat(mode.next(new RankTarget(FrenchRank.ACE)), is(new RankTarget(FrenchRank.TWO)));
    }

    @Test
    void givenKing_whenNext_thenWrapsToAce() {
        assertThat(mode.next(new RankTarget(FrenchRank.KING)), is(new RankTarget(FrenchRank.ACE)));
    }

    @Test
    void givenAllCardsMatchTarget_whenMatches_thenTrue() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.HEART).build(),
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.CLUB).build());
        assertThat(mode.matches(cards, new RankTarget(FrenchRank.SEVEN)), is(true));
    }

    @Test
    void givenAnyCardOffTarget_whenMatches_thenFalse() {
        List<Card> cards = List.of(
                aCard().withRank(FrenchRank.SEVEN).withSuit(FrenchSuit.HEART).build(),
                aCard().withRank(FrenchRank.EIGHT).withSuit(FrenchSuit.CLUB).build());
        assertThat(mode.matches(cards, new RankTarget(FrenchRank.SEVEN)), is(false));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -f backend/pom.xml test -Dtest=AscendingRankClaimModeTest`
Expected: FAIL — types do not exist.

- [ ] **Step 3: Create the strategy types**

`ClaimTarget.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public sealed interface ClaimTarget permits RankTarget {
    String label();
}
```

`RankTarget.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.deck.french.FrenchRank;

public record RankTarget(FrenchRank rank) implements ClaimTarget {

    @Override
    public String label() {
        return rank.toString();
    }
}
```

`ClaimMode.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;

import java.util.List;

public interface ClaimMode {

    ClaimTarget initial();

    ClaimTarget next(ClaimTarget current);

    boolean matches(List<Card> cards, ClaimTarget target);
}
```

`AscendingRankClaimMode.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

public class AscendingRankClaimMode implements ClaimMode {

    private static final List<FrenchRank> ORDER = List.of(
            FrenchRank.ACE, FrenchRank.TWO, FrenchRank.THREE, FrenchRank.FOUR,
            FrenchRank.FIVE, FrenchRank.SIX, FrenchRank.SEVEN, FrenchRank.EIGHT,
            FrenchRank.NINE, FrenchRank.TEN, FrenchRank.JACK, FrenchRank.QUEEN, FrenchRank.KING);

    @Override
    public ClaimTarget initial() {
        return new RankTarget(FrenchRank.ACE);
    }

    @Override
    public ClaimTarget next(ClaimTarget current) {
        FrenchRank rank = ((RankTarget) current).rank();
        int nextIndex = (ORDER.indexOf(rank) + 1) % ORDER.size();
        return new RankTarget(ORDER.get(nextIndex));
    }

    @Override
    public boolean matches(List<Card> cards, ClaimTarget target) {
        FrenchRank expected = ((RankTarget) target).rank();
        return cards.stream().allMatch(card -> card.getRank() == expected);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -f backend/pom.xml test -Dtest=AscendingRankClaimModeTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/ClaimTarget.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/RankTarget.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/ClaimMode.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/AscendingRankClaimMode.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/AscendingRankClaimModeTest.java
git commit -m "feat(bullshit): add ClaimMode strategy with ascending-rank implementation"
```

### Task 1.3: `DiscardPile`

**Files:**
- Create: `.../bullshit/domain/DiscardPile.java`
- Test: `.../bullshit/domain/DiscardPileTest.java`

- [ ] **Step 1: Write the failing test**

`DiscardPileTest.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cards.testhelpers.CardBuilder.aCard;

class DiscardPileTest {

    private final Card aceSpade = aCard().withRank(FrenchRank.ACE).withSuit(FrenchSuit.SPADE).build();
    private final Card twoHeart = aCard().withRank(FrenchRank.TWO).withSuit(FrenchSuit.HEART).build();

    @Test
    void givenNewPile_thenEmpty() {
        DiscardPile pile = new DiscardPile();
        assertThat(pile.isEmpty(), is(true));
        assertThat(pile.size(), is(0));
    }

    @Test
    void whenCardsAdded_thenSizeGrows() {
        DiscardPile pile = new DiscardPile();
        pile.add(List.of(aceSpade));
        pile.add(List.of(twoHeart));
        assertThat(pile.size(), is(2));
        assertThat(pile.isEmpty(), is(false));
    }

    @Test
    void whenTakeAll_thenReturnsEverythingAndEmpties() {
        DiscardPile pile = new DiscardPile();
        pile.add(List.of(aceSpade, twoHeart));

        List<Card> taken = pile.takeAll();

        assertThat(taken, contains(aceSpade, twoHeart));
        assertThat(pile.isEmpty(), is(true));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -f backend/pom.xml test -Dtest=DiscardPileTest`
Expected: FAIL — `DiscardPile` does not exist.

- [ ] **Step 3: Implement**

`DiscardPile.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;

import java.util.ArrayList;
import java.util.List;

public class DiscardPile {

    private final List<Card> cards = new ArrayList<>();

    public void add(List<Card> newCards) {
        cards.addAll(newCards);
    }

    public List<Card> takeAll() {
        List<Card> taken = new ArrayList<>(cards);
        cards.clear();
        return taken;
    }

    public int size() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public List<Card> getCards() {
        return new ArrayList<>(cards);
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -f backend/pom.xml test -Dtest=DiscardPileTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/DiscardPile.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/DiscardPileTest.java
git commit -m "feat(bullshit): add DiscardPile"
```

### Task 1.4: `Discard`, `Result`, `CallBullshitOutcome` records + exceptions

**Files:**
- Create: `.../bullshit/domain/Discard.java`
- Create: `.../bullshit/domain/Result.java`
- Create: `.../bullshit/domain/CallBullshitOutcome.java`
- Create: `.../bullshit/domain/FinishedGameException.java`
- Create: `.../bullshit/domain/NotPlayersTurnException.java`
- Create: `.../bullshit/domain/InvalidDiscardCountException.java`
- Create: `.../bullshit/domain/CardsNotInHandException.java`
- Create: `.../bullshit/domain/CannotCallBullshitException.java`

> These are simple types with no branching logic; they are exercised by the aggregate tests in Tasks 1.6–1.9. Create them directly.

- [ ] **Step 1: Create the records**

`Discard.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;

import java.util.List;

public record Discard(PlayerId claimant, ClaimTarget claimedTarget, List<Card> actualCards) {
}
```

`Result.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public record Result(Player winningPlayer) {

    public static final Result ONGOING = new Result(null);

    public boolean isFinished() {
        return winningPlayer != null;
    }
}
```

`CallBullshitOutcome.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public record CallBullshitOutcome(boolean claimWasTruthful, PlayerId pilePicker) {
}
```

- [ ] **Step 2: Create the exceptions**

`FinishedGameException.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public class FinishedGameException extends Exception {
}
```

`NotPlayersTurnException.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public class NotPlayersTurnException extends Exception {

    public NotPlayersTurnException(PlayerId playerId) {
        super("Not the turn of player " + playerId);
    }
}
```

`InvalidDiscardCountException.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public class InvalidDiscardCountException extends Exception {

    public InvalidDiscardCountException(int count) {
        super("A discard must contain 1 to 4 cards, got " + count);
    }
}
```

`CardsNotInHandException.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public class CardsNotInHandException extends Exception {

    public CardsNotInHandException(PlayerId playerId) {
        super("Player " + playerId + " does not hold all of the discarded cards");
    }
}
```

`CannotCallBullshitException.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

public class CannotCallBullshitException extends Exception {

    public CannotCallBullshitException(PlayerId playerId) {
        super("Player " + playerId + " cannot call bullshit now");
    }
}
```

- [ ] **Step 3: Compile**

Run: `mvn -f backend/pom.xml test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Discard.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Result.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/CallBullshitOutcome.java backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/*Exception.java
git commit -m "feat(bullshit): add Discard, Result, CallBullshitOutcome and domain exceptions"
```

### Task 1.5: `Player`

**Files:**
- Create: `.../bullshit/domain/Player.java`
- Test (main): `.../bullshit/domain/PlayerTest.java`
- Test helper: `.../bullshit/domain/PlayerBuilder.java`

- [ ] **Step 1: Create the test builder**

`PlayerBuilder.java` (test sources):

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;
import org.kevinkib.cards.domain.deck.french.FrenchSuit;
import org.kevinkib.cards.domain.hand.Hand;
import org.kevinkib.cards.testhelpers.CardBuilder;
import org.kevinkib.cards.testhelpers.HandBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PlayerBuilder {

    private int id = 0;
    private Hand hand = HandBuilder.aHand().withNoCards().build();

    public static PlayerBuilder aPlayer() {
        return new PlayerBuilder();
    }

    public PlayerBuilder withId(int id) {
        this.id = id;
        return this;
    }

    public PlayerBuilder withHand(Hand hand) {
        this.hand = hand;
        return this;
    }

    public PlayerBuilder withEmptyHand() {
        this.hand = HandBuilder.aHand().withNoCards().build();
        return this;
    }

    /** Builds a hand from ranks, assigning distinct suits per repeated rank (up to 4). */
    public PlayerBuilder withRanks(FrenchRank... ranks) {
        List<FrenchSuit> suits = FrenchSuit.getSuits();
        Map<FrenchRank, Integer> seen = new HashMap<>();
        List<Card> cards = new ArrayList<>();
        for (FrenchRank rank : ranks) {
            int n = seen.merge(rank, 1, Integer::sum) - 1;
            cards.add(CardBuilder.aCard().withRank(rank).withSuit(suits.get(n % suits.size())).build());
        }
        this.hand = HandBuilder.aHand().withCards(cards).build();
        return this;
    }

    public Player build() {
        return new Player(id, hand);
    }
}
```

- [ ] **Step 2: Write the failing test**

`PlayerTest.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class PlayerTest {

    @Test
    void givenChosenCards_whenDiscarded_thenRemovedFromHand() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE, FrenchRank.ACE, FrenchRank.KING).build();
        List<Card> toDiscard = player.getCards().subList(0, 2);

        player.discard(toDiscard);

        assertThat(player.handSize(), is(1));
        assertThat(player.hasAnyCards(), is(true));
    }

    @Test
    void givenLastCardsDiscarded_thenHandEmpty() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE).build();

        player.discard(player.getCards());

        assertThat(player.hasAnyCards(), is(false));
    }

    @Test
    void givenCardsAddedFromPile_thenHandGrows() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE).build();
        Player other = PlayerBuilder.aPlayer().withRanks(FrenchRank.TWO, FrenchRank.THREE).build();

        player.addCards(other.getCards());

        assertThat(player.handSize(), is(3));
    }

    @Test
    void givenCardNotHeld_whenPossessesAll_thenFalse() {
        Player player = PlayerBuilder.aPlayer().withRanks(FrenchRank.ACE).build();
        Player other = PlayerBuilder.aPlayer().withRanks(FrenchRank.TWO).build();

        assertThat(player.possessesAll(other.getCards()), is(false));
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mvn -f backend/pom.xml test -Dtest=PlayerTest`
Expected: FAIL — `Player` does not exist / has no such methods.

- [ ] **Step 4: Implement `Player`**

`Player.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.hand.CannotPlayNonPossessedCardException;
import org.kevinkib.cards.domain.hand.Hand;

import java.util.List;
import java.util.Objects;

public record Player(PlayerId id, Hand hand) {

    public Player(Integer id, Hand hand) {
        this(new PlayerId(id), hand);
    }

    public boolean possessesAll(List<Card> cards) {
        return cards.stream().allMatch(hand::possesses);
    }

    public void discard(List<Card> cards) {
        for (Card card : cards) {
            try {
                hand.play(card);
            } catch (CannotPlayNonPossessedCardException e) {
                throw new IllegalStateException("Card not in hand after possession check: " + card, e);
            }
        }
    }

    public void addCards(List<Card> cards) {
        hand.add(cards);
    }

    public List<Card> getCards() {
        return hand.getCards();
    }

    public int handSize() {
        return hand.getSize();
    }

    public boolean hasAnyCards() {
        return hand.hasAnyCards();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Player) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -f backend/pom.xml test -Dtest=PlayerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Player.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/PlayerBuilder.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/PlayerTest.java
git commit -m "feat(bullshit): add Player with chosen-card discard"
```

### Task 1.6: `Bullshit` aggregate — construction, dealing, getters, available actions

**Files:**
- Create: `.../bullshit/domain/Bullshit.java`
- Test helper: `.../bullshit/domain/BullshitBuilder.java`
- Test helper: `.../bullshit/domain/BullshitFixtures.java`
- Test: `.../bullshit/domain/BullshitTest.java`

- [ ] **Step 1: Create the test builder and fixtures**

`BullshitBuilder.java` (test sources):

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.deck.french.FrenchRank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BullshitBuilder {

    private BullshitId id = BullshitId.generate();
    private List<Player> players = new ArrayList<>();
    private ClaimMode claimMode = new AscendingRankClaimMode();
    private ClaimTarget currentTarget = null;
    private int currentPlayerIndex = 0;

    public static BullshitBuilder aBullshit() {
        return new BullshitBuilder();
    }

    public BullshitBuilder withPlayers(Player... players) {
        this.players = new ArrayList<>(Arrays.asList(players));
        return this;
    }

    public BullshitBuilder withPlayers(List<Player> players) {
        this.players = new ArrayList<>(players);
        return this;
    }

    public BullshitBuilder withClaimMode(ClaimMode claimMode) {
        this.claimMode = claimMode;
        return this;
    }

    public BullshitBuilder withCurrentTarget(FrenchRank rank) {
        this.currentTarget = new RankTarget(rank);
        return this;
    }

    public BullshitBuilder withCurrentPlayerIndex(int index) {
        this.currentPlayerIndex = index;
        return this;
    }

    public Bullshit build() {
        ClaimTarget target = currentTarget != null ? currentTarget : claimMode.initial();
        return new Bullshit(id, players, claimMode, target, currentPlayerIndex);
    }
}
```

`BullshitFixtures.java` (test sources):

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.domain.deck.french.FrenchRank;

public final class BullshitFixtures {

    public static Player playerWithRanks(int id, FrenchRank... ranks) {
        return PlayerBuilder.aPlayer().withId(id).withRanks(ranks).build();
    }

    public static Player emptyPlayer(int id) {
        return PlayerBuilder.aPlayer().withId(id).withEmptyHand().build();
    }
}
```

- [ ] **Step 2: Write the failing test**

`BullshitTest.java` (initial cut — construction + actions):

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.junit.jupiter.api.Test;
import org.kevinkib.cards.domain.deck.french.FrenchRank;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.kevinkib.cardgames.bullshit.domain.BullshitFixtures.playerWithRanks;

class BullshitTest {

    @Test
    void givenFreshGame_thenWholeDeckDealtAndAceClaimedFirst() {
        Bullshit game = new Bullshit(BullshitId.generate(), 4);

        int totalCards = game.getPlayers().stream().mapToInt(Player::handSize).sum();
        assertThat(totalCards, is(52));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
        assertThat(game.getCurrentPlayerIndex(), is(0));
        assertThat(game.isFinished(), is(false));
    }

    @Test
    void givenThreePlayers_thenDealtUnevenlyWithoutError() {
        Bullshit game = new Bullshit(BullshitId.generate(), 3);
        int totalCards = game.getPlayers().stream().mapToInt(Player::handSize).sum();
        assertThat(totalCards, is(52));
        assertThat(game.getPlayers(), hasSize(3));
    }

    @Test
    void givenNoDiscardYet_whenCurrentPlayerActions_thenOnlyDiscard() {
        Bullshit game = aThreePlayerGame();
        assertThat(game.getAvailableActions(new PlayerId(0)), contains(Action.DISCARD));
    }

    @Test
    void givenNoDiscardYet_whenOtherPlayerActions_thenNone() {
        Bullshit game = aThreePlayerGame();
        assertThat(game.getAvailableActions(new PlayerId(1)), hasSize(0));
    }

    private Bullshit aThreePlayerGame() {
        return BullshitBuilder.aBullshit()
                .withPlayers(
                        playerWithRanks(0, FrenchRank.ACE),
                        playerWithRanks(1, FrenchRank.TWO),
                        playerWithRanks(2, FrenchRank.THREE))
                .build();
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mvn -f backend/pom.xml test -Dtest=BullshitTest`
Expected: FAIL — `Bullshit` does not exist.

- [ ] **Step 4: Implement the aggregate (full file — discard/callBullshit/forfeit included so later tasks only add tests)**

`Bullshit.java`:

```java
package org.kevinkib.cardgames.bullshit.domain;

import org.kevinkib.cards.CardsService;
import org.kevinkib.cards.domain.Card;
import org.kevinkib.cards.domain.Visibility;
import org.kevinkib.cards.domain.deck.Deck;
import org.kevinkib.cards.domain.deck.DeckCreationOptions;
import org.kevinkib.cards.domain.deck.DeckType;
import org.kevinkib.cards.domain.hand.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Bullshit {

    private final BullshitId id;
    private final List<Player> players;
    private final ClaimMode claimMode;
    private final DiscardPile discardPile;
    private ClaimTarget currentTarget;
    private int currentPlayerIndex;
    private Discard lastDiscard;
    private PlayerId pendingWinner;
    private Result result;

    public Bullshit(BullshitId id, int nbPlayers) {
        this(id, nbPlayers, new AscendingRankClaimMode());
    }

    public Bullshit(BullshitId id, int nbPlayers, ClaimMode claimMode) {
        this(id, deal(nbPlayers), claimMode, claimMode.initial(), 0);
    }

    Bullshit(BullshitId id, List<Player> players, ClaimMode claimMode, ClaimTarget currentTarget, int currentPlayerIndex) {
        this.id = id;
        this.players = new ArrayList<>(players);
        this.claimMode = claimMode;
        this.currentTarget = currentTarget;
        this.currentPlayerIndex = currentPlayerIndex;
        this.discardPile = new DiscardPile();
        this.lastDiscard = null;
        this.pendingWinner = null;
        this.result = Result.ONGOING;
    }

    private static List<Player> deal(int nbPlayers) {
        Deck deck = new CardsService().createDeck(DeckType.FRENCH, new DeckCreationOptions(Visibility.HIDDEN));
        List<Hand> hands = deck.distributeAll(nbPlayers);
        List<Player> dealt = new ArrayList<>();
        for (int i = 0; i < nbPlayers; i++) {
            dealt.add(new Player(i, hands.get(i)));
        }
        return dealt;
    }

    public synchronized void discard(PlayerId playerId, List<Card> cards)
            throws FinishedGameException, NotPlayersTurnException, InvalidDiscardCountException, CardsNotInHandException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (!currentPlayer().id().equals(playerId)) {
            throw new NotPlayersTurnException(playerId);
        }
        if (pendingWinner != null) {
            // The next player plays on instead of calling BS: the unchallenged claim stands and wins.
            result = new Result(playerById(pendingWinner));
            return;
        }
        if (cards.isEmpty() || cards.size() > 4) {
            throw new InvalidDiscardCountException(cards.size());
        }
        Player player = currentPlayer();
        if (!player.possessesAll(cards)) {
            throw new CardsNotInHandException(playerId);
        }

        player.discard(cards);
        discardPile.add(cards);
        lastDiscard = new Discard(playerId, currentTarget, List.copyOf(cards));
        currentTarget = claimMode.next(currentTarget);
        advanceTurn();

        if (!player.hasAnyCards()) {
            pendingWinner = playerId;
        }
    }

    public synchronized CallBullshitOutcome callBullshit(PlayerId callerId)
            throws FinishedGameException, CannotCallBullshitException {
        if (isFinished()) {
            throw new FinishedGameException();
        }
        if (lastDiscard == null || callerId.equals(lastDiscard.claimant())) {
            throw new CannotCallBullshitException(callerId);
        }

        boolean truthful = claimMode.matches(lastDiscard.actualCards(), lastDiscard.claimedTarget());
        PlayerId claimantId = lastDiscard.claimant();
        PlayerId pickerId = truthful ? callerId : claimantId;

        playerById(pickerId).addCards(discardPile.takeAll());

        if (truthful && claimantId.equals(pendingWinner)) {
            result = new Result(playerById(claimantId));
        } else {
            if (claimantId.equals(pendingWinner)) {
                pendingWinner = null;
            }
            currentTarget = claimMode.initial();
            currentPlayerIndex = players.indexOf(playerById(pickerId));
        }
        lastDiscard = null;

        return new CallBullshitOutcome(truthful, pickerId);
    }

    public synchronized void forfeit(PlayerId playerId) {
        if (isFinished()) {
            return;
        }
        int index = indexOf(playerId);
        if (index < 0) {
            return;
        }
        if (playerId.equals(pendingWinner)) {
            pendingWinner = null;
        }
        players.remove(index);
        if (currentPlayerIndex >= players.size()) {
            currentPlayerIndex = 0;
        }
        if (players.size() == 1) {
            result = new Result(players.get(0));
        }
    }

    public List<Action> getAvailableActions(PlayerId playerId) {
        List<Action> actions = new ArrayList<>();
        if (isFinished()) {
            return actions;
        }
        if (currentPlayer().id().equals(playerId)) {
            actions.add(Action.DISCARD);
        }
        if (lastDiscard != null && !playerId.equals(lastDiscard.claimant())) {
            actions.add(Action.CALL_BULLSHIT);
        }
        return actions;
    }

    private void advanceTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    }

    private Player currentPlayer() {
        return players.get(currentPlayerIndex);
    }

    private Player playerById(PlayerId playerId) {
        return players.stream()
                .filter(player -> player.id().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown player " + playerId));
    }

    private int indexOf(PlayerId playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(playerId)) {
                return i;
            }
        }
        return -1;
    }

    public BullshitId getId() {
        return id;
    }

    public List<Player> getPlayers() {
        return new ArrayList<>(players);
    }

    public Player getCurrentPlayer() {
        return currentPlayer();
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public ClaimTarget getCurrentTarget() {
        return currentTarget;
    }

    public Optional<Discard> getLastDiscard() {
        return Optional.ofNullable(lastDiscard);
    }

    public int getDiscardPileSize() {
        return discardPile.size();
    }

    public boolean isFinished() {
        return result.isFinished();
    }

    public Player getWinner() {
        return result.winningPlayer();
    }

    public Optional<PlayerId> getPendingWinner() {
        return Optional.ofNullable(pendingWinner);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Bullshit) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -f backend/pom.xml test -Dtest=BullshitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/domain/Bullshit.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitBuilder.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitFixtures.java backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitTest.java
git commit -m "feat(bullshit): add Bullshit aggregate (construction, dealing, actions)"
```

### Task 1.7: `discard` behaviour and guards

**Files:**
- Modify: `.../bullshit/domain/BullshitTest.java` (add cases)

- [ ] **Step 1: Add failing tests**

Append to `BullshitTest.java`:

```java
    @org.junit.jupiter.api.Test
    void givenValidDiscard_thenCardsLeaveHandAndPileGrowsAndTurnAdvances() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        Player p0 = game.getPlayers().get(0);

        game.discard(new PlayerId(0), p0.getCards());

        assertThat(game.getDiscardPileSize(), is(2));
        assertThat(game.getCurrentPlayerIndex(), is(1));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.TWO)));
        assertThat(game.getLastDiscard().isPresent(), is(true));
    }

    @org.junit.jupiter.api.Test
    void givenNotYourTurn_whenDiscard_thenThrows() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        org.junit.jupiter.api.Assertions.assertThrows(NotPlayersTurnException.class,
                () -> game.discard(new PlayerId(1), game.getPlayers().get(1).getCards()));
    }

    @org.junit.jupiter.api.Test
    void givenFiveCards_whenDiscard_thenInvalidCount() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.ACE, FrenchRank.KING),
                        playerWithRanks(1, FrenchRank.TWO))
                .build();
        org.junit.jupiter.api.Assertions.assertThrows(InvalidDiscardCountException.class,
                () -> game.discard(new PlayerId(0), game.getPlayers().get(0).getCards()));
    }

    @org.junit.jupiter.api.Test
    void givenCardsNotHeld_whenDiscard_thenThrows() {
        Player p0 = playerWithRanks(0, FrenchRank.ACE);
        Player p1 = playerWithRanks(1, FrenchRank.TWO);
        Bullshit game = BullshitBuilder.aBullshit().withPlayers(p0, p1).build();

        org.junit.jupiter.api.Assertions.assertThrows(CardsNotInHandException.class,
                () -> game.discard(new PlayerId(0), p1.getCards()));
    }
```

- [ ] **Step 2: Run**

Run: `mvn -f backend/pom.xml test -Dtest=BullshitTest`
Expected: PASS (the aggregate from Task 1.6 already implements `discard`). If any fail, fix the aggregate, not the tests.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitTest.java
git commit -m "test(bullshit): cover discard behaviour and guards"
```

### Task 1.8: `callBullshit` resolution (lie / truthful / win)

**Files:**
- Modify: `.../bullshit/domain/BullshitTest.java`

- [ ] **Step 1: Add failing tests**

Append to `BullshitTest.java`:

```java
    @org.junit.jupiter.api.Test
    void givenLie_whenCalled_thenLiarTakesPileAndRoundResets() throws Exception {
        // p0 must claim ACE but actually holds a KING -> a lie.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards());

        CallBullshitOutcome outcome = game.callBullshit(new PlayerId(1));

        assertThat(outcome.claimWasTruthful(), is(false));
        assertThat(outcome.pilePicker(), is(new PlayerId(0)));
        assertThat(game.getDiscardPileSize(), is(0));
        assertThat(game.getCurrentTarget(), is(new RankTarget(FrenchRank.ACE)));
        assertThat(game.getCurrentPlayerIndex(), is(0));            // liar starts next round
        assertThat(game.getPlayers().get(0).handSize(), is(1));     // took the pile back
    }

    @org.junit.jupiter.api.Test
    void givenTruthfulClaim_whenCalled_thenCallerTakesPile() throws Exception {
        // p0 keeps a KING so it does NOT empty its hand (avoids the win branch);
        // it discards only the ACE, truthfully claiming ACE.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.KING), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));

        CallBullshitOutcome outcome = game.callBullshit(new PlayerId(1));

        assertThat(outcome.claimWasTruthful(), is(true));
        assertThat(outcome.pilePicker(), is(new PlayerId(1)));
        assertThat(game.isFinished(), is(false));
        assertThat(game.getCurrentPlayerIndex(), is(1));            // caller starts next round
        assertThat(game.getPlayers().get(1).handSize(), is(2));     // had 1, took the 1-card pile
    }

    @org.junit.jupiter.api.Test
    void givenTruthfulFinalDiscard_whenCalled_thenClaimantWins() throws Exception {
        // p0's only card is an ACE, claimed as ACE -> empties hand truthfully.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards());

        game.callBullshit(new PlayerId(1));

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(0)));
    }

    @org.junit.jupiter.api.Test
    void givenOwnDiscard_whenCallBullshit_thenThrows() throws Exception {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards().subList(0, 1));

        org.junit.jupiter.api.Assertions.assertThrows(CannotCallBullshitException.class,
                () -> game.callBullshit(new PlayerId(0)));
    }

    @org.junit.jupiter.api.Test
    void givenNoDiscardYet_whenCallBullshit_thenThrows() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        org.junit.jupiter.api.Assertions.assertThrows(CannotCallBullshitException.class,
                () -> game.callBullshit(new PlayerId(1)));
    }
```

- [ ] **Step 2: Run**

Run: `mvn -f backend/pom.xml test -Dtest=BullshitTest`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitTest.java
git commit -m "test(bullshit): cover call-bullshit resolution and winning"
```

### Task 1.9: Unchallenged-bluff win (decline path) and `forfeit`

**Files:**
- Modify: `.../bullshit/domain/BullshitTest.java`

- [ ] **Step 1: Add failing tests**

Append to `BullshitTest.java`:

```java
    @org.junit.jupiter.api.Test
    void givenEmptyHandThenNextPlayerPlaysOn_thenBluffStandsAndWins() throws Exception {
        // p0 empties hand (pendingWinner); p1 declines to call by discarding -> p0 wins.
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .withCurrentTarget(FrenchRank.ACE)
                .build();
        game.discard(new PlayerId(0), game.getPlayers().get(0).getCards());
        assertThat(game.getPendingWinner().isPresent(), is(true));

        game.discard(new PlayerId(1), game.getPlayers().get(1).getCards());

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(0)));
    }

    @org.junit.jupiter.api.Test
    void givenThreePlayers_whenOneForfeits_thenRemovedFromRotation() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO), playerWithRanks(2, FrenchRank.THREE))
                .build();

        game.forfeit(new PlayerId(1));

        assertThat(game.getPlayers(), hasSize(2));
        assertThat(game.isFinished(), is(false));
    }

    @org.junit.jupiter.api.Test
    void givenTwoPlayers_whenOneForfeits_thenOtherWins() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();

        game.forfeit(new PlayerId(0));

        assertThat(game.isFinished(), is(true));
        assertThat(game.getWinner().id(), is(new PlayerId(1)));
    }

    @org.junit.jupiter.api.Test
    void givenFinishedGame_whenForfeit_thenNoOp() {
        Bullshit game = BullshitBuilder.aBullshit()
                .withPlayers(playerWithRanks(0, FrenchRank.ACE), playerWithRanks(1, FrenchRank.TWO))
                .build();
        game.forfeit(new PlayerId(0)); // p1 wins
        Player winnerBefore = game.getWinner();

        game.forfeit(new PlayerId(1)); // already finished -> ignored

        assertThat(game.getWinner(), is(winnerBefore));
    }
```

- [ ] **Step 2: Run**

Run: `mvn -f backend/pom.xml test -Dtest=BullshitTest`
Expected: PASS.

- [ ] **Step 3: Run the whole Bullshit package**

Run: `mvn -f backend/pom.xml test -Dtest="org.kevinkib.cardgames.bullshit.domain.*"`
Expected: PASS — all Bullshit domain tests green.

- [ ] **Step 4: Commit**

```bash
git add backend/src/test/java/org/kevinkib/cardgames/bullshit/domain/BullshitTest.java
git commit -m "test(bullshit): cover unchallenged-bluff win and forfeit"
```

### Task 1.10: Full-suite gate, docs, and MR #3

**Files:**
- Modify: `docs/superpowers/architecture/context-map.md` (add the Bullshit Core context)

- [ ] **Step 1: Full backend suite**

Run: `mvn -f backend/pom.xml test`
Expected: BUILD SUCCESS — Bullshit domain plus all pre-existing tests green.

- [ ] **Step 2: Document the new bounded context**

In `context-map.md`, add a short note that `org.kevinkib.cardgames.bullshit.domain` is a second upstream Core/game bounded context (sibling to `bataillecorse`), depending only on `frenchcards`, with no session/presentation wiring yet (Slice 2).

- [ ] **Step 3: Commit and open MR #3**

```bash
git add docs/superpowers/architecture/context-map.md
git commit -m "docs: record Bullshit core bounded context"
```

Push and open a merge request titled `feat(bullshit): add Bullshit game-rules core hexagon`. Body: summarise the new domain (aggregate, pluggable `ClaimMode`, N-player support, call-BS race, win/forfeit) and that it is pure domain with full unit coverage and no transport. **This completes the plan.**

---

## Self-review notes

- **Spec coverage:** Slice A (pom + 3 files) → Phase A; Slice 0 restructure → Phase 0; Slice 1 domain (aggregate, `ClaimMode`/`ClaimTarget`/`AscendingRankClaimMode`, `DiscardPile`, `Discard`, `Result`, `Player`, exceptions, builders/fixtures, full coverage incl. ascending+wrap, discard guards, lie/truthful resolution, decline-win, forfeit) → Phase 1. House rules #1/#2 verified in Task 1.8; wrap-around in Task 1.2.
- **No suit `ClaimMode`, no session/WS/frontend, no AI** — out of scope per spec; the `ClaimMode` seam leaves the suit variant a pure add.
- **Type consistency:** `ClaimTarget`/`RankTarget`, `Discard(claimant, claimedTarget, actualCards)`, `CallBullshitOutcome(claimWasTruthful, pilePicker)`, `Player.discard/possessesAll/addCards/getCards/handSize/hasAnyCards`, and the `Bullshit` public surface (`getId/getPlayers/getCurrentPlayer/getCurrentPlayerIndex/getCurrentTarget/getLastDiscard/getDiscardPileSize/isFinished/getWinner/getPendingWinner/getAvailableActions/discard/callBullshit/forfeit`) are used consistently across tasks.
- **frenchcards 0.2.0 APIs used:** `Visibility.HIDDEN`, `CardsService().createDeck(...)`, `deck.distributeAll(n)`, `Hand.play/possesses/add/getCards/getSize/hasAnyCards`, testhelpers `CardBuilder`/`HandBuilder` from the test-jar.
