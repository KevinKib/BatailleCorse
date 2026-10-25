# Public game list: plan

Produced by Opus 5.5 (Claude Opus 5.5), 2026-10-26. Spec: `docs/specs/2026-10-26-public-game-list-design.md`.
Design canvas (option A chosen by the owner): https://claude.ai/artifact/FVEbikx3WGMDbqrNWFhV4X

**Approved by the owner on 2026-10-26, with the amendments below.**

## Key decisions (from the spec)

1. Every lobby is public by default; "Private game" (host, in Customise) hides it from the list. It is a
   session concern in `sessionmanagement/core`, not a game option. It can also be set **at creation**
   (`privateGame` on `POST /api/lobby`, default false, backward compatible), so a lobby is never public by
   accident.
2. The list shows every public game of the type: Open (Join), Full, In play (greyed). "Open games only" is on by
   default. Order: open newest first, then the others. **At most 50 entries**, with `total` and `truncated`.
3. **A started game cannot be joined, even with a free seat**: the current rule, decided by the owner on
   2026-10-26.

**Amendments approved by the owner (2026-10-26):**
- `privateGame` at creation: the signatures are added in P0, the behaviour in B1 and B2, the Game home checkbox
  in F2.
- STOMP loggers go to INFO outside test and dev (B2).
- The privacy page (`feat/privacy-page`) is updated in I.
- Started games stay non-joinable.
4. Live updates: a REST snapshot plus a whole-list push on `/topic/lobbies/{gameType}`, coalesced to one push per
   500 ms per type, and a refetch on reconnect.
5. Sized for 0 to 5 real games: no paging, search or rate limiter. Lobbies with no human online are hidden, and
   the existing 30-minute sweep removes them. List data: host display name, counts, state, game key and id only.

## Order and parallelism

```
P0 (interfaces, < 30 min) ──┬──> B1 (session core) ───────┐
                            ├──> B2 (presentation) ───────┤
                            ├──> F1 (join page list) ─────┼──> I (integration, e2e, docs)
                            └──> F2 (lobby private option) ┘
```

- **Series:** P0 first. It only adds types, stubs and texts, and removes nothing.
- **In parallel after P0:** B1, B2, F1 and F2, each in its own worktree with its own sub-agent. B2 codes against
  the `SessionService` signatures that P0 added (it unit tests with a fake); its STOMP `*IT` that needs B1's real
  behaviour is written in B2 but tagged to run green only after B1 (see B2). F1 and F2 code against the DTO
  shapes with fixtures, not against a running backend.
- **I** needs all four.

**Files at risk of conflict** (touch minimally; rebase on `origin/main` before the final push):

| File | Chunks | How the risk is kept small |
| --- | --- | --- |
| `sessionmanagement/core/application/SessionService.java` | P0 (stubs), B1 (bodies) | B2 never edits it, it only calls it |
| `sessionmanagement/core/application/LobbyView.java` | P0 (field, `false`), B1 (real value) | single field |
| `presentation/LobbyController.java` | B2 only | |
| `frontend/src/locales/Messages.ts`, `en.ts` | **P0 only** | every new text is added in P0; F1 and F2 add none (if one is missing, the chunk adds it at the end of the `join` or `lobby` block and says so) |
| `frontend/src/model/lobby/LobbyView.ts`, `model/games.ts` | P0 only | |
| `frontend/src/service/WebSocketService.ts` | F1 only | F2 publishes through the existing `publish` |
| `frontend/src/components/lobby/GameLobby.vue`, `LobbyInvite.vue` | F2 only | |
| `frontend/src/components/JoinGameForm.vue`, `view/GameJoinView.vue` | F1 only | |
| 6 game stores and 6 game screens | P0 (`create` signature), then F2 | one line each (`setPrivate`, `@set-private`); P0 is merged before F2 starts |
| `presentation/LobbyRestController.java`, `application*.properties`, `docker-compose.dev.yml` | B2 only | |
| `components/GameHome.vue`, `composables/useLobbyCreator.ts` | F2 only | |
| privacy page files (`feat/privacy-page`) | I only | |
| `docs/architecture/context-map.md` | I only | |

