# Ninety-Nine (99): plan

Spec: `docs/specs/2026-10-28-ninety-nine-design.md` (rules, sources and their differences, numeric table, domain,
contract, UI requirements, owner questions). Written by Claude Opus 5.5 on `origin/main` at `bb340ddd`.

## Key decisions

1. **Id `ninety-nine`** (`ninetynine`, `NinetyNine`, `ninetyNine`), display name "99": a digit cannot start a Java
   package or a TypeScript identifier.
2. **The owner's card table wins over the secondary source** (zpag): 4 reverses, 9 is +0, J and Q +10, Ace 1 or 11,
   K sets 99. Gaps filled with a recommendation each, all open to the owner: the 5 counts +5, one 52-card deck with
   the pile reshuffled, the 10 floors at 0 (spec §3, §13).
3. **A stuck hand busts automatically, and a safe card must be played when one exists**: going over 99 only ever
   happens to a seat holding no safe card; the bust reveals the stuck hand. No dead "go over" click (Q4).
4. **No barrier between rounds**: a 2.5 s server pause then the next deal (12 players never wait for a click);
   quick mode ranks by lives with shared places; a forfeit never triggers the quick end (Q5, Q5b, Q9).
5. **Three bot levels** (random safe card / greedy "push the total up, keep the escape cards" / determinised Monte
   Carlo over the unseen cards, 200 samples, 20 ms cap). Every UI layout (12-seat table, centre counter, Ace
   choice, lives, bust banner, lobby options, end screen) goes through a **Claude Design canvas** (chunk D) before
   the screen chunk F2.

Nothing numeric is implemented before the owner agrees to the "current / proposed" table of spec §5.

## Order and parallelism

```
P0 (interfaces, add-only) ──┬──> C1 (domain aggregate) ──> C3 (presentation, config, coordinator) ──┐
                            ├──> C2 (bot strategies) ────────────────────────────────────────────────┤
                            ├──> F1 (model, session, store, bootstrap) ─────────────────────────────┤──> I (entry PR,
D (canvas, owner chooses) ──┴──────────────────> F2 (components, screen) ───────────────────────────┘    integration)
```

- **P0** first, short (under 30 minutes): the shared types, nothing removed, nothing registered.
- **In parallel after P0**, each in its own worktree with its own sub-agent: **C1**, **C2**, **F1**; **D** can start
  as soon as this plan is approved (it waits only for the owner). **F2** starts once D is chosen and P0 is merged
  (it codes against the P0 contract and fixtures, not a running backend).
- **C3** needs C1 (it drives the real aggregate in its ITs); it uses the P0 strategy port, so it does not wait for
  C2: until C2 lands, its coordinator test uses a stub strategy.
- **I** needs C2, C3, F1, F2. It is the game's only entry PR: never at the same time as another game's entry PR or
  a picker change (`add-a-game.md` §1).

**Merge accord.** 99 is not in the owner's restricted list (Mangola and President backend domains only): the
orchestrator merges each chunk after review and green CI. D needs the owner's choice.

