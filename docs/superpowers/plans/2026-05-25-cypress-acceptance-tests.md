# Cypress Acceptance Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three full-stack Cypress acceptance tests covering game creation, game rehydration, and card sending to catch regressions in the WebSocket + REST flow.

**Architecture:** Cypress runs on the host machine against a real Docker Compose stack (frontend on 5173, backend on 8080). A `docker-compose.e2e.yml` activates `SPRING_PROFILES_ACTIVE=test` on the backend, wiring in an empty `application-test.properties` that will hold AI-disable flags when the AI moves server-side. The frontend AI is slowed to 2100ms by seeding `bc_difficulty=0` in localStorage before every page load via a Cypress `window:before:load` hook.

**Tech Stack:** Cypress 13, TypeScript, Vue 3, Spring Boot (STOMP/SockJS), Docker Compose

---

## File Map

**Created:**
- `backend/src/main/resources/application-test.properties` — Spring test profile, empty now
- `docker-compose.e2e.yml` — real stack with `SPRING_PROFILES_ACTIVE=test`
- `frontend/cypress.config.ts` — Cypress config pointing at `http://localhost:5173`
- `frontend/cypress/tsconfig.json` — TypeScript config for the cypress folder (adds Cypress types)
- `frontend/cypress/support/e2e.ts` — global `window:before:load` hook (seeds localStorage)
- `frontend/cypress/support/commands.ts` — `cy.createGame()` custom command
- `frontend/cypress/specs/create-game.cy.ts` — Flow A
- `frontend/cypress/specs/rehydrate-game.cy.ts` — Flow B
- `frontend/cypress/specs/send-card.cy.ts` — Flow C

**Modified:**
- `frontend/package.json` — add `cypress` devDependency + `cy:open` / `cy:run` scripts
- `frontend/src/view/alpha/GameScreen.vue` — add `data-cy` attributes to card counter wrappers

---

## Task 1: Backend test profile

**Files:**
- Create: `backend/src/main/resources/application-test.properties`

- [ ] **Step 1: Create the properties file**

```properties
# E2E test profile. When AI moves to the backend, add: game.ai.enabled=false
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/resources/application-test.properties
git commit -m "feat: add Spring test profile for E2E acceptance tests"
```

---

## Task 2: Docker Compose e2e file

**Files:**
- Create: `docker-compose.e2e.yml`

- [ ] **Step 1: Create the compose file**

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
      - "5005:5005"
    volumes:
      - ./backend:/app
      - backend_m2:/root/.m2/repository
      - ./deploy/docker/settings.xml:/root/.m2/settings.xml:ro
    environment:
      - SPRING_PROFILES_ACTIVE=test

volumes:
  backend_m2:
```

- [ ] **Step 2: Start the stack and verify it's healthy**

```bash
docker compose -f docker-compose.e2e.yml up
```

In another terminal:

```bash
curl http://localhost:5173
# Expected: HTTP 200 (Vite dev server responds)

curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/game/ping
# Expected: 4xx (any HTTP response means the server is up; connection refused means it's not)
```

If the backend prints `Started Application` in its logs, it's ready.

- [ ] **Step 3: Commit**

```bash
git add docker-compose.e2e.yml
git commit -m "feat: add docker-compose.e2e.yml with Spring test profile"
```

---

## Task 3: Install Cypress and scaffold support files

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/cypress.config.ts`
- Create: `frontend/cypress/tsconfig.json`
- Create: `frontend/cypress/support/e2e.ts`
- Create: `frontend/cypress/support/commands.ts`

- [ ] **Step 1: Add Cypress to package.json**

In `frontend/package.json`, add to `"scripts"`:

```json
"cy:open": "cypress open",
"cy:run":  "cypress run"
```

Add to `"devDependencies"`:

```json
"cypress": "^13.0.0"
```

- [ ] **Step 2: Install**

```bash
cd frontend && npm install
```

Expected: `cypress` appears in `node_modules/cypress`.

- [ ] **Step 3: Create cypress.config.ts**

`frontend/cypress.config.ts`:

```ts
import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/specs/**/*.cy.ts',
  },
});
```

- [ ] **Step 4: Create cypress/tsconfig.json**

