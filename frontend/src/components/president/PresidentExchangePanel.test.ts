import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import PresidentExchangePanel from './PresidentExchangePanel.vue';
import type { PresidentExchangePair, PresidentTitle } from '../../model/president/PresidentState';

// Five players: seat 0 President, 1 Vice-President, 2 Neutral, 3 Vice-Scum, 4 Scum. Pairs: 0 and 4 swap two cards,
// 1 and 3 swap one.
const PAIRS: PresidentExchangePair[] = [
  { high: 0, low: 4, count: 2, done: false },
  { high: 1, low: 3, count: 1, done: false },
];
const NAMES = ['Ann', 'Ben', 'Cy', 'Di', 'Ed'];

function mountPanel(mySeat: number, title: PresidentTitle, props: Record<string, unknown> = {}) {
  return mount(PresidentExchangePanel, {
    props: {
      pairs: PAIRS, mySeat, title, receivedCount: 0, selectedCount: 0, canGive: false,
      nameOf: (seat: number) => NAMES[seat], ...props,
    },
    global: { plugins: [PrimeVue] },
  });
}
const text = (w: ReturnType<typeof mountPanel>, id: string) => w.get(`[data-test="${id}"]`).text();

describe('PresidentExchangePanel', () => {
  describe('President', () => {
    it('namesTheRoleTheCardsReceivedAndWhatToGive', () => {
      const w = mountPanel(0, 'PRESIDENT', { receivedCount: 2 });
      expect(text(w, 'exchange-role')).toBe('You are the President');
      expect(text(w, 'exchange-received')).toBe('Ed gave you their 2 best cards. They are tagged in your hand.');
      expect(text(w, 'exchange-instruction')).toBe('Choose 2 cards to give to Ed.');
    });

    it('theGiveButtonNamesTheCountAndThePartnerAndIsDisabledUntilTheCountIsRight', async () => {
      const w = mountPanel(0, 'PRESIDENT', { receivedCount: 2, selectedCount: 1 });
      expect(text(w, 'exchange-give')).toBe('Give 2 cards to Ed');
      expect(w.get('[data-test="exchange-give"]').attributes('disabled')).toBeDefined();
      await w.setProps({ selectedCount: 2, canGive: true });
      expect(w.get('[data-test="exchange-give"]').attributes('disabled')).toBeUndefined();
      await w.get('[data-test="exchange-give"]').trigger('click');
      expect(w.emitted('give')).toHaveLength(1);
    });

    it('suggestAndClearAreEmitted', async () => {
      const w = mountPanel(0, 'PRESIDENT', { selectedCount: 1 });
      await w.get('[data-test="exchange-suggest"]').trigger('click');
      expect(w.emitted('suggest')).toHaveLength(1);
      await w.get('[data-test="exchange-clear"]').trigger('click');
      expect(w.emitted('clear')).toHaveLength(1);
    });

    it('withoutASelectionThereIsNothingToClear', () => {
      expect(mountPanel(0, 'PRESIDENT').find('[data-test="exchange-clear"]').exists()).toBe(false);
    });
  });

  describe('Vice-President', () => {
    it('givesOneCardAndTheTextsAreSingular', () => {
      const w = mountPanel(1, 'VICE_PRESIDENT', { receivedCount: 1 });
      expect(text(w, 'exchange-role')).toBe('You are the Vice-President');
      expect(text(w, 'exchange-received')).toBe('Di gave you their best card. It is tagged in your hand.');
      expect(text(w, 'exchange-instruction')).toBe('Choose 1 card to give to Di.');
      expect(text(w, 'exchange-give')).toBe('Give 1 card to Di');
    });
  });

  describe('Neutral', () => {
    it('isOutOfTheExchangeAndTheBoardTellsThePairs', () => {
      const w = mountPanel(2, 'NEUTRAL');
      expect(text(w, 'exchange-role')).toBe('You are Neutral');
      expect(text(w, 'exchange-instruction')).toBe('You neither give nor receive. Waiting for the others.');
      expect(w.find('[data-test="exchange-give"]').exists()).toBe(false);
      expect(w.find('[data-test="exchange-suggest"]').exists()).toBe(false);
      expect(w.find('[data-test="exchange-pair"]').exists()).toBe(false);
    });
  });

  describe('Vice-Scum', () => {
    it('hadItsBestCardTakenAndWaitsForTheReturn', () => {
      const w = mountPanel(3, 'VICE_SCUM');
      expect(text(w, 'exchange-role')).toBe('You are the Vice-Scum');
      expect(text(w, 'exchange-instruction')).toBe('Your best card went to Ben. Waiting for them to give one back.');
      expect(w.find('[data-test="exchange-give"]').exists()).toBe(false);
    });
  });

  describe('Scum', () => {
    it('hadItsTwoBestCardsTakenAndWaitsForTheReturn', () => {
      const w = mountPanel(4, 'SCUM');
      expect(text(w, 'exchange-role')).toBe('You are the Scum');
      expect(text(w, 'exchange-instruction')).toBe('Your 2 best cards went to Ann. Waiting for them to give 2 back.');
      expect(w.find('[data-test="exchange-give"]').exists()).toBe(false);
    });

    it('whenTheReturnIsDoneItWaitsForTheOthers', () => {
      const w = mountPanel(4, 'SCUM', { pairs: [{ high: 0, low: 4, count: 2, done: true }, PAIRS[1]] });
      expect(text(w, 'exchange-instruction')).toBe('Ann gave back 2 cards. Waiting for the others.');
    });
  });

  it('aHighRankWhoHasGivenWaitsForTheOthers', () => {
    const w = mountPanel(0, 'PRESIDENT', { pairs: [{ high: 0, low: 4, count: 2, done: true }, PAIRS[1]] });
    expect(text(w, 'exchange-instruction')).toBe('You gave 2 cards back to Ed. Waiting for the others.');
    expect(w.find('[data-test="exchange-give"]').exists()).toBe(false);
    expect(w.find('[data-test="exchange-pair"]').exists()).toBe(false);
  });

  it('theRoleIsReadAsTextWithoutColour', () => {
    const w = mountPanel(0, 'PRESIDENT');
    expect(w.get('[data-test="exchange-panel"]').attributes('role')).toBe('region');
    expect(w.get('[data-test="exchange-instruction"]').attributes('role')).toBe('status');
  });
});
