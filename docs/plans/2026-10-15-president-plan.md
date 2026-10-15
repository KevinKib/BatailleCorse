# President: plan

Spec: `docs/specs/2026-10-15-president-design.md` (rules, variant catalogue with sources and uncertainty
flags, options, domain model, DTO, bots, UI decisions). Written by Claude Opus 5.5 on `origin/main` at
`28c5832`; amended on 2026-10-15 with the owner's decisions (spec section 13) and re-sliced (V1 to V4).
Checklist followed: `docs/architecture/add-a-game.md`; names: `docs/architecture/game-conventions.md`
(`president` everywhere: id, package, `President` prefix, `president` camel form).

**Goal:** a fifth game, President (Le Président / Trou du cul), playable with the owner's rules and every
offered variant, against humans and bots, in a series of small PRs.

## Key decisions (from the spec, section 13; decided by the owner on 2026-10-15)

1. **Identity and titles**: id `president`; English UI titles President / Vice-President / Neutral /
   Vice-Scum / Scum; French names (Trou du cul...) only in the rules text.
2. **Players and deck**: 3 to 8 players, one 52-card deck, no jokers; no 2-player mode, no two-deck mode.
3. **Classic defaults (the owner's rules)**: re-entry after a pass; equal play on, binding the next player to
   match or pass, repeating along the chain; the four cards of a rank played in succession close the trick,
   and their last holder may complete them out of turn (optional, window open from the second play of the
   chain until the next play; the first pass ends the binding); a quad played at once closes the trick
   (`quadClears = on`); forbidden finish on a 2; exchange 2/1 (best cards forced, free return); the king of hearts holder leads round 1 with
   any play; the Scum leads rounds 2+; points by place; the first player out closes the trick, later ones
   do not (a forbidden finisher out first closes nothing: only the player taking 1st place does). The lobby offers one preset, Classic (owner, 2026-10-15; the other presets are out of scope, spec 3.5), plus a "Customise" path; a preset is a lobby convenience, client-side only, unknown to the
   server.
4. **Exchange flow**: the server takes the low ranks' best cards automatically; the high ranks choose their
   returns freely, all at once, no timeout (the disconnect forfeit covers absence).
5. **Order**: base game end to end first (P1-P3, F1-F4), then bots on the base rules (B1-B3, candidates from
   the domain's `LegalPlays` so later variants cannot break them), then the variant groups V1-V4 (each full
   stack), then the e2e matrix E1.

Other decisions (spec §13.1, items 6-17): revolution as specified (no jokers), a ready barrier between
rounds, forfeit down to two players, every option combination accepted, `afterPass = out` reading, variants
dropped for now (spec §3.5).

Answers of 2026-10-15 to the former open questions (spec §13.1, items 18 to 23): **O1** the first pass ends
the equal-play binding; **O2** default match length `rounds-5` (`endless` stays an option); **O3** a
forbidden finisher going out first does not close the trick (only the player taking 1st place does); **O4** a
single lead does not open the completion window (an equal play is needed first); **O5** a quad played at once
closes the trick by default (`quadClears` is `on` by default). The owner also accepted the seven P2 scoring
and exchange rules (#223 merged).

Still open (spec §13.2), blocks nothing: **N1** whether the Neutrals who exchange in `scaled` mode (6 to 8
players) get distinct names (today all "Neutral").

## Rules for every PR

- Own worktree and branch from `origin/main` (`feat/president-<subject>`), PR to `main`, a draft PR with its
  real number opened at launch (empty commit), never merged without the owner.
- **Strict TDD**: a `test: …` commit with the failing tests before the `feat: …` commit that makes them pass
  (`add-a-game.md` §6). Rules tables are **parameterised tests** (`@ParameterizedTest` with `@MethodSource` or
  `@CsvSource`): one row per line of the spec's tables (ranking, titles by player count, exchange amounts,
  beat matrix, effects, scoring, the worked examples of spec §3.1.2).
- Domain tests: JUnit 5 + Hamcrest, no Mockito; a `PresidentBuilder` (explicit hands, trick, phase, titles,
  options) and `PresidentFixtures` (`cards("3C 3H 2S")`).
- Every PR keeps an invariant test: the cards of hands + trick + discarded tricks are always the 52 cards of
  the deck, after every command of a scripted or random game.
- Suites before the PR: `cd backend && mvn -s ../deploy/docker/settings.xml test` (unit, `*IT`, ArchUnit);
  `cd frontend && npm test && npx vite build`. Cypress for the PRs with a visible change, on the e2e stack or
  a shifted stack (`--config baseUrl=...`), waiting on DOM states or frames, never `cy.wait(ms)`.
- Every visible text through `frontend/src/locales/` (`president`, `presidentUi`, `options.president`).
- A dev environment only for the PRs with visible play (F2-F4, B3, V1-V4), never for P1-P3, F1, B1-B2, E1.
- UI PRs follow the `ui-change` skill (inventory before/after, 375 and 1280 px, light and dark). The screens of
  F2-F4 follow the owner's choices of 2026-10-15 on the Claude Design canvas (spec §11, "UI decisions taken";
  https://claude.ai/artifact/45XcdLmxpAc2CCS5CBmTnY).
- Tracking: open an issue "President" at P1 launch; comment on it and on roadmap #1 at each merge; no status
  PR. Update this Status table in the last PR.

**Cost scale** (as the Kobo plans): S about half a day, M about a day, L about two days.

## Status

| PR | Title | Size | Needs | Tests (strategy) | Risks | State |
|----|-------|------|-------|------------------|-------|-------|
| D0 | `docs: President game spec and plan` | S | - | - | owner reviews the variant catalogue | #216 |
| D1 | `docs: President owner decisions` | S | D0 | - | - | this PR |
| P1 | `feat: President round rules (domain)` | L | D0 | ranking, play parsing, beat matrix, trick end, going out, titles: parameterised tables; scripted rounds | trick-end counting with re-entry and players out | done (#219) |
| P2 | `feat: President match, exchange and scoring (domain)` | M-L | P1 | exchange amounts by `n` and option, best-card selection, returns, scoring tables, match end, forfeit in each phase | forfeit during the exchange | done (#223); the first-out-closes-trick rule was not in it, P3 carries it |
| P3 | `feat: President at the table (server)` | M-L | P2 | factory and options (strict parse), option pruning, `kingOfHearts`, `endless`, the first player out closes the trick (parameterised `TrickAfterGoingOutTest`), DTO privacy, actions, controllers, STOMP IT, deal seam, shared tests | shared test counts collide with Mangola (#217) | done (#229) |
| F1 | `feat: President model, session and store` | M | P3 | Vitest: DTO fixtures, one test per command, selection-to-pattern matcher, version guard | none visible | done (#234) |
| F2 | `feat: President in the game selection, lobby and table` (entry PR) | L | F1, decisions 1-3, 6 (spec §11) | Vitest per component; Cypress lobby + a scripted round | the only entry PR at a time (Mangola) | done (#236) |
| F3 | `feat: President exchange step` | M | F2, decision 4 (spec §11) | Vitest; Cypress with a fixed deal into round 2 | waiting state for the non-exchanging seats | - |
| F4 | `feat: President round summary, standings and end screen` | M | F2, decision 5 (spec §11) | Vitest; Cypress to `FINISHED` and Play again; standings of an `endless` match | several winners, forfeit subtitle | - |
| B1 | `feat: President bot strategy` | M-L | P2 | view parity (in B2), memory, heuristic decision table (parameterised), self-play | strategy quality is subjective | - |
| B2 | `feat: President bots at the table` | M | B1, P3 | coordinator with a manual scheduler, broadcaster skips bots, bot game IT | stale tasks | - |
| B3 | `feat: play President against bots` | S-M | B2, F2 | Vitest; Cypress lobby with bots, a round against a hard bot | shared lobby specs | - |
| V1 | `feat: President equal play, the four and completion out of turn` | L | P3, B2 (F2 for the UI part) | worked examples of spec §3.1.2 as parameterised rows; ordering of a completion against the next play and against passes; DTO `completion` lifetime; bot completion; Cypress "Complete the four" | the first out-of-turn command of the game | - |
| V2 | `feat: President cuts, quad, forbidden finish and must play` | M | V1 | beat and effect matrix per option value, parameterised; Cypress per visible indicator | cuts against the equal-play binding | - |
| V3 | `feat: President revolution and aces high` | M | V2 | rank order under each option and revolution state; exchange best cards under each order | revolution timing | - |
| V4 | `feat: President runs and suit lock` | M-L | V3 | run parsing and beating tables; lock tables; `LegalPlays` enumeration size | `LegalPlays` growth with runs | - |
| E1 | `test: President end-to-end matrix` | M | V4, B3 | Cypress: the Classic preset through a scripted round, a completion out of turn, rematch, disconnect and forfeit | e2e duration | - |

Total about 18 to 21 days of agent work, of which D0 and P1 (about 2.5) are done; it was 20 to 24 before the
2026-10-15 re-slice (no jokers, no two decks, putsch or fall; the equal-play rules grow V1). P1 and P2 may be
one PR if the diff stays under about 800 lines. F3 and F4 can run in parallel after F2; B1 can start right
after P2 (pure domain), in parallel with P3. The variant PRs are in series (they all extend `BeatRule`,
`PlayEffects` and `LegalPlays`). V1 ships a default rule of the owner: until it merges, the table plays
without equal play; V2 ships `quadClears = on` and the forbidden finish the same way; V1 may start as soon as P3, F2 and B2 are merged, in parallel with B3.

## Conflicts

| File | PRs | Handling |
|------|-----|----------|
| `config/PerGameConfigTest.java`, `architecture/ArchitectureRules.java`, `ArchitectureTest.java`, `MalformedTokenIT.java`, `LobbyControllerTest.java` | P3, Mangola backend (#217's plan) | counts and lists: rebase the second, never weaken a shared test |
| `model/games.ts`, `router.ts`, `useGameRooms.ts`, `locales/*`, `GamePickerView.test.ts`, picker Cypress specs | F2, Mangola entry PR | never two entry PRs at once (`add-a-game.md` §1): F2 waits for the other entry PR to merge, or the reverse |
| `president/domain/rules/*` | P2 (#223), P3 | P3 prunes the enums after P2 merges; P2 does not touch them beyond its own keys |
| `president/domain/play/*`, `trick/*`, `PresidentDto.java`, `components/president/*`, `presentation/bot/*` | V1-V4, B1-B3 | in series; bot candidates come from `LegalPlays` and `completion(seat)`, so a variant touches the heuristic only to add a preference |
| `docs/architecture/context-map.md` | P3, B2, V1 | one section, extended |

## D0 `docs: President game spec and plan` (#216)

Spec, plan, nothing else.

## D1 `docs: President owner decisions` (this PR)

The owner's decisions of 2026-10-15 in the spec and this plan; no code.

## P1 `feat: President round rules (domain)` (L, done in #219)

Package `president/` with `package-info.java` and `domain/` only; no wiring.

- `rules/PresidentOptions` with the P1 keys (`afterPass`, `firstLead`) and the enums; every later key exists
  in the record as its "off" value, so V-PRs only add parsing.
- `card/RankOrder` (normal order now; the `ranking` and revolution hooks are parameters that V3 fills),
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

What P1 left that later PRs adjust (owner's decisions of 2026-10-15):

- Every finisher lets the trick continue; the first player out must close it (P2).
- `FirstLead` has no `KING_OF_HEARTS` and defaults to `LOWEST_CLUB` (P3).
- `PresidentOptions` and its enums still carry the dropped or excluded values: `Jokers`, `Decks`,
  `eightClears`, `putsch`, `presidentFall`, `EqualPlay.SKIP` / `OR_NOTHING` (the owner's rule is one value,
  `ON`), `Revolution.TRICK`, `Runs.ANY_SUIT`; `DeckSpec(decks, jokers)` and the joker and multi-deck branches
  of `RandomDeckSource` (P3).

## P2 `feat: President match, exchange and scoring (domain)` (M-L, done in #223)

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
- **The first player out closes the trick** (owner, 2026-10-15; spec §3.1 and §6.3): when the play that
  takes 1st place is made, the trick ends at once (`TrickWon` reason `FIRST_OUT`) and the next seat with
  cards leads; later finishers keep P1's behaviour. Forbidden finishers are V2's, so in P2 the first player
  out always takes 1st place. #223 merged without it, so P3 carries it.
- Tests first:
  - `ExchangePlanTest` (parameterised: `n` from 3 to 8 × `exchange` → pairs and amounts).
  - `BestCardsTest` (parameterised: hands → the k best, ties of rank irrelevant).
  - `ExchangeFlowTest`: best cards moved at the deal; returns in any order; wrong count; a seat with no return
    refused; play starts after the last return; `lowest` skips the phase; a high rank may give back a card
    just received; privacy of `received` / `given` per seat.
  - `MatchScoringTest` (parameterised: place × `n` × scoring → points).
  - `MatchLengthTest`: rounds and points targets, ties.
  - `NextLeadTest`: `scum`, `president`.
  - `TrickAfterGoingOutTest` (parameterised: who goes out (first or later) × whether the trick top is theirs
    × seats left → trick closed or not, next leader).
  - `PresidentForfeitTest`: one test per phase and position of the forfeiter (spec §8).
  - Invariant over whole seeded matches with 3, 5 and 8 players.

## P3 `feat: President at the table (server)` (M-L)

- Domain adjustments first (owner, 2026-10-15): prune `PresidentOptions` and the enums to the spec's sixteen
  keys (remove `Jokers`, `Decks`, `eightClears`, `putsch`, `presidentFall`; `EqualPlay` becomes `ON|OFF`;
  `Revolution` `OFF|ROUND|PERSISTENT`; `Runs` `OFF|SAME_SUIT`); remove `DeckSpec` (or reduce it to the
  standard deck) and the joker and multi-deck branches of `RandomDeckSource`; add `FirstLead.KING_OF_HEARTS`
  as the default (the holder leads with any play); add `MatchLength.ENDLESS` (never `FINISHED` by length;
  only one player left ends it, reason `FORFEIT`).
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
- Tests first: `FirstLeadTest` gains `kingOfHearts` (the default) and a lead without the king; `MatchLengthTest`
  gains `endless` (twenty rounds, never finished; a forfeit down to one player finishes);
  `PresidentFactoryTest` (type, bounds, descriptors, strict parse refuses an unknown key or value, including
  the pruned ones such as `jokers`, defaults filled), `PresidentDtoTest` (no other hand or other pair's
  exchange cards in any DTO; legal plays only for the current seat), `PresidentGameActionsTest`,
  `PresidentRestAndLifecycleTest`, `PresidentWebSocketControllerTest`, `PresidentWebSocketControllerIT`
  (create, join, start, a few plays, a refused play answered to the sender alone, a forfeit),
  `PresidentFixedDealIT` and `PresidentFixedDealAbsentInProductionIT`.
- Docs: `context-map.md` section "President" (package, factory, endpoints, deal seam, not covered yet).

## F1 `feat: President model, session and store` (M)

No visible change. `model/president/PresidentState.ts` (types shaped on `PresidentDto`),
`model/president/selection.ts` (does a selection match a `legalPlays` pattern; which cards complete it),
`model/president/titles.ts`, `application/PresidentSession.ts` (`PRESIDENT_SESSION_CONFIG`), `state/President.store.ts`
(`usePresidentStore`, `president-store`), `composables/usePresidentBootstrap.ts`, `model/fixtures/president.ts`.
Tests first: one per command (destination and body), selection matcher table (parameterised `it.each`),
version guard, store selection reset on a new trick or a refused play.

## F2 `feat: President in the game selection, lobby and table` (L, entry PR)

After the decisions 1, 2, 3 and 6 (spec §11): ring of opponents, tap-to-select with a hint line, the lobby options through
the existing pattern (constraint in Risks), top play plus rule chips (to re-evaluate after trying it). Registry entry (`supportsBots: false`), route `president-room`,
`useGameRooms`, locales (`gamePicker.games.president`, rules `president`, `presidentUi`, `options.president`),
`PresidentLobbyOptions` (the Classic preset + groups, generic fallback), `PresidentGameScreen.vue` with the table, hand,
trick, seats, play and pass, rule chips, a minimal round-over line (F4 does the full summary). Shared tests and
Cypress lists per `add-a-game.md` §3.2 and §4. Cypress `president-lobby.cy.ts` (options, the Classic preset and Customise, Start at 3
with two scripted players) and `president-game.cy.ts` (fixed deal, a full round with scripted players to
`ROUND_OVER`). `CLAUDE.md` game list updated. Never at the same time as another entry PR.

## F3 `feat: President exchange step` (M)

After decision 4 (inline exchange, received card tagged, Suggest link, "Give X to Y" button). `PresidentExchangePanel` (received cards, choose k, Give, Suggest = lowest cards
that break no pair, computed client-side as a convenience only), waiting state for the others with the pair
counts. Vitest per role (President, Vice, Neutral, Vice-Scum, Scum). Cypress `president-exchange.cy.ts`:
fixed deal, round 1 scripted to a known order, round 2 exchange from both sides.

## F4 `feat: President round summary, standings and end screen` (M)

After decision 5 (end-of-round sheet; keep the Scores button idea). `PresidentRoundSummary` (finishing order, title changes, forbidden finishes, round and
total points, ready checks), standings (also the running scoreboard of an `endless` match), `EndGameOverlay`
with several winners and the forfeit subtitle, Play again to the lobby. Vitest; Cypress `president-end.cy.ts`
(`length = rounds-3` scripted to `FINISHED`, Play again).

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

## V1 `feat: President equal play, the four and completion out of turn` (L)

The owner's equal-play rules (spec §3.1.1), the default of the Classic preset. O1 (the first pass ends the
binding) and O4 (a single lead does not open the window) are decided.

- Key `equalPlay` (`on|off`, default `on`): descriptor, parsing, locales.
- Domain: `BeatRule` equal case; `Trick` chain (rank, cards in succession) and `MATCH_OR_PASS` binding that
  repeats along the chain (the first pass ends it, O1: the next seat gets the `BEAT` constraint); the four completed in succession closes the trick (reason `QUAD`),
  the completing player leads or, if out, the next seat with cards; `Completion` (missing cards, holder) and
  `completion(seat)`; `play` accepted out of turn only as a completion, the window open from the second play
  of the chain (a lone lead never opens it, O4) until the next play or the trick's end, passes leaving it open;
  `CompletionClosedException`; `availableActions` `COMPLETE`; `LegalPlays` under the binding.
- Presentation: `PresidentDto` `chain` (public) and `completion` (holder only), event type `COMPLETION`; the
  `/president/play` destination serves both; a completion refused as too late is answered to the sender alone.
- Bots: candidates under the binding, the completion decision (take it, `hard` may keep it, spec §10), the
  coordinator schedules a completion task for a bot that is not the current seat and drops it when refused.
- Frontend: the "9 or nothing" and chain chips; the "Complete the four" button for the holder while
  `completion` is present, in or out of turn.
- Tests first: the worked examples of spec §3.1.2 (1 to 8; example 9 belongs to V2) as parameterised rows
  (hands, command sequence, expected trick state after each command), among them example 6 (first pass ends
  the binding, D beats the 9s), 7 (D free, equal 9 rebinds A) and 8 (a lone lead: out-of-turn triple refused
  with `NotPlayersTurnException`), plus: an out-of-turn play that is not a completion refused without
  mutation; a completion after a play refused (`CompletionClosedException`), after one or several passes
  accepted; the completion by a passer under `afterPass = out`; a completion that empties the hand (and, when
  first out, `FIRST_OUT` and `QUAD` agree on the next leader); DTO: `completion` only in the holder's DTO,
  present across passes, gone after the next play or the trick's end; a two-thread test (current player's
  play and the completion submitted together, the aggregate serialises them, exactly one outcome, the card
  invariant holds); bot coordinator completion task; self-play with `equalPlay` on and off. Cypress
  `president-equal-play.cy.ts` (fixed deal: a chain closed in turn, a completion out of turn by a seat who
  passed, a completion refused after a play).
- Docs: `context-map.md` notes the first out-of-turn command.

## V2 `feat: President cuts, quad, forbidden finish and must play` (M)

Keys `twoCuts`, `quadClears` (a quad played at once closes the trick; `on` by default, O5), `forbiddenFinish` (default `lastPlace`), `mustPlay`.
Domain: `BeatRule` cut cases, `PlayEffects` clears with reasons, forbidden finish places (a forbidden
finisher out first does not close the trick, O3 decided; only the player taking 1st place does, so the
`FIRST_OUT` rule of P2/P3 keys on 1st place; worked example 9 of spec §3.1.2 as a test row), `mustPlay` in `pass` (a completion is never
compulsory), `LegalPlays` with cuts. Frontend: trick-won reason in the log, forbidden finish marker in the
summary. Bot: keep a cutting 2, avoid a forbidden finish (already planned in B1). Tests first: a
parameterised effect matrix (option value × play × top → beats, clear reason); scenario tests from the spec
catalogue rows V4, V5, V7 and V11, including a cut refused under the binding and two forbidden finishers;
self-play with each option on. Cypress `president-trick-variants.cy.ts` (fixed deal: a cut, a forbidden
finish).

## V3 `feat: President revolution and aces high` (M)

Keys `revolution` (`off|round|persistent`), `ranking`. Domain: `RankOrder` under revolution and aces high,
counter-revolution, durations (`round`, `persistent` across rounds), immediate effect, the cutting card and
the forbidden finish following the top rank in force, best cards and `lowestClub` under each order; a four
completed in succession is no revolution. Frontend: the Revolution chip, hand sorted by the order in force.
Tests first: parameterised rank-order table (ranking × revolution × rank), revolution timing scenarios
(immediate answer by a lower quad), exchange best cards under a persistent revolution. Cypress: a revolution
flips the hand order and the chip.

## V4 `feat: President runs and suit lock` (M-L)

Keys `runs` (`off|sameSuit`), `suitLock`. Domain: run parsing (3+, one suit, no wrap, 2 at the top end), run
beating, equal runs and the binding on runs, revolution on runs, suit lock (two consecutive plays with the
same suits), `LegalPlays` for runs (bounded enumeration, measured). Frontend: a run shown as a sequence, the
"Suits locked" chip; the selection matcher learns run patterns. Tests first: run-validity and run-beat
tables, lock tables, `LegalPlays` size bound on a 13-card hand. Cypress: a run and a lock in a fixed deal.

## E1 `test: President end-to-end matrix` (M)

Cypress only (and fixtures): the Classic preset through one
scripted round on a fixed deal; a completion out of turn on the Classic preset; Play again keeps options and
bots; a disconnect then a forfeit mid-trick and mid-exchange; reload into each phase restores the DTO. Runs
on the e2e stack, one at a time.

## Risks

- **Rule ambiguity**: President has no canonical rulebook; the owner's rules are now the defaults, the other
  uncertain rules ([U] in the spec) are options with a documented choice; O1 to O5 are answered (2026-10-15)
  and only the small question N1 (Neutral names in `scaled` mode) is open. Any default can change without
  touching the model.
- **Out-of-turn completion** (V1): the first command of the game accepted from a seat that is not the current
  one. Mitigated by the one-deck rule (a single possible holder, no race between players), the aggregate's
  `synchronized` commands and a completion condition re-checked at processing time (a play closes the window,
  passes do not), a two-thread test, and the DTO flag only in the holder's view.
- **First-finisher rule** (merged P2 #223 did not carry it): P3 adds it, keyed on the player taking 1st place.
- **Option explosion**: sixteen keys. Mitigated by the Classic preset, grouped lobby options, and the rule that every
  combination is legal; the effect matrix tests cover every value, not every combination (self-play covers
  random combinations).
- **`LegalPlays` size** with runs: measured in V4; patterns, not concrete card lists, keep the DTO small.
- **Lobby options constraint (F2, owner 2026-10-15)**: customisation goes through the lobby with the existing
  pattern (host options in the shared `GameLobby` / game options mechanism, as the other games do). F2 must not
  introduce a new pattern; the Classic card + Customise disclosure is kept only if it fits that pattern,
  otherwise the existing pattern is followed exactly (and the Classic-preset wording of this plan and of the
  Cypress specs is adapted).
- **Table for 8 players (F2)**: the ring of opponents was chosen; check what 8 players need at 375 px and report
  it in the entry PR.
- **Trick and rule chips (F2)**: decision 6 (option C) is provisional; re-evaluate after trying it.
- **Entry-PR collision with Mangola (#217)**: F2 is scheduled after the other entry PR, never at the same time.
- **Bot quality**: subjective; self-play tests guarantee legality and termination, not strength; a tuning pass
  can follow playtests.

## Not done

- Out-of-turn plays other than the completion of the four, negotiation-based exchanges, the variants of spec
  §3.4 (jokers among them).
- Dropped for now by the owner (spec §3.5): the skip form of equal play, eight stops, `revolution = trick`,
  `runs = anySuit`, two decks and 9-10 players, putsch, the fall of the President.
- Spectators, chat, turn timers, persistence.
