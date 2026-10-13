import { describe, it, expect } from 'vitest';
import { visibleFaces, opponentColumns, revealToken } from './faces';
import { slotKey } from './interaction';
import { buildKoboCard, buildKoboPlayer, buildKoboSlot, buildKoboState } from '../fixtures/kobo';

const KING = buildKoboCard('KING', 'HEART');
const SEVEN = buildKoboCard('7', 'CLUB');

describe('visibleFaces', () => {
  it('givenAnOrdinaryTurn_thenNoCardIsFaceUp', () => {
    expect(visibleFaces(buildKoboState(), 0)).toEqual({});
  });

  it('givenMemorisingAndNotReady_thenOnlyMyStartingCardsAreFaceUp', () => {
    const game = buildKoboState({
      phase: 'MEMORISING', readySeats: [1],
      startingCards: [{ slot: 2, card: KING }, { slot: 3, card: SEVEN }],
    });
    expect(visibleFaces(game, 0)).toEqual({ [slotKey(0, 2)]: KING, [slotKey(0, 3)]: SEVEN });
  });

  it('givenIAmReady_thenTheStartingCardsTurnFaceDownEvenIfTheStateStillCarriesThem', () => {
    const game = buildKoboState({
      phase: 'MEMORISING', readySeats: [0],
      startingCards: [{ slot: 2, card: KING }, { slot: 3, card: SEVEN }],
    });
    expect(visibleFaces(game, 0)).toEqual({});
  });

  it('givenStartingCardsOutsideMemorising_thenTheyAreNotShown', () => {
    const game = buildKoboState({ phase: 'DRAW', startingCards: [{ slot: 2, card: KING }] });
    expect(visibleFaces(game, 0)).toEqual({});
  });

  it('givenAPeek_thenOnlyThatSlotIsFaceUp', () => {
    const players = [buildKoboPlayer(0), buildKoboPlayer(1, { slots: [0, 1, 2, 3].map(i => buildKoboSlot(i, { revision: i === 1 ? 4 : 0 })) })];
    const game = buildKoboState({ players, reveal: { seat: 1, slot: 1, revision: 4, card: KING } });
    expect(visibleFaces(game, 0)).toEqual({ [slotKey(1, 1)]: KING });
  });

  it('givenAPeekWhoseSlotChangedSince_thenItIsNotShown', () => {
    const players = [buildKoboPlayer(0), buildKoboPlayer(1, { slots: [0, 1, 2, 3].map(i => buildKoboSlot(i, { revision: 5 })) })];
    const game = buildKoboState({ players, reveal: { seat: 1, slot: 1, revision: 4, card: KING } });
    expect(visibleFaces(game, 0)).toEqual({});
  });

  it('givenARevealedRound_thenEverySlotThatCarriesACardIsFaceUp', () => {
    const players = [
      buildKoboPlayer(0, { slots: [buildKoboSlot(0, { card: KING }), buildKoboSlot(1, { card: SEVEN })], handSize: 2 }),
      buildKoboPlayer(1, { slots: [buildKoboSlot(0, { card: SEVEN }), buildKoboSlot(1, { occupied: false })], handSize: 1 }),
    ];
    const game = buildKoboState({ phase: 'ROUND_OVER', revealed: true, players });
    expect(visibleFaces(game, 0)).toEqual({
      [slotKey(0, 0)]: KING, [slotKey(0, 1)]: SEVEN, [slotKey(1, 0)]: SEVEN,
    });
  });
});

describe('hiding the peeked card', () => {
  const game = buildKoboState({
    phase: 'TURN_END',
    players: [buildKoboPlayer(0), buildKoboPlayer(1, { slots: [buildKoboSlot(0, { revision: 3 }), buildKoboSlot(1)] })],
    reveal: { seat: 1, slot: 0, revision: 3, card: KING },
  });

  it('givenAPeek_thenItsTokenNamesTheSeatSlotAndRevision', () => {
    expect(revealToken(game)).toBe('1:0:3');
    expect(revealToken(buildKoboState())).toBeNull();
  });

  it('givenThePeekIsHidden_thenTheCardGoesFaceDown', () => {
    expect(visibleFaces(game, 0, '1:0:3')).toEqual({});
  });

  it('givenAnotherPeekWasHidden_thenThisOneStaysFaceUp', () => {
    expect(visibleFaces(game, 0, '1:1:3')).toEqual({ [slotKey(1, 0)]: KING });
  });

  it('givenTheHiddenPeekOnlyAffectsThePeek_thenARevealedRoundStillShowsEverything', () => {
    const over = buildKoboState({
      phase: 'ROUND_OVER', revealed: true, reveal: game.reveal,
      players: [buildKoboPlayer(0), buildKoboPlayer(1, { slots: [buildKoboSlot(0, { revision: 3, card: KING })] })],
    });
    expect(visibleFaces(over, 0, '1:0:3')).toEqual({ [slotKey(1, 0)]: KING });
  });
});

describe('opponentColumns', () => {
  it.each([[1, 1], [2, 2], [3, 3], [4, 2], [5, 3]])('given%iOpponents_thenTheCompactGridHas%iColumns', (count, columns) => {
    expect(opponentColumns(count)).toBe(columns);
  });
  it('givenNoOpponent_thenOneColumn', () => {
    expect(opponentColumns(0)).toBe(1);
  });
});
