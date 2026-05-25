# Backend Context Map

Two bounded contexts, one presentation layer.

```mermaid
graph TB
    subgraph Websocket["🌐 websocket (presentation / driving adapter)"]
        WS["WebSocketController"]
        REST["GameRestController"]
        WS & REST -->|calls| SS
    end

    subgraph SessionMgmt["📦 Bounded Context: Session Management (downstream)"]
        SS["SessionService"]
        PORT["SessionRepository (port)"]
        SG["SessionGame"]
        ST["SessionToken"]
        REPO["InMemorySessionRepository"]
        SS --> PORT & SG
        SG --> ST
        PORT -.->|implements| REPO
    end

    subgraph Core["♟️ Bounded Context: Core / Game Rules (upstream)"]
        BC["BatailleCorse (aggregate root)"]
    end

    SS -->|creates / loads| BC
    SG -->|references (conformist)| BC

    style Core fill:#1a3a2a,stroke:#4ade80,color:#fff
    style SessionMgmt fill:#1a2a3a,stroke:#60a5fa,color:#fff
    style Websocket fill:#2a1a3a,stroke:#c084fc,color:#fff
```

## Bounded Contexts

**Core** — upstream, pure game rules. `BatailleCorse` is the aggregate root. No knowledge of sessions, tokens, or transport.

**Session Management** — downstream, conforms to Core's model. Two responsibilities:
- *Room lifecycle* — `SessionGame` ties a game ID to a set of player seats
- *Player identity* — `SessionToken` maps each player seat to a secret token, used to authenticate actions

`SessionRepository` is a port (interface); `InMemorySessionRepository` is the current adapter.

**websocket** — driving adapter. No domain logic; orchestrates the two BCs and handles serialization. Named `websocket` for now but may be split or renamed as more transports are added.

## Relationship

Session Management has a **Conformist** relationship to Core: it speaks Core's language (`BatailleCorseId`, `PlayerId`) without translation. Core has no dependency on Session Management.
