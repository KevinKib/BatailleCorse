import { describe, it, expect } from 'vitest';
import { MOTION_MAX_MS, eventSpanMs, planMovements, type Movement } from './motion';
import { TEMPO } from './tempo';
import type { KoboEventData, KoboLogEntry } from './KoboEvents';
import { buildKoboCard } from '../fixtures/kobo';

const entry = (eventType: string, data: Partial<KoboEventData> | null, action = eventType): KoboLogEntry => ({
  eventType,
  data: data === null ? null : {
    seat: 1, action, slot: null, targetSeat: null, targetSlot: null, card: null,
    givenSlot: null, success: null, barrierOpened: null, ...data,
  },
});
const ME = 0;
const end = (m: Movement) => m.delayMs + m.durationMs + m.holdAfterMs;

describe('planMovements', () => {
  it('givenADrawByAnOpponent_thenACardTravelsFromTheDrawPileToTheHandFaceDown', () => {
    const [m, ...rest] = planMovements(entry('DRAW', {}), ME);
    expect(rest).toEqual([]);
    expect(m).toMatchObject({ kind: 'travel', from: { kind: 'draw' }, to: { kind: 'hand' }, card: null, flip: false, byOpponent: true });
  });

  it('givenMyDraw_thenTheCardTurnsFaceUpInFlight', () => {
    const [m] = planMovements(entry('DRAW', { seat: ME }), ME);
    expect(m).toMatchObject({ flip: true, byOpponent: false });
  });

  it('givenADrawFromAnEmptyPile_thenNothingMoves', () => {
    expect(planMovements(entry('DRAW', { success: false }), ME)).toEqual([]);
  });

  it('givenASwap_thenTheDrawnCardGoesToTheSlotWhileTheOldOneGoesToTheDiscardFaceUp', () => {
    const old = buildKoboCard('7', 'SPADE');
    const moves = planMovements(entry('SWAP', { slot: 2, card: old }), ME);
    expect(moves).toHaveLength(2);
    expect(moves[0]).toMatchObject({ from: { kind: 'hand' }, to: { kind: 'slot', seat: 1, slot: 2 }, card: null, delayMs: TEMPO.opponent.holdBeforeMs });
    expect(moves[1]).toMatchObject({ from: { kind: 'slot', seat: 1, slot: 2 }, to: { kind: 'discard' }, card: old, delayMs: TEMPO.opponent.holdBeforeMs });
  });

  it('givenADiscardOfTheDrawnCard_thenItGoesFromTheHandToTheDiscardFaceUp', () => {
    const card = buildKoboCard('4', 'CLUB');
    const [m] = planMovements(entry('DISCARD_DRAWN', { card }), ME);
    expect(m).toMatchObject({ from: { kind: 'hand' }, to: { kind: 'discard' }, card });
  });

  it('givenASuccessfulMatchOnAnotherPlayersCard_thenThatCardGoesToTheDiscardAndMyGiftToTheEmptiedSlot', () => {
    const card = buildKoboCard('9', 'HEART');
    const moves = planMovements(entry('MATCH', { seat: ME, targetSeat: 1, targetSlot: 2, givenSlot: 3, success: true, card }), ME);
    expect(moves).toHaveLength(2);
    expect(moves[0]).toMatchObject({ from: { kind: 'slot', seat: 1, slot: 2 }, to: { kind: 'discard' }, card });
    expect(moves[1]).toMatchObject({ from: { kind: 'slot', seat: ME, slot: 3 }, to: { kind: 'slot', seat: 1, slot: 2 }, card: null });
  });

  it('givenASuccessfulMatchOnMyOwnCard_thenOnlyThatCardGoesToTheDiscard', () => {
    const card = buildKoboCard('9', 'HEART');
    const moves = planMovements(entry('MATCH', { seat: 1, targetSeat: 1, targetSlot: 2, success: true, card }), ME);
    expect(moves).toHaveLength(1);
    expect(moves[0]).toMatchObject({ from: { kind: 'slot', seat: 1, slot: 2 }, to: { kind: 'discard' }, card });
  });

  it('givenAMissedMatch_thenAPenaltyCardTravelsFromTheDrawPileToANewSlotOfTheMatcher', () => {
    const [m, ...rest] = planMovements(entry('MATCH', { seat: 1, success: false }), ME);
    expect(rest).toEqual([]);
    expect(m).toMatchObject({ from: { kind: 'draw' }, to: { kind: 'newSlot', seat: 1 }, card: null });
  });

  it('givenAPeekAtAnotherPlayersCard_thenAGlanceGoesFromThatSlotToThePeeker', () => {
    const [m] = planMovements(entry('PEEK', { seat: 2, targetSeat: 1, targetSlot: 3 }), ME);
    expect(m).toMatchObject({ kind: 'glance', from: { kind: 'slot', seat: 1, slot: 3 }, to: { kind: 'seat', seat: 2 }, byOpponent: true });
  });

  it('givenAPeekAtMyOwnCard_thenNothingMoves', () => {
    expect(planMovements(entry('PEEK', { seat: 1, targetSeat: 1, targetSlot: 3 }), ME)).toEqual([]);
  });

  it.each(['BLIND_SWAP', 'KING_SWAP'])('given%s_thenTheTwoCardsCrossFaceDown', (type) => {
    const moves = planMovements(entry(type, { seat: 1, slot: 0, targetSeat: 2, targetSlot: 3 }), ME);
    expect(moves).toHaveLength(2);
    expect(moves[0]).toMatchObject({ from: { kind: 'slot', seat: 1, slot: 0 }, to: { kind: 'slot', seat: 2, slot: 3 }, card: null });
    expect(moves[1]).toMatchObject({ from: { kind: 'slot', seat: 2, slot: 3 }, to: { kind: 'slot', seat: 1, slot: 0 }, card: null });
  });

  it('givenACommandThatEndedTheRound_thenItsMovesAreFollowedByARevealOfTheTable', () => {
    const moves = planMovements(entry('ROUND_END', { seat: 1 }, 'END_TURN'), ME);
    expect(moves.map(m => m.kind)).toEqual(['reveal']);
    const withCard = planMovements(entry('ROUND_END', { seat: 1, slot: 0, card: buildKoboCard('2') }, 'SWAP'), ME);
    expect(withCard.map(m => m.kind)).toEqual(['travel', 'travel', 'reveal']);
  });

  it.each(['END_TURN', 'READY', 'KOBO', 'SKIP_POWER', 'GIVE_TEN', 'START', 'ROUND_START'])('given%s_thenNothingMoves', (type) => {
    expect(planMovements(entry(type, {}), ME)).toEqual([]);
  });

  it('givenAnEntryWithoutData_thenNothingMoves', () => {
    expect(planMovements(entry('DRAW', null), ME)).toEqual([]);
  });

  const samples = () => [
    entry('DRAW', {}), entry('SWAP', { slot: 0 }), entry('DISCARD_DRAWN', {}),
    entry('MATCH', { targetSeat: 1, targetSlot: 0, givenSlot: 1, success: true }),
    entry('MATCH', { success: false }), entry('PEEK', { targetSeat: 2, targetSlot: 0 }),
    entry('BLIND_SWAP', { slot: 0, targetSeat: 2, targetSlot: 0 }), entry('ROUND_END', {}, 'END_TURN'),
  ];

  it('everyMovementEndsUnderTheLimit', () => {
    for (const s of samples()) for (const m of planMovements(s, ME)) expect(end(m)).toBeLessThanOrEqual(MOTION_MAX_MS);
  });

  it('givenAnOpponentsMove_thenItFliesForAboutASecondAfterAShortHold', () => {
    for (const s of samples().filter(e => e.eventType !== 'ROUND_END')) {
      for (const m of planMovements(s, ME).filter(x => x.byOpponent && x.kind === 'travel')) {
        expect(m.durationMs).toBeGreaterThanOrEqual(900);
        expect(m.durationMs).toBeLessThanOrEqual(1200);
        expect(m.delayMs).toBeGreaterThanOrEqual(TEMPO.opponent.holdBeforeMs);
        expect(m.holdAfterMs).toBe(TEMPO.opponent.holdAfterMs);
      }
    }
  });

  it('givenMyOwnMove_thenItFliesAtAReadablePaceAfterAShortHold', () => {
    const moves = [...planMovements(entry('DRAW', { seat: ME }), ME), ...planMovements(entry('SWAP', { seat: ME, slot: 1 }), ME)];
    expect(moves.length).toBeGreaterThan(0);
    for (const m of moves) {
      expect(m.durationMs).toBeGreaterThanOrEqual(600);
      expect(m.durationMs).toBeLessThanOrEqual(800);
      expect(m.delayMs).toBeGreaterThanOrEqual(TEMPO.own.holdBeforeMs);
      expect(m.holdAfterMs).toBeGreaterThan(0);
    }
  });

  it('givenAGlanceAtAnOpponent_thenItIsSlowToo', () => {
    const [m] = planMovements(entry('PEEK', { seat: 2, targetSeat: 1, targetSlot: 3 }), ME);
    expect(m.durationMs).toBeGreaterThanOrEqual(900);
  });

  it('givenTheGiftOfAMatch_thenItLeavesAfterTheMatchedCardHasStartedItsFlight', () => {
    const moves = planMovements(entry('MATCH', { seat: ME, targetSeat: 1, targetSlot: 2, givenSlot: 3, success: true, card: buildKoboCard('9', 'HEART') }), ME);
    expect(moves[1].delayMs).toBeGreaterThan(moves[0].delayMs);
  });

  it('marksTheSlotsEveryMoveTouchesButNotThePiles', () => {
    const [draw] = planMovements(entry('DRAW', {}), ME);
    expect(draw.marks).toEqual([]);
    const swap = planMovements(entry('SWAP', { slot: 2, card: buildKoboCard('7', 'SPADE') }), ME);
    expect(swap[0].marks).toEqual([{ kind: 'slot', seat: 1, slot: 2 }]);
    const [blind] = planMovements(entry('BLIND_SWAP', { seat: 1, slot: 0, targetSeat: 2, targetSlot: 3 }), ME);
    expect(blind.marks).toEqual([{ kind: 'slot', seat: 1, slot: 0 }, { kind: 'slot', seat: 2, slot: 3 }]);
    const [penalty] = planMovements(entry('MATCH', { seat: 1, success: false }), ME);
    expect(penalty.marks).toEqual([{ kind: 'newSlot', seat: 1 }]);
    const [peek] = planMovements(entry('PEEK', { seat: 2, targetSeat: 1, targetSlot: 3 }), ME);
    expect(peek.marks).toEqual([{ kind: 'slot', seat: 1, slot: 3 }]);
    const gift = planMovements(entry('MATCH', { seat: ME, targetSeat: 1, targetSlot: 2, givenSlot: 3, success: true }), ME);
    expect(gift[1].marks).toEqual([{ kind: 'slot', seat: ME, slot: 3 }, { kind: 'slot', seat: 1, slot: 2 }]);
  });

  it('eventSpanMs_isTheEndOfTheLastFlightWithoutTheHoldAfterLanding', () => {
    const moves = planMovements(entry('MATCH', { seat: ME, targetSeat: 1, targetSlot: 2, givenSlot: 3, success: true }), ME);
    expect(eventSpanMs(moves)).toBe(Math.max(...moves.map(m => m.delayMs + m.durationMs)));
    expect(eventSpanMs([])).toBe(0);
  });
});
