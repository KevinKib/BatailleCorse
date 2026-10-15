# President: plan

Spec: `docs/specs/2026-10-15-president-design.md` (rules, variant catalogue with sources and uncertainty
flags, options, domain model, DTO, bots, UI decisions). Written by Claude Opus 5.5 on `origin/main` at
`28c5832`. Checklist followed: `docs/architecture/add-a-game.md`; names: `docs/architecture/game-conventions.md`
(`president` everywhere: id, package, `President` prefix, `president` camel form).

**Goal:** a fifth game, President (Le Président / Trou du cul), playable with every offered variant, against
humans and bots, in a series of small PRs.

## Key decisions to confirm (from the spec, section 13)

1. **Identity and titles**: id `president`; English UI titles President / Vice-President / Neutral /
   Vice-Scum / Scum; French names (Trou du cul...) only in the rules text.
2. **Players and decks**: 3 to 8 players with one deck when the game opens, 10 with two decks from V4; no
   2-player mode.
3. **Classic defaults**: re-entry after a pass, equal play skips the next player, forbidden finish on a 2,
   exchange 2/1 with a free return, the Scum leads rounds 2+, the 3♣ leads round 1, rank points over 5 rounds;
   every other variant off. Presets (Classic, Pagat basic, Strict, Party, Daifugō-like) are a lobby
   convenience, unknown to the server.
4. **Exchange flow**: the server takes the low ranks' best cards automatically; the high ranks choose their
   returns freely, all at once, no timeout (the disconnect forfeit covers absence).
5. **Order**: base game end to end first (P1-P3, F1-F4), then bots on the base rules (B1-B3, candidates from
   the domain's `LegalPlays` so later variants cannot break them), then the variant groups V1-V4 (each full
   stack), then the e2e matrix E1.

Other decisions (spec §13, items 6-12): revolution timing and duration, skip counted as a pass, forbidden
finishers at the bottom, a ready barrier between rounds, forfeit down to two players, every option
combination accepted, uncertain variants kept as options.

## Rules for every PR

- Own worktree and branch from `origin/main` (`feat/president-<subject>`), PR to `main`, a draft PR with its
  real number opened at launch (empty commit), never merged without the owner.
- **Strict TDD**: a `test: …` commit with the failing tests before the `feat: …` commit that makes them pass
  (`add-a-game.md` §6). Rules tables are **parameterised tests** (`@ParameterizedTest` with `@MethodSource` or
  `@CsvSource`): one row per line of the spec's tables (ranking, titles by player count, exchange amounts,
  beat matrix, effects, scoring).
- Domain tests: JUnit 5 + Hamcrest, no Mockito; a `PresidentBuilder` (explicit hands, trick, phase, titles,
  options) and `PresidentFixtures` (`cards("3C 3H 2S")`).
- Every PR keeps an invariant test: the cards of hands + trick + discarded tricks are always the whole deck
  (52, 54, 104 or 108), after every command of a scripted or random game.
- Suites before the PR: `cd backend && mvn -s ../deploy/docker/settings.xml test` (unit, `*IT`, ArchUnit);
  `cd frontend && npm test && npx vite build`. Cypress for the PRs with a visible change, on the e2e stack or
  a shifted stack (`--config baseUrl=...`), waiting on DOM states or frames, never `cy.wait(ms)`.
- Every visible text through `frontend/src/locales/` (`president`, `presidentUi`, `options.president`).
- A dev environment only for the PRs with visible play (F2-F4, B3, V1-V4), never for P1-P3, F1, B1-B2, E1.
- UI PRs follow the `ui-change` skill (inventory before/after, 375 and 1280 px, light and dark). The screens of
  F2-F4 wait for the owner's choices on the Claude Design canvas (spec §11).
- Tracking: open an issue "President" at P1 launch; comment on it and on roadmap #1 at each merge; no status
  PR. Update this Status table in the last PR.

**Cost scale** (as the Kobo plans): S about half a day, M about a day, L about two days.

## Status

