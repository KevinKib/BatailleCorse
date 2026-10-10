# Bullshit Bot Players Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the host add and remove bot players from a Bullshit lobby. Bots play mixed games through the same action path as humans, see only what their seat sees, decide with a probabilistic rule-based strategy, and act after a 1-2 s simulated thinking delay.

**Architecture:** A bot is a flagged seat in the game-agnostic session core (no token exposed, no presence). The decision logic is a pure domain port (`BotStrategy`) in `bullshit.domain.bot`, fed a `BotObservation` that mirrors `BullshitDto.forViewer`. A presentation-level `BullshitBotCoordinator` schedules delayed actions through a `BotScheduler` port (adapter on the Spring `TaskScheduler`) and a `ThinkingDelay` port, calling the same `BullshitGameActions` as the WebSocket controller. A `RandomGenerator`, the scheduler and the delay are injected, so tests are deterministic.

**Tech Stack:** Java 22, JUnit 5 + Hamcrest (no Mockito on domain), Spring WebSocket/STOMP; Vue 3 `<script setup>` + TypeScript, Pinia, PrimeVue, Vitest (frontend task only).

**Spec:** `docs/specs/2026-10-10-bullshit-bots-design.md`

**Dependencies and ordering:**
- Task 0 is a standalone clean-up and must be merged before the rest of B2 starts.
- B2 (tasks 1-9, backend) can run in parallel with the frontend track A (it touches no frontend file).
- B3 (task 10, frontend lobby) must start only after A3 lot 1b (lobby and start screens) is merged, because both rewrite `BullshitStartGame.vue` and the lobby view. Never run them in parallel.
- Tests for the Cypress bot scenario belong to C2 (Bullshit e2e) and need the delay pinned to 0 in the `test` profile (task 8).

---

## File Structure

**Backend, create:**
- `bullshit/domain/bot/BotStrategy.java` - port
- `bullshit/domain/bot/BotDecision.java` - sealed: `Discard`, `CallBullshit`, `Pass`
- `bullshit/domain/bot/BotObservation.java` - the seat's view, built from the aggregate
- `bullshit/domain/bot/BotMemory.java` - public-information knowledge
- `bullshit/domain/bot/PlausibilityEstimator.java` - hypergeometric tail
- `bullshit/domain/bot/BotTuning.java` - strategy constants
- `bullshit/domain/bot/ProbabilisticBotStrategy.java` - the single difficulty level
- `bullshit/presentation/BullshitGameActions.java` - discard / call use cases shared with bots
- `bullshit/presentation/bot/BullshitBotCoordinator.java`, `BotScheduler.java`, `ThinkingDelay.java`, `BotSeats.java`
- `bullshit/presentation/api/BullshitBotPayload.java`
- `bullshit/presentation/bot/TaskSchedulerBotScheduler.java` - adapter
- Test mirrors under `backend/src/test/...`, plus `ManualBotScheduler` and `FixedThinkingDelay` test doubles

**Backend, modify:**
- `bullshit/presentation/api/BullshitCreatePayload.java` - drop `nbPlayers`, `mode` (task 0)
- `bullshit/domain/Bullshit.java` - add a `version()` counter
- `bullshit/presentation/BullshitWebSocketController.java` - delegate to `BullshitGameActions`; add `addBot` / `removeBot` endpoints
- `bullshit/presentation/BullshitStateBroadcaster.java` - state listener hook; skip bot seats
- `sessionmanagement/core/domain/SessionPlayer.java`, `SessionGame.java` - `bot` flag, `claimBot`, `removeBot`, token guard
- `sessionmanagement/core/application/SessionService.java`, `SeatView.java`, `LobbyView.java` - `addBot`, `removeBot`, `botSeats`, `bot` flags, `removableBotSeats`
- `config/AppConfig.java` - wiring; `application-test` profile pins the delay to 0

**Frontend (task 10):**
- `frontend/src/model/bullshit/LobbyView.ts` - `bot`, `removableBotSeats`
- `frontend/src/application/BullshitSession.ts` - `addBot()`, `removeBot(seat)`
- `frontend/src/state/Bullshit.store.ts` - actions
- the lobby component(s) as they exist after lot 1b - buttons and bot badge
- `frontend/src/locales/*` - all new visible text

(Backend paths are relative to `backend/src/main/java/org/kevinkib/cardgames/`.)

---

## Task 0: Clean `BullshitCreatePayload` (standalone PR, before B2)

**Files:**
- Modify: `bullshit/presentation/api/BullshitCreatePayload.java`
- Modify: `backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitWebSocketControllerTest.java`

The frontend sends only `{ name, claimMode }` and the controller reads only those two; `nbPlayers` and `mode` (and the `GameMode` import) are dead.

