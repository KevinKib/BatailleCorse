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
      { key: 'forbiddenFinish.lastPlace' },
      { key: 'quadClears.on' },
    ]);
  });

  it('givenAnEmptyTrickAfterRoundOne_thenNoLeadRuleIsShown', () => {
    const chips = ruleChips({ options: classic, trick: buildPresidentTrick(), round: 2 });
    expect(chips.map(c => c.key)).toEqual(['afterPass.reenter', 'forbiddenFinish.lastPlace', 'quadClears.on']);
  });

  it('givenATopPlay_thenTheChipSaysWhatToBeat', () => {
    const trick = buildPresidentTrick({ top: { kind: 'SET', rank: '9', count: 2 }, plays: [{ seat: 1, cards: [] }] });
    const chips = ruleChips({ options: classic, trick, round: 3 });
    expect(chips[0]).toEqual({ key: 'beat', vars: { count: 2, rank: '9' } });
  });

  it('givenAPassRuleOfOut_thenItsChipFollowsTheOption', () => {
    const chips = ruleChips({ options: { ...classic, afterPass: 'out' }, trick: buildPresidentTrick(), round: 2 });
    expect(chips.map(c => c.key)).toContain('afterPass.out');
    expect(chips.map(c => c.key)).not.toContain('afterPass.reenter');
  });

  it('givenAnOptionValueTheClientDoesNotKnow_thenItGetsNoChip', () => {
    const chips = ruleChips({
      options: { afterPass: 'sideways', twoCuts: 'triple', mustPlay: 'maybe' }, trick: buildPresidentTrick(), round: 2,
    });
    expect(chips).toEqual([]);
  });
});

describe('ruleChips, equal play', () => {
  const bound = buildPresidentTrick({
    top: { kind: 'SET', rank: 'NINE', count: 1 }, plays: [{ seat: 1, cards: [] }],
    constraint: 'MATCH_OR_PASS', requiredRank: 'NINE', requiredCount: 1,
    chain: { rank: 'NINE', cards: [], plays: 2 },
  });

  it('givenTheBinding_thenTheMatchChipReplacesTheBeatChipAndTheChainIsShown', () => {
    const chips = ruleChips({ options: classic, trick: bound, round: 3 });
    expect(chips.slice(0, 2)).toEqual([
      { key: 'match', vars: { rank: 'NINE' } },
      { key: 'chain', vars: { count: 2, rank: 'NINE' } },
    ]);
    expect(chips.map(c => c.key)).not.toContain('beat');
  });

  it('givenAFreeTrick_thenNoMatchChipIsShown', () => {
    const trick = buildPresidentTrick({ top: { kind: 'SET', rank: 'NINE', count: 1 }, constraint: 'BEAT' });
    expect(ruleChips({ options: classic, trick, round: 3 }).map(c => c.key))
      .toEqual(['beat', 'afterPass.reenter', 'forbiddenFinish.lastPlace', 'quadClears.on']);
  });
});

describe('ruleChips, the trick variants', () => {
  const keys = (options: Record<string, string>) =>
    ruleChips({ options, trick: buildPresidentTrick(), round: 3 }).map(c => c.key);

  it('givenTheClassicDefaults_thenTheForbiddenFinishAndTheFourAreShownAndNoCutOrCompulsoryPlay', () => {
    expect(keys(classic)).toEqual(['afterPass.reenter', 'forbiddenFinish.lastPlace', 'quadClears.on']);
  });

  it.each([
    ['single', 'twoCuts.single'],
    ['ladder', 'twoCuts.ladder'],
  ])('givenTwoCuts_%s_thenItsChipIsShown', (value, chip) => {
    expect(keys({ ...classic, twoCuts: value })).toContain(chip);
  });

  it('givenCompulsoryPlay_thenItsChipIsShown', () => {
    expect(keys({ ...classic, mustPlay: 'on' })).toContain('mustPlay.on');
  });

  it('givenTheVariantsSwitchedOff_thenTheirChipsGoAway', () => {
    const chips = keys({ ...classic, quadClears: 'off', forbiddenFinish: 'off', twoCuts: 'off', mustPlay: 'off' });
    expect(chips).toEqual(['afterPass.reenter']);
  });
});
