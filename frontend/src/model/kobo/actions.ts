import type { KoboState } from './KoboState';

/** A button of the action bar (a key; the labels live in the locales). */
export type KoboBarButton = 'ready' | 'draw' | 'discard' | 'skip' | 'endTurn' | 'announce';

/**
 * The buttons of the action bar, straight from the server's `availableActions`. Actions that are a click on a
 * card (swap, peek, blind swap, king swap) are not buttons, and the matching discard and the gift belong to the
 * steps that bring their own controls. "I have memorised" is the READY of the memorising phase only.
 */
export function barButtons(game: KoboState): KoboBarButton[] {
  const listed = (action: KoboState['availableActions'][number]) => game.availableActions.includes(action);
  const buttons: KoboBarButton[] = [];
  if (listed('READY') && game.phase === 'MEMORISING') buttons.push('ready');
  if (listed('DRAW')) buttons.push('draw');
  if (listed('DISCARD_DRAWN')) buttons.push('discard');
  if (listed('SKIP_POWER')) buttons.push('skip');
  if (listed('END_TURN')) buttons.push('endTurn');
  if (listed('ANNOUNCE_KOBO')) buttons.push('announce');
  return buttons;
}