`frontend/cypress/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "es2022",
    "lib": ["es2022", "dom"],
    "types": ["cypress"]
  },
  "include": ["**/*.ts"]
}
```

This isolates Cypress global types (`cy`, `Cypress`) from the main frontend TypeScript config.

- [ ] **Step 5: Create cypress/support/e2e.ts**

`frontend/cypress/support/e2e.ts`:

```ts
Cypress.on('window:before:load', (win) => {
  win.localStorage.setItem('bc_difficulty', '0');
});
```

`bc_difficulty=0` selects "Training" difficulty (2100ms AI reaction time), giving Cypress assertions a safe window before the frontend AI acts. This fires before every `cy.visit`, so it applies to all tests automatically.

- [ ] **Step 6: Create cypress/support/commands.ts**

`frontend/cypress/support/commands.ts`:

```ts
declare global {
  namespace Cypress {
    interface Chainable {
      createGame(): Chainable<void>;
    }
  }
}

Cypress.Commands.add('createGame', () => {
  cy.visit('/create');
  cy.contains('button', 'Deal Cards').click();
  cy.url().should('match', /\/room\/.+/);
});
```

- [ ] **Step 7: Verify Cypress opens without errors (stack must be running from Task 2)**

```bash
cd frontend && npm run cy:open
```

Expected: Cypress app opens, shows the `cypress/specs/` folder (empty). No TypeScript errors in the console.

- [ ] **Step 8: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/cypress.config.ts \
  frontend/cypress/tsconfig.json frontend/cypress/support/e2e.ts \
  frontend/cypress/support/commands.ts
git commit -m "feat: install Cypress and scaffold support files"
```

---

## Task 4: Add data-cy attributes to GameScreen

The card counter `<div class="card_counter">` wrappers in `GameScreen.vue` have no stable test selectors. Add `data-cy` attributes so tests can target each counter independently.

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Add data-cy to the opponent card counter (gamescreen_top)**

Find this block (around line 17):

```html
<div class="card_counter">
  <CardCounter :count="batailleCorse?.players.at(1)?.nbCards"/>
</div>
```

Change to:

```html
<div class="card_counter" data-cy="opponent-card-count">
  <CardCounter :count="batailleCorse?.players.at(1)?.nbCards"/>
</div>
```

- [ ] **Step 2: Add data-cy to the pile card counter (gamescreen_middle)**

Find this block (around line 36):

```html
<div class="card_counter">
  <CardCounter :count="batailleCorse?.pile.cards.length"/>
</div>
```

Change to:

```html
<div class="card_counter" data-cy="pile-card-count">
  <CardCounter :count="batailleCorse?.pile.cards.length"/>
</div>
```

- [ ] **Step 3: Add data-cy to the player card counter (gamescreen_bottom)**

Find this block (around line 60):

```html
<div class="card_counter">
  <CardCounter :count="batailleCorse?.players.at(0)?.nbCards"/>
</div>
```

Change to:

```html
<div class="card_counter" data-cy="player-card-count">
  <CardCounter :count="batailleCorse?.players.at(0)?.nbCards"/>
</div>
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: add data-cy attributes to GameScreen card counters"
```

---

## Task 5: Flow A — create-game spec

Validates: lobby → create screen → WebSocket CREATE event received → navigated to game screen → initial state is correct.

**Prerequisite:** Stack is running (`docker compose -f docker-compose.e2e.yml up`).

**Files:**
- Create: `frontend/cypress/specs/create-game.cy.ts`

- [ ] **Step 1: Write the spec**

`frontend/cypress/specs/create-game.cy.ts`:

```ts
describe('Create game', () => {
  it('navigates to game screen and shows initial game state', () => {
    cy.visit('/');

    cy.contains('button', 'New Game').click();
    cy.url().should('include', '/create');

    cy.contains('button', 'Deal Cards').click();

    // The store watches for a WebSocket CREATE response and navigates on gameId.
    cy.url().should('match', /\/room\/.+/);

    // Initial state: 52 cards split evenly, pile empty.
    cy.get('[data-cy="player-card-count"]').should('contain.text', '26');
    cy.get('[data-cy="opponent-card-count"]').should('contain.text', '26');
    cy.get('[data-cy="pile-card-count"]').should('contain.text', '0');
  });
});
```

- [ ] **Step 2: Run the spec**

```bash
cd frontend && npm run cy:run -- --spec cypress/specs/create-game.cy.ts
```

Expected output:
```
  Create game
    ✓ navigates to game screen and shows initial game state (Xms)

  1 passing
