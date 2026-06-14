Mermaid diagram:

```
flowchart BT
    Core{Core} -->|Depends on| FrenchCards
    Core -->|Generates| Events
    Statistics -->|Uses| Events
    GameManager -->|Uses & Coordinates| Core
    GameManager -->|Uses & Coordinates| Statistics
    WebSockets -->|Uses| GameManager
```