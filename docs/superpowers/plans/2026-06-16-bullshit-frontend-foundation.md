# Bullshit Frontend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal but real Bullshit game frontend — create a 2-player game, share a join link, opponent joins, each player sees only their own hand over a per-seat topic, discards 1–4 cards as the dictated claim, calls bullshit to reveal, and reaches a win/lose state.

**Architecture:** A parallel Bullshit stack mirroring BatailleCorse: new `Bullshit.store.ts` + `BullshitSession.ts` + `model/bullshit/*` + `BullshitGameScreen.vue` + `BullshitStartGame.vue` + routes under `/games/bullshit`, plus a per-seat subscribe (with a `token` STOMP header) and an optional lobby listener added to the shared `WebSocketService`. BatailleCorse is untouched. One thin backend addition: `POST /api/bullshit/game/{id}/join`.

**Tech Stack:** Backend Java 17 + Spring Boot (STOMP) + JUnit 5/Hamcrest + Maven. Frontend Vue 3 + TypeScript + Pinia + Vue Router + Vite, tested with Vitest + @vue/test-utils + happy-dom.

---

## Conventions for every task

**Backend build/test (run from repo root in the worktree).** No Maven wrapper; use the IntelliJ-bundled Maven + JBR (bash):
```bash
export JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.3\jbr"
MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2025.3.3/plugins/maven/lib/maven3/bin/mvn"
```
- Single backend test class: `"$MVN" -f backend/pom.xml test -Dtest=ClassName`
- Full backend suite: `"$MVN" -f backend/pom.xml clean test`

**Frontend build/test (run from `frontend/`).** The worktree may lack `node_modules`; run `npm install` once first if `npm test`/`npm run build` fail to find deps.
- Run a single test file: `cd frontend && npx vitest run src/path/to/File.test.ts`
- Run all frontend tests: `cd frontend && npm test`
- The real type-check + bundling gate: `cd frontend && npm run build` (bare `vue-tsc` gives false passes — `vite build` is the gate).

- `LF will be replaced by CRLF` git warnings on Windows are normal. After writing any new file, `git add` it immediately. End every commit message with the trailer `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

**Testing rules:** no Mockito on backend domain classes (test-double subclasses of infra like `GameMessagingService`/`BullshitStateBroadcaster` are fine); name tests `givenX_whenY_thenZ` (backend) / behavioral `it('...')` (frontend). Frontend session/store tests use a fake WebSocket port object and `vi.stubGlobal('fetch', ...)`, mirroring `frontend/src/application/GameSession.test.ts`.

**Key backend facts (verified):**
- `BullshitRestController` already exists (`@RequestMapping("/api/bullshit")`, ctor takes `SessionService`) with `GET /game/{id}`.
- `SessionService.joinGame(GameId, String)` returns `JoinResult` (`playerId()` → `PlayerId`, `token()` → `SessionToken`); it seats `PlayerId(1)` and throws `SeatUnavailableException` if seat 1 is already claimed.
- `BullshitStateBroadcaster.broadcast(Bullshit, String eventType, EventData, String message)` fans per-seat to all seats.
- `LifecycleEventType.JOIN` exists; `EmptyEventData` is `record EmptyEventData() implements EventData`.
- `JoinResponseDto(int playerId, String token)` and `JoinGamePayload(String name)` exist.
- `sessionService.getGame(GameId, Bullshit.class)` throws `InvalidGameIdException` for unknown ids; `new GameId(String)` throws `IllegalArgumentException` for malformed.

**Key frontend facts (verified):**
- `WebSocketService` is a default-exported singleton (`frontend/src/service/WebSocketService.ts`). Its `onConnect` subscribes `/topic/game` and routes to the BatailleCorse store; per-game subscribe is `doSubscribeToGame`. `publish(destination, body?)` sends to `/app/...`.
- The BatailleCorse `CREATE` handler guards with `if (!this.pendingCreate) return;`, so a foreign (Bullshit) `CREATE` reaching the BC store is ignored unless BC is itself mid-create.
- Generic `Card` type: `frontend/src/model/Card.ts` = `{ rank: string; suit: string; name: string }`.
- Router in `frontend/src/main.ts`, `BASE = '/games/bataillecorse'`.

---

## File structure

**Backend — modified:**
- `backend/.../bullshit/presentation/BullshitRestController.java` — add `POST /game/{id}/join`; inject `BullshitStateBroadcaster`.
- `backend/.../config/AppConfig.java` — no change (controller is component-scanned; broadcaster bean already exists).

**Frontend — created:**
- `frontend/src/model/bullshit/BullshitState.ts` — per-seat state + sealed sub-types (mirror of `BullshitDto`).
- `frontend/src/model/bullshit/BullshitEvents.ts` — `DiscardEventData`, `CallBullshitEventData`, `BullshitResponse`.
- `frontend/src/application/BullshitSession.ts` — action/transport orchestrator + `BullshitWebSocketPort`/`BullshitSessionCallbacks` interfaces + `BullshitSessionEvent` union.
- `frontend/src/state/Bullshit.store.ts` — Pinia setup store.
- `frontend/src/composables/useBullshitBootstrap.ts` — rehydrate-on-mount.
- `frontend/src/view/bullshit/BullshitStartGame.vue` — create/join form.
- `frontend/src/view/bullshit/BullshitGameScreen.vue` — the minimal game screen.

**Frontend — modified:**
- `frontend/src/service/WebSocketService.ts` — add `subscribeToSeat`, `unsubscribeFromSeat`, `setLobbyListener`; reconnect replay; fan the existing `/topic/game` handler to the lobby listener.
- `frontend/src/main.ts` — register Bullshit routes.
- `frontend/src/view/alpha/LobbyView.vue` — add a "Play Bullshit" link.

---

## Task 1: Backend — `POST /api/bullshit/game/{id}/join`

**Files:**
- Modify: `backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestController.java`
- Test: `backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestControllerTest.java` (extend)

- [ ] **Step 1: Add failing tests**

Append these tests to `BullshitRestControllerTest` (the class already has a `sessionService` + `controller` set up in `@BeforeEach`; the controller constructor will gain a second arg — update `setUp` accordingly). Add a recording broadcaster double and these imports:

```java
import org.kevinkib.cardgames.bullshit.presentation.dto.event.DiscardEventData; // (not needed) remove if unused
import org.kevinkib.cardgames.game.GameId;
import org.kevinkib.cardgames.presentation.GameMessagingService;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.sessionmanagement.domain.GameMode;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
```

Replace the `@BeforeEach setUp` so the controller gets a real `BullshitStateBroadcaster` backed by a recording `GameMessagingService` (so we can assert the per-seat JOIN fan-out):

```java
    private RecordingMessaging messaging;

    static final class RecordingMessaging extends GameMessagingService {
        final List<Integer> seats = new ArrayList<>();
        final List<String> eventTypes = new ArrayList<>();

        RecordingMessaging() { super(null); }

        @Override
        public void sendToSeat(org.kevinkib.cardgames.game.GameId gameId,
                               org.kevinkib.cardgames.game.PlayerId seat, Object payload) {
            seats.add(seat.id());
            eventTypes.add(((org.kevinkib.cardgames.presentation.api.Response) payload).getEventType());
        }
    }

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                new InMemorySessionRepository(Clock.systemUTC()),
                new GameFactories(List.of(new BullshitFactory())));
        messaging = new RecordingMessaging();
        controller = new BullshitRestController(sessionService, new BullshitStateBroadcaster(messaging));
    }
