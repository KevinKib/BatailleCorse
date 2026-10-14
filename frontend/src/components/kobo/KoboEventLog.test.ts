import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KoboEventLog from './KoboEventLog.vue';
import type { KoboEventData, KoboLogEntry } from '../../model/kobo/KoboEvents';
import { buildKoboCard } from '../../model/fixtures/kobo';

const entry = (eventType: string, data: Partial<KoboEventData>): KoboLogEntry => ({
  eventType,
  data: {
    seat: 1, action: eventType, slot: null, targetSlot: null, targetSeat: null, card: null,
    givenSlot: null, success: null, barrierOpened: null, ...data,
  },
});
const names: Record<number, string> = { 0: 'Alice', 1: 'Bob', 2: 'Carol' };

function mountLog(events: KoboLogEntry[], mySeat = 0) {
  return mount(KoboEventLog, { props: { events, mySeat, nameOf: (seat: number) => names[seat] } });
}
const line = (w: ReturnType<typeof mountLog>) => w.get('[data-test="event-log-line"]');

describe('KoboEventLog', () => {
  it('givenNoEvent_thenTheLiveRegionIsThereAndEmpty', () => {
    const wrapper = mountLog([]);
    expect(line(wrapper).attributes('aria-live')).toBe('polite');
    expect(line(wrapper).text()).toBe('');
  });

  it('givenAnOpponentsMatchOnMyCard_thenNamesWhoMatchedWhichCardOfWhomAndTheGift', () => {
    const wrapper = mountLog([entry('MATCH', {
      seat: 1, targetSeat: 0, targetSlot: 1, givenSlot: 3, success: true, card: buildKoboCard('7', 'SPADE'),
    })]);
    expect(line(wrapper).text()).toBe('Bob matched your card 2 (7 of spade), gave card 4');
  });

  it('givenMyOwnMatch_thenSaysYou', () => {
    const wrapper = mountLog([entry('MATCH', { seat: 0, targetSeat: 0, targetSlot: 0, success: true, card: buildKoboCard('KING', 'HEART') })]);
    expect(line(wrapper).text()).toBe('You matched card 1 (king of heart)');
  });

  it('givenAMatchOnAnotherPlayersCard_thenUsesTheirName', () => {
    const wrapper = mountLog([entry('MATCH', { seat: 0, targetSeat: 2, targetSlot: 2, givenSlot: 0, success: true, card: buildKoboCard('9', 'CLUB') })]);
    expect(line(wrapper).text()).toBe("You matched Carol's card 3 (9 of club), gave card 1");
  });

  it('givenAMissedMatch_thenSaysThePenaltyAndFlagsTheLineAsBad', () => {
    const wrapper = mountLog([entry('MATCH', { seat: 1, success: false })]);
    expect(line(wrapper).text()).toBe('Bob missed a match and took a penalty card');
    expect(wrapper.get('[data-test="event-log"]').attributes('data-outcome')).toBe('bad');
  });

  it('givenASuccessfulMatch_thenFlagsTheLineAsGood', () => {
    const wrapper = mountLog([entry('MATCH', { seat: 1, targetSeat: 1, targetSlot: 0, success: true })]);
    expect(wrapper.get('[data-test="event-log"]').attributes('data-outcome')).toBe('good');
  });

  it('givenAPlainEvent_thenNoOutcomeFlag', () => {
    const wrapper = mountLog([entry('DISCARD_DRAWN', { card: buildKoboCard('7', 'SPADE') })]);
    expect(line(wrapper).text()).toBe('Bob discarded 7 of spade');
    expect(wrapper.get('[data-test="event-log"]').attributes('data-outcome')).toBeUndefined();
  });

  it('givenTheNewestEventsAreEndTurnAndDraw_thenTheLineKeepsTheLastDiscard', () => {
    const wrapper = mountLog([
      entry('END_TURN', { seat: 2 }), entry('DRAW', { seat: 2 }),
      entry('DISCARD_DRAWN', { seat: 1, card: buildKoboCard('3', 'HEART') }),
    ]);
    expect(line(wrapper).text()).toBe('Bob discarded 3 of heart');
  });

  it.each([
    [entry('PEEK', { seat: 1, targetSeat: 0, targetSlot: 3 }), 'Bob looked at your card 4'],
    [entry('BLIND_SWAP', { seat: 0, slot: 0, targetSeat: 1, targetSlot: 2 }), "You swapped card 1 with Bob's card 3"],
    [entry('SWAP', { seat: 1, slot: 1, card: buildKoboCard('2', 'CLUB') }), 'Bob swapped card 2 and discarded 2 of club'],
    [entry('KOBO', { seat: 1 }), 'Bob announced Kobo!'],
    [entry('GIVE_TEN', { seat: 1, targetSeat: 0 }), 'Bob gave 10 points to you'],
  ])('describes%j', (e, text) => {
    expect(line(mountLog([e])).text()).toBe(text);
  });

  it('exposesTheFullTextAsTitleForATruncatedLine', () => {
    const wrapper = mountLog([entry('KOBO', { seat: 1 })]);
    expect(line(wrapper).attributes('title')).toBe('Bob announced Kobo!');
  });
});
