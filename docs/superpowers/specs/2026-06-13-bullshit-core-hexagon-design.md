# Bullshit — Core Hexagon Design

**Date:** 2026-06-13
**Status:** Approved (design); Slice 1 ready for planning
**Scope of this spec:** Slice 1 only — the pure game-rules engine. Session, WebSocket, and frontend are later slices, scoped below but not specified here.

## Goal

Add the card game **Bullshit** (a.k.a. Cheat / I Doubt It) to the project as a new Core bounded context — `bullshit.core.domain` — sitting beside the existing `core` (BatailleCorse) hexagon. Both depend on the shared `frenchcards` library. This spec covers the pure rules engine: a self-contained, fully unit-tested aggregate with no transport, session, or UI concerns.

## Rules being modelled

- Players are dealt the whole deck. First player starts by discarding 1–4 cards face-down, claiming them as **Aces** ("one ace", "two aces", …).
- Play passes left. Each turn the claimed rank advances in consecutive ascending order: A, 2, 3, …, 10, J, Q, K, then wraps back to A and cycles indefinitely. **The claimed rank is forced by the rules — the player only chooses how many cards and which actual cards to put down.**
- A player may bluff: discard cards that do not match the claimed rank, hoping nobody calls it.
- Any other player may **call Bullshit** on the most recent discard. The questioned cards are revealed:
  - If they do **not** match the claim, the discarder (the liar) takes the entire discard pile into their hand.
  - If they **do** match, the caller takes the entire discard pile. A player may not call Bullshit on their own discard.
- **Winner:** first player to empty their hand and survive the call-Bullshit window on their final discard. Single winner; the game ends there.

## Decisions

| Decision | Choice |
|---|---|
| Player count | 2–6, N-player-aware from day one |
| Call-BS window | **Race until next discard** — after a discard, any opponent may `callBullshit` on the most recent discard, OR the next player may `discard`; first action wins (`synchronized`, like BatailleCorse `send`/`slap`). An unchallenged bluff wins. |
| Solo / AI | Out of scope. Multiplayer-only first; a bluffing AI is a separate future project. |
| Claim mechanic | Pluggable strategy (`ClaimMode`) so a future **suit-based** lying variant is a new implementation, not a rewrite. Slice 1 ships only the ascending-rank implementation. |

### Confirmed house rules

1. **After any pile is taken** (any BS resolution), the pile clears, the claim target resets to its initial value (`ACE`), and the player who *picked up the pile* starts the next round.
2. **After a successful call** (claim was truthful, caller took the pile), same as #1 — the caller starts the next round at `ACE`.
3. **Wrap-around:** claim targets cycle `A,2,…,10,J,Q,K,A,…` indefinitely; no end-of-deck reset.

## Architecture — where this lives

New Core bounded context `bullshit.core.domain`, sibling to `core`, both depending on `frenchcards`. Slice 1 touches **no existing code**.

### Full-feature decomposition (context for later slices)

| Slice | Scope | Status |
|---|---|---|
| **1. Bullshit Core hexagon** | Pure rules engine, fully unit-tested. New package only. | **This spec** |
| **2. Generalize Session hexagon + reusable presentation** | Extract a `Game` abstraction so `SessionService`/`SessionGame`/`SessionRepository` serve any game; lift game-agnostic WS plumbing into a shared layer; add a Bullshit action/state adapter. | Later (own spec) |
| **3. Vue frontend** | Bullshit board: hand selection, claim display, call-BS, reveal animation. | Later (own spec) |

### Presentation reuse (informs the aggregate's public surface)

Session/presentation concerns split into **game-agnostic** (reusable) and **per-game** (new for Bullshit):

