# President exchange synchronisation: plan

Spec: `docs/specs/2026-10-19-president-exchange-sync-design.md`. Written by Claude Opus 5.5 on `origin/main` at
`44927da` (2026-10-19).

**Goal:** after the card exchange of a round, the first trick waits until every human seat has seen the exchange
(flights and summary), at most 20 s; bots never make anyone wait.

## Key decisions (spec section 2)

1. Barrier in the President domain: phase `EXCHANGE_VIEW`, every active seat acknowledges; the bot coordinator
   acknowledges for bots at once, a presentation listener closes the view after the timeout.
2. Command `/app/president/seeExchange {gameId, token, round}`; late or repeated acknowledgements are silent no-ops.
3. Timeout 20 s (`president.exchange-view.timeout`); on timeout the late seats count as having seen it.
4. The client acknowledges once the flights have landed and the summary has shown for 3 s (scaled by the speed,
   Instant at once); the summary's cross acknowledges at once.
5. Additive wire change: `EXCHANGE_VIEW`, `SEE_EXCHANGE`, `players[].sawExchange`, `EXCHANGE_SEEN`,
   `EXCHANGE_CLOSED`; no deadline on the wire.

## Rules for every chunk

- Own worktree and branch from `origin/main`, a draft PR opened at launch with its real number. Rebase on `main`
  before the final push.
- Strict TDD: each new test seen failing for the right reason before the code.
- Visible text through `frontend/src/locales/` only (`presidentUi.exchangeView`, added by chunk 0).
- Suites before ready: `cd backend && mvn -s ../deploy/docker/settings.xml test`; `cd frontend && npm test && npx
  vite build`; Cypress `president-*` where the chunk says so, on a shifted-port e2e stack.
- **Owner's merge accord needed** for every chunk that touches the President domain backend (chunks 0 and 1; the
  merge-autonomy experiment excludes the President domain).

## Chunks

| Chunk | Title | Size | Needs | Runs with | Model | Owner accord |
|-------|-------|------|-------|-----------|-------|--------------|
| 0 | `feat: President exchange view, shared contract` | S | - | first, alone | default (Sonnet) | yes (domain enums and signatures) |
| 1 | `feat: President exchange view, domain barrier` | M | 0 | 2 (3 stacked on it) | default (Sonnet), Opus review | yes |
| 2 | `feat: President exchange view, screen and acknowledgement` | M | 0 | 1, 3 | default (Sonnet) | no |
| 3 | `feat: President exchange view, timeout and bots` | M | 0, 1 | 2 | default (Sonnet) | no (presentation only) |
| 4 | `feat: President exchange view, end to end` | S-M | 1, 2, 3 | last, alone | default (Sonnet), Opus branch review | no |

Chunk 3 needs the real barrier to test the timer and the bots (a `President` in `EXCHANGE_VIEW`), so it is
stacked on chunk 1's branch (retargeted to `main` before chunk 1's merge). Chunks 1 and 2 are independent: chunk 2
works on fixtures.

**Merge order** (the barrier must never reach `main` without its way out): 0, then 2 (inert until the server
enters the view: the client and the Cypress guests acknowledge only when `SEE_EXCHANGE` is offered), then 1 and 3
together (1 alone would leave a table without a timeout), then 4.

### Chunk 0: shared contract (no behaviour)

Everything the other chunks compile against, with the view never entered yet:

- Backend domain: `Phase.EXCHANGE_VIEW` (javadoc of spec 4.1), `Action.SEE_EXCHANGE`, events `ExchangeSeen`,
  `ExchangeViewClosed` (in the sealed `PresidentEvent`), `PresidentBotDecision.SeeExchange`; `President.seeExchange`,
  `closeExchangeView` (return `List.of()`), `exchangeSeen()` (empty set); the `switch` cases the new constant needs
  (`availableActions`, `HeuristicPresidentBot.decide`: answer `SeeExchange` when `SEE_EXCHANGE` is offered).
- Backend presentation: `PresidentEventType.EXCHANGE_SEEN|EXCHANGE_CLOSED`, `PresidentRoundPayload`,
  `PresidentExchangeClosedData`, `PlayerDto.sawExchange`, `PresidentGameActions.seeExchange|closeExchangeView`
  (`run` skips touch and broadcast on no event), the `/president/seeExchange` destination, the coordinator's
  `SeeExchange` branch in `apply` and its `EXCHANGE_VIEW` fallback.
- Frontend: the types of spec 7.1, the fixtures (`sawExchange: false`), `PresidentSession.seeExchange(round)`, the
  locale keys of spec 7.4 in `en.ts` and `Messages.ts`.
