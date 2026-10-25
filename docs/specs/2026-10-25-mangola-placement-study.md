# Mangola starting placement: study and proposal

**Status:** study done, proposal awaiting the owner's decision. **No bot behaviour changed** in this work: the
harness and the analysis live in test scope only. Plan: `docs/plans/2026-10-25-mangola-placement-plan.md`.
Produced by Opus 5.5 on 2026-10-25 from `origin/main` at `5e567cc0`.

Owner request: *"j'aimerais bien déterminer à terme quels sont les meilleurs placements"*: which layouts of the 32
seeds on one's own 16 pits are the best, given that both seats place at the same time, hidden from each other
(base spec `2026-10-15-mangola-design.md`, "The placement phase"), and how the Expert and Medium bots should place.

## Summary

1. **The first mover has a large edge.** Expert against Expert, side A (the starter) wins **77 to 78 %** of the
   games of a round robin of 78 to 89 layouts, and **97 %** (29 of 30) on boards drawn by the random set-up. No game
   was drawn (no threefold repetition, no game reached the 600-ply cap); games last 19 to 20 plies on average.
2. **One family dominates: "four pits of 8, one pit in four"** (the catalogue pattern `{8,0,0,0}` and its four
   rotations, C17 to C20). C18 (`8` on d and h of the inner row, on a and e of the outer row) is the best layout
   against a uniform field on both sides (0.94 as starter, 0.89 as second player, field of 89 layouts) and, **when
   the side is not known**, it is a pure equilibrium of the seat-swapped game in the base pool. The worst family is
   `{4,4,4,4,0,0,0,0}` (four 4s in a row): 0.05 to 0.09.
3. **Most layouts are dominated.** In the final pool of 89, 32 starter layouts and 38 second-player layouts are
   weakly dominated by another single layout (63 and 59 of the base 78).
