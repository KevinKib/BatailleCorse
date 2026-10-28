import { describe, it, expect } from 'vitest';
import {
  squareCoords, squareAt, selectable, targets, candidatesFor, separatingSquares, onSquare, onCandidatePiece,
  moveFocus, orientationOf,
} from './board';
import {
  draughtsState, draughtsOpeningState, draughtsCaptureState, draughtsAmbiguousState, draughtsFinishedState,
} from './fixtures';

describe('squareCoords', () => {
  it('places the corners and the bands the FMJD way with White at the bottom', () => {
    expect(squareCoords(5, 'WHITE')).toEqual({ row: 0, col: 9 });
    expect(squareCoords(1, 'WHITE')).toEqual({ row: 0, col: 1 });
    expect(squareCoords(46, 'WHITE')).toEqual({ row: 9, col: 0 });
    expect(squareCoords(50, 'WHITE')).toEqual({ row: 9, col: 8 });
    expect(squareCoords(6, 'WHITE')).toEqual({ row: 1, col: 0 });
  });

  it('turns the board by 180 degrees when Black is at the bottom', () => {
    expect(squareCoords(5, 'BLACK')).toEqual({ row: 9, col: 0 });
    expect(squareCoords(46, 'BLACK')).toEqual({ row: 0, col: 9 });
    expect(squareCoords(1, 'BLACK')).toEqual({ row: 9, col: 8 });
  });

  it('puts every square on a dark cell, once, in both orientations', () => {
    for (const orientation of ['WHITE', 'BLACK'] as const) {
      const cells = new Set<string>();
      for (let square = 1; square <= 50; square++) {
        const { row, col } = squareCoords(square, orientation);
        expect((row + col) % 2).toBe(1);
        cells.add(`${row}:${col}`);
      }
      expect(cells.size).toBe(50);
    }
  });

  it('is undone by squareAt', () => {
    for (const orientation of ['WHITE', 'BLACK'] as const) {
      for (let square = 1; square <= 50; square++) {
        const { row, col } = squareCoords(square, orientation);
        expect(squareAt(row, col, orientation)).toBe(square);
      }
    }
    expect(squareAt(0, 0, 'WHITE')).toBeNull();
  });
});

describe('orientationOf', () => {
  it('shows the viewer colour at the bottom', () => {
    expect(orientationOf(draughtsOpeningState, 0)).toBe('WHITE');
    expect(orientationOf(draughtsOpeningState, 1)).toBe('BLACK');
  });

  it('shows White at the bottom for a spectator or an unknown seat', () => {
    expect(orientationOf(draughtsOpeningState, 7)).toBe('WHITE');
  });
});

describe('selectable', () => {
  it('lists the pieces that have a legal move, on my turn', () => {
    expect(selectable(draughtsOpeningState, 0)).toEqual([31, 32, 33, 34, 35]);
  });

  it('gives nothing to the seat that is not on turn', () => {
    expect(selectable(draughtsOpeningState, 1)).toEqual([]);
  });

  it('gives nothing once the game is over', () => {
    expect(selectable(draughtsFinishedState, 0)).toEqual([]);
  });

  it('gives nothing without the MOVE action', () => {
    expect(selectable(draughtsState({ availableActions: ['ACCEPT_DRAW', 'DECLINE_DRAW'] }), 0)).toEqual([]);
  });

  it('lists the capturing pieces the server allows when a capture is compulsory', () => {
    expect(selectable(draughtsCaptureState, 0)).toEqual([33, 34]);
  });
});

describe('targets', () => {
  it('lists the destinations of the selected piece', () => {
    expect(targets(draughtsOpeningState, 31)).toEqual([26, 27]);
    expect(targets(draughtsOpeningState, 35)).toEqual([30]);
  });

  it('lists nothing without a selection', () => {
    expect(targets(draughtsOpeningState, null)).toEqual([]);
  });

  it('lists a destination once when two captures end there', () => {
    expect(targets(draughtsAmbiguousState, 46)).toEqual([19]);
  });
});

describe('candidatesFor and separatingSquares', () => {
  it('has one candidate for a plain destination and two for an ambiguous one', () => {
    expect(candidatesFor(draughtsOpeningState, 31, 26)).toHaveLength(1);
    expect(candidatesFor(draughtsAmbiguousState, 46, 19)).toHaveLength(2);
  });

  it('finds the pieces that belong to only one candidate', () => {
    const candidates = candidatesFor(draughtsAmbiguousState, 46, 19);
    expect(separatingSquares(candidates)).toEqual(new Map([[37, 0], [28, 0], [32, 1], [23, 1]]));
  });

  it('leaves out a piece taken by every candidate', () => {
    const candidates = [
      { from: 1, to: 2, path: [2], captures: [10, 11], promotes: false },
      { from: 1, to: 2, path: [2], captures: [10, 12], promotes: false },
    ];
    expect(separatingSquares(candidates)).toEqual(new Map([[11, 0], [12, 1]]));
  });
});

