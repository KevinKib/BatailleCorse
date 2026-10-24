# Game naming conventions

One name per game, derived mechanically in every layer, so that plans and agents stop inventing names
(friction F5 of `docs/architecture/new-game-retrospective-three-men-morris.md`, action R1). The checklist
that uses these names is `docs/architecture/add-a-game.md`.

Written 2026-10-14 from `origin/main` at `8b6bf42`. Labels:

- **[V]** verified in the code at that commit (the existing games follow it, file given);
- **[D]** a rule chosen here for new games where the code is not uniform; listed again under
  "Decisions to confirm" at the end.

Existing deviations are listed under "Legacy names": **do not copy them, and do not rename them just for
symmetry** (renames touch the wire contract, stored tokens and invite links); converge only when the code is
touched for another reason.

## 1. The game id and its four forms

A game has **one id**: lowercase ASCII words joined by hyphens (kebab-case), stable forever once released,
because it is in URLs, stored tokens and the server wire. Every other name is derived from it.

| Form | Rule | Three Men's Morris | Kobo | Used for |
|------|------|--------------------|------|----------|
| `<id>` | kebab-case | `three-men-morris` | `kobo` | everything on the wire and at runtime (§2) |
| `<pkg>` | `<id>` without hyphens | `threemenmorris` | `kobo` | Java package, Spring property prefix, frontend game folders (§3, §4) |
| `<Pascal>` | PascalCase of the words | `ThreeMenMorris` | `Kobo` | class, type and file prefixes |
| `<camel>` | camelCase of the words | `threeMenMorris` | `kobo` | locale namespaces, option label keys, Spring bean names |

Pick an id that reads well in a URL (`/games/<id>`), and is not the prefix of another game's id.

## 2. Where `<id>` appears (wire and runtime)

| Layer | Pattern | Verified examples |
|-------|---------|-------------------|
| Server game type | `<Pascal>Factory.GAME_TYPE = "<id>"`, returned by `gameType()` | `ThreeMenMorrisFactory`, `KoboFactory`, `BullshitFactory` [V] |
| Lobby creation | `POST /api/lobby` with `{"gameType": "<id>"}` | `SeatGameSession.create` sends `config.gameType` [V] |
| REST | `/api/<id>/game/{id}` (state), `/join`, `/play-again` | `@RequestMapping("/api/three-men-morris")`, `/api/kobo`, `/api/bullshit` [V] |
| STOMP commands | `/app/<id>/<command>`, command in camelCase (`/start` first) | `/three-men-morris/place`, `/kobo/swapDrawn` [V]; Bullshit's are unprefixed (legacy) |
| STOMP topics | shared, not per game: `/topic/game/{gameId}` and `/topic/game/{gameId}/seat/{token}` | `GameMessagingService` [V] |
| Test-only REST seam | `/api/test/<id>/...` under `@Profile("test")` | `KoboTestDealController` (`/api/test/kobo/{gameId}/deal`) [V] |
| Seat token (localStorage) | `<id>:tokens:<gameId>` (`SeatGameSessionConfig.tokenKeyPrefix`) | `three-men-morris:tokens:`, `kobo:tokens:`, `bullshit:tokens:` [V] |
| Frontend registry | `GameKey` member and `define('<id>', …)` in `frontend/src/model/games.ts` | [V] |
| Routes | `/games/<id>`, `/games/<id>/join/:id?`, `/games/<id>/room/:id` (built by `define`) | [V] |
| Room route name | `<id>-room` in `frontend/src/router.ts` | `three-men-morris-room`, `kobo-room`, `bullshit-room` [V] |
| Pinia store id | `defineStore('<id>-store', …)` | `three-men-morris-store`, `kobo-store`, `bullshit-store` [V] |
| Picker texts | `gamePicker.games['<id>']` in `locales/Messages.ts` / `en.ts` | [V] |
| `data-test` hooks of the picker | `choice-<id>`, `play-<id>` | `cypress/specs/game-picker.cy.ts` [V] |
| Cypress scripted player | `ScriptedGameType` member in `cypress/support/stompPlayer.ts` | [V] |
| Cypress spec files | `<id>-<topic>.cy.ts` | `kobo-lobby.cy.ts`, `bullshit-*.cy.ts` [V]; Morris uses `morris-*` (legacy) |
| Hotkey storage | `<id>.hotkeys` (one JSON entry) | `bullshit.hotkeys` [V, single example; D] |

