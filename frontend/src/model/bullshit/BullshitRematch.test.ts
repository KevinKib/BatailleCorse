import { describe, it, expect } from 'vitest';
import { bullshitRematchButton } from './BullshitRematch';

describe('bullshitRematchButton', () => {
  it('idle before I request', () => {
    expect(bullshitRematchButton({ iRequested: false, ready: 0, eligible: 0 }))
      .toEqual({ label: 'Play again', disabled: false });
  });

  it('waiting with progress after I request', () => {
    expect(bullshitRematchButton({ iRequested: true, ready: 1, eligible: 3 }))
      .toEqual({ label: 'Waiting… 1/3 ready', disabled: true });
  });
});
