# BatailleCorse

A real-time card game with a Spring Boot (Java 22) backend and a Vue 3 + Vite frontend, orchestrated with Docker Compose.

## Prerequisites

- Docker with BuildKit enabled (`DOCKER_BUILDKIT=1`)
- A Maven `settings.xml` placed at `deploy/docker/settings.xml` (gitignored — required to resolve backend dependencies)

## Development

Runs the frontend with hot reload and the backend with live reload and a remote debug port.

```sh
sh dev.sh
```

Or run the underlying command directly:

```sh
docker compose -f docker-compose.dev.yml up --build
```

Services:
- Frontend (Vite dev server): http://localhost:5173
- Backend (Spring Boot): http://localhost:8080
- Backend remote debug (JDWP): port 5005

Source folders are bind-mounted, so edits on the host are reflected in the running containers.

## Testing

### Frontend unit tests (Vitest)

Run from the `frontend` directory (no backend needed):

```sh
cd frontend
npm install        # first time only
npm test           # one-off run
npm run test:watch # watch mode
```

### End-to-end tests (Cypress)

The e2e specs drive the real app, so they need the full stack running. The e2e stack
(`docker-compose.e2e.yml`, Spring profile `test`) is not the dev stack: nothing is
bind-mounted and nothing compiles at start-up.

- Backend: the `run` target of `backend/Dockerfile` (the production image) runs a jar built
  during `docker compose build`. Maven resolves dependencies with `deploy/docker/settings.xml`
  passed as a build secret, in an image layer that only changes with `pom.xml`, so later
  builds only recompile.
- Frontend: the `e2e` target of `frontend/Dockerfile` serves the production build with
  `vite preview`, which proxies `/api` and `/connect` (WebSocket) to the Docker service
  `backend:8080` like the dev server does (that target only resolves on the Compose
  network). No hot reload: `VITE_HMR_CLIENT_PORT` has no effect on this stack.

A source change reaches the stack only through a rebuild, so always start it with `--build`
(unchanged layers are reused). Measured on 2026-10-24 (details in `docs/harnesses.md`): about
3 minutes the first time or after a `pom.xml` change, under a minute after a code change,
about 25 s without changes, against 4 to 13 minutes for the former stack, which compiled
inside the container at every start.

1. Start the e2e stack (backend on `:8080`, frontend on `:5173`) and wait for both healthchecks:

   ```sh
   docker compose -f docker-compose.e2e.yml up --build -d --wait
   ```

2. In a second terminal, run Cypress from the `frontend` directory (it targets
   `http://localhost:5173`):

   ```sh
   cd frontend
   npm install      # first time only
   npm run cy:run   # headless (CI-style)
   npm run cy:open  # or the interactive runner
   ```

Other commands (from `frontend`):

```sh
npm run cy:smoke      # the smoke subset of cypress/smoke-specs.json (what the CI e2e-smoke job runs)
npm run cy:component  # Cypress component tests (cypress/component/), no stack needed
npm run cy:summary    # per-spec summary of cypress/results/run-log.jsonl (runs, failures, retries, median time)
```

Specs live in `frontend/cypress/specs/`. When done, stop the stack with
`docker compose -f docker-compose.e2e.yml down`.

On Windows, `cypress run` keeps the machine out of standby while it runs
(`frontend/cypress/keepAwake.ts`): a run caught by the idle standby never resumes
after wake-up and only ends when killed.

#### Series of runs, standby watchdog, free ports

Helper scripts live in `scripts/` (POSIX `sh`, run them from Git Bash on Windows). Their tests:
`bash scripts/tests/run.sh`.

- `sh scripts/e2e-loop.sh <runs> [cypress run arguments]` runs `cypress run` several times in a row. On Windows
  it holds the keep-awake request (`scripts/keep-awake.ps1`) for the **whole** loop, so the gap between two
  runs is covered too. A closed lid still suspends the machine: keep the laptop plugged in with the lid open.
- `sh scripts/e2e-watchdog.sh -- <command>` wraps one command. A `timeout` cannot see a standby (its clock
  stops with the machine), so the watchdog compares the wall clock between heartbeats: a jump larger than
  `WATCHDOG_GAP` seconds (default 30) marks the run **INVALID** (exit code 3) instead of a pass or a failure.
  `e2e-loop.sh` already wraps every run with it.
- `sh scripts/free-ports.sh <ports.yml>` picks the smallest port offset for which the frontend (5173),
  backend (8080) and debug (5005) ports are all free, and writes the Compose override for it (with
  `VITE_HMR_CLIENT_PORT`). Keep the file outside the repository, then start the stack under its own project name:

  ```sh
  sh scripts/free-ports.sh <scratchpad>/ports.yml     # prints the URL and the commands
  docker compose -p bc-<subject> -f docker-compose.e2e.yml -f <scratchpad>/ports.yml up --build -d --wait
  npx cypress run --config baseUrl=http://localhost:<frontend port>
  ```