**Tests (every chunk):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test`; frontend
`cd frontend && npm test && npx vite build`. Strict TDD: the domain tests are written red first (a `test:` commit
before the `feat:` commit). Cypress only in I: one nominal spec, `ninety-nine-game.cy.ts` (spec §11), plus the
game added to the lists of `game-picker.cy.ts`, `game-home.cy.ts` and `game-screens-homogeneity.cy.ts`; run once
each (issue #312 rule), on a shifted stack if 5173 is taken. A dev environment only for F2 (fixtures) and I
(manual play).

**Cost scale:** S about half a day, M about a day, L about two days.

## Status

| Chunk | Title | Cost | Needs | Parallel with | State |
|-------|-------|------|-------|---------------|-------|
| P0 | `feat: 99 shared types (interfaces)` | S (< 30 min) | plan approved | D | [ ] |
| C1 | `feat: 99 rules in the domain` | L | P0 | C2, F1, D, F2 | [ ] |
| C2 | `feat: 99 bot strategies` | M | P0 | C1, C3, F1, F2 | [ ] |
| F1 | `feat: 99 frontend model, session and store` | S-M | P0 | C1, C2, C3, D | [ ] |
| D | Claude Design canvas, 2-3 options, owner chooses | S | plan approved | P0, C1, C2, F1 | [ ] |
| F2 | `feat: 99 table components and screen` | L | P0, D | C1, C2, C3, F1 | [ ] |
| C3 | `feat: 99 presentation, config and bot coordinator` | M-L | C1 | C2, F1, F2 | [ ] |
| I | `feat: 99 playable (entry PR)` (integration) | M | C2, C3, F1, F2 | - | [ ] |

About 8 to 10 days of work, 7 PRs plus the canvas.

## Conflicts

| File | Chunks | Handling |
|------|--------|----------|
| `ninetynine/domain/NinetyNineOptions.java`, `CardEffect.java` (signatures), `DeckSource.java`, `event/*`, exceptions, `bot/NinetyNineBotView.java`, `bot/NinetyNineBotStrategy.java` | P0, C1, C2 | P0 creates them; after P0 they are read only for C2; C1 may fill bodies (`CardEffect`) but not change signatures. A signature change goes in a tiny follow-up on `main` |
| `ninetynine/domain/NinetyNine.java`, `NinetyNineFactory.java` | C1 | C1 owns them |
| `ninetynine/domain/bot/*` (strategies) | C2 | C2 owns them |
| `ninetynine/presentation/**`, `config/NinetyNineConfig.java`, `application*.properties` | C3 | C3 owns them; `application.properties` and `application-test.properties` are shared by every game: append-only, rebase |
| Shared backend tests (`PerGameConfigTest`, `ArchitectureRules`, `ArchitectureTest`, `MalformedTokenIT`, `LobbyControllerTest`) | C3 (and any other game in flight) | append-only lines; rebase on `origin/main` before the final push; never weaken a shared test |
| `frontend/src/model/ninetynine/NinetyNineState.ts` | P0, F1, F2 | P0 creates it; F1 and F2 add helpers in separate files |
| `frontend/src/locales/Messages.ts`, `en.ts` (and `fr.ts` once the French translation lands) | F2, I, French translation chunks | F2 adds `ninetyNineUi` and `options.ninetyNine` only; I adds the picker and rules texts; if `fr.ts` and its parity test are on `main`, the same chunk adds the French texts (parity test FAILS otherwise) |
| `games.ts`, `games.test.ts`, `router.ts`, `router.test.ts`, `useGameRooms.ts`(+test), `GamePickerView.test.ts`, `en.test.ts`, Cypress shared specs, `stompPlayer.ts` | I | entry PR only; never in parallel with another entry PR |
| `docs/architecture/context-map.md`, `CLAUDE.md` game list | C3 (section), I (game list, "not covered") | different lines; rebase |
| This spec | F2 (UI addendum), I (status) | different sections |

## P0 `feat: 99 shared types (interfaces)` (S, under 30 minutes)

Add-only, nothing registered (no factory bean, so no shared test moves), no behaviour.

- Backend `ninetynine/`: `package-info.java`; `domain/NinetyNineOptions` with `Lives` and `QuickMode` enums and the
  strict/lenient parse; `CardEffect` signatures (`isSafe`, `safeCards`, `apply`, `aceValues`); `Direction`, `Phase`;
  `DeckSource` port; the sealed `NinetyNineEvent` records and the exception classes; `bot/NinetyNineBotView`
  (record), `NinetyNineBotStrategy` (port), `NinetyNineBotLevel`, `NinetyNineDecision`.
- Frontend: `model/ninetynine/NinetyNineState.ts` (the DTO and event types of spec §8.3) and a fixture file used by
  F1 and F2 tests (a 4-seat round, a 12-seat round, a bust, a quick-mode end).
- **Tests first:** `NinetyNineOptionsTest` (defaults 3 / off; strict parse refuses `lives=5`, unknown keys).

## C1 `feat: 99 rules in the domain` (L)

- `NinetyNine` aggregate, `NinetyNineFactory` (`GAME_TYPE = "ninety-nine"`, 2 to 12, `supportsBots() = true`,
  descriptors `lives`, `quick`, seat option `level`), `CardEffect` bodies, `RandomDeckSource`,
  `ScriptedDeckSource`, `NinetyNineBuilder` (tests).
- **Tests first:** `CardEffectTest`, the ten worked examples of spec §6.2 as parameterised rows,
  `NinetyNineRoundTest` (deal of 3 to live seats, starter rules, reshuffle keeping the top, direction reset),
  `NinetyNineEndTest` (normal ranking, quick ranking with shared places), `NinetyNineForfeitTest` (on turn, off turn,
  last but one), `NinetyNineFactoryTest`, version bumps on every accepted command, a refused command never mutates.
- Answers of the owner that differ from the recommendations are local: Q1 (one row of `CardEffect`), Q3 (one line),
  Q4 (the stuck branch), Q5/Q5b (ranking), Q6/Q7 (starter, direction).

## C2 `feat: 99 bot strategies` (M)

Owns `ninetynine/domain/bot/` except the P0 types. Works from `NinetyNineBotView` built in tests by hand, so it
does not wait for C1.

- `RandomNinetyNineBot`, `GreedyNinetyNineBot`, `SamplingNinetyNineBot` (spec §10), `NinetyNineBotTuning`.
- **Tests first:** every level returns a safe card and an allowed Ace value on 1,000 random views; greedy picks the
  documented card on hand-written views (push the total, keep the K); sampling is deterministic with a seeded
  generator, prefers the play that leaves the next seat stuck when the unseen cards make it certain, stays under
  20 ms; opt-in benchmark (`-Dninetynine.bench=true`): hard vs medium vs easy, 200 games of 4 players, results
  written in the spec's bot section.
- `NinetyNineBotView.forSeat(game, seat)` and its DTO parity test move to C3 (they need the aggregate and the DTO).

## D Claude Design canvas (owner chooses) (S)

Artifact `quickstart`, intent `design`, 2 or 3 options on the real 99 data of spec §9.3 (12-seat table at 94 with
a reversed direction, a stuck bust, a quick-mode end with shared places), 375 px and 1280 px, light and dark, from
the Kobo/President felt and shared chrome. One recommendation. Covers the seven items of spec §9.3. The choice and
its link go into a "UI addendum" of the spec (written by F2).

## F1 `feat: 99 frontend model, session and store` (S-M)

- `application/NinetyNineSession.ts` (`NINETY_NINE_SESSION_CONFIG`, `play(card, aceValue?)` on
  `/app/ninety-nine/play`), `state/NinetyNine.store.ts` (`ninety-nine-store`, `useSeatRoom`),
  `composables/useNinetyNineBootstrap.ts`, model helpers (`playable` lookup, effect codes for the log line, seat
  order from `opponentOrder.ts`), `model/ninetynine/tempo.ts`, `motion.ts` (anchors and pure plans for `PLAY`,
  `RESHUFFLE`, `BUST`, `ROUND_START`).
- **Tests first:** session (destinations, payloads), store, bootstrap, motion plans over the P0 fixtures.

## F2 `feat: 99 table components and screen` (L)

- `components/ninetynine/*` and `view/ninetynine/NinetyNineGameScreen.vue` in the look chosen in D; the shared
  animation barrel, `seatRing.ts`, `TotalsTable.vue`, the shared chrome. Texts in `ninetyNineUi` and
  `options.ninetyNine` only. The screen is not routed yet (I does it).
- **Tests first:** each component (unsafe cards disabled with the reason, the Ace chooser shows only allowed
  values, lives and eliminated states, the bust banner with the revealed hand, shared places), the screen over
  fixtures. A 12-seat check at 375 px reported in the PR (`ui-change` skill for the visual part).

## C3 `feat: 99 presentation, config and bot coordinator` (M-L)

- `NinetyNineRestController`, `NinetyNineWebSocketController` (game-type check before start), `NinetyNineGameActions`,
  `NinetyNinePlayPayload`, `NinetyNineDto.forViewer`, `NinetyNineEventType` / `EventData`, `NinetyNineRoundPause`,
  `NinetyNineTestDealController` (`test` profile), `NinetyNineBotView.forSeat`, `NinetyNineBotCoordinator`,
  `config/NinetyNineConfig.java`, properties `ninetynine.bots.*` and `ninetynine.round-pause-ms` (0 under `test`).
- Shared backend tests per `add-a-game.md` §2.2; a `context-map.md` section.
- **Tests first:** `NinetyNineDtoTest` (no foreign hand, `playable` for the viewer only, the stuck hand public in
  `lastBust`), bot view parity with the DTO, `NinetyNineGameActionsTest`, `NinetyNineRestAndLifecycleTest`,
  `NinetyNineWebSocketControllerTest`, `NinetyNineWebSocketControllerIT` (STOMP game to a bust and the end, an
  unsafe card refused to the sender alone), `NinetyNineFixedDealIT` and `NinetyNineFixedDealAbsentInProductionIT`,
  round pause and coordinator with a scheduler stub (stale version dropped, failing strategy falls back).

## I `feat: 99 playable (entry PR)` (integration, M)

- Merge order check (C2, C3, F1, F2 on `main`), rebase.
- The shared frontend files of `add-a-game.md` §3.2 (registry `define('ninety-nine', { supportsBots: true, picker
  })`, route `ninety-nine-room`, `useGameRooms`, picker and rules texts, the pinned counts), `stompPlayer.ts`
  `ScriptedGameType`.
- Cypress (written red first): `ninety-nine-game.cy.ts`, the **only** 99 spec, nominal path of spec §11; the game
  added to the three shared picker specs. Each run once; report the specs and total Cypress time.
- Docs: project `CLAUDE.md` game list, `context-map.md` "not covered" line, this plan's status, issue #1 comment.
- All suites, a review of the whole feature (`code-review` skill), manual play in a dev environment (two tabs plus
  bots of each level).

## Test plan summary

| Level | Where | What |
|-------|-------|------|
| Domain (strict TDD) | C1, C2 | card effects, worked examples, rounds, ending and ranking, forfeit, options; bots legal on random views, greedy choices, sampling determinism and time cap |
| Presentation | C3 | DTO privacy, bot view parity, actions, REST, WebSocket unit and IT, seam present / absent, pause, coordinator |
| Frontend unit | F1, F2, I | session, store, bootstrap, motion plans, components, screen, registry and picker counts |
| E2E (nominal only) | I | `ninety-nine-game.cy.ts` (create, scripted join, start, play, bust, next round, end screen); shared picker specs |