- *Reusable:* create & join game, seat claiming, `SessionToken`/`SessionPlayer`, presence, disconnect-forfeit + cleanup, rematch unanimity, STOMP config, the `Response`/`Error`/`Success` envelope, `SessionViewDto`/`SeatDto`/`JoinResponseDto`.
- *Per-game (new):* the action protocol (`discard`/`callBullshit`), the game-state DTOs (a `BullshitDto` exposing own-hand, opponents' hand counts, discard-pile size, current target, last claim, current player), and event payloads (`DiscardEventData`/`CallBullshitEventData`/`RevealEventData`).

**Design principle pushed onto Slice 1:** shape the aggregate's public surface to match what session/presentation already consume from `BatailleCorse` — `getId()`, `getPlayers()`, `getCurrentPlayer()`, `getAvailableActions(player)`, `isFinished()`, `getWinner()`, plus an N-player `forfeit(playerId)`. Do **not** build the shared `Game` interface in Slice 1 (YAGNI) — but matching method shapes makes the later extraction a lift, not a rewrite.

## Domain model (Slice 1)

### Aggregate root: `Bullshit` (id `BullshitId`)

State:
- `players: List<Player>` — Bullshit-local `Player(PlayerId, Hand)`, mirroring the existing one; reuses `frenchcards` `Hand`.
- `discardPile: DiscardPile` — flat face-down stack of the actual cards played; this is what a BS call hands to someone.
- `claimMode: ClaimMode` — injected strategy (default `AscendingRankClaimMode`), like `slapRules`/`penality` are injected into `BatailleCorse`.
- `currentTarget: ClaimTarget` — the value the current player must claim; produced and advanced by `claimMode`.
- `lastDiscard: Discard?` — `{ claimant: PlayerId, claimedTarget: ClaimTarget, actualCards: List<Card> }`. The only discard BS can be called on (most-recent-only). Null right after a pile is taken.
- `currentPlayerIndex` + an `IndexHandler`-style rotation.
- `pendingWinner: PlayerId?` — set when a player empties their hand; promoted to actual winner only when the call-BS window closes in their favour.
- `result: Result` — winner once decided.

### Strategy seam: `ClaimMode` / `ClaimTarget`

- `ClaimTarget` — value type wrapping the claim (rank mode: a `Rank`).
- `ClaimMode` interface:
  - `ClaimTarget initial()` — first/reset target (rank mode: `ACE`).
  - `ClaimTarget next(ClaimTarget current)` — progression (rank mode: ascending, wrap `K→A`).
  - `boolean matches(List<Card> cards, ClaimTarget target)` — truth check (rank mode: every card has that rank).
- `AscendingRankClaimMode implements ClaimMode` — the only implementation in Slice 1. Future `CyclingSuitClaimMode` is a new implementation injected at game creation.

### Actions (`Action.DISCARD`, `Action.CALL_BULLSHIT`)

**`discard(player, cards)`** — legal only when: it is `player`'s turn, game is live, `1 ≤ cards.size ≤ 4`, all `cards` are in `player`'s hand. The claimed target is always `currentTarget` (player chooses count + actual cards only). Effect: remove cards from hand → push onto `discardPile` as the new `lastDiscard` → `currentTarget = claimMode.next(currentTarget)` → advance turn. If the hand is now empty → `pendingWinner = player` (game not yet finished). **Decline semantics:** if a `pendingWinner` exists and the next player issues a `discard` (rather than calling BS), the `pendingWinner` wins at that moment (unchallenged bluff wins) and the incoming discard is not applied.

**`callBullshit(caller)`** — legal when: a `lastDiscard` exists, `caller != lastDiscard.claimant`, game is live. Reveal `lastDiscard.actualCards` and evaluate `claimMode.matches(actualCards, lastDiscard.claimedTarget)`:
- **Lie** (no match): `lastDiscard.claimant` takes the whole `discardPile` into hand; clear `pendingWinner` if it was the claimant. Then apply house rule #1 (pile clears, target resets to initial, claimant — the picker-upper — starts the next round).
- **Truthful** (match): `caller` takes the whole `discardPile`. If `pendingWinner == claimant`, the claimant **wins now** (truthful + empty hand → `result` = claimant). Otherwise apply house rule #2 (caller starts the next round at initial target).

### Other behaviour

- **`forfeit(playerId)`** — N-player: remove the player and their hand from rotation. If exactly one player remains, that player wins. No-op if the game is already finished (mirrors `BatailleCorse.concede`'s race-safety).
- **`getAvailableActions(player)`** — returns the subset of `{DISCARD, CALL_BULLSHIT}` currently legal for `player`, computed by attempting the same guards the action methods use (mirrors `BatailleCorse.getAvailableActions`).
- **Win condition** — single winner: first to empty their hand and survive the closing of the call-BS window. Game ends; no play for 2nd/3rd place.

### Exceptions

Game-specific checked exceptions mirroring the BatailleCorse style: `NotPlayersTurnException`, `FinishedGameException`, `CardsNotInHandException`, `InvalidDiscardCountException` (not 1–4), `CannotCallBullshitException` (no `lastDiscard`, or caller is the claimant). Names to be finalised during planning.

## Testing

Per project testing rules: **no Mockito on domain classes.** Plain unit tests with Builders + Fixtures — a `BullshitBuilder`, `BullshitFixtures`, and `PlayerFixtures`/`PlayerBuilder` analog, following the existing `core/domain` test conventions (`givenX_thenY` naming).

Coverage targets the rules that matter:
- Forced-ascending claim target; rank wrap-around `K→A`.
- `discard` guards: turn enforcement, 1–4 count bound, cards-in-hand.
- Truthful vs. lying BS resolution — liar takes pile / caller takes pile.
- The discard→react race (first action wins).
- Pending-winner confirmed on a next-player decline (unchallenged bluff wins).
- Win on truthful empty-hand final discard.
- `forfeit` collapsing rotation to a single winner; no-op when finished.
- `AscendingRankClaimMode` in isolation (initial/next/matches).

## Slice-1 boundary

**Delivers:** the `bullshit.core.domain` package — `Bullshit`, `BullshitId`, `Player`, `DiscardPile`, `Discard`, `ClaimMode` / `ClaimTarget` / `AscendingRankClaimMode`, `Result`, `Action`, exceptions — plus the full test suite.

**Explicitly excludes:** session management, WebSocket controller/DTOs/events, frontend, AI, and the suit-based `ClaimMode` variant. Those are Slices 2–3 and future work.
