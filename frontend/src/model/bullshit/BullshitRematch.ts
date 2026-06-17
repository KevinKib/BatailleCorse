import type { RematchButton } from '../RematchButton';

export interface BullshitRematchProgress {
  iRequested: boolean;
  ready: number;
  eligible: number;
}

/** Bullshit's Play-Again button: idle until I click, then a waiting label with live progress. */
export function bullshitRematchButton(p: BullshitRematchProgress): RematchButton {
  if (!p.iRequested) return { label: 'Play again', disabled: false };
  return { label: `Waiting… ${p.ready}/${p.eligible} ready`, disabled: true };
}