```

Add tests:

```java
    @Test
    void givenOpenSeat_whenJoin_thenReturnsSeat1TokenAndBroadcastsJoinToAllSeats() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.MULTIPLAYER);
        String id = game.getId().uuid().toString();

        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(id, new org.kevinkib.cardgames.presentation.api.JoinGamePayload("Bob"));

        assertThat(response.getStatusCode().value(), is(200));
        assertThat(response.getBody().playerId(), is(1));
        assertThat(response.getBody().token(), is(notNullValue()));
        // per-seat JOIN fan-out to both seats
        assertThat(messaging.seats.size(), is(2));
        assertThat(messaging.eventTypes, everyItem(is("JOIN")));
    }

    @Test
    void givenSeatAlreadyTaken_whenJoin_thenConflict() {
        Bullshit game = (Bullshit) sessionService.createGame("bullshit", 2, GameMode.MULTIPLAYER);
        String id = game.getId().uuid().toString();
        controller.joinGame(id, new org.kevinkib.cardgames.presentation.api.JoinGamePayload("Bob"));

        ResponseEntity<JoinResponseDto> second =
                controller.joinGame(id, new org.kevinkib.cardgames.presentation.api.JoinGamePayload("Carol"));

        assertThat(second.getStatusCode().value(), is(409));
    }

    @Test
    void givenUnknownGame_whenJoin_thenNotFound() {
        ResponseEntity<JoinResponseDto> response =
                controller.joinGame(java.util.UUID.randomUUID().toString(), null);

        assertThat(response.getStatusCode().value(), is(404));
    }
```

Add Hamcrest imports if missing: `everyItem`, `notNullValue`.

- [ ] **Step 2: Run to verify it fails**

Run: `"$MVN" -f backend/pom.xml test -Dtest=BullshitRestControllerTest`
Expected: FAIL — `BullshitRestController` has no `joinGame` and the 2-arg constructor doesn't exist (compile error).

- [ ] **Step 3: Implement**

Edit `BullshitRestController.java`: add the broadcaster dependency and the join mapping.

New imports:
```java
import org.kevinkib.cardgames.presentation.api.JoinGamePayload;
import org.kevinkib.cardgames.presentation.dto.JoinResponseDto;
import org.kevinkib.cardgames.presentation.dto.event.EmptyEventData;
import org.kevinkib.cardgames.presentation.dto.event.LifecycleEventType;
import org.kevinkib.cardgames.sessionmanagement.application.JoinResult;
import org.kevinkib.cardgames.sessionmanagement.application.SeatUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

Change the field/constructor to also take the broadcaster:
```java
    private final SessionService sessionService;
    private final BullshitStateBroadcaster broadcaster;

    public BullshitRestController(SessionService sessionService, BullshitStateBroadcaster broadcaster) {
        this.sessionService = sessionService;
        this.broadcaster = broadcaster;
    }
```