```

If it fails: check that the Docker stack is fully started (backend logs show `Started Application`) and that `http://localhost:5173` loads the lobby in a browser.

- [ ] **Step 3: Commit**

```bash
git add frontend/cypress/specs/create-game.cy.ts
git commit -m "test: add Flow A acceptance test — create game via WebSocket"
```

---

## Task 6: Flow B — rehydrate-game spec

Validates: navigating directly to `/room/:id` (e.g. refreshing) loads game state via REST and does not redirect away.

**Prerequisite:** Stack is running.

**Files:**
- Create: `frontend/cypress/specs/rehydrate-game.cy.ts`

- [ ] **Step 1: Write the spec**

`frontend/cypress/specs/rehydrate-game.cy.ts`:

```ts
describe('Rehydrate game from URL', () => {
  it('loads game state when navigating directly to /room/:id', () => {
    cy.createGame();

    cy.url().then((gameUrl) => {
      // Navigate away, then come back directly — simulates a page refresh.
      cy.visit('/');
      cy.visit(gameUrl);

      // GameScreen fetches /api/game/:id on mount; should not redirect to /.
      cy.url().should('eq', gameUrl);

      cy.get('[data-cy="player-card-count"]').should('contain.text', '26');
      cy.get('[data-cy="opponent-card-count"]').should('contain.text', '26');
    });
  });
});
```

- [ ] **Step 2: Run the spec**

```bash
cd frontend && npm run cy:run -- --spec cypress/specs/rehydrate-game.cy.ts
```

Expected output:
```
  Rehydrate game from URL
    ✓ loads game state when navigating directly to /room/:id (Xms)

  1 passing
```

If the test redirects to `/` instead: the backend's in-memory store may not have the game (e.g. backend restarted between `cy.createGame()` and the re-visit). Ensure the stack stays up for the full test run.

- [ ] **Step 3: Commit**

```bash
git add frontend/cypress/specs/rehydrate-game.cy.ts
git commit -m "test: add Flow B acceptance test — rehydrate game from URL"
```

---

## Task 7: Flow C — send-card spec

Validates: clicking Send publishes a STOMP message, the backend processes it, broadcasts the updated state, and the frontend reflects the change.

**Prerequisite:** Stack is running.

**Files:**
- Create: `frontend/cypress/specs/send-card.cy.ts`

- [ ] **Step 1: Write the spec**

`frontend/cypress/specs/send-card.cy.ts`:

```ts
describe('Send card', () => {
  it('decrements player hand count and adds a card to the pile', () => {
    cy.createGame();

    cy.contains('button', 'Send').click();

    // Player 0 sent one card: hand drops from 26 to 25.
    cy.get('[data-cy="player-card-count"]').should('contain.text', '25');

    // Pile has at least one card (may have more if AI acted within 2100ms, which is unlikely).
    cy.get('[data-cy="pile-card-count"]')
      .invoke('text')
      .then((text) => parseInt(text.trim(), 10))
      .should('be.gte', 1);
  });
});
```

- [ ] **Step 2: Run the spec**

```bash
cd frontend && npm run cy:run -- --spec cypress/specs/send-card.cy.ts
```

Expected output:
```
  Send card
    ✓ decrements player hand count and adds a card to the pile (Xms)

  1 passing
```

If the pile count assertion is flaky (AI acted before Cypress checked): the Training difficulty window (2100ms) may be insufficient on a slow machine. Increase the difficulty seed to keep Training, or add a `cy.wait(100)` before the pile assertion to let the server response arrive before the AI fires.

- [ ] **Step 3: Run all specs together to confirm no interference**

```bash
cd frontend && npm run cy:run
```

Expected output:
```
  3 passing
```

- [ ] **Step 4: Commit**

```bash
git add frontend/cypress/specs/send-card.cy.ts
git commit -m "test: add Flow C acceptance test — send card updates pile via WebSocket"
```
