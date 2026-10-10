# Kobo Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Kobo as a playable game on the backend: a pure `kobo/domain` aggregate (rounds, turns, powers, matching-discard race, scoring, forfeit), plugged into the existing session core, lobby, presence and play-again, with per-seat state DTOs and a token-authenticated STOMP/REST API.

**Architecture:** `Kobo implements Game` is a `synchronized` aggregate with an explicit phase machine; cards are addressed by `SlotRef(seat, slot)` with a per-slot `revision` (the server never sees what players remember). A `KoboFactory` joins `GameFactories`. The presentation layer mirrors Bullshit: `KoboGameActions` (domain call, touch, one broadcast inside `synchronized (game)`), `KoboStateBroadcaster` (one `KoboDto` per seat on the token-addressed topic), `KoboWebSocketController`, `KoboRestController`, `KoboLifecycleBroadcaster`. No change in `sessionmanagement`.

**Tech Stack:** Java 22 (compiled with `-source/-target 17`), JUnit 5 + Hamcrest (no Mockito in the domain), Spring WebSocket/STOMP, FrenchCards 0.2.1.

**Spec:** `docs/specs/2026-10-11-kobo-backend-design.md`. Hypotheses marked `[H..]` there are assumed as written; if the product owner changes one, adjust only the task named in "Hypothesis impact" at the end.

**Test commands (project):**
- One class: `cd backend && mvn -s ../deploy/docker/settings.xml -Dtest=KoboTest test`
- Whole backend (unit tests plus `*IT`, which Surefire is configured to run): `cd backend && mvn -s ../deploy/docker/settings.xml test`
- Frontend is untouched, but before the PR: `cd frontend && npm test && npx vite build`.

**Conventions:** English code and docs; backend paths are relative to `backend/src/main/java/org/kevinkib/cardgames/` (tests under `backend/src/test/java/org/kevinkib/cardgames/`, same package). Each task is red -> green -> commit (`feat: ...`, `test: ...`). Branch `feat/kobo-backend`, PR to `main` only after all suites pass. Domain exceptions are checked and extend `KoboException`, like Bullshit's.

---

## File structure

**Create, domain (`kobo/domain/`, grouped in sub-packages by concept; each exception sits with the concept it protects):**
- `domain/`: `Kobo.java` aggregate; `KoboFactory.java`; `FinishedGameException`, `NothingToMatchException`, `StaleDiscardException`, `InvalidGiftTargetException`
- `domain/rules/`: `KoboRules.java` (constants); `KoboException`
- `domain/card/`: `CardPoints.java`
- `domain/power/`: `Power.java`
- `domain/turn/`: `Phase.java`; `Action.java` (available-action enum); `NotPlayersTurnException`, `WrongPhaseException`, `AlreadyReadyException`
- `domain/tableau/`: `Tableau.java` (+ `Slot` record); `SlotRef.java`; `InvalidSlotException`, `EmptySlotException`, `StaleSlotException`, `InvalidGiveSlotException`
- `domain/scoring/`: `RoundScoring.java`; `RoundResult.java`
- `domain/event/`: `KoboEvent.java` (sealed outcomes); `KoboResult.java`; `PrivateReveal.java`
- `package-info.java` for `kobo` and for each domain sub-package

**Create, presentation (`kobo/presentation/`):**
- `KoboGameActions.java`, `KoboStateBroadcaster.java`, `KoboWebSocketController.java`, `KoboRestController.java`, `KoboLifecycleBroadcaster.java`
- `api/{KoboCreatePayload,KoboSlotPayload,KoboTargetPayload,KoboSwapPayload,KoboMatchPayload,KoboGiftPayload}.java`
- `dto/{KoboDto,KoboPlayerDto,SlotDto,CardDto,PrivateRevealDto,RoundResultDto,OutcomeDto}.java`
- `dto/event/{KoboEventType,KoboEventData}.java` (small records per event kind)

**Modify:**
- `config/AppConfig.java` - `KoboFactory` bean in `GameFactories`; Kobo broadcaster, actions, lifecycle beans
- `docs/architecture/context-map.md` - add the Kobo context

**Tests (create):** mirrors of the above, plus `KoboBuilder`, `KoboFixtures` (test support), `kobo/KoboGameIntegrationTest`, `kobo/presentation/KoboWebSocketControllerIT`.

---

## Part A - Domain

### Task 1: Point values and powers

**Files:** Create `kobo/domain/card/CardPoints.java`, `kobo/domain/power/Power.java`; Test `CardPointsTest.java`, `PowerTest.java`.

- [ ] **Step 1: Failing tests.**
  - `CardPoints.of(card)`: Ace 1; 2 to 10 face value; Jack 11; Queen 12; King of spades and clubs 13; King of hearts and diamonds 0. A joker throws `IllegalArgumentException`.
  - `Power.of(card)`: Ace to 6 `NONE`; 7 and 8 `PEEK_OWN`; 9 and 10 `PEEK_OTHER`; Jack and Queen `BLIND_SWAP`; King `KING` (any colour).
