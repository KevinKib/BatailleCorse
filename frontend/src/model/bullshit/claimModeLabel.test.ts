import { describe, expect, it } from 'vitest';
import { messagesEn } from '../../locales/en';
import { claimModeLabel } from './claimModeLabel';

const start = messagesEn.bullshitUi.start;

describe('claimModeLabel', () => {
  it('shows the A to K range for the 52-card deck', () => {
    expect(claimModeLabel(start, 'rank', '52')).toBe('By rank (A→K)');
  });

  it('shows the 7 to A range for the 32-card deck', () => {
    expect(claimModeLabel(start, 'rank', '32')).toBe('By rank (7→A)');
  });

  it('shows both ranges while the deck is automatic', () => {
    expect(claimModeLabel(start, 'rank', 'auto')).toBe('By rank (7→A or A→K)');
    expect(claimModeLabel(start, 'rank')).toBe('By rank (7→A or A→K)');
  });

  it('leaves the suit mode untouched', () => {
    expect(claimModeLabel(start, 'suit', '32')).toBe('By suit');
  });
});
