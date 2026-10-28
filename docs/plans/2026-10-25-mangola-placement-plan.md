# Mangola bot placement from the study: plan

Spec: `docs/specs/2026-10-25-mangola-placement-study.md`. **Produced by Opus 5.5** on 2026-10-25 from
`origin/main` at `5e567cc0`. **Nothing here starts before the owner agrees to the spec's "current / proposed"
table** (and answers Q1, Q2, Q4).

## Key decisions (from the spec)

1. **The Expert draws its layout from a fixed side-aware list** (18 layouts as starter, 15 as second player after S0's
   fifteen oracle rounds, with probabilities): no layout search any more. Measured 0.605 ± 0.048 against the current
   Expert placement (0.520 ± 0.098 at the production-like budget), with no real cost against weak placements (0.870
   against Medium's, 0.860 against Easy's; spec Q1, "What changed after round 2"). S0 is done: the lists to freeze
   are in the spec's proposal, and the study's `book` stage prints them.
2. **The lists are data in the domain** (`mangola/domain/bot`), frozen from the study; the study harness (test
   scope) is the tool that regenerates them. No property, no host option.
3. **Medium draws uniformly from the same lists** (spec Q2); Easy is unchanged; the evaluation weights and the
   move budget are unchanged (no effect found).
4. **A refused layout (stuck start) is drawn again from the list without the refused ones**, then the existing
   `AbstractMangolaBot` fallback (catalogue, random) applies, so the decision always ends.
5. **Merge rule: every implementation PR of this plan (P0 to P3) needs the owner's explicit merge accord**: it
   changes the Mangola bots, the Mangola backend business domain. The orchestrator merges none of them on its own.
   S0 is test scope and docs only.