- [ ] **Step 2:** Run both classes (red).
- [ ] **Step 3:** Implement with `FrenchRank`/`FrenchSuit.getColor()`; no dependence on `FrenchRank.getValue()`.
- [ ] **Step 4:** Green. Commit `feat: add Kobo card points and powers`.

### Task 2: `RoundScoring` (pure function) and `KoboRules`

**Files:** Create `kobo/domain/rules/KoboRules.java`, `kobo/domain/scoring/{RoundScoring,RoundResult}.java`; Test `RoundScoringTest.java`.

`KoboRules` holds the constants (4 cards, reset values 75 and 100 -> 50, end above 100, gift 10, announcer penalty 20, 2 to 6 players). `RoundScoring.score(handValues, announcer, giftTarget, totalsBefore)` returns a `RoundResult`.

- [ ] **Step 1: Failing tests (hand-computed).**
  - No announcer: totals = before + hand values.
  - Announcer strictly lowest: announcer round points 0, gift of 10 added to the target, others hand values.
  - Announcer tied with the lowest: counts as the fewest (0 + gift). Tied with a lower player: fails.
  - Announcer not lowest: hand value + 20, others hand values, no gift.
  - Order: round points, then gift, then reset. A total of exactly 75 becomes 50; exactly 100 becomes 50; 74, 76, 99, 101 unchanged. A gift that brings a player to exactly 75 or 100 resets it (reset after gift). A round that reaches exactly 100 without a gift resets too.
  - Reset applies to every player, not only the announcer or the gifted one.
  - `RoundResult` records hand values, round points, gift (from, to), totals before and after, and who was reset.
  - A gift to the announcer or to an unknown seat throws `IllegalArgumentException`; a missing target when one is required throws.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo round scoring`.

### Task 3: `Tableau`, `SlotRef`, revisions

**Files:** Create `kobo/domain/tableau/{Tableau,SlotRef}.java` (+ exceptions `KoboException`, `InvalidSlotException`, `EmptySlotException`); Test `TableauTest.java`.

- [ ] **Step 1: Failing tests.**
  - A tableau built from 4 cards has slots 0 to 3 occupied, all at revision 0.
  - `replace(slot, card)` returns the old card and bumps that slot's revision; `remove(slot)` empties it (revision bumps), the slot index stays, other slots unchanged.
  - `append(card)` adds a new slot at the end (revision 0) and never reuses an empty one; `put(slot, card)` fills an empty slot (used by gifts) and bumps revision.
  - `card(slot)` of an empty or unknown slot: unknown throws `InvalidSlotException`, empty throws `EmptySlotException`.
  - `occupiedSlots()`, `handSize()`, `points()` (sum of `CardPoints`), and an immutable `view()` of `(slot, occupied, revision)`.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo tableau with slot revisions`.

### Task 4: Aggregate skeleton, deal, factory, `Game` contract

**Files:** Create `kobo/domain/{Kobo,KoboFactory}.java`, `kobo/domain/turn/Phase.java`, `kobo/domain/event/KoboResult.java`, `package-info.java`; Test `KoboFactoryTest.java`, `KoboGameConformanceTest.java`, `KoboDealTest.java`; test support `KoboBuilder.java`, `KoboFixtures.java`.

- [ ] **Step 1: Failing tests.**
  - `KoboFactory`: `gameType()` is `"kobo"`, min 2, max 6; `create(id, n)` and `create(id, n, options)` return a `Kobo` with `n` players.
  - Deal: for 2 to 6 players each tableau holds 4 occupied slots, the draw pile holds `52 - 4n`, the discard pile is empty, all 52 cards are distinct, the phase is `MEMORISING`, `version()` is 0, round is 1, totals are 0.
  - `Game` contract (as `BullshitGameConformanceTest`): id, `getPlayerIds()` is `0..n-1`, `isFinished()` false.
  - Seeded determinism: two games dealt with the same seed deal the same cards; two with different seeds differ.
  - `KoboBuilder` can build a game in any phase from explicit tableaux, piles, totals and current seat (package-private constructor of `Kobo`).
- [ ] **Step 2:** Run (red).
- [ ] **Step 3:** Implement the constructor that deals through `CardsService.createDeck(DeckType.FRENCH, new DeckCreationOptions(Visibility.HIDDEN, seed))`. Check the `Deck.distribute`/`draw` signatures with `javap` (done while writing this plan: `distribute(Integer, Integer)`, `draw()`, `shuffle(Random)`, `new Deck(List, Random)`); if `distribute` does not fit, deal with `draw()` in a loop. Keep the draw pile and discard pile as `Deque<Card>` owned by the aggregate; reshuffle uses a `Random` held by the aggregate.
- [ ] **Step 4:** Green. Commit `feat: add Kobo aggregate skeleton, deal and factory`.

