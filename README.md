# BatailleCorse

A real-time card game with a Spring Boot (Java 22) backend and a Vue 3 + Vite frontend, orchestrated with Docker Compose.

## Prerequisites

- Docker with BuildKit enabled (`DOCKER_BUILDKIT=1`)
- A Maven `settings.xml` placed at `deploy/docker/settings.xml` (gitignored — required to resolve backend dependencies)

## Development

Runs the frontend with hot reload and the backend with live reload and a remote debug port.

```sh
./dev.sh
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

## Production

Builds optimized images and serves everything behind an Nginx gateway.

```sh
./prod.sh
```

Or run the underlying command directly:

```sh
DOCKER_BUILDKIT=1 docker compose -f docker-compose.prod.yml up --build -d
```

Services:
- Gateway (Nginx): http://localhost — routes to the frontend and backend
- Frontend: built static assets served by Nginx (`bataillecorse-frontend:0.1`)
- Backend: packaged JAR (`bataillecorse-backend:0.1`), also exposed on port 8080

The backend image consumes `deploy/docker/settings.xml` as a build secret (`maven_settings`).

To stop:

```sh
docker compose -f docker-compose.prod.yml down
```
