# Six Men's Morris: plan

Spec: `docs/specs/2026-10-18-six-men-morris-design.md` (rules with sources, state space, domain design,
contract, UI requirements, product questions). Follow-up of issue #147 (Three Men's Morris v2). Written by
Claude Opus 5.5.

**Goal:** remove the Picaria variant and offer Six Men's Morris in its place, in the same game, lobby and
screen: mills that remove a piece, a removal step, a two-piece loss, draws by a quiet-move limit, three bot
levels of which none is very strong, and the perfect-play value of the game measured once offline.

**Order and parallelism.**

```
C1 (Picaria out) ──> C2 (interfaces) ──┬──> C3 (aggregate + wire) ──┬──> C6 (bot coordinator) ──┐
                                       ├──> C4 (transitions + bots) ┘                          ├──> C7 (integration, e2e, docs)
                                       ├──> C5 (offline solve)  ─────────────────────────────────┤
D (canvas, owner chooses) ─────────────┴──> F1 (frontend) ───────────────────────────────────────┘
```

- **Series:** C1 then C2 (both touch every domain file).
- **In parallel after C2:** C3, C4, C5, each in its own worktree with its own sub-agent. F1 runs in parallel
  too, as soon as the canvas choice (D) is made: it codes against the contract of the spec with fixtures,
  not against a running backend.
- **D** (the design canvas) can start as soon as this spec is merged, in parallel with C1 and C2; it is the
  only step that waits for the owner.
- **C6** needs C3 and C4. **C7** needs C3, C5, C6 and F1.

**Tests (every PR):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test` (JUnit, `*IT`,
ArchUnit; no new game package, `ArchitectureRules.GAMES` unchanged); frontend `cd frontend && npm test &&
npx vite build`. Cypress (`docker compose -f docker-compose.e2e.yml`, `npm run cy:run`, or a shifted stack
with `--config baseUrl=...`) for C1, F1 (the specs it touches) and C7; waits on DOM states only. Strict TDD:
each bullet "tests first" is written red, then made green. A dev environment only for F1 and C7 (manual play).

**Conventions:** one draft PR opened at the start of each chunk with its real number (`gh pr create --draft`),
`gh pr ready` at the end; branches `refactor/...`, `feat/...`, `test/...`; conventional titles; new visible
text through `locales/Messages.ts` and `locales/en.ts`; backend paths relative to
`backend/src/main/java/org/kevinkib/cardgames/threemenmorris/` (tests under the same path in `src/test`),
frontend paths relative to `frontend/src/`. Deleting a file: `git rm` if the session allows it; a session whose
harness blocks deletions asks its orchestrator.

**Merge accord.** None of these chunks is in the owner's restricted list (only the Mangola and President
backend domains are): the orchestrator merges each after review and green CI. **D** needs the owner's choice
(not a merge accord). The product questions of the spec have recommended defaults that are pure data; C2 and
C3 use them, and an answer that differs is a one-line change of `MorrisRules.SIX_MEN` (flying, quiet limit)
or a small change of the removal rule.

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| Chunk | Title | Cost | Needs | Parallel with | State |
|-------|-------|------|-------|---------------|-------|
| C1 | `refactor: remove the Picaria variant of Three Men's Morris` (+ the host colour defaults to random) | M | - | D | [x] |
| C2 | `feat: Six Men's Morris rules as data (interfaces)` | S | C1 | D | [x] |
| C3 | `feat: mills, removal and Six Men's Morris in the Morris engine` | L | C2 | C4, C5, F1 | [x] |
| C4 | `feat: Six Men's Morris transitions and bots` | M-L | C2 | C3, C5, F1 | [x] |
| C5 | `test: solve Six Men's Morris offline` | M | C2 | C3, C4, F1 | [x] |
| D | Claude Design canvas, 2-3 options, owner chooses | S | spec merged | C1 to C5 | [x] |
| F1 | `feat: Six Men's Morris on screen` | L | D, C2 (contract) | C3, C4, C5 | [x] |
| C6 | `feat: Morris bots remove pieces and think outside the game lock` | S | C3, C4 | F1 | [x] |
| C7 | `test: Six Men's Morris end to end` (integration) | M | C3, C5, C6, F1 | - | [x] |

About 9 to 11 days of work, 8 PRs.

## Conflicts