### Task 5: Memorisation barrier (`ready`) and starting view data

**Files:** Modify `Kobo.java`; create `AlreadyReadyException`, `WrongPhaseException`, `NotPlayersTurnException`, `FinishedGameException`; Test `KoboReadyTest.java`.

- [ ] **Step 1: Failing tests.**
  - `ready(seat)` in `MEMORISING` adds the seat to `readySeats()`, bumps `version`; the last remaining player's `ready` moves to `DRAW` with the current seat set to the first seat of the round (seat 0 in round 1).
  - `ready` twice by the same seat throws `AlreadyReadyException`, no version bump; `ready` for an unknown seat throws `IllegalArgumentException`; `ready` outside `MEMORISING`/`ROUND_OVER` throws `WrongPhaseException`; on a finished game `FinishedGameException`.
  - `startingCards(seat)` returns that seat's slots 2 and 3 (values), only while `phase == MEMORISING` or no card has been drawn yet in the round (flag `firstDrawDone`), then empty.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo memorisation barrier`.

### Task 6: Drawing, reshuffle and exhaustion

**Files:** Modify `Kobo.java`; Test `KoboDrawTest.java`.

- [ ] **Step 1: Failing tests.**
  - `draw(seat)` by the current player in `DRAW`: draw pile shrinks by one, `drawnCard()` is that card, phase `DECISION`, version +1. Wrong seat: `NotPlayersTurnException`; wrong phase: `WrongPhaseException`; neither mutates nor bumps version.
  - Empty draw pile with a discard pile of 5 cards: the 4 under the top are reshuffled into the draw pile (assert the set of cards and that the top is unchanged), then drawn from.
  - Empty draw pile and a discard pile of 1 card (or empty): the draw is skipped, phase goes to `TURN_END`, event `DrawSkipped`, no exception.
  - `firstDrawDone` flips on the first draw of a round.
- [ ] **Step 2:** Run (red). **Step 3:** Implement a private `drawFromPile()` shared with the penalty (returns `Optional<Card>`). **Step 4:** Green. Commit `feat: add Kobo draw with reshuffle`.

### Task 7: Swap the drawn card, discard it directly, end turn

**Files:** Modify `Kobo.java`; create `KoboEvent.java`; Test `KoboTurnTest.java`.

- [ ] **Step 1: Failing tests.**
  - `swapDrawn(seat, slot)`: the old card becomes the discard top, the drawn card sits in the slot, revision bumps, phase `TURN_END`, `drawnCard()` empty. An empty or unknown slot throws and leaves the drawn card held.
  - `discardDrawn(seat)` of a rank with no power (Ace to 6): top of discard is the drawn card, phase `TURN_END`. Of a 7, 9, Jack, King: phase `POWER` with the matching `Power` (`pendingPower()`). A swap never grants a power (swap a 7: phase `TURN_END`).
  - `endTurn(seat)` in `TURN_END`: current seat advances to the next remaining seat (wrapping), phase `DRAW`. Wrong seat or phase throws.
  - A 3-turn scripted sequence for 3 players cycles seats 0, 1, 2, 0.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo turn actions`.

### Task 8: Peek powers and `skipPower`

**Files:** Modify `Kobo.java`, create `PrivateReveal.java`; Test `KoboPeekTest.java`.

- [ ] **Step 1: Failing tests.**
  - 7 or 8 then `peek(seat, ownRef)`: `revealFor(seat)` holds the card (value, ref, revision), phase `TURN_END`; `revealFor(otherSeat)` is empty. Peeking another player's card with a 7 throws `InvalidSlotException`.
  - 9 or 10 then `peek(seat, otherRef)`: same; peeking an own slot throws.
  - Peek of an empty slot throws `EmptySlotException`; wrong power (a Jack pending) throws `WrongPhaseException`.
  - `skipPower` in `POWER`: phase `TURN_END`, no reveal. The reveal of a seat is cleared when that seat's next command is accepted (`endTurn`).
  - The public event for a peek carries the ref but never the card.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo peek powers`.

### Task 9: Blind swap (Jack, Queen)

**Files:** Modify `Kobo.java`; Test `KoboBlindSwapTest.java`.

