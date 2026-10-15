import { describe, it, expect } from 'vitest';
import { ruleChips } from './chips';
import { buildPresidentTrick, CLASSIC_PRESIDENT_OPTIONS } from '../fixtures/president';

const classic = { ...CLASSIC_PRESIDENT_OPTIONS };

describe('ruleChips', () => {
  it('givenAnEmptyTrickInRoundOne_thenTheLeadRuleAndThePassRuleAreShown', () => {
    const chips = ruleChips({ options: classic, trick: buildPresidentTrick(), round: 1 });
    expect(chips).toEqual([
      { key: 'firstLead.kingOfHearts' },
      { key: 'afterPass.reenter' },
    ]);
  });

  it('givenAnEmptyTrickAfterRoundOne_thenNoLeadRuleIsShown', () => {
    const chips = ruleChips({ options: classic, trick: buildPresidentTrick(), round: 2 });
    expect(chips.map(c => c.key)).toEqual(['afterPass.reenter']);
  });

  it('givenATopPlay_thenTheChipSaysWhatToBeat', () => {
    const trick = buildPresidentTrick({ top: { kind: 'SET', rank: '9', count: 2 }, plays: [{ seat: 1, cards: [] }] });
    const chips = ruleChips({ options: classic, trick, round: 3 });
    expect(chips[0]).toEqual({ key: 'beat', vars: { count: 2, rank: '9' } });
  });

  it('givenAPassRuleOfOut_thenItsChipFollowsTheOption', () => {
    const chips = ruleChips({ options: { ...classic, afterPass: 'out' }, trick: buildPresidentTrick(), round: 2 });
    expect(chips.map(c => c.key)).toEqual(['afterPass.out']);
  });

  it('givenAnOptionValueTheClientDoesNotKnow_thenItGetsNoChip', () => {
    const chips = ruleChips({ options: { ...classic, afterPass: 'sideways' }, trick: buildPresidentTrick(), round: 2 });
    expect(chips).toEqual([]);
  });
});
