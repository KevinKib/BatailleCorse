import { format, formatList, plural } from '../../locales/format';
import type { PresidentUiMessages } from '../../locales/Messages';
import type { SelectionRefusal } from './selection';
import { rankLabel } from './cardLabel';

/** The hint line under the Play button: why the selection cannot be played. Empty when it can. */
export function hintText(refusal: SelectionRefusal | null, ui: PresidentUiMessages): string {
  if (!refusal) return '';
  switch (refusal.code) {
    case 'nothingSelected': return ui.hint.nothingSelected;
    case 'mixedRanks': return ui.hint.mixedRanks;
    case 'mustPass': return ui.hint.mustPass;
    case 'cannotPlay': return ui.hint.cannotPlay;
    case 'notPlayable': return ui.hint.notPlayable;
    case 'wrongCount': return format(ui.hint.wrongCount, { counts: formatList(refusal.counts.map(String), 'disjunction') });
    case 'notHigher': return plural(ui.hint.notHigher, refusal.count, { rank: rankLabel(refusal.rank, ui) });
    case 'matchOnly': return plural(ui.hint.matchOnly, refusal.count, { rank: rankLabel(refusal.rank, ui) });
  }
}