- [ ] **Step 1: Failing tests.**
  - `blindSwap(seat, ownSlot, otherRef)`: the two cards exchange places, both slots bump revision, phase `TURN_END`, nothing revealed to anyone (`revealFor` empty for all).
  - Other ref on self throws `InvalidSlotException`; either slot empty throws `EmptySlotException`; wrong power/phase throws.
  - A player with no occupied slot can only `skipPower` (the swap throws `EmptySlotException`/`InvalidSlotException`, skip works).
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo blind swap power`.

### Task 10: King (peek then optional swap)

**Files:** Modify `Kobo.java`; create `StaleSlotException`; Test `KoboKingTest.java`.

- [ ] **Step 1: Failing tests.**
  - Discarding a king (black or red) then `peek(seat, otherRef)`: reveal to the king's player, phase `KING_DECISION` (not `TURN_END`).
  - `kingSwap(seat, ownSlot)`: peeked card goes to the own slot, own card to the peeked slot, both revisions bump, phase `TURN_END`, reveal cleared.
  - `skipPower` in `KING_DECISION`: nothing moves, phase `TURN_END`.
  - Between the peek and `kingSwap` the peeked slot is changed (revision moved, simulated through `matchDiscard` or a direct fixture mutation): `kingSwap` throws `StaleSlotException`; emptied: `EmptySlotException`; `skipPower` still works.
  - Peeking an own card with a king throws.
- [ ] **Step 2:** Run (red). **Step 3:** Implement (store `kingTarget` with its revision). **Step 4:** Green. Commit `feat: add Kobo king power`.

### Task 11: Matching discard on own cards

**Files:** Modify `Kobo.java`; create `NothingToMatchException`, `InvalidGiveSlotException`; Test `KoboMatchOwnTest.java`.

- [ ] **Step 1: Failing tests.**
  - Own card of the same rank as the discard top: card leaves the slot (revision bump), becomes the new top, hand size -1, event `MatchSucceeded`; works for a non-current player and in every phase `DRAW`, `DECISION`, `POWER`, `KING_DECISION`, `TURN_END`; turn and phase unchanged.
  - Colour is irrelevant: red king on black king, 7 of hearts on 7 of spades.
  - `expectedTopRank` differing from the real top rank: `StaleDiscardException`, no penalty, no version bump (H4, decided). Every other test passes the correct expected rank.
  - Different rank: the actor gets one more slot, appended (index = previous size, never an empty slot), draw pile -1, event `MatchPenalised`, the discard top unchanged, the targeted card stays in its slot and hidden (no revealed value in the event).
  - Penalty with empty draw pile: reshuffle applies; with nothing left, no penalty card and no exception.
  - Empty target slot: `EmptySlotException`, no penalty, no version bump. Empty discard pile: `NothingToMatchException`, no penalty. Unknown slot: `InvalidSlotException`.
  - Rejected in `MEMORISING`, `GIFT`, `ROUND_OVER`, `FINISHED` (`WrongPhaseException` / `FinishedGameException`).
  - A player can fall to 0 cards (`handSize()` 0, `points()` 0).
- [ ] **Step 2:** Run (red). **Step 3:** Implement `matchDiscard(seat, SlotRef, Integer giveSlot)` with the check order of the spec. **Step 4:** Green. Commit `feat: add Kobo matching discard on own cards`.

### Task 12: Matching discard on another player's card (with gift)

**Files:** Modify `Kobo.java`; Test `KoboMatchOtherTest.java`.

- [ ] **Step 1: Failing tests.**
  - Same rank on another player's slot `s` with `giveSlot = g`: target card goes to the discard top; the actor's card from slot `g` lands in the target's slot `s`; the actor's slot `g` is empty; revisions bump on `(target, s)` and `(actor, g)`; the targeted player's `Tableau` size unchanged.
  - Different rank: only the actor is penalised (one extra slot); the target and the giver are untouched.
  - Missing, empty or unknown `giveSlot` with a non-empty actor tableau: `InvalidGiveSlotException`, no mutation, no penalty, no version bump.
  - Actor with 0 cards matching another's card: the slot stays empty, nothing given, no exception.
  - `giveSlot` is ignored when the target is the actor's own card.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo matching discard on other players cards`.

### Task 13: Race semantics (concurrency)

**Files:** Test `KoboMatchRaceTest.java` (implementation should already pass if the aggregate is `synchronized`; fix if not).

- [ ] **Step 1: Tests.**
  - Fixture: discard top is a 7; seat 1's slot 0 is a 7. Seats 0, 2, 3 each call `matchDiscard` on `(1, 0)` from separate threads released by a `CountDownLatch`, repeated 200 times with fresh games: exactly one call succeeds, the others throw `EmptySlotException`; no penalty card appears anywhere; total card count across tableaux, draw pile and discard pile stays 52; `version` increased by exactly one.
  - Two threads match two different 7s: both succeed in some order; the discard pile has both.
  - A thread matches while another discards a drawn card of a different rank: whichever order, the sum of cards stays 52 and the loser of the order is judged against the right top (assert both reachable outcomes are legal states).
- [ ] **Step 2:** Run. If red, make `matchDiscard` and every command `synchronized`. **Step 3:** Green. Commit `test: add Kobo race tests`.

### Task 14: Announcing Kobo and final turns

**Files:** Modify `Kobo.java`; Test `KoboAnnounceTest.java`.