## 3. Backend names

Paths relative to `backend/src/main/java/org/kevinkib/cardgames/`.

| What | Name | Verified examples |
|------|------|-------------------|
| Bounded context package | `<pkg>/` with `package-info.java`, `domain/`, `presentation/` | `threemenmorris/`, `kobo/`, `bullshit/`, `bataillecorse/` [V] |
| Aggregate | `<pkg>/domain/<Pascal>.java` implementing `game.Game` | `ThreeMenMorris`, `Kobo`, `Bullshit` [V] |
| Factory | `<pkg>/domain/<Pascal>Factory.java` implementing `game.GameFactory` | [V] |
| Domain exceptions, events, results | free inside `domain/`, prefixed `<Pascal>` when the name would be ambiguous across games | [D] (Morris uses `MorrisEvent`, `MorrisResult`) |
| Controllers | `presentation/<Pascal>RestController`, `presentation/<Pascal>WebSocketController` | [V] |
| Use cases | `presentation/<Pascal>GameActions` | [V] |
| Payloads | `presentation/api/<Pascal><Command>Payload` | `ThreeMenMorrisPlacePayload`, `KoboSlotPayload`, `BullshitDiscardPayload` [V] |
| Per-seat state | `presentation/dto/<Pascal>Dto` with a static `forViewer` | [V] |
| Events | `presentation/dto/event/<Pascal>EventType` (+ `<Pascal>EventData`) | [V] |
| Bots | `<pkg>/domain/bot/` (strategy), `presentation/bot/<Pascal>BotCoordinator` | `BullshitBotCoordinator`, `BatailleCorseBotCoordinator` [V] |
| Spring configuration | `config/<Pascal>Config.java` | `ThreeMenMorrisConfig`, `KoboConfig` [V] |
| Bean names | `<camel>Factory`, `<camel>StateBroadcaster`, `<camel>GameActions`, `<camel>LifecycleBroadcaster` | every `config/*Config.java` [V] |
| Spring properties | `<pkg>.<feature>.<key>` (kebab-case keys) | `bullshit.bots.delay-min-ms`, `bataillecorse.bots.extra-delay-ms` [V]; `threemenmorris.bots.*` planned in the Morris v2 plan |
| Option label keys | `options.<camel>.<optionKey>` in `OptionDescriptor.labelKey` | `options.bullshit.claimMode` [V]; `options.threeMenMorris.variant` planned in the Morris v2 plan |
| Start-check message | `"This room is not a <Display name> game"` | `ThreeMenMorrisWebSocketController.start` [V] |
| Tests | mirror the main package; integration tests end in `IT` (or `Test`) or they do not run | `ThreeMenMorrisWebSocketControllerIT` [V]; see `CLAUDE.md` |
| Test fixture | `<pkg>/presentation/<Pascal>Table.java` over `SessionTable` | `KoboTable`, `ThreeMenMorrisTable` [V] |

### Shared backend building blocks to reuse (do not copy a game)

| Block | Path | Role |
|-------|------|------|
| `GameFactory` | `game/GameFactory.java` | `gameType()`, `minPlayers()`, `maxPlayers()`, `create`, `supportsBots()` (default `false`), `optionDescriptors()`, `seatOptionDescriptors()` |
| `GameFactories` | `sessionmanagement/core/application/GameFactories.java` | built in `AppConfig` from every `GameFactory` bean: a new game never edits it |
| `SeatCommands` | `presentation/command/SeatCommands.java` | authenticates the seat, runs the command, answers a refusal to the sender alone |
| `SeatGameRest` | `presentation/rest/SeatGameRest.java` | state / join / play-again behind the three REST endpoints |
| `SeatStateBroadcaster<G, Dto>` | `presentation/state/SeatStateBroadcaster.java` | one DTO per seat; declared as a bean in `config/<Pascal>Config.java` |
| `SeatLifecycleBroadcaster<G>` | `presentation/state/SeatLifecycleBroadcaster.java` | presence and forfeit; the `GameLifecycleBroadcaster` bean of the game |
| Test kit | `src/test/.../presentation/support/` | `RecordingMessaging`, `StompTestClient`, `SessionTable`, `StartedRooms` |