- [ ] **Step 1:** In `BullshitWebSocketControllerTest`, change the three `new BullshitCreatePayload(null, null, "Alice", null)` calls to `new BullshitCreatePayload("Alice", null)`. Run the class: it fails to compile (red).
- [ ] **Step 2:** Reduce the record to `BullshitCreatePayload(String name, String claimMode)` and delete the `GameMode` import.
- [ ] **Step 3:** `grep -rn "BullshitCreatePayload" backend frontend` shows no other user. Run the full backend suite (green).
- [ ] **Step 4:** Commit `refactor: drop unused fields from BullshitCreatePayload`; PR to main.

---

## Task 1: `BotObservation`, `BotDecision`, `BotStrategy`, game version

**Files:**
- Create: `bullshit/domain/bot/{BotStrategy,BotDecision,BotObservation}.java`
- Modify: `bullshit/domain/Bullshit.java`
- Test: `bullshit/domain/bot/BotObservationTest.java`, additions to `BullshitTest.java`

- [ ] **Step 1: Failing tests.**
  - `BullshitTest`: `version()` starts at 0 and increases by one after each successful `discard`, `callBullshit` and `forfeit`; it does not change when the action throws.
  - `BotObservationTest`: for a game built with `BullshitBuilder`, `BotObservation.forSeat(game, seat)` returns own hand, hand count of every seat, current target, pile size, last claim (claimant, target, count), pending winner and available actions; building it for a seat not in the game throws `IllegalArgumentException`.
  - Parity: for the same game and seat, every shared fact equals the corresponding field of `BullshitDto.forViewer(game, seat)` (hand, counts, target label, pile size, claim count, pending winner, actions).
  - Structural: reflection over the record's components asserts none is of type `Hand`, `Player` or `Discard`, and that none exposes another seat's cards.
- [ ] **Step 2:** Run them (red).
- [ ] **Step 3:** Add `private int version` to `Bullshit`, incremented at the end of each mutating method on success, with a `version()` getter. Create the observation record (immutable copies), the sealed `BotDecision` (`Discard(List<Card>)`, `CallBullshit`, `Pass`). The `BotStrategy` interface (`BotDecision decide(BotObservation observation, BotMemory memory)`) is added in task 4, once `BotMemory` exists (task 3).
- [ ] **Step 4:** Run the domain suite (green). Commit `feat: add bot observation and decision types`.

## Task 2: `PlausibilityEstimator`

**Files:**
- Create: `bullshit/domain/bot/PlausibilityEstimator.java`
- Test: `PlausibilityEstimatorTest.java`

API: `double probabilityTrue(int claimedCount, int matchingUnseen, int knownMatchesWithClaimant, int unseenPopulation, int claimantHandBefore)`.

- [ ] **Step 1: Failing tests.** Claim larger than the unseen matches gives 0.0. Claim of 0 required (known matches already cover it) gives 1.0. A hand-computed hypergeometric case (for example population 40, 3 matches, claimant hand 10, claim 1 gives `1 - C(37,10)/C(40,10)`). Monotonic: probability decreases as the claimed count grows. Guards: negative or inconsistent inputs throw `IllegalArgumentException`.
- [ ] **Step 2:** Run (red).
- [ ] **Step 3:** Implement the tail with log-gamma or iterative binomial ratios (no overflow for 52 cards).
- [ ] **Step 4:** Green. Commit `feat: add claim plausibility estimator`.

## Task 3: `BotMemory`

**Files:**
- Create: `bullshit/domain/bot/BotMemory.java`
- Test: `BotMemoryTest.java`

- [ ] **Step 1: Failing tests.**
  - `onOwnDiscard(cards)` adds to known pile cards; `onPileTaken()` clears them.
  - `onReveal(picker, cards)` records the cards as held by `picker`; `onDiscardBy(seat)` forgets what was known for that seat; revealed cards of the bot itself are ignored (it already sees its hand).
  - `knownMatches(seat, target, claimMode)` counts known cards of `seat` matching a target through `ClaimMode.matches`.
  - `unseenPopulation(ownHand)` = 52 minus own hand minus known pile cards.
- [ ] **Step 2:** Red, implement, green. Commit `feat: add bot memory`.

## Task 4: `ProbabilisticBotStrategy` and `BotTuning`

**Files:**
- Create: `bullshit/domain/bot/{BotTuning,ProbabilisticBotStrategy}.java`
- Test: `ProbabilisticBotStrategyTest.java`

Constructor: `(ClaimMode claimMode, RandomGenerator random, BotTuning tuning)`.