- [ ] **Step 1: Failing tests.**
  - `announceKobo(seat)` by the current player in `TURN_END`: announcer recorded, the turn moves to the next seat in `DRAW`, `pendingFinalTurns()` lists every other remaining seat in order after the announcer, version +1.
  - Rejected: out of turn; in `DRAW`, `DECISION`, `POWER`, `KING_DECISION` (`WrongPhaseException`, game unchanged); a second announce in the same round; during final turns.
  - A player with 0 cards can announce.
  - In final turns each player plays a full turn (`draw`, action, `endTurn`); each `endTurn` pops the queue; the matching discard still works during final turns; after the last queued player's `endTurn`, the game does not return to the announcer and moves to scoring (task 15).
  - 2 players: announcer then exactly one more turn.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo announce and final turns`.

### Task 15: Round resolution, gift, next round, end of game

**Files:** Modify `Kobo.java`; create `InvalidGiftTargetException`; Test `KoboRoundEndTest.java`, `KoboGameEndTest.java`.

- [ ] **Step 1: Failing tests.**
  - After the last final turn, with the announcer strictly or tied lowest: phase `GIFT`; only the announcer may `giveTen(target)`; wrong seat, self, unknown or removed target throw `InvalidGiftTargetException`/`NotPlayersTurnException`; after the gift the result is applied (`RoundScoring`), phase `ROUND_OVER`, `lastRoundResult()` filled, all tableaux flagged revealed.
  - Announcer not lowest: no `GIFT` phase, round scored at once with +20.
  - 75/100 reset visible in `totals()`; exactly 100 does not end the game, 101 does (phase `FINISHED`, `isFinished()` true, `result()` winners are the lowest totals; a tie yields several winners).
  - `ROUND_OVER`: `ready` from every remaining player starts round 2: fresh 52-card deal, 4 cards each, empty discard pile, `round()` 2, totals kept, start seat rotated, phase `MEMORISING`, `readySeats` cleared, `startingCards` available again.
  - Whole-game scripted test: three rounds by scripted piles ending with a player above 100.
- [ ] **Step 2:** Run (red). **Step 3:** Implement `giveTen`, `finishRound`, `startNextRound`. **Step 4:** Green. Commit `feat: add Kobo round end, gift and game end`.

### Task 16: Forfeit

**Files:** Modify `Kobo.java`; Test `KoboForfeitTest.java`.

- [ ] **Step 1: Failing tests.**
  - Non-current player forfeits: removed from `getPlayerIds()`, current seat and phase unchanged, their cards under the draw pile (conserved total of 52), version +1.
  - Current player forfeits in `DRAW`, in `DECISION` (drawn card goes under the draw pile), in `POWER`: the turn passes to the next remaining seat in `DRAW`.
  - Forfeit in `MEMORISING`/`ROUND_OVER`: the barrier is re-evaluated (if everyone else is ready it opens).
  - Forfeit during final turns: removed from the queue; if the queue empties the round is scored.
  - The announcer forfeits: Kobo void, the remaining final turns are played, scoring has no bonus, penalty or gift.
  - The announcer owes the gift and forfeits in `GIFT`: round finalised without gift.
  - One player left: `FINISHED`, that player wins, reason `FORFEIT`. Forfeit of an unknown seat or on a finished game is a no-op, like Bullshit.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo forfeit`.

### Task 17: Available actions and private view

**Files:** Modify `Kobo.java`, create `Action.java`; Test `KoboAvailableActionsTest.java`.

- [ ] **Step 1: Failing tests (one assertion per phase and seat).**
  - `MEMORISING`: `READY` for a seat not yet ready, none for a ready one. `DRAW`: `DRAW` for the current seat only. `DECISION`: `SWAP_DRAWN`, `DISCARD_DRAWN`. `POWER`: the power action (`PEEK` or `BLIND_SWAP`) and `SKIP_POWER`. `KING_DECISION`: `KING_SWAP`, `SKIP_POWER`. `TURN_END`: `END_TURN` and `ANNOUNCE_KOBO` (the latter absent in final turns or after an announce). `GIFT`: `GIVE_TEN` for the announcer. `ROUND_OVER`: `READY`. `FINISHED`: none.
  - `MATCH_DISCARD` for every remaining seat whenever a match is legal (live round phases, discard not empty), regardless of the current seat.
  - `drawnCard(seat)` is present only for the current seat in `DECISION`.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Run the whole domain package. Commit `feat: add Kobo available actions`.

---

## Part B - Application and session

### Task 18: Register Kobo in the session core

**Files:** Modify `config/AppConfig.java`; Test `kobo/KoboSessionTest.java` (in the style of `SessionServiceGenericGameTest`).

