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

describe('PresidentTrick', () => {
  it('givenAnEmptyTrick_thenItSaysNothingWasPlayed', () => {
    const wrapper = mountTrick();
    expect(wrapper.get('[data-test="trick-empty"]').text()).toBe('Nothing played yet');
    expect(wrapper.findAll('img')).toHaveLength(0);
  });

  it('givenAPlay_thenItShowsTheTopPlayCardsAndWhoMadeIt', () => {
    const trick = buildPresidentTrick({
      leader: 1,
      plays: [{ seat: 1, cards: [nine('CLUB')] }, { seat: 2, cards: [nine('HEART'), nine('SPADE')] }],
      top: { kind: 'SET', rank: '9', count: 2 },
    });
    const wrapper = mountTrick(trick);
    expect(wrapper.get('[data-test="trick-by"]').text()).toBe('Seat 2 played');
    expect(wrapper.findAll('[data-test="trick-card"]')).toHaveLength(2);
    expect(wrapper.find('[data-test="trick-empty"]').exists()).toBe(false);
  });

  it('givenMyOwnPlay_thenItSaysYouPlayed', () => {
    const trick = buildPresidentTrick({ plays: [{ seat: 0, cards: [nine('CLUB')] }], top: { kind: 'SET', rank: '9', count: 1 } });
    expect(mountTrick(trick).get('[data-test="trick-by"]').text()).toBe('You played');
  });

  it('showsTheRuleChipsAsText', () => {
    const wrapper = mountTrick(buildPresidentTrick(), {
      chips: [{ key: 'beat', vars: { count: 2, rank: 'JACK' } }, { key: 'afterPass.reenter' }],
    });
    const chips = wrapper.findAll('[data-test="rule-chip"]').map(c => c.text());
    expect(chips).toEqual(['To beat: 2 × Jack', 'After a pass: play again']);
  });

  it('givenNoNote_thenNoNoteLineIsShown', () => {
    expect(mountTrick().find('[data-test="trick-note"]').exists()).toBe(false);
  });

  it('givenNotes_thenEachIsAnAnnouncedLineUnderThePile', () => {
    const wrapper = mountTrick(buildPresidentTrick(), { notes: ['A 2 cut the trick.', 'Seat 1 leads.'] });
    const notes = wrapper.findAll('[data-test="trick-note"]');
    expect(notes.map(n => n.text())).toEqual(['A 2 cut the trick.', 'Seat 1 leads.']);
    expect(wrapper.get('[data-test="trick-notes"]').attributes('aria-live')).toBe('polite');
  });

  it('givenAnUnknownChipKey_thenNothingIsShownForIt', () => {
    const wrapper = mountTrick(buildPresidentTrick(), { chips: [{ key: 'nope' }] });
    expect(wrapper.findAll('[data-test="rule-chip"]')).toHaveLength(0);
  });
});
