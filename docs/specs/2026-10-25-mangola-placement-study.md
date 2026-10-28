# Mangola starting placement: study and proposal

**Status:** study done; owner decisions given (Q1 exploitative Expert drawing from the side-aware lists, Q2 Medium
draws from the lists, Q3 no `{8,0,0,0}` preset, Q4 remove `mangola.bots.layout-ms` at once, Q5 re-run the oracle
whenever the rules or the engine change). **No bot behaviour changed** in this work: the harness and the analysis
live in test scope only. Plan: `docs/plans/2026-10-25-mangola-placement-plan.md`.
Produced by Opus 5.5 on 2026-10-25 from `origin/main` at `5e567cc0`.

**Update 2026-10-28 (chunk S0): the oracle was continued from round 3 to round 15 and the lists below are the
round-15 ones. It did not settle in the strict sense (every round still found 5 or 6 layouts that entered a mix,
at the 2 % threshold), but the starter's value stopped moving (0.484, 0.492, 0.501, 0.493 over rounds 12 to 15), so
it was stopped there: "value settled, strict rule not reached". The picture changed a lot compared with the
two-round version (see "What changed after round 2"): the starter's equilibrium value is about 0.49, not 0.667,
and the old lists are replaced.**

## What changed after round 2 (S0)

| | After round 2 (89 layouts) | After round 15 (161 layouts) |
|---|---|---|
| Starter's equilibrium value (side-aware) | 0.667 | **0.493** (gap 0.0002; 0.484 to 0.501 over rounds 12 to 15) |
| Starter list | 6 layouts (C17, O2A1, O1A1, C30, C31, O2B1) | **18 layouts**, mostly dense and irregular (largest: O6A2 0.137) |
| Second-player list | 9 layouts (C18, C17, C16, C20, ...) | **15 layouts**, mostly sparse `{8,0,0,0}`-like with two or three 8s and some 5 to 7s (largest: O10B0 0.123) |
| Seat-swapped (side unknown) | 7-layout mix | 12-layout mix, led by O10B0 0.313; pure C18 no longer an equilibrium |
| Dominated starter / second / swapped | 32 / 38 / 29 | 10 / 51 / 11 |
| Starter's share of the games | 77 to 78 % | 73.6 % (22.0 plies on average) |
| Uniform field as starter against the second player's mix | not computed | 0.291 (the mix scores 0.709 against a uniform starter) |

