# Hot Reload for Dev Docker Environment

**Date:** 2026-05-18
**Status:** Pending implementation

## Overview

Add hot reload to both the frontend (Vite HMR) and backend (Spring Boot DevTools + auto-compile) in the Docker dev environment. The existing `deploy-dev.sh` / `run-dev.sh` scripts and the single `docker-compose.dev.yml` remain unchanged — hot reload is baked in as a permanent part of the dev setup.

---

## Architecture

```
Developer saves file
       │
       ├── frontend/src/**  ──→  Vite HMR  ──→  Browser updates in-place (~100ms)
       │
       └── backend/src/**   ──→  inotifywait detects change
                                      │
                                      └──→  mvn compile -q (500ms debounce)
                                                    │
                                                    └──→  DevTools detects new .class files
                                                                  │
                                                                  └──→  Spring Boot restarts in-process (~3–5s)
```

Both services run with source code mounted from the host. The production build path (JAR, nginx) is unaffected.

---

## Frontend

**File:** `frontend/vite.config.mjs`

Add `server.hmr.host: 'localhost'` to pin the HMR WebSocket host explicitly. Without this, Vite infers the host from the browser's origin, which works in most cases but can resolve to the container's internal hostname on Windows/Docker Desktop with polling enabled.

```js
server: {
  host: true,
  port: 5173,
  hmr: {
    host: 'localhost',
    port: 5173,
  },
  proxy: { ... }  // unchanged
}
```

Everything else is already in place: volume mount, `CHOKIDAR_USEPOLLING=true`, and the `dev` Dockerfile stage running `npm run dev`.

---

## Backend

### 1. `backend/pom.xml`

Uncomment the `spring-boot-devtools` dependency. Remove the hardcoded version `3.4.5` — it was wrong for Spring Boot 4 and the parent BOM manages it. Keep `<optional>true</optional>` to exclude DevTools from the production JAR.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

### 2. `backend/Dockerfile` — new `dev` stage

Added between the existing `build` and `run` stages. Based on the Maven image (same as `build`) so `mvn` is available at runtime. Source code is NOT copied in — it is mounted at runtime via the compose volume.

```dockerfile
# ---- Dev stage ----
FROM maven:3.9-eclipse-temurin-22 AS dev

WORKDIR /app

RUN apt-get update && apt-get install -y inotify-tools && rm -rf /var/lib/apt/lists/*

COPY entrypoint-dev.sh /entrypoint-dev.sh
RUN chmod +x /entrypoint-dev.sh

ENTRYPOINT ["/entrypoint-dev.sh"]
```

### 3. `backend/entrypoint-dev.sh` — new file

Orchestrates two concurrent concerns: running the app and watching for changes.

```bash
#!/bin/bash

cleanup() {
  echo "[dev] Shutting down..."
  kill $MVN_PID 2>/dev/null
  exit 0
}
trap cleanup SIGTERM SIGINT

echo "[dev] Starting Spring Boot..."
mvn spring-boot:run &
MVN_PID=$!

echo "[dev] Watching src/ for changes..."
while true; do
  inotifywait -r -e close_write,moved_to,create,delete src/ 2>/dev/null || true
  echo "[dev] Change detected — compiling..."
  sleep 0.5
  mvn compile -q || true
done

wait $MVN_PID
```

Key details:
- `sleep 0.5` debounces rapid multi-file saves (e.g. IDE auto-format on save)
- `|| true` on both `inotifywait` and `mvn compile` prevents the watcher loop from dying on transient errors
- `SIGTERM`/`SIGINT` trap ensures `docker compose down` cleanly kills the Maven process
- `mvn compile` covers both `.java` changes and `src/main/resources` changes (resources are processed as part of the compile lifecycle)

### 4. `docker-compose.dev.yml` — backend service changes

```yaml
backend:
  build:
    context: ./backend
    dockerfile: Dockerfile
    target: dev          # points to the new dev stage
    secrets:
      - maven_settings   # still needed at build time for dependency resolution
  ports:
    - "8080:8080"
  volumes:
    - ./backend:/app                                          # source mount
    - backend_m2:/root/.m2/repository                        # Maven cache (survives restarts)
    - ./deploy/docker/settings.xml:/root/.m2/settings.xml:ro # GitHub credentials at runtime

volumes:
  backend_m2:
```

`settings.xml` is mounted read-only to `/root/.m2/settings.xml` — the standard Maven settings location — so `mvn spring-boot:run` and `mvn compile` can resolve the `frenchcards` dependency from GitHub Packages without any extra `-s` flag.

The `backend_m2` named volume persists the local Maven repository across container restarts. On first `docker compose up`, Maven downloads all dependencies (~30–60s depending on connection). Subsequent runs start immediately.

---

## What does NOT change

- `deploy-dev.sh` — unchanged
- `run-dev.sh` — unchanged
- `docker-compose.prod.yml` — unchanged
- `backend/Dockerfile` `build` and `run` stages — unchanged
- `frontend/Dockerfile` — unchanged
- `deploy/docker/settings.xml` — unchanged

---

## Expected developer experience

| Action | Result |
|---|---|
| Save a `.vue` or `.ts` file | Browser updates in-place via Vite HMR, no page reload |
| Save a `.java` file | `mvn compile` runs, DevTools restarts Spring Boot in ~3–5s, WebSocket reconnects |
| Save `application.properties` | Same as `.java` — compile lifecycle processes resources |
| `docker compose down` | Both processes shut down cleanly |
| Restart containers | Maven cache intact, app starts without re-downloading dependencies |
