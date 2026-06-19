# Play-Again Workflow — Test Coverage Design

**Date:** 2026-06-19
**Status:** Approved (brainstorm)

## Motivation

Three bugs have landed in the Bullshit rematch / play-again workflow, and the last
(#68) was found by a single manual scenario, not by the suite. The bugs share a
signature: they are **emergent across the reopen boundary with multiple
participants** — seat accounting (allocated vs joined vs participant seats,
recycled indices, contiguity) and message routing. The existing tests are all
single-component / single-actor, so the workflow itself is uncovered.

## Decision

Add a **service-level scenario battery** at `SessionService` (the layer that owns
the reopen + seat-claim logic). Deterministic, runs in CI, no production change, no
test double to keep faithful. This targets the **seat-accounting** class — where 2
of the 3 bugs lived. The **routing** class (#68) is guarded by construction by the
unit tests already added (`GameMessagingService` addresses by token;
`SeatSubscriptionInterceptor` rejects stale tokens; `SessionService` regenerates
seat tokens on reopen).

A full multi-client "fake broker" integration harness was considered and rejected:
for the routing bug it mostly re-glues already-unit-tested facts and cannot catch a
real frontend/backend topic-format drift (only a true end-to-end could), while
adding a STOMP-semantics double to maintain. The accounting value it offered is
captured here at lower cost.

## Scope

New `PlayAgainTest` nested class in
`backend/src/test/java/.../sessionmanagement/core/application/SessionServiceTest.java`,
driving the Bullshit room flow with the real in-memory repository and
`GameFactories(BullshitFactory)` — mirroring the existing `playAgain_reopensRoom…`
tests. No production code is touched.

### Scenarios

Already covered (kept, not duplicated): first caller becomes seat 0 and the next
game has only returning players; seat-0 token is regenerated on reopen.

New:

1. **Middle-leaver contiguity.** 3-player game (seats 0,1,2). After finish, only
   the former seat-0 and seat-2 players press Play Again → they receive
   **contiguous** seats 0 and 1, and the next `startGame` deals to exactly 2. (The
   "3-not-2" class.)
2. **Second returner does not re-reopen.** After the first caller reopens, a second
   `playAgain` claims the next free seat (1) and must **not** drop the fresh lobby
   or evict the first returner. (Guard: `playAgain` only reopens when a game is
   present; after the first reopen the room is a lobby.)
3. **Host presses first.** The host reopens and re-takes seat 0; a returning
   non-host gets seat 1.
4. **All seats' tokens regenerate.** Generalize the seat-0 token test: after a
   reopen, every former seat's token no longer resolves
   (`findPlayerIdByToken` empty), and the recycled seats carry fresh tokens.
5. **First caller is host regardless of prior seat.** The former seat-2 player
   pressing Play Again first becomes seat 0 / host.

## Out of scope

Real-socket STOMP tests (flaky, currently `@Disabled`/excluded from CI); a fake-broker
integration harness; frontend tests (the routing guarantee is server-side). Tests
are characterization/regression guards over the now-correct behavior, so they pass
on commit.
