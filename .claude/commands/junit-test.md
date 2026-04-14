You are helping write or review JUnit 5 tests for a Java project following hexagonal architecture and DDD principles.

## Step 1 — Identify the test type

Determine the type of test to write based on the class under test:

| Class location | Test type |
|---|---|
| `core/domain/` | Domain Unit Test |
| `sessionmanagement/application/` | Application Test |
| `websocket/presentation/v1/` | Integration Test (suffix `IT`) |

If reviewing, identify which rules are violated and fix them.

---

## Domain Unit Tests

**Rules:**
- No Spring context. Pure Java only.
- **Never use Mockito on domain classes** (`BatailleCorse`, `Player`, `CentralPile`, `Penality`, `SlapRules`, etc.).
  - Use **Builders** (`BatailleCorseBuilder`, `PlayerBuilder`, `CentralPileBuilder`, …) to construct real domain state.
  - Use **Fixtures** (`BatailleCorseFixtures`, `PlayerFixtures`, `CentralPileFixtures`, `SlapRulesFixtures`, …) for common pre-built scenarios.
  - For domain interfaces (e.g., `Penality`, `SlapRule`), follow the `SlapRulesFixtures` pattern: static factory methods returning real or minimal implementations (`neverApplyingSlapRules()`, `alwaysApplyingSlapRules()`). Create a `*Fixtures` class if one doesn't exist yet.
  - **Never** call `new` directly on domain objects in test bodies — always go through a Builder or Fixture.
- Group tests with `@Nested` classes named after the method or scenario (e.g., `class SendTest`, `class WhenPileIsFull`).
- Name test methods: `givenX_thenY` or `givenX_whenY_thenZ`.
- Use Hamcrest (`assertThat(..., is(...))`) for assertions. For domain-specific assertions, create a `TypeSafeMatcher` (see `IsEveryCardHidden` in `BatailleCorseTest` as a reference).
- One behavioral concept per test. Multiple `assertThat` lines are fine if they assert the same concept.
- Do not assert on implementation details — assert on observable behavior (return values, state accessible via public API, exceptions thrown).

**Template:**
```java
class MyDomainClassTest {

    @Nested
    class WhenDoingSomething {

        @Test
        void givenSomeContext_thenExpectedOutcome() {
            MyDomainClass subject = aMyDomainClass()
                    .withSomeState(...)
                    .build();

            assertThat(subject.doSomething(), is(expectedValue));
        }

        @Test
        void givenSomeContext_whenAction_thenExpectedOutcome() {
            MyDomainClass subject = aMyDomainClass()
                    .withSomeState(...)
                    .build();

            subject.performAction();

            assertThat(subject.getResult(), is(expectedValue));
        }
    }
}
```

---

## Application Tests (SessionService)

**Rules:**
- No Spring context — instantiate directly in `@BeforeEach`.
- Use the **real `InMemorySessionRepository`** — never mock ports.
- Test the full application behavior end-to-end: application logic + domain, no shortcuts.
- Same `@Nested` + naming conventions as domain tests.

**Template:**
```java
class SessionServiceTest {

    private SessionRepository repository;
    private SessionService service;

    @BeforeEach
    void setUp() {
        repository = new InMemorySessionRepository();
        service = new SessionService(repository);
    }

    @Nested
    class CreateGame {

        @Test
        void givenValidNbPlayers_thenGameIsSavedAndReturned() {
            BatailleCorse game = service.createGame(2);

            assertNotNull(game);
            // assert game is retrievable from the repository
        }
    }
}
```

---

## Integration Tests (suffix `IT`)

**Rules:**
- Class name must end with `IT` (e.g., `BatailleCorseWebSocketControllerIT`).
- Use `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` — full Spring context.
- **No mocks.** Real implementations only (`InMemorySessionRepository` is wired by Spring). Only mock external I/O that does not exist in the project yet.
- Each test represents a **realistic user workflow**: a sequence of user-facing actions with assertions on the observable outcome at each step.
- Use a STOMP WebSocket client (`WebSocketStompClient` + `SockJsClient`) to communicate through the real WebSocket layer.
- `@Nested` to group by workflow. Same naming convention.
- Workflows to consider:
  - A player creates a game and the initial state is returned.
  - Two players alternate sending cards.
  - A player slaps and wins (pile is cleared, cards go to slapping player).
  - A player slaps and loses (penalty is applied).
  - A player grabs the pile after an honour card sequence.

**Template:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BatailleCorseWebSocketControllerIT {

    @LocalServerPort
    private int port;

    private StompSession session;

    @BeforeEach
    void connect() throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())))
        );
        client.setMessageConverter(new MappingJackson2MessageConverter());
        session = client.connect("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {})
                .get(1, TimeUnit.SECONDS);
    }

    @AfterEach
    void disconnect() {
        if (session.isConnected()) session.disconnect();
    }

    @Nested
    class CreateGame {

        @Test
        void givenCreateRequest_thenGameIsCreatedWithCorrectNumberOfPlayers() throws Exception {
            BlockingQueue<Response> received = new LinkedBlockingQueue<>();
            session.subscribe("/topic/game", new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return Response.class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) { received.offer((Response) payload); }
            });

            session.send("/app/create", null);

            Response response = received.poll(2, TimeUnit.SECONDS);
            assertNotNull(response);
            // assert game state
        }
    }
}
```

---

## General Rules (all test types)

- `@Nested` class names: noun phrase or verb phrase describing the scenario (`CreateGame`, `WhenSendingCard`, `WhenPileIsFull`).
- Test method names: `givenX_thenY` or `givenX_whenY_thenZ`. No other formats.
- If a Builder or Fixture is missing for a class you need to set up, **create it** before writing the test. Never bypass this with `new` or Mockito.
- Do not add comments like `// arrange`, `// act`, `// assert` — the test structure should be self-evident.
- Do not assert on log output, internal fields, or anything not accessible via the public API.