- [ ] **Step 1: Failing tests (each uses a scripted `RandomGenerator`, a helper that returns a fixed sequence of doubles, to force branches).**
  - Challenge: pending winner and the bot is next player always returns `CallBullshit`; an impossible claim with the generator below the cap returns `CallBullshit`, above the cap `Pass`/`Discard` (cap 0.95); a very plausible claim with the generator above `pCall` does not call; the bot never calls on its own claim (no `CALL_BULLSHIT` in the observation gives no call).
  - Play: with matching cards and the honest branch it plays all matching cards (max 4); with the bluff branch it adds one non-matching card; with no matching card it plays 1, 2 or 3 cards per the scripted draw, picking the cards whose target comes up latest (rank mode and suit mode via `AscendingRankClaimMode` / `CyclingSuitClaimMode`); a hand that is entirely matching and at most 4 cards is played whole with no bluff; a played set is always valid (1-4 cards, all in hand).
  - Not its turn and no claim: returns `Pass`.
  - Determinism: two strategies with equal seeds return equal decisions over 200 generated observations.
  - Statistical (seeded `Random`, 2000 rounds): a certain lie is called in [0.90, 0.97] of rounds; a near-certain truth in [0.03, 0.15].
  - Property: over many seeded random games' observations, every `Discard` decision is accepted by `Bullshit.discard` (no domain exception).
- [ ] **Step 2:** Red, implement, green. Commit `feat: add probabilistic bot strategy`.

## Task 5: Session core, bot seats

**Files:**
- Modify: `sessionmanagement/core/domain/{SessionPlayer,SessionGame}.java`, `core/application/{SessionService,SeatView,LobbyView}.java`
- Test: `SessionGameTest`, `SessionServiceTest`, `LobbyViewTest` (existing files in `backend/src/test/.../sessionmanagement/core/`)

- [ ] **Step 1: Failing tests.**
  - `SessionGame.claimBot(name)` claims the lowest free seat, marks it `bot`, names it `Bot 1`, `Bot 2` when `name` is blank; fails with `NoFreeSeatException` when full; counts in `claimedCount`.
  - `removeBot(seat)` frees a bot seat; refuses a human seat and an unclaimed seat; refuses when a human sits above the seat; renumbers bots above it when only bots are above.
  - `findPlayerByToken` returns empty for a bot seat's own token.
  - `isRematchUnanimous` is unaffected (Bullshit does not use it); `clearRematch` unchanged.
  - `SessionService.addBot(gameId, hostToken)` / `removeBot(gameId, hostToken, seat)`: `NotHostException` for a non-host or unknown token, `GameAlreadyStartedException` after start, `RoomFullException` when full; host alone plus one bot satisfies `startGame` (min 2).
  - `SessionService.botSeats(gameId)` returns the bot seats; `playAgain` yields a lobby with no bots.
  - `LobbyView.LobbyPlayer` and `SeatView` carry `bot`; `LobbyView.removableBotSeats` lists exactly the removable ones; no field contains a token.
- [ ] **Step 2:** Red, implement, green. Run the whole `sessionmanagement` package (presence tests must stay green).
- [ ] **Step 3:** Commit `feat: let a session host bot seats`.

## Task 6: Extract `BullshitGameActions`

**Files:**
- Create: `bullshit/presentation/BullshitGameActions.java`
- Modify: `bullshit/presentation/BullshitWebSocketController.java`
- Test: `BullshitGameActionsTest.java`; the existing `BullshitWebSocketControllerTest` is the regression net

- [ ] **Step 1: Failing tests.** `discard(gameId, seat, cards)` and `callBullshit(gameId, seat)` apply the move, touch the session, and broadcast the same event data and messages the controller sends today (assert via `BullshitStateBroadcasterTest`-style recording of `GameMessagingService`); domain exceptions propagate to the caller.
- [ ] **Step 2:** Move the move-applying and broadcasting code from the controller into the class; the controller keeps token resolution and replies with `ErrorResponse` on exception. No change in behaviour.
- [ ] **Step 3:** Run the whole `bullshit/presentation` package (green). Commit `refactor: share Bullshit discard and call use cases with bots`.

## Task 7: Coordinator, scheduler and delay ports

**Files:**
- Create: `bullshit/presentation/bot/{BullshitBotCoordinator,BotScheduler,ThinkingDelay,BotSeats}.java`
- Modify: `bullshit/presentation/BullshitStateBroadcaster.java`
- Test: `BullshitBotCoordinatorTest.java`, `ManualBotScheduler`, `FixedThinkingDelay`; additions to `BullshitStateBroadcasterTest`

