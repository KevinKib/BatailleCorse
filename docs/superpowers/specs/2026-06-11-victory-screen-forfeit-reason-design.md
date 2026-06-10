# Victory screen forfeit reason, and Human-default opponent toggle

Date: 2026-06-11

## Overview

Two unrelated edits:

1. **Show *why* the winner won when the game ended by forfeit.** Today the
   victory screen always reads "You beat {opponent}!" — even when the opponent
   resigned or their connection dropped, which makes a forfeit win look like a
   played-out win. Surface the reason on the winner's screen: distinguish a
   deliberate **resignation** from a **disconnection**.
2. **Default the create-game opponent toggle to Human.** It currently defaults
   to Computer.

## Change 1 — Forfeit reason on the victory screen

### Scope decisions

- **Winner perspective only.** The loser keeps the plain "{opponent} won."
  Telling the resigner "you resigned" is redundant (they just clicked it), and
  the disconnected player is gone — they only see a screen if they reconnect to
  an already-finished game, a corner of a corner. The loser-side branch buys
  nothing, so we don't build it.
- **Two reasons, kept distinct:** `RESIGNED` (explicit forfeit / leaving an
  in-progress multiplayer game) and `DISCONNECTED` (the 60s disconnect timer
  fired). The distinction carries a real signal — a disconnect win feels
  unearned, a resignation feels legitimate.
- **No `NORMAL`/out-of-cards reason value.** A natural loss is already expressed
  by the loser's empty hand; adding a third enum value would be redundant state.
  A forfeit reason is *present only* for a player who forfeited.

### Architectural placement (the important part)

The card-game domain must **not** learn about disconnection. "Disconnected" is a
transport fact (a WebSocket dropped, a timer fired), not a rule of the game. To
the domain, a timed-out socket and a clicked "resign" are the *same* event: this
seat gives up, the other wins. The reason classification is therefore a
**session-hexagon concern**, owned once and reusable by future games — not baked
into `BatailleCorse`.

This also respects the project's "drive end-of-game off *state*, not per-action
events" rule: the reason is stored alongside the game in the session layer (not
only on the transient `FORFEIT` event), so a refresh/resync still carries it.

Boundary summary:

- **Generic (session hexagon):** detecting a disconnect, the forfeit *decision*,
  and the *reason* (`RESIGNED` vs `DISCONNECTED`). The session already models a
  game-agnostic `Seat (gameId, playerId)` — exactly the identity needed to
  attribute a forfeit without knowing any rules.
- **Game-specific (BatailleCorse domain):** the *consequence* of a forfeit. In
  2-player, conceding hands the win to the other seat. A future 4-player game
  would remove the seat and continue. The session can only *tell* a game "seat X
  forfeits"; the game's own rules resolve it. That call — `game.concede(seat)` —
  is the natural port between generic session and specific game, which is why it
  stays **transport-neutral (no reason parameter)**.

### Current architecture (relevant pieces)

- **Domain:** `BatailleCorse.concede(PlayerId loser)` is explicitly 2-player and
  sets `result = new Result(winner)`. `Result` is `record Result(Player
  winningPlayer)`; `Result.update(...)` derives the natural-win winner from card
  counts. (`backend/.../core/domain/{BatailleCorse,Result}.java`)
- **Both forfeit paths converge** on
  `DisconnectForfeitService.forfeit(Seat)` → `game.concede(seat.playerId())` →
  broadcast `EventType.FORFEIT`. The explicit `/app/forfeit` endpoint (used by
  the leave-game guard) and the 60s disconnect timer are *already distinct call
  sites* — only they know which reason applies.
  (`backend/.../websocket/presentation/v1/DisconnectForfeitService.java`,
  `BatailleCorseWebSocketController.java:177`)
- **`OPPONENT_DISCONNECTED`** is a transient "opponent dropped, 60s countdown"
  warning — *not* the game-ending signal. It must not be confused with the
  terminal forfeit.
- **State DTO:** `BatailleCorseDto.from(game)` carries `winner: PlayerIdDto |
  null` plus a per-player list. There is no per-player extra status.
- **Frontend:** `GameScreen.vue` end-overlay (lines ~105–116) reads
  `didIWin`/`opponentLabel` off game state via the `BatailleCorse` model
  (`frontend/src/model/BatailleCorse.ts`). `Response.ts` already lists `FORFEIT`
  in its `eventType` union.

