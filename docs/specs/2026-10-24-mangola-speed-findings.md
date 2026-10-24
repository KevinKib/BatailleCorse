# Mangola: unstable animation speed (findings and proposal)

Written by Claude Opus 5.5 on 2026-10-24 from `origin/main` at `9c3deb02`, after the owner's playtest: "j'ai
l'impression que la vitesse du jeu n'est pas stable il faudrait 1. la stabiliser 2. la rendre plus lente encore
pour qu'on puisse suivre (le mode normal passe en fast quand il y a des captures j'ai l'impression)". Findings and a
proposal only: **nothing is implemented; the numbers wait for the owner's explicit agreement** (table "Current /
proposed" below). Related: `2026-10-24-mangola-direction-design.md`, `2026-10-24-mangola-button-colour-findings.md`.

## Answer in short

The owner's impression is right. The sowing animation does not have a speed per level; it has a **time budget per
move** shared among the seeds, plus a halving inside the move and a cap on the whole move. A capture adds the
captured seeds to the sowing, so a move with captures has many more seeds to share the same budget, and each
seed goes faster. Measured on simulated games: **in Normal, a move with two captures drops a seed every 114 ms on
average, faster than Fast without a capture (184 ms)**; with three captures or more Normal runs at 68 ms a seed,
close to Fast's floor (60 ms). Within one move the tempo also doubles from the first seed to the last. Nothing
else (network, queue, bot) explains the impression.

## How the tempo is computed today [V]

`frontend/src/model/mangola/trace.ts` (`SPEED_PROFILES`, `tickMs`, `planSowing`), played by
`frontend/src/composables/useSowingAnimation.ts`:

1. **Tick of the move** (`tickMs`): `clamp(tickBudgetMs / drops, tickMinMs, tickMaxMs)`, where `drops` is the
   number of seeds dropped by the whole move, captures and relays included. Normal: `4800 / drops` within 120 to
   560 ms. Up to 8 seeds the tick is 560 ms; at 20 seeds 240 ms; from 40 seeds 120 ms.
2. **Acceleration inside the move** (`planSowing`, `tickAt`): the tick shrinks linearly from the base to half of
   it between the first and the last seed (`base * (1 - 0.5 * k / (drops - 1))`).
3. **Pauses**: every step that is not a drop (pick, relay, capture, resume after a capture, skip of an empty pit)
   lasts `max(minPauseMs, 1.5 ticks)`: 400 ms or more in Normal.
4. **Cap** (`maxTotalMs`): a move planned longer than 7 s (Normal) or 4.5 s (Fast) is scaled down as a whole,
   **below** `tickMinMs` (the clamp of step 1 is applied before the scaling).
5. Other fixed times: the hand slides between pits in `handSlideMs` (180 ms Normal, CSS transition in
   `MangolaBoard.vue`), which is longer than the tick as soon as the tick drops under 180 ms, so the hand never
   rests on a pit; an opponent's move first rests 500 ms on its chosen pit (`leadInMs`), my own move does not.
6. `useSowingAnimation.ts` queues the states that arrive during a sowing; with more than `MAX_QUEUED_STATES` (2)
   waiting, the older ones are dropped and the newest shown at once. A bot answers 0.7 to 1.3 s after the state,
   so in a game against a bot the queue holds one state at most: this rule does not fire in normal play.