Under the e2e (`test`) Spring profile the forfeit grace of a dropped player is 5 s instead of 60 s
(`presence.forfeit-grace-seconds` in `application-test.properties`); production keeps 60 s.

## Languages

The UI is available in English and French. The language comes from the player's saved choice (`bc_locale` in `localStorage`), then the browser languages, then English; the globe button on the game picker and the language section of the settings panel switch it (the page reloads). Texts live in `frontend/src/locales/` (`en.ts`, `fr/`, `Messages.ts` for the shape). `npm test` fails if a French key is missing or still English (`parity.test.ts`), and `length.test.ts` lists short French strings much longer than English ones (`LIST_LONG_FRENCH=<file>` writes the list). See `docs/architecture/i18n.md`.

## Production

Runs at https://cardgames.kevinkib.com behind the shared gateway
([KevinKib/gateway](https://github.com/KevinKib/gateway)), which owns ports 80/443 and the HTTPS
certificates. The frontend and backend join its `web` network as `bataillecorse-frontend` and
`bataillecorse-backend`; nothing publishes a port. Start the gateway first, then:

```sh
bash prod.sh
```

Or run the underlying command directly:

```sh
DOCKER_BUILDKIT=1 docker compose -f docker-compose.prod.yml up --build -d
```

Services:
- Frontend: built static assets served by Nginx (`bataillecorse-frontend:0.1`)
- Backend: packaged JAR (`bataillecorse-backend:0.1`)

The backend image consumes `deploy/docker/settings.xml` as a build secret (`maven_settings`).

HTTPS certificates and the move from BatailleCorse's former built-in gateway are documented in the
gateway's README.

To stop:

```sh
docker compose -f docker-compose.prod.yml down
```

## Usage statistics

"Is the site really played?" is answered by the backend itself, with no database and no third-party tracker.
The design is in `docs/architecture/usage-statistics.md`.

**Counters (Micrometer, tag `game`, reset at every redeploy)**: `cardgames.games.created`, `cardgames.games.started`
(+ `.humans` / `.bots`: seats at start), `cardgames.games.finished`, `cardgames.games.rematches`,
`cardgames.seats.joined` (humans who joined a room, host excluded), `cardgames.bots.added`.
**Gauges**: `cardgames.rooms.open` (rooms in memory) and `cardgames.players.connected` (seats with a live connection).

**Reading them in production.** Actuator listens on its own port (`management.server.port`, 8081), which the
gateway never proxies and `docker-compose.prod.yml` never publishes, so it is unreachable from the internet. From
the server:

```sh
docker compose -f docker-compose.prod.yml exec backend wget -qO- localhost:8081/actuator/metrics                           # all meters
docker compose -f docker-compose.prod.yml exec backend wget -qO- localhost:8081/actuator/metrics/cardgames.games.started   # one meter
docker compose -f docker-compose.prod.yml exec backend wget -qO- 'localhost:8081/actuator/metrics/cardgames.games.started?tag=game:mangola'
```

**Counting per day (survives redeploys).** Every event also writes one INFO line (`stats event=game_created game=mangola`;
event name, game and counts only: no name, address, token or game id). Number of events per day and per game:

```sh
docker compose -f docker-compose.prod.yml logs --no-log-prefix backend \
  | grep -o '^[0-9-]\{10\}.*stats event=[a-z_]* game=[a-z-]*' \
  | sed -E 's/^([0-9-]{10}).*stats /\1 /' | sort | uniq -c
```

Add `| grep game_started` for games actually started. Docker keeps the logs until the container is recreated
(`docker compose up --build` recreates it), so export them before a redeploy if you need a longer history.

## Troubleshooting

### `npm ci` fails with "package.json and package-lock.json are in sync" / "Missing: &lt;pkg&gt; from lock file"

Seen during the frontend Docker build, e.g.:

```
npm error `npm ci` can only install packages when your package.json and
package-lock.json or npm-shrinkwrap.json are in sync.
npm error Missing: @vue/test-utils@2.4.11 from lock file
```

**Cause:** your machine runs a newer npm (e.g. npm 11) than the Docker image
(`node:20-alpine` ships npm 10). The two resolve the dependency tree slightly
differently, so a lock file generated locally gets rejected by `npm ci` inside the
image. Running `npm install` again on your machine cannot fix it — the lock must be
generated by the *same* npm the image uses.

**Fix** (requires Docker running):

```sh
./frontend/sync-lock.sh        # or: cd frontend && npm run lock:sync
git add frontend/package-lock.json
git commit -m "chore: sync frontend lockfile for node:20-alpine (npm 10)"
```

The script regenerates `frontend/package-lock.json` inside `node:20-alpine` with
`--package-lock-only` (it never touches your local `node_modules`) and verifies it
with `npm ci --dry-run` in the same image. Then re-run your build / `bash prod.sh`.