Add the mapping (keep the existing `getGame`):
```java
    @PostMapping("/game/{id}/join")
    public ResponseEntity<JoinResponseDto> joinGame(@PathVariable String id,
                                                    @RequestBody(required = false) JoinGamePayload payload) {
        try {
            GameId gameId = new GameId(id);
            Bullshit game = sessionService.getGame(gameId, Bullshit.class);
            String name = (payload != null) ? payload.name() : null;
            JoinResult result = sessionService.joinGame(gameId, name);
            sessionService.touch(gameId);

            broadcaster.broadcast(game, LifecycleEventType.JOIN.toString(), new EmptyEventData(),
                    "Player " + result.playerId().id() + " joined.");

            return ResponseEntity.ok(new JoinResponseDto(
                    result.playerId().id(), result.token().uuid().toString()));
        } catch (InvalidGameIdException | IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (SeatUnavailableException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `"$MVN" -f backend/pom.xml test -Dtest=BullshitRestControllerTest`
Expected: PASS (the original `getGame` tests still pass — they construct the controller; update those call sites too if any construct it with the old 1-arg ctor: change to `new BullshitRestController(sessionService, new BullshitStateBroadcaster(messaging))`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestController.java backend/src/test/java/org/kevinkib/cardgames/bullshit/presentation/BullshitRestControllerTest.java
git commit -m "feat(bullshit): REST join endpoint with per-seat JOIN broadcast"
```

---

## Task 2: Frontend — per-seat subscribe + lobby listener in `WebSocketService`

**Files:**
- Modify: `frontend/src/service/WebSocketService.ts`

This is thin STOMP glue to a third-party client; it is verified by `npm run build` and exercised end-to-end by later tasks (the session is unit-tested against a fake port). No dedicated unit test.

- [ ] **Step 1: Add the seat-subscription + lobby-listener fields and methods**

In `WebSocketService.ts`, add fields next to the existing ones:
```ts
  private seatSubscription: { gameId: string; seat: number; token: string; onMessage: (r: any) => void } | null = null;
  private currentSeatSub: { unsubscribe: () => void } | null = null;
  private lobbyListener: ((response: any) => void) | null = null;
```

In `onConnect`, change the existing `/topic/game` handler to also notify the lobby listener, and replay the seat subscription after reconnect. Replace the existing handler block:
```ts
        // Generic channel: receives CREATE events. Routes to the BatailleCorse store
        // (which ignores foreign CREATEs unless it is itself mid-create) and to an
        // optional lobby listener (used by Bullshit to catch its own CREATE ack).
        stompClient.subscribe('/topic/game', message => {
          const response = JSON.parse(message.body);
          useBatailleCorseStore().onResponse(response);
          this.lobbyListener?.(response);
        });

        if (this.currentGameId) {
          this.doSubscribeToGame(this.currentGameId);
        }

        if (this.seatSubscription) {
          this.doSubscribeToSeat();
        }
```
(Keep the existing presence re-assert block below it unchanged.)

Add public/private methods (near `subscribeToGame`):
```ts
  public setLobbyListener(fn: ((response: any) => void) | null) {
    this.lobbyListener = fn;
  }

  public subscribeToSeat(gameId: string, seat: number, token: string, onMessage: (response: any) => void) {
    this.seatSubscription = { gameId, seat, token, onMessage };
    if (this.client?.connected) {
      this.doSubscribeToSeat();
    }
  }

  public unsubscribeFromSeat() {
    this.currentSeatSub?.unsubscribe();
    this.currentSeatSub = null;
    this.seatSubscription = null;
  }

  private doSubscribeToSeat() {
    if (!this.seatSubscription) return;
    this.currentSeatSub?.unsubscribe();
    const { gameId, seat, token, onMessage } = this.seatSubscription;
    this.currentSeatSub = this.client.subscribe(
      `/topic/game/${gameId}/seat/${seat}`,
      message => onMessage(JSON.parse(message.body)),
      { token },
    );
  }
```

- [ ] **Step 2: Verify it builds**

Run: `cd frontend && npm run build`
Expected: build succeeds with no type errors. (If `node_modules` is missing, run `npm install` first.)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/service/WebSocketService.ts
git commit -m "feat(frontend): per-seat STOMP subscribe with token header + lobby listener"
```

---

## Task 3: Frontend — Bullshit model types

**Files:**
- Create: `frontend/src/model/bullshit/BullshitState.ts`
- Create: `frontend/src/model/bullshit/BullshitEvents.ts`

Pure TypeScript types (mirrors of the backend per-seat DTOs); verified by `npm run build` and used by later tasks.

- [ ] **Step 1: Create `BullshitState.ts`**

```ts
import type Card from '../Card';

export interface BullshitPlayer {
  id: string;
  handCount: number;
  isCurrentPlayer: boolean;
}

export type TableView =
  | { state: 'NO_CLAIM' }
  | { state: 'CLAIM'; claimantId: string; claimedTargetLabel: string; count: number };

export type PendingWinnerView =
  | { state: 'NONE' }
  | { state: 'PENDING'; playerId: string };

export type OutcomeView =
  | { status: 'ONGOING' }
  | { status: 'FINISHED'; winnerId: string };

export interface BullshitState {
  id: string;
  gameType: string;
  myHand: Card[];
  availableActions: string[];
  players: BullshitPlayer[];
  currentTarget: { label: string };
  discardPileSize: number;
  table: TableView;
  pendingWinner: PendingWinnerView;
  outcome: OutcomeView;
}
```

- [ ] **Step 2: Create `BullshitEvents.ts`**

```ts
import type Card from '../Card';
import type { BullshitState } from './BullshitState';

export interface DiscardEventData {
  claimantSeat: number;
  claimedTargetLabel: string;
  count: number;
}

export interface CallBullshitEventData {
  callerSeat: number;
  claimantSeat: number;
  truthful: boolean;
  pickerSeat: number;
  revealedCards: Card[];
}