| File | Chunks | Handling |
|------|--------|----------|
| `domain/MorrisRules.java`, `domain/Geometry.java`, `domain/MorrisPosition.java`, `domain/MorrisResult.java`, `domain/Action.java`, `domain/bot/MorrisDecision.java` | C1, C2 | series; after C2 they are read only (a change needed by C3/C4 goes in a tiny follow-up on `main`, not in both branches) |
| `domain/ThreeMenMorris.java` | C1, C3 | series (C3 owns it after C1) |
| `domain/bot/MorrisTransitions.java`, `domain/bot/*Bot*.java`, `domain/bot/MorrisSolver.java` | C1, C2 (guard only), C4 | series C1, C2; C4 owns them after |
| `domain/MorrisVariant.java`, `domain/ThreeMenMorrisFactory.java` | C1, C3 | C3 adds `SIX` |
| bot tests with `@EnumSource(MorrisVariant.class)` (`MorrisSolverCrossCheckTest`, `MorrisTransitionsTest`, `PerfectBotTest`, `RandomBotTest`, `MorrisSolverTest`) | C1, C3, C4 | **C3** narrows them to the solvable variants when it adds `SIX` (`names = {"TAPATAN", "ACHI"}` or a `MorrisVariant.solvable()` source); C4 adds Six cases in new test classes, not in these, so it does not touch the same lines |
| `presentation/dto/ThreeMenMorrisDto.java`, `presentation/dto/event/*`, `presentation/ThreeMenMorrisWebSocketController.java`, `presentation/ThreeMenMorrisGameActions.java`, `backend/src/test/resources/golden/three-men-morris-variants-wire.txt` | C1, C3 | series |
| `presentation/bot/MorrisBotCoordinator.java`, `config/ThreeMenMorrisConfig.java` | C6 | one chunk |
| `model/threemenmorris/geometries.ts`, `MorrisState.ts`, `board.ts`, `components/morris/*`, `view/threemenmorris/ThreeMenMorrisGameScreen.vue`, `state/ThreeMenMorris.store.ts` | C1, F1 | series (F1 starts after C1 is merged) |
| `locales/Messages.ts`, `locales/en.ts` | C1, F1 (and any other game's frontend work in flight) | rebase; Morris namespaces only |
| `cypress/specs/morris-variants.cy.ts`, `morris-bot.cy.ts` | C1, C7 | series |
| `docs/architecture/context-map.md` | C1, C7 | series |
| this spec (`docs/specs/2026-10-18-six-men-morris-design.md`) | C5 (result), F1 (UI addendum), C7 | different sections; rebase |

## C1 `refactor: remove the Picaria variant of Three Men's Morris` (M)

Backend, frontend, Cypress and docs in one PR, so `main` never offers a variant the other side lacks.

- **Backend.** Remove `MorrisRules.PICARIA`, `MorrisRules.CENTRE`, `PlacementRule` and the `placement`
  component (record becomes `(geometry, piecesPerPlayer)` for now), `Geometry.PICARIA` and `picaria()`,
  `MorrisVariant.PICARIA`, `PointLockedException`, `isLocked` in `ThreeMenMorris` and `MorrisTransitions`.
  `legalPlacements` and its DTO field stay (Achi). Delete `PicariaTest`, `PicariaSolverResultTest`; drop the
  Picaria cases of `MorrisVariantTest`, `MorrisSolverTest` (41,983), `PerfectBotTest` (opening on point 1),
  `ThreeMenMorrisFactoryOptionsTest`, `ThreeMenMorrisRulesTest` (constructors), `ThreeMenMorrisVariantDtoTest`,
  `ThreeMenMorrisVariantIT`, `ThreeMenMorrisVariantsWireContractTest`, and the Picaria part of
  `golden/three-men-morris-variants-wire.txt`.
- **Tests first:** `ThreeMenMorrisFactoryOptionsTest`: the `variant` descriptor is `tapatan|achi`; `validate`
  refuses `variant=picaria` with `InvalidOptionException`. `MorrisVariantTest`: `fromKey("picaria")` throws.
  IT: `/app/lobby/setOptions` with `variant=picaria` answers an error and the lobby keeps its value.
- **Frontend.** `MorrisVariantKey = 'tapatan' | 'achi'`; `geometries.ts` loses the Picaria entry, its
  points and edges, `crossings` and `closedWhilePlacing` (and `neighbourByPage` if nothing else needs it:
  check the 3x3 boards first); `MorrisBoard.vue` loses the crossing and padlock rendering (`locked` prop);
  `ThreeMenMorrisGameScreen.vue` loses `lockedPoints`; `MorrisMiniBoard.vue` the crossing radius. **Kept:**
  the `refined` flag and its styles, and `MorrisPlayerTag`'s `marked` (generic; the canvas decides for Six; F1
  removes them if unused). Locales: `variants.picaria`, `morrisVariantRules.picaria`, `options...variant.picaria`,
  `closed`, `centreClosed`, `pointNames` beyond 9. Vitest files updated (Picaria cases out; a test that the
  lobby shows two cards).
