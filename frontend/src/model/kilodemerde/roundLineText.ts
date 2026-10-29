import { format } from '../../locales/format';
import type { Messages } from '../../locales/Messages';
import { kiloLabel, type FaceLetters } from './kilo';
import type { RoundLine } from './roundSummary';

/**
 * The text of one round line, said by the banner and the live region (`name` of the drawer, `card` in short form, `kg`).
 * A line about my own seat takes its "you" variant, since "you" has its own verb form ("Vous prenez", not "Vous prend").
 */
export function roundLineText(
  line: RoundLine, nameOf: (seat: number) => string, outcome: Messages['kiloDeMerdeUi']['outcome'], mySeat: number | null,
  /** The face letters of the language (`faceLetters(ui.ranks)`); English when omitted. */
  letters?: FaceLetters,
): string {
  const mine = line.seat !== null && line.seat === mySeat;
  const vars = { name: line.seat === null ? '' : nameOf(line.seat), card: line.card ? kiloLabel(line.card, letters) : '', kg: line.kg };
  switch (line.code) {
    case 'LAST_DRAWS': return format(mine ? outcome.lastYou : outcome.last, vars);
    case 'FALSE_SLAP': return format(mine ? outcome.falseSlapYou : outcome.falseSlap, vars);
    case 'FLUSH': return format(mine ? outcome.flushYou : outcome.flush, vars);
    case 'NO_CARD': return format(mine ? outcome.noCardYou : outcome.noCard, vars);
    case 'VOIDED': return outcome.voided;
    default: return outcome.stalled;
  }
}