Reading: the approximate oracle keeps finding second-player layouts that hold the starter's edge down, so the
first-mover advantage at equilibrium is much smaller than round 2 suggested (0.49 against 0.667); the round-2
lists were an artefact of a pool the second player had not yet explored. The proposal's lists, and its expected
effect numbers, are updated below; the headline (a fixed side-aware list beats the current Expert's placement)
holds but its margin is smaller at the production budget.

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
3. **Most layouts are dominated.** In the pool of 89 after round 2, 32 starter layouts and 38 second-player layouts are
   weakly dominated by another single layout (63 and 59 of the base 78).
4. **The bot knows its side, and the best layouts differ by side.** Starting is about attacking (dense or
   `{8,0,0,0}` layouts); playing second is about surviving the first move (sparse `{8,0,0,0}`-like layouts, which
   give the starter almost no capture). The side-aware game (rows: the starter's layout, columns: the second
   player's) had a value of 0.667 for the starter after round 2 (6 and 9 layouts); **after round 15 its value is
   0.493 with mixes of 18 and 15 layouts** (pool of 161).
5. **The current Expert places well as second player, poorly as starter.** Its side-B choices are already
   `{8,0,0,0}` variants (0.61 to 0.67 as second player, against 0.14 for random layouts); its side-A choices are
   dense layouts no better than random ones (0.83 to 0.91 as starter, random 0.86, best 0.95). Its layout score
   correlates only moderately with the tournament (Spearman 0.63 for side A, 0.48 for side B against a uniform
   field; 0.30 and 0.55 against the equilibrium), because the mean over its 4 typical opponents is dominated by
   the "forced win" values (about 1,000,000) its 300-node search finds against the weak ones.
6. **Drawing from the side-aware list beats the current Expert placement, by a smaller margin with the round-15
   lists:** **0.605 ± 0.048** over 400 games at 4,000 nodes a move (0.633 ± 0.047 with the round-2 lists), and
   **0.520 ± 0.098** over 100 games at 40,000 nodes (the production budget on this machine; 0.61 ± 0.096 with the
   round-2 lists): at the production budget the advantage over the current Expert's placement is not
   distinguishable from zero. Against weak placements the round-15 lists are **no longer less exploitative** than
   the current Expert: 0.870 against Medium's layouts (current Expert 0.880) and 0.860 against Easy's (current
   Expert 0.900). The trade-off of Q1 mostly disappears.
7. **Medium places no better than Easy today** (0.483 ± 0.049 with the same move search on both sides): half of its
   layouts are random and the catalogue it draws the other half from is, on average, worse than random as starter
   (0.72 against 0.86). Drawing uniformly from the side-aware list instead scores 0.863 against current Medium (round-15 lists; 0.768 with
   the round-2 lists).
8. **The evaluation weights do not matter for placement.** With weights (10,0,0,0), (10,6,10,6) or (10,1,2,1)
   instead of (10,3,5,3), 97 to 99 % of the ordered pairs keep the same result and the rankings are unchanged
   (Spearman 0.95 to 0.99); the layout score ranking is unchanged too (0.999). **The node budget matters little:**
   at 40,000 nodes a move instead of 4,000, 96.3 % of the pairs keep their result (Spearman 0.86 as starter, 0.97 as
   second).
9. **The list is the best found, not a proven equilibrium.** Each round of the approximate double oracle found
   new layouts that entered the mixes. The starter's value went 0.600, 0.615, 0.667 (rounds 0 to 2), then 0.593,
   0.593, 0.637, 0.590, 0.565, 0.585 (rounds 3 to 8), 0.578, 0.542, 0.518, 0.484, 0.492, 0.501, 0.493 (rounds 9 to
   15) and was stopped at round 15, when it had stayed within 0.01 of 0.49 for four rounds (S0). The list is the
   round-15 one; it was fitted on the same games it is judged on, so the validation duels below (fresh seed, but
   the same pool of layouts) are optimistic about layouts outside the pool.

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

Stages: `tournament`, `layoutscore`, `budget`, `weights`, `oracle`, `duel`, `hybrid`, `book`, `regression` (comma list,
or `all`). `book` prints the final pruned mixes as Java `Entry` lines for the placement book, from the cache the
`oracle` stage wrote. `regression` plays the production Expert's placement (the book) against the October 2026
layout search kept in `StudyExpert`, 400 games, same seed and move search as the validation duels (`all` includes it; alone: `-Dmangola.study=regression`).

Continuing the oracle (S0): `-Dmangola.study.rounds=N` is the number of oracle rounds in all (default 2). The
extended pool is cached after every round in `target/mangola-study/layouts-final.csv` and `pairs-final.csv` and
resumed by the next run, so `-Dmangola.study=oracle,book -Dmangola.study.rounds=15` plays only the rounds missing.
A run stops early when a round finds no new layout or none of its layouts enters a pruned mix. The cache is only
valid for the study's constants (seed, node cap, repetitions) and the current rules and engine: delete the
directory after changing any of them (Q5: re-run the oracle whenever the rules or the engine change).

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
dropped). The oracle added 11 more in rounds 1 and 2 (89), and 72 more in rounds 3 to 15 (5 or 6 per round):
161 in the end.

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

Fifteen rounds (two in the first version of this study, thirteen more in S0). In each, for each side, three hill climbs (first improvement, 40 steps, moving one or two seeds,
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

S0 (rounds 3 to 15 and the validation duels on the round-15 pool): the round-robin extensions took **5,672 s CPU**
(measured: 2,301 s for rounds 3 to 8, 3,371 s for rounds 9 to 15), the hill climbs about 2,540 s of wall time on
4 threads (**about 8,000 s CPU**, estimated at 3.2 busy threads: they were not timed in CPU), the 13 validation duels
and the analysis of the final pool **2,656 s CPU** (measured, one JVM): in all about **4.4 CPU-hours** (about 3 hours
of wall time on 4 threads, plus some CPU lost in an interrupted run of the duels on a non-final pool).

## Results

### Per layout (pool of 89 after round 2, uniform field, 178 games per side, standard error at most 0.037)

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

### Per layout, final pool of 161 (round 15, uniform field, 322 games per side, standard error at most 0.028)

| Layout | Inner a..h / outer a..h | As starter | As second | Mean |
|--------|-------------------------|-----------:|----------:|-----:|
| O10B0 | `10601082` / `08000600` | 0.879 | 0.823 | 0.851 |
| O6A0 | `01080108` / `60008000` | 0.910 | 0.776 | 0.843 |
| O11B1 | `25000801` / `00820060` | 0.901 | 0.780 | 0.840 |
| O3B0 | `10820050` / `06000802` | 0.885 | 0.789 | 0.837 |
| O12B0 | `00820040` / `05002812` | 0.870 | 0.755 | 0.812 |
| O14B1 | `00501080` / `28001502` | 0.801 | 0.817 | 0.809 |
| C18 | `00080008` / `80008000` | 0.857 | 0.733 | 0.795 |
| C19 | `00800080` / `08000800` | 0.832 | 0.733 | 0.783 |
| C20 | `08000800` / `00800080` | 0.842 | 0.724 | 0.783 |
| C17 | `80008000` / `00080008` | 0.820 | 0.658 | 0.739 |
| C39 (worst) | `00444400` / `00444400` | 0.075 | 0.006 | 0.040 |

The oracle's layouts now fill the top of the ranking: they are the `{8,0,0,0}` family bent by a seed or two (a 6, a
5, a 1, a 2) so that the starter's first captures fail. The pure `{8,0,0,0}` patterns (C17 to C20) fall to 0.66 to
0.86 on average.

### Equilibria

| | Base pool (78) | After oracle round 1 (83) | After round 2 (89) | After round 15 (161) |
|---|---|---|---|---|
| Seat-swapped value / mix | 0.5, pure C18 | 0.5, pure C18 | 0.5, O2B1 0.30, C18 0.20, C19 0.20, C17 0.11, O1A1 0.10, C31 0.05, EB1 0.05 | 0.5, O10B0 0.31, O3B0 0.14, O12B0 0.12, O9A1 0.10, O13A1 0.06, O15A1 0.05, O14B0 0.05, O12A0 0.04, O4B1 0.04, O14A1 0.03, C18 0.03, O6B0 0.03 |
| Side-aware: starter's value | 0.600 | 0.615 | 0.667 | **0.493** (0.484, 0.492, 0.501, 0.493 over rounds 12 to 15) |
| Starter's mix | C31 0.4, C18 0.4, R14 0.2 | C18 0.31, C19 0.23, C30 0.15, R14 0.15, O1A1 0.08, C01 0.08 | see the proposal (round 2) | see the proposal |
| Second player's mix | C18 0.4, C19 0.4, EB5 0.2 | C19 0.27, EB5 0.25, O1B0 0.12, C18 0.12, C16 0.08, ... | see the proposal (round 2) | see the proposal |
| Dominated (starter / second / swapped) | 63 / 59 / 53 | 56 / 60 / 53 | 32 / 38 / 29 | 10 / 51 / 11 |

No pure layout guarantees anything in the side-aware game: every starter layout loses to some second-player layout
and the reverse (maximin 0, minimax 1), which is why a mix is needed. The mixes are small because the matrix is
almost all 0s and 1s (the games are nearly deterministic).

### Calibration

| Question | Measure | Answer |
|----------|---------|--------|
| Do the evaluation weights matter for placement? | Subset of 20 layouts (every 4th of the pool), 800 games each, weights (10,0,0,0), (10,6,10,6), (10,1,2,1) against (10,3,5,3) | **No.** 97.0, 97.3, 99.0 % of ordered pairs keep their result; Spearman as starter 0.99, 0.98, 0.99, as second 0.96, 0.95, 0.95. The layout score ranking is the same (Spearman 0.999 and 1.000). |
| Does the node budget matter? | Same subset at 40,000 nodes, 400 games | **Little.** 96.3 % of pairs keep their result; Spearman 0.86 as starter, 0.97 as second; the starter wins 78.5 % (78 % at 4,000). |
| Does the 300-node layout search predict the tournament? | Its score (mean of 8 draws of its 4 typical opponents) for every layout of the base pool | **Moderately.** Spearman 0.63 (side A, uniform field), 0.30 (side A, against the second player's mix), 0.48 (side B, uniform), 0.55 (side B, against the starter's mix). Its side-A top 5 (C21, EA5, C26, EA2, EA1) are dense layouts worth about a random one; its side-B top 5 (C20, C18, EB5, C19, EB6) are right. Cause: the raw mean is dominated by forced-win values (about 10^6) found against the weakest typical opponents. |
| Does the first mover have an advantage? | Every game of the tournament; random boards | **Yes, large:** 77 to 78 % for the starter in the base round robin (73.6 % in the final pool of 161, where the second player's layouts are tuned against the starter), 97 % on random boards, but only 0.493 for the starter at the side-aware equilibrium of the round-15 pool (0.667 after round 2): with tuned layouts the second player is not at a disadvantage. `firstPlayer: random` (the default) is what keeps a series fair. |

### Validation duels (seed 900001, the expert's moves at 4,000 nodes unless stated)

| First policy vs second | Games | Round 15 (S0): score of the first (95 %) | Wins starting / second | Round 2 (first version) |
|------------------------|------:|-----------------------------------------:|-----------------------:|------------------------:|
| Side-aware list (proposed) vs current Expert | 400 | **0.605 ± 0.048** | 101/200, 141/200 | 0.633 ± 0.047 |
| Side-aware list vs current Expert, 40,000 nodes | 100 | **0.520 ± 0.098** | 17/50, 35/50 | 0.610 ± 0.096 |
| Side-blind list vs current Expert | 400 | 0.623 ± 0.048 | 109/200, 140/200 | 0.610 ± 0.048 |
| Hybrid (current search + both lists) vs current Expert | 400 | 0.580 ± 0.048 | 70/200, 162/200 | 0.568 ± 0.049 |
| Hybrid vs side-aware list | 400 | 0.463 ± 0.049 | 88/200, 97/200 | 0.438 ± 0.049 |
| Side-aware list vs current Medium | 400 | 0.870 ± 0.033 | 191/200, 157/200 | 0.770 ± 0.041 |
| Current Expert vs current Medium | 400 | 0.880 ± 0.032 | 190/200, 162/200 | same |
| Hybrid vs current Medium | 400 | 0.895 ± 0.030 | 187/200, 171/200 | 0.898 ± 0.030 |
| Side-aware list drawn uniformly vs current Medium | 400 | 0.863 ± 0.034 | 188/200, 157/200 | 0.768 ± 0.041 |
| Side-aware list vs Easy | 400 | 0.860 ± 0.034 | 195/200, 149/200 | 0.818 ± 0.038 |
| Current Expert vs Easy | 400 | 0.900 ± 0.029 | 200/200, 160/200 | same |
| Hybrid vs Easy | 400 | 0.945 ± 0.022 | 200/200, 178/200 | 0.908 ± 0.028 |
| Current Medium vs Easy | 400 | 0.483 ± 0.049 | 178/200, 15/200 | same |

The round-15 lists draw from 161 layouts fitted on the same games (the duels use a fresh seed, so the games differ,
but not the pool): read the margins as an upper bound for layouts outside the pool.

The hybrid is the current search with the side's list scored first and the other side's list added to its four
typical opponents. It places as well as the list when second (157/200) but badly when it starts (70/200): its
side-A scoring still prefers the dense layouts that beat the weak typical opponents.

## Proposal

### Expert: draw from a side-aware list (option A, recommended)

The Expert stops searching for its layout and draws it from a fixed list for its side with the probabilities below
(the pruned mixes after fifteen oracle rounds, S0; they replace the two-round lists of the first version, which
were: starter C17 0.333, O2A1 0.250, O1A1 0.167, C30, C31, O2B1 0.083 each; second player C18 0.235, C17 0.167,
C16, C20, O2B1, C05, C19, EB5, O2B0 about 0.086 each). Rationale: it is the only measured policy that beats the
current Expert's placement and the hybrid head to head (0.605 and 0.537), costs no layout time, and is not
exploitable inside the pool. With the round-15 lists its cost against weak placements has almost gone (0.870
against Medium's, 0.860 against Easy's, where the current Expert scores 0.880 and 0.900); its margin over the
current Expert's placement shrank to 0.605 ± 0.048 at 4,000 nodes and 0.520 ± 0.098 at 40,000 nodes: see Q1.

**Starter list (side A, 18 layouts):**

| Layout | Probability | Inner a..h / outer a..h | Counts in loop order |
|--------|------------:|-------------------------|----------------------|
| O6A2 | 0.137 | `22133122` / `11212234` | `[2,2,1,3,3,1,2,2,4,3,2,2,1,2,1,1]` |
| EB1 | 0.094 | `00080008` / `70008100` | `[0,0,0,8,0,0,0,8,0,0,1,8,0,0,0,7]` |
| O9A1 | 0.086 | `01070208` / `50108000` | `[0,1,0,7,0,2,0,8,0,0,0,8,0,1,0,5]` |
| O10B0 | 0.067 | `10601082` / `08000600` | `[1,0,6,0,1,0,8,2,0,0,6,0,0,0,8,0]` |
| O14A2 | 0.062 | `02022321` / `20601650` | `[0,2,0,2,2,3,2,1,0,5,6,1,0,6,0,2]` |
| O1A1 | 0.056 | `04221223` / `32303230` | `[0,4,2,2,1,2,2,3,0,3,2,3,0,3,2,3]` |
| C04 | 0.054 | `22222222` / `22222222` | `[2,2,2,2,2,2,2,2,2,2,2,2,2,2,2,2]` |
| O14A0 | 0.054 | `02200052` / `12127620` | `[0,2,2,0,0,0,5,2,0,2,6,7,2,1,2,1]` |
| O13A1 | 0.051 | `07000600` / `00801082` | `[0,7,0,0,0,6,0,0,2,8,0,1,0,8,0,0]` |
| O15A2 | 0.050 | `42020521` / `11133160` | `[4,2,0,2,0,5,2,1,0,6,1,3,3,1,1,1]` |
| O5A2 | 0.045 | `03111435` / `11142320` | `[0,3,1,1,1,4,3,5,0,2,3,2,4,1,1,1]` |
| O14A1 | 0.040 | `10501080` / `08000801` | `[1,0,5,0,1,0,8,0,1,0,8,0,0,0,8,0]` |
| O15A1 | 0.038 | `10501180` / `08100700` | `[1,0,5,0,1,1,8,0,0,0,7,0,0,1,8,0]` |
| O11B0 | 0.038 | `00401082` / `08001602` | `[0,0,4,0,1,0,8,2,2,0,6,1,0,0,8,0]` |
| C21 | 0.035 | `32213221` / `12231223` | `[3,2,2,1,3,2,2,1,3,2,2,1,3,2,2,1]` |
| O8B2 | 0.034 | `12021332` / `32104323` | `[1,2,0,2,1,3,3,2,3,2,3,4,0,1,2,3]` |
| O15B1 | 0.031 | `10811030` / `04012803` | `[1,0,8,1,1,0,3,0,3,0,8,2,1,0,4,0]` |
| O11B1 | 0.028 | `25000801` / `00820060` | `[2,5,0,0,0,8,0,1,0,6,0,0,2,8,0,0]` |

**Second-player list (side B, 15 layouts):**

| Layout | Probability | Inner a..h / outer a..h | Counts in loop order |
|--------|------------:|-------------------------|----------------------|
| O10B0 | 0.123 | `10601082` / `08000600` | `[1,0,6,0,1,0,8,2,0,0,6,0,0,0,8,0]` |
| O14B1 | 0.118 | `00501080` / `28001502` | `[0,0,5,0,1,0,8,0,2,0,5,1,0,0,8,2]` |
| O15B0 | 0.108 | `00501090` / `18002402` | `[0,0,5,0,1,0,9,0,2,0,4,2,0,0,8,1]` |
| O12B0 | 0.099 | `00820040` / `05002812` | `[0,0,8,2,0,0,4,0,2,1,8,2,0,0,5,0]` |
| C20 | 0.093 | `08000800` / `00800080` | `[0,8,0,0,0,8,0,0,0,8,0,0,0,8,0,0]` |
| O9A1 | 0.077 | `01070208` / `50108000` | `[0,1,0,7,0,2,0,8,0,0,0,8,0,1,0,5]` |
| O3B0 | 0.072 | `10820050` / `06000802` | `[1,0,8,2,0,0,5,0,2,0,8,0,0,0,6,0]` |
| O4B1 | 0.064 | `00701040` / `27000803` | `[0,0,7,0,1,0,4,0,3,0,8,0,0,0,7,2]` |
| O15B1 | 0.058 | `10811030` / `04012803` | `[1,0,8,1,1,0,3,0,3,0,8,2,1,0,4,0]` |
| O6B0 | 0.042 | `00701050` / `17010703` | `[0,0,7,0,1,0,5,0,3,0,7,0,1,0,7,1]` |
| O11B1 | 0.038 | `25000801` / `00820060` | `[2,5,0,0,0,8,0,1,0,6,0,0,2,8,0,0]` |
| C19 | 0.031 | `00800080` / `08000800` | `[0,0,8,0,0,0,8,0,0,0,8,0,0,0,8,0]` |
| O6A0 | 0.026 | `01080108` / `60008000` | `[0,1,0,8,0,1,0,8,0,0,0,8,0,0,0,6]` |
| O11B0 | 0.026 | `00401082` / `08001602` | `[0,0,4,0,1,0,8,2,2,0,6,1,0,0,8,0]` |
| O9B0 | 0.023 | `00600080` / `08000703` | `[0,0,6,0,0,0,8,0,3,0,7,0,0,0,8,0]` |

The probabilities are the pruned (under 2 % dropped) and renormalised fictitious-play mixes of the side-aware game
of the 161-layout pool (starter's value 0.493, gap 0.0002). The rounding to three decimals leaves up to 0.001 of
difference in each sum: P0 normalises the weights when it freezes them. Five layouts appear in both lists (O10B0,
O9A1, O15B1, O11B1, O11B0): the lists are per side, so that is harmless. `-Dmangola.study=book` prints the lists as
Java `Entry` lines for P0.

Alternatives measured: keep the current search (option C: best against weak placements, loses 0.367 to option A);
the hybrid (option B: 0.568 against the current Expert, 0.438 against option A, best against weak placements). An
unmeasured option D, for later: option A as second player and an exploitative search as starter.

### Every changed parameter

| Parameter | Current | Proposed | Changes |
|-----------|---------|----------|:-------:|
| Expert layout method (`ExpertMangolaBot.proposeLayout`) | 24 candidates (20 catalogue rotations from a random start, 4 random) then 24 mutants of the best, each scored by a 300-node search from the first move against 4 typical opponents (catalogue 0, catalogue 26, 2 random) | One draw from the list of its side with the probabilities above, no search | **yes** |
| Expert uses its side | only through the search | a different list for side A and side B | **yes** |
| Expert starter list / second-player list | none | the two tables above (18 and 15 layouts, constants in the domain) | **yes** (new) |
| `LAYOUT_NODE_CAP`, `LAYOUT_CANDIDATES`, `LAYOUT_MUTANTS` | 300, 24, 24 | removed | **yes** |
| `mangola.bots.layout-ms` / `Budget.layoutMillis` | 1000 ms (0 under `test`) | removed with the property, `DEFAULT_LAYOUT_MS` and the budget field (Q4, P3) | **yes** |
| Layout refused at confirmation (stuck start) | `AbstractMangolaBot` asks again (4 proposals), then the catalogue, then random | a draw from the side's list without the refused layouts; when the list is exhausted, the current fallback | **yes** (small) |
| Medium layout (`GreedyMangolaBot.proposeLayout`) | random or catalogue, half and half | a uniform draw from the Expert's list of its side | **yes** (Q2) |
| Easy layout | a random layout | unchanged | no |
| Evaluation weights (seed, mobility, opportunity, exposure) | 10, 3, 5, 3 | unchanged (no effect found on placement) | no |
| Move budget `mangola.bots.think-ms`, `EXPERT_NODE_CAP` | 300 ms, 5,000,000 | unchanged | no |
| Pattern catalogue (`MangolaLayouts`) | 14 patterns, 53 layouts | unchanged (fallback only) | no |

### Expected effect (measured, expert moves on both sides)

| Matchup | Current | Proposed |
|---------|--------:|---------:|
| Expert placement against the current Expert's | 0.500 | 0.605 ± 0.048 at 4,000 nodes; 0.520 ± 0.098 at 40,000 nodes (first version, round-2 lists: 0.633 ± 0.047 and 0.61 ± 0.10) |
| Expert placement against Medium's | 0.880 ± 0.032 (current Medium) | 0.870 ± 0.033 (current Medium); first version 0.770 ± 0.041 |
| Expert placement against Easy's | 0.900 ± 0.029 | 0.860 ± 0.034; first version 0.818 ± 0.038 |
| Medium placement against Easy's | 0.483 ± 0.049 | not measured; uniform list against current Medium 0.863 ± 0.034 (first version 0.768 ± 0.041) |
| Layout decision time of the Expert | up to 1,000 ms | under 1 ms |

These are placement effects with the expert playing both sides; against a human or the greedy move search the
numbers will differ (the existing test `theExpertBeatsTheGreedyBotInAtLeast70PercentOfOneHundredSeededGames` is
the guard).

**What S0 changes for these numbers.** The first version promised +0.13 against the current Expert's placement at a
cost of 0.08 to 0.11 against weak ones. With the round-15 lists the gain is +0.10 at 4,000 nodes but only +0.02 (not
distinguishable from zero) at the production budget of 40,000 nodes, while the cost against weak placements is
gone (-0.01 against Medium's, -0.04 against Easy's, both small). The main benefit is therefore the saved layout
time (up to 1,000 ms to under 1 ms), a varied and side-aware placement, and no loss against weak players; the
strength gain over the current Expert is probably small, and `theExpertBeatsTheGreedyBot...` stays the guard.

## Open questions for the owner

Owner decisions (2026-10-28):

- **Q1: safe or exploitative Expert?** Decided: the Expert draws from the fixed side-aware lists. With the
  round-15 lists the cost against weak placements is mostly gone (see "What S0 changes").
- **Q2: Medium's placement.** Decided: Medium draws from the lists (uniformly: 0.863 against current Medium with
  the round-15 lists).
- **Q3: show the finding to players?** Decided: no `{8,0,0,0}` preset. (The round-15 lists also show that the pure
  pattern is no longer the answer: the best layouts are bent variants of it.)
- **Q4:** decided: remove `mangola.bots.layout-ms` at once.
- **Q5:** decided: re-run the study (the oracle part, `-Dmangola.study=oracle,book`, after deleting the cache)
  whenever the rules or the engine change; the lists are a frozen artefact of the current rules
  (`MangolaRules.OWNER`).

### Regression after the merge (P3, 2026-10-28)

The `regression` stage plays the production Expert's placement (the frozen book, through `ExpertMangolaBot`) against the
October 2026 layout search (`StudyExpert`), 400 games, seed 900001, both seats searching 4,000 nodes: score
**0.630 ± 0.047** (252 wins, 0 draws, 148 losses; 116/200 won when starting, 136/200 when second), 80 s wall and 217 s
CPU on 4 threads. The study measured **0.605 ± 0.048** for the round-15 lists (same seed and budget): the two figures
are within their intervals (the draws use different random streams), so the frozen code reproduces the study. No regression.
## Limits

- The opponent model is the expert itself at 4,000 nodes; humans play differently, and the greedy move search
  too. The duel at 40,000 nodes agrees but is small (100 games).
- The list is optimal **within a pool of 161 layouts**. The oracle was stopped at round 15, when the starter's
  value had stayed near 0.49 for four rounds, but every round still found 5 or 6 layouts that entered a mix (the
  strict "a round adds nothing" rule was not reached), so a layout outside the pool may still beat the lists and
  the mixes may keep drifting slowly. The duels at 40,000 nodes no longer separate the lists from the current
  Expert's placement (0.520 ± 0.098).
- 2 games per ordered pair: the games being almost deterministic, more repetitions would mostly repeat the same
  outcome; the uncertainty is in the pool and the opponent model, not in the sampling.