describe('onSquare', () => {
  const opening = draughtsOpeningState;

  it('selects a piece that can move', () => {
    expect(onSquare(opening, 0, null, 31)).toEqual({ kind: 'select', square: 31 });
  });

  it('switches to another movable piece', () => {
    expect(onSquare(opening, 0, 31, 32)).toEqual({ kind: 'select', square: 32 });
  });

  it('deselects on a tap on the selected piece', () => {
    expect(onSquare(opening, 0, 31, 31)).toEqual({ kind: 'deselect' });
  });

  it('plays a unique destination with the captured set', () => {
    expect(onSquare(opening, 0, 31, 26)).toEqual({ kind: 'play', from: 31, to: 26, captures: [] });
    expect(onSquare(draughtsCaptureState, 0, 33, 22)).toEqual({ kind: 'play', from: 33, to: 22, captures: [28] });
  });

  it('asks to choose when several moves end on the same square', () => {
    const intent = onSquare(draughtsAmbiguousState, 0, 46, 19);
    expect(intent.kind).toBe('choose');
    if (intent.kind === 'choose') {
      expect(intent.candidates.map(c => c.captures)).toEqual([[37, 28], [32, 23]]);
    }
  });

  it('deselects on a tap elsewhere, and ignores it without a selection', () => {
    expect(onSquare(opening, 0, 31, 12)).toEqual({ kind: 'deselect' });
    expect(onSquare(opening, 0, null, 12)).toEqual({ kind: 'none' });
  });

  it('does nothing for the seat that is not on turn', () => {
    expect(onSquare(opening, 1, null, 31)).toEqual({ kind: 'none' });
    expect(onSquare(opening, 1, 31, 26)).toEqual({ kind: 'none' });
  });

  it('does not select a piece that cannot capture when a capture is compulsory', () => {
    expect(onSquare(draughtsCaptureState, 0, null, 47)).toEqual({ kind: 'none' });
  });
});

describe('onCandidatePiece', () => {
  const candidates = draughtsAmbiguousState.legalMoves;

  it('plays the candidate that the tapped piece belongs to', () => {
    expect(onCandidatePiece(candidates, 28)).toEqual({ kind: 'play', from: 46, to: 19, captures: [37, 28] });
    expect(onCandidatePiece(candidates, 23)).toEqual({ kind: 'play', from: 46, to: 19, captures: [32, 23] });
  });

  it('ignores a piece that no candidate takes', () => {
    expect(onCandidatePiece(candidates, 5)).toEqual({ kind: 'none' });
  });

  it('ignores a piece that every candidate takes', () => {
    const both = [
      { from: 1, to: 2, path: [2], captures: [10, 11], promotes: false },
      { from: 1, to: 2, path: [2], captures: [10, 12], promotes: false },
    ];
    expect(onCandidatePiece(both, 10)).toEqual({ kind: 'none' });
  });
});

describe('moveFocus', () => {
  it('moves along the row with left and right', () => {
    expect(moveFocus(32, 'ArrowRight', 'WHITE')).toBe(33);
    expect(moveFocus(32, 'ArrowLeft', 'WHITE')).toBe(31);
  });

  it('reverses left and right when Black is at the bottom', () => {
    expect(moveFocus(32, 'ArrowRight', 'BLACK')).toBe(31);
    expect(moveFocus(32, 'ArrowLeft', 'BLACK')).toBe(33);
  });

  it('moves to the row above or below on the visible board with up and down', () => {
    expect(moveFocus(32, 'ArrowUp', 'WHITE')).toBe(27);
    expect(moveFocus(32, 'ArrowDown', 'WHITE')).toBe(37);
    expect(moveFocus(32, 'ArrowUp', 'BLACK')).toBe(37);
    expect(moveFocus(32, 'ArrowDown', 'BLACK')).toBe(27);
  });

  it('stays on the edge of the board', () => {
    expect(moveFocus(31, 'ArrowLeft', 'WHITE')).toBe(31);
    expect(moveFocus(5, 'ArrowRight', 'WHITE')).toBe(5);
    expect(moveFocus(3, 'ArrowUp', 'WHITE')).toBe(3);
    expect(moveFocus(48, 'ArrowDown', 'WHITE')).toBe(48);
  });
});
