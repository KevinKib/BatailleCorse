import { describe, it, expect } from 'vitest';
import { rematchButtonFor } from './RematchButton';

describe('rematchButtonFor', () => {
  it('givenSolo_thenPlayAgainEnabled', () => {
    expect(rematchButtonFor(true, 'idle')).toEqual({ label: 'Play Again', disabled: false });
  });

  it('givenSolo_thenIgnoresRematchState', () => {
    expect(rematchButtonFor(true, 'requested-by-me')).toEqual({ label: 'Play Again', disabled: false });
  });

  it('givenMultiplayerIdle_thenPlayAgainEnabled', () => {
    expect(rematchButtonFor(false, 'idle')).toEqual({ label: 'Play Again', disabled: false });
  });

  it('givenMultiplayerRequestedByMe_thenWaitingDisabled', () => {
    expect(rematchButtonFor(false, 'requested-by-me')).toEqual({ label: 'Waiting for opponent…', disabled: true });
  });

  it('givenMultiplayerRequestedByOpponent_thenAcceptEnabled', () => {
    expect(rematchButtonFor(false, 'requested-by-opponent')).toEqual({ label: 'Accept Rematch', disabled: false });
  });
});