Copy the layout of `threemenmorris/` + `config/ThreeMenMorrisConfig.java` (the newest, smallest seat game),
never Bataille Corse's.

## 4. Frontend names

Paths relative to `frontend/src/`.

| What | Name | Verified examples |
|------|------|-------------------|
| Model folder | `model/<pkg>/` (state types `<Short>State.ts`, events, pure helpers) | `model/threemenmorris/`, `model/kobo/`, `model/bullshit/` [V] |
| Session | `application/<Pascal>Session.ts`, subclass of `SeatGameSession`, exporting `<ID_IN_UPPER_SNAKE_CASE>_SESSION_CONFIG` | `KOBO_SESSION_CONFIG` in `application/KoboSession.ts`, `BULLSHIT_SESSION_CONFIG` [V]; Morris sits in `application/threemenmorris/` [D: flat file] |
| Store | `state/<Pascal>.store.ts` exporting `use<Pascal>Store`, built on `useSeatRoom` | `state/ThreeMenMorris.store.ts` → `useThreeMenMorrisStore` [V] |
| Bootstrap | `composables/use<Pascal>Bootstrap.ts` over `useSeatGameBootstrap` | `useKoboBootstrap`, `useBullshitBootstrap` [V]; Morris is `useMorrisBootstrap` [D: full prefix] |
| Screen | `view/<pkg>/<Pascal>GameScreen.vue` | `view/threemenmorris/ThreeMenMorrisGameScreen.vue`, `view/kobo/KoboGameScreen.vue` [V] |
| Components | `components/<pkg>/` | `components/bullshit/` [V]; Morris uses `components/morris/` [D: `<pkg>`] |
| Rules texts | `<camel>: RulesMessages` in `locales/Messages.ts` | `bullshit`, `kobo`, `threeMenMorris` [V] |
| Screen texts | `<camel>Ui` | `bullshitUi`, `koboUi`, `threeMenMorrisUi`, `batailleCorseUi` [V] |
| Option texts | `options.<camel>` (keys match the backend `labelKey`) | `options.bullshit`, `options.bataillecorse` [V] |
| Unit tests | next to the file, `<File>.test.ts` (Vitest) | [V] |
| Animation of a table | `model/<pkg>/motion.ts` (anchor type `<Pascal>Anchor`, pure plans), `model/<pkg>/tempo.ts` (its numbers), over the shared `animation/` module | `PresidentAnchor` in `model/president/motion.ts` + `tempo.ts`, `BullshitAnchor` [V]; Kobo's anchor is `MotionAnchor`, Bullshit keeps its numbers in `motion.ts` [D: `<Pascal>Anchor`, `tempo.ts`] |

Components internal to a game's folder may use a shorter name (`MorrisBoard.vue`); the extension points above
(session, store, bootstrap, screen, config export) use the full `<Pascal>` prefix [D].

### Shared frontend building blocks to reuse

| Block | Path | Role |
|-------|------|------|
| `GameDefinition`, `GAMES`, `findGame` | `model/games.ts` | the registry; `supportsBots` decides whether the lobby offers "Add bot" |
| `SeatGameSession` + `SeatGameSessionConfig` | `application/SeatGameSession.ts` | lobby, join, rehydrate, start, play again; config = `gameType`, `restBase`, `tokenKeyPrefix`, `startDestination` |
| `useSeatRoom` | `composables/useSeatRoom.ts` | the store body of a seat game |
| `useSeatGameBootstrap` | `composables/useSeatGameBootstrap.ts` | screen bootstrap: rehydrate from the stored token, redirects |
| `useGameRooms` | `composables/useGameRooms.ts` | key → `{ create, join }` for the Game home and the join page |
| `gameChoice` | `locales/gameChoice.ts` | picker texts of a game; throws when missing |
| Shared chrome | `components/` (`GameTimer`, `RulesPanel`, `EndGameOverlay`, `DisconnectOverlay`, `ForfeitBanner`, `lobby/GameLobby`…) | timer, rules, Back, overlays, lobby |
| Card animation | `animation/` (import the barrel `animation/index.ts` only): `useTableMotion`, `defineAnchors`, `CardMotionLayer`, `useDisplayedState`, `useTableSettled`, `useAnimationSpeed` + `AnimationSpeedButton`… | flights, rings, burst queue, speed, reduced motion, texts that follow the picture; guide `animation/README.md`, map `docs/architecture/card-animation.md` |

