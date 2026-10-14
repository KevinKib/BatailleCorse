# E2E instability: root-cause investigation

Date: 2026-10-14. Scope: the Cypress suite (`frontend/cypress`), the stacks it runs against
(`docker-compose.e2e.yml`) and the client/server messaging it exercises. Follows #167 (four proven causes:
lobby command lost before the first STOMP connection, late hydrate after Leave, 800 ms route fade, Windows
standby) and the friction F3 / recommendation R11 of `new-game-retrospective-three-men-morris.md`. Part of #28.

Rule followed: measure first. Code changes were made only on a throwaway branch (`scratch/flakyroot-probe`,
never pushed) or outside the repository, except one one-line fix proven before/after and shipped separately
(#196, merged).

## 1. Method

- **Stack.** One isolated stack `bc-flakyroot` (`docker-compose.e2e.yml`, test profile) on offset ports
  front 5195 / back 8095 / debug 5025, `VITE_HMR_CLIENT_PORT=5195`, ports checked free with `netstat -ano` and
  owned per `docker ps`. Port override file kept outside the repository.
- **Runs.** One spec at a time, `npx cypress run --browser electron --config baseUrl=http://localhost:5195`,
  10 runs in a row, no `retries`, each wrapped in `timeout 600`, one log per run.
- **Samplers (outside the repository).**
  - every ~10 s: VM load (`/proc/loadavg` from a container), `docker stats` of every container, established TCP
    connections on the backend's 8080 and the frontend's 5173 (`/proc/net/tcp*`);
  - every 2 s on the host: connections held by the Cypress browser to the Cypress proxy, and by the Cypress
    server to `localhost:5195` (`netstat -ano` filtered on the `Cypress.exe` PIDs);
  - backend DEBUG log (`logging.level.org.springframework.web.socket=DEBUG` is already on in the test profile):
    every WebSocket / SockJS session opened and closed, with its transport and close code.
- **Probes.**
  - Cypress instrumentation (throwaway branch): number of `stompPlayer.ts` module instances and the size of each
    `openClients` set at the end of every test; a merged `kobo-table` + `kobo-table-turn` spec (as before #186
    split them).
  - Black-box STOMP order probe (Node script, outside the repository): one client sends N `/app/lobby/rename`
    in a burst on the raw `/ws` endpoint; the probe reads which name the server kept and the order of the
    broadcast lobby views (by `lobbyVersion`).
  - Vitest probe (throwaway branch): the real `WebSocketService` and `SeatGameSession` against a fake broker
    that only delivers a frame to subscriptions active at that moment, like Spring's simple broker.
- **Windows power log.** `Get-WinEvent` System log, Kernel-Power 506/507/566, matched against run logs.

Machine: AMD Ryzen 7 260 (8 cores / 16 threads), 15.3 GB RAM (0.63 GB free at the start), Windows 11 laptop.
Docker Desktop WSL2 VM: 16 CPUs, 7.39 GiB, no `.wslconfig` (WSL defaults).

## 2. Measurements

### 2.1 Failure rates (bc-flakyroot, Electron, 10 runs per spec, no retries)

| Spec | Tests | Valid runs | Failed | Run duration (wall) | VM load1 during runs (mean) |
|---|---|---|---|---|---|
| shared-lobby | 10 | 10 | 0 | 67-113 s | 16.8-21.7 |
| game-home | 14 | 10 | 0 | 55-86 s | 11.7-14.4 |
| bullshit-lobby-bots | 10 | 10 | 0 | 62-85 s | 13.1-17.3 |
| morris-game | 12 | 8 | 0 | 130-169 s | 12.2-17.4 |
| kobo-table | 8 | 10 | 0 | 60-63 s | 11.2-12.2 |
| kobo-table-turn | 4 | 10 | 0 | 32-38 s | 11.1-11.5 |
| **kobo-table + kobo-table-turn merged, before #196** | 12 | 3 | **3** (2 tests each) | 101-121 s | ~11 |
| **same, with the #196 fix** | 12 | 3 | 0 | 91-100 s | ~11 |

Invalid runs (not counted): morris-game run 5 (frozen 62 min, lid closed at 01:21:26, Kernel-Power 506
"Lid") and run 7 (Electron renderer crashed at 02:26:51, the second of a standby enter/exit at 02:26:51 and
02:26:54); a later `game-home` baseline run frozen by an "Idle Timeout" standby. In both frozen cases
`timeout 600` did not fire until the machine woke (3,739 s and 2,776 s wall): the watchdog's clock stops with
the machine.

Outside the investigation's own failures, every spec was green 10/10 on a stack shared with 1 to 3 other
idle stacks (VM load 11-22).

### 2.2 Scripted STOMP players were never closed (proven, fixed in #196)

- Instrumentation, merged Kobo spec, each line is the end of one test: `instances=2`, the set read by the
  `afterEach` of `support/e2e.ts` is always `0`, the set filled by the spec grows 1, 4, 9, 10, 13, 18, 19, 21,
  23, 29, 31, 32. Cypress bundles the support file and each spec separately, so `support/stompPlayer.ts` ran
  twice and the cleanup added by #167 emptied an empty set.
- Backend log, shared-lobby run 2: the 8 raw `/connect/websocket` sessions (scripted players) opened during the
  spec all close at 22:35:20.525-.531 with code 1001 (page going away), i.e. at the end of the spec file; none
  closes with 1000 after its test.
- Merged spec, before the fix, 3/3 runs: the last two tests fail with `cy.wrap() timed out waiting 10000ms`
  (the probe of `connectScriptedPlayer` never returns). In the same window the backend sees the app's SockJS
  fall back to `xhr_streaming` (6 sessions) and `eventsource` (6 sessions); in all other runs of the
  investigation it used `websocket` 514 times and `xhr_streaming` never.
- With the set on the shared window (`globalThis.__scriptedStompClients`, #196): at most 6 clients per test,
  reset to 0 after each; 3/3 merged runs pass; SockJS uses `websocket` only (36 sessions).

The ceiling sits at ~32 WebSockets held by the browser at once (31 scripted + the app's own). This matches the
per-proxy socket limit of Chromium (32), which applies because Electron goes through the Cypress proxy; the
limit itself is inferred from the measured threshold and the Chromium defaults, not read from Electron.

### 2.3 The server handles one client's STOMP frames out of order (proven, not fixed)

Black-box probe, one client, raw `/ws` endpoint, test profile, during e2e runs (VM load ~12-20):

| Burst | Rounds | Final name kept by the server | Renames applied out of order | Broadcasts received out of order |
|---|---|---|---|---|
| 200 renames | 5 | N170, N185, N193, N196, N198 (never N200) | 79-106 of 199 | 48-67 of 199 |
| 2 renames | 60 | N1 once (1/60) | 1/60 | 1/60 |

`WebSocketConfiguration` sets neither `StompEndpointRegistry.setPreserveReceiveOrder(true)` nor
`MessageBrokerRegistry.setPreservePublishOrder(true)` (Spring 7.0.6): inbound frames of one session run in
parallel on the `clientInboundChannel` pool and outbound frames to one session are not ordered either. The
specs already work around it: `botTierIs` in `support/commands.ts` ("the two commands travel on separate
broker threads"); the client drops stale lobby views by `lobbyVersion` and stale Kobo states by `version`.

### 2.4 An update sent before the first STOMP connection is lost (reproduced in Vitest, not fixed)

Vitest probe (throwaway branch), both tests fail deterministically:

1. `restore` → REST `hydrate` answers lobby v1 → the server broadcasts v2 on the seat channel while SockJS is
   still connecting → `onConnect` subscribes: the session still shows v1, and nothing asks for v2.
2. The first connection is not an online transition: `online` starts at `true`
   (`composables/useConnectionStatus.ts:6`) and `onConnect` sets it to `true` again, so the screens' re-hydrate
   watchers (`BullshitGameScreen.vue:157`, `KoboGameScreen.vue:71`, `ThreeMenMorrisGameScreen.vue:52`) never run
   after the first connection, and the lobby has no such watcher at all.

Three specs carry a workaround for exactly this ("a view sent between the browser's first load and its seat
subscription is lost"): `untilBrowserSees` in `support/koboRoom.ts`, `asBlack` in `morris-game.cy.ts:45-61`,
and `lobby-remove-seat.cy.ts:38`. It is a product bug, not only a test one: a guest opening a lobby can miss a
join or an option change until the next lobby update.

### 2.5 Machine load and environment

- **Cold start.** Backend container started 22:25:55, Spring Boot ready 22:30:33 (4 min 38 s: Maven
  resolution on an empty `backend_m2` volume, compile, then a 49 s context start); the whole `up --build`
  took 5 min. The healthcheck window (60 s start period + 60 × 5 s) held this time.
- **VM load without tests.** Load1 stays at 11-17 with 2 to 4 idle stacks, for only ~17 % CPU busy over a 5 s
  `/proc/stat` sample, 93 % of it system time (1,077 system vs 74 user jiffies); cumulative since VM boot,
  system time is 10× user time. Each idle Vite container with `CHOKIDAR_USEPOLLING=true` costs 25-57 % of a
  CPU in `docker stats`. The load is dominated by the polling watchers' syscalls on the Windows bind mounts,
  not by the apps.
- **Load vs failures.** No failure in 58 valid runs at load 11-22; run time did vary with load (shared-lobby
  test time 54 s at load 16.8 vs 75-93 s at load 19-22). The #167 failure (game-home, `cy.visit` 8.8 s while
  another stack was building, load 16.6) was not reproduced: the controlled "second stack cold build during a
  game-home series" was not run before the deadline.
- **Standby.** Three standby episodes during the investigation, two not covered by `keepAwake.ts`:
  closing the lid (01:21:26, reason "Lid") overrides `SetThreadExecutionState`; and an "Idle Timeout" standby at
  03:56:22 fell in a 38 s gap between two `cypress run` processes, when no keep-awake request was held. Runs
  survive a few minutes of modern standby, then freeze or the renderer crashes.

## 3. Verdict per hypothesis

| | Hypothesis | Verdict | Evidence level |
|---|---|---|---|
| H-A | Connection leak | **Confirmed, in the browser under test.** Scripted STOMP clients were never closed (module duplicated across Cypress bundles); past ~32 WebSockets in the browser, new ones never open and SockJS falls back to streaming. Not a server-side leak: backend sessions close as soon as the browser closes them. Not the HTTP/1.1 6-per-host limit: the failure appears at ~32 WebSockets, and the "streaming connections" seen in #186 are the fallback, a symptom. | Measured before/after (3/3 failed → 3/3 passed), instrumentation, backend session log. Exact Chromium limit inferred. |
| H-B | Machine load | **Not settled.** No failure at load 11-22 over 58 runs; durations do grow with load; the single #167 failure under a concurrent build was not reproduced. Load is mostly the Vite polling watchers. Lid and idle standby are confirmed as a separate environment cause. | Measured (no correlation found in the range observed); the cold-build experiment is missing. |
| H-C | Implicit waits in the application | **Confirmed for two races, plausible for three more** (section 4). | Vitest reproduction (first-connection loss); black-box probe (frame reordering). |
| H-D | Fixed waits / fragile specs | **Minor.** One `cy.wait(100)` (a bounded poll), three 50 ms polling loops, a real 60 s forfeit grace (`PresenceService.FORFEIT_GRACE`), no `retries`, no `Thread.sleep` in Cypress. The three replay loops of section 2.4 are workarounds for an app race, not test defects. | Code inventory. |

## 4. Races in the application (H-C)

| # | Race | Where | Status |
|---|---|---|---|
| C1 | Update broadcast between the REST hydrate and the first STOMP connection is lost; the first connection triggers no re-hydrate. | `SeatGameSession.ts:128-142` (bind subscribes, then `hydrate`), `WebSocketService.ts:78-83` (subscription deferred to `onConnect`), `useConnectionStatus.ts:6` (optimistic `true`); same for Bataille Corse in `GameSession.ts` | Reproduced in Vitest |
| C2 | Inbound frames of one client handled in parallel: two commands sent back to back can be applied in the reverse order (option then Start, rename then rename, presence then command). | `WebSocketConfiguration.java` (no `setPreserveReceiveOrder`) | Proven: 1/60 pairs, ~50 % in a burst |
| C3 | Outbound frames to one client reordered: a stale state can overwrite a newer one. Lobby views and Kobo states are guarded by a version; Three Men's Morris carries `version` (`model/threemenmorris/MorrisState.ts:33`) but the client ignores it; Bullshit and Bataille Corse game frames have no guard. | `WebSocketConfiguration.java` (no `setPreservePublishOrder`) | Reordering proven on lobby frames; stale game view not observed in e2e |
| C4 | Even when connected, `SUBSCRIBE` is processed asynchronously and without a receipt, while the hydrate `GET` runs on a Tomcat thread: a broadcast between the two can be lost. | `SeatGameSession.ts:135` then `:106/:121` | Plausible, not reproduced |
| C5 | After a reconnect, the lobby and the Bataille Corse game screen do not re-hydrate (only the Bullshit, Kobo and Morris game screens watch `online`). Updates missed while offline stay missed. | `useConnectionStatus` used only by three game screens | Code reading |
| C6 | A hydrate answered by a proxy 5xx or a network error returns `unavailable` and keeps the room page silently, with no retry: under load this produces exactly the #167 game-home signature (stays on `/room/<id>`). | `useSeatGameBootstrap.ts:41`, `useGameBootstrap.ts` | Plausible, not reproduced |

## 5. Root causes, ranked

1. **Scripted STOMP clients leaking until the end of the spec file** (fixed by #196). Turns every long
   spec into a countdown to the browser's WebSocket ceiling; explains the Kobo split of #186 and why the
   morris spec ran at 34-43 browser connections.
2. **Windows standby during long loops** (#167 cause 4, still open in two cases: lid closed, idle gap between
   runs). Explains every frozen or crashed run of this investigation; the run watchdog also freezes.
3. **First-connection race in the app (C1).** Explains why three specs need replay loops; a real user-facing
   bug.
4. **Unordered STOMP processing (C2, C3).** Low rate per pair (~1.7 %), but systematic; explains the
   `botTierIs` workaround and is a latent source of rare failures and wrong game states.
5. **Machine load** (not proven as a failure cause): slows runs, mostly from idle Vite polling watchers.

## 6. Proposed corrections

| # | Correction | Effect | Cost | Risk |
|---|---|---|---|---|
| 1 | Close scripted clients after each test (one line, shared set on the window). | Removes cause 1; the Kobo specs could be merged again. | Done (#196) | None |
| 2 | `registry.setPreserveReceiveOrder(true)` and `config.setPreservePublishOrder(true)` in `WebSocketConfiguration` (2 lines), with the order probe turned into a backend IT (`*IT`, N renames → last one wins). Then drop `botTierIs`. | Removes C2 and C3 at the source. | S | Low: per-session serialisation, negligible at this traffic. |
| 3 | Re-hydrate once the seat subscription is live after every connection, including the first: `WebSocketService` exposes a "connected" event (or a connection counter) that the sessions use, instead of relying on `online` flipping. Vitest from the probe of section 2.4. Then remove `untilBrowserSees` and the two replay loops. | Removes C1 and C5 (and C4 if the hydrate follows a `SUBSCRIBE` receipt). | S-M | Low-medium: one more `GET` per connection; must not flash an offline banner. |
| 4 | Drop stale game states by `version` for Three Men's Morris (already sent) and add a version to Bullshit and Bataille Corse game frames. | Removes the visible effect of C3 even without 2. | M | Low |
| 5 | Keep-awake for the whole loop, not per `cypress run` (a wrapper holding the request from the first to the last run), plus a documented "plugged in, lid open" rule; a watchdog on wall-clock time (compare `date +%s` before and after) that marks a run invalid when a standby is logged during it. | Removes or flags cause 2. | S | None |
| 6 | Hydrate retry on `unavailable` in the room bootstrap (C6), bounded, with a visible "reconnecting" state. | Removes a load-sensitive dead end for users and specs. | S | Low |
| 7 | Environment budget: stop idle stacks after use (already a rule), consider replacing `CHOKIDAR_USEPOLLING` by native watching where the mount allows it, or a `.wslconfig` with explicit `processors`/`memory`; `dev-ports` script choosing free offsets and setting `VITE_HMR_CLIENT_PORT` (R11). | Lower VM load, fewer port incidents. | S-M | Low (polling exists because Windows bind mounts do not deliver inotify events) |
| 8 | CI e2e job on demand: `workflow_dispatch` with a `spec` input plus a `e2e` PR label, ubuntu-latest, `settings.xml` generated from `GITHUB_TOKEN` (as `setup-java` does), `docker compose -f docker-compose.e2e.yml up --build -d --wait`, `cypress run --spec`. Not launched: it has to land on `main` first. | A clean reference to compare local failures with. | M | Medium (CI minutes, cold build ~5 min) |
| 9 | Test profile property for `PresenceService.FORFEIT_GRACE` (now a 60 s constant) so the forfeit spec does not wait a real minute. | -60 s on morris-game, fewer long waits. | S | Low |

## 7. Still unknown

- Whether machine load alone can fail a spec (H-B): the controlled experiment (series run while a second stack
  builds cold) was not done.
- The exact browser WebSocket ceiling and whether it is per proxy or per host in Electron 118: the threshold
  (~32) is measured, its source inferred.
- Whether C4 and C6 ever happen in practice: no failing run was traced to them.
- CI behaviour: no e2e run on Linux runners yet (correction 8).

## 8. Reproduce

- Stack: `docker compose -p bc-flakyroot -f docker-compose.e2e.yml -f <ports file> up --build -d`, then wait in
  a loop until `http://localhost:5195/` answers 200 (the backend needs ~5 min from an empty Maven volume).
- Runs: `npx cypress run --browser electron --config baseUrl=http://localhost:5195 --spec cypress/specs/<spec>.cy.ts`,
  in the background, one log per run, with the machine plugged in and the lid open.
- Sessions: `docker logs <backend> | grep LoggingWebSocketHandlerDecorator` (open / close, transport, close
  code); `/connect/<n>/<id>/<transport>` in the same log shows SockJS fallbacks.
- Order probe: a STOMP client on `ws://localhost:8095/ws` sending N `/app/lobby/rename` in a burst, then
  `GET /api/bullshit/game/<id>?token=<token>` for the final name.
