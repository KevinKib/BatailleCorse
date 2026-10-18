import type { PresidentState } from './PresidentState';

/**
 * The rule chips next to the pile (owner decision 6C, provisional). A chip is a key into
 * `presidentUi.chips` and the values its text quotes. Only what the DTO sends today is shown: the game options in
 * force and the trick's binding. The revolution has no field yet; the variant PRs add their chips here when the
 * server sends them.
 */
export interface PresidentChip {
  key: string;
  vars?: Record<string, string | number>;
}

type ChipContext = Pick<PresidentState, 'options' | 'trick' | 'round'>;

/** The values of an option that have a chip text: any other value (a variant the client does not know) gets none. */
const KNOWN: Record<string, readonly string[]> = {
  afterPass: ['reenter', 'out'],
  // the variants that change the table get a chip; their default-off value (no compulsory play) does not
  mustPlay: ['on'],
  reducedCut: ['on'],
};

function known(options: Record<string, string>, name: string): string | null {
  const value = options[name];
  return value !== undefined && KNOWN[name]?.includes(value) ? value : null;
}

export function ruleChips(state: ChipContext): PresidentChip[] {
  const chips: PresidentChip[] = [];
  const top = state.trick.top;
  if (state.trick.constraint === 'MATCH_OR_PASS' && state.trick.requiredRank) {
    // Equal play: the binding replaces "to beat" (only the same rank and count may follow), and the chain says
    // how long the run of equal plays is.
    chips.push({ key: 'match', vars: { rank: state.trick.requiredRank } });
    const chain = state.trick.chain;
    if (chain) chips.push({ key: 'chain', vars: { count: chain.plays, rank: chain.rank } });
  } else if (top) {
    chips.push({ key: 'beat', vars: { count: top.count, rank: top.rank } });
  }
  // Equal play, the king of hearts lead and the forbidden finish are fixed rules: only the options get a chip.
  const afterPass = known(state.options, 'afterPass');
  if (afterPass) chips.push({ key: `afterPass.${afterPass}` });
  const mustPlay = known(state.options, 'mustPlay');
  if (mustPlay) chips.push({ key: `mustPlay.${mustPlay}` });
  const reducedCut = known(state.options, 'reducedCut');
  if (reducedCut) chips.push({ key: `reducedCut.${reducedCut}` });
  return chips;
}
