import { describe, it, expect } from 'vitest';
import { rematchButtonFor } from './RematchButton';

const texts = { playAgain: 'Play again', pending: 'Joining the new table…' };

describe('rematchButtonFor', () => {
  it('givenIdle_thenPlayAgainEnabled', () => {
    expect(rematchButtonFor('idle', texts)).toEqual({ label: 'Play again', disabled: false });
  });

  it('givenPending_thenBusyAndDisabled', () => {
    expect(rematchButtonFor('pending', texts)).toEqual({ label: 'Joining the new table…', disabled: true });
  });

  it('givenFailed_thenPlayAgainEnabledToRetry', () => {
    expect(rematchButtonFor('failed', texts)).toEqual({ label: 'Play again', disabled: false });
  });
});