export interface BullshitResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: BullshitState | null;
}
```

- [ ] **Step 3: Verify it builds**

Run: `cd frontend && npm run build`
Expected: success.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/model/bullshit/BullshitState.ts frontend/src/model/bullshit/BullshitEvents.ts
git commit -m "feat(frontend): Bullshit per-seat state and event types"
```

---

## Task 4: Frontend — `BullshitSession`

**Files:**
- Create: `frontend/src/application/BullshitSession.ts`
- Test: `frontend/src/application/BullshitSession.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import BullshitSession, { type BullshitWebSocketPort, type BullshitSessionCallbacks, type BullshitSessionEvent } from './BullshitSession';
import type { BullshitState } from '../model/bullshit/BullshitState';

function sampleState(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    id: 'g1', gameType: 'bullshit',
    myHand: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }],
    availableActions: ['DISCARD'],
    players: [
      { id: '0', handCount: 1, isCurrentPlayer: true },
      { id: '1', handCount: 1, isCurrentPlayer: false },
    ],
    currentTarget: { label: 'ACE' },
    discardPileSize: 0,
    table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

function makeSession() {
  const events: BullshitSessionEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const seatSubs: { gameId: string; seat: number; token: string }[] = [];
  let lobbyFn: ((r: any) => void) | null = null;
  let seatFn: ((r: any) => void) | null = null;

  const webSocket: BullshitWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (gameId, seat, token, onMessage) => { seatSubs.push({ gameId, seat, token }); seatFn = onMessage; },
    setLobbyListener: (fn) => { lobbyFn = fn; },
  };
  const callbacks: BullshitSessionCallbacks = { onEvent: (e) => events.push(e) };
  const session = new BullshitSession(webSocket, callbacks);
  return {
    session, events, published, seatSubs,
    fireLobby: (r: any) => lobbyFn?.(r),
    fireSeat: (r: any) => seatFn?.(r),
  };
}

describe('BullshitSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('create publishes nbPlayers=2 MULTIPLAYER and registers a lobby listener', () => {
    const { session, published } = makeSession();
    session.create('Alice');
    expect(published).toContainEqual({ dest: '/app/bullshit/create', body: JSON.stringify({ nbPlayers: 2, mode: 'MULTIPLAYER', name: 'Alice' }) });
  });

  it('on its own CREATE ack, subscribes seat 0 with the token and persists it', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => sampleState() }));
    const { session, seatSubs, fireLobby } = makeSession();
    session.create('Alice');
    fireLobby({ eventType: 'CREATE', eventData: { gameId: 'g1', gameType: 'bullshit', tokens: { '0': 'tok-0' } } });
    await Promise.resolve(); await Promise.resolve();
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 0, token: 'tok-0' });
    expect(JSON.parse(localStorage.getItem('bullshit:tokens:g1')!)).toEqual({ 0: 'tok-0' });
  });

  it('ignores a foreign (non-bullshit) CREATE on the lobby', () => {
    const { session, seatSubs, fireLobby } = makeSession();
    session.create('Alice');
    fireLobby({ eventType: 'CREATE', eventData: { game: { id: 'x' }, gameType: 'bataille-corse' } });
    expect(seatSubs).toHaveLength(0);
  });

  it('join posts to the bullshit join endpoint, subscribes its seat, and hydrates', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ playerId: 1, token: 'tok-1' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => sampleState() });
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs, events } = makeSession();
    await session.join('g1', 'Bob');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/bullshit/game/g1/join');
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 1, token: 'tok-1' });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
  });

  it('discard publishes selected cards to /app/discard', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    const cards = [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }];
    session.discard(cards);
    expect(published).toContainEqual({ dest: '/app/discard', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', cards }) });
  });

  it('callBullshit publishes to /app/callBullshit', () => {
    const { session, published } = makeSession();
    session.restore('g1', 1, 'tok-1');
    session.callBullshit();
    expect(published).toContainEqual({ dest: '/app/callBullshit', body: JSON.stringify({ gameId: 'g1', token: 'tok-1' }) });
  });

  it('on an incoming seat message, emits state-update and the event', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: true, eventType: 'DISCARD', eventData: { claimantSeat: 0, claimedTargetLabel: 'ACE', count: 1 }, message: 'm', state: sampleState() });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
    expect(events).toContainEqual({ type: 'event', eventType: 'DISCARD', eventData: { claimantSeat: 0, claimedTargetLabel: 'ACE', count: 1 }, message: 'm' });
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/application/BullshitSession.test.ts`
Expected: FAIL — `BullshitSession` does not exist.

- [ ] **Step 3: Implement `BullshitSession.ts`**

