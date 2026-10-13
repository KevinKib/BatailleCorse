# Retrospective: adding Three Men's Morris (architecture view)

Written 2026-10-14 by Claude Opus 5.5, from `origin/main` at `a61377e` (merge of #187). Scope: what it cost to
add the fourth game, Three Men's Morris (backend #146, frontend #176 to #187), compared with Kobo and
Bullshit where useful, and a progressive roadmap towards **agentic game creation**: an agent adds a game with
the fewest files, decisions and collisions possible.

Evidence labels used throughout:

- **[V]** verified from git, `gh`, the code or a PR body (reference given);
- **[T]** testimony of the orchestrating session, not visible in git (reported as told);
- **[I]** my own inference or impression.

## 1. Executive summary

1. The **backend** is ready for new games: Morris needed 1 shared main file (28 lines of `AppConfig`) out of 43, in a PR open for 3 hours, and since the mutualization it needs none [V].
2. The **frontend was not**: before Morris could appear, two shared PRs (S1 #178, S2 #180, 54 files, 1,956 lines) had to make screens registry-driven and extract a generic seat-game session; that is 46% of the lines of the Morris frontend path [V].
3. Once S1/S2 landed, game-specific PRs were cheap and parallel (#181, #182, #183: 0 to 2 shared files each, merged within 5 minutes of each other) [V]; the **entry PR** still edits ~13 shared files, and two parallel entry PRs (Kobo K2 #184, Morris #185) collided on 8 files [V].
4. The most expensive frictions are not in the domain but at the edges: hard-coded game lists in shared tests, a picker not designed for N games, unstable Cypress runs and Docker ports, and names chosen independently by each plan [V/T].
5. The way forward is incremental: one naming convention, completeness tests that fail when a game misses an extension point, then a per-game manifest that removes most shared edits, then a `new-game` skill; no code generator and no dynamic routing before that.

## 2. What was measured

### 2.1 The Morris path, PR by PR [V]

Source: `gh pr view <n> --json files`. "Specific" = a path that names the game; "shared" = every other code
or test file; docs counted apart. Lines = additions + deletions.

| PR | What | Open → merged (UTC) | Specific files / lines | Shared files / lines (of which tests) | Notes |
|----|------|---------------------|------------------------|----------------------------------------|-------|
| #146 | Morris backend | 10-11 19:58 → 23:02 | 39 / 2,370 | 2 / 35 (1) | `AppConfig` +28, `LobbyRestControllerTest` 6; docs 2 files / 73 |
| #176 | Morris frontend spec + plan | 10-13 09:36 → 09:37 | – | – | docs 2 / 423 |
| #178 | S1: registry-driven screens | 11:46 → 14:25 | 0 | 37 / 710 (16) | shared with Kobo |
| #180 | S2: one seat-game session | 14:30 → 19:51 | 0 | 17 / 1,246 (6) | shared with Kobo; critical path (5 h 21) |
| #183 | Morris model, session, store | 19:53 → 20:05 | 10 / 968 | 0 | `MorrisSession.ts` is 33 lines |
| #182 | Morris board component | 19:53 → 20:10 | 2 / 504 | 2 / 29 (0) | `Messages.ts`, `en.ts` |
| #185 | Play Morris (entry + screen + e2e) | 20:11 → 21:21 | 7 / 1,324 | 13 / 308 (8) | collided with Kobo K2 #184 |
| #187 | Picker fits four games | 21:31 → 21:51 | 0 | 3 / 62 (1) | consequence of #184 + #185 |

Frontend path totals (#178, #180, #182, #183, #185, #187): **5,151 changed lines, 2,796 Morris-specific
(54%) and 2,355 shared (46%)**. Over the whole window #146 → #187 (38 merged PRs, 93 non-merge commits):
backend 216 files (+7,239 / −3,029), frontend 164 files (+10,781 / −3,542), docs 11 files (+1,652) [V].

The 13 shared files of #185: `model/games.ts`, `model/games.test.ts`, `router.ts`, `router.test.ts`,
`composables/useGameRooms.ts`, `useGameRooms.test.ts`, `locales/Messages.ts`, `locales/en.ts`,
`locales/en.test.ts`, `view/GamePickerView.test.ts`, `cypress/specs/game-picker.cy.ts`,
`cypress/specs/game-home.cy.ts`, `cypress/specs/game-screens-homogeneity.cy.ts` [V].

### 2.2 Comparison with Kobo [V]

| | Kobo | Morris |
|---|---|---|
| Backend PR | #115: 73 specific / 2 shared files, 6,706 added lines (78 files) | #146: 39 specific / 2 shared files, 2,475 added lines (43 files) |
| Extra backend seam for e2e | #179 `DeckSource` port + test-profile controller: 12 files / 651 lines | none (no randomness) |
| Model/session/store PR | #181: 16 specific / 2 shared files (`useGameRooms` + test), `KoboSession.ts` 79 lines | #183: 10 specific / 0 shared |
| Entry PR | #184: 3 specific / 13 shared files (7 tests) | #185: 7 specific / 13 shared (8 tests) |
| Frontend footprint today (`git ls-files` by name) | 19 files / 1,940 lines (table not done yet) | 18 files / 2,796 lines |
| Backend footprint today (main) | 51 files / 2,218 lines | 26 files / 838 lines (19 domain, 7 presentation) |

Bullshit, the second game, predates this window: its frontend now spans 34 files / 4,833 lines and its
session class (263 lines) was the one S2 generalised. The Morris spec measured, before S1/S2, that a third
game would have meant "editing six shared files that switch on the two existing games, copying a 260-line
session class and a bootstrap composable" (`docs/specs/2026-10-13-three-men-morris-frontend-design.md`,
"Shared-structure readiness", 14 items with file:line) [V].

### 2.3 Merge conflict between the two parallel entry PRs [V]

#184 (Kobo K2) and #185 (Morris PR5) both touched 12 files. Replaying #185's diff onto `main` without
#184 (`git merge-tree --merge-base 7eaeb3a eebe132 5c4f8e9`) gives **8 conflicted files**:
`games.ts`, `games.test.ts`, `router.ts`, `router.test.ts`, `en.ts`, `GamePickerView.test.ts`,
`game-picker.cy.ts`, `game-home.cy.ts`. **5 of the 8 are tests**, all because they list the games by hand
(`toEqual(['bataillecorse', 'bullshit', 'kobo', 'three-men-morris'])`, `toHaveLength(4)`, per-key loops).
The resolution commit `f6336fc` also **relaxed** the picker spec ("no longer requires four cards to fit
375x667 without an inner scroll"), re-tightened 30 minutes later by #187 [V]. (The replay is an
approximation: #185 had already been rebased; the true conflict count at rebase time is not in git.)

### 2.4 Shared files that enumerate the games today [V]

| Layer | File | Fails if a 5th game is forgotten? |
|-------|------|-----------------------------------|
| Backend test | `architecture/ArchitectureRules.java` (`GAMES` list) | **No**: the new package silently escapes the purity rules |
| Backend test | `architecture/ArchitectureTest.java` (two `@ValueSource` lists) | **No** (same) |
| Backend test | `config/PerGameConfigTest.java` (config names, game types, `assertEquals(4, …)`) | Yes, by the count only |
| Backend test | `presentation/MalformedTokenIT.java` (per-game REST and WS cases) | No: the game is simply not covered |
| Backend test | `presentation/LobbyControllerTest.java` (Add bot refused per game) | No |
| Frontend | `model/games.ts` (`GameKey` union + `define`) | n/a (it is the registry) |
| Frontend | `router.ts` (one route per game, hand-written redirects; Kobo has none) | No |
| Frontend | `composables/useGameRooms.ts` (key → store) | Yes, at runtime, guarded by a test on `GAMES` (deviation of S1: the plan wanted a compile error) |
| Frontend | `locales/Messages.ts` + `en.ts` (`gamePicker.games.<key>`, rules and `*Ui` namespaces) | Partly: `gameChoice()` throws without picker texts |
| Frontend tests | `games.test.ts`, `router.test.ts`, `GamePickerView.test.ts`, `useGameRooms.test.ts`, `en.test.ts` | Some by hard-coded counts, which is what conflicts |
| E2E | `game-picker.cy.ts`, `game-home.cy.ts`, `game-screens-homogeneity.cy.ts`, `support/stompPlayer.ts` (`ScriptedGameType`) | No |

### 2.5 Test suites over the period [V]

Backend `mvn test`: 943 tests at #146, 1,233 at #179. Vitest: 631 (#178) → 664 (#180) → 685 (#182) →
712 (#183) → 834 (#184) → 872 (#185) → 882 (#187). Cypress is not run in CI (`.github/workflows/ci.yml`).

## 3. What worked well

1. **The backend game abstraction holds.** `GameFactory` + `GameFactories` + the session core meant Morris
   changed nothing in `sessionmanagement` or the `game` kernel; #146 touched 1 shared main file (`AppConfig`,
   28 lines) [V]. Since #160 a game has its own `config/<Game>Config.java` and `PerGameConfigTest` states
   "adding a game never edits AppConfig" [V].
2. **Mutualization paid off on the newest game.** After #160 to #175 (`SeatCommands`, `SeatGameRest`,
   `SeatStateBroadcaster`, `SeatLifecycleBroadcaster`, shared test kit), Morris presentation shrank from 10
   files / 495 lines to 7 files + 1 config / 363 lines; its two broadcasters disappeared [V]. ArchUnit locks
   the context-map rules (#159) [V]. The rule of three in the mutualization spec kept the extraction honest
   (no abstract controller, no command bus) [V].
3. **After S1/S2, a game's frontend is mostly additive.** `MorrisSession` is a 33-line subclass of
   `SeatGameSession`; #183 touched only Morris files; #181, #182 and #183 ran in parallel and merged within
   5 minutes without conflict [V]. Bullshit moved onto the generic session with its existing tests untouched
   (#180: 412-line session test and store test green unchanged) [V].
4. **The server is the only rule source.** The Morris board model reads `availableActions`, `board` and
   `legalMoves` only, with no adjacency table on the client (#183 body) [V]; `MorrisBoard.vue` is a pure,
   isolated component (props in, events out, 21 tests) that could be built before any route existed [V].
5. **Plans and handoff reports made the work auditable.** The Morris spec's readiness audit (14 items with
   file:line) predicted exactly what S1/S2 had to do and the collision risk with Kobo [V]. PR bodies carry a
   handoff report, plan deviations and a "Non vérifié" section; S1 and S2 recorded DOM inventories before and
   after (26 identical readings in S1; 23/29/3 elements per screen in S2), so the refactors provably changed
   nothing visible for the existing games [V]. #180 even counted the 15 gaps that keep Bataille Corse outside
   the generic session [V].

## 4. Frictions, by severity

Severity reflects measured cost (lines, hours, rework) first, recurrence second.

### High

| # | Layer | Friction | Evidence |
|---|-------|----------|----------|
| F1 | Frontend | **Screens hard-wired to two games.** Closed `GameKey`, `LobbySeats` always showing "Add bot" (refused by the server since #168), `GameJoinView`/`useLobbyCreator` ternaries falling back to Bataille Corse, Bataille Corse shortcuts applied to any game, a hand-written picker. Two prerequisite PRs (S1, S2) were needed before any game work: 54 files, 1,956 lines, 46% of the Morris frontend path; S2 alone was open 5 h 21 and sat on the critical path of both games. | [V] spec readiness items 1-8; #178, #180. [T] context (a). |
| F2 | Frontend / tests | **Shared files edited by every game entry, and tests that pin the game list.** Each entry PR edits ~13 shared files; two entry PRs in parallel produced 8 conflicted files, 5 of them tests with hard-coded lists; the resolution weakened a test for 30 minutes. | [V] §2.3; [T] context (b). |
| F3 | Tests / tooling | **Unstable e2e and environment.** #167 found four root causes (lobby command lost before the first STOMP connection, late hydrate after Leave, 800 ms route fade, Windows modern standby freezing Cypress); before the fix `shared-lobby` failed 6/10 runs and `font-fit` 10/10. Each agent picked its own offset ports (5176/8083, 5273/8180/5105, 5190/8090/5095 in #167, #180, #187); HMR pointing at another session's stack and ports held by other projects were reported. E2E is not in CI. | [V] #167, #180, #187 bodies, `ci.yml`; [T] context (e), (g). |

### Medium

| # | Layer | Friction | Evidence |
|---|-------|----------|----------|
| F4 | Frontend | **Picker not designed for N games.** `GameChoiceCard.vue` was edited by 4 PRs in one day (#178, #184, #185, #187), with three successive squeezes; #187 warns that "a 5th game will make the panel scroll" at 360x640. | [V]; [T] context (c). |
| F5 | Process | **No shared naming convention.** The two Opus plans named the same things differently: `useGameClient.ts` vs `useGameRooms.ts`, `state/seatRoom.ts` vs `composables/useSeatRoom.ts`, `canAddBot` vs `supportsBots`, `joinAsScriptedPlayer(gameId, name, gameType)` vs `(gameType, gameId, name)`, `components/threemenmorris/` (plan) vs `components/morris/` (code). One game has four identifiers: type `three-men-morris`, package `threemenmorris`, classes `ThreeMenMorris*` and `Morris*`; Bataille Corse is `bataille-corse` on the server and in `stompPlayer`, `bataillecorse` in the frontend registry and routes. Folders differ too (`application/KoboSession.ts` vs `application/threemenmorris/MorrisSession.ts`), as do locale namespaces (`rules` is Bataille Corse's, then `bullshit`, `kobo`, `threeMenMorris`, `morrisBoard`, `*Ui`). | [V] plans, code; [T] context (d). |
| F6 | Backend tests | **Extension points that fail silently.** A 5th game package not added to `ArchitectureRules.GAMES` and the two `@ValueSource` lists is never checked for domain purity; `MalformedTokenIT` and `LobbyControllerTest` simply would not cover it. Only `PerGameConfigTest` fails, by a count. | [V] §2.4. |
| F7 | Tests | **Determinism is per game.** Kobo's random deal blocked e2e until #179 added a `DeckSource` port and a test-profile controller (12 files, 651 lines). Nothing tells the next game with randomness to do this up front. | [V] #179; [T] context (f). |
| F8 | Process | **TDD not verifiable, sometimes not followed.** Each step lands as one `feat:` commit after a `chore:` start commit, so test-first order is invisible in git; #184 states its Cypress specs were written after the code; several agents did the same despite a strict TDD instruction. | [V] commit lists, #184 body; [T] context (e). |
| F9 | Docs | **Docs drift.** `context-map.md` still opens with "Two bounded contexts", its first diagram says Bullshit is "not wired yet (Slice 2)", the Morris section names `ThreeMenMorrisLifecycleBroadcaster` (deleted by #171) and says "Not covered yet: … the frontend" for Kobo and Morris; the project `CLAUDE.md` lists Bataille Corse, Bullshit and Kobo only. Every game updates context-map, specs, plans, two issues and `CLAUDE.md` by hand. | [V] files; [T] context (j). |

### Low (known, contained)

| # | Layer | Friction | Evidence |
|---|-------|----------|----------|
| F10 | Frontend | **Bataille Corse stays outside the generic helpers**: 15 gaps (4 config, 5 genuine: public animated topic, event queue…, 6 accidental). Not blocking a new game, but every shared helper has two code paths. | [V] #180; [T] context (h). |
| F11 | Contract | **Two sources of truth and several outcome shapes.** Bots are declared twice (`GameFactory.supportsBots()` on the server, `GameDefinition.supportsBots` in the registry, owner decision in S1). Outcome DTOs have 4 wire shapes (`Won(winnerId: String)`, `Finished(winners: List<Integer>)`, `Finished(winner: Integer)`, `PlayerIdDto`), so each game writes its own end-screen mapping. | [V] mutualization spec §1.3, #178. |
| F12 | Tooling | **Agent interruptions.** API network errors stopped agents mid-task; they were resumed by message. One "frozen" Cypress run in #167 was in fact an interrupted agent. | [T] context (i); [V] #167. |

## 5. Projection: what a 5th game touches today

Assumption: a seat game like Morris (per-seat DTO, no bots, no options). Paths relative to
`backend/src/{main,test}/java/org/kevinkib/cardgames/` and `frontend/`.

**New, game-specific (≈ 55 files for a Morris-sized game) [V by analogy with Morris]**

- Backend main (Morris: 26): `<game>/package-info.java`; `domain/` aggregate implementing `Game`,
  `<Game>Factory` (`GAME_TYPE`, `supportsBots()`, option descriptors), events, result, exceptions;
  `presentation/<Game>RestController` (3 endpoints over `SeatGameRest`), `<Game>WebSocketController` (start +
  commands over `SeatCommands`), `<Game>GameActions`, `api/*Payload`, `dto/<Game>Dto` (`forViewer` + outcome),
  `dto/event/<Game>EventData`, `<Game>EventType`; `config/<Game>Config.java` (factory, `SeatStateBroadcaster`,
  actions, `SeatLifecycleBroadcaster`).
- Backend tests (Morris: 11): domain, DTO, actions, REST + lifecycle, WebSocket unit, `*WebSocketControllerIT`,
  a `*Table` fixture.
- If the game is random: a source port + `@Profile("test")` controller, as Kobo #179.
- Frontend (Morris: 18): `model/<game>/` state types and pure helpers, `application/<Game>Session.ts`,
  `state/<Game>.store.ts`, `composables/use<Game>Bootstrap.ts`, `view/<game>/<Game>GameScreen.vue`,
  `components/<game>/*`, each with its test; `cypress/specs/<game>-lobby.cy.ts`, `<game>-game.cy.ts`.

**Shared files to edit: 21 mandatory, up to 23 [V]**

- Backend (5): `ArchitectureRules.java`, `ArchitectureTest.java`, `PerGameConfigTest.java`,
  `MalformedTokenIT.java`, `LobbyControllerTest.java` (no-bot refusal).
- Frontend (14): `model/games.ts`, `model/games.test.ts`, `router.ts`, `router.test.ts`,
  `composables/useGameRooms.ts`, `useGameRooms.test.ts`, `locales/Messages.ts`, `locales/en.ts`,
  `locales/en.test.ts`, `view/GamePickerView.test.ts`, `cypress/specs/game-picker.cy.ts`,
  `cypress/specs/game-home.cy.ts`, `cypress/specs/game-screens-homogeneity.cy.ts`,
  `cypress/support/stompPlayer.ts`.
- Docs (2): `docs/architecture/context-map.md`, `CLAUDE.md` (game list).
- Likely (+2): `view/GamePickerView.vue` and `components/GameChoiceCard.vue` (picker capacity, F4).

Plus a spec, a plan and a tracking issue. **Total ≈ 75 to 80 files, of which 21 to 23 shared**; only about
7 of the shared edits fail a test when forgotten.

## 6. What a new agent should know today

1. Backend: copy the Morris layout (`threemenmorris/` + `config/ThreeMenMorrisConfig.java`); reuse
   `SeatCommands`, `SeatGameRest`, `SeatStateBroadcaster`, `SeatLifecycleBroadcaster` and the test kit
   (`RecordingMessaging`, `StompTestClient`, `SessionTable`); check the game type in `start` before
   `startGame(id, token, Class)`. Add the game to the three ArchUnit lists by hand: nothing will tell you.
2. Frontend: subclass `SeatGameSession` (config: game type, REST base, token prefix, start destination),
   build the store on `useSeatRoom`, the bootstrap on `useSeatGameBootstrap`. Do **not** start from Bataille
   Corse (`GameSession.ts`, `view/alpha/`): it is the legacy outlier.
3. Keep the game invisible until the last PR: model/session/store and components first (0 shared files),
   one **entry PR** that registers the game (`games.ts`, route, `useGameRooms`, locales, shared tests). Never
   run two entry PRs at once; rebase the second.
4. The server decides: read `availableActions` and legal moves from the DTO, put no rule in the client.
5. If the game is random, add a seedable source under the `test` profile before writing e2e.
6. E2E: one stack at a time or explicit offset ports with `VITE_HMR_CLIENT_PORT`; wait on DOM states or
   scripted frames, never `cy.wait(ms)`; the route fade is 800 ms; lobby commands before the first STOMP
   connection are queued (#167).
7. Update `context-map.md` and `CLAUDE.md`; both are already behind.

## 7. Progressive simplification roadmap

Ordered: each step is small, mergeable alone, and makes the next one cheaper. Cost: S ≤ half a day,
M ≈ 1 day, L > 1 day of agent work.

| # | Action | Benefit | Cost | Risk | Depends on |
|---|--------|---------|------|------|------------|
| R1 | **Naming convention** (`docs/architecture/game-conventions.md`): one kebab-case game id used as server type, registry key, route segment, STOMP prefix, token prefix, Cypress file prefix and folder name in every layer (`model/<id>/`, `application/<id>/`, `view/<id>/`, `components/<id>/`); one PascalCase prefix for classes; locale namespaces `<camelId>Rules` / `<camelId>Ui`; Bataille Corse's `bataillecorse` / `bataille-corse` split documented as the only legacy alias. | Removes F5: plans and agents stop inventing names | S | Low (docs only) | – |
| R2 | **Docs refresh + "Add a game" checklist** (`docs/architecture/adding-a-game.md`) listing §5 as checkboxes, the PR order of §6.3, the determinism rule; fix `context-map.md` and `CLAUDE.md` (F9). | One page an agent can execute; fixes drift | S | Low | R1 |
| R3 | **Backend completeness test**: derive the game list from packages (every top-level package with a `domain` sub-package is a game) and feed ArchUnit with it; assert per game a `<Game>Config`, a `GameFactory` bean, a `GameLifecycleBroadcaster`, a `/<id>/start` mapping; replace the hard-coded lists and counts. | F6: forgetting fails; no list to edit or conflict on | S | Low | R1 |
| R4 | **Frontend completeness test + de-pinned shared tests**: one Vitest over `GAMES` asserting a room route named `<id>-room`, a `useGameRooms` client, picker texts, rules and UI namespaces, a `ScriptedGameType`; rewrite `games.test`, `router.test`, `GamePickerView.test`, `useGameRooms.test`, `en.test` and the picker/home Cypress specs to iterate `GAMES` instead of listing keys. | Removes 5 of the 8 conflict sources of §2.3; forgetting fails | S-M | Low | R1 |
| R5 | **Picker built for N games**: layout tested with a fake registry of 6 to 8 entries at 360x640 and 375x667 (scrolling list or compact cards decided once). | F4: no more per-game squeeze | S | Low (visual; `ui-change`) | R4 |
| R6 | **Per-game frontend manifest**: `games/<id>/manifest.ts` (definition, lazy room component, room client factory, locale module, scripted type) collected with `import.meta.glob`; `games.ts`, `router.ts`, `useGameRooms.ts` and `stompPlayer.ts` derive from it; per-game locale files merged into `Messages`. Lazy imports keep the model free of views (the objection of spec item 9). | Entry PR goes from ~13 shared files to ~0-2; parallel entries stop colliding (F2) | M | Medium: touches routing and typing of locales; do after R4 so tests guard it | R4 |
| R7 | **Shared contract suites**: backend abstract JUnit `SeatGameContract` (create, join, start with type check, unknown token ignored, refused command answers the sender alone with state, forfeit, play again, REST rehydrate) that each game extends with a small adapter; Vitest `seatGameSessionContract(config)`; one Cypress lobby spec parameterised over `GAMES` (create, invite, Add bot only if `supportsBots`, Start, Leave) replacing `kobo-lobby` / `morris-lobby` duplicates. | A new game gets ~30 tests for free and the same guarantees as the others | M | Low-medium (abstract test classes must stay readable) | R3, R4 |
| R8 | **Determinism convention**: a game with randomness exposes a source port and a `@Profile("test")` seed endpoint following #179; checklist item + contract test that the endpoint is absent outside `test`. | F7 settled before e2e starts | S (doc) / M (per game) | Low | R2 |
| R9 | **`new-game` skill** for agents: reads R1/R2, asks only product questions (players, bots, options, randomness), prescribes the PR sequence (backend → model/session/store → components → one entry PR), the commit order that shows TDD (`test:` commit before `feat:`), and the e2e port rule. | Fewer decisions per agent; consistent plans (F5, F8) | S | Low | R2, ideally R6 |
| R10 | **One outcome DTO** (`winners: int[]`, `reason`) for the seat games, with one end-screen mapping on the client. The rule of three is met (Bullshit, Kobo, Morris). | One less per-game decision; generic end screen | M | Medium: wire contract change, needs R7 first | R7 |
| R11 | **E2E environment hardening**: a `dev-ports` script that finds a free offset and sets `VITE_HMR_CLIENT_PORT`; e2e in CI (nightly or on label) on one stack. | F3: fewer port/HMR incidents, flakiness seen early | M | Medium (CI time, Docker in CI) | – |
| R12 | **Scaffold generator** (`npm run new-game <id>` or a script) producing the R6 folder, the backend skeleton and the contract-test subclasses. | Minimum friction for agents | M | Medium: a template frozen too early copies today's mistakes | R6, R7, and one more game added by hand with them |

### What not to generalise yet

- **No dynamic `/{gameType}` routes or STOMP destinations, no abstract controller, no command bus**: the
  mutualization spec rejected them for grep-ability; nothing since contradicts it.
- **No generic table or board component**: Bullshit's ring, Kobo's grids and the Morris board share chrome
  (timer, rules, Back, overlays), not layout.
- **No shared bot coordinator** until a third game has bots (two today).
- **No migration of Bataille Corse onto `SeatGameSession`** just for symmetry: its 5 genuine gaps (public
  animated topic, event queue) would bend the generic session. Converge the 6 accidental gaps only when
  Bataille Corse is touched anyway.
- **No server-driven `supportsBots` replacing the registry flag** before R6: it is the same fact twice, but
  low cost; fold it into the manifest or the lobby view later.
- **No code generator before R6 and R7**: generate from a convention that has survived one more game.

## 8. What I would do next

1. **R1 + R2 in one docs PR**: the naming convention, the "Add a game" checklist and the context-map /
   `CLAUDE.md` refresh. Cheapest step, and every later agent reads it.
2. **R3 + R4**: completeness tests on both sides, and shared tests that iterate the registry instead of
   listing games. They turn silent omissions into red tests and remove most merge conflicts before the Kobo
   table PRs and any 5th game.
3. **R6, the per-game frontend manifest**, then write the `new-game` skill (R9) against it, so the entry PR
   of the next game touches almost no shared file.