### Design

**Session hexagon — own the reason.**

- Add a `ForfeitReason { RESIGNED, DISCONNECTED }` enum in the session/forfeit
  area of the presentation-WebSocket layer (next to `DisconnectForfeitService`
  and `Seat`).
- `DisconnectForfeitService.forfeit(Seat)` → `forfeit(Seat, ForfeitReason)`. The
  disconnect timer schedules `forfeit(seat, DISCONNECTED)`; the `/app/forfeit`
  controller endpoint calls `forfeit(seat, RESIGNED)`.
- Record the reason **per game, per seat**, in a small session-layer store that
  lives as long as the game (so it survives repeated broadcasts and resyncs).
  Keyed by `(gameId, playerId)`. The existing finished-game eviction
  (`GameCleanupService`) should drop these records when the game is evicted.

**Domain — unchanged contract.** `concede(PlayerId loser)` stays exactly as is
(transport-neutral, 2-player, no reason). `Result` is untouched.

**DTO — merge the reason in.** The per-player entry in `BatailleCorseDto` gains a
nullable `forfeitReason` (`"RESIGNED" | "DISCONNECTED" | null`; null = this
player did not forfeit). It is populated from the session-layer reason store when
the DTO is built. Every place that builds `BatailleCorseDto.from(game)` for a
given game must source the same reason record so a late resync is consistent.

**Frontend — model decides, view maps.**

- Add `forfeitReason` to the per-player state type (`Response.ts` / wherever the
  player is parsed) and the `BatailleCorse` model.
- The model exposes the *opponent's* exit, e.g. `opponentForfeitReason(myIndex)`
  returning `RESIGNED | DISCONNECTED | null` (logic lives in the model per
  project convention; the component only delegates).
- `GameScreen.vue` maps it to copy. End-screen strings live in one place (a small
  labels map / single source of truth):

  | Outcome | Winner sees | Loser sees |
  |---|---|---|
  | ran out of cards | `You beat {opp}!` | `{opp} won.` *(unchanged)* |
  | RESIGNED | `{opp} resigned.` | `{opp} won.` |
  | DISCONNECTED | `{opp} disconnected.` | `{opp} won.` |

### Known boundary, intentionally not addressed now

`DisconnectForfeitService` currently constructs `BatailleCorseDto` directly —
generic session code reaching into one game's DTO. When a second game arrives
this is where a "game presentation port" would formalize the seam. We do **not**
build that abstraction now (one game = speculative); we only keep `concede`
transport-neutral so the extraction stays trivial. Likewise, the "60s grace then
forfeit" *reaction policy* may eventually need to be per-game (a turn-based async
game might prefer to pause indefinitely) — not built now, just flagged.

### Testing

- **Domain** (`BatailleCorseConcedeTest`): unchanged behavior — concede still
  sets the winner; no reason in the domain. (No Mockito on domain; builders /
  fixtures per project convention.)
- **Session/forfeit**: `forfeit(seat, RESIGNED)` and `forfeit(seat, DISCONNECTED)`
  record the matching reason; the reason appears on the broadcast state for that
  seat; the timer path records `DISCONNECTED`, the `/app/forfeit` path records
  `RESIGNED`; eviction clears the record.
- **DTO**: `forfeitReason` serializes per player (null when no forfeit).
- **Frontend** (`GameScreen` component test): the three winner-side subtitle
  variants render from state.

## Change 2 — Default opponent to Human

`frontend/src/view/alpha/StartGame.vue` initializes `const vsComputer =
ref(true)`. Flip to `ref(false)`. Consequences are already handled by existing
template bindings:

- The opponent toggle highlights **Human** by default.
- The difficulty slider stays hidden (it is `v-if`'d on `vsComputer`).
- The submit button label becomes "Create Game" (the human branch).
- `startGame()` computes `gameMode = 'multiplayer'` by default.

Check `StartGame`'s tests for any assertion that depends on the old default and
update them to match (and add/confirm coverage that Human is the default
selection).

## Out of scope

- 4-player game logic, multi-game generalization machinery (game registry,
  formal ports/adapters), and per-game disconnect policy. The design only keeps
  boundaries clean for those futures.
- Loser-perspective forfeit messaging.
- Any change to the `OPPONENT_DISCONNECTED` countdown warning.
