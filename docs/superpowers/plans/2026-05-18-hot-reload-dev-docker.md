# Hot Reload Dev Docker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Vite HMR for the frontend and Spring Boot DevTools auto-restart for the backend in the Docker dev environment, with no changes to the prod path or the dev shell scripts.

**Architecture:** The frontend volume mount is already in place — only a minor `vite.config.mjs` tweak is needed. The backend gets a new `dev` Dockerfile stage (Maven-based) that runs `mvn spring-boot:run` alongside an `inotifywait` loop; when a source file changes the loop runs `mvn compile -q`, DevTools detects the new `.class` files and restarts in ~3–5s. `docker-compose.dev.yml` is updated to target the new stage, mount the backend source, and wire in the Maven credential file at runtime via a bind mount.

**Tech Stack:** Docker Compose, Vite 5, Spring Boot 4, Maven 3.9, `inotify-tools` (Linux), `spring-boot-devtools`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `backend/pom.xml` | Modify | Enable DevTools at runtime |
| `backend/entrypoint-dev.sh` | Create | Orchestrate app + file watcher |
| `backend/Dockerfile` | Modify | Add `dev` stage |
| `docker-compose.dev.yml` | Modify | Target dev stage, add volumes |
| `frontend/vite.config.mjs` | Modify | Pin HMR WebSocket host |

---

## Task 1: Enable Spring Boot DevTools in pom.xml

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: Replace the commented-out DevTools block**

In `backend/pom.xml`, find the commented-out block (lines 47–52) and replace it with:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

The version is intentionally omitted — Spring Boot 4's parent BOM manages it. `<optional>true</optional>` prevents DevTools from being packaged into the production JAR.

- [ ] **Step 2: Verify the XML is valid**

Run from the `backend/` directory:
```bash
mvn validate -q
```
Expected: no output (success). If there is a parse error, fix the XML.

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "feat: enable spring-boot-devtools for dev hot reload"
```

---

## Task 2: Create the dev entrypoint script

**Files:**
- Create: `backend/entrypoint-dev.sh`

This script runs inside the Linux container. **It must use LF line endings**, not CRLF. If you are on Windows, your editor may default to CRLF — configure it explicitly (in VS Code: click the line-ending indicator in the status bar and select LF; in IntelliJ: File > Line Separators > LF).

- [ ] **Step 1: Create `backend/entrypoint-dev.sh`**

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

Key points:
- `mvn spring-boot:run` starts the app in the background; DevTools is active because it is on the classpath (Task 1) and we are running from Maven, not a JAR.
- `inotifywait -r` watches `src/` recursively, covering both `.java` and `src/main/resources/` changes.
- `sleep 0.5` debounces rapid multi-file saves (e.g. IDE auto-format on save touching several files at once).
- `|| true` on both `inotifywait` and `mvn compile` prevents the loop from dying on transient errors.
- The `SIGTERM`/`SIGINT` trap ensures `docker compose down` cleanly terminates the Maven process.

- [ ] **Step 2: Commit**

```bash
git add backend/entrypoint-dev.sh
git commit -m "feat: add dev entrypoint script with inotifywait watcher"
```

---

## Task 3: Add dev stage to backend Dockerfile

**Files:**
- Modify: `backend/Dockerfile`

- [ ] **Step 1: Insert the `dev` stage between `build` and `run`**

Open `backend/Dockerfile`. After the closing line of the `build` stage (`-s /run/secrets/maven_settings`) and before the `# ---- Run stage ----` comment, insert:

```dockerfile
# ---- Dev stage ----
FROM maven:3.9-eclipse-temurin-22 AS dev

WORKDIR /app

RUN apt-get update && apt-get install -y inotify-tools && rm -rf /var/lib/apt/lists/*

COPY entrypoint-dev.sh /entrypoint-dev.sh
# Strip Windows CRLF line endings in case the file was saved on Windows
RUN sed -i 's/\r$//' /entrypoint-dev.sh && chmod +x /entrypoint-dev.sh

ENTRYPOINT ["/entrypoint-dev.sh"]
```

The `sed -i 's/\r$//'` step strips CRLF line endings so the script runs correctly regardless of what OS wrote the file. Source code is intentionally NOT copied in — it is mounted at runtime via the compose volume.

The full file should now read, in order: `build` → `dev` → `run`.

- [ ] **Step 2: Verify the Dockerfile parses**

```bash
docker build --target dev -f backend/Dockerfile backend/ --no-cache 2>&1 | tail -5
```

Expected: last lines show `Successfully built <image-id>` or similar. If there is a syntax error, fix the Dockerfile.

Note: this build will fail if Docker BuildKit secrets are required and not provided (the `build` stage), but `--target dev` stops before the `build` stage, so it should succeed without secrets.

- [ ] **Step 3: Commit**

```bash
git add backend/Dockerfile
git commit -m "feat: add dev stage to backend Dockerfile"
```

---

## Task 4: Update docker-compose.dev.yml

