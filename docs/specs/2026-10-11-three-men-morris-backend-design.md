# Three Men's Morris backend: design

Scope: the backend of a new two-player game, "La petite marelle à trois pions" (Three Men's Morris).
Rules source: https://locusludi.ch/wp-content/uploads/2020/05/La-petite-marelle-%C3%A0-trois-pions_-FR.pdf.
No frontend, no bot, no game or seat options. Items marked **[A]** are assumptions the rules text does
not settle; the product owner may change them.

## Rules retained

- Board B of the PDF: nine points (3x3) numbered row by row (`0 1 2 / 3 4 5 / 6 7 8`, centre 4),
  with the lines of the square **and the two diagonals through the centre**. Board A (no diagonals)
  is discarded.
- Adjacency: orthogonal neighbours, plus the centre, which is adjacent to the eight other points. A
  corner is adjacent to its 2 orthogonal neighbours and the centre; a side middle to its 2 corners and
  the centre.
- 8 winning lines: 3 rows, 3 columns, 2 diagonals (`Board.LINES`).
- Three pieces per player. Placement phase: the players alternately place one piece on a free point
  (6 turns). Movement phase: alternately, move one of your pieces to a free adjacent point; no jump,
  no pass, moving is mandatory.
- The first player to align their three pieces wins.

## Assumptions

- **[A1]** An alignment counts from the placement phase ("the first to align their three pieces").
- **[A2]** Seat 0 (white) starts; after the 6th placement white also plays first in the movement phase.
- **[A3]** A player with no legal move loses (passing is forbidden and the rules say nothing else).
  Note: on board B with three pieces each, a player can only be boxed in by a white/black alignment
  (the minimal box around a corner cluster is a diagonal), so the line wins first; the blocked rule is
  implemented and tested on an artificial position but is practically unreachable.
- **[A4]** No draw and no repetition detection in v1 (a game can in theory last forever).
- **[A5]** Forfeit and disconnection use the common presence mechanism (60 s grace, then forfeit: the
  other player wins, reason `FORFEIT`). A rematch goes through the shared `playAgain`.
- **[A6]** Exactly 2 seats, no bot, no options (empty descriptors).
- **[A7]** `getPlayerIds()` always returns both seats, also after a forfeit, so the loser still
  receives the final state.

## Domain (`threemenmorris/domain`)

`ThreeMenMorris implements Game`: a synchronized aggregate (every command validates, mutates, bumps
`version()` and returns a `MorrisEvent`; a rejected command changes nothing and does not bump the
version). Phases `PLACEMENT`, `MOVEMENT`, `FINISHED`. Commands `place(seat, point)`,
`move(seat, from, to)`, `forfeit(loser)`. Queries `phase()`, `currentSeat()`, `owner(point)`,
`piecesToPlace(seat)`, `legalMoves(seat)`, `availableActions(seat)`, `result()` (winner and reason
`LINE`, `BLOCKED` or `FORFEIT`), `winningLine()`, `version()`. Domain exceptions (all checked, all
extending `ThreeMenMorrisException`, no separate package): `FinishedGameException`,
`NotPlayersTurnException`, `WrongPhaseException`, `InvalidPointException`, `PointOccupiedException`,
`NotYourPieceException`, `NotAdjacentException`. `ThreeMenMorrisFactory` (type `three-men-morris`,
2 to 2 players) is registered in `GameFactories` through `AppConfig`.

## API

All commands carry the seat token (`gameId`, `token`); a rejected command answers the sender alone
with an `ErrorResponse` carrying its current state.

| Channel | Payload | Effect |
|---------|---------|--------|
| `POST /api/lobby` `{gameType:"three-men-morris", name}` | existing | creates the room (host token in the answer) |
| `GET /api/three-men-morris/game/{id}?token=` | | the viewer's state, or the `LobbyView` before the start |
| `POST /api/three-men-morris/game/{id}/join`, `/play-again` | `JoinGamePayload` | as Kobo |
| STOMP `/app/three-men-morris/start` | `GameActionPayload` | host starts, `START` + one state per seat |
| STOMP `/app/three-men-morris/place` | `ThreeMenMorrisPlacePayload(gameId, token, point)` | placement |
| STOMP `/app/three-men-morris/move` | `ThreeMenMorrisMovePayload(gameId, token, from, to)` | step |

Broadcast events (`SuccessResponse(eventType, eventData, message, state)` to every seat on its
private topic): `START`, `PLACE`, `MOVE`, `GAME_OVER` (a command that ended the game is announced as
`GAME_OVER`; its data still names the command). Lifecycle events reuse `LifecycleEventType`. The
state (`ThreeMenMorrisDto`) holds `version`, `phase`, `currentSeat`, `board` (9 entries, owner seat
or null), `players` (seat, name, pieces to place), `legalMoves` and `availableActions` of the viewer,
`winningLine`, `outcome`. Everything is public; only the available actions differ per seat.
