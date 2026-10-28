import { format } from '../../locales/format';
import type { Messages } from '../../locales/Messages';
import { kiloLabel } from './kilo';
import type { RoundLine } from './roundSummary';

/** The text of one round line, said by the banner and the live region (`name` of the drawer, `card` in short form, `kg`). */
export function roundLineText(line: RoundLine, nameOf: (seat: number) => string, outcome: Messages['kiloDeMerdeUi']['outcome']): string {
  const vars = { name: line.seat === null ? '' : nameOf(line.seat), card: line.card ? kiloLabel(line.card) : '', kg: line.kg };
  switch (line.code) {
    case 'LAST_DRAWS': return format(outcome.last, vars);
    case 'FALSE_SLAP': return format(outcome.falseSlap, vars);
    case 'FLUSH': return format(outcome.flush, vars);
    case 'VOIDED': return outcome.voided;
    default: return outcome.stalled;
  }
}