```ts
import type Card from '../model/Card';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { BullshitResponse } from '../model/bullshit/BullshitEvents';

export interface BullshitWebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToSeat(gameId: string, seat: number, token: string, onMessage: (response: any) => void): void;
  setLobbyListener(fn: ((response: any) => void) | null): void;
}

export type BullshitSessionEvent =
  | { type: 'state-update'; state: BullshitState }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'seat-change'; seat: number }
  | { type: 'event'; eventType: string; eventData: unknown; message: string };

export interface BullshitSessionCallbacks {
  onEvent(event: BullshitSessionEvent): void;
}

export default class BullshitSession {
  private gameId: string | null = null;
  private mySeat = 0;
  private myToken: string | null = null;
  private pendingCreate = false;

  constructor(
    private readonly webSocket: BullshitWebSocketPort,
    private readonly callbacks: BullshitSessionCallbacks,
  ) {}

  create(name?: string): void {
    this.pendingCreate = true;
    this.webSocket.setLobbyListener(r => this.onLobby(r));
    this.webSocket.publish('/app/bullshit/create', JSON.stringify({ nbPlayers: 2, mode: 'MULTIPLAYER', name: name ?? null }));
  }

  private onLobby(response: any): void {
    if (!this.pendingCreate) return;
    if (response?.eventType !== 'CREATE') return;
    const data = response.eventData;
    if (!data || data.gameType !== 'bullshit') return;
    this.pendingCreate = false;
    this.webSocket.setLobbyListener(null);

    const gameId: string = data.gameId;
    const token: string = data.tokens['0'];
    this.bind(gameId, 0, token);
    localStorage.setItem(`bullshit:tokens:${gameId}`, JSON.stringify({ 0: token }));
    void this.hydrate();
  }

  async join(gameId: string, name?: string): Promise<void> {
    const res = await fetch(`/api/bullshit/game/${gameId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name ?? null }),
    });
    if (!res.ok) throw new Error(`Join failed: ${res.status}`);
    const body = await res.json() as { playerId: number; token: string };
    this.bind(gameId, body.playerId, body.token);
    localStorage.setItem(`bullshit:tokens:${gameId}`, JSON.stringify({ [body.playerId]: body.token }));
    await this.hydrate();
  }

  /** Re-attach to a known game/seat (page reload). */
  restore(gameId: string, seat: number, token: string): void {
    this.bind(gameId, seat, token);
  }

  private bind(gameId: string, seat: number, token: string): void {
    this.gameId = gameId;
    this.mySeat = seat;
    this.myToken = token;
    this.webSocket.subscribeToSeat(gameId, seat, token, r => this.onResponse(r));
    this.callbacks.onEvent({ type: 'game-id-change', gameId });
    this.callbacks.onEvent({ type: 'seat-change', seat });
  }

  /** Per-seat REST rehydration (also used right after create/join to show the hand). */
  async hydrate(): Promise<void> {
    if (!this.gameId || this.myToken === null) return;
    const res = await fetch(`/api/bullshit/game/${this.gameId}?token=${this.myToken}`);
    if (res.ok) {
      const state = await res.json() as BullshitState;
      this.callbacks.onEvent({ type: 'state-update', state });
    }
  }

  discard(cards: Card[]): void {
    this.webSocket.publish('/app/discard', JSON.stringify({ gameId: this.gameId, token: this.myToken, cards }));
  }

  callBullshit(): void {
    this.webSocket.publish('/app/callBullshit', JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }

  onResponse(response: BullshitResponse): void {
    if (response.state) {
      this.callbacks.onEvent({ type: 'state-update', state: response.state });
    }
    this.callbacks.onEvent({ type: 'event', eventType: response.eventType, eventData: response.eventData, message: response.message });
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/application/BullshitSession.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/application/BullshitSession.ts frontend/src/application/BullshitSession.test.ts
git commit -m "feat(frontend): BullshitSession transport orchestrator"
```

---

## Task 5: Frontend — `Bullshit.store.ts`

**Files:**
- Create: `frontend/src/state/Bullshit.store.ts`
- Test: `frontend/src/state/Bullshit.store.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBullshitStore } from './Bullshit.store';
import type { BullshitState } from '../model/bullshit/BullshitState';

function state(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    id: 'g1', gameType: 'bullshit',
    myHand: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }, { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }],
    availableActions: ['DISCARD'],
    players: [
      { id: '0', handCount: 2, isCurrentPlayer: true },
      { id: '1', handCount: 5, isCurrentPlayer: false },
    ],
    currentTarget: { label: 'ACE' },
    discardPileSize: 0,
    table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

