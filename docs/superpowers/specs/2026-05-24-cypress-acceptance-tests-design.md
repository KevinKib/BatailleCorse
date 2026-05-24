# Cypress Acceptance Tests — Design Spec

**Date:** 2026-05-24  
**Status:** Approved

---

## Goal

Add end-to-end acceptance tests using Cypress to validate that the WebSocket game flow works correctly and protect against regressions in the app's basic functionality. The existing test suite covers domain logic and backend layers only — there is no frontend or full-stack test coverage.

---

## Approach

Full-stack: Cypress drives a real browser against a real running stack (frontend + backend). No mocking of WebSocket or HTTP. A dedicated Docker Compose file starts the stack in a Spring test profile, which establishes the hook for disabling the backend AI when it is eventually implemented there.

---

## File Layout

```
frontend/
  cypress.config.ts             ← Cypress configuration
  cypress/
    specs/
      create-game.cy.ts         ← Flow A: create game via WebSocket, navigate to game screen
      rehydrate-game.cy.ts      ← Flow B: navigate directly to /room/:id, REST hydration works
      send-card.cy.ts           ← Flow C: player sends a card, pile updates
    support/
      commands.ts               ← cy.createGame() shared command
      e2e.ts                    ← global beforeEach (localStorage seeding)

docker-compose.e2e.yml          ← repo root, real stack with SPRING_PROFILES_ACTIVE=test
backend/src/main/resources/
  application-test.properties   ← Spring test profile, empty now, future AI flag goes here
```

---

## Test Infrastructure

### docker-compose.e2e.yml

Identical to `docker-compose.dev.yml` with one addition on the backend service:

```yaml
environment:
  - SPRING_PROFILES_ACTIVE=test
```

### application-test.properties

Empty for now. When the AI moves to the backend, add `game.ai.enabled=false` (or equivalent) here — the profile is already wired in.

### cypress.config.ts

```ts
import { defineConfig } from 'cypress';

export default defineConfig({
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/specs/**/*.cy.ts',
  },
});
```

### cypress/support/e2e.ts (global setup)

```ts
beforeEach(() => {
  localStorage.setItem('bc_difficulty', '0'); // Training mode — 2100ms AI reaction delay
});
```

Setting difficulty to 0 (Training, 2100ms) ensures the frontend AI does not fire during Cypress assertions. When the AI moves to the backend, this line can be removed and `application-test.properties` will handle it instead.

---

## AI Handling

The AI is currently on the frontend (`AI.ts`, called from `BatailleCorse.store.ts`). Its reaction time is driven by `DIFFICULTY[settingsStore.difficulty].reactionTime`, read from `localStorage` key `bc_difficulty`.

Forcing `bc_difficulty=0` gives 2100ms before the AI acts. Cypress assertions on game state complete in under 500ms — safe margin with no code changes needed.

The `docker-compose.e2e.yml` + `application-test.properties` pattern is established now so that when AI moves to the backend, disabling it in tests costs a single line in the properties file.

---

## Shared Command

**`cypress/support/commands.ts`** — `cy.createGame()`

Navigates through the UI to create a game and land on `/room/:id`. Used as setup in Flows B and C.

```ts
Cypress.Commands.add('createGame', () => {
  cy.visit('/create');
  cy.contains('button', 'Deal Cards').click();
  cy.url().should('match', /\/room\/.+/);
});
```

---

## Test Flows

### Flow A — `create-game.cy.ts`

Validates that the full CREATE flow works: lobby → create screen → WebSocket CREATE event received → store navigates to game screen.

1. Visit `/`
2. Assert lobby is visible ("New Game" button exists)
3. Click "New Game" — assert URL is `/create`
4. Click "Deal Cards"
5. Assert URL matches `/room/:uuid` (WebSocket CREATE event was received and navigation dispatched)
6. Assert pile card counter shows 0
7. Assert both player card counters show 26

### Flow B — `rehydrate-game.cy.ts`

Validates that navigating directly to `/room/:id` (e.g. refreshing the page) loads game state via REST and re-establishes the WebSocket subscription rather than redirecting to `/`.

1. Use `cy.createGame()` to create a game and land on `/room/:id`
2. Save the current URL
3. Navigate to `/`
4. Visit the saved URL directly
5. Assert URL is still `/room/:id` (not redirected away)
6. Assert both card counters show 26

### Flow C — `send-card.cy.ts`

Validates that a player action (SEND) is published over WebSocket, processed by the backend, and the resulting state update is broadcast back and reflected in the UI.

1. Use `cy.createGame()` to land on `/room/:id`
2. Click "Send"
3. Assert player 0's card counter shows 25
4. Assert pile card counter shows ≥ 1 (≥ not exact, AI may have reacted)

---

## Running Tests

```bash
# Start the full stack with test profile
docker compose -f docker-compose.e2e.yml up

# In a second terminal, from frontend/
npm run cy:open   # interactive
npm run cy:run    # headless CI
```

Add to `frontend/package.json`:

```json
"cy:open": "cypress open",
"cy:run": "cypress run"
```

---

## Out of Scope

- CI integration (GitHub Actions or similar) — can be added after tests are stable locally
- Flows D (successful slap) and E (erroneous slap) — deferred, AI timing makes these harder to assert cleanly until AI is on the backend and can be disabled via the test profile
