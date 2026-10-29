# Add a game: executable checklist

The steps, files and tests needed to add a game, in order. Names follow
`docs/architecture/game-conventions.md` (`<id>`, `<pkg>`, `<Pascal>`, `<camel>`). Background and measured
costs: `docs/architecture/new-game-retrospective-three-men-morris.md` (action R2).

Written 2026-10-14 from `origin/main` at `8b6bf42`, for a seat game like Three Men's Morris or Kobo
(per-seat DTO, `SeatGameSession` on the client). Backend paths are relative to
`backend/src/{main,test}/java/org/kevinkib/cardgames/`, frontend paths to `frontend/`.

Each shared extension point is marked:

- **FAILS**: a test goes red if you forget it;
- **SILENT**: nothing fails; only this checklist protects you.

## 0. Questions for the owner, before the spec

Ask only product questions; everything else is in this page and the conventions.

1. Display name and **id** (`<id>`, kebab-case, permanent: it is in URLs and stored tokens).
2. Players: minimum and maximum.
3. **Bots**: yes or no; if yes, levels (a seat option) and thinking delays.
4. **Host options** in the lobby (variants, sizes…): keys, values, default.
5. **Randomness** (deal, draw, first player): if any, the e2e need a fixed seam (§5).
6. End of game: winners, draws, scoring over rounds; what the end screen says.
7. Keyboard shortcuts: remappable actions or not (no gear when none).
8. Picker card: icon and severity; rules text content.

The request is architectural: write the spec (`docs/specs/YYYY-MM-DD-<id>-*-design.md`) and the plan
(`docs/plans/...`), commit them, and wait for the owner to validate the **plan** before any code (global
`CLAUDE.md`). Open or reuse a tracking issue; tick roadmap issue #1 when the game ships.

## 1. PR order

| # | PR | Shared files | Visible to players? |
|---|----|--------------|---------------------|
| 1 | Spec + plan (docs) | none | no |
| 2 | **Backend**: context, config, its tests, the shared backend tests (§2), the determinism seam if random (§5), `context-map.md` section | ~5 test files + docs | no (API only) |
| 3 | Frontend **model, session, store, bootstrap** (§3.1) | 0 | no |
| 4 | Frontend **components** of the board or table (§3.1) | `locales/Messages.ts`, `locales/en.ts` if they hold texts | no |
| 5 | **Entry PR**: registry, route, room client, locales, screen, shared tests, Cypress (§3.2, §4), `CLAUDE.md` game list | ~13 | **yes** |