The rule was a decision of the base spec ("tick 2400 ms / seeds dropped, clamped to 60..280 ms per seed, halving
toward the end of the trace ... total cap 4.5 s", `2026-10-15-mangola-design.md`, UI decision 2), and Normal was
later defined as exactly twice Fast (`traceSpeed.test.ts`). Both make the speed depend on the length of the move.

## Measurements [C]

Throwaway script (not committed) that runs the app's own `planSowing` on real traces produced by the app's
TypeScript port of the engine (`model/mangola/sowingFixtures.ts`): 2,000 games from random layouts, players
choosing uniformly among non-endless moves, **117,281 moves**. Random players capture less than good players,
so the share of capture moves below is a lower bound. 73 % of moves have no capture (median 5 seeds dropped);
moves with a capture have a median of 22 seeds and a 90th percentile of 64.

| Moves | Count | Mean seeds | Normal: first tick | Normal: mean ms per seed | Normal: mean length | Normal: hit the 7 s cap | Fast: mean ms per seed | Fast: mean length |
|-------|-------|-----------|--------------------|--------------------------|---------------------|-------------------------|------------------------|-------------------|
| no capture | 86,176 | 8.7 | 490 | 367 | 4.0 s | 3 % | 184 | 2.0 s |
| 1 capture | 20,907 | 23.8 | 288 | 216 | 6.3 s | 25 % | 110 | 3.3 s |
| 2 captures | 6,836 | 40.1 | 152 | **114** | 6.9 s | 72 % | 62 | 3.9 s |
| 3 captures or more | 3,362 | 56.8 | 90 | **68** | 7.0 s | 99 % | 41 | 4.4 s |

Two real traces timed in Normal (durations in ms; `PI` pick, `DR` drop, `CA` capture, `RE` relay or resume,
`SK` skip of an empty pit):

- 6 seeds, no capture: `PI840 DR560 DR504 DR448 RE588 DR392 DR336 SK420 DR280`: the last seed takes half the
  time of the first.
- 36 seeds, two captures: `PI368 DR123 DR121 ... CA368 ... DR63 SK368 DR61`: every seed under 125 ms (Fast's
  short-move tempo is 280 ms), the cap pulls the pauses under their 400 ms floor and the last seeds under the
  120 ms floor.

So, as perceived: a quiet move in Normal plays at about half a second a seed and slows to nothing between seeds;
the next move, with captures, plays four to eight times faster; Normal with captures looks like Fast, or faster.

## Proposal (not implemented; owner's agreement needed)

Principle: **one tempo per move, constant from its first seed to its last**, and a tempo that depends on the
level, not on the move, unless the move is very long.

- Tick of a move = `clamp(budget / drops, floor, base)`, the same for every seed of the move (no halving).
- A pause = 1.5 ticks of the move (no separate minimum: the floor bounds it).
- No cap on the whole move (the budget and the floor play that role); Skip and a tap on the board still end it.
- The hand slides in half a tick, so it always rests on a pit.
- Moves of up to `budget / base` seeds (40 in Normal) all play at exactly `base`; longer ones play slower than
  that only down to `floor`, still constant within the move.

### Current / proposed

Changed rows are marked **changed**. "Slow" is a possible fourth level, only if the owner wants one: it changes the
shared speed button, which President uses too (`animation/core/speed.ts`, `AnimationSpeedButton`), so President
would need a factor for it.

| Parameter | Normal current | Normal proposed | Fast current | Fast proposed | Slow (new, optional) | |
|-----------|----------------|-----------------|--------------|---------------|----------------------|---|
| Tick at the start of a short move (`tickMaxMs`, "base") | 560 ms | 500 ms | 280 ms | 250 ms | 800 ms | **changed** |
| Tick at the end of the same move | 280 ms (halved) | 500 ms (constant) | 140 ms | 250 ms | 800 ms | **changed** |
| Budget shared by the seeds (`tickBudgetMs`) | 4,800 ms | 20,000 ms | 2,400 ms | 8,000 ms | 32,000 ms | **changed** |
| Floor of a tick (`tickMinMs`) | 120 ms, then broken by the cap | 250 ms, never broken | 60 ms | 120 ms | 400 ms | **changed** |
| Pause (pick, relay, capture, resume, skip) | max(400 ms, 1.5 ticks), then scaled by the cap | 1.5 ticks (750 ms at base) | max(200 ms, 1.5 ticks) | 1.5 ticks (375 ms) | 1.5 ticks (1,200 ms) | **changed** |
| Cap on a whole move (`maxTotalMs`) | 7,000 ms | none | 4,500 ms | none | none | **changed** |
| Hand slide (`handSlideMs`) | 180 ms | half a tick (250 ms at base) | 90 ms | half a tick (125 ms) | half a tick (400 ms) | **changed** |
| Pause on the opponent's chosen pit (`leadInMs`) | 500 ms | 700 ms | 250 ms | 350 ms | 1,000 ms | **changed** |
| Instant | 0 | 0 | | | | unchanged |
| Default level | Normal | Normal | | | | unchanged |

Effect on the same simulated moves [C] (mean tick, then the length of a move: mean / 90th percentile / longest):

| Moves | Normal proposed | Fast proposed | Slow proposed |
|-------|-----------------|---------------|---------------|
| no capture | 497 ms; 6.5 / 11.8 / 66 s | 248 ms; 3.2 / 5.9 / 32 s | 796 ms; 10.3 / 18.8 / 106 s |
| 1 capture | 480 ms; 15.4 / 27.1 / 57 s | 234 ms; 7.3 / 11.3 / 27 s | 767 ms; 24.6 / 43.4 / 91 s |
| 2 captures | 450 ms; 23.5 / 29.2 / 76 s | 210 ms; 10.7 / 12.3 / 36 s | 720 ms; 37.6 / 46.6 / 121 s |
| 3 captures or more | 388 ms; 28.5 / 33.3 / 58 s | 173 ms; 12.7 / 16.0 / 28 s | 621 ms; 45.7 / 53.2 / 93 s |
| all moves | 488 ms; 9.7 / 24.8 / 76 s | 241 ms; 4.6 / 10.9 / 36 s | 781 ms; 15.5 / 39.7 / 121 s |

Today Normal lasts 4.7 s on average and never more than 7 s; Fast 2.4 s and never more than 4.5 s.

### The trade-off to decide

A stable tempo makes long moves long: in proposed Normal a move with two captures lasts about 23 s instead of 7 s.
That is the price of following every seed. Two levers, both one number each:

- a **lower floor** (Normal 200 ms instead of 250 ms) shortens the longest moves by a fifth and keeps them steady
  within the move;
- a **lower budget** (Normal 12,000 ms: constant 500 ms up to 24 seeds) shortens capture moves but brings back a
  difference between short and long moves (still none inside a move).

Recommendation: the Normal and Fast columns as proposed, no Slow level at first (Normal becomes slower and steady,
which is what was asked), Skip as today; then the owner tries it and asks for Slow or a lower floor if needed.

## Owner questions

- **S1** Agree with the proposed Normal and Fast values (table above)? Or other values per row.
- **S2** Add a fourth level Slow (shared with President's speed button)? Recommended: not now.
- **S3** Accept long animations for long moves (up to about a minute in rare cases; Skip available)? Or a lower
  floor (S1).

## If agreed: the follow-up PR

One PR, `fix: steady Mangola sowing tempo`: `model/mangola/trace.ts` (`SPEED_PROFILES` gains `floorMs`/`budgetMs`,
loses `maxTotalMs`; `planSowing` constant tick; pause and slide from the tick), `traceSpeed.test.ts` and
`trace.test.ts` rewritten test-first (the "Normal is exactly twice Fast" and "capped at 7 s" tests change by
design), `useSowingAnimation.ts` (lead-in from the profile, unchanged code), `MangolaBoard.vue` (slide time from
the move's tick), the base spec's UI decision 2 updated. Cypress: only `mangola-animation-speed.cy.ts`. It touches
`MangolaBoard.vue` like the direction plan's D3: the second to merge rebases. Frontend only; owner's accord before
merge (Mangola).
