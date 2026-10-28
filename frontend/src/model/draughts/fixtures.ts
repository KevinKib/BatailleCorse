import type {
  DraughtsBoard,
  DraughtsLegalMove,
  DraughtsPieceCode,
  DraughtsPlayer,
  DraughtsState,
} from './DraughtsState';

/** Builds the 50-entry board from a layout of 50 characters (square 1 first): `w W b B .`. */
export function boardFromLayout(layout: string): DraughtsBoard {
  if (layout.length !== 50) {
    throw new Error(`A layout has 50 characters, got ${layout.length}`);
  }
  return [...layout].map((c) => (c === '.' ? null : (c as DraughtsPieceCode)));
}

function layoutWith(pieces: Record<number, DraughtsPieceCode>): string {
  const cells = Array<string>(50).fill('.');
  for (const [square, code] of Object.entries(pieces)) {
    cells[Number(square) - 1] = code;
  }
  return cells.join('');
}

function plain(from: number, to: number): DraughtsLegalMove {
  return { from, to, path: [to], captures: [], promotes: false };
}

function whiteAndBlack(
  white: Partial<DraughtsPlayer>,
  black: Partial<DraughtsPlayer>,
  current: 0 | 1,
): DraughtsPlayer[] {
  return [
    { seat: 0, name: 'Alice', colour: 'WHITE', current: current === 0, men: 20, kings: 0, captured: 0, ...white },
    { seat: 1, name: 'Bob', colour: 'BLACK', current: current === 1, men: 20, kings: 0, captured: 0, ...black },
  ];
}

/** A draughts state for tests; the defaults describe the opening, Alice (White, seat 0) to move. */
export function draughtsState(overrides: Partial<DraughtsState> = {}): DraughtsState {
  return {
    started: true,
    id: 'g1',
    gameType: 'draughts',
    version: 0,
    phase: 'PLAY',
    currentSeat: 0,
    whiteSeat: 0,
    board: boardFromLayout('b'.repeat(20) + '.'.repeat(10) + 'w'.repeat(20)),
    players: whiteAndBlack({}, {}, 0),
    availableActions: ['MOVE', 'OFFER_DRAW'],
    legalMoves: [
      plain(31, 26),
      plain(31, 27),
      plain(32, 27),
      plain(32, 28),
      plain(33, 28),
      plain(33, 29),
      plain(34, 29),
      plain(34, 30),
      plain(35, 30),
    ],
    captureRequired: false,
    maxCaptures: 0,
    drawOffersAllowed: true,
    history: [],
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

/** The opening position, White (the viewer) to move, nine legal moves, none backwards. */
export const draughtsOpeningState: DraughtsState = draughtsState({ id: 'opening' });

/**
 * A compulsory capture, White to move: 33x22 and 34x25 each take one piece (the majority is 1), so the
 * hint "you must capture" shows and only those two pieces can move.
 */
export const draughtsCaptureState: DraughtsState = draughtsState({
  id: 'capture',
  version: 24,
  board: boardFromLayout(layoutWith({ 10: 'b', 28: 'b', 30: 'b', 33: 'w', 34: 'w', 47: 'w' })),
  players: whiteAndBlack({ men: 3, captured: 17 }, { men: 3, captured: 17 }, 0),
  legalMoves: [
    { from: 33, to: 22, path: [22], captures: [28], promotes: false },
    { from: 34, to: 25, path: [25], captures: [30], promotes: false },
  ],
  captureRequired: true,
  maxCaptures: 1,
  history: ['32-28', '19-23', '28x19'],
});

/**
 * SYNTHETIC: written by hand, its paths are not derived from the geometry and the rules engine would not
 * produce it (an ambiguous destination is rare). It only feeds the board's chooser tests: two moves of the
 * same piece with the same `from` and `to` that take different pieces.
 */
export const draughtsAmbiguousState: DraughtsState = draughtsState({
  id: 'ambiguous',
  version: 61,
  board: boardFromLayout(layoutWith({ 46: 'W', 37: 'b', 28: 'b', 32: 'b', 23: 'b' })),
  players: whiteAndBlack({ men: 0, kings: 1, captured: 16 }, { men: 4, captured: 19 }, 0),
  legalMoves: [
    { from: 46, to: 19, path: [32, 19], captures: [37, 28], promotes: false },
    { from: 46, to: 19, path: [41, 19], captures: [32, 23], promotes: false },
  ],
  captureRequired: true,
  maxCaptures: 2,
  history: [],
});

/** A finished game: Alice (White, seat 0) took the last black piece with 33x22. */
export const draughtsFinishedState: DraughtsState = draughtsState({
  id: 'finished',
  version: 83,
  phase: 'FINISHED',
  currentSeat: 1,
  board: boardFromLayout(layoutWith({ 22: 'w', 47: 'W' })),
  players: whiteAndBlack({ men: 1, kings: 1, captured: 20 }, { men: 0, captured: 18 }, 1),
  availableActions: [],
  legalMoves: [],
  lastMove: { seat: 0, from: 33, to: 22, path: [22], captures: [28], promotes: false },
  history: ['32-28', '19-23', '28x19', '33x22'],
  outcome: { status: 'FINISHED', winner: 0, reason: 'NO_PIECES' },
});
