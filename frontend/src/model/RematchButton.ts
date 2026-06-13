export interface RematchButton {
  label: string;
  disabled: boolean;
}

/** Rematch handshake state, mirrored from the session (see the store's `rematchState`). */
export type RematchState = 'idle' | 'requested-by-me' | 'requested-by-opponent';

/**
 * The Play-Again / rematch button's label and enabled state, derived from the game
 * mode and the multiplayer rematch handshake. Solo can always replay immediately;
 * multiplayer reflects who has requested the rematch. Kept here, beside the type,
 * so any game screen showing a rematch button shares one source of truth.
 */
export function rematchButtonFor(isSolo: boolean, rematchState: RematchState): RematchButton {
  if (isSolo) return { label: 'Play Again', disabled: false };
  switch (rematchState) {
    case 'requested-by-me':       return { label: 'Waiting for opponent…', disabled: true };
    case 'requested-by-opponent': return { label: 'Accept Rematch', disabled: false };
    default:                      return { label: 'Play Again', disabled: false };
  }
}
