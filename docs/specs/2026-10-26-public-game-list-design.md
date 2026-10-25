# Public game list: design

Produced by Opus 5.5 (Claude Opus 5.5), 2026-10-26. Plan: `docs/plans/2026-10-26-public-game-list-plan.md`.
Design canvas (owner's choice: option A, "Stacked"): https://claude.ai/artifact/FVEbikx3WGMDbqrNWFhV4X

Owner request: "in the Join game option of each game, have an option to see every game that is running and
join it, if there is room in it."

## Key decisions

1. **Every lobby is public by default and the host can make it private.** The host option "Private game" is
   off by default and lives in the lobby's Customise disclosure. It can also be set **at creation** (a
   `privateGame` boolean on `POST /api/lobby`, default `false`, so existing callers are unaffected), which
   closes the moment where a new lobby would be public. A private game never appears in the list;
   people still join it through the invite link. Privacy belongs to `sessionmanagement` core (a session
   concern), not to the games' `GameOptions`.
2. **The list shows every public game of the game type, joinable or not.** A row shows the host's display
   name, the taken seats out of the maximum, the number of bots and a state (Open, Full, In play). Full and
   started games are greyed. An **"Open games only" toggle, on by default**, hides them; turning it off brings
   them back.
3. **A started game cannot be joined, even with a free seat.** It is listed as "In play" with no Join
   button. This is the current session rule (`SessionService.join` refuses any game whose `Game` exists).
   **Decided by the owner (2026-10-26)**: started games stay non-joinable (joining mid-game would need every
   game's domain to accept a newcomer).
4. **The list updates live over STOMP.** A REST snapshot `GET /api/lobbies/{gameType}` is fetched first.
   The client then subscribes to `/topic/lobbies/{gameType}`, which carries the **whole list** (never
   deltas), at most once every 500 ms per game type. The client fetches the snapshot again after every
   reconnect.
5. **Small limits, sized for the real load.** At most 50 games are listed per game type; the payload says
   when more exist. Lobbies whose humans are all offline are not listed. The existing 30-minute idle sweep
   still removes abandoned lobbies. The expected real load is **0 to 5 games**, so nothing beyond this is
   built (no paging, no search, no rate limiter).

## Context (state of `main` at 400fd217)

- `GameJoinView` (`/:game/join/:id?`) renders `JoinGameForm`. The form has a Game ID field (it takes an id or
  a whole link), a Your name field, Join game and Back. Join calls `useGameRooms()(game).join(id, name)`, then
  routes to the room. Refusals: 404 (lobby gone), 409 `full`, 409 `started`.
- Lobbies live in `InMemorySessionRepository` (`sessionGames`, `games`, `lastActivityAt`). A lobby is a
  `SessionGame` with no `Game`; a started game has both. `GameCleanupService.sweep()` runs every minute and
  evicts a game after 30 minutes of inactivity (2 minutes once finished). It tells the
  `GameEvictionListener`s. A lobby whose last human leaves is removed by `SessionService.leaveLobby`, which
  calls `lobbyRemovalListener`.
- Every lobby mutation goes through `SessionService`, under its single monitor: create, join, play again,
  add or remove bot, remove seat, leave, rename, options, seat options, connection mark, start.
- STOMP: a simple broker on `/topic`, the `/app` prefix, publish order preserved. Per-seat topics are guarded
  by `SeatSubscriptionInterceptor`; other topics are open. The frontend uses one `WebSocketService`
  (SockJS on `/connect`) that resubscribes on reconnect.
- Frontend game keys differ from server game types for one game: `bataillecorse` ↔ `bataille-corse`. The
  other keys are equal: `bullshit`, `kobo`, `three-men-morris`, `president`, `mangola`.
- `LobbyCustomise` is used today only by President and Mangola (through the lobby's options slot). The other
  four lobbies have no Customise disclosure.

## Product rules

### What is listed

A lobby or game is listed for its game type when **all** of these hold:

- it is **public** (`visibility = PUBLIC`);
- at least one human seat is **connected**. A lobby whose humans are all offline counts as abandoned: it is
  not listed, and the idle sweep removes it later. A started game is listed whatever its connections are
  (presence and forfeit already handle it);
- it is not evicted or removed.

The state of each listed entry:

| State | Condition | Join button |
| --- | --- | --- |
| `OPEN` | no `Game` yet and at least one free seat | yes |
| `FULL` | no `Game` yet and no free seat | no |
| `IN_PLAY` | a `Game` exists, finished or not, until it is reopened (Play again) or evicted | no |

A finished game stays `IN_PLAY` until Play again reopens it, at which point it becomes `OPEN` again, or the
sweep evicts it 2 minutes later. Detecting "finished" would need a hook in every game; this is not worth it.

### Order and cap

- Order: `OPEN` first, newest first (`openedAt` descending), then `FULL` and `IN_PLAY` together, newest first.
  Ties are broken by game id, so the order is stable.
- **Cap: 50 entries per game type** (`PublicLobbies.MAX_LISTED = 50`), taken after sorting, so the open games
  win. The payload carries `total` (all public entries) and `truncated` (`total > 50`). When truncated, the UI
  shows under the list "Showing 50 of {total} games." Creating a lobby is never refused because of the cap.
- `openedAt` is the time the lobby was first saved. Play again keeps it: the room is the same.

### Data in a list entry (privacy)

Only: `gameId`, `gameType`, `hostName` (the display name of seat 0, which may be the default "Player 1"),
`joined` (taken seats, bots included), `bots`, `maxPlayers`, `state`. **Never**: a token, a seat number,
another player's name, the options, the connection state, an IP address or a timestamp. The `gameId` is
already the join capability that the invite link carries; publishing it for public games is the feature.
Private games' ids are never published.

### Private game

- The host toggles "Private game" in the lobby (`/app/lobby/setPrivate`, `{gameId, token, private}`). The
  rule is host only and lobby only, like the other host options. Everybody receives a new lobby view, which
  gains `privateGame: boolean`. The list is updated at once.
- The setting survives Play again (the room reopens with the same visibility) and option changes.
- The other players see a read-only "Private game" chip next to the invite link. The invite hint changes:
  "Only people with this link can join." when private, otherwise "Listed in Public games. Anyone can join
  while a seat is free."
- **At creation**: `POST /api/lobby` accepts `privateGame` (optional boolean, absent means `false`).
  `SessionService.createRoom(gameType, hostName, options, privateGame)` is added next to the existing
  overloads, which keep public as the default. The lobby is then never public, not even for a moment. On the
  frontend, `GameRoomClient.create(name, privateGame?)` and each store's `create` gain the optional argument.
  The Game home shows a "Private game" checkbox next to New game (off by default, with the same hint), and
  the lobby switch starts from that value.

## UI (option A, Stacked)

The `JoinGameForm` panel, top to bottom:

1. Title block (game name, "Join game").
2. **Your name** (moved to the top: both ways of joining use it). *Visible change.*
3. Game ID, then Join game: unchanged behaviour. An id from the route is pre-filled, as today.
4. Separator "or pick a public game".
5. **Public games** section (`<section aria-labelledby>`):
   - Heading "Public games" with a count: "{open} open" (and "· {total} total" when the toggle is off). The
     live dot is static, never pulsing.
   - Toggle "Open games only" (a checkbox with its label, on by default, 44 px target). The choice is
     remembered per device (`localStorage`, wrapped in try/catch).
   - **The list sits in a scroll region capped in height**: `max-height: min(45vh, 420px)`, `overflow-y:
     auto`, `tabindex="0"`, `role="region"`, `aria-labelledby` the heading. The page, Back and the form stay
     reachable on a phone even with 50 entries.
   - `<ul>` of rows. Each row shows the host name (cut with an ellipsis), a state badge in words, seat
     dots (filled for humans, squared for bots, hollow for free seats; `aria-hidden`), and "{joined} /
     {max} players" with "· {n} bot(s)" when there are bots. An open row has a **Join** button (44 px,
     `aria-label` "Join {host}'s game, {joined} of {max} players"). Full and started rows have no button and
     are not focusable.
   - States: loading ("Looking for games…", `aria-busy`); empty ("No public {game} game right now." plus a
     "New game" link to the game home); connection lost ("Live updates paused. Reconnecting…", `role=status`,
     list dimmed but usable); truncated footer.
6. Back.

Join from a row uses the name field. It calls the same `join(id, name)`, then routes to the room. A refusal
shows the same messages as the form (full, started, not found), in the row, `role="alert"`. The next list
push redraws the row.

**Accessibility and motion.**

- One polite live region (`useLiveAnnouncements` or an equivalent) announces changes **in batches**: at most
  one message every 2 seconds, which sums up what happened ("1 new game. 3 open."). It says nothing about the
  first snapshot or a reconnect snapshot.
- If the focused row disappears, focus moves to the list heading (which has `tabindex="-1"`).
- New rows fade in over 150 ms; with `prefers-reduced-motion: reduce`, nothing animates.
- State is always given in words, never by colour alone. Text contrast is at least 4.5:1 on the panel, the
  greyed rows included (colour, not opacity).

**Lobby.** In the four lobbies with no Customise disclosure (Bataille Corse, Bullshit, Kobo, Three Men's
Morris), `GameLobby` adds one `LobbyCustomise` that holds only the Private game switch. President and Mangola
put the switch at the top of their own Customise panel, through a new `privacy` prop of the options slot.
The control is `LobbyPrivateOption`: a `role="switch"` button labelled "Private game", with
`aria-describedby` pointing at its hint "Hidden from the public list. Only people with the invite link can
join." It is editable by the host and disabled for the others.

## Backend design

### Session core (`sessionmanagement/core`)

- `domain/LobbyVisibility` enum `PUBLIC | PRIVATE`. `SessionGame` gains a mutable visibility holder (like
  `version`), `visibility()` and `setVisibility(...)`, default `PUBLIC`. The two `new SessionGame(...)` calls
  in `SessionService` (`playAgain`, `updateOptions`) carry it over.
- `application/PublicLobby` record `(String gameId, String gameType, String hostName, int joined, int bots,
  int maxPlayers, PublicLobbyState state, Instant openedAt)` and `PublicLobbyState` enum
  `OPEN | FULL | IN_PLAY`. `PublicLobbyList` record `(String gameType, List<PublicLobby> lobbies, int total,
  boolean truncated, long version)`. `openedAt` is used for sorting and is **not** serialised.
- `application/PublicLobbies`: a pure function `list(gameType, entries, max)` that applies the rules above
  (filter, state, sort, cap). It is unit tested on its own.
- `SessionService`:
  - `publicLobbies(String gameType)` builds the `PublicLobbyList` under the monitor. An unknown game type
    fails with `IllegalArgumentException`, which the REST layer turns into a 404.
  - `setPrivate(GameId, hostToken, boolean)` is host only and lobby only (`openLobbyOwnedBy`), bumps the
    version and returns the lobby views.
  - `setPublicLobbiesListener(Consumer<String> gameTypeChanged)` works like `setLobbyRemovalListener`. It is
    called **after** the monitor is released, with the game type, by every use case that changes what the list
    shows: create, join, play again, add or remove bot, remove seat, leave (and lobby removal), rename (the host
    name), set private, start, and the connection mark (only when it changed). Options and seat options do not
    call it, since nothing listed depends on them.
  - Eviction: an eviction listener (`GameEvictionListener`) fires the same notification. The repository gives
    the game type of an evicted id before removing it, or the listener notifies every known type. With 6 types,
    this is the simplest choice.
- `SessionRepository` gains `List<SessionGame> sessionsOfType(String gameType)` and `Instant openedAt(GameId)`.
  The in-memory adapter records `openedAt` on the first `saveLobby` of an id, and clears it on `remove`.
- `LobbyView` gains `privateGame` (boolean).

### Presentation

- `PublicLobbyRestController`: `GET /api/lobbies/{gameType}` returns `PublicLobbyListDto` (200) or 404 for an
  unknown type. No token is needed.
- `PublicLobbyBroadcaster` subscribes to the listener. **Coalescing**: per game type, at most one send every
  500 ms (a trailing send through the `TaskScheduler`, so the last change is always sent). It sends the whole
  `PublicLobbyListDto` to `/topic/lobbies/{gameType}`. `version` is a per-type counter, so a client drops a
  list older than the one it holds.
- `LobbyController` gets `@MessageMapping("/lobby/setPrivate")` through its existing `handle(...)`. A refusal
  goes to the sender alone, as for the other commands.
- `SeatSubscriptionInterceptor` is unchanged: `/topic/lobbies/*` is public on purpose.
- ArchUnit: every new type stays in `sessionmanagement` (core) or `presentation`. No game package learns about
  the list.

### Abuse and limits

- 50 entries per type, so a payload stays under about 6 KB.
- At most 2 pushes per second per type, whatever the number of changes.
- Abandoned lobbies (no human online) are hidden, and the 30-minute idle sweep removes them.
- Not built, because the expected load is 0 to 5 games: a per-IP limit on `POST /api/lobby` or on
  subscriptions, paging, search. The plan's integration task records this in `docs/architecture/context-map.md`
  as a known limit.

### Observability and logs

- `application.properties` sets `org.springframework.messaging` and `org.springframework.web.socket` to
  `DEBUG`. With this level, STOMP frames, and so the list payloads (host names), may land in the server logs.
  Lobby frames already carry every player's name today, so the list adds no new kind of data, but it does send
  names to anyone who subscribes. **Decided (owner, 2026-10-26)**: these two loggers drop to `INFO` in
  `application.properties` (production). `DEBUG` stays in `application-test.properties` (e2e stack) and in the
  dev stack through environment variables in `docker-compose.dev.yml`
  (`LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_MESSAGING=DEBUG`, `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_SOCKET=DEBUG`).
  There is no `dev` Spring profile today, and none is added. This is done in B2.
- New log lines carry only the game type and counts (for example `Public lobbies of kobo: 3 listed`, at DEBUG),
  **never** a host name or a game id.

### Privacy and legal

With this feature, the host's display name is shown to anyone who opens the join page of that game. The data
is minimal (display name, counts, state, game key and id), lives only in memory and disappears with the lobby.
A privacy page is being added separately (PR `feat/privacy-page`, not merged when this was written). The
integration chunk I updates that page to cover the public list: what is shown publicly, that Private game
(at creation or in the lobby) keeps a game unlisted, and that nothing is stored. If that PR is still open when I
runs, I adds the paragraph on top of its branch, or opens a follow-up once it is merged.

## Frontend design

- `model/games.ts`: `GameDefinition.serverType` (`'bataille-corse'` for `bataillecorse`, the key otherwise).
- `model/lobby/PublicLobby.ts`: `PublicLobby`, `PublicLobbyList`, `PublicLobbyState` (mirroring the DTO).
  `LobbyView.privateGame?: boolean`.
- `service/WebSocketService.ts`: `subscribeToPublicLobbies(gameType, onMessage, onConnected)` and its
  unsubscribe. It survives reconnects like the seat subscription, and `onConnected` fires after each
  (re)subscription.
- `composables/usePublicLobbies(game)`: fetches the snapshot, subscribes, drops lists with an older
  `version`, refetches on reconnect, exposes `lobbies`, `total`, `truncated`, `loading`, `online`, and the
  batched announcement text. It unsubscribes on unmount.
- `components/PublicGameList.vue`: the section described above. Its props are `lobbies`, `total`,
  `truncated`, `loading`, `online`, `game`, plus a join callback; it owns no store.
- `JoinGameForm.vue`: name first, then the existing form, then `PublicGameList` (option A). `GameJoinView`
  wires `usePublicLobbies`.
- Lobby: `components/lobby/LobbyPrivateOption.vue`; `GameLobby.vue` (own Customise for the four games, a
  `privacy` slot prop for the two others); `LobbyInvite.vue` (chip and hint); `useLobbyActions.setPrivate`;
  each game store and screen wires `set-private` like `set-option`.
- All new texts go into `locales/Messages.ts` and `locales/en.ts` (`join.publicGames.*`, `lobby.privateGame*`).

## Testing

- Backend unit tests:
  - `PublicLobbies`: filters, states, sort, cap and `truncated`.
  - `SessionGame` visibility.
  - `SessionService`: `setPrivate` (host only, lobby only, survives play again), and the listener fired for each
    mutation in the list above and not for options.
- Backend `*IT`:
  - REST snapshot (200, 404, no token in the JSON, a private game absent).
  - STOMP: the topic receives an updated list after a join, coalesced within 500 ms.
  - `setPrivate` command.
- Frontend (Vitest):
  - `usePublicLobbies`: version ordering, reconnect refetch, batching.
  - `PublicGameList`: rows, toggle, empty, loading and offline states, focus fallback, aria labels.
  - `JoinGameForm`: field order, join from a row, row errors.
  - `LobbyPrivateOption`, and `GameLobby` placement for the 4 + 2 games.
  - `useLobbyActions.setPrivate`.
- Cypress (integration chunk only): `public-game-list.cy.ts`. A host creates a Kobo lobby; a second visitor
  sees it live on `/kobo/join` and joins from the row; the host turns Private on and the row disappears; Open
  games only hides a full lobby.

## Out of scope

Joining a started game (decided: not allowed); listing across game types; search and paging; a per-IP rate
limit; the privacy page itself (its own PR; I only adds the public-list paragraph).

## Amendments (owner, 2026-10-26)

1. `privateGame` at creation (additive, default false), in P0's signatures.
2. STOMP loggers drop to INFO outside test and dev (B2).
3. The privacy page (`feat/privacy-page`) is updated in I to mention the public list.
4. Started games stay non-joinable: decided.