- [ ] **Step 1: Failing tests (all with `ManualBotScheduler`, `FixedThinkingDelay`, a scripted strategy; no sleeping).**
  - After a state change with a bot on turn, exactly one task is scheduled with the injected delay; running it applies the strategy's decision through `BullshitGameActions`.
  - A claim on the table schedules one decision per non-claimant bot; the first to run wins, the others drop (version changed).
  - A task whose captured version is stale does nothing.
  - A new state change cancels the previous tasks of that game.
  - Finished game: all tasks cancelled and the memory dropped.
  - No human left in the game's seats: nothing scheduled, pending tasks cancelled (spec D7).
  - Strategy returns `Pass`: nothing applied; re-evaluated on the next change.
  - Strategy or action throws: logged, fallback discard of the lowest card; the table does not stall; a second failure only logs.
  - The memory is fed from public events only (own discard, pile taken, reveal with picker).
  - `BullshitStateBroadcaster` calls registered listeners after sending; bot seats receive no message.
- [ ] **Step 2:** Red, implement (`synchronized (game)` around the version check and the action), green.
- [ ] **Step 3:** Commit `feat: schedule bot turns with a thinking delay`.

## Task 8: Endpoints, wiring and test profile

**Files:**
- Create: `bullshit/presentation/api/BullshitBotPayload.java`, `bullshit/presentation/bot/TaskSchedulerBotScheduler.java`
- Modify: `BullshitWebSocketController.java`, `config/AppConfig.java`, `application-test` properties
- Test: additions to `BullshitWebSocketControllerTest`; `TaskSchedulerBotSchedulerTest`; `ApplicationContextTest` (existing)

- [ ] **Step 1: Failing tests.**
  - `/bullshit/addBot` as host adds a bot and broadcasts a lobby refresh to every human seat (`LobbyBroadcaster`); as a non-host or after start sends an `ErrorResponse` to the actor's seat; `/bullshit/removeBot` same, with the contiguity refusal.
  - After `/bullshit/start`, the coordinator is triggered for a game with a bot on turn (first move scheduled).
  - `TaskSchedulerBotScheduler` runs a task after the delay and honours `cancel()`.
  - The Spring context starts; with the `test` profile the thinking delay is 0 ms so Cypress runs are not slowed.
- [ ] **Step 2:** Red, implement, green. Wire `ThreadLocalRandom`-backed `RandomGenerator` and a uniform 1000-2000 ms `ThinkingDelay` in production.
- [ ] **Step 3:** Commit `feat: add bot management endpoints`.

## Task 9: Integration test and docs

**Files:**
- Create: `backend/src/test/.../bullshit/BotGameIT.java`
- Modify: `docs/architecture/context-map.md`, `docs/harnesses.md` only if the harness changes

- [ ] **Step 1:** Integration test: a game of 1 human (moves chosen by a scripted helper through `BullshitGameActions`) plus 2 bots under `ManualBotScheduler`, across several seeds, reaches a winner within a step bound without exceptions; a game with humans all forfeited stops scheduling; rank and suit claim modes both pass.
- [ ] **Step 2:** Update `context-map.md`: bots in session management (flag), the `bullshit.domain.bot` port, and the presentation coordinator (the spec's D2 diagram, in the map's mermaid style).
- [ ] **Step 3:** Run all suites (`mvn -s ../deploy/docker/settings.xml test`; frontend `npm test` and `npx vite build` must be unaffected). Commit `docs: describe bot players in the context map`; PR for B2 to main.

---

## Task 10: Frontend lobby "Add bot" (B3, after A3 lot 1b)

**Do not start before A3 lot 1b is merged** (same files). Use the `ui-change` skill and the `vue-best-practices` skill: before/after of visible text and key elements, light and dark theme, 375 px and 1280 px, a Cypress or Vitest regression test.

**Files:** see "Frontend" in the file structure above.

- [ ] **Step 1: Failing tests (Vitest).**
  - The model parses `bot` and `removableBotSeats`.
  - `BullshitSession.addBot()` publishes `/app/bullshit/addBot` with `{ gameId, token }`; `removeBot(seat)` publishes `/app/bullshit/removeBot` with `{ gameId, token, seat }`.
  - Lobby component: "Add bot" is shown to the host only, disabled when the room is full; a bot row shows a bot badge and, for the host and only for seats in `removableBotSeats`, a remove button; non-hosts see the bot row without controls; the start button follows `canStart` (host plus one bot enables it).
- [ ] **Step 2:** Red, implement; every new visible string goes through `frontend/src/locales/` (including the bot label derived from the `bot` flag).
- [ ] **Step 3:** Before/after inventory of the information the lobby displays (list from the component before editing, verify each is still visible after, in the browser). Run `npm test`, `npx vite build`.
- [ ] **Step 4:** Commit `feat: add bots from the Bullshit lobby`; PR to main. Cypress coverage of a bot game is added with C2.

---

## Verification checklist before each PR

- All backend suites green, including `ApplicationContextTest`.
- The bot never receives an object with another seat's cards (task 1 tests).
- No test uses `Thread.sleep` or the real clock.
- New text in English in code and docs; no use of the forbidden word in paths.