- PRs 3 and 4 can run in parallel once 2 is merged; they touch no shared logic, so the game stays invisible.
- There is **exactly one entry PR** per game, and **never two entry PRs at once** (two games, or a game and a
  picker change): they collide on the same shared files (8 conflicted files in #184 / #185). Rebase the
  second one after the first is merged, and never weaken a shared test to resolve a conflict.
- One draft PR per step, opened at the start (`docs/harnesses.md`, draft-PR rule).

## 2. Backend checklist (PR 2)

### 2.1 New files (copy the layout of `threemenmorris/` and `config/ThreeMenMorrisConfig.java`)

- [ ] `<pkg>/package-info.java`: what the context is; `domain` pure, `presentation` the adapters.
- [ ] `<pkg>/domain/<Pascal>.java`: the aggregate, implementing `game.Game`; rules, turn order, end of game,
      `forfeit`. No Spring, no session, no transport (ArchUnit, once §2.2 is done).
- [ ] `<pkg>/domain/<Pascal>Factory.java`: `GAME_TYPE = "<id>"`, `gameType()`, `minPlayers()`, `maxPlayers()`,
      `create(...)`; override `supportsBots()` only if the game has bots; `optionDescriptors()` /
      `seatOptionDescriptors()` with label keys `options.<camel>.<key>`.
- [ ] `<pkg>/domain/...`: exceptions, events, result; a randomness port if needed (§5).
- [ ] `<pkg>/presentation/<Pascal>RestController.java`: `@RequestMapping("/api/<id>")`, the three endpoints
      `GET /game/{id}`, `POST /game/{id}/join`, `POST /game/{id}/play-again` over `SeatGameRest`.
- [ ] `<pkg>/presentation/<Pascal>WebSocketController.java`: `@MessageMapping("/<id>/start")` that **checks
      the game type before** `sessionService.startGame(id, token, <Pascal>.class)`, then one mapping per command
      `/<id>/<command>` through `SeatCommands`.
- [ ] `<pkg>/presentation/<Pascal>GameActions.java`: the use cases (call the aggregate, touch, broadcast under
      the game monitor), shared by humans and bots.
- [ ] `<pkg>/presentation/api/<Pascal><Command>Payload.java`, `dto/<Pascal>Dto.java` (`forViewer`: the only
      projection, hides what a seat must not see), `dto/event/<Pascal>EventType.java`,
      `dto/event/<Pascal>EventData.java`.
- [ ] `config/<Pascal>Config.java`: beans `<camel>Factory`, `SeatStateBroadcaster<<Pascal>, <Pascal>Dto>`
      `<camel>StateBroadcaster` (`BotSeats.none()` without bots), `<camel>GameActions`,
      `GameLifecycleBroadcaster <camel>LifecycleBroadcaster` (a `SeatLifecycleBroadcaster`). **Never edit
      `AppConfig`**: `GameFactories` and `GameLifecycleBroadcasters` collect the beans.
- [ ] Bots only: `<pkg>/domain/bot/` (strategy fed by what the seat sees), `presentation/bot/<Pascal>BotCoordinator`,
      properties `<pkg>.bots.*` in `src/main/resources/application.properties`, zero delays in
      `application-test.properties`.

### 2.2 Shared backend tests to edit

| File (under `src/test/...`) | Change | If forgotten |
|-----------------------------|--------|--------------|
| `config/PerGameConfigTest.java` | add `"<Pascal>Config"` to `CONFIGS`, `"<id>"` to `GAME_TYPES`, bump both counts (`assertEquals(4, …)`) | **FAILS** on the counts once the config exists; the two lists themselves are SILENT |
| `architecture/ArchitectureRules.java` | add `"<pkg>"` to `GAMES` | **SILENT**: the package escapes the "no game knows another game" rule |
| `architecture/ArchitectureTest.java` | add `"<pkg>"` to both `@ValueSource` lists | **SILENT**: the domain is never checked for purity |
| `presentation/MalformedTokenIT.java` | add the REST case (`restViewAnswers403("/api/<id>/game/", <Pascal>Factory.GAME_TYPE)`) and the WebSocket cases of every command | **SILENT**: the game is simply not covered |
| `presentation/LobbyControllerTest.java` | add the factory to the `GameFactories` list; without bots, a `given<Pascal>Room_whenAddBot_thenRefused…` case via `assertAddBotRefused` | **SILENT** |
| `presentation/LobbyRestControllerTest.java` | add the factory to its `GameFactories` list if a lobby case needs the game | SILENT |

### 2.3 Tests to write (Morris has 11 test files; mirror them)

- [ ] Domain: one test class per rule area (placement, moves, end of game, forfeit), plus the factory
      (`gameType`, player bounds, options validation).
- [ ] `<Pascal>DtoTest`: `forViewer` shows each seat only what it may see.
- [ ] `<Pascal>GameActionsTest`, `<Pascal>RestAndLifecycleTest`, `<Pascal>WebSocketControllerTest` (unit,
      over the `presentation/support` kit and a `<Pascal>Table` fixture built on `SessionTable`).
- [ ] `<Pascal>WebSocketControllerIT`: a real STOMP game with `StompTestClient` (create, join, start, a few
      commands, a refused command answered to the sender alone, the end). The name must end in `IT` or `Test`.
- [ ] If random: the seam tests of §5.
- [ ] `<Pascal>WireContractTest`: the wire contract. With `presentation/support/WireContract`, record a lobby
      (`contract.lobby(...)`), a scripted game on fixed cards that reaches every phase and the end, and the
      lifecycle (`WireContract.playLifecycle`), plus `verifyEventTypes(GAME_TYPE, <Pascal>EventType.values())`. The
      golden files land in the repository-level `contract/<id>/` (written on the first run, rewritten with
      `-Dcontract.update=true`); commit them. Everything recorded must be deterministic (fix every deal and draw).

## 3. Frontend checklist

### 3.1 New files (PRs 3 and 4: nothing shared)

- [ ] `src/model/<pkg>/<Short>State.ts`: the view and response types of the DTO; pure helpers with tests. Read
      `availableActions` / legal moves from the DTO: **no rule in the client**.
- [ ] `src/application/<Pascal>Session.ts` (+ `.test.ts`): subclass of `SeatGameSession` with
      `<ID_IN_UPPER_SNAKE_CASE>_SESSION_CONFIG = { gameType: '<id>', restBase: '/api/<id>/game', tokenKeyPrefix: '<id>:tokens:',
      startDestination: '/app/<id>/start' }` and one method per command (`publishAction('/app/<id>/<command>', …)`).
- [ ] `src/state/<Pascal>.store.ts` (+ test): `defineStore('<id>-store', …)` built on `useSeatRoom`.
- [ ] `src/composables/use<Pascal>Bootstrap.ts` (+ test): over `useSeatGameBootstrap(findGame('<id>'), …)`.
- [ ] `src/components/<pkg>/*.vue` (+ tests): pure components, props in and events out.
- [ ] `src/view/<pkg>/<Pascal>GameScreen.vue` (+ test): composes the shared chrome (`GameTimer`, `RulesPanel`,
      `GameLobby`, `EndGameOverlay`, `DisconnectOverlay`, `ForfeitBanner`…). It can land in PR 4 or the entry PR.
- [ ] Every visible text goes through `src/locales/` (`<camel>Ui`, rules at `<camel>`), never inline.
- [ ] A table whose cards move: plug it into the shared animation module, never copy another game's loop.
      `src/model/<pkg>/tempo.ts` (its numbers), `src/model/<pkg>/motion.ts` (its anchors and a pure plan per event,
      tested), then in the screen `defineAnchors`, `useTableMotion` and `CardMotionLayer`, imported from the barrel
      `src/animation` (a file inside it is refused by `animation/boundaries.test.ts`: **FAILS**). Recipe and example:
      `src/animation/README.md`; map: `docs/architecture/card-animation.md`.

### 3.2 Entry PR: shared files

| File | Change | If forgotten |
|------|--------|--------------|
| `src/model/games.ts` | add `'<id>'` to `GameKey` and a `define('<id>', { supportsBots, picker, hotkeys? })` entry; `supportsBots` must equal the factory's | the game stays invisible (this is the gate) |
| `src/model/games.test.ts` | update the key list of the first test; add a case for the game's traits | **FAILS** (the key list is pinned) |
| `src/router.ts` | import the screen, add `{ path: '/<id>/room/:id', name: '<id>-room', component, props }` | **SILENT**: the room URL falls into the catch-all and lands on `/` |
| `src/router.test.ts` | add `'<id>'` to the two hard-coded key loops | SILENT |
| `src/composables/useGameRooms.ts` | add `'<id>': { create, join }` from the store | **FAILS** (`givenEveryRegisteredGame_thenItHasARoomClient`) |
| `src/composables/useGameRooms.test.ts` | a `given<Pascal>_thenCreateAndJoinGoThroughItsStore` case | SILENT |
| `src/locales/Messages.ts`, `src/locales/en.ts` | `gamePicker.games['<id>']`, `<camel>: RulesMessages`, `<camel>Ui`, `options.<camel>` if the game has options | picker texts **FAIL** (`en.test.ts` loops over `GAMES`; `gameChoice` throws at runtime); the others are SILENT |
| `src/locales/en.test.ts` | non-empty checks for `<camel>` and `<camel>Ui` | SILENT |
| `src/view/GamePickerView.test.ts` | add the game to the texts loop, bump `toHaveLength(4)` twice, a "Play goes to its Game home" case | **FAILS** (counts pinned) |
| `cypress/specs/game-picker.cy.ts`, `game-home.cy.ts`, `game-screens-homogeneity.cy.ts` | add the game to their lists or cases | SILENT (and Cypress is not in CI) |
| `cypress/support/stompPlayer.ts` | add `'<id>'` to `ScriptedGameType` | SILENT |
| `src/view/GamePickerView.vue`, `src/components/GameChoiceCard.vue` | only if the picker no longer fits (checked at 360x640 and 375x667, `ui-change` skill) | SILENT |

TypeScript types are not checked by any suite (`npm test` is Vitest, `npx vite build` does not type-check, no
`vue-tsc` in `package.json`): a missing key in `en.ts` or `ScriptedGameType` only shows in the editor.
The one exception is the wire: `src/contract/wireContract.test.ts` type-checks every golden file of `contract/`
against the frontend types with the TypeScript compiler (field names, nullability, enum values, extra fields, event
type strings both ways, and the typed fixtures listed). Add the game to `src/contract/games.ts` (its view, event
type union, `eventData` type per event type, fixtures): **FAILS** while a `contract/` folder has no entry.

## 4. E2E (entry PR)

- [ ] `cypress/specs/<id>-lobby.cy.ts`: create, invite, join, Start; Add bot only if `supportsBots`; Leave.
- [ ] `cypress/specs/<id>-game.cy.ts`: a full game to its end screen, the second seat driven by
      `joinAsScriptedPlayer(gameId, name, '<id>')` and `waitForFrame`.
- [ ] Wait on DOM states or frames, never `cy.wait(ms)`; the route fade lasts 800 ms; lobby commands sent
      before the first STOMP connection are queued (#167).
- [ ] Run on the e2e stack (`docker-compose.e2e.yml`, Spring profile `test`), one stack at a time, or on an
      offset stack with `--config baseUrl=...` (`CLAUDE.md`, Tests and dev environment).

## 5. Determinism (random games, PR 2)

A random game cannot be tested end to end until a test can fix the draw. Do it in the backend PR, before
any e2e, following Kobo #179:

- [ ] A port in `<pkg>/domain/` (Kobo: `DeckSource`) with a random default (`RandomDeckSource`) and a
      scripted implementation (`ScriptedDeckSource`).
- [ ] In `config/<Pascal>Config.java`: the random bean under `@Profile("!test")`, the scripted one under
      `@Profile("test")`.
- [ ] A test-only controller `presentation/<Pascal>Test…Controller` under `@Profile("test")`, mapped to
      `/api/test/<id>/...` (Kobo: `KoboTestDealController`, `PUT`/`DELETE /api/test/kobo/{gameId}/deal`).
- [ ] Tests: the seam works under `test` (Kobo: `KoboFixedDealIT`) and **the route does not exist outside
      `test`** (Kobo: `KoboFixedDealAbsentInProductionIT`).

## 6. TDD and commits

- [ ] Every step is test-first, and git shows it: a `test: …` commit with the failing tests **before** the
      `feat: …` commit that makes them pass (Cypress specs included). A single `feat:` commit does not show the
      order (retrospective F8).
- [ ] Before the PR, all suites pass: `cd backend && mvn -s ../deploy/docker/settings.xml test`;
      `cd frontend && npm test && npx vite build`; the game's Cypress specs plus the shared ones touched.

## 7. Docs

- [ ] PR 2: a section in `docs/architecture/context-map.md` (package, factory, endpoints, broadcasters, what
      is not covered yet).
- [ ] Entry PR: the game list of the project `CLAUDE.md` (intro line and bounded contexts), and update the
      "not covered yet" line of `context-map.md`.
- [ ] Tracking: comment or tick the game issue and roadmap #1 when each PR is merged; no status PR.
- [ ] A name this page or `game-conventions.md` does not cover: decide it in the plan, then add it to
      `game-conventions.md` in the same PR.