**Tests (every PR):** `cd backend && mvn -s ../deploy/docker/settings.xml test`; `cd frontend && npm test && npx
vite build` (unchanged frontend, run for the rule). No Cypress (no visible change: the bot's layout is only seen
after the reveal, and no spec pins it). No dev environment (backend bot behaviour, nothing new to try by hand;
the owner may ask for one in P3 to watch the Expert's layouts).

**Conventions:** branches `feat/mangola-placement-...` (`test/` for S0); a draft PR with its real number at the
start of each chunk; test-first visible in git (`test:` commit before `feat:`); backend paths relative to
`backend/src/main/java/org/kevinkib/cardgames/`, tests to `backend/src/test/java/org/kevinkib/cardgames/`.

## Status

| Chunk | Title | Cost | Needs | Runs | State |
|-------|-------|------|-------|------|-------|
| S0 | `test: continue the Mangola placement oracle until it settles` | M (about 1 CPU-hour on 4 threads) | the owner's go on the table | first, alone (optional: skip it to ship the current lists) | merged |
| P0 | `feat: Mangola placement book (contract)` | S (< 30 min) | S0 merged or skipped | after S0, alone | merged (#392) |
| P1 | `feat: the Mangola expert places from the placement book` | M | P0 merged | parallel with P2 | merged (#394) |
| P2 | `feat: the Mangola medium bot places from the placement book` | S | P0 merged | parallel with P1 | merged (#395) |
| P3 | `feat: Mangola placement book, integration` | S | P1, P2 merged | last (integration) | in review (this PR) |

## Files at risk of conflict

| File | Chunks | Handling |
|------|--------|----------|
| `mangola/domain/bot/MangolaPlacementBook.java` (new), `MangolaPlacementBookTest.java` (new) | P0 only | P1 and P2 read it, never edit it |
| `mangola/domain/bot/AbstractMangolaBot.java` | P0 only (a protected `fromBook(side, rejected)` helper; behaviour unchanged until used) | |
| `mangola/domain/bot/ExpertMangolaBot.java`, `ExpertMangolaBotTest.java` | P1 only | |
| `mangola/domain/bot/GreedyMangolaBot.java`, `GreedyMangolaBotTest.java` | P2 only | |
| test `mangola/domain/bot/StudyExpert.java`, `StudyExpertTest.java` | S0 (none expected), P1 (the layout-equivalence test compares with production: P1 keeps the old search in `StudyExpert` as "the October 2026 layout search" and drops that one assertion, the search assertion stays) | series S0 then P1 |
| test `mangola/domain/bot/MangolaPlacementStudy.java` | S0 (more rounds, a `book` stage that prints the lists as Java), P3 (a duel of the book against the old search, as a regression stage) | series S0 then P3 |
| `config/MangolaConfig.java`, `src/main/resources/application-test.properties`, `mangola/presentation/bot/MangolaBotWiringIT.java` | P3 only (`mangola.bots.layout-ms`, per Q4) | |
| `docs/specs/2026-10-15-mangola-design.md` (Bots, "Layouts"), `docs/specs/2026-10-25-mangola-placement-study.md` (tables) | S0 (study tables), P3 (base spec) | series |

## S0 `test: continue the Mangola placement oracle until it settles` (optional)

Test scope and docs only (may be merged like a docs PR).

- `MangolaPlacementStudy`: `ORACLE_ROUNDS` from a property (`-Dmangola.study.rounds`, default 2), stop early when a
  round adds no layout to either mix; cache the extended round robin (`pairs-final.csv`) like the base one.
- Run up to 4 more rounds, then the `duel` and `hybrid` stages; update the spec's equilibrium, proposal and
  validation tables with the new lists and numbers; state the CPU time.
- Test: `MangolaPlacementStudyTest` for the early stop (a stub oracle that finds nothing).

## P0 `feat: Mangola placement book (contract)` (S, under 30 minutes)

Shared interface, no behaviour change, nothing deleted.

- `mangola/domain/bot/MangolaPlacementBook.java` (package-private, final): `record Entry(String name, double
  weight, int[] counts)`; `static List<Entry> starter()`, `static List<Entry> second()` (the frozen lists of the
  spec, or S0's); `static List<Entry> of(Side side)`; `static Optional<int[]> draw(Side side, RandomGenerator
  random, Set<List<Integer>> rejected)` (weighted, skipping rejected layouts; empty when all are rejected);
  `static Optional<int[]> drawUniform(...)` (same, equal weights). Javadoc: where the lists come from (the study,
  its seed and date) and how to regenerate them.
- `AbstractMangolaBot`: a protected `int[] fromBook(MangolaRules rules, Side side, Set<List<Integer>> rejected,
  boolean weighted)` that returns a book draw passing `acceptable`, or `randomOrPattern()`; not called yet.
- Tests first (`MangolaPlacementBookTest`): every entry is a legal layout (16 counts, sum 32, not endless only), the
  weights of each side sum to 1 (within 1e-9), no duplicate in a list, a rejected layout is never drawn (even with
  `BotFixtures.CONSTANT`), all rejected gives empty, the weighted draw's frequencies over 10,000 seeded draws are
  within 0.02 of the weights, the uniform draw within 0.02 of 1/n.

## P1 `feat: the Mangola expert places from the placement book` (M)

- `ExpertMangolaBot.proposeLayout` → `fromBook(rules, side, rejected, true)`; delete `typicalOpponents`,
  `layoutScore`, `mutate`, `LAYOUT_NODE_CAP`, `LAYOUT_CANDIDATES`, `LAYOUT_MUTANTS`; the class comment's layout
  paragraph rewritten (the list, why, the study). `Budget.layoutMillis` stays until P3 (removed there).
- Tests first (`ExpertMangolaBotTest`): side A layouts are drawn from the starter list and side B from the second
  list (200 seeded decisions each, every layout in the list, frequencies within 0.05); a refused layout is never
  proposed again and the decision ends when every list layout is refused (falls back on the catalogue); the
  existing `itsLayoutIsLegalNeverRefused...` and `itsLayoutIsReproducible...` stay green; the strength test
  `theExpertBeatsTheGreedyBotInAtLeast70PercentOfOneHundredSeededGames` stays green (if it drops under 70, stop
  and report: it is the guard against the trade-off of Q1).
- `StudyExpert` keeps the old layout search for the study (see "Files at risk"); its search equivalence test stays.

## P2 `feat: the Mangola medium bot places from the placement book` (S)

- `GreedyMangolaBot.proposeLayout` → `fromBook(rules, side, rejected, false)` (uniform over its side's list), or
  the variant the owner chose for Q2. Class comment updated.
- Tests first (`GreedyMangolaBotTest`): layouts come from its side's list, every layout of the list appears over 200
  seeded decisions, a refused layout is never repeated; `itsLayoutsAreLegalAndVaried` stays green (adapt "varied"
  to "at least as many distinct layouts as the list holds" if needed).

## P3 `feat: Mangola placement book, integration` (S)

- `MangolaConfig`: per Q4 (decided: remove at once), `mangola.bots.layout-ms` is gone (property, `DEFAULT_LAYOUT_MS`, the
  `Budget` field, `application-test.properties`, the `MangolaBotWiringIT` assertion).
- `MangolaPlacementStudy`: a `regression` stage, the book against the October 2026 search (`StudyExpert`), 400
  games, to re-check the spec's 0.605 after the merge (done: 0.630 ± 0.047).
- Base spec `2026-10-15-mangola-design.md`, Bots, "Layouts": the book replaces the search sentence; link the study.
- All suites; a review of the whole branch; the roadmap issue commented once merged.
