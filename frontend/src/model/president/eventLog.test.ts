import { describe, expect, it } from 'vitest';
import { appendEntry, toLogEntry, MAX_LOG } from './eventLog';
import type { PresidentEventData } from './PresidentEvents';
import { buildPresidentCard as card, buildPresidentState, buildPresidentTrick } from '../fixtures/president';

const data = (over: Partial<PresidentEventData> = {}): PresidentEventData => ({
  seat: 1, action: 'PLAY', cards: [card('9', 'HEART')], count: null, outPlace: null, outForbidden: null,
  trickWinner: null, trickEndReason: null, barrierOpened: null, ...over,
});

// Before: Alice (0) led a pair of 7s, Bob (1) is to play.
const before = buildPresidentState({
  round: 2,
  currentSeat: 1,
  trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [card('7', 'HEART'), card('7', 'CLUB')] }] }),
});

describe('toLogEntry', () => {
  it('givenAPlay_thenTheEntryNamesTheSeatAndItsCards_inTurn', () => {
    const entry = toLogEntry(7, 'PLAY', data({ cards: [card('9', 'HEART'), card('9', 'CLUB')] }), before);
    expect(entry).toMatchObject({
      id: 7, eventType: 'PLAY', action: 'PLAY', seat: 1, round: 2, outOfTurn: false, outForbidden: false,
      outPlace: null, closure: null,
    });
    expect(entry?.cards.map(c => c.name)).toEqual(['HEART_9', 'CLUB_9']);
  });

  it('givenAPass_thenAnEntryWithoutCards', () => {
    const entry = toLogEntry(1, 'PASS', data({ action: 'PASS', cards: null }), before);
    expect(entry).toMatchObject({ action: 'PASS', seat: 1, cards: [], closure: null });
  });

  it.each([
    ['the start', 'START'],
    ['a new round', 'ROUND_START'],
  ])('givenAnEventThatIsNotAMove (%s)_thenNoEntry', (_label, type) => {
    expect(toLogEntry(1, type, data(), before)).toBeNull();
  });

  it.each([['READY'], ['GIVE_BACK']])('givenACommandThatIsNotAPlayOrAPass (%s)_thenNoEntry', (action) => {
    expect(toLogEntry(1, action === 'READY' ? 'READY' : 'EXCHANGE', data({ action, cards: null }), before)).toBeNull();
  });

  it('givenNoData_thenNoEntry', () => {
    expect(toLogEntry(1, 'PLAY', null, before)).toBeNull();
  });

  it.each([
    ['a COMPLETION event', 'COMPLETION', 1, true],
    ['a play by a seat that was not to play', 'PLAY', 2, true],
    ['a play by the seat to play', 'PLAY', 1, false],
  ])('givenOutOfTurnDetection (%s)', (_label, type, seat, expected) => {
    expect(toLogEntry(1, type, data({ seat }), before)?.outOfTurn).toBe(expected);
  });

  it('givenAForbiddenFinish_thenTheEntrySaysSo', () => {
    const entry = toLogEntry(1, 'PLAY', data({ cards: [card('2', 'HEART')], outForbidden: true }), before);
    expect(entry?.outForbidden).toBe(true);
  });

  it('givenAPlayThatClosesTheTrick_thenTheClosureHasTheWinnerTheReasonTheClosingCardsAndTheSizeOfTheClosedTrick', () => {
    const entry = toLogEntry(1, 'PLAY', data({ cards: [card('2', 'SPADE'), card('2', 'CLUB')], trickWinner: 1, trickEndReason: 'CUT' }), before);
    expect(entry?.closure).toEqual({
      winner: 1, reason: 'CUT', closer: 1, cards: [card('2', 'SPADE'), card('2', 'CLUB')], size: 4,
      trickCards: [card('2', 'SPADE'), card('2', 'CLUB'), card('7', 'HEART'), card('7', 'CLUB')],
    });
  });

  it('givenAPassThatClosesTheTrick_thenTheClosingCardsAreTheTopOfThePreviousState', () => {
    const entry = toLogEntry(1, 'PASS', data({ action: 'PASS', cards: null, trickWinner: 0, trickEndReason: 'ALL_PASSED' }), before);
    expect(entry?.closure).toEqual({
      winner: 0, reason: 'ALL_PASSED', closer: 0, cards: [card('7', 'HEART'), card('7', 'CLUB')], size: 2,
      trickCards: [card('7', 'HEART'), card('7', 'CLUB')],
    });
  });

  it('givenSeveralPlaysInTheClosedTrick_thenTrickCardsListTheClosingPlayFirstThenTheEarlierPlaysNewestFirst', () => {
    const longTrick = buildPresidentState({
      round: 2, currentSeat: 2,
      trick: buildPresidentTrick({
        leader: 0,
        plays: [{ seat: 0, cards: [card('5', 'HEART')] }, { seat: 1, cards: [card('8', 'CLUB')] }],
      }),
    });
    const entry = toLogEntry(1, 'PLAY', data({ seat: 2, cards: [card('2', 'SPADE')], trickWinner: 2, trickEndReason: 'CUT' }), longTrick);
    expect(entry?.closure?.trickCards.map(c => c.name)).toEqual(['SPADE_2', 'CLUB_8', 'HEART_5']);
  });

  it('givenNoPreviousState_thenTheClosureCountsTheClosingPlayOnly', () => {
    const entry = toLogEntry(1, 'PLAY', data({ trickWinner: 1, trickEndReason: 'QUAD' }), null);
    expect(entry?.closure).toMatchObject({ reason: 'QUAD', size: 1, closer: 1 });
    expect(entry?.round).toBeNull();
  });

  it('givenAnUnknownReason_thenItIsKeptAsGiven', () => {
    const entry = toLogEntry(1, 'PLAY', data({ trickWinner: 1, trickEndReason: 'SOMETHING_NEW' }), before);
    expect(entry?.closure?.reason).toBe('SOMETHING_NEW');
  });
});

describe('appendEntry', () => {
  it('givenEntries_thenTheNewestComesFirstAndTheLogIsCapped', () => {
    let log = [] as ReturnType<typeof appendEntry>;
    for (let i = 1; i <= MAX_LOG + 3; i++) log = appendEntry(log, toLogEntry(i, 'PASS', data({ action: 'PASS', cards: null }), before));
    expect(log).toHaveLength(MAX_LOG);
    expect(log[0].id).toBe(MAX_LOG + 3);
  });

  it('givenNoEntry_thenTheSameLog', () => {
    const log = [toLogEntry(1, 'PASS', data({ action: 'PASS', cards: null }), before)!];
    expect(appendEntry(log, null)).toBe(log);
  });
});