**Tests (every PR):** backend `cd backend && mvn -s ../deploy/docker/settings.xml test`; frontend `cd frontend &&
npm test && npx vite build`. Strict TDD: each "tests first" bullet is written red, run alone and seen to fail
for the right reason, then made green. Cypress only in I (the new spec, plus `game-home.cy.ts` and one lobby
spec, per the issue #312 rule). A dev environment only for I (manual check on two browsers).

**Conventions:**

- Each chunk opens a draft PR at its start with its real number and runs `gh pr ready` at the end.
- Branches: `feat/public-lobbies-interfaces` (P0), `feat/public-lobbies-core` (B1), `feat/public-lobbies-api`
  (B2), `feat/public-game-list-ui` (F1), `feat/private-game-option` (F2), `feat/public-game-list` (I).
- PR titles are conventional; PR bodies are in French.
- Backend paths are relative to `backend/src/main/java/org/kevinkib/cardgames/` (tests under the same path in
  `src/test`); frontend paths are relative to `frontend/src/`.

## P0: interfaces (series, < 30 min)

Branch `feat/public-lobbies-interfaces`. Adds, removes nothing, changes no behaviour.

- Backend:
  - `sessionmanagement/core/domain/LobbyVisibility.java` (enum).
  - `sessionmanagement/core/application/{PublicLobby,PublicLobbyState,PublicLobbyList}.java` (records and enum,
    as in the spec).
  - `SessionService`: `publicLobbies(String)` returns an empty `PublicLobbyList` (version 0);
    `setPrivate(GameId, String, boolean)` throws `UnsupportedOperationException`;
    `setPublicLobbiesListener(Consumer<String>)` stores the listener and never calls it.
  - `LobbyView.privateGame` is always `false`, with the callers updated.
  - `presentation/dto/PublicLobbyListDto.java` and `PublicLobbyDto.java` (no `openedAt`), with a static
    `from(PublicLobbyList)`.
  - `presentation/api/LobbyPrivatePayload.java` `(gameId, token, boolean privateGame)`.
  - Constant `PublicLobbyTopics.destination(gameType)` = `/topic/lobbies/{gameType}`.
  - Creation, additive:
    - `CreateLobbyPayload` gains `Boolean privateGame` (null means false).
    - `SessionService.createRoom(gameType, hostName, options, boolean privateGame)` is a new overload. The
      existing overloads delegate with `false`. In P0 the flag is accepted but not stored yet (B1 stores it).
- Frontend:
  - Creation, additive:
    - `GameRoomClient.create(name?, privateGame?)` and each store's `create(name?, privateGame = false)`.
    - The store sends `privateGame` in the `POST /api/lobby` body.
    - The existing callers are unchanged.
  - `model/lobby/PublicLobby.ts` (types).
  - `LobbyView.privateGame?: boolean`.
  - `GameDefinition.serverType` (with a test that `bataillecorse` maps to `bataille-corse`).
  - Every new text in `Messages.ts` and `en.ts` (`join.publicGames.{title, openCount, totalCount, openOnly,
    join, joinAria, open, full, inPlay, players, bots, loading, empty, emptyHint, newGame, offline,
    truncated, announce}`, `join.or`, `lobby.{privateGame, privateHint, privateChip, inviteHintPrivate,
    inviteHintPublic}`, plus the Game home checkbox label and hint), with the existing locale tests extended.
- Tests: the existing suites stay green, plus the two small tests above.

## B1: session core (parallel)

Branch `feat/public-lobbies-core`. Files: `sessionmanagement/core/**`, `GameCleanupService` wiring (the
listener), `InMemorySessionRepository`.

- Tests first:
  - `PublicLobbiesTest`: states (`OPEN`, `FULL`, `IN_PLAY`, finished-but-not-reopened counts as `IN_PLAY`);
    private excluded; no human online excluded (lobby), started game kept; sort (open newest first, then the
    others newest first, tie by id); cap 50 with `total` and `truncated`; bots counted in `joined` and in
    `bots`; host name of seat 0 (default "Player 1").
  - `SessionGameTest`: visibility defaults to `PUBLIC` and is kept by `vacate` and `claimSeatAheadOfBots`.
  - `SessionServiceTest`:
    - `setPrivate` is host only, lobby only, bumps the version, and is kept through `playAgain` and
      `updateOptions`.
    - `createRoom(..., true)` creates a private lobby, never listed (not even before its first view);
      the old overloads stay public.
    - `LobbyView.privateGame`.
    - `publicLobbies` refuses an unknown type.
    - The listener fires once, with the type, after each mutation in the spec's list, after the monitor is
      released (assert from the listener that the monitor is not held). It does not fire for options, seat
      options or an unchanged connection mark.
  - `InMemorySessionRepositoryTest`: `openedAt` is set once, kept by `saveLobby` and reopen, and cleared by
    `remove`; `sessionsOfType`.
  - Eviction: an evicted lobby triggers the notification.
- Then the implementation, as in the spec. Use cases that already return views keep their signatures.

## B2: presentation (parallel)

Branch `feat/public-lobbies-api`. Files: `presentation/PublicLobbyRestController.java`,
`presentation/PublicLobbyBroadcaster.java` (new), `presentation/LobbyController.java` (`setPrivate`), the Spring
wiring (where `SessionService` beans are declared).

- Tests first:
  - `PublicLobbyBroadcasterTest` (fake clock or scheduler): 10 changes within 500 ms give exactly one send per
    type, with the latest list; two types do not block each other; `version` increases; the payload has no
    `openedAt` and no token.
  - `PublicLobbyRestControllerTest` (with a `SessionService` fake): 200 with the DTO, 404 for an unknown type.
  - `LobbyController` `setPrivate`: views to every seat; a refusal goes to the sender alone.
  - `PublicLobbyListIT` (Spring context, STOMP client):
    - The REST snapshot lists a created lobby.
    - A subscriber on `/topic/lobbies/kobo` receives the new list after a join.
    - A private lobby disappears.
    - The JSON never contains a token (assert on the raw frame).

    This IT depends on B1's bodies: B2 writes it and **marks it `@Disabled("needs B1")`** if B1 is not merged
    when B2 finishes. I removes the annotation.
- `LobbyRestController.create` passes `payload.privateGame()` (null means false) to `createRoom`. Tests first:
  a body with `privateGame: true` gives a lobby that is absent from the snapshot; a body without the field
  gives a listed one.
- No change to `SeatSubscriptionInterceptor`: `/topic/lobbies/*` is open.
- Logs:
  - One DEBUG line per push, with the type and count only.
  - STOMP loggers: in `backend/src/main/resources/application.properties`, `org.springframework.messaging`
    and `org.springframework.web.socket` go from DEBUG to INFO.
  - `application-test.properties` sets them to DEBUG (e2e stack).
  - `docker-compose.dev.yml` sets `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_MESSAGING=DEBUG` and
    `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SOCKET=DEBUG` on the backend.
  - No new Spring profile.
  - Check: the existing ITs stay green.

## F1: join page list (parallel)

Branch `feat/public-game-list-ui`. Files: `service/WebSocketService.ts`, `composables/usePublicLobbies.ts`
(new), `components/PublicGameList.vue` (new), `components/JoinGameForm.vue`, `view/GameJoinView.vue`, and their
tests. Load the `vue-best-practices` skill.

- Before changing `JoinGameForm`, list what it shows (title, subtitle, Game ID, Your name, error, Join, Back)
  and check that each one is still there after the change (global UI rule).
- Tests first:
  - `usePublicLobbies.test.ts`:
    - REST snapshot, then the topic.
    - An older `version` is dropped.
    - Refetch on reconnect (`onConnected`).
    - Unsubscribe on unmount.
    - Announcements batched (one per 2 s, none for the snapshot), with fake timers.
  - `PublicGameList.test.ts`:
    - Rows with name, badge in words, "{joined} / {max} players · {n} bot(s)".
    - Join only on open rows, with its `aria-label`.
    - The Open games only toggle (default on, remembered, `localStorage` failure tolerated).
    - Heading counts.
    - Scroll region (`role=region`, `tabindex=0`).
    - Empty, loading, offline and truncated states.
    - Focus moves to the heading when the focused row disappears.
    - No animation class under reduced motion (`matchMedia` stub).
  - `JoinGameForm.test.ts`:
    - Name field first.
    - The existing behaviour is unchanged (invite id pre-filled, errors).
    - Join from a row calls `join(id, name)` then routes.
    - A 409 `full` from a row shows the message in that row.
- Then the implementation (option A of the canvas, the look of `TitlePanel`).
- Uses only texts from P0.

## F2: lobby private option (parallel)

Branch `feat/private-game-option`. Files:

- `components/lobby/LobbyPrivateOption.vue` (new), `components/lobby/GameLobby.vue`,
  `components/lobby/LobbyInvite.vue`.
- `components/president/PresidentLobbyOptions.vue`, `components/mangola/MangolaLobbyOptions.vue`.
- `composables/useLobbyActions.ts`.
- The 6 stores (`setPrivate`) and the 6 game screens (`@set-private`).
- `components/GameHome.vue` and `composables/useLobbyCreator.ts`: a "Private game" checkbox next to New game,
  off by default, passed to `create(name, privateGame)` (signature from P0). Tests first in `GameHome.test.ts`.

Before changing `GameLobby`, `LobbyInvite` and `GameHome`, list what they show and check it afterwards.

- Tests first:
  - `useLobbyActions.test.ts`: `setPrivate(true)` publishes `/app/lobby/setPrivate` with
    `{gameId, token, privateGame: true}`.
  - `LobbyPrivateOption.test.ts`: `role=switch`, `aria-checked`, `aria-describedby`, editable for the host,
    disabled otherwise; it emits on click.
  - `GameLobby.test.ts`:
    - The four games without an options slot get a Customise that holds only the switch.
    - With an options slot, the slot receives a `privacy` prop and no second Customise is shown.
    - The invite chip and the invite hint follow `lobby.privateGame`.
  - `PresidentLobbyOptions` and `MangolaLobbyOptions`: the switch sits at the top of their Customise panel.
  - One store test (Kobo) proves `setPrivate` is wired. For the other five, a one-line check in their existing
    store test.
- Uses only texts from P0.

## I: integration (after B1, B2, F1, F2)

Branch `feat/public-game-list`.

- Rebase and merge the four branches. Remove `@Disabled("needs B1")` from `PublicLobbyListIT`.
- Run every suite (backend, frontend, build).
- Cypress `public-game-list.cy.ts`, on a shifted-port e2e stack:
  - A host creates a Kobo lobby; a second visitor (a separate session through `cy.session` or a second REST
    client) sees the row on `/kobo/join` live and joins from the row, landing in the lobby.
  - The host turns Private on, and the row disappears from the list.
  - A full lobby is hidden while Open games only is on and shown, greyed, when it is off.
  - Also run `game-home.cy.ts` and `kobo-lobby.cy.ts` once (issue #312 rule).
- Docs:
  - `docs/architecture/context-map.md`: the public lobby read model, topic, privacy and the known limits (no
    per-IP limit, 50 cap).
  - `README.md` if the API list is documented there.
  - **Privacy page** (from PR `feat/privacy-page`): add a paragraph on the public list. It covers what is shown
    (host display name, counts, state, game and id), that Private game (at creation or in the lobby) keeps a
    game unlisted, and that nothing is stored. Once that PR is merged, I edits the page on `main`; while it is
    open, I asks the orchestrator whether to wait or to open a follow-up.
- Cypress: a lobby created with Private game checked on the Game home never shows in the list.
- Manual check on a dev stack: two browsers, phone width 375 px and 1280 px. Check each item listed before the
  change, then close the stack after the merge.