- Tests: the destination and payload (controller test, `PresidentSession.test.ts`), `PresidentDtoTest` (`sawExchange`
  present and false), the `en.test.ts` keys, `run` broadcasting nothing on no event (`PresidentGameActionsTest`).

### Chunk 1: domain barrier

`President`, `Round`, spec section 4. Tests: `ExchangeViewTest` (spec section 9, domain row) first; then the
existing tests and drivers that cross an exchange acknowledge for every seat (one helper in the test fixtures,
`seeExchangeAll(game)`, used by `PresidentDriver`, `OrderedRounds` and the self-play tests).
Files: `domain/President.java`, `domain/round/Round.java`, `domain/round/Phase.java` (javadoc), tests under
`president/domain`.

### Chunk 2: screen and acknowledgement

Spec section 7: `model/president/exchangeView.ts` (TDD), `exchangeRecap.ts` (shown in `EXCHANGE_VIEW`), the store
(`seeExchange`, no log entry for the two events), the screen (acknowledgement timer from `playExchange` or the
summary, the cross, the phase lines). Also the Cypress support, inert until the server enters the view: the
scripted STOMP guests and `autoplay` acknowledge when `SEE_EXCHANGE` is offered (an option holds a guest back).
Tests: the frontend row of spec section 9; the existing `president-*` Cypress specs stay green (nothing changes yet
on the server).
Files: `model/president/exchangeView.ts` (new), `model/president/exchangeRecap.ts`, `state/President.store.ts`,
`view/president/PresidentGameScreen.vue`, `components/president/PresidentActionBar.vue` (only if the phase line
needs a prop), `frontend/cypress/support/stompPlayer.ts`, `presidentRoom.ts`, `presidentAutoplay.ts`, their tests.

### Chunk 3: timeout and bots

Spec 5.2 and 5.3: `PresidentExchangeViewTimeout` (listener and eviction listener, `BotScheduler`), its bean and the
`president.exchange-view.timeout` property in `PresidentConfig`, the coordinator's zero-delay `SEE_EXCHANGE` step.
Tests: `PresidentExchangeViewTimeoutTest` (manual scheduler), `PresidentBotCoordinatorTest`, `PresidentBotGameIT`
(through the human's acknowledgement and through the timeout), `PresidentWebSocketControllerIT`,
`PresidentBotViewParityTest`.
Files: `presentation/PresidentExchangeViewTimeout.java` (new), `presentation/bot/PresidentBotCoordinator.java`,
`config/PresidentConfig.java`, their tests.

### Chunk 4: end to end and docs

- New `president-exchange-sync.cy.ts` (spec section 9, Cypress row, using chunk 2's hold-back option); every
  `president-*` spec green on a shifted-port e2e stack with the barrier live, plus `kobo-*` if a shared support
  file changed.
- Docs: `docs/architecture/context-map.md` (President row: the destination, the phase, the timer listener), the
  President spec (`2026-10-15-president-design.md`: section 6.1 phase table and section 7.1 destinations, a pointer
  to this spec), the roadmap issue #1 comment once merged.
- All suites, then an Opus review of the merged result (`code-review` on the branch).
Files: `frontend/cypress/specs/president-exchange-sync.cy.ts` (new), the docs above.

## Conflicts

| File | Chunks / others | Handling |
|------|-----------------|----------|
| `president/domain/President.java`, `round/Round.java` | 0, 1; any parallel President rule work | 0 only adds signatures; 1 owns the bodies |
| `presentation/bot/PresidentBotCoordinator.java` | 0, 3 | 0 adds the `apply` branch; 3 the scheduling |
| `presentation/PresidentGameActions.java` | 0 | done in 0 |
| `config/PresidentConfig.java` | 3 | one bean and one property |
| `model/president/PresidentState.ts`, `model/fixtures/president.ts` | 0, 2 | 0 adds the fields; 2 reads them |
| `locales/en.ts`, `Messages.ts` | 0; the Six Men's Morris chunks in flight | own `presidentUi.exchangeView` block; rebase |
| `view/president/PresidentGameScreen.vue` | 2; animations chunks 4 and 5 | 2 touches the exchange block only (`playExchange`, the recap, the phase line); rebase on whichever lands first |
| `frontend/cypress/support/*` | 2; any spec work in flight | additive option, default unchanged for the other games |

## Risks

- **Existing Cypress specs stall** on the barrier if their scripted guests (human seats) do not acknowledge: each
  round 2 would wait 20 s. Chunk 2 makes them acknowledge before the server needs it (merge order above); chunk 4
  replays every `president-*` spec with the barrier live (the CI has no e2e).
- **Extra broadcasts**: one per bot acknowledgement (at most 7 per exchange round). Watched in chunk 4's spec with
  8 players at 375 px (`president-table-fit` stays green).
- **A tab in the background** acknowledges late: bounded by the timeout; accepted.