describe('Bullshit store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('isMyTurn / canDiscard reflect my seat and available actions', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state() });
    expect(store.isMyTurn).toBe(true);
    expect(store.canDiscard).toBe(true);
    expect(store.canCallBullshit).toBe(false);
  });

  it('phase is waiting until a JOIN event arrives, then playing', () => {
    const store = useBullshitStore();
    store.markCreated();          // creator starts in waiting
    store.applyEvent({ type: 'state-update', state: state() });
    expect(store.phase).toBe('waiting');
    store.applyEvent({ type: 'event', eventType: 'JOIN', eventData: {}, message: '' });
    expect(store.phase).toBe('playing');
  });

  it('phase is finished and iWon true when I am the winner', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state({ outcome: { status: 'FINISHED', winnerId: '0' } }) });
    expect(store.phase).toBe('finished');
    expect(store.iWon).toBe(true);
  });

  it('records a CALL_BULLSHIT reveal', () => {
    const store = useBullshitStore();
    const reveal = { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [] };
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });
    expect(store.reveal).toEqual(reveal);
  });

  it('toggleCard selects and deselects from the hand', () => {
    const store = useBullshitStore();
    const card = { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' };
    store.toggleCard(card);
    expect(store.selectedCards).toHaveLength(1);
    store.toggleCard(card);
    expect(store.selectedCards).toHaveLength(0);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/state/Bullshit.store.test.ts`
Expected: FAIL — store does not exist.

- [ ] **Step 3: Implement `Bullshit.store.ts`**

```ts
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

import webSocketService from '../service/WebSocketService';
import BullshitSession, { type BullshitSessionEvent } from '../application/BullshitSession';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { CallBullshitEventData } from '../model/bullshit/BullshitEvents';
import type Card from '../model/Card';

export const useBullshitStore = defineStore('bullshit-store', () => {
  const state = ref<BullshitState | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  const waiting = ref<boolean>(false);
  const reveal = ref<CallBullshitEventData | null>(null);
  const lastMessage = ref<string>('');
  const selectedCards = ref<Card[]>([]);

  const session = new BullshitSession(webSocketService, {
    onEvent(event: BullshitSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: BullshitSessionEvent) {
    switch (event.type) {
      case 'state-update': state.value = event.state; break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        lastMessage.value = event.message;
        if (event.eventType === 'JOIN') waiting.value = false;
        if (event.eventType === 'CALL_BULLSHIT') reveal.value = event.eventData as CallBullshitEventData;
        else reveal.value = null;
        break;
    }
  }

  function markCreated() { waiting.value = true; }

  const me = computed(() => state.value?.players.find(p => p.id === String(mySeat.value)) ?? null);
  const isMyTurn = computed(() => me.value?.isCurrentPlayer ?? false);
  const canDiscard = computed(() => state.value?.availableActions.includes('DISCARD') ?? false);
  const canCallBullshit = computed(() => state.value?.availableActions.includes('CALL_BULLSHIT') ?? false);
  const iWon = computed(() =>
    state.value?.outcome.status === 'FINISHED' && state.value.outcome.winnerId === String(mySeat.value));
  const phase = computed<'connecting' | 'waiting' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    if (waiting.value) return 'waiting';
    return 'playing';
  });

  function toggleCard(card: Card) {
    const i = selectedCards.value.findIndex(c => c.name === card.name);
    if (i >= 0) selectedCards.value.splice(i, 1);
    else if (selectedCards.value.length < 4) selectedCards.value.push(card);
  }
  function clearSelection() { selectedCards.value = []; }

  return {
    state, gameId, mySeat, waiting, reveal, lastMessage, selectedCards,
    me, isMyTurn, canDiscard, canCallBullshit, iWon, phase,
    applyEvent, markCreated, toggleCard, clearSelection,
    create: (name?: string) => { markCreated(); session.create(name); },
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    discard: () => { session.discard(selectedCards.value); clearSelection(); },
    callBullshit: () => session.callBullshit(),
  };
});
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/state/Bullshit.store.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/state/Bullshit.store.ts frontend/src/state/Bullshit.store.test.ts
git commit -m "feat(frontend): Bullshit Pinia store"
```

---

## Task 6: Frontend — `useBullshitBootstrap` (rehydrate on reload)

**Files:**
- Create: `frontend/src/composables/useBullshitBootstrap.ts`
- Test: `frontend/src/composables/useBullshitBootstrap.test.ts`

On reload of `/games/bullshit/room/:id`, read the stored token and re-attach. If no token is stored, redirect home.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { resolveBullshitSession } from './useBullshitBootstrap';

describe('resolveBullshitSession', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('returns the stored seat and token for a game', () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    expect(resolveBullshitSession('g1')).toEqual({ seat: 1, token: 'tok-1' });
  });

  it('returns null when no token is stored', () => {
    expect(resolveBullshitSession('g1')).toBeNull();
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/composables/useBullshitBootstrap.test.ts`
Expected: FAIL — module does not exist.

- [ ] **Step 3: Implement `useBullshitBootstrap.ts`**

```ts
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useBullshitStore } from '../state/Bullshit.store';

/** Reads the persisted seat+token for a game, or null if none stored. */
export function resolveBullshitSession(gameId: string): { seat: number; token: string } | null {
  const stored = localStorage.getItem(`bullshit:tokens:${gameId}`);
  if (!stored) return null;
  const tokens = JSON.parse(stored) as Record<string, string>;
  const seats = Object.keys(tokens).map(Number);
  if (seats.length === 0) return null;
  const seat = seats[0];
  return { seat, token: tokens[String(seat)] };
}

/** On mount: re-attach to the game from localStorage, or redirect home. */
export function useBullshitBootstrap(gameId: string) {
  const router = useRouter();
  const store = useBullshitStore();

  onMounted(async () => {
    const session = resolveBullshitSession(gameId);
    if (!session) {
      router.replace('/games/bullshit/create');
      return;
    }
    store.restore(gameId, session.seat, session.token);
    await store.hydrate();
  });
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `cd frontend && npx vitest run src/composables/useBullshitBootstrap.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/composables/useBullshitBootstrap.ts frontend/src/composables/useBullshitBootstrap.test.ts
git commit -m "feat(frontend): Bullshit reload rehydration composable"
```

---

## Task 7: Frontend — screens, routes, and lobby link

**Files:**
- Create: `frontend/src/view/bullshit/BullshitStartGame.vue`
- Create: `frontend/src/view/bullshit/BullshitGameScreen.vue`
- Test: `frontend/src/view/bullshit/BullshitGameScreen.test.ts`
- Modify: `frontend/src/main.ts`
- Modify: `frontend/src/view/alpha/LobbyView.vue`

- [ ] **Step 1: Write the failing component test**

```ts
import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import BullshitGameScreen from './BullshitGameScreen.vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import type { BullshitState } from '../../model/bullshit/BullshitState';

function playingState(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    id: 'g1', gameType: 'bullshit',
    myHand: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }],
    availableActions: ['DISCARD'],
    players: [
      { id: '0', handCount: 1, isCurrentPlayer: true },
      { id: '1', handCount: 3, isCurrentPlayer: false },
    ],
    currentTarget: { label: 'ACE' },
    discardPileSize: 0,
    table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

const stubs = { RouterLink: true };

describe('BullshitGameScreen', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('disables Discard until a card is selected, then enables it on my turn', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState() });
    store.waiting = false;
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [], stubs } });

    const discardBtn = wrapper.get('[data-test="discard"]');
    expect((discardBtn.element as HTMLButtonElement).disabled).toBe(true);

    await wrapper.get('[data-test="hand-card-0"]').trigger('click');
    expect((discardBtn.element as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows the reveal panel after a CALL_BULLSHIT event', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState() });
    store.waiting = false;
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
      eventData: { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [{ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }] } });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { stubs } });

    expect(wrapper.find('[data-test="reveal"]').exists()).toBe(true);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: FAIL — component does not exist.

