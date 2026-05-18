# Game Sessions — Per-Room Channel Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Isolate WebSocket game events per room (`/topic/game/{gameId}`) and make `GameScreen` refresh-safe by rehydrating state from a new REST endpoint on mount.

**Architecture:** The backend broadcasts action events (SEND/SLAP/GRAB) to `/topic/game/{gameId}` instead of the shared `/topic/game/`. A new `GET /api/game/{id}` REST endpoint returns the full current game state. On mount, `GameScreen` reads the game ID from the URL param, fetches initial state via REST, then subscribes to the per-game WebSocket channel. The CREATE event stays on `/topic/game` (needed to learn the game ID before navigating).

**Tech Stack:** Java 21 / Spring Boot (Maven), JUnit 5, Hamcrest, Mockito · Vue 3 / TypeScript / Pinia / STOMP.js / SockJS

---

## File Map

**Backend — Modify:**
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameMessagingService.java` — uncomment per-game channel destination

**Backend — Create:**
- `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java` — new `GET /api/game/{id}` endpoint
- `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameMessagingServiceTest.java` — unit test for channel routing
- `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java` — integration test for REST endpoint

**Frontend — Modify:**
- `frontend/src/service/WebSocketService.ts` — add `subscribeToGame` / `unsubscribeFromGame`, reconnect-aware
- `frontend/src/state/BatailleCorse.store.ts` — add `hydrate(id, gameState)` action
- `frontend/src/view/alpha/GameScreen.vue` — read route param, fetch state on mount, subscribe to per-game channel

---

## Task 1: Fix per-game channel in GameMessagingService

**Files:**
- Test: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameMessagingServiceTest.java`
- Modify: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameMessagingService.java`

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameMessagingServiceTest.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameMessagingServiceTest {

    @Test
    void givenGameId_whenSendToGame_thenBroadcastsToPerGameChannel() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessagingService service = new GameMessagingService(template);

        service.sendToGame("abc-123", "payload");

        verify(template).convertAndSend("/topic/game/abc-123", (Object) "payload");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && mvn test -Dtest=GameMessagingServiceTest
```

Expected: FAIL — `Wanted but not invoked: template.convertAndSend("/topic/game/abc-123", ...)`

- [ ] **Step 3: Fix GameMessagingService**

In `GameMessagingService.java`, replace the `destination` method body:

```java
private String destination(String gameId) {
    return "/topic/game/" + gameId;
}
```

(Remove the commented line and the old `return "/topic/game/";`.)

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && mvn test -Dtest=GameMessagingServiceTest
```

Expected: PASS

- [ ] **Step 5: Run the full test suite to catch regressions**

```bash
cd backend && mvn test
```

Expected: All tests pass. (The `BatailleCorseWebSocketControllerIT` is `@Disabled` and will be skipped.)

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameMessagingService.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameMessagingServiceTest.java
git commit -m "feat: broadcast game events to per-room channel /topic/game/{gameId}"
```

---

## Task 2: Add GET /api/game/{id} REST endpoint

