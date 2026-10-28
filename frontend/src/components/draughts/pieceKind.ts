import type { DraughtsPieceCode } from '../../model/draughts/DraughtsState';

/** What a piece is, in words the markup and the tests use (`data-piece`). */
export type PieceKind = 'white-man' | 'white-king' | 'black-man' | 'black-king';

const KINDS: Record<DraughtsPieceCode, PieceKind> = {
  w: 'white-man',
  W: 'white-king',
  b: 'black-man',
  B: 'black-king',
};

export const pieceKindOf = (code: DraughtsPieceCode): PieceKind => KINDS[code];
export const isKing = (kind: PieceKind): boolean => kind.endsWith('king');
export const isWhitePiece = (kind: PieceKind): boolean => kind.startsWith('white');
