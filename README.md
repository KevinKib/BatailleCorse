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

The e2e specs drive the real app, so they need the full stack running. The Vite dev
server proxies `/api` and `/connect` (WebSocket) to the Docker service `backend:8080`,
so the stack must be started with Docker Compose — that proxy target only resolves on
the Compose network.

1. Start the e2e stack (backend on `:8080`, Vite dev server on `:5173`):

   ```sh
   docker compose -f docker-compose.e2e.yml up --build
   ```

2. In a second terminal, run Cypress from the `frontend` directory (it targets
   `http://localhost:5173`):

   ```sh
   cd frontend
   npm install      # first time only
   npm run cy:run   # headless (CI-style)
   npm run cy:open  # or the interactive runner
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

## Production

Runs at https://kevinkib.com behind the shared gateway
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
