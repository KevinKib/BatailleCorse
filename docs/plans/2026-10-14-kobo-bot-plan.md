# Kobo bot: plan

Spec: `docs/specs/2026-10-14-kobo-bot-design.md` (hidden information, strategy, match race, levels,
coordinator, decisions to confirm). Written by Claude Opus 5.5.

**Goal:** add one to five computer players to a Kobo table, at three levels, that memorise, play their turns,
take part in the matching race with a human-like reaction time and never cheat.

**Order:** B1 (domain, nothing visible) → B2 (table wiring; the server accepts Kobo bots, the frontend does not
offer them yet) → B3 (frontend). Strictly in series: each needs the previous one's API. B3 starts after #206
(Kobo K7b, animations) is merged, or rebases on it. Independent of the Morris frontend PRs except for two
shared files (see Conflicts).

**Tests (every PR):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test` (JUnit, `*IT`, ArchUnit
`ArchitectureTest` green; no new game package, so `ArchitectureRules.GAMES` is unchanged; `kobo/domain/bot`
must depend on nothing but `kobo/domain`, the `game` kernel and the cards library); frontend
`cd frontend && npm test && npx vite build`. Cypress for B3 only (`docker compose -f docker-compose.e2e.yml`,
`npm run cy:run`, or a shifted stack with `--config baseUrl=...`); waits on DOM states only, never
`cy.wait(ms)`. TDD: each listed test is written red first. Dev environment only for B3 (manual test).

**Conventions:** branches `feat/kobo-bot-domain`, `feat/kobo-bot-table`, `feat/kobo-bot-frontend`;
conventional titles; one draft PR opened at the start of each step with its real number; every new visible
text through `locales/Messages.ts` and `locales/en.ts`. Backend paths relative to
`backend/src/main/java/org/kevinkib/cardgames/kobo/` unless stated, frontend paths relative to
`frontend/src/`.

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| PR | Title | Cost | Needs | State |
|----|-------|------|-------|-------|
| B1 | `feat: Kobo bot strategy and memory` | M-L | - | [x] done (PR #208) |
| B2 | `feat: Kobo bots at the table` | M-L | B1 | [x] done (PR #209) |
| B3 | `feat: play Kobo against bots` | S-M | B2, #206 | [x] done (PR #213) |

Total about 4 to 5 days of work. B1 may be split in two PRs (B1a view and memory, B1b strategy and self-play)
if its diff passes about 800 lines.

## Conflicts

| File | PRs | Handling |
|------|-----|----------|
| `config/KoboConfig.java`, `kobo/presentation/dto/KoboDto.java` | B2 only | no other open work touches them |
| `backend/src/main/resources/application-test.properties` | B2 | one new block; the Morris block is already merged |
| `docs/architecture/context-map.md` | B2 | new "Kobo bots" paragraph after "Three Men's Morris bots" |
| `model/games.ts`, `model/games.test.ts` | B3, Morris F3 | one flag each; rebase |
| `locales/Messages.ts`, `locales/en.ts` | B3, Morris F1-F3, Kobo K7b (#206) | separate `options.kobo` namespace; rebase |
| `view/kobo/KoboGameScreen.vue`, `state/Kobo.store.ts`, `model/kobo/*` | B3, K7b (#206) | B3 after #206, or rebase on it |
| `cypress/specs/kobo-lobby.cy.ts`, `shared-lobby.cy.ts` | B3 | their "no Add bot" assertions for Kobo change |

## B1 `feat: Kobo bot strategy and memory` (M-L)

Domain only, package `domain/bot/`, no wiring, no visible change.

- `KoboBotView` (record): `forSeat(Kobo game, PlayerId seat)` under the game's monitor, the fields of
  `KoboDto.forViewer` in domain types; slots as `(slot, occupied, revision)`, cards only when
  `game.isRevealed()`; private parts from `startingCards`, `drawnCard`, `revealFor`; `lastRound` from
  `lastRoundResult()`. Helpers `can(Action)`, `opponents()`.
- `KoboBotMemory`: entries `SlotRef -> (revision, Card)`; `onMoved(from, to)`, `onOwnDrawnPlaced(slot)`,
  `observe(view)` (re-stamp, drop stale, record private parts, clear on a new round), `forget(random, p)`,
  queries `known(SlotRef)`, `knownOfRank(Rank)` (own first), `knownSlots(seat)`. Constructor takes the seat and
  whether it follows public moves.
- `KoboBotDecision` (sealed): `Ready`, `Draw`, `SwapDrawn(slot)`, `DiscardDrawn`, `Peek(SlotRef)`,
  `BlindSwap(ownSlot, SlotRef)`, `KingSwap(ownSlot)`, `SkipPower`, `EndTurn`, `AnnounceKobo`,
  `GiveTen(PlayerId)`, `Match(SlotRef target, Integer giveSlot, Rank expectedTopRank, int expectedRevision)`.
- `KoboBotStrategy` (port): `decide(view, memory)`, `matchFor(view, memory)`.
- `HeuristicKoboBot`: the decision table, announce rule (with round pressure), gift rule and match rule of the
  spec; the unknown-card value is the deck average computed from `CardPoints` (6.5).
- `KoboBotTuning` (record, validated), `KoboBotLevel` (`EASY`, `MEDIUM`, `HARD`; `key()`, `DEFAULT = MEDIUM`,
  `fromOption`, `tuning()`).
- Tests first:
  - `KoboBotViewTest`: in `MEMORISING`, `DECISION`, `POWER`, `KING_DECISION`, `TURN_END` no card of another
    seat; own starting cards until the first draw only; drawn card only for the current seat in `DECISION`;
    reveal only for the peeker; every card once revealed.
  - `KoboBotMemoryTest`: starting cards survive the first draw; a peek survives the next command; a revision
    change forgets; blind swap, king swap and match gift relocate (and only forget when moves are not
    followed); own drawn card placed after `SWAP`; new round clears; forgetting with a scripted generator
    (0.0 forgets, 0.99 keeps at p = 0.08).
  - `HeuristicKoboBotTest`: one test per row of the spec's decision table; red King kept; a 6 discarded with
    only unknown cards; a 7 discarded then a peek on the lowest unknown own slot; J swaps a known 12 for an
    unknown opponent card, skips with nothing known; king swaps a peeked 1 for a known 10, skips a peeked 9;
    announce at estimate 5 with every card known at `hard`, not with two unknowns at `medium`, not when an
    opponent's known hand is lower, always at 0 cards; pressure after the 6th own turn; gift avoids 75 and
    100, pushes over 100 when the bot then leads; match: own card first, opponent's card only at
    `medium`/`hard`, highest card given, no match without a remembered card, `expectedRevision` = memory's.
  - `KoboBotLevelTest`: tuning per level as in the spec table; `fromOption(null)`, `fromOption("x")` give
    `MEDIUM`.
  - `KoboBotSelfPlayTest`: seeded games of 2, 3 and 6 bots (all levels mixed) driven by a test harness
    (`KoboBotHarness` in test sources: after each command, every bot is asked `matchFor` in a seeded order,
    then the current or barrier seats `decide`): each game reaches `FINISHED` within 20,000 commands,
    `cardsInPlay() == 52` after every command of a live round, no `MatchPenalised` by a bot, no exception.
- Docs: none (the context map is updated in B2 with the wiring).

## B2 `feat: Kobo bots at the table` (M-L)

- `domain/KoboFactory.java`: `supportsBots() = true`; `seatOptionDescriptors()` = `level` (`easy|medium|hard`,
  default `medium`, label `options.kobo.level`); constant `LEVEL_KEY`.
- `presentation/dto/KoboDto.java`: `PlayerDto.bot`; `forViewer(game, viewer, names, bots)`; the existing
  overloads delegate with an empty set.
- `presentation/bot/KoboBotCoordinator.java`: per spec section 6 (table per game, memories, `turnTasks` and
  `matchTasks`, `scheduledVersion`, last top; translation of `KoboEventData` into `onMoved` /
  `onOwnDrawnPlaced`; turn tasks with version check; match tasks keyed on the top; fallback per phase; drop on
  finish, eviction, rematch, no human left). Constructor: `BotSeats`, `BotSeatOptions`, `KoboGameActions`,
  `BotScheduler`, `ThinkingDelay`, `ReactionDelay` (new small port in `presentation/bot/` of the Kobo package:
  `Duration next(KoboBotTuning)`), `Function<KoboBotLevel, KoboBotStrategy>`, `RandomGenerator`.
- `config/KoboConfig.java` (path relative to `cardgames/`): broadcaster with `sessionService::botSeats` and
  the `bots` argument passed to the projection; `koboBotRandom` bean (`ThreadLocalRandomGenerator` with
  `@Profile("!test")`, a fixed-seed generator with `@Profile("test")`); the coordinator bean registered on the
  broadcaster, delays from `kobo.bots.delay-min-ms` (600) / `delay-max-ms` (1200) and
  `kobo.bots.reaction-scale` (1.0), built here and not published as beans (as in `ThreeMenMorrisConfig`).
- `backend/src/main/resources/application-test.properties`: `kobo.bots.delay-min-ms=0`,
  `kobo.bots.delay-max-ms=0`, `kobo.bots.reaction-scale=0`.
- Tests first:
  - `KoboBotViewParityTest` (`kobo/presentation` test package): `KoboBotView` equals `KoboDto.forViewer` field
    for field in each phase, for the current seat and another one.
  - `KoboDtoTest`: `bot` true for bot seats only; `KoboFactory` tests: `supportsBots`, the `level` descriptor,
    `validateSeatOption` refuses `level=expert`.
  - `KoboBotCoordinatorTest` (manual scheduler, fixed generator): acts after the thinking delay; a stale turn
    task is dropped; readies at `MEMORISING` and `ROUND_OVER`; gives ten in `GIFT`; a match task survives a
    human's `draw` and fires; it is cancelled when the top changes; a lost race (human matched first) is
    ignored, no fallback; a throwing strategy falls back per phase; nothing scheduled once finished, after
    eviction, or when only bots remain; a new `Kobo` under the same id gets a fresh table and memories.
  - Broadcaster test: a bot seat receives no message; the listener is called once per broadcast.
  - `KoboBotGameIT` (real `SessionService`, `KoboGameActions`, zero delays, fixed deal through
    `ScriptedDeckSource`): two scripted humans and one bot at `hard`; the bot readies, plays turns, matches its
    starting card when a human discards that rank; the round reaches `ROUND_OVER`; Play again keeps the bot
    and its level; the bot seat never gets a message and is never forfeited.
  - `KoboWiringIT` (or an addition to the existing wiring test): the coordinator is a listener of the Kobo
    broadcaster and an eviction listener.
- Docs: `docs/architecture/context-map.md` gains "Kobo bots" (domain view and memory, coordinator, ports,
  levels, match tasks keyed on the top).

## B3 `feat: play Kobo against bots` (S-M)

Start after #206 is merged, or rebase on it. Text-only change on the table (no new visual), so no Claude
Design canvas: the lobby already shows bots and their seat option with the shared components.

- `model/games.ts`: `supportsBots: true` for `kobo`; `games.test.ts` updated.
- Locales `options.kobo.level` (`label: 'Level'`, `easy: 'Easy'`, `medium: 'Medium'`, `hard: 'Hard'`).
- `model/kobo` player type gains `bot?: boolean`; `view/kobo/KoboGameScreen.vue` names seats through the
  shared `model/table/displayName.ts` (bots labelled "Bot N" from the flag), every place that shows a name
  keeps showing it (list before and after, per the global UI rule).
- Tests first: Vitest (`games.test.ts`; `KoboGameScreen.test.ts`: the lobby shows Add bot and the level select
  for the host; a bot seat named "Bot 1" whatever its name). Cypress: `kobo-lobby.cy.ts` and
  `shared-lobby.cy.ts` updated (Add bot now present for Kobo); new `kobo-bot.cy.ts` (fixed deal via the test
  deal endpoint, zero delays): add a bot, set Hard, start, memorise, the bot plays a turn (event log shows it),
  the human announces Kobo and sees the bot in the round scores; Play again keeps the bot and its level.
- Manual check in the dev environment (shifted ports if needed): with the default delays each step of a bot
  turn is visible, and a bot matches a few seconds after a matching discard.

## After the merges

- Comment on the Kobo tracking issue (#149) and the roadmap (#1) with the merged PRs; no status PR.
- Update this plan's Status table in the last PR.
