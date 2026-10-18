import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentExchangeRecap from './PresidentExchangeRecap.vue';
import type { ExchangeRecapLine } from '../../model/president/exchangeRecap';
import { buildPresidentCard as card } from '../../model/fixtures/president';

const NAMES = ['Ann', 'Ben', 'Cy', 'Di', 'Ed'];
const lines: ExchangeRecapLine[] = [
  { high: 0, low: 4, count: 2, mine: true, best: [card('ACE', 'CLUB'), card('2', 'HEART')], back: [card('3', 'CLUB'), card('4', 'SPADE')] },
  { high: 1, low: 3, count: 1, mine: false, best: null, back: null },
];
const mountRecap = (props: Record<string, unknown> = {}) =>
  mount(PresidentExchangeRecap, { props: { lines, nameOf: (s: number) => NAMES[s], ...props } });

describe('PresidentExchangeRecap', () => {
  it('givenMyPair_thenItNamesWhoGaveWhichCardsToWhomAndWhatCameBack', () => {
    const mine = mountRecap().findAll('[data-test="recap-line"]')[0];
    expect(mine.get('[data-test="recap-given"]').text()).toBe('Ed gave Ann:');
    expect(mine.get('[data-test="recap-returned"]').text()).toBe('Ann gave back:');
    const given = mine.get('[data-test="recap-given-cards"]').findAll('img').map(i => i.attributes('alt'));
    expect(given).toEqual(['Ace of clubs', '2 of hearts']);
    const back = mine.get('[data-test="recap-returned-cards"]').findAll('img').map(i => i.attributes('alt'));
    expect(back).toEqual(['3 of clubs', '4 of spades']);
    expect(mine.classes()).toContain('mine');
  });

  it('givenAnotherPair_thenOnlyTheCountsAreSaid', () => {
    const other = mountRecap().findAll('[data-test="recap-line"]')[1];
    expect(other.text()).toBe('Di gave their best card to Ben, who gave one back.');
    expect(other.findAll('img')).toHaveLength(0);
  });

  it('givenASeatOutsideEveryPair_thenTheSummaryIsMarkedToStayOutOfSightOnAPhone', () => {
    const w = mountRecap({ lines: [lines[1]] });
    expect(w.get('[data-test="exchange-recap"]').classes()).toContain('no-mine');
    expect(mountRecap().get('[data-test="exchange-recap"]').classes()).not.toContain('no-mine');
  });

  it('givenTheCloseButton_thenItAsksToDismissTheSummary', async () => {
    const w = mountRecap();
    const close = w.get('[data-test="recap-close"]');
    expect(close.attributes('aria-label')).toBe('Close the exchange summary');
    await close.trigger('click');
    expect(w.emitted('close')).toHaveLength(1);
    expect(w.get('[data-test="exchange-recap"]').text()).toContain('The exchange');
  });
});