| PR | Title | Size | Needs | Tests (strategy) | Risks | State |
|----|-------|------|-------|------------------|-------|-------|
| D0 | `docs: President game spec and plan` | S | - | - | owner reviews the variant catalogue | #216 |
| P1 | `feat: President round rules (domain)` | L | D0 | ranking, play parsing, beat matrix, trick end, going out, titles: parameterised tables; scripted rounds | trick-end counting with re-entry and players out | done (#219) |
| P2 | `feat: President match, exchange and scoring (domain)` | M-L | P1 | exchange amounts by `n` and option, best-card selection, returns, scoring tables, match end, forfeit in each phase | forfeit during the exchange | done (#223) |
| P3 | `feat: President at the table (server)` | M-L | P2 | factory and options (strict parse), DTO privacy, actions, controllers, STOMP IT, deal seam, shared tests | shared test counts collide with Mangola (#217) | - |
| F1 | `feat: President model, session and store` | M | P3 | Vitest: DTO fixtures, one test per command, selection-to-pattern matcher, version guard | none visible | - |
| F2 | `feat: President in the game selection, lobby and table` (entry PR) | L | F1, canvas 1-3, 6 | Vitest per component; Cypress lobby + a scripted round | the only entry PR at a time (Mangola) | - |
| F3 | `feat: President exchange step` | M | F2, canvas 4 | Vitest; Cypress with a fixed deal into round 2 | waiting state for the non-exchanging seats | - |
| F4 | `feat: President round summary, standings and end screen` | M | F2, canvas 5 | Vitest; Cypress to `FINISHED` and Play again | several winners, forfeit subtitle | - |
| B1 | `feat: President bot strategy` | M-L | P2 | view parity (in B2), memory, heuristic decision table (parameterised), self-play | strategy quality is subjective | - |
| B2 | `feat: President bots at the table` | M | B1, P3 | coordinator with a manual scheduler, broadcaster skips bots, bot game IT | stale tasks | - |
| B3 | `feat: play President against bots` | S-M | B2, F2 | Vitest; Cypress lobby with bots, a round against a hard bot | shared lobby specs | - |
| V1 | `feat: President trick variants (equal play, cuts, clears, forbidden finish, must play)` | L | P3 (F2 for the UI part) | beat and effect matrix per option value, parameterised; Cypress per visible indicator | combinations of skip, or-nothing and cuts | - |
| V2 | `feat: President jokers, revolution and aces high` | L | V1 | rank order under each option and revolution state; wild-set parsing; exchange best cards with jokers | wild jokers in quads and revolution | - |
| V3 | `feat: President runs and suit lock` | L | V2 | run parsing and beating tables; lock tables; `LegalPlays` enumeration size | `LegalPlays` growth with runs | - |
| V4 | `feat: President two decks, putsch and fallen President` | L | V3 | two-deck deal and multiset tests up to 10 players; putsch and fall scenarios | 10 seats on a phone; putsch rule uncertain | - |
| E1 | `test: President end-to-end matrix` | M | V4, B3 | Cypress: each preset through a scripted round, rematch, disconnect and forfeit | e2e duration | - |

Total about 20 to 24 days of agent work. P1 and P2 may be one PR if the diff stays under about 800 lines.
F3 and F4 can run in parallel after F2; B1 can start right after P2 (pure domain), in parallel with P3. The
variant PRs are in series (they all extend `BeatRule`, `PlayEffects` and `LegalPlays`).

## Conflicts

| File | PRs | Handling |
|------|-----|----------|
| `config/PerGameConfigTest.java`, `architecture/ArchitectureRules.java`, `ArchitectureTest.java`, `MalformedTokenIT.java`, `LobbyControllerTest.java` | P3, Mangola backend (#217's plan) | counts and lists: rebase the second, never weaken a shared test |
| `model/games.ts`, `router.ts`, `useGameRooms.ts`, `locales/*`, `GamePickerView.test.ts`, picker Cypress specs | F2, Mangola entry PR | never two entry PRs at once (`add-a-game.md` §1): F2 waits for the other entry PR to merge, or the reverse |
| `president/domain/play/*`, `PresidentDto.java`, `components/president/*` | V1-V4, B1-B3 | in series; bot candidates come from `LegalPlays`, so a variant touches the heuristic only to add a preference |
| `docs/architecture/context-map.md` | P3, B2, V4 | one section, extended |

## D0 `docs: President game spec and plan` (this PR, #216)

Spec, plan, nothing else.

## P1 `feat: President round rules (domain)` (L)

Package `president/` with `package-info.java` and `domain/` only; no wiring.

- `rules/PresidentOptions` with the P1 keys (`afterPass`, `firstLead`) and the enums; every later key exists
  in the record as its "off" value, so V-PRs only add parsing.
- `card/RankOrder` (normal order now; the `ranking` and revolution hooks are parameters that V2 fills),
  `HandSort`.
- `play/Play`, `PlayParser`, `BeatRule` (same count, strictly higher), `LegalPlays` (sets only),
  `PlayEffects` (none yet).
- `trick/Trick`, `round/Round`, `round/Phase` (`PLAYING`, `ROUND_OVER`), `exchange/Title`,
  `Titles.forPlaces(n)`.
- `President` aggregate (one round per instance until P2 adds the match), `DeckSource`, `RandomDeckSource`,
  `ScriptedDeckSource`, events, exceptions.
- Tests first:
  - `RankOrderTest` (parameterised: every rank pair, 3 low to 2 high).
  - `PlayParserTest` (parameterised: valid single/pair/triple/quad; mixed ranks, empty, unknown card →
    `IllegalPlayException`).
  - `BeatRuleTest` (parameterised matrix: count × rank of top and candidate → beats / refused).
  - `LegalPlaysTest`: leading lists every set of the hand; following lists only beating sets of the count.
  - `PresidentDealTest`: 3 to 8 players, all 52 cards dealt, sizes differ by at most one, the extra cards go
    to the seats right after the dealer, seeded determinism, `ScriptedDeckSource` order.
  - `FirstLeadTest`: `lowestClub` (3♣ holder), `queenOfHearts`, `random` (scripted generator).
  - `TrickTest` (scripted): pass and re-entry; `afterPass = out` (a passer cannot play again; trick ends when
    only the last player remains); the leader cannot pass; trick won by the last player; the last player
    went out → lead to the next seat with cards.
  - `GoingOutTest`: places in order; the round ends when one seat has cards; titles for 3, 4, 5 and 8 players
    (parameterised from the spec table).
  - `PresidentCommandGuardsTest`: wrong seat, wrong phase, cards not in hand, finished game: no mutation, no
    version bump.
  - Invariant: random legal games (seeded, a random legal-play driver) keep 52 cards and end.
- Docs: none (context map in P3).

## P2 `feat: President match, exchange and scoring (domain)` (M-L)

- Phases `EXCHANGE` and `FINISHED`; `ready` barrier; next round: deal from the previous Scum, titles in
  force, exchange, `nextLead`.
- `exchange/ExchangePlan` (`two`, `one`, `scaled`, `none`), automatic best cards, `giveBack` (`free`,
  `lowest`), `PendingReturn`.
- `scoring/MatchScoring` (`rank`, `titles`, `zeroSum`), `Standings`, `RoundResult`, match `length`
  (`rounds-3|5|10`, `points-11|21`, a `zeroSum` target stops after 10 rounds), `PresidentResult` (several
  winners on a tie).
- `forfeit`: in `PLAYING` (current player, trick top, last-but-one player), in `EXCHANGE` (pending return
  completed with the lowest cards), in `ROUND_OVER` (barrier re-evaluated); down to two players continues,
  one left finishes with reason `FORFEIT`.
- Tests first:
  - `ExchangePlanTest` (parameterised: `n` from 3 to 8 × `exchange` → pairs and amounts).
  - `BestCardsTest` (parameterised: hands → the k best, ties of rank irrelevant).
  - `ExchangeFlowTest`: best cards moved at the deal; returns in any order; wrong count; a seat with no return
    refused; play starts after the last return; `lowest` skips the phase; a high rank may give back a card
    just received; privacy of `received` / `given` per seat.
  - `MatchScoringTest` (parameterised: place × `n` × scoring → points).
  - `MatchLengthTest`: rounds and points targets, ties.
  - `NextLeadTest`: `scum`, `president`.
  - `PresidentForfeitTest`: one test per phase and position of the forfeiter (spec §8).
  - Invariant over whole seeded matches with 3, 5 and 8 players.

## P3 `feat: President at the table (server)` (M-L)

- `PresidentFactory` (`GAME_TYPE = "president"`, 3 to 8, `optionDescriptors()` for the P1-P2 keys with label
  keys `options.president.<key>`, `validate` = `PresidentOptions.parseStrict`), `supportsBots()` false until
  B2.
- `presentation/`: `PresidentGameActions`, `PresidentWebSocketController` (start with the type check, play,
  pass, giveBack, ready), `PresidentRestController` (`/api/president`), payloads, `PresidentDto.forViewer`,
  `PresidentEventType` / `PresidentEventData`, `PresidentTestDealController` (`@Profile("test")`).
- `config/PresidentConfig.java` (factory, broadcaster with `BotSeats.none()`, actions, lifecycle, deck source
  by profile).
- Shared tests (`add-a-game.md` §2.2): `PerGameConfigTest`, `ArchitectureRules.GAMES`, `ArchitectureTest`
  (both lists), `MalformedTokenIT` (REST and every command), `LobbyControllerTest` (`assertAddBotRefused`).
- Tests first: `PresidentFactoryTest` (type, bounds, descriptors, strict parse refuses an unknown key or
  value, defaults filled), `PresidentDtoTest` (no other hand or other pair's exchange cards in any DTO; legal
  plays only for the current seat), `PresidentGameActionsTest`, `PresidentRestAndLifecycleTest`,
  `PresidentWebSocketControllerTest`, `PresidentWebSocketControllerIT` (create, join, start, a few plays, a
  refused play answered to the sender alone, a forfeit), `PresidentFixedDealIT` and
  `PresidentFixedDealAbsentInProductionIT`.
- Docs: `context-map.md` section "President" (package, factory, endpoints, deal seam, not covered yet).

## F1 `feat: President model, session and store` (M)

No visible change. `model/president/PresidentState.ts` (types shaped on `PresidentDto`),
`model/president/selection.ts` (does a selection match a `legalPlays` pattern; which cards complete it),
`model/president/titles.ts`, `application/PresidentSession.ts` (`PRESIDENT_SESSION_CONFIG`), `state/President.store.ts`
(`usePresidentStore`, `president-store`), `composables/usePresidentBootstrap.ts`, `model/fixtures/president.ts`.
Tests first: one per command (destination and body), selection matcher table (parameterised `it.each`),
version guard, store selection reset on a new trick or a refused play.

## F2 `feat: President in the game selection, lobby and table` (L, entry PR)

After the canvas decisions 1, 2, 3 and 6. Registry entry (`supportsBots: false`), route `president-room`,
`useGameRooms`, locales (`gamePicker.games.president`, rules `president`, `presidentUi`, `options.president`),
`PresidentLobbyOptions` (presets + groups, generic fallback), `PresidentGameScreen.vue` with the table, hand,
trick, seats, play and pass, rule chips, a minimal round-over line (F4 does the full summary). Shared tests and
Cypress lists per `add-a-game.md` §3.2 and §4. Cypress `president-lobby.cy.ts` (options, presets, Start at 3
with two scripted players) and `president-game.cy.ts` (fixed deal, a full round with scripted players to
`ROUND_OVER`). `CLAUDE.md` game list updated. Never at the same time as another entry PR.

## F3 `feat: President exchange step` (M)

After canvas decision 4. `PresidentExchangePanel` (received cards, choose k, Give, Suggest = lowest cards
that break no pair, computed client-side as a convenience only), waiting state for the others with the pair
counts. Vitest per role (President, Vice, Neutral, Vice-Scum, Scum). Cypress `president-exchange.cy.ts`:
fixed deal, round 1 scripted to a known order, round 2 exchange from both sides.

## F4 `feat: President round summary, standings and end screen` (M)

After canvas decision 5. `PresidentRoundSummary` (finishing order, title changes, forbidden finishes, round and
total points, ready checks), standings, `EndGameOverlay` with several winners and the forfeit subtitle, Play
again to the lobby. Vitest; Cypress `president-end.cy.ts` (`length = rounds-3` scripted to `FINISHED`, Play
again).

## B1 `feat: President bot strategy` (M-L)

Domain only, `president/domain/bot/`: `PresidentBotView`, `PresidentBotMemory`, `PresidentBotDecision`
(sealed), `PresidentBotStrategy`, `HeuristicPresidentBot`, `PresidentBotLevel` (`easy|medium|hard`,
`DEFAULT = MEDIUM`), `PresidentBotTuning`. Tests first: view hides other hands; memory records played and
exchanged cards, clears on a new round; one parameterised row per heuristic rule of spec §10 (lead lowest
unbroken set, keep control cards, pass rather than break a pair, avoid a forbidden finish, give back lowest
singletons); level tuning table; `PresidentBotSelfPlayTest` (seeded matches of 3, 5 and 8 bots, all levels
mixed, under the Classic defaults: every match finishes, the card invariant holds, no refused command).

## B2 `feat: President bots at the table` (M)

`supportsBots() = true`, seat option `level`; `PresidentDto.PlayerDto.bot`; `PresidentBotCoordinator`
(turn, return, barrier tasks after the `ThinkingDelay`, version-keyed, fallback chain); `PresidentConfig`
wiring (`president.bots.delay-min-ms=700`, `delay-max-ms=1300`, zero under `test`; a seeded generator under
`test`). Tests first: `PresidentBotViewParityTest`, coordinator with a manual scheduler (acts after the delay,
stale task dropped, throwing strategy falls back, stops on finish, eviction, rematch, no human left),
broadcaster skips bot seats, `PresidentBotGameIT` (two humans, one hard bot, a fixed deal, a round, Play again
keeps the bot). Docs: "President bots" in `context-map.md`.

## B3 `feat: play President against bots` (S-M)

`supportsBots: true` in the registry, locales `options.president.level`, bot names through the shared
`displayName`. Vitest; Cypress `president-bot.cy.ts` (one human and two hard bots play a round to the
summary) and `shared-lobby.cy.ts` updated.

## V1 `feat: President trick variants` (L)

Keys `equalPlay`, `twoCuts`, `quadClears`, `eightClears`, `forbiddenFinish`, `mustPlay` (descriptors,
parsing, locales, presets unlocked). Domain: `BeatRule` equal and cut cases, `PlayEffects` (skip,
`MATCH_OR_PASS`, clears with reasons, quad completed by equal plays), forbidden finish places, `mustPlay` in
`pass`, `LegalPlays` with cuts and equals. Frontend: rule chips ("Skip", "9 or nothing"), trick-won reason in
the log, forbidden finish marker in the summary. Bot: prefer an equal play on the most dangerous next player,
keep a cutting 2, avoid a forbidden finish (already planned in B1). Tests first: a parameterised effect matrix
(option value × play × top → beats, skip, clear reason); scenario tests from the spec catalogue rows V2-V7 and
V11, including two players left with a skip, a cut refused under "or nothing", two forbidden finishers;
self-play with each option on. Cypress `president-trick-variants.cy.ts` (fixed deal: a skip, a cut, a
forbidden finish).

## V2 `feat: President jokers, revolution and aces high` (L)

Keys `jokers`, `revolution`, `ranking`. Domain: deck with jokers (54 cards), wild-set parsing and equality,
joker cut, `RankOrder` under revolution and aces high, counter-revolution, durations (`round`, `trick`,
`persistent` across rounds), best cards and `lowestClub` under each order. Frontend: joker cards, the
Revolution chip, hand sorted by the order in force. Tests first: parameterised rank-order table (ranking ×
revolution × rank), wild-set table, revolution timing scenarios (immediate answer by a lower quad), exchange
best cards with jokers, card invariant at 54. Cypress: a revolution flips the hand order and the chip.

## V3 `feat: President runs and suit lock` (L)

Keys `runs`, `suitLock`. Domain: run parsing (3+, no wrap, 2 at the top end, same suit or any), run beating,
equal runs, revolution on runs, suit lock (two consecutive plays with the same suits), `LegalPlays` for runs
(bounded enumeration, measured). Frontend: a run shown as a sequence, the "Suits locked" chip; the selection
matcher learns run patterns. Tests first: run-validity and run-beat tables, lock tables, `LegalPlays` size
bound on a 13-card hand. Cypress: a run and a lock in a fixed deal.

## V4 `feat: President two decks, putsch and fallen President` (L)

Keys `decks`, `putsch`, `presidentFall`; `maxPlayers()` 8 → 10. Domain: two-deck deal (104 / 108 cards),
multiset hands, sets up to 8, first lead with two 3♣; `PUTSCH` phase and its scoring; fall of the reigning
President. Frontend: lobby up to 10 seats at 375 px, the putsch prompt for the Scum, a 10-player table.
Tests first: two-deck invariant at 104/108, duplicate cards in one play, putsch success and failure (round
stops, titles kept, -1, four cards next round, no putsch then), fall scenarios. Cypress at 375 × 667 with 10
seats (9 scripted). Docs: `context-map.md` updated.

## E1 `test: President end-to-end matrix` (M)

Cypress only (and fixtures): each preset (Classic, Pagat basic, Strict, Party, Daifugō-like) through one
scripted round on a fixed deal; Play again keeps options and bots; a disconnect then a forfeit mid-trick and
mid-exchange; reload into each phase restores the DTO. Runs on the e2e stack, one at a time.

## Risks

- **Rule ambiguity**: President has no canonical rulebook; the uncertain rules ([U] in the spec) are options
  with a documented choice, and the owner may change any default without touching the model.
- **Option explosion**: about twenty keys. Mitigated by presets, grouped lobby options, and the rule that every
  combination is legal; the effect matrix tests cover every value, not every combination (self-play covers
  random combinations).
- **`LegalPlays` size** with runs, wild jokers and two decks: measured in V3/V4; patterns, not concrete card
  lists, keep the DTO small.
- **10 players on a phone** (V4): checked in the canvas first; the compact list layout (decision 1B) is the
  fallback.
- **Entry-PR collision with Mangola (#217)**: F2 is scheduled after the other entry PR, never at the same time.
- **Bot quality**: subjective; self-play tests guarantee legality and termination, not strength; a tuning pass
  can follow playtests.

## Not done

- Out-of-turn plays, negotiation-based exchanges, the variants of spec §3.4.
- Spectators, chat, turn timers, persistence.
