
import { describe, it, expect } from 'vitest';
import { cardSrc, backSrc } from '../../model/fixtures/cardSrc';
import { mount } from '@vue/test-utils';
import KoboTableau from './KoboTableau.vue';
import { slotKey } from '../../model/kobo/interaction';
import { buildKoboCard, buildKoboPlayer, buildKoboSlot } from '../../model/fixtures/kobo';

function mountTableau(props: Record<string, unknown> = {}) {
  return mount(KoboTableau, {
    props: { player: buildKoboPlayer(1), owner: "Bob's", groupLabel: "Bob's cards", faces: {}, compact: false, ...props },
  });
}

describe('KoboTableau', () => {
  it('givenFourCards_thenFourFaceDownSlotsNamedFromOne', () => {
    const wrapper = mountTableau();
    const buttons = wrapper.findAll('button');
    expect(buttons).toHaveLength(4);
    expect(buttons.map(b => b.attributes('aria-label'))).toEqual([
      "Bob's card 1, face down", "Bob's card 2, face down", "Bob's card 3, face down", "Bob's card 4, face down",
    ]);
    expect(wrapper.find('[role="group"]').attributes('aria-label')).toBe("Bob's cards");
  });

  it('givenAFaceFromTheState_thenThatSlotShowsItAndNamesIt', () => {
    const faces = { [slotKey(1, 2)]: buildKoboCard('KING', 'HEART') };
    const wrapper = mountTableau({ faces });
    expect(wrapper.get('[data-test="slot-1-2"]').attributes('aria-label')).toBe("Bob's card 3: king of heart");
    expect(wrapper.get('[data-test="slot-1-2"] img').attributes('src')).toBe(cardSrc('KING', 'HEART'));
    expect(wrapper.get('[data-test="slot-1-0"] img').attributes('src')).toBe(backSrc());
  });

  it('givenNoFaceForASlot_thenItNeverShowsAValue', () => {
    const wrapper = mountTableau({ player: buildKoboPlayer(1, { slots: [buildKoboSlot(0, { revision: 3 })], handSize: 1 }) });
    expect(wrapper.get('[data-test="slot-1-0"] img').attributes('src')).toBe(backSrc());
  });

  it('givenAnEmptySlot_thenItIsAPlaceholderWithoutAButton', () => {
    const slots = [buildKoboSlot(0), buildKoboSlot(1, { occupied: false }), buildKoboSlot(2), buildKoboSlot(3)];
    const wrapper = mountTableau({ player: buildKoboPlayer(1, { slots, handSize: 3 }) });
    expect(wrapper.findAll('button')).toHaveLength(3);
    expect(wrapper.find('[data-test="slot-1-1"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="slot-empty-1-1"]').exists()).toBe(true);
  });

  it('givenAPenaltyCard_thenItOpensAThirdColumn', () => {
    const wrapper = mountTableau({ player: buildKoboPlayer(1, { handSize: 5 }) });
    expect(wrapper.get('.tableau').attributes('style')).toContain('--columns: 3');
    expect(wrapper.get('[data-test="slot-1-4"]').attributes('style')).toContain('grid-column: 3');
    expect(wrapper.get('[data-test="slot-1-4"]').attributes('style')).toContain('grid-row: 2');
    expect(wrapper.get('[data-test="slot-1-2"]').attributes('style')).toContain('grid-row: 1');
  });

  it('givenNothingClickable_thenEverySlotIsDisabled', () => {
    const wrapper = mountTableau();
    expect(wrapper.findAll('button').every(b => b.attributes('disabled') !== undefined)).toBe(true);
  });

  it('givenAClickableSlot_thenItIsEnabledAndEmitsItsPosition', async () => {
    const wrapper = mountTableau({ clickable: { [slotKey(1, 3)]: true } });
    const slot = wrapper.get('[data-test="slot-1-3"]');
    expect(slot.attributes('disabled')).toBeUndefined();
    await slot.trigger('click');
    expect(wrapper.emitted('pick')![0]).toEqual([1, 3]);
  });

  it('givenTheCompactFlag_thenTheTableauCarriesIt', () => {
    expect(mountTableau({ compact: true }).get('.tableau').classes()).toContain('tableau--compact');
  });

  it('givenASelectedSlot_thenOnlyThatSlotIsMarkedPressed', () => {
    const wrapper = mountTableau({ selected: 2, clickable: { [slotKey(1, 2)]: true } });
    expect(wrapper.get('[data-test="slot-1-2"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-test="slot-1-2"]').classes()).toContain('slot--selected');
    expect(wrapper.get('[data-test="slot-1-1"]').attributes('aria-pressed')).toBeUndefined();
  });

  describe('slots named by the last event', () => {
    it('givenHighlightedKeys_thenOnlyThoseSlotsAreMarked', () => {
      const wrapper = mountTableau({ highlighted: [slotKey(1, 2)] });
      expect(wrapper.get('[data-test="slot-1-2"]').attributes('data-highlighted')).toBe('true');
      expect(wrapper.get('[data-test="slot-1-0"]').attributes('data-highlighted')).toBeUndefined();
    });

    it('givenAHighlightedEmptySlot_thenItsPlaceholderIsMarkedToo', () => {
      const slots = [buildKoboSlot(0), buildKoboSlot(1, { occupied: false })];
      const wrapper = mountTableau({ player: buildKoboPlayer(1, { slots, handSize: 1 }), highlighted: [slotKey(1, 1)] });
      expect(wrapper.get('[data-test="slot-empty-1-1"]').attributes('data-highlighted')).toBe('true');
    });

    it('givenNoHighlight_thenNothingIsMarked', () => {
      expect(mountTableau().findAll('[data-highlighted]')).toHaveLength(0);
    });
  });

  describe('one-click match mode', () => {
    const clickable = { [slotKey(1, 0)]: true, [slotKey(1, 1)]: true };

    it('givenMatchMode_thenClickableSlotsSayTheyMatchAndAreMarkedLightly', () => {
      const wrapper = mountTableau({ clickable, matchMode: true });
      const slot = wrapper.get('[data-test="slot-1-0"]');
      expect(slot.attributes('aria-label')).toBe("Bob's card 1, face down: match this card");
      expect(slot.attributes('title')).toBe('Match this card');
      expect(slot.attributes('data-match')).toBe('true');
      expect(slot.classes()).toContain('slot--match');
    });

    it('givenARivalTableauInMatchMode_thenTheNameSaysThatAGiveFollows', () => {
      const wrapper = mountTableau({ clickable, matchMode: true, rival: true });
      expect(wrapper.get('[data-test="slot-1-0"]').attributes('aria-label'))
        .toBe("Bob's card 1, face down: match this card, then pick one of yours to give");
    });

    it('givenARivalSlotPickedToMatch_thenItIsMarkedPressedAndNamedAsPicked', () => {
      const wrapper = mountTableau({ clickable: {}, rival: true, picked: 2 });
      const slot = wrapper.get('[data-test="slot-1-2"]');
      expect(slot.attributes('aria-label')).toBe("Bob's card 3, face down: picked to match");
      expect(slot.attributes('aria-pressed')).toBe('true');
      expect(slot.classes()).toContain('slot--selected');
      expect(wrapper.get('[data-test="slot-1-1"]').attributes('aria-pressed')).toBeUndefined();
    });

    it('givenMatchMode_thenASlotThatIsNotClickableKeepsItsPlainName', () => {
      const wrapper = mountTableau({ clickable, matchMode: true });
      const slot = wrapper.get('[data-test="slot-1-3"]');
      expect(slot.attributes('aria-label')).toBe("Bob's card 4, face down");
      expect(slot.attributes('data-match')).toBeUndefined();
      expect(slot.attributes('title')).toBeUndefined();
    });

    it('givenNoMatchMode_thenClickableSlotsAreNamedAsBefore', () => {
      const slot = mountTableau({ clickable }).get('[data-test="slot-1-0"]');
      expect(slot.attributes('aria-label')).toBe("Bob's card 1, face down");
      expect(slot.attributes('data-match')).toBeUndefined();
    });

    it('givenMatchMode_thenAFaceUpSlotKeepsItsCardInTheName', () => {
      const faces = { [slotKey(1, 0)]: buildKoboCard('KING', 'HEART') };
      const slot = mountTableau({ clickable, matchMode: true, faces }).get('[data-test="slot-1-0"]');
      expect(slot.attributes('aria-label')).toBe("Bob's card 1: king of heart: match this card");
    });
  });
});