- [ ] **Step 1: Failing tests.** With `GameFactories(List.of(new KoboFactory()))`: `createRoom("kobo", "Alice")` gives a room with 6 seats and a host token; two `joinRoom`; `startGame` with 1 player throws `NotEnoughPlayersException`, with 2 or 3 builds a `Kobo` with that many players; `playAgain` on a finished game reopens the room (seat 0 host); `rematch` after `requestRematch` from all seats builds a fresh `Kobo`; `requestRematch`/`leaveRematch` behave as for Bullshit. An `ApplicationContextTest`-level check that `GameFactories` knows `"kobo"`.
- [ ] **Step 2:** Run (red). **Step 3:** Add `koboFactory()` bean and add it to the `GameFactories` list. **Step 4:** Green. Commit `feat: register Kobo in the game factories`.

### Task 19: Lifecycle broadcaster

**Files:** Create `kobo/presentation/KoboLifecycleBroadcaster.java` (needs the state broadcaster from task 21, so build the test with a recording `GameMessagingService` double as in `BullshitLifecycleBroadcasterTest`); Test `KoboLifecycleBroadcasterTest.java`.

- [ ] **Step 1: Failing tests.** `supports(game)` true only for `Kobo`; `disconnected`, `reconnected`, `forfeited` each send, per human seat, a `SuccessResponse` with the matching `LifecycleEventType` and `OpponentDisconnectedEventData(seat, deadline)` / `OpponentReconnectedEventData` / `ForfeitEventData`, with that seat's `KoboDto`.
- [ ] **Step 2-4:** Implement (after task 21), green. Commit `feat: add Kobo lifecycle broadcaster`. (Order: do tasks 20 and 21 first, then this one.)

### Task 20: `KoboDto` (per-seat projection)

**Files:** Create `kobo/presentation/dto/{KoboDto,KoboPlayerDto,SlotDto,CardDto,PrivateRevealDto,RoundResultDto,OutcomeDto}.java`; Test `KoboDtoTest.java`, `KoboDtoPrivacyTest.java`, `DiscriminatedDtoTest` equivalent for `OutcomeDto`.

- [ ] **Step 1: Failing tests.**
  - Shape: `started`, `id`, `gameType == "kobo"`, `version`, `round`, `phase`, `currentSeat`, `readySeats`, `announcer`, `finalTurnsRemaining`, `drawPileSize`, `discardTop` (null when empty), `discardSize`, `players[]` (seat, name, bot flag, total, `handSize`, `slots[] = {slot, occupied, revision}`), `availableActions`, `outcome` (`ONGOING` / `FINISHED` with `winners`), `lastRound` (null before the first scoring).
  - Privacy (the core of this task): for a mid-round game, serialise every seat's DTO to JSON and assert that no hidden card name (e.g. `HEART_SEVEN`) of any tableau appears in another seat's JSON, nor the draw pile, nor another seat's drawn card; the viewer's `drawnCard` is present only for the current seat in `DECISION` (others see `hasDrawn`); `startingCards` only for the viewer, only before the first draw; `reveal` only for the viewer who peeked and gone after their next command.
  - Reveal: in `GIFT`, `ROUND_OVER`, `FINISHED` every slot carries its card, for every viewer.
  - A viewer not in the game (forfeited) gets an empty private part and no actions, like Bullshit's `viewerPresent`.
  - Names come from the `Map<PlayerId,String>` passed in.
- [ ] **Step 2:** Run (red). **Step 3:** Implement `KoboDto.forViewer(game, viewer, names)` reading only the aggregate's public queries (no private field access). **Step 4:** Green. Commit `feat: add Kobo per-seat state DTO`.

### Task 21: State broadcaster and use cases

**Files:** Create `kobo/presentation/{KoboStateBroadcaster,KoboGameActions}.java`, `dto/event/{KoboEventType,KoboEventData}.java`; Test `KoboStateBroadcasterTest.java`, `KoboGameActionsTest.java`.

- [ ] **Step 1: Failing tests.**
  - `KoboStateBroadcaster.broadcast(game, type, data, message)` sends one `SuccessResponse` per human seat on `sendToSeat`, each with its own `KoboDto`; `stateFor(game, seat)` for REST and errors. Same constructor shape as `BullshitStateBroadcaster` (messaging, seat names supplier); no bot filtering (out of scope).
  - `KoboGameActions` has one method per command (`ready`, `draw`, `swapDrawn`, `discardDrawn`, `skipPower`, `endTurn`, `announceKobo`, `peek`, `blindSwap`, `kingSwap`, `match`, `giveTen`): domain call, `sessionService.touch`, one broadcast with the right `KoboEventType` and public `KoboEventData` (peek carries no value; failed match carries the actor only; successful match carries the card; round scored carries the `RoundResult`). Domain exceptions propagate unchanged. A rejected command broadcasts nothing and does not touch.
  - Order: the call, touch and broadcast run inside `synchronized (game)`: a test with two threads and a recording messaging double asserts the `version` carried by successive broadcasts is non-decreasing.
  - After a command that finishes the round, the broadcast event type is `ROUND_END`; after one that ends the game, `GAME_OVER`.
