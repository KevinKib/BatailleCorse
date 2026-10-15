import { format, plural } from '../../locales/format';
import type { MangolaUiMessages } from '../../locales/Messages';
import { pitName } from './geometry';
import type { MangolaLastMove } from './MangolaState';
import { capturedCount } from './trace';

/** The move in words for the live region: who, from which pit, how many relays and seeds captured. */
export function moveSentence(move: MangolaLastMove, player: string, ui: MangolaUiMessages): string {
  const relays = move.trace.filter(step => step.type === 'RELAY').length;
  const captured = capturedCount(move.trace);
  const details: string[] = [];
  if (relays > 0) details.push(plural(ui.move.relays, relays));
  if (captured > 0) details.push(plural(ui.move.captured, captured));
  const head = format(ui.move.sowed, { player, pit: pitName(move.pit) });
  return details.length > 0 ? `${head}: ${details.join(', ')}.` : `${head}.`;
}
