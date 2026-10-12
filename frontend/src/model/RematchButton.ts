export interface RematchButton {
  label: string;
  disabled: boolean;
}

/** Where my own Play again request stands: sent and waiting for the server, or refused. */
export type RematchState = 'idle' | 'pending' | 'failed';

/**
 * The Play again button: enabled, or busy while my request runs (a refusal enables it again, the screen
 * says why). Play again leads back to the lobby, so it never waits for the opponent.
 */
export function rematchButtonFor(state: RematchState, texts: { playAgain: string; pending: string }): RematchButton {
  return state === 'pending'
    ? { label: texts.pending, disabled: true }
    : { label: texts.playAgain, disabled: false };
}
