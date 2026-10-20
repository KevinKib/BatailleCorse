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

  it('givenWhatTheTableShowsSoFar_thenTheTextsSayItWhileTheCardsAreThePile', () => {
    // The pile (the latest state, under the flights) has three plays; the table has only shown the first one so far.
    const pile = { play: { seat: 3, cards: [nine('SPADE')] }, closure: null, count: 3 };
    const display = { play: { seat: 1, cards: [buildPresidentCard('7', 'CLUB')] }, closure: null, count: 1 };
    const wrapper = mountTrick(buildPresidentTrick(), { pile, display });
    expect(wrapper.get('[data-test="trick-by"]').text()).toBe('Seat 1 played');
    expect(wrapper.get('[data-test="trick-count"]').text()).toBe('1 card in the trick');
    expect(wrapper.findAll('[data-test="trick-card"]')).toHaveLength(1);
    expect(wrapper.get('[data-test="trick-card"]').attributes('aria-label')).toContain('9');
  });

  it('givenAClosureNotShownYet_thenNoBadge', () => {
    const cut = { winner: 2, reason: 'CUT', closer: 2, cards: [buildPresidentCard('2', 'CLUB')], size: 2, trickCards: [] };
    const pile = { play: { seat: 2, cards: cut.cards }, closure: cut, count: 2 };
    const display = { play: { seat: 1, cards: [nine('CLUB')] }, closure: null, count: 1 };
    const wrapper = mountTrick(buildPresidentTrick(), { pile, display });
    expect(wrapper.find('[data-test="trick-closed"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="trick-by"]').text()).toBe('Seat 1 played');
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

  describe('the trick indicator and the closed trick (spec §11 decision 7)', () => {
    const cut = { winner: 2, reason: 'CUT', closer: 2, cards: [buildPresidentCard('2', 'CLUB')], size: 3 };

    it('givenPlaysOnTheTrick_thenTheIndicatorCountsItsCards', () => {
      const trick = buildPresidentTrick({ plays: [{ seat: 1, cards: [nine('CLUB')] }, { seat: 2, cards: [nine('HEART'), nine('SPADE')] }] });
      expect(mountTrick(trick).get('[data-test="trick-count"]').text()).toBe('3 cards in the trick');
    });

    it('putsTheIndicatorFirstInTheRowOfChips_soThePileTakesOneRowLess', () => {
      const trick = buildPresidentTrick({ plays: [{ seat: 1, cards: [nine('CLUB')] }] });
      const row = mountTrick(trick, { chips: [{ key: 'afterPass.reenter' }] }).get('[data-test="trick-chips"]');
      expect(row.element.firstElementChild?.getAttribute('data-test')).toBe('trick-count');
      expect(row.findAll('[data-test="rule-chip"]')).toHaveLength(1);
    });

    it('givenAClosedTrick_thenTheBadgeTakesThePlaceOfWhoPlayed_outsideTheCards', () => {
      const wrapper = mountTrick(buildPresidentTrick(), { pile: { play: { seat: 2, cards: cut.cards }, closure: cut, count: 3 } });
      expect(wrapper.find('[data-test="trick-by"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="trick-cards"] [data-test="trick-closed"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="trick-closed"]').classes()).toContain('trick-closed--line');
    });

    it('givenAnEmptyTrick_thenNoIndicator', () => {
      expect(mountTrick().find('[data-test="trick-count"]').exists()).toBe(false);
    });

    it('givenAClosedTrick_thenTheClosingPlayStaysDimmedWithABadgeSayingWhyAndWhoLeads', () => {
      const wrapper = mountTrick(buildPresidentTrick(), { pile: { play: { seat: 2, cards: cut.cards }, closure: cut, count: 3 } });
      const badge = wrapper.get('[data-test="trick-closed"]');
      expect(badge.attributes('data-reason')).toBe('CUT');
      expect(badge.get('[data-test="trick-closed-reason"]').text()).toBe('Closed by a 2');
      expect(badge.get('[data-test="trick-closed-leader"]').text()).toBe('Seat 2 leads');
      expect(wrapper.findAll('[data-test="closed-trick-card"]')).toHaveLength(1);
      // Not a play to beat: the cards of the trick in progress are a different test id.
      expect(wrapper.find('[data-test="trick-card"]').exists()).toBe(false);
      expect(wrapper.get('.trick-plate').classes()).toContain('trick-plate--closed');
      expect(wrapper.get('[data-test="trick-count"]').text()).toBe('3 cards in the trick');
    });

    it('givenIWinTheClosedTrick_thenItSaysYouLead', () => {
      const wrapper = mountTrick(buildPresidentTrick(), { pile: { play: null, closure: { ...cut, winner: 0, reason: 'ALL_PASSED' }, count: 3 } });
      expect(wrapper.get('[data-test="trick-closed-reason"]').text()).toBe('Everyone passed');
      expect(wrapper.get('[data-test="trick-closed-leader"]').text()).toBe('You lead');
    });

    it('givenAnUnknownReason_thenAPlainClosedBadge', () => {
      const wrapper = mountTrick(buildPresidentTrick(), { pile: { play: null, closure: { ...cut, reason: 'NEW' }, count: 3 } });
      expect(wrapper.get('[data-test="trick-closed-reason"]').text()).toBe('Trick closed');
    });

    it('givenTheClosedCardsHaveBeenSweptAway_thenThePileIsEmptyButTheBadgeAndTheCountStay', () => {
      const wrapper = mountTrick(buildPresidentTrick(), { pile: { play: { seat: 2, cards: cut.cards }, closure: cut, count: 3 }, swept: true });
      const cards = wrapper.get('[data-test="trick-cards"]');
      expect(cards.classes()).toContain('trick-cards--swept');
      expect(cards.attributes('aria-hidden')).toBe('true');
      expect(wrapper.get('[data-test="trick-closed-reason"]').text()).toBe('Closed by a 2');
      expect(wrapper.get('[data-test="trick-closed-leader"]').text()).toBe('Seat 2 leads');
      expect(wrapper.get('[data-test="trick-count"]').text()).toBe('3 cards in the trick');
    });

    it('givenAnOpenTrick_thenSweptHidesNothing', () => {
      const trick = buildPresidentTrick({ plays: [{ seat: 1, cards: [nine('CLUB')] }] });
      const wrapper = mountTrick(trick, { swept: true });
      expect(wrapper.get('[data-test="trick-cards"]').classes()).not.toContain('trick-cards--swept');
    });

    it('givenStill_thenTheClosingFlashDoesNotPlay', () => {
      const wrapper = mountTrick(buildPresidentTrick(), { pile: { play: { seat: 2, cards: cut.cards }, closure: cut, count: 3 }, still: true });
      expect(wrapper.get('.trick-plate').classes()).toContain('trick-plate--still');
    });

    it('keepsTheNotesAsASecondaryLineForScreenReaders', () => {
      const wrapper = mountTrick(buildPresidentTrick(), { notes: ['Bob closed the trick with a 2.'] });
      expect(wrapper.get('[data-test="trick-notes"]').classes()).toContain('sr-only');
    });
  });

  describe('the reserved places (nothing moves when a text comes or goes)', () => {
    it('givenAnEmptyTrick_thenTheLineUnderThePileAndTheChipRowAreStillThere', () => {
      const wrapper = mountTrick();
      expect(wrapper.find('[data-test="trick-line"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="trick-chips"]').exists()).toBe(true);
      expect(wrapper.findAll('[data-test="rule-chip"]')).toHaveLength(0);
    });

    it('putsWhoPlayedAndTheClosedBadgeInTheSameLine', () => {
      const play = buildPresidentTrick({ plays: [{ seat: 1, cards: [nine('CLUB')] }], top: { kind: 'SET', rank: '9', count: 1 } });
      expect(mountTrick(play).get('[data-test="trick-line"] [data-test="trick-by"]').text()).toBe('Seat 1 played');
      const cut = { winner: 2, reason: 'CUT', closer: 2, cards: [buildPresidentCard('2', 'CLUB')], size: 3 };
      const closed = mountTrick(buildPresidentTrick(), { pile: { play: { seat: 2, cards: cut.cards }, closure: cut, count: 3 } });
      expect(closed.get('[data-test="trick-line"] [data-test="trick-closed"]').exists()).toBe(true);
      expect(closed.findAll('[data-test="trick-line"]')).toHaveLength(1);
    });

    it('keepsTheEmptyTrickTextInThePlaceOfTheCards', () => {
      expect(mountTrick().get('[data-test="trick-stage"] [data-test="trick-empty"]').text()).toBe('Nothing played yet');
    });
  });

  it('givenAnUnknownChipKey_thenNothingIsShownForIt', () => {
    const wrapper = mountTrick(buildPresidentTrick(), { chips: [{ key: 'nope' }] });
    expect(wrapper.findAll('[data-test="rule-chip"]')).toHaveLength(0);
  });
});