Do not start from Bataille Corse (`application/GameSession.ts`, `view/alpha/`): it stays outside the generic
session (see `docs/architecture/new-game-retrospective-three-men-morris.md`, F10).

## 5. Legacy names (do not copy)

### Bataille Corse: the only id alias

Bataille Corse predates the convention. Its **server id is `bataille-corse`, its frontend registry key is
`bataillecorse`**: this split is the only alias between the server game type and the registry key [V].

| Uses `bataille-corse` | Uses `bataillecorse` | Unprefixed (no game segment) |
|-----------------------|----------------------|------------------------------|
| `BatailleCorseFactory.GAME_TYPE`; `GameSession.ts` (`GAME_TYPE`); Pinia id `bataille-corse-store`; `ScriptedGameType` in `stompPlayer.ts` | Java package `bataillecorse` (normal `<pkg>`); STOMP `/app/bataillecorse/start`; properties `bataillecorse.bots.*`; label key `options.bataillecorse.tier`; `GameKey`, routes `/games/bataillecorse/...`, `gamePicker.games.bataillecorse`, `options.bataillecorse`; Cypress `bataillecorse-*.cy.ts` | REST `/api/game/{id}`, `/join`, `/play-again`; STOMP `/app/send`, `/app/slap`, `/app/grab`; token key `tokens:<gameId>`; room route name `room`; screen `view/alpha/GameScreen.vue`; session `application/GameSession.ts`; bootstrap `useGameBootstrap`; rules texts at `rules` |

`stompPlayer.ts` maps the REST difference explicitly (`joinPath`). Bataille Corse also keeps its own
`BatailleCorseStateBroadcaster` and `BatailleCorseLifecycleBroadcaster` instead of the seat helpers.

### Other deviations

| Game | Deviation | Convention |
|------|-----------|------------|
| Bullshit | STOMP commands `/app/discard`, `/app/callBullshit` (start is `/app/bullshit/start`) | `/app/<id>/<command>` |
| Three Men's Morris | `MorrisSession`, `MORRIS_SESSION_CONFIG`, `useMorrisBootstrap` (domain `MorrisEvent`, `MorrisResult` and model `MorrisState` are internal names, allowed) | full `<Pascal>` prefix on extension points |
| Three Men's Morris | `application/threemenmorris/MorrisSession.ts` | `application/<Pascal>Session.ts` [D] |
| Three Men's Morris | `components/morris/`, Cypress `morris-*.cy.ts`, locale `morrisBoard` | `components/<pkg>/`, `<id>-*.cy.ts`, texts under `<camel>Ui` |

## 6. Decisions to confirm

The rules below are choices made in this document where the code is not uniform or has a single example.
They follow the majority of the existing games; the owner may overturn any of them.

1. **Frontend game folders use `<pkg>` (`threemenmorris`), not `<id>`.** The retrospective's R1 proposed the
   kebab id for folders; the code uses `<pkg>` in `model/`, `application/` and `view/` for Morris, and Java
   packages cannot hold hyphens, so one compact form serves both sides.
2. **The session is a flat `application/<Pascal>Session.ts`** (Bullshit, Kobo), not a per-game folder
   (Morris). The per-game manifest of R6 may later regroup a game's files; until then flat is the majority.
3. **Rules texts stay at `<camel>`, screen texts at `<camel>Ui`.** R1 proposed `<camel>Rules`; every seat game
   uses the bare `<camel>` today (`bullshit`, `kobo`, `threeMenMorris`), so renaming would touch three games.
4. **Extension points carry the full `<Pascal>` prefix** (`ThreeMenMorrisSession`, not `MorrisSession`);
   names internal to the game's own folder are free.
5. **Option label keys and their texts use `<camel>`** (`options.threeMenMorris.variant`, as the Morris v2
   plan already does), not `<pkg>`; `options.bataillecorse` is the legacy form.
6. **Hotkey storage key `<id>.hotkeys`**, from the single example `bullshit.hotkeys`.
7. **Cypress spec files are prefixed by `<id>`** (`three-men-morris-*.cy.ts` for a new spec), not by a short
   name.
8. **Legacy names are not renamed for symmetry.** In particular Bataille Corse keeps its alias and unprefixed
   endpoints, and Morris keeps its `Morris*` names unless M1 or a later Morris PR renames them on its own.