- [ ] **Step 3: Create `BullshitGameScreen.vue`**

```vue
<script setup lang="ts">
import { computed } from 'vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { useBullshitBootstrap } from '../../composables/useBullshitBootstrap';
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import type Card from '../../model/Card';

const props = defineProps<{ gameId: string }>();
const store = useBullshitStore();
useBullshitBootstrap(props.gameId);

const opponents = computed(() =>
  (store.state?.players ?? []).filter(p => p.id !== String(store.mySeat)));
const isSelected = (card: Card) => store.selectedCards.some(c => c.name === card.name);
const joinLink = computed(() => `${location.origin}/games/bullshit/join/${props.gameId}`);
</script>

<template>
  <div class="bullshit-screen">
    <div v-if="store.phase === 'waiting'" data-test="waiting" class="panel">
      <p>Waiting for opponent…</p>
      <p class="share">Share: <code>{{ joinLink }}</code></p>
    </div>

    <div v-else-if="store.phase === 'finished'" data-test="end" class="panel">
      <h2>{{ store.iWon ? 'You win!' : 'You lose' }}</h2>
    </div>

    <template v-else>
      <div class="opponents">
        <div v-for="opp in opponents" :key="opp.id" class="opponent" :class="{ active: opp.isCurrentPlayer }">
          <span class="seat">Player {{ opp.id }}</span>
          <CardCounter :count="opp.handCount" />
        </div>
      </div>

      <div class="table">
        <p class="claim">Claim: {{ store.state?.currentTarget.label }}</p>
        <p v-if="store.state?.table.state === 'CLAIM'" class="last-claim">
          Player {{ store.state.table.claimantId }} played {{ store.state.table.count }} card(s) face-down
        </p>
        <p class="pile">Discard pile: {{ store.state?.discardPileSize }}</p>
      </div>

      <div v-if="store.reveal" data-test="reveal" class="reveal">
        <p>
          Player {{ store.reveal.callerSeat }} called bullshit on Player {{ store.reveal.claimantSeat }} —
          claim was {{ store.reveal.truthful ? 'TRUE' : 'FALSE' }} — Player {{ store.reveal.pickerSeat }} takes the pile
        </p>
        <div class="revealed-cards">
          <PlayingCard v-for="(c, i) in store.reveal.revealedCards" :key="i" :rank="c.rank" :suit="c.suit" />
        </div>
      </div>

      <div class="hand">
        <button
          v-for="(card, i) in store.state?.myHand ?? []"
          :key="card.name"
          :data-test="`hand-card-${i}`"
          class="hand-card"
          :class="{ selected: isSelected(card) }"
          type="button"
          @click="store.toggleCard(card)">
          <PlayingCard :rank="card.rank" :suit="card.suit" />
        </button>
      </div>

      <div class="actions">
        <button
          data-test="discard"
          type="button"
          :disabled="!store.isMyTurn || store.selectedCards.length === 0"
          @click="store.discard()">
          Discard as {{ store.state?.currentTarget.label }}
        </button>
        <button
          data-test="call"
          type="button"
          :disabled="!store.canCallBullshit"
          @click="store.callBullshit()">
          Call Bullshit
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bullshit-screen { display: flex; flex-direction: column; gap: 1rem; padding: 1rem; align-items: center; }
.panel { text-align: center; }
.share code { word-break: break-all; }
.opponents { display: flex; gap: 1rem; }
.opponent.active { outline: 2px solid var(--p-primary-color); border-radius: 0.5rem; }
.hand { display: flex; gap: 0.25rem; flex-wrap: wrap; justify-content: center; }
.hand-card { background: none; border: none; padding: 0; cursor: pointer; }
.hand-card.selected { transform: translateY(-12px); }
.actions { display: flex; gap: 1rem; }
.reveal { text-align: center; }
.revealed-cards { display: flex; gap: 0.25rem; justify-content: center; }
</style>
```

Note on `PlayingCard` props: it accepts `rank`, `suit`, and optional `hidden`/`size`. If the existing `PlayingCard.vue` requires a different prop shape, adapt these usages to match it (read `frontend/src/components/PlayingCard.vue`).

- [ ] **Step 4: Create `BullshitStartGame.vue`**

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBullshitStore } from '../../state/Bullshit.store';
import { watch } from 'vue';

const route = useRoute();
const router = useRouter();
const store = useBullshitStore();

const name = ref('');
const joinId = ref((route.params.id as string) ?? '');
const isJoin = ref(route.name === 'bullshit-join');

// When create produces a game id, navigate to the room.
watch(() => store.gameId, (id) => {
  if (id) router.push(`/games/bullshit/room/${id}`);
});

async function onCreate() {
  store.create(name.value || undefined);
}

async function onJoin() {
  await store.join(joinId.value, name.value || undefined);
  router.push(`/games/bullshit/room/${joinId.value}`);
}
</script>

