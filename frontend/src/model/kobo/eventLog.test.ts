import { describe, it, expect } from 'vitest';
import { describeEvent, eventHighlights, headlineEvent } from './eventLog';
import type { KoboEventData, KoboLogEntry } from './KoboEvents';
import { buildKoboCard } from '../fixtures/kobo';

const entry = (eventType: string, data: Partial<KoboEventData>): KoboLogEntry => ({
  eventType,
  data: {
    seat: 1, action: eventType, slot: null, targetSeat: null, targetSlot: null, card: null,
    givenSlot: null, success: null, barrierOpened: null, ...data,
  },
});

describe('describeEvent', () => {
  it.each<[string, Partial<KoboEventData>, string]>([
    ['DRAW', {}, 'draw'],
    ['DRAW', { success: false }, 'drawEmpty'],
    ['SWAP', { slot: 2, card: buildKoboCard('7', 'SPADE') }, 'swap'],
    ['DISCARD_DRAWN', { card: buildKoboCard('7', 'SPADE') }, 'discard'],
    ['PEEK', { targetSeat: 0, targetSlot: 1 }, 'peek'],
    ['BLIND_SWAP', { slot: 0, targetSeat: 0, targetSlot: 3 }, 'blindSwap'],
    ['KING_SWAP', { slot: 0, targetSeat: 0, targetSlot: 3 }, 'kingSwap'],
    ['SKIP_POWER', {}, 'skipPower'],
    ['END_TURN', {}, 'endTurn'],
    ['KOBO', {}, 'kobo'],
    ['READY', {}, 'ready'],
    ['GIVE_TEN', { targetSeat: 0 }, 'giveTen'],
  ])('given%sWith%j_thenKey%s', (type, data, key) => {
    expect(describeEvent(entry(type, data))?.key).toBe(key);
  });

  it('givenAMatchOnTheActorsOwnCard_thenMatchOwn', () => {
    const line = describeEvent(entry('MATCH', { seat: 1, targetSeat: 1, targetSlot: 2, success: true, card: buildKoboCard('9') }));
    expect(line).toMatchObject({ key: 'matchOwn', actor: 1, owner: 1, targetSlot: 2 });
  });

  it('givenAMatchOnAnotherPlayersCardWithAGift_thenMatchOtherNamesTheGivenSlot', () => {
    const line = describeEvent(entry('MATCH', { seat: 1, targetSeat: 0, targetSlot: 2, givenSlot: 3, success: true, card: buildKoboCard('9') }));
    expect(line).toMatchObject({ key: 'matchOther', owner: 0, targetSlot: 2, givenSlot: 3 });
  });

  it('givenAMatchOnAnotherPlayersCardWithNothingToGive_thenMatchOtherNoGive', () => {
    const line = describeEvent(entry('MATCH', { seat: 1, targetSeat: 0, targetSlot: 2, success: true }));
    expect(line?.key).toBe('matchOtherNoGive');
  });

  it('givenAFailedMatch_thenMatchMissed', () => {
    expect(describeEvent(entry('MATCH', { success: false }))?.key).toBe('matchMissed');
  });

  it('givenACommandThatEndedTheRound_thenItIsDescribedByTheCommand', () => {
    expect(describeEvent(entry('ROUND_END', { action: 'END_TURN' }))?.key).toBe('endTurn');
  });

  it('givenNoDataOrAnUnknownAction_thenNothingToSay', () => {
    expect(describeEvent({ eventType: 'DRAW', data: null })).toBeNull();
    expect(describeEvent(entry('SOMETHING', {}))).toBeNull();
  });
});

describe('eventHighlights', () => {
  it('namesTheSwappedSlot', () => {
    expect(eventHighlights(entry('SWAP', { seat: 1, slot: 2 }))).toEqual(['1:2']);
  });
  it('namesThePeekedSlot', () => {
    expect(eventHighlights(entry('PEEK', { seat: 1, targetSeat: 0, targetSlot: 3 }))).toEqual(['0:3']);
  });
  it('namesBothSlotsOfASwap', () => {
    expect(eventHighlights(entry('BLIND_SWAP', { seat: 1, slot: 0, targetSeat: 0, targetSlot: 3 }))).toEqual(['1:0', '0:3']);
    expect(eventHighlights(entry('KING_SWAP', { seat: 1, slot: 1, targetSeat: 2, targetSlot: 0 }))).toEqual(['1:1', '2:0']);
  });
  it('namesTheMatchedSlotAndTheGivenOne', () => {
    expect(eventHighlights(entry('MATCH', { seat: 1, targetSeat: 0, targetSlot: 2, givenSlot: 3, success: true }))).toEqual(['0:2', '1:3']);
    expect(eventHighlights(entry('MATCH', { seat: 1, targetSeat: 1, targetSlot: 0, success: true }))).toEqual(['1:0']);
  });
  it('namesNothingForAMissedMatchOrAnEventWithoutSlots', () => {
    expect(eventHighlights(entry('MATCH', { success: false }))).toEqual([]);
    expect(eventHighlights(entry('END_TURN', {}))).toEqual([]);
    expect(eventHighlights(null)).toEqual([]);
  });
});

describe('headlineEvent', () => {
  it('givenNoEvent_thenNull', () => {
    expect(headlineEvent([])).toBeNull();
  });

  it('skipsTheEventsTheTableAlreadyShows_drawReadyEndTurn', () => {
    const match = entry('MATCH', { success: false });
    expect(headlineEvent([entry('END_TURN', {}), entry('DRAW', {}), match])).toBe(match);
    expect(headlineEvent([entry('READY', {}), match])).toBe(match);
  });

  it('givenOnlyThoseEvents_thenNull', () => {
    expect(headlineEvent([entry('END_TURN', {}), entry('DRAW', {})])).toBeNull();
  });

  it('takesTheNewestOtherwise', () => {
    const newest = entry('DISCARD_DRAWN', { card: buildKoboCard('7') });
    expect(headlineEvent([newest, entry('MATCH', { success: false })])).toBe(newest);
  });
});
