# Blackjack: plan

Spec: `docs/specs/2026-10-28-blackjack-design.md` (rules with their sources, the 29 decisions taken autonomously,
numbers, domain, contract, layout, bots). Written by Claude Opus 5.5 on `origin/main` at `8f7a36d1`. The owner
delegated every decision for this game: no owner question, no Claude Design canvas; the plan runs end to end
without further input, and the owner gives feedback after playing the finished game.

## Key decisions

1. **One game, two modes by seat count**: id `blackjack`, 1 to 6 seats; one seat is solo against the dealer, two or
   more is a tournament. 100 points each, `hands` option (10 default, 5, 15, 20), `maxBet` option (50 default, 25,
   100), minimum bet 1, elimination at 0, no rebuy, the most points after the last hand wins, equal stacks share
   the place (spec A2 to A8, A20 to A23).
2. **A neutral automated dealer for everyone**, players compete through their stacks (tournament blackjack), each
   hand names its best hand for information; no rotating player-banker (A4, A5).
3. **Standard house rules, kept small**: 6 decks shuffled fresh every hand, dealer stands on all 17s and peeks,
   blackjack 3:2 rounded down, double on two cards and after split, one split, no insurance and no surrender
   (A9 to A16).
4. **Fast phone flow**: sealed simultaneous bets with a one-tap "Bet N" (last bet), play in seat order from a
   rotating first seat, no pause between hands; layout decided in the spec (dealer strip, opponent grid, my seat
   and a sticky action bar) (A17 to A19, A27).
5. **Three bot levels**: random; basic strategy with a flat bet; basic strategy plus tournament bet sizing and
   last-hand risk (with a fresh shuffle each hand, betting relative to the other stacks is the only room for a
   stronger bot) (A25).

## Order and parallelism

```
               ┌──> C1 (domain aggregate) ──> C3 (presentation, config, coordinator) ──┐
               ├──> C2 (bot strategies) ──────────────────────────────────────────────┤
P0 (interfaces)┼──> F1 (model, session, store, bootstrap, motion) ────────────────────┼──> E (entry PR, Cypress,
               ├──> L  (locales en + fr) ──> F2 (table components, screen) ────────────┘    smoke list, docs)
```

- **P0** first, alone, short (under 30 minutes): shared types, nothing removed, nothing registered.
- **In parallel after P0**, each in its own worktree with its own sub-agent: **C1**, **C2**, **F1**, **L**.
- **F2** needs P0 and L (its components read the real texts); it codes against the P0 types and fixtures, not a
  running backend, so it runs in parallel with C1, C2, C3 and F1.
- **C3** needs C1 (it drives the real aggregate in its ITs); it uses the P0 strategy port, so it does not wait for
  C2: until C2 lands, its coordinator test uses a stub strategy.
- **C2**'s strategies need only P0 (views built by hand); its opt-in benchmark needs the aggregate: C2 rebases on
  `main` once C1 is merged and runs the benchmark last (if C1 is not merged when the strategies are ready, C2 opens
  its PR without the benchmark and C3 runs it).
- **E** needs C2, C3, F1, F2. It is the game's only entry PR: never at the same time as another game's entry PR or
  a picker change (`add-a-game.md` §1).

**Merge accord.** Blackjack is not in the owner's restricted list (Mangola and President backend domains only):
the orchestrator merges each chunk after review and green CI.