<template>
  <div class="start">
    <h1>Bullshit</h1>
    <label>Your name <input v-model="name" type="text" /></label>

    <template v-if="!isJoin">
      <button type="button" @click="onCreate">Create 2-player game</button>
    </template>
    <template v-else>
      <label>Game ID <input v-model="joinId" type="text" /></label>
      <button type="button" :disabled="!joinId" @click="onJoin">Join game</button>
    </template>
  </div>
</template>

<style scoped>
.start { display: flex; flex-direction: column; gap: 1rem; padding: 2rem; max-width: 28rem; margin: 0 auto; }
</style>
```

- [ ] **Step 5: Register routes in `main.ts`**

Add imports near the other view imports:
```ts
import BullshitGameScreen from './view/bullshit/BullshitGameScreen.vue';
import BullshitStartGame from './view/bullshit/BullshitStartGame.vue';
```

Add to the `routes` array (the room route passes `:id` as the `gameId` prop):
```ts
  { path: '/games/bullshit/create',    name: 'bullshit-create', component: BullshitStartGame },
  { path: '/games/bullshit/join/:id?', name: 'bullshit-join',   component: BullshitStartGame },
  { path: '/games/bullshit/room/:id',  name: 'bullshit-room',   component: BullshitGameScreen, props: route => ({ gameId: route.params.id }) },
```

- [ ] **Step 6: Add a "Play Bullshit" link to `LobbyView.vue`**

Read `frontend/src/view/alpha/LobbyView.vue` and add, alongside the existing actions, a link following the existing styling:
```vue
<router-link to="/games/bullshit/create" class="lobby-action">Play Bullshit</router-link>
```
Keep it minimal — match the markup/classes of the neighbouring BatailleCorse actions. This is the only entry point (no game-picker).

- [ ] **Step 7: Run the component test + build**

Run: `cd frontend && npx vitest run src/view/bullshit/BullshitGameScreen.test.ts`
Expected: PASS.
Run: `cd frontend && npm run build`
Expected: build succeeds (type-checks all new code + routes).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/view/bullshit/ frontend/src/main.ts frontend/src/view/alpha/LobbyView.vue
git commit -m "feat(frontend): Bullshit start + game screens, routes, lobby link"
```

---

## Task 8: Full verification

**Files:** none (verification + any small wiring fixes).

- [ ] **Step 1: Backend full suite**

Run: `"$MVN" -f backend/pom.xml clean test`
Expected: BUILD SUCCESS, all green (the new join tests plus the pre-existing suite).

- [ ] **Step 2: Frontend tests + build**

Run: `cd frontend && npm test`
Expected: all test files pass (existing BatailleCorse tests + the new Bullshit ones).
Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 3: Manual smoke (optional but recommended)**

If running the app locally: open two browsers. In A, go to `/games/bullshit/create`, create, copy the join link, confirm A shows "Waiting for opponent" and A's hand is visible. In B, open the join link, join. Confirm A leaves waiting, both see only their own hand, seat 0 can discard, seat 1 can call bullshit and sees the reveal, and the game reaches a win/lose screen.

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "test(bullshit): full backend + frontend verification green"
```
(Skip if nothing changed.)

---

## Self-Review (completed during planning)

**1. Spec coverage:**
- Routing & entry (`/games/bullshit/*`, lobby link) → Task 7. ✓
- Per-seat token-header subscribe + reconnect replay + lobby listener → Task 2. ✓
- Model mirrors of per-seat DTOs + events → Task 3. ✓
- `BullshitSession` (create/join/discard/callBullshit/hydrate/restore, foreign-CREATE guard) → Task 4. ✓
- `Bullshit.store.ts` (phase/isMyTurn/canDiscard/canCallBullshit/iWon/reveal/selection) → Task 5. ✓
- Reload rehydration → Task 6. ✓
- Minimal screen (hand select, discard, call, reveal, waiting + share link, end) → Task 7. ✓
- Creator hydrates via REST after create (CREATE ack has null state) → Task 4 `onLobby` → `hydrate`. ✓
- Backend thin join + per-seat JOIN broadcast (409 on taken seat, 404 unknown) → Task 1. ✓
- Outcome/turn driven off `state`, not events → store getters read `state`; only JOIN/CALL_BULLSHIT events drive waiting/reveal. ✓
- Full regression → Task 8. ✓
- Out of scope (solo, 3–6 join, picker, overlays, timer, rematch, animations, suit variant, rules panel) → no task. ✓

**2. Placeholder scan:** No "TBD"/"implement later". Two "adapt to the real component/markup" notes (PlayingCard prop shape in Task 7 Step 3; LobbyView markup in Task 7 Step 6) are explicit call-outs to match existing code, with the file to read named — not logic placeholders.

**3. Type consistency:** `BullshitSessionEvent` union (`state-update`/`game-id-change`/`seat-change`/`event`) is identical across Task 4 (definition), Task 5 (store `applyEvent`), and Task 7 (test). `BullshitState` shape identical across Tasks 3/4/5/7. Store method names (`create`/`join`/`restore`/`hydrate`/`discard`/`callBullshit`/`toggleCard`/`clearSelection`/`applyEvent`/`markCreated`) match between store (Task 5) and screens/tests (Task 7). Backend `joinGame(String, JoinGamePayload)` signature matches its test (Task 1). `WebSocketService.subscribeToSeat(gameId, seat, token, onMessage)` / `setLobbyListener` match the `BullshitWebSocketPort` the session depends on.