- [ ] **Step 2:** Run (red). **Step 3:** Implement. **Step 4:** Green. Commit `feat: add Kobo state broadcaster and use cases`.

---

## Part C - WebSocket, REST and wiring

### Task 22: WebSocket controller

**Files:** Create `kobo/presentation/KoboWebSocketController.java`, `api/{KoboCreatePayload,KoboSlotPayload,KoboTargetPayload,KoboSwapPayload,KoboMatchPayload,KoboGiftPayload}.java`; Test `KoboWebSocketControllerTest.java` (style of `BullshitWebSocketControllerTest`).

- [ ] **Step 1: Failing tests.**
  - `/kobo/create`: creates a `"kobo"` room, response `CREATE` with `{gameId, gameType: "kobo", tokens: {0: hostToken}}` (no options).
  - `/kobo/start`: host only; broadcasts `START`; a non-host or too-few-players gets an `ErrorResponse` on its own seat.
  - Each command endpoint resolves the seat from the token (invalid token: silently ignored, as Bullshit), calls the matching `KoboGameActions` method, and on a domain exception replies only to that seat with `ErrorResponse(eventType, message, state)`.
  - Payload mapping: `/kobo/match` passes `giveSlot` (nullable) and `expectedTopRank`; `/kobo/blindSwap` passes own slot and target ref; no endpoint accepts a card value.
- [ ] **Step 2:** Run (red). **Step 3:** Implement with the `/kobo/...` destinations of the spec. **Step 4:** Green. Commit `feat: add Kobo WebSocket controller`.

### Task 23: REST controller

**Files:** Create `kobo/presentation/KoboRestController.java`; Test `KoboRestControllerTest.java` (style of `BullshitRestControllerTest`).

- [ ] **Step 1: Failing tests.** `GET /api/kobo/game/{id}?token=`: 403 without or with a wrong token; 404 for an unknown id or a game of another type; `LobbyView` before the start; the viewer's `KoboDto` after (each seat sees its own private part). `POST /game/{id}/join`: 200 with seat and token, 409 when started or full, 404 for another type. `POST /game/{id}/play-again`: reopens the room, first caller seat 0 and host, 409 when full.
- [ ] **Step 2-4:** Implement (copy the Bullshit controller restricted to `KoboFactory.GAME_TYPE`); green. Commit `feat: add Kobo REST controller`.

### Task 24: Spring wiring

**Files:** Modify `config/AppConfig.java`; Test `presentation/ApplicationContextTest.java` (existing) plus `KoboWiringTest.java`.

- [ ] **Step 1: Failing tests.** The context starts; beans `KoboStateBroadcaster`, `KoboGameActions`, `KoboLifecycleBroadcaster` exist; `GameLifecycleBroadcasters.broadcasterFor(new Kobo(...))` returns the Kobo one (not an `IllegalStateException`); the existing Bataille Corse and Bullshit lifecycle broadcasters still match only their own games.
- [ ] **Step 2:** Run (red). **Step 3:** Add the beans (`new KoboStateBroadcaster(gameMessagingService, sessionService()::seatNames)`, `KoboGameActions`, `KoboLifecycleBroadcaster`). **Step 4:** Green. Commit `feat: wire Kobo beans`.

---

## Part D - Integration tests

### Task 25: Whole game through session core and broadcasters (no network)

**Files:** Test `kobo/KoboGameIntegrationTest.java` (style of `BotGameIntegrationTest`: real `SessionService`, `InMemorySessionRepository`, recording messaging double, seeded randomness).

- [ ] **Step 1: Tests.**
  - A 3-player room is created and started through `SessionService`; a scripted driver (a few lines in the test, not a bot) plays turns until a total passes 100: always `ready`, `draw`, `discardDrawn`/`swapDrawn`, `endTurn`, with an occasional `announceKobo` after a fixed number of turns; the game finishes within a step bound; at every step the invariant "cards in tableaux + draw + discard + held drawn card = 52" holds.
  - Privacy over a whole game: the recording double sees every message; assert no message to seat A ever contains a hidden card of seat B at the time it was hidden (reuse the privacy checker from task 20).
  - Forfeit through `PresenceService.forfeit` mid-round: the game continues for the others; the last forfeit finishes it; then `playAgain` reopens the room and a new `Kobo` can be started.
- [ ] **Step 2-3:** Fix what fails in the domain/actions. Commit `test: add Kobo whole-game integration test`.

### Task 26: STOMP end-to-end test

**Files:** Test `kobo/presentation/KoboWebSocketControllerIT.java` (style of `BatailleCorseWebSocketControllerIT`: `@SpringBootTest(RANDOM_PORT)`, real `StandardWebSocketClient`).