**Tests (every chunk):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test`; frontend
`cd frontend && npm test && npx vite build`. Strict TDD: the domain and model tests are committed red first (a
`test:` commit before the `feat:` commit, retrospective F8). **Cypress: nominal only, one spec,
`blackjack-game.cy.ts` (two cases, spec §12), written in E only** and added to `frontend/cypress/smoke-specs.json`
so the CI `e2e-smoke` job runs it on every PR; the game is also added to the lists of `game-picker.cy.ts`,
`game-home.cy.ts` and `game-screens-homogeneity.cy.ts`. Local Cypress is currently forbidden by the owner (RAM,
until further notice): E relies on the CI `e2e-smoke` job and reports its result; if local Cypress is allowed
again by then, run the new spec once on a shifted stack (issue #312 rule). A dev environment only for F2 (fixtures)
and E (manual play).

**Conventions:** one draft PR per chunk opened at its start with its real number, `gh pr ready` at the end;
branches `feat/blackjack-<chunk>`; conventional titles; backend paths relative to
`backend/src/main/java/org/kevinkib/cardgames/blackjack/` (tests in the mirror path), frontend paths relative to
`frontend/src/`; every visible text through `locales/` (English in `en.ts`, French in `fr/blackjack.ts`; the
parity test fails otherwise).

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| Chunk | Title | Cost | Needs | Parallel with | State |
|-------|-------|------|-------|---------------|-------|
| P0 | `feat: blackjack shared types (interfaces)` | S (< 30 min) | this plan merged | - | [ ] |
| C1 | `feat: blackjack rules in the domain` | L | P0 | C2, F1, L, F2 | [ ] |
| C2 | `feat: blackjack bot strategies` | M | P0 (benchmark: C1) | C1, C3, F1, L, F2 | [ ] |
| F1 | `feat: blackjack frontend model, session and store` | S-M | P0 | C1, C2, C3, L, F2 | [ ] |
| L | `feat: blackjack texts in English and French` | S | P0 | C1, C2, F1 | [ ] |
| F2 | `feat: blackjack table components and screen` | L | P0, L | C1, C2, C3, F1 | [ ] |
| C3 | `feat: blackjack presentation, config and bot coordinator` | M-L | C1 | C2, F1, F2 | [ ] |
| E | `feat: blackjack playable (entry PR)` (integration) | M | C2, C3, F1, F2 | - | [ ] |

About 8 to 10 days of work, 8 PRs. Critical path: P0, C1, C3, E (F2 after L in parallel).

## Conflicts

| File | Chunks | Handling |
|------|--------|----------|
| `domain/BlackjackOptions.java`, `HandValue.java`, `Action.java`, `Phase.java`, `ShoeSource.java`, `domain/event/*`, exceptions, `domain/bot/BlackjackBotView.java`, `BlackjackBotStrategy.java`, `BlackjackBotLevel.java` | P0, then read only | C1 and C2 call them; a signature change goes in a tiny follow-up on `main`, never in two branches |
| `domain/Blackjack.java`, `BlackjackFactory.java`, `DealerRule.java`, `Settlement.java`, `PlayerHand.java`, `SeatState.java`, `Shoe.java`, `RandomShoeSource.java`, `ScriptedShoeSource.java` | C1 | C1 owns them |
| `domain/bot/*` other than the P0 types | C2 | C2 owns them |
| `presentation/**`, `config/BlackjackConfig.java` | C3 | C3 owns them |
| `application.properties`, `application-test.properties` | C3 | shared by every game: append-only, rebase |
| Shared backend tests (`PerGameConfigTest`, `ArchitectureRules`, `ArchitectureTest`, `MalformedTokenIT`, `LobbyControllerTest`) | C3 (and any other game in flight) | append-only lines; rebase on `origin/main` before the final push; never weaken a shared test |
| `model/blackjack/BlackjackState.ts`, `model/blackjack/fixtures.ts` | P0, then read only | F1 and F2 add helpers in separate files |
| `model/games.ts` (`GameKey` union only) | P0 | opens the key (the comment of `games.ts` allows keys ahead of their game); `define(...)` stays for E |
| `locales/Messages.ts`, `locales/en.ts`, `locales/fr/index.ts`, `locales/parity.test.ts` (allowlist), `locales/en.test.ts` | L (all `blackjack*` namespaces, `options.blackjack`, `gamePicker.games.blackjack`) | every game and the translation chunks touch these files: add only the blackjack lines, rebase; F2 and E do not edit texts except a missing key, added in both languages |
| `locales/fr/blackjack.ts` | L | new file, L owns it |
| `components/blackjack/*`, `view/blackjack/BlackjackGameScreen.vue` | F2 | F2 owns them |
| `model/blackjack/*` (other than P0's), `application/BlackjackSession.ts`, `state/Blackjack.store.ts`, `composables/useBlackjackBootstrap.ts` | F1 | F1 owns them |
| `games.ts` (`define`), `games.test.ts`, `router.ts`, `router.test.ts`, `useGameRooms.ts`(+test), `GamePickerView.test.ts`, `cypress/support/stompPlayer.ts`, `cypress/smoke-specs.json`, the three shared picker Cypress specs | E | entry PR only; never in parallel with another entry PR; the smoke list is edited only by integration chunks |
| `docs/architecture/context-map.md` | C3 (section), E ("not covered" line) | different lines; rebase |
| `CLAUDE.md` (game list) | E | rebase |
| This spec | C2 (benchmark results in §10.4), E (status) | different sections |

## P0 `feat: blackjack shared types (interfaces)` (S, under 30 minutes)

Add-only, nothing registered (no factory bean, so no shared test moves), no behaviour beyond two tiny pure values.

- Backend: `blackjack/package-info.java` (the context: a seat card game against an automated dealer, `domain`
  pure, `presentation` the adapters); `domain/BlackjackOptions` (`Hands` 10/5/15/20, `MaxBet` 50/25/100, `from`
  lenient, `parseStrict` strict, `toGameOptions`, `defaults()`, constants `STARTING_STACK = 100`, `MIN_BET = 1`);
  `domain/HandValue` (record + `of(List<Card>, boolean fromSplit)`: total, soft, blackjack, bust; the frenchcards
  `Card` rank values); `Action`, `Phase`; `ShoeSource` port (`shuffledShoe(GameId, int handNumber)`); the sealed
  `BlackjackEvent` records and `BlackjackResult` of spec §6; the checked `BlackjackException` and its subclasses;
  `domain/bot/BlackjackBotView` (record: own hands with totals and soft flags and pair flag, dealer up card value,
  available actions, bet limits, every live stack, the button, hands left, solo), `BlackjackBotStrategy` (port:
  `decideBet(view)`, `decidePlay(view)`), `BlackjackBotLevel` (`easy|medium|hard`, default `medium`, `key()`).
- Frontend: `model/blackjack/BlackjackState.ts` (the DTO, event types and error codes of spec §8), `fixtures.ts`
  (a 4-seat betting phase with sealed bets, a playing phase with my split hands and `availableActions`, a dealer
  blackjack, a settled hand with a best hand, a tournament end with shared places, a solo end), `'blackjack'` added
  to the `GameKey` union in `model/games.ts` (no `define`).
- **Tests first:** `BlackjackOptionsTest` (defaults 10 / 50; strict parse refuses `hands=7`, `maxBet=30`, unknown
  keys), `HandValueTest` (every rank, soft and hard totals, `A A 9` = 21 soft, `A K` blackjack but not after a
  split, bust).

## C1 `feat: blackjack rules in the domain` (L)

- `Blackjack` aggregate (spec §3, §6.1), `BlackjackFactory` (`GAME_TYPE = "blackjack"`, 1 to 6, `supportsBots()
  = true`, descriptors `hands` and `maxBet`, seat option `level`), `DealerRule`, `Settlement`, `PlayerHand`,
  `SeatState`, `Shoe`, `RandomShoeSource` (6 French decks from `CardsService`, shuffled with a fresh seed),
  `ScriptedShoeSource` (a scripted prefix per hand, then the rest of a fixed 312-card order), `BlackjackBuilder`
  (tests).
- **Tests first:** `DealerRuleTest`, `SettlementTest` (every row of spec §3.8, 3:2 rounding: 5 → 7, 1 → 1), the 15
  worked examples of spec §6.2 as parameterised rows across `BlackjackBetTest`, `BlackjackDealTest`,
  `BlackjackPlayTest`, `BlackjackSplitDoubleTest`, `BlackjackEndTest`, `BlackjackForfeitTest`;
  `BlackjackFactoryTest` (type, bounds 1 and 6, strict options, descriptors); every accepted command bumps
  `version()`, a refused command never mutates (one property-style test over random legal and illegal commands
  with a seeded shoe: stacks plus bets are conserved against the dealer's net, no negative stack).

## C2 `feat: blackjack bot strategies` (M)

Owns `domain/bot/` except the P0 types. Works from `BlackjackBotView` built by hand in tests.

- `BasicStrategy` (the three tables of spec §10.2), `RandomBlackjackBot`, `BasicStrategyBlackjackBot`,
  `TournamentBlackjackBot` (spec §10.3), `BlackjackBotTuning` (flat bet 10, the betting thresholds), `BlackjackBots`
  (level → strategy).
- **Tests first:** `BasicStrategyTest` (every cell, with "double if allowed else hit / stand" and split only when
  `SPLIT` is available), every level returns a legal bet and a legal action on 1,000 random views,
  `TournamentBlackjackBotTest` (one hand-written view per row of the betting table and per last-hand deviation;
  solo falls back to the flat bet), determinism with a seeded generator for `easy`.
- Last: opt-in `BlackjackBotStrengthBenchmark` (`-Dblackjack.bench=true`, needs C1 on `main`): 1,000 tournaments
  of 4 seats (1 hard vs 3 medium, 1 medium vs 3 easy, 1 hard + 1 medium + 2 easy), seats rotated; results written
  into spec §10.4. If `hard` does not beat `medium`, tune `BlackjackBotTuning` and say so in the PR.
- `BlackjackBotView.forSeat(game, seat)` and its DTO parity test belong to C3 (they need the aggregate and the DTO).

## F1 `feat: blackjack frontend model, session and store` (S-M)

Codes against the P0 types and fixtures; nothing registered.

- `application/BlackjackSession.ts` (`BLACKJACK_SESSION_CONFIG`, `bet(amount)` on `/app/blackjack/bet`, `hit()`,
  `stand()`, `doubleDown()`, `split()` on `/app/blackjack/{hit,stand,double,split}`), `state/Blackjack.store.ts`
  (`blackjack-store`, `useSeatRoom`), `composables/useBlackjackBootstrap.ts`.
- Model helpers: `betting.ts` (clamp to `betLimits`, chip increments `+1 +5 +10 +25`, `Max`, `Clear`, the "Bet N"
  default = `lastBet` clamped, else 10 clamped), `standings.ts` (order by stack, shared places, eliminated and
  forfeited below), `seatOrder.ts` (opponents in seat order after me, from `model/table/opponentOrder.ts`),
  `eventLog.ts` (one live-region line per event as a key and parameters), `tempo.ts`, `motion.ts` (anchors
  `BlackjackAnchor`: shoe, dealer, `seat-<n>-hand-<i>`; pure plans for `DEAL` in the spec §3.4 order, `HIT`,
  `DOUBLE`, `SPLIT`, `DEALER_PLAY` with the reveal first).
- **Tests first:** session (destinations, payloads), store, bootstrap, `betting.test.ts` (limits 1 to 37 with a
  stack of 37, `Max` = `min(maxBet, stack)`, default from `lastBet`), `standings.test.ts` (shared places),
  `motion.test.ts` over the P0 fixtures.

## L `feat: blackjack texts in English and French` (S)

- `locales/Messages.ts` types and `locales/en.ts` texts: `gamePicker.games.blackjack` (name "Blackjack", a one-line
  pitch: "Beat the dealer, alone or in a 100-point tournament"), `blackjack: RulesMessages` (the seven sections of
  spec §11), `blackjackUi` (every label of the layout of spec §9.3: header "Hand {n} / {total}", dealer, plate
  fields, statuses Blackjack / Bust / Double / Split / Push / Soft, the bet panel and its chip buttons, "Bet {n}",
  the action bar Hit / Stand / Double / Split, waiting lines, the last hand summary, the best hand badge, the solo
  hint under Start, the lobby fixed line, the end screen texts for the four reasons and the solo net, the live
  region lines, the error codes of spec §8.1, the bot label), `options.blackjack` (`hands`, `maxBet`, `level` Easy /
  Medium / Hard).
- `locales/fr/blackjack.ts` (`blackjack`, `blackjackUi`, `blackjackOptions`) wired in `locales/fr/index.ts`; French
  in "vous" (owner's translation decisions), French typography (narrow no-break space before `:` `;` `!` `?`), short
  action verbs: Carte, Rester, Doubler, Séparer; "Miser {n}", "Croupier", "Égalité", "Éliminé"; the allowlist of
  `parity.test.ts` gets the leaves equal in both languages (`gamePicker.games.blackjack.name`, the bot label, the
  digit-only option values).
- `en.test.ts`: non-empty checks for `blackjack` and `blackjackUi`; `length.test.ts` report checked (short French
  labels).
- **Tests first:** the parity test and `en.test.ts` additions red, then the texts.

## F2 `feat: blackjack table components and screen` (L)

Starts once L is merged (it can start earlier on fixtures and add the text keys as L defines them). Codes
against the P0 types and fixtures, with F1's helpers once merged (until then, the component tests use fixtures
directly). Vitest only; the screen is not routed yet (E does it).

- `components/blackjack/`: `BlackjackTable`, `BlackjackDealer` (hole card face down until shown), `BlackjackSeatPlate`
  (name and level, stack, standing, "Bet placed" or the amount, hands with totals and tags, turn highlight,
  eliminated with place, disconnect badge), `BlackjackMySeat` (one or two hands, the active one outlined, soft
  total shown as "7 / 17", nets after the settlement), `BlackjackHand`, `BlackjackBetPanel` (chip buttons, `Max`,
  `Clear`, "Bet N" prefilled, disabled outside the limits), `BlackjackActionBar` (only the `availableActions`,
  48 px targets), `BlackjackHandSummary` (dealer total, my net, best hand), `BlackjackStandings` (end body; solo net),
  `BlackjackLobbyOptions` (`hands`, `maxBet`, the fixed line, the solo hint).
- `view/blackjack/BlackjackGameScreen.vue`: the layout of spec §9.3, the shared chrome, the animation barrel
  (`defineAnchors`, `useTableMotion`, `CardMotionLayer`, the speed button), the live region.
- **Tests first:** each component over the fixtures (sealed bets show "Bet placed", never an amount; the hole card
  stays face down until `holeHidden` is false; only the available actions are buttons; "Bet N" defaults to the last
  bet and one tap sends it; split hands side by side with the active one marked; eliminated and shared places; the
  solo end shows the net), the screen over fixtures. A 6-seat check at 375 px and 1280 px reported in the PR with
  the `ui-change` skill (no layout choice is left open: the spec decides it).

## C3 `feat: blackjack presentation, config and bot coordinator` (M-L)

- `BlackjackRestController`, `BlackjackWebSocketController` (game-type check before start; `bet`, `hit`, `stand`,
  `double`, `split` through `SeatCommands`; the error codes of spec §8.1), `BlackjackGameActions`,
  `api/BlackjackBetPayload`, `api/BlackjackFixedShoePayload`, `dto/BlackjackDto.forViewer`,
  `dto/event/BlackjackEventType` and `BlackjackEventData`, `BlackjackTestShoeController` (`@Profile("test")`,
  `PUT`/`DELETE /api/test/blackjack/{gameId}/deal`), `BlackjackBotView.forSeat`,
  `presentation/bot/BlackjackBotCoordinator`, `config/BlackjackConfig.java`, properties `blackjack.bots.*` (700 /
  1300, 0 under `test`).
- Shared backend tests per `add-a-game.md` §2.2; a "Blackjack" section in `docs/architecture/context-map.md`.
- **Tests first:** `BlackjackDtoTest` (hole card hidden until shown; others' bets hidden during `BETTING`;
  `availableActions`, `betLimits`, `lastBet` for the viewer only), bot view parity with the DTO,
  `BlackjackGameActionsTest`, `BlackjackRestAndLifecycleTest`, `BlackjackWebSocketControllerTest`,
  `BlackjackWebSocketControllerIT` (STOMP: create, join, start, bets, a hit, an out-of-turn action refused to the
  sender alone, a settlement, the end), a **1-seat IT** (a room with only the host starts and plays a solo hand:
  `minPlayers = 1` is new for the session core), `BlackjackFixedShoeIT` and
  `BlackjackFixedShoeAbsentInProductionIT`, the coordinator with a scheduler stub (bets for every bot that has
  not bet, the bot on turn acts, a stale version is dropped, a failing strategy falls back to a bet of 1 and
  `STAND`), `LobbyControllerTest` "add bot" accepted.

## E `feat: blackjack playable (entry PR)` (integration, M)

- Merge check (C2, C3, F1, F2 on `main`, no other entry PR open), rebase.
- Shared frontend files of `add-a-game.md` §3.2: `define('blackjack', { supportsBots: true, picker: { icon:
  'pi pi-wallet', severity: 'success' } })`, `games.test.ts`, route `blackjack-room` in `router.ts` and its test,
  `useGameRooms` and its test, `GamePickerView.test.ts` counts, `cypress/support/stompPlayer.ts`
  (`ScriptedGameType` + the `bet`, `hit`, `stand` commands the spec needs if the helper has no generic send).
- **Cypress, written red first:** `cypress/specs/blackjack-game.cy.ts`, the **only** blackjack spec, the two nominal
  cases of spec §12 (tournament to an elimination and the end screen; solo to "broke" on a dealer blackjack), over
  the test seam; added to `frontend/cypress/smoke-specs.json`; the game added to `game-picker.cy.ts`,
  `game-home.cy.ts`, `game-screens-homogeneity.cy.ts`. Run by the CI `e2e-smoke` job (local Cypress forbidden
  for now, see "Tests" above); report its result and time.
- Docs: project `CLAUDE.md` game list (intro line and contexts), `context-map.md` "not covered" line, this plan's
  status, spec status line; a comment on roadmap issue #1.
- All suites, a review of the whole feature (`code-review` skill), manual play in a dev environment (solo, then two
  tabs plus a bot of each level) before `gh pr ready`.

## Test plan summary

| Level | Where | What |
|-------|-------|------|
| Domain (strict TDD) | P0, C1, C2 | options, hand values, dealer rule, settlement and 3:2 rounding, the 15 worked examples (bets, deal order, peek, split, double, end and ranking, solo, forfeit, button), conservation property; basic strategy cells, bots legal on random views, tournament betting rows |
| Benchmark (opt-in) | C2 (or C3) | bot strength over 1,000 tournaments, recorded in spec §10.4 |
| Presentation | C3 | DTO privacy (hole card, sealed bets), bot view parity, actions, REST, WebSocket unit and IT, the 1-seat IT, seam present / absent, coordinator |
| Frontend unit | F1, L, F2, E | session, store, bootstrap, betting and standings helpers, motion plans, locales parity, components, screen, registry and picker counts |
| E2E (nominal only) | E | `blackjack-game.cy.ts` in the smoke list: a tournament to its end screen, a solo game to "broke"; shared picker specs |