**Files:**
- Modify: `docker-compose.dev.yml`

- [ ] **Step 1: Update the backend service**

Replace the entire `backend:` service block with:

```yaml
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
      target: dev
    ports:
      - "8080:8080"
    volumes:
      - ./backend:/app
      - backend_m2:/root/.m2/repository
      - ./deploy/docker/settings.xml:/root/.m2/settings.xml:ro
```

Changes from the original:
- `target: dev` selects the new dev stage (no JAR compilation at build time).
- `secrets` removed from the build block — the dev stage does not use them at build time.
- `./backend:/app` mounts source so the watcher and compiler see live edits.
- `backend_m2:/root/.m2/repository` persists the Maven local repo across container restarts (avoids re-downloading all dependencies each time).
- `./deploy/docker/settings.xml:/root/.m2/settings.xml:ro` mounts the GitHub Packages credentials at the standard Maven settings path so `mvn spring-boot:run` and `mvn compile` can resolve the `frenchcards` dependency without an explicit `-s` flag.

- [ ] **Step 2: Add the named volume declaration and remove the unused secret**

At the end of the file, add a `volumes:` top-level key and remove the now-unused `secrets:` top-level key. The final file should be:

```yaml
version: "3.9"

services:
  frontend:
    build:
      context: ./frontend
      target: dev
      dockerfile: Dockerfile
    ports:
      - "5173:5173"
    volumes:
      - ./frontend:/app
      - /app/node_modules
    environment:
      - CHOKIDAR_USEPOLLING=true

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
      target: dev
    ports:
      - "8080:8080"
    volumes:
      - ./backend:/app
      - backend_m2:/root/.m2/repository
      - ./deploy/docker/settings.xml:/root/.m2/settings.xml:ro

volumes:
  backend_m2:
```

- [ ] **Step 3: Validate the compose file**

```bash
docker compose -f docker-compose.dev.yml config --quiet
```

Expected: no output (valid). Fix any YAML errors before continuing.

- [ ] **Step 4: Commit**

```bash
git add docker-compose.dev.yml
git commit -m "feat: configure backend dev stage with hot reload volumes"
```

---

## Task 5: Pin Vite HMR host in vite.config.mjs

**Files:**
- Modify: `frontend/vite.config.mjs`

- [ ] **Step 1: Add `hmr` to the `server` block**

In `frontend/vite.config.mjs`, update the `server` block to:

```js
server: {
  host: true,
  port: 5173,
  hmr: {
    host: 'localhost',
    port: 5173,
  },
  proxy: {
    "/connect": {
      target: "http://backend:8080",
      ws: true,
      changeOrigin: true
    }
  }
},
```

This pins the HMR WebSocket to `localhost:5173` so the browser always connects back to the forwarded port rather than the container's internal hostname, which can misbehave on Windows Docker Desktop with polling.

- [ ] **Step 2: Commit**

```bash
git add frontend/vite.config.mjs
git commit -m "feat: pin Vite HMR host for Docker dev reliability"
```

---

## Task 6: Build, run and verify

- [ ] **Step 1: Build the dev images**

```bash
./deploy-dev.sh
```

Expected: both images build successfully. The backend build installs `inotify-tools` — you will see `apt-get` output. The frontend build is unchanged.

- [ ] **Step 2: Start the dev environment**

```bash
./run-dev.sh
```

Watch the logs. Expected sequence for the backend:
```
backend-1  | [dev] Starting Spring Boot...
backend-1  | [dev] Watching src/ for changes...
backend-1  | ... (Maven downloading dependencies on first run — may take 1–2 min)
backend-1  | ... Tomcat started on port 8080
```

For the frontend:
```
frontend-1 | VITE v5.x.x ready in ...ms
frontend-1 | ➜ Local: http://localhost:5173/
```

- [ ] **Step 3: Verify frontend HMR**

Open `http://localhost:5173` in a browser. Edit any `.vue` file in `frontend/src/` (e.g. change a visible text string). The browser should update within ~1s without a full page reload. The terminal should show a Vite HMR log line.

- [ ] **Step 4: Verify backend auto-restart**

Edit any `.java` file in `backend/src/main/java/` (e.g. add a harmless comment). Save the file. Watch the `docker compose up` output — expected within ~5s:

```
backend-1  | [dev] Change detected — compiling...
backend-1  | ... Restarting due to devtools...
backend-1  | ... Tomcat started on port 8080
```

- [ ] **Step 5: Verify WebSocket reconnects**

While the app is open in the browser with an active game, trigger a backend restart (Step 4). After the restart (~5s), the browser's WebSocket (`/connect`) should reconnect automatically — the STOMP client in the frontend handles reconnect. If the game lobby is reachable after reconnect, the setup is complete.

- [ ] **Step 6: Final commit (if any fixup edits were needed during verification)**

```bash
git add -p
git commit -m "fix: hot reload verification fixups"
```

Only create this commit if something needed adjustment during Steps 1–5. Skip otherwise.