**Files:**
- Create: `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java`
- Create: `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java`

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.junit.jupiter.api.Test;
import org.kevinkib.bataillecorse.websocket.presentation.v1.api.Response;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.event.CreateEventData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameRestControllerIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BatailleCorseWebSocketController wsController;

    @Test
    void givenUnknownId_whenGetGame_thenReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/game/unknown-id", String.class);

        assertThat(response.getStatusCode(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    @SuppressWarnings("unchecked")
    void givenExistingGame_whenGetGame_thenReturnsGameStateWithTwoPlayers() {
        Response createResponse = wsController.createGame();
        CreateEventData createData = (CreateEventData) createResponse.getEventData();
        String gameId = createData.game().getId();

        ResponseEntity<Map> response = restTemplate.getForEntity("/api/game/" + gameId, Map.class);

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        List<Map<String, Object>> players = (List<Map<String, Object>>) response.getBody().get("players");
        assertThat(players, hasSize(2));
        for (Map<String, Object> player : players) {
            assertThat(player.get("nbCards"), is(26));
        }
        Map<String, Object> pile = (Map<String, Object>) response.getBody().get("pile");
        assertThat((List<?>) pile.get("cards"), empty());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=GameRestControllerIT
```

Expected: FAIL — `404` returned for both tests because the endpoint doesn't exist yet.

- [ ] **Step 3: Create GameRestController**

Create `backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java`:

```java
package org.kevinkib.bataillecorse.websocket.presentation.v1;

import org.kevinkib.bataillecorse.core.domain.BatailleCorse;
import org.kevinkib.bataillecorse.core.domain.BatailleCorseId;
import org.kevinkib.bataillecorse.sessionmanagement.application.InvalidGameIdException;
import org.kevinkib.bataillecorse.sessionmanagement.application.SessionService;
import org.kevinkib.bataillecorse.websocket.presentation.v1.dto.BatailleCorseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GameRestController {

    private final SessionService sessionService;

    public GameRestController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/game/{id}")
    public ResponseEntity<BatailleCorseDto> getGame(@PathVariable String id) {
        try {
            BatailleCorse game = sessionService.getGame(new BatailleCorseId(id));
            return ResponseEntity.ok(new BatailleCorseDto(game));
        } catch (InvalidGameIdException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd backend && mvn test -Dtest=GameRestControllerIT
```

Expected: PASS

- [ ] **Step 5: Run the full test suite to catch regressions**

```bash
cd backend && mvn test
```

Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestController.java \
        backend/src/test/java/org/kevinkib/bataillecorse/websocket/presentation/v1/GameRestControllerIT.java
git commit -m "feat: add GET /api/game/{id} endpoint for game state rehydration"
```

---

## Task 3: Add hydrate action to BatailleCorse store

**Files:**
- Modify: `frontend/src/state/BatailleCorse.store.ts`

- [ ] **Step 1: Add the hydrate function**

In `BatailleCorse.store.ts`, add this function inside the store definition (after the `create` function, before `send`):

```typescript
function hydrate(id: string, gameState: BatailleCorse) {
  gameId.value = id;
  state.value = gameState;
}
```

- [ ] **Step 2: Export hydrate from the store**

In the `return` block at the bottom of the store, add `hydrate`:

```typescript
return {
  state,
  gameId,
  lastSend,
  lastGrab,
  lastSlap,
  lastSuccessfulSlap,
  lastErroneousSlap,
  create,
  hydrate,
  send,
  slap,
  grab,
  onResponse,
  notifyAnimationComplete,
  cancelAutoGrab,
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/state/BatailleCorse.store.ts
git commit -m "feat: add hydrate action to store for REST-based state rehydration"
```

---

## Task 4: Update WebSocketService for dynamic per-game subscription

**Files:**
- Modify: `frontend/src/service/WebSocketService.ts`

- [ ] **Step 1: Replace WebSocketService.ts with the updated version**

Replace the entire file content:

```typescript
import SockJS from 'sockjs-client/dist/sockjs';
import { Client } from '@stomp/stompjs';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

class WebSocketService {

  private readonly enableLogs = false;
  private readonly connectUrl = '/connect';

  private client!: Client;
  private currentGameId: string | null = null;
  private currentGameSubscription: { unsubscribe: () => void } | null = null;

  public init() {
    this.log("Creating SockJS...");
    const factory = () => {
      this.log("Creating SockJS connection to " + this.connectUrl);
      return new SockJS(this.connectUrl);
    };

    const stompClient = new Client({
      webSocketFactory: factory,
      reconnectDelay: 3000,
      debug: (str) => this.log("[STOMP DEBUG]", str),
      onConnect: (frame) => {
        this.log('[STOMP] Connected:', frame);

        // Generic channel: receives CREATE events so the client learns the game ID.
        stompClient.subscribe('/topic/game', message => {
          const response = JSON.parse(message.body);
          useBatailleCorseStore().onResponse(response);
        });

        // Re-subscribe to per-game channel after reconnect.
        if (this.currentGameId) {
          this.doSubscribeToGame(this.currentGameId);
        }
      },
      onDisconnect: () => {
        this.log('[STOMP] Disconnected — will reconnect in 3s');
      },
      onStompError: (frame) => {
        console.error('[STOMP] Error:', frame.headers['message']);
        console.error('[STOMP] Details:', frame.body);
      },
    });

    this.log("Activating STOMP client...");
    stompClient.activate();

    this.client = stompClient;
  }

  public subscribeToGame(gameId: string) {
    this.currentGameId = gameId;
    if (this.client?.connected) {
      this.doSubscribeToGame(gameId);
    }
  }

  public unsubscribeFromGame() {
    this.currentGameSubscription?.unsubscribe();
    this.currentGameSubscription = null;
    this.currentGameId = null;
  }

  public publish(destination: string, body?: any) {
    this.client.publish({ destination, body });
  }

  private doSubscribeToGame(gameId: string) {
    this.currentGameSubscription?.unsubscribe();
    this.currentGameSubscription = this.client.subscribe(`/topic/game/${gameId}`, message => {
      const response = JSON.parse(message.body);
      useBatailleCorseStore().onResponse(response);
    });
  }

  private log(message?: any, ...optionalParams: any[]) {
    if (this.enableLogs) {
      console.log(message, ...optionalParams);
    }
  }

}

const service = new WebSocketService();

export default service;
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/service/WebSocketService.ts
git commit -m "feat: add subscribeToGame/unsubscribeFromGame to WebSocketService"
```

---

## Task 5: Update GameScreen to use URL param and fetch state on mount

**Files:**
- Modify: `frontend/src/view/alpha/GameScreen.vue`

- [ ] **Step 1: Add imports to the script section**

In `GameScreen.vue`, update the import line that currently reads:

```typescript
import { computed, nextTick, onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue';
```

to:

```typescript
import { computed, nextTick, onBeforeUnmount, onMounted, useTemplateRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import webSocketService from '../../service/WebSocketService';
import type BatailleCorse from '../../model/BatailleCorse';
```

- [ ] **Step 2: Add route and router setup after the store declarations**

After the line `const settingsStore = useSettingsStore();` near the top of `<script setup>`, add:

```typescript
const route = useRoute();
const router = useRouter();
```

- [ ] **Step 3: Replace the empty onMounted with the fetch-and-subscribe logic**

Replace:

```typescript
onMounted(() => {
});
```

with:

```typescript
onMounted(async () => {
  const gameId = route.params.id as string;
  const response = await fetch(`/api/game/${gameId}`);
  if (response.status === 404) {
    router.replace('/');
    return;
  }
  const gameState = await response.json() as BatailleCorse;
  batailleCorseStore.hydrate(gameId, gameState);
  webSocketService.subscribeToGame(gameId);
});
```

- [ ] **Step 4: Add unsubscribeFromGame to onBeforeUnmount**

Replace:

```typescript
onBeforeUnmount(() => {
  animation.cancelAllAnimations();
  batailleCorseStore.cancelAutoGrab();
});
```

with:

```typescript
onBeforeUnmount(() => {
  animation.cancelAllAnimations();
  batailleCorseStore.cancelAutoGrab();
  webSocketService.unsubscribeFromGame();
});
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/view/alpha/GameScreen.vue
git commit -m "feat: GameScreen reads game ID from URL, fetches state on mount, subscribes to per-game channel"
```

---

## Task 6: Smoke test end-to-end

- [ ] **Step 1: Start the backend**

```bash
cd backend && mvn spring-boot:run
```

- [ ] **Step 2: Start the frontend**

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: Verify normal game flow**

1. Navigate to `/create`
2. Click "Deal Cards"
3. Verify you land on `/room/{uuid}` and the game loads (cards visible, counters correct)
4. Play a card — verify the pile updates
5. Check the browser's Network tab: WebSocket messages should arrive on `/topic/game/{uuid}`, not `/topic/game/`

- [ ] **Step 4: Verify refresh-safe behaviour**

1. While on `/room/{uuid}`, hard-refresh the page (Ctrl+F5)
2. Verify the game reloads correctly — same state, playable

- [ ] **Step 5: Verify 404 redirect**

1. Navigate directly to `/room/not-a-real-id`
2. Verify you are redirected to `/`