4. **The bot knows its side, and the best layouts differ by side.** Starting is about attacking (dense or
   `{8,0,0,0}` layouts); playing second is about surviving the first move (sparse `{8,0,0,0}`-like layouts, which
   give the starter almost no capture). The side-aware game (rows: the starter's layout, columns: the second
   player's) has a value of **0.667 for the starter** with mixes of 6 and 9 layouts.
5. **The current Expert places well as second player, poorly as starter.** Its side-B choices are already
   `{8,0,0,0}` variants (0.61 to 0.67 as second player, against 0.14 for random layouts); its side-A choices are
   dense layouts no better than random ones (0.83 to 0.91 as starter, random 0.86, best 0.95). Its layout score
   correlates only moderately with the tournament (Spearman 0.63 for side A, 0.48 for side B against a uniform
   field; 0.30 and 0.55 against the equilibrium), because the mean over its 4 typical opponents is dominated by
   the "forced win" values (about 1,000,000) its 300-node search finds against the weak ones.
6. **Drawing from the side-aware list beats the current Expert placement:** **0.633 ± 0.047** over 400 games at
   4,000 nodes a move, **0.61 ± 0.096** over 100 games at 40,000 nodes (the production budget on this machine).
   It is **less exploitative against weak placements:** against Medium's layouts it scores 0.77 where the current
   Expert scores 0.88, and 0.82 against Easy's where the current Expert scores 0.90. That is the trade-off the owner
   decides (open question Q1).
7. **Medium places no better than Easy today** (0.483 ± 0.049 with the same move search on both sides): half of its
   layouts are random and the catalogue it draws the other half from is, on average, worse than random as starter
   (0.72 against 0.86). Drawing uniformly from the side-aware list instead scores 0.768 against current Medium.
8. **The evaluation weights do not matter for placement.** With weights (10,0,0,0), (10,6,10,6) or (10,1,2,1)
   instead of (10,3,5,3), 97 to 99 % of the ordered pairs keep the same result and the rankings are unchanged
   (Spearman 0.95 to 0.99); the layout score ranking is unchanged too (0.999). **The node budget matters little:**
   at 40,000 nodes a move instead of 4,000, 96.3 % of the pairs keep their result (Spearman 0.86 as starter, 0.97 as
   second).
9. **The list is the best found, not a proven equilibrium.** Each round of the approximate double oracle found
   new layouts that entered the mixes (starter's value 0.600, then 0.615, then 0.667). More rounds may still change
   the list (plan, chunk P0).

## Method

### Harness (test scope, never in the production jar)

All in `backend/src/test/java/org/kevinkib/cardgames/mangola/domain/bot/`:

| Class | Role | Tested by |
|-------|------|-----------|
| `StudyExpert` | A copy of `ExpertMangolaBot` (negamax, alpha-beta, iterative deepening, move ordering, evaluation, layout search) whose weights and layout node cap are parameters and whose budget is a **node count only** (no clock). With the production weights it returns exactly the production search result and layout for the same generator. | `StudyExpertTest` (40 positions, identical `SearchResult`; 8 layouts identical to `decideLayout`) |
| `PlacementTournament` | Games between fixed layouts through the real `Mangola` aggregate (threefold repetition, endless rule, stuck start), a round robin of every ordered pair, its extension with new layouts, and duels of two layout policies (seats alternated). Every game's seed is derived from the base seed and the game's coordinates, so results do not depend on threads. | `PlacementTournamentTest` (forced win in 3 plies, counts, zero-sum, thread independence, extension = full run, duels) |
| `ZeroSumGame` | Alternating fictitious play (Brown 1951; Robinson's theorem gives convergence for zero-sum games) with the guaranteed lower and upper values, weak dominance, maximin. | `ZeroSumGameTest` (rock-paper-scissors, a 2x2 game with a known mix, dominance) |
| `MangolaPlacementStudy` | The on-demand runner (all stages below). Not matched by Surefire's includes and disabled without `-Dmangola.study`. | `MangolaPlacementStudyTest` (Spearman, layout display, pool) |

Reproduce (about 40 minutes of wall time on 4 threads; results are written to `backend/target/mangola-study/`, and
a second run reuses the base round robin from there):

```sh
cd backend && mvn -s ../deploy/docker/settings.xml test -Dtest=MangolaPlacementStudy \
  -Dmangola.study=all -Dmangola.study.threads=4 -Dsurefire.failIfNoSpecifiedTests=false
```

Stages: `tournament`, `layoutscore`, `budget`, `weights`, `oracle`, `duel`, `hybrid` (comma list, or `all`).

### Settings

| Setting | Value |
|---------|-------|
| Rules | `MangolaRules.OWNER` (endless sowing illegal, two seeds to move) |
| Move search | `StudyExpert` with production weights (10, 3, 5, 3), **4,000 nodes a move** (production at 300 ms reaches about 40,000 nodes on this machine: mean of 15 random starts) |
| Robustness budget | 40,000 nodes a move (subset and one duel) |
| Ply cap | 600 (a capped game counts as a draw; none happened) |
| Tournament seed | 20261025, **2 games per ordered pair** (the two games agree in 99.2 to 99.3 % of the pairs: the expert is almost deterministic, only its tie-breaks are random) |
| Duel seed | 900001, 400 games per duel (100 at 40,000 nodes), the first policy starting every other game |
| Fictitious play | 2,000,000 iterations; gap between the guaranteed values at most 0.0001 |
| Mix pruning | layouts under 2 % dropped, the rest renormalised |

### The pool

78 distinct layouts: the 53 layouts of the catalogue (`MangolaLayouts`: 14 periodic patterns and their rotations,
C00 to C52), 15 random layouts (the random set-up, seeds 1000 and up, R00 to R14) and the layouts the production
expert chooses with seeds 1 to 6 on each side (EA1 to EA6, EB1 to EB6; two duplicates of catalogue layouts
dropped). The oracle added 11 more (O1xx, O2xx): 89 in the end.

A layout is written as its owner sees it: the inner row then the outer row, each from column a to column h (the
16 counts in loop order are given in the tables of the proposal).

### Two games, both analysed

- **Seat-swapped (side unknown):** `M[i][j]` is the mean of the game where `i` starts against `j` and the game
  where `j` starts against `i`: zero-sum and symmetric, the first-move advantage cancels out. It answers "which
  layout is best if I do not know whether I start".
- **Side-aware:** `S[i][j]` is the starter's score with layout `i` against layout `j` on side B. The bot knows its
  side when it places (side A starts, `MangolaBot.decideLayout`), so this is the game the bots actually play; its
  row mix is the starter's strategy and its column mix the second player's.

### Approximate double oracle

Two rounds. In each, for each side, three hill climbs (first improvement, 40 steps, moving one or two seeds,
endless-only layouts skipped) against the opponent's current mix (its layouts over 2 %, weighted, one game each
with common seeds), started from the two best layouts of the pool against that mix and from one random layout. New
layouts join the pool, the round robin is extended (same seeds, so it equals a full run) and the mixes recomputed.

### Validation

The mixes are fitted to the tournament's games; the duels replay the policies with a fresh seed, layouts drawn
anew each game, and include the current bots' own layout code (`ExpertMangolaBot.decideLayout` with an unlimited
clock, `GreedyMangolaBot`, `RandomMangolaBot`). Since the expert is almost deterministic, a fresh seed mostly
re-draws the layouts; the 40,000-node checks are the test of whether the results are an artefact of the budget.

### CPU used

About **2.6 CPU-hours** in all (about 45 minutes of wall time on 4 of the 16 cores): base round robin 2,756 s CPU,
budget subset 814 s, three weight subsets 538 s, two oracle rounds about 2,100 s (climbs and extensions), 13 duels about 3,000 s.

## Results

### Per layout (final pool of 89, uniform field, 178 games per side, standard error at most 0.037)

| Layout | Inner a..h / outer a..h | As starter | As second | Mean |
|--------|-------------------------|-----------:|----------:|-----:|
| C18 | `00080008` / `80008000` | 0.938 | 0.888 | 0.913 |
| C19 | `00800080` / `08000800` | 0.933 | 0.837 | 0.885 |
| C17 | `80008000` / `00080008` | 0.955 | 0.764 | 0.860 |
| C20 | `08000800` / `00800080` | 0.944 | 0.747 | 0.846 |
| EB1 (expert, side B) | `00080008` / `70008100` | 0.910 | 0.618 | 0.764 |
| O1B0 (oracle) | `00801080` / `05010801` | 0.893 | 0.629 | 0.761 |
| EB5 (expert, side B) | `00601080` / `18000800` | 0.826 | 0.669 | 0.747 |
| O2A1 (oracle) | `00700080` / `18000800` | 0.927 | 0.489 | 0.708 |
| C00 (classic checkerboard) | `40404040` / `04040404` | 0.719 | 0.275 | 0.497 |
| C04 (two per pit) | `22222222` / `22222222` | 0.921 | 0.236 | 0.579 |
| EA1 to EA6 (expert, side A) | dense | 0.83 to 0.91 | 0.11 to 0.23 | |
| R00 to R14 (random), mean | | 0.860 | 0.141 | 0.500 |
| Catalogue, mean of 53 | | 0.718 | 0.214 | 0.466 |
| C39 (worst) | `00444400` / `00444400` | 0.090 | 0.011 | 0.051 |

### Equilibria

| | Base pool (78) | After oracle round 1 (83) | After round 2 (89) |
|---|---|---|---|
| Seat-swapped value / mix | 0.5, pure C18 | 0.5, pure C18 | 0.5, O2B1 0.30, C18 0.20, C19 0.20, C17 0.11, O1A1 0.10, C31 0.05, EB1 0.05 |
| Side-aware: starter's value | 0.600 | 0.615 | **0.667** |
| Starter's mix | C31 0.4, C18 0.4, R14 0.2 | C18 0.31, C19 0.23, C30 0.15, R14 0.15, O1A1 0.08, C01 0.08 | see the proposal |
| Second player's mix | C18 0.4, C19 0.4, EB5 0.2 | C19 0.27, EB5 0.25, O1B0 0.12, C18 0.12, C16 0.08, ... | see the proposal |
| Dominated (starter / second / swapped) | 63 / 59 / 53 | 56 / 60 / 53 | 32 / 38 / 29 |

No pure layout guarantees anything in the side-aware game: every starter layout loses to some second-player layout
and the reverse (maximin 0, minimax 1), which is why a mix is needed. The mixes are small because the matrix is
almost all 0s and 1s (the games are nearly deterministic).

### Calibration

| Question | Measure | Answer |
|----------|---------|--------|
| Do the evaluation weights matter for placement? | Subset of 20 layouts (every 4th of the pool), 800 games each, weights (10,0,0,0), (10,6,10,6), (10,1,2,1) against (10,3,5,3) | **No.** 97.0, 97.3, 99.0 % of ordered pairs keep their result; Spearman as starter 0.99, 0.98, 0.99, as second 0.96, 0.95, 0.95. The layout score ranking is the same (Spearman 0.999 and 1.000). |
| Does the node budget matter? | Same subset at 40,000 nodes, 400 games | **Little.** 96.3 % of pairs keep their result; Spearman 0.86 as starter, 0.97 as second; the starter wins 78.5 % (78 % at 4,000). |
| Does the 300-node layout search predict the tournament? | Its score (mean of 8 draws of its 4 typical opponents) for every layout of the base pool | **Moderately.** Spearman 0.63 (side A, uniform field), 0.30 (side A, against the second player's mix), 0.48 (side B, uniform), 0.55 (side B, against the starter's mix). Its side-A top 5 (C21, EA5, C26, EA2, EA1) are dense layouts worth about a random one; its side-B top 5 (C20, C18, EB5, C19, EB6) are right. Cause: the raw mean is dominated by forced-win values (about 10^6) found against the weakest typical opponents. |
| Does the first mover have an advantage? | Every game of the tournament; random boards | **Yes, large:** 77 to 78 % for the starter in the round robin, 97 % on random boards, value 0.667 for the starter at the side-aware equilibrium. `firstPlayer: random` (the default) is what keeps a series fair. |

### Validation duels (seed 900001, the expert's moves at 4,000 nodes unless stated)

| First policy vs second | Games | Score of the first (95 %) | Wins starting / second |
|------------------------|------:|--------------------------:|-----------------------:|
| Side-aware list (proposed) vs current Expert | 400 | **0.633 ± 0.047** | 125/200, 128/200 |
| Side-aware list vs current Expert, 40,000 nodes | 100 | **0.610 ± 0.096** | 33/50, 28/50 |
| Side-blind list vs current Expert | 400 | 0.610 ± 0.048 | 146/200, 98/200 |
| Hybrid (current search + both lists) vs current Expert | 400 | 0.568 ± 0.049 | 70/200, 157/200 |
| Hybrid vs side-aware list | 400 | 0.438 ± 0.049 | 93/200, 82/200 |
| Side-aware list vs current Medium | 400 | 0.770 ± 0.041 | 182/200, 126/200 |
| Current Expert vs current Medium | 400 | 0.880 ± 0.032 | 190/200, 162/200 |
| Hybrid vs current Medium | 400 | 0.898 ± 0.030 | 191/200, 168/200 |
| Side-aware list drawn uniformly vs current Medium | 400 | 0.768 ± 0.041 | 178/200, 129/200 |
| Side-aware list vs Easy | 400 | 0.818 ± 0.038 | 190/200, 137/200 |
| Current Expert vs Easy | 400 | 0.900 ± 0.029 | 200/200, 160/200 |
| Hybrid vs Easy | 400 | 0.908 ± 0.028 | 199/200, 164/200 |
| Current Medium vs Easy | 400 | 0.483 ± 0.049 | 178/200, 15/200 |

The hybrid is the current search with the side's list scored first and the other side's list added to its four
typical opponents. It places as well as the list when second (157/200) but badly when it starts (70/200): its
side-A scoring still prefers the dense layouts that beat the weak typical opponents.

## Proposal

### Expert: draw from a side-aware list (option A, recommended)

The Expert stops searching for its layout and draws it from a fixed list for its side with the probabilities below
(the pruned mixes after two oracle rounds; P0 of the plan re-runs more rounds and may change them before they are
frozen). Rationale: it is the only measured policy that beats every other one head to head (0.633 against the
current Expert, 0.562 against the hybrid), costs no layout time, and is not exploitable inside the pool. Its cost:
about 8 to 11 points less against weak placements (0.77 against Medium's, 0.82 against Easy's, where the current
Expert scores 0.88 and 0.90): see Q1.

**Starter list (side A):**

| Layout | Probability | Inner a..h / outer a..h | Counts in loop order |
|--------|------------:|-------------------------|----------------------|
| C17 | 0.333 | `80008000` / `00080008` | `[8,0,0,0,8,0,0,0,8,0,0,0,8,0,0,0]` |
| O2A1 | 0.250 | `00700080` / `18000800` | `[0,0,7,0,0,0,8,0,0,0,8,0,0,0,8,1]` |
| O1A1 | 0.167 | `04221223` / `32303230` | `[0,4,2,2,1,2,2,3,0,3,2,3,0,3,2,3]` |
| C30 | 0.083 | `30323032` / `23032303` | `[3,0,3,2,3,0,3,2,3,0,3,2,3,0,3,2]` |
| C31 | 0.083 | `03230323` / `32303230` | `[0,3,2,3,0,3,2,3,0,3,2,3,0,3,2,3]` |
| O2B1 | 0.083 | `00701070` / `17010701` | `[0,0,7,0,1,0,7,0,1,0,7,0,1,0,7,1]` |

**Second-player list (side B):**

| Layout | Probability | Inner a..h / outer a..h | Counts in loop order |
|--------|------------:|-------------------------|----------------------|
| C18 | 0.235 | `00080008` / `80008000` | `[0,0,0,8,0,0,0,8,0,0,0,8,0,0,0,8]` |
| C17 | 0.167 | `80008000` / `00080008` | `[8,0,0,0,8,0,0,0,8,0,0,0,8,0,0,0]` |
| C16 | 0.087 | `06020602` / `20602060` | `[0,6,0,2,0,6,0,2,0,6,0,2,0,6,0,2]` |
| C20 | 0.086 | `08000800` / `00800080` | `[0,8,0,0,0,8,0,0,0,8,0,0,0,8,0,0]` |
| O2B1 | 0.086 | `00701070` / `17010701` | `[0,0,7,0,1,0,7,0,1,0,7,0,1,0,7,1]` |
| C05 | 0.086 | `44004400` / `00440044` | `[4,4,0,0,4,4,0,0,4,4,0,0,4,4,0,0]` |
| C19 | 0.086 | `00800080` / `08000800` | `[0,0,8,0,0,0,8,0,0,0,8,0,0,0,8,0]` |
| EB5 | 0.086 | `00601080` / `18000800` | `[0,0,6,0,1,0,8,0,0,0,8,0,0,0,8,1]` |
| O2B0 | 0.082 | `00600070` / `08011702` | `[0,0,6,0,0,0,7,0,2,0,7,1,1,0,8,0]` |

Alternatives measured: keep the current search (option C: best against weak placements, loses 0.367 to option A);
the hybrid (option B: 0.568 against the current Expert, 0.438 against option A, best against weak placements). An
unmeasured option D, for later: option A as second player and an exploitative search as starter.

### Every changed parameter

| Parameter | Current | Proposed | Changes |
|-----------|---------|----------|:-------:|
| Expert layout method (`ExpertMangolaBot.proposeLayout`) | 24 candidates (20 catalogue rotations from a random start, 4 random) then 24 mutants of the best, each scored by a 300-node search from the first move against 4 typical opponents (catalogue 0, catalogue 26, 2 random) | One draw from the list of its side with the probabilities above, no search | **yes** |
| Expert uses its side | only through the search | a different list for side A and side B | **yes** |
| Expert starter list / second-player list | none | the two tables above (6 and 9 layouts, constants in the domain) | **yes** (new) |
| `LAYOUT_NODE_CAP`, `LAYOUT_CANDIDATES`, `LAYOUT_MUTANTS` | 300, 24, 24 | removed | **yes** |
| `mangola.bots.layout-ms` / `Budget.layoutMillis` | 1000 ms (0 under `test`) | no longer read by the Expert; kept for one release then removed (Q4) | **yes** |
| Layout refused at confirmation (stuck start) | `AbstractMangolaBot` asks again (4 proposals), then the catalogue, then random | a draw from the side's list without the refused layouts; when the list is exhausted, the current fallback | **yes** (small) |
| Medium layout (`GreedyMangolaBot.proposeLayout`) | random or catalogue, half and half | a uniform draw from the Expert's list of its side | **yes** (Q2) |
| Easy layout | a random layout | unchanged | no |
| Evaluation weights (seed, mobility, opportunity, exposure) | 10, 3, 5, 3 | unchanged (no effect found on placement) | no |
| Move budget `mangola.bots.think-ms`, `EXPERT_NODE_CAP` | 300 ms, 5,000,000 | unchanged | no |
| Pattern catalogue (`MangolaLayouts`) | 14 patterns, 53 layouts | unchanged (fallback only) | no |

### Expected effect (measured, expert moves on both sides)

| Matchup | Current | Proposed |
|---------|--------:|---------:|
| Expert placement against the current Expert's | 0.500 | 0.633 ± 0.047 (0.61 ± 0.10 at 40,000 nodes) |
| Expert placement against Medium's | 0.880 ± 0.032 (current Medium) | 0.770 ± 0.041 (current Medium) |
| Expert placement against Easy's | 0.900 ± 0.029 | 0.818 ± 0.038 |
| Medium placement against Easy's | 0.483 ± 0.049 | not measured; uniform list against current Medium 0.768 ± 0.041 |
| Layout decision time of the Expert | up to 1,000 ms | under 1 ms |

These are placement effects with the expert playing both sides; against a human or the greedy move search the
numbers will differ (the existing test `theExpertBeatsTheGreedyBotInAtLeast70PercentOfOneHundredSeededGames` is
the guard).

## Open questions for the owner

- **Q1 (main): safe or exploitative Expert?** Option A wins the head-to-head against every placement policy
  measured but exploits weak placements less (about 0.10 less against random-like layouts). Which matters more for
  the Expert: strong players who learn its layouts, or average players? Recommendation: A, since humans who choose
  Expert are the ones likely to learn and punish a predictable layout, and a mix of 6 to 9 layouts is varied.
- **Q2: Medium's placement.** Uniform over the Expert's lists makes Medium place almost as well as the Expert
  (0.768 against current Medium); the levels then differ only by the move search. Alternatives: half from the list
  and half random, or the `{8,0,0,0}` family only. Recommendation: uniform over the lists, measured in P2.
- **Q3: show the finding to players?** The rules panel or the placement editor could offer the `{8,0,0,0}`
  pattern as a preset (today: checkerboard, random, two per pit). That changes the human side of the game; not in
  this proposal.
- **Q4:** remove `mangola.bots.layout-ms` at once or keep it read-but-unused for one release.
- **Q5:** re-run the study (the oracle part) whenever the rules or the engine change; the list is a frozen
  artefact of the current rules (`MangolaRules.OWNER`).

## Limits

- The opponent model is the expert itself at 4,000 nodes; humans play differently, and the greedy move search
  too. The duel at 40,000 nodes agrees but is small (100 games).
- The list is optimal **within a pool of 89 layouts**; the oracle still improved it in round 2, so a layout
  outside the pool may beat it. P0 continues the oracle until a round adds nothing.
- 2 games per ordered pair: the games being almost deterministic, more repetitions would mostly repeat the same
  outcome; the uncertainty is in the pool and the opponent model, not in the sampling.
