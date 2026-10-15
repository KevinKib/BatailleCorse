import type { PresidentState } from './PresidentState';

/**
 * The rule chips next to the pile (owner decision 6C, provisional). A chip is a key into
 * `presidentUi.chips` and the values its text quotes. Only what the DTO sends today is shown: the game options in
 * force and the top play. The constraint, the required rank, the chain and the revolution have no field yet; the
 * variant PRs add their chips here when the server sends them.
 */
export interface PresidentChip {
  key: string;
  vars?: Record<string, string | number>;
}

type ChipContext = Pick<PresidentState, 'options' | 'trick' | 'round'>;

/** The values of an option that have a chip text: any other value (a variant the client does not know) gets none. */
const KNOWN: Record<string, readonly string[]> = {
  afterPass: ['reenter', 'out'],
  firstLead: ['kingOfHearts', 'lowestClub', 'queenOfHearts', 'random'],
};

function known(options: Record<string, string>, name: string): string | null {
  const value = options[name];
  return value !== undefined && KNOWN[name]?.includes(value) ? value : null;
}

export function ruleChips(state: ChipContext): PresidentChip[] {
  const chips: PresidentChip[] = [];
  const top = state.trick.top;
  if (top) chips.push({ key: 'beat', vars: { count: top.count, rank: top.rank } });
  // The lead rule only matters to the first player of the match.
  const firstLead = known(state.options, 'firstLead');
  if (state.round === 1 && state.trick.plays.length === 0 && firstLead) chips.push({ key: `firstLead.${firstLead}` });
  const afterPass = known(state.options, 'afterPass');
  if (afterPass) chips.push({ key: `afterPass.${afterPass}` });
  return chips;
}