- **Cypress.** Delete `morris-picaria.cy.ts`; remove the Picaria parts of `morris-variants.cy.ts` and
  `morris-bot.cy.ts`. Run the Morris specs.
- **Docs.** `docs/architecture/context-map.md` (variants `tapatan|achi`, "Picaria removed 2026-10-18, see
  the Six Men's Morris spec"); a dated note at the top of `docs/specs/2026-10-14-three-men-morris-v2-design.md`
  ("Picaria removed, replaced by Six Men's Morris"; the Picaria sections stay as history).
- **Migration note in the PR body:** rooms are in memory, so the deployment ends them; an old tab sending
  `picaria` gets the existing invalid-option error; no alias.

## C2 `feat: Six Men's Morris rules as data (interfaces)` (S)

The shared types every later chunk reads. No behaviour change for Tapatan and Achi; `MorrisVariant` is not
touched (Six is not playable yet).

- `Geometry.SIX_MEN` (16 points, the numbering, adjacency and 8 lines of the spec).
- `MorrisRules(geometry, piecesPerPlayer, LineRule, RemovalRule, FlyingRule, quietPlyLimit)` with the enums,
  the constructor checks, `solvable()`, the constants `TAPATAN`, `ACHI`, `SIX_MEN`, and a convenience
  constructor `(geometry, pieces)` for `WIN` rules.
- `MorrisPosition.removing` (4-argument constructor kept), `MorrisDecision.Remove`, `Action.REMOVE`,
  `MorrisResult.Reason.REDUCED` and `DRAW_MOVE_LIMIT` (`isDraw`).
- `MorrisSolver.forRules` and the constructor refuse rules that are not `solvable()`.
- `ThreeMenMorris`: `MILL` rules are refused by the constructor for now (`UnsupportedOperationException`,
  removed by C3), so nothing half-built can be created.
- Frontend types only (`model/threemenmorris/MorrisState.ts`): the optional fields of the contract
  (`removal`, `legalRemovals`, `players[].lost`, `quietPlies`, `quietPlyLimit`), `MorrisAction` gains
  `REMOVE`, `MorrisReason` gains `REDUCED`, `DRAW_MOVE_LIMIT`, the `REMOVE` event data with `mill`. No
  `six` key yet. This pins the contract F1 codes against.
- **Tests first:** `GeometryTest` (16 points; each corner has 2 neighbours, each middle 3; 20 edges; 8 lines,
  each a straight side; no line on a join; the 16 symmetries map lines to lines), `MorrisRulesTest` (the
  constants, `solvable()`, refusals), `MorrisPositionTest` (`removing` default false, part of equality, not of
  `Key`), `MorrisSolverTest` (`forRules(SIX_MEN)` throws).

## C3 `feat: mills, removal and Six Men's Morris in the Morris engine` (L)

Owns `ThreeMenMorris.java`, the factory, `MorrisVariant`, presentation and the golden wire files.

- **Domain:** the aggregate of the spec (pending removal, `remove`, mill detection through the destination,
  protection rule, `REDUCED`, flying `AT_THREE`, quiet plies and `DRAW_MOVE_LIMIT`, repetition counts cleared
  by a removal, draw offer cancelled by a removal), `fromLayout(id, rules, layout, ...)`, the four exceptions,
  `MorrisEvent.Removed`, `MorrisEvent.Placed/Moved` gain `mill` (empty list when none).
  `MorrisVariant.SIX("six", MorrisRules.SIX_MEN)`. Remove C2's constructor refusal.
- **Narrow the solver tests** to `TAPATAN` and `ACHI` (see Conflicts) in the same commit that adds `SIX`.
- **Presentation:** `ThreeMenMorrisGameActions.remove`, destination `/three-men-morris/remove` with
  `ThreeMenMorrisRemovePayload`, event type `REMOVE`, `ThreeMenMorrisEventData` gains `NON_NULL` `mill`,
  DTO fields of the spec (written only for `MILL` rules). New golden file
  `golden/three-men-morris-six-wire.txt`; the Tapatan and Achi golden files stay byte-identical (their tests
  are the proof).
- **Tests first** (`SixMenMorrisTest`, `SixMenMorrisRemovalTest`, `SixMenMorrisEndTest`,
  `SixMenMorrisDrawTest`): a mill during placement makes `REMOVE` the only action and keeps the turn; place
  or move during a pending removal is refused; removing an own piece, an empty point, or a protected piece is
  refused; when all opponent pieces are in mills one of them can be taken; a double mill gives one removal; a
  removal that leaves 2 pieces finishes `REDUCED`; placement ends when both hands are empty (after 12
  placements, whatever was removed meanwhile) and White moves first; a blocked seat after a removal loses; flying under
  `AT_THREE` (a rules instance built in the test) and not under `SIX_MEN`; 50 quiet plies draw, a removal
  resets the count; a repetition is not counted while a removal is pending and the count is cleared by a
  removal; draw offer cancelled by a removal; version bumps on every accepted command. DTO tests
  (`removal`, `legalRemovals` for the remover only, `lost`, `quietPlies` from 40), controller test, an IT
  that plays a scripted game to a mill and a removal between two seats, `ThreeMenMorrisFactoryOptionsTest`
  (`variant=six` builds `SIX_MEN`).

## C4 `feat: Six Men's Morris transitions and bots` (M-L)

Owns `domain/bot/`. Works from `MorrisRules.SIX_MEN` directly (not from the enum), so it does not wait for C3.

- `MorrisTransitions` for `MILL` rules (removing positions, `Remove` decisions, `Win` on two pieces or a block,
  flying when the rules say so); `GreedyBot` generalised (mill now, block a threat, safe move, removal choice);
  `SearchBot` (negamax alpha-beta, 3 plies, removal in the same ply, node cap 200,000, evaluation of the spec,
  ties through the generator); `PerfectBot` delegates to `SearchBot` when the rules are not solvable;
  `AbstractMorrisBot.answerDrawOffer` uses the solver only for solvable rules, else "accept unless ahead in
  pieces left"; `RandomBot` unchanged except removals come for free from `legal`.
- **Tests first** (new classes, so the enum-driven tests are not edited here): `SixMenTransitionsTest`
  (placements, steps, a mill gives a removing position with the same seat to move, protected pieces not
  listed, all-in-mills lists all, `Win` on two pieces left, on a block; no flying), `SixMenGreedyBotTest`
  (closes a mill; blocks a threat; removes a threatening piece), `SearchBotTest` (finds a mill in two plies
  that greedy misses; avoids a move that lets the opponent mill; answers within the node cap; deterministic
  with a constant generator; never returns an illegal decision on 1,000 random Six positions),
  `SixMenDrawAnswerTest`. Benchmark `SixMenStrengthBenchmark`, opt-in (`-Dmorris.bench=true`), 100 games
  each pairing, colours alternating, results written into the spec's bot section.
- Measure and record the time per Expert decision (target under 50 ms on the CI runner; lower the node cap
  if not).

## C5 `test: solve Six Men's Morris offline` (M)

Test sources only; production code untouched (so it cannot conflict with C3 or C4).

- `SixMenMorrisSolveRunTest` (`@EnabledIfSystemProperty(named = "morris.solveSix", matches = "true")`) and a
  compact solver next to it (16-bit masks per colour, dense index by combinatorial rank, layers by pieces
  left solved from the smallest, sweeps to a fixed point inside a movement layer, the placement phase by
  backward induction). It prints and asserts nothing about the value on its first run; the PR then pins what
  it found.
- **Tests first** (these run in the default suite, they are small): `SixMenSolverIndexTest` (rank and unrank
  are inverse on every layer; counts per layer equal `C(16,w) * C(16-w,b)`), `SixMenSolverSmallTest` (on a
  reduced rule set, 4 pieces each, the compact solver agrees with a brute-force search written in the test),
  rule cross-check against hand-written positions of the spec (mill, protection, double mill, two-piece
  loss, block).
- **Run** once: `mvn -s ../deploy/docker/settings.xml test -Dtest=SixMenMorrisSolveRunTest
  -Dmorris.solveSix=true` (check that Surefire forwards the property; else `-DargLine=-Dmorris.solveSix=true`),
  with enough heap (`-DargLine=-Xmx2g`). Record in the spec ("Who wins with perfect play"): the value of the
  opening for White, the reachable positions (compare with Liou's 42,372,745), whether `BLOCKED` and `REDUCED`
  endings are reachable, time and memory. **If White (or Black) has a forced win**, say so in the PR body and
  flag it to the orchestrator for the owner before F1 merges.

## D Claude Design canvas (owner chooses) (S)

Before F1, as the global rules require for a new screen layout: Artifact `quickstart` with intent `design`,
**2 or 3 options** of the Six table on real positions (an opening, a pending removal with protected pieces
marked, a 3 vs 4 endgame with the quiet-ply warning, the end screen with `REDUCED`), at 375 px and 1280 px,
light and dark, from the existing Morris screen (keep its tags, hint, draw controls). One recommendation. The
owner chooses; the choice and a link go into a short "UI addendum" section of the spec, written by F1.

## F1 `feat: Six Men's Morris on screen` (L)

Starts after C1 is merged and D is chosen; codes against the C2 contract with fixtures; Vitest only (the
Cypress specs come in C7).

- `geometries.ts`: `six` (16 points, 20 segments, 6 pieces); `VARIANT_KEYS` gains `six` (this is what makes
  the lobby card appear, once the server offers it).
- `board.ts`: `selectable` returns `legalRemovals` when `REMOVE` is available; `onPoint` gives
  `{ kind: 'remove', point }`; `MorrisSession`/store send `/three-men-morris/remove`; the store keeps the last
  removal for the captured marker.
- `MorrisBoard.vue`: removable state, mill highlight during `removal`, captured marker; the look of the
  chosen canvas option. `MorrisPlayerTag.vue`: on board and lost counts, the three-pieces warning.
  `ThreeMenMorrisGameScreen.vue`: removal prompts, live-region announcements, quiet-ply warning, end-screen
  subtitles for `REDUCED` and `DRAW_MOVE_LIMIT`. `MorrisLobbyOptions.vue` and `MorrisMiniBoard.vue`: the `six`
  card. Rules panel: a Six section; the level note ("Expert looks three moves ahead on Six").
- Locales: every new text in `Messages.ts` and `en.ts`; `pointNames` for 16 points.
- Remove `refined` / `marked` if the chosen option does not use them.
- **Tests first:** `geometries.test.ts` (16 points, segments join neighbours only, every point reachable by
  arrows, `segmentsOnLine` returns the two half-sides of a mill), `board.test.ts` (removal selection and
  intent; nothing selectable for the other seat), `MorrisBoard.test.ts` (only removable pieces are buttons
  during a removal, the mill is lit, the captured marker), `MorrisPlayerTag.test.ts` (counts),
  `ThreeMenMorris.store.test.ts` (remove command, last removal), `ThreeMenMorrisGameScreen.test.ts` (prompts,
  warnings, end subtitles), `MorrisLobbyOptions.test.ts` (three cards). Before/after inventory of what the
  Morris screen shows for Tapatan and Achi (global UI rule): nothing disappears; the `ui-change` skill for the
  visual part.
- Dev environment for a manual game (two tabs, and one against each bot level) once C3 is on `main`.

## C6 `feat: Morris bots remove pieces and think outside the game lock` (S)

- `MorrisBotCoordinator`: schedules a bot seat whose action is `REMOVE` (same delay); takes a snapshot
  (position, level, version) under the monitor, decides outside it, re-takes the monitor, checks the version,
  then acts through `ThreeMenMorrisGameActions.remove` / `place` / `move`; the fallback "first legal action"
  covers `REMOVE`. Same pattern as `MangolaBotCoordinator`.
- **Tests first:** coordinator test with a scheduler stub (a bot that closes a mill is scheduled again and
  removes; a human move between snapshot and action drops the stale decision; a failing strategy falls back
  to the first legal removal), IT: a human against the greedy bot on Six with a scripted opening reaches a bot
  removal; the existing Tapatan coordinator tests stay green.
- **Cross-check** (needs C3 and C4, so it lives here): `SixMenTransitionsCrossCheckTest` replays 200 random
  Six games through the aggregate and `MorrisTransitions` and compares the legal decisions and the endings at
  every ply (the 3x3 cross-check is the model).

## C7 `test: Six Men's Morris end to end` (integration) (M)

- Merge order check: C3, C4, C5, C6, F1 all on `main`; rebase this branch on it.
- Cypress `morris-six.cy.ts`: two players (the `stompPlayer` helper for the second seat) play a scripted game:
  placements, a mill, the removal prompt with only removable pieces clickable, a protected piece refused
  (not clickable), the lost count, the movement phase, a win by `REDUCED` with its end screen; a second
  test against the greedy bot (constant generator under `test`) up to the bot's first removal. Update
  `morris-variants.cy.ts` (three cards: Tapatan, Achi, Six) and `morris-bot.cy.ts` (Six with Easy).
- Docs: `docs/architecture/context-map.md` (variants `tapatan|achi|six`, mills and removal, `SearchBot`,
  the coordinator outside the lock); the spec's status lines (solver result from C5, benchmark from C4, UI
  addendum from F1) checked; this plan's status table.
- All suites (backend, frontend, the Morris Cypress specs on a shifted stack if 5173 is taken), a review of
  the whole Six feature on `main` plus this branch (the `code-review` skill), and a comment on issue #147 (or
  the follow-up issue the orchestrator opens) with the merged PRs. No status PR.