- [ ] **Step 1: Tests.**
  - Two clients: host `/app/kobo/create`, guest joins over REST, both subscribe to `/topic/game/{id}/seat/{token}` (with the `token` header), host `/app/kobo/start`, both `/app/kobo/ready`: each receives a state where its own seat sees `startingCards` and the other seat's DTO has none; after `draw` by seat 0 only seat 0's DTO has `drawnCard`.
  - A seat cannot subscribe to another seat's topic (existing interceptor; asserted here for the Kobo topic).
  - Race: two clients send `/app/kobo/match` on the same card at the same time; exactly one `MATCH` success event is received by both; the other client receives an `ErrorResponse` and no penalty appears in the state.
  - A wrong command (draw out of turn) answers only the sender with an `ErrorResponse`.
  - Reconnection: `GET /api/kobo/game/{id}?token=` after some moves equals the last state pushed to that seat.
- [ ] **Step 2-3:** Fix wiring issues. Commit `test: add Kobo STOMP integration test`.

---

## Part E - Docs and checks

### Task 27: Architecture docs and package notes

**Files:** Modify `docs/architecture/context-map.md` (add the Kobo bounded context, its factory in `GameFactories`, the per-seat DTO flow and the race note); create `kobo/package-info.java` (rules context, depends only on the kernel `game` and `frenchcards`).

- [ ] **Step 1:** Add a Mermaid block for Kobo next to the Bullshit one and a short "Kobo" section (aggregate, slot revisions, race rule, what is out of scope). **Step 2:** Commit `docs: add Kobo to the context map`. Docs-only change inside a code PR, no separate PR.

### Task 28: Full verification

- [ ] Run `cd backend && mvn -s ../deploy/docker/settings.xml test` (all unit tests and `*IT`): green.
- [ ] Run `cd frontend && npm test && npx vite build` (untouched, but part of the CI).
- [ ] Self-review against the spec's edge-case list (17 items): each has a named test; tick them in the PR description.
- [ ] Push `feat/kobo-backend`, open the PR to `main` (title `feat: add Kobo game backend`); no merge without the product owner's request.

---

## Dependencies and ordering

- Tasks 1 to 3 are independent of each other; 4 needs 1 to 3; 5 to 17 are sequential on `Kobo.java` (do not parallelise; one aggregate file).
- 18 can start after 4. 20 needs 17. 21 needs 20. 19 needs 21. 22 and 23 need 21. 24 needs 19, 22, 23. 25 and 26 need 24.
- The frontend track is separate and can start from the DTO and event names of the spec once task 20 is merged.

## Hypothesis impact (which task changes if the product owner answers differently)

- H1 (initial discard empty): tasks 4, 11. H2/H14 (reshuffle, exhaustion): task 6. H3 (empty-slot no penalty): tasks 11, 13. H4 (decided: expected top rank, stale attempt rejected without penalty): tasks 11, 22.
- H6 (explicit end-of-turn step): tasks 7, 14, 22 (a flag on the last action instead). H7 (gift rules): tasks 2, 15. H8 (tied winners): task 15.
- H9 (occupied slots required): tasks 7, 9. H10 (ready barrier, no timeout): tasks 5, 15, 16. H11 (starting cards resent): tasks 5, 20.
- H12 (penalty slot appended): tasks 3, 11. H13 (forfeit cards, void Kobo): task 16. H16 (rotation): task 15. H17 (no card to give): task 12.

---

## Implementation notes (deviations from the plan above)

- **H4 decided**: `matchDiscard` takes the rank the client saw on top of the discard pile (`expectedTopRank`, required); a moved pile throws `StaleDiscardException` with no penalty.
- **Added `expectedRevision`** (optional) on the match: a card given into a slot after a match refills it, so a second attempt on the same position would otherwise find a card of another rank and be penalised for a lost race. A changed slot revision throws `StaleSlotException`, no penalty. The WebSocket payload carries it; the spec row for `/kobo/match` is updated.
- **Event types**: no `ROUND_START` event (the phase in the state tells it); a command that closes a round is broadcast as `ROUND_END`, one that ends the game as `GAME_OVER`, with the command named in the event data (`KoboEventData.action`).
- **DTO layout**: the sub-records (`SlotDto`, `PlayerDto`, `RevealDto`, `RoundDto`...) are nested in `KoboDto`; `CardDto` and `OutcomeDto` are separate files. Java source level is 17, so events are mapped with `instanceof` patterns, not pattern `switch`.
- **Tests grouped by theme** instead of one class per task (`KoboTurnAndPowersTest`, `KoboMatchTest`, `KoboRoundTest`, ...); every listed case is covered.
- **Task 24 wiring** is covered by `KoboGameIntegrationTest` (real Spring context: lifecycle broadcaster lookup, presence forfeit, play-again) and `KoboWebSocketControllerIT` instead of a dedicated `KoboWiringTest`.
