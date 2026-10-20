import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentTrick from './PresidentTrick.vue';
import { buildPresidentCard, buildPresidentTrick } from '../../model/fixtures/president';

const nine = (suit: string) => buildPresidentCard('9', suit);

function mountTrick(trick = buildPresidentTrick(), extra: Record<string, unknown> = {}) {
  return mount(PresidentTrick, {
    props: { trick, mySeat: 0, nameOf: (seat: number) => (seat === 0 ? 'Alice' : `Seat ${seat}`), chips: [], ...extra },
  });
}

describe('the "N or nothing" state of equal play (the binding)', () => {
  const bound = (cards: number, rank = '9') => buildPresidentTrick({
    leader: 1,
    plays: [{ seat: 1, cards: [nine('CLUB')] }, { seat: 2, cards: [nine('HEART')] }],
    top: { kind: 'SET', rank, count: 1 },
    constraint: 'MATCH_OR_PASS', requiredRank: rank, requiredCount: 1,
    chain: { rank, plays: cards, cards: Array.from({ length: cards }, (_, i) => nine(['CLUB', 'HEART', 'DIAMOND', 'SPADE'][i])) },
  });
  const chips = [{ key: 'match', vars: { rank: '9' } }, { key: 'chain', vars: { count: 2, rank: '9' } }, { key: 'afterPass.reenter' }];

  it('givenABoundTrick_thenAFrameBannerSaysTheRankOrNothingAndHowManyOfTheFourNinesArePlayed', () => {
    const wrapper = mountTrick(bound(2), { chips });
    const banner = wrapper.get('[data-test="binding"]');
    expect(banner.get('[data-test="binding-rank"]').text()).toBe('9');
    expect(banner.text()).toContain('or nothing');
    expect(banner.findAll('[data-test="binding-pip"]')).toHaveLength(4);
    expect(banner.findAll('[data-test="binding-pip"].binding-pip--on')).toHaveLength(2);
    expect(banner.get('[data-test="binding-progress"]').text()).toBe('2 of 4 played');
    expect(wrapper.get('.trick-plate').classes()).toContain('trick-plate--bound');
  });

  it('givenTheBanner_thenTheMatchAndChainChipsAreNotRepeated_theOtherChipsStay', () => {
    const wrapper = mountTrick(bound(2), { chips });
    expect(wrapper.findAll('[data-test="rule-chip"]').map(c => c.text())).toEqual(['After a pass: play again']);
  });

  it('givenNoBinding_thenNoBannerAndTheChipsAreAsBefore', () => {
    const trick = buildPresidentTrick({ plays: [{ seat: 1, cards: [nine('CLUB')] }], top: { kind: 'SET', rank: '9', count: 1 } });
    const wrapper = mountTrick(trick, { chips: [{ key: 'beat', vars: { count: 1, rank: '9' } }] });
    expect(wrapper.find('[data-test="binding"]').exists()).toBe(false);
    expect(wrapper.get('.trick-plate').classes()).not.toContain('trick-plate--bound');
    expect(wrapper.findAll('[data-test="rule-chip"]')).toHaveLength(1);
  });

  it('givenAClosedTrickOnThePile_thenTheBannerStepsAside', () => {
    const cut = { winner: 2, reason: 'QUAD', closer: 2, cards: [nine('SPADE')], size: 4 };
    const wrapper = mountTrick(bound(3), { chips, pile: { play: { seat: 2, cards: cut.cards }, closure: cut, count: 4 } });
    expect(wrapper.find('[data-test="binding"]').exists()).toBe(false);
  });

  it('givenTheBannerIsThere_thenItIsInsideTheChipRowNotAnExtraBlock', () => {
    const wrapper = mountTrick(bound(2), { chips });
    expect(wrapper.get('[data-test="trick-chips"]').find('[data-test="binding"]').exists()).toBe(true);
  });
});

