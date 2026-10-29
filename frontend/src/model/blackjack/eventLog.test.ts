import { describe, it, expect } from 'vitest';
import { eventLine } from './eventLog';
import { card } from './fixtures';
import type { BlackjackEventData } from './BlackjackState';
import type { BlackjackTableEvent } from './motion';

const empty: BlackjackEventData = {
  seat: null, handIndex: null, card: null, value: null, cards: null, holeCard: null, handNumber: null,
  button: null, bets: null, nets: null, stacks: null, bestSeats: null,
};
const ev = (eventType: string, data: Partial<BlackjackEventData> = {}): BlackjackTableEvent =>
  ({ id: 1, eventType, data: { ...empty, ...data } });

describe('eventLine', () => {
  it('names a bet by its seat, never the amount (bets are sealed)', () => {
    expect(eventLine(ev('BET', { seat: 1, bets: { 1: 20 } }), 0))
      .toEqual({ key: 'blackjackUi.events.bet', params: { seat: 1 } });
  });

  it('reads a hit with the new total', () => {
    expect(eventLine(ev('HIT', { seat: 1, card: card('SEVEN'), value: 18 }), 0))
      .toEqual({ key: 'blackjackUi.events.hit', params: { seat: 1, total: 18 } });
  });

  it('reads stand, double, split and bust by seat', () => {
    expect(eventLine(ev('STAND', { seat: 2 }), 0)).toEqual({ key: 'blackjackUi.events.stand', params: { seat: 2 } });
    expect(eventLine(ev('DOUBLE', { seat: 2, value: 19 }), 0))
      .toEqual({ key: 'blackjackUi.events.double', params: { seat: 2, total: 19 } });
    expect(eventLine(ev('SPLIT', { seat: 2 }), 0)).toEqual({ key: 'blackjackUi.events.split', params: { seat: 2 } });
    expect(eventLine(ev('BUST', { seat: 3, value: 24 }), 0))
      .toEqual({ key: 'blackjackUi.events.bust', params: { seat: 3, total: 24 } });
  });

  // "You" takes its own verb form ("You bust", "Vous brûlez"), never the third person of "{player} busts".
  it('reads my own actions with their …You variant', () => {
    expect(eventLine(ev('BET', { seat: 0 }), 0)).toEqual({ key: 'blackjackUi.events.betYou', params: { seat: 0 } });
    expect(eventLine(ev('HIT', { seat: 0, value: 18 }), 0))
      .toEqual({ key: 'blackjackUi.events.hitYou', params: { seat: 0, total: 18 } });
    expect(eventLine(ev('STAND', { seat: 0 }), 0)).toEqual({ key: 'blackjackUi.events.standYou', params: { seat: 0 } });
    expect(eventLine(ev('DOUBLE', { seat: 0, value: 19 }), 0))
      .toEqual({ key: 'blackjackUi.events.doubleYou', params: { seat: 0, total: 19 } });
    expect(eventLine(ev('SPLIT', { seat: 0 }), 0)).toEqual({ key: 'blackjackUi.events.splitYou', params: { seat: 0 } });
    expect(eventLine(ev('BUST', { seat: 0, value: 24 }), 0))
      .toEqual({ key: 'blackjackUi.events.bustYou', params: { seat: 0, total: 24 } });
    expect(eventLine(ev('ELIMINATED', { seat: 0 }), 0))
      .toEqual({ key: 'blackjackUi.events.eliminatedYou', params: { seat: 0 } });
  });

  it('reads the deal with the hand number and the dealer blackjack', () => {
    expect(eventLine(ev('DEAL', { handNumber: 3 }), 0)).toEqual({ key: 'blackjackUi.events.deal', params: { hand: 3 } });
    expect(eventLine(ev('DEALER_BLACKJACK'), 0)).toEqual({ key: 'blackjackUi.events.dealerBlackjack', params: {} });
  });

  it('tells a dealer bust from a dealer stand', () => {
    expect(eventLine(ev('DEALER_PLAY', { value: 24 }), 0))
      .toEqual({ key: 'blackjackUi.events.dealerBusts', params: { total: 24 } });
    expect(eventLine(ev('DEALER_PLAY', { value: 18 }), 0))
      .toEqual({ key: 'blackjackUi.events.dealerStands', params: { total: 18 } });
  });

  describe('SETTLE reads my own result', () => {
    it('a win', () => {
      expect(eventLine(ev('SETTLE', { nets: { 0: 15, 1: -10 } }), 0))
        .toEqual({ key: 'blackjackUi.events.settleWin', params: { amount: 15 } });
    });
    it('a loss', () => {
      expect(eventLine(ev('SETTLE', { nets: { 0: 15, 1: -10 } }), 1))
        .toEqual({ key: 'blackjackUi.events.settleLoss', params: { amount: 10 } });
    });
    it('a push', () => {
      expect(eventLine(ev('SETTLE', { nets: { 0: 0 } }), 0))
        .toEqual({ key: 'blackjackUi.events.settlePush', params: {} });
    });
    it('nothing when I am not in the settlement', () => {
      expect(eventLine(ev('SETTLE', { nets: { 1: 5 } }), 0)).toBeNull();
      expect(eventLine(ev('SETTLE'), 0)).toBeNull();
    });
  });

  it('reads an elimination and the end of the game', () => {
    expect(eventLine(ev('ELIMINATED', { seat: 3 }), 0)).toEqual({ key: 'blackjackUi.events.eliminated', params: { seat: 3 } });
    expect(eventLine(ev('GAME_OVER'), 0)).toEqual({ key: 'blackjackUi.events.gameOver', params: {} });
  });

  it('says nothing of an event it does not know or of a seatless action', () => {
    expect(eventLine(ev('START'), 0)).toBeNull();
    expect(eventLine(ev('SOMETHING_ELSE'), 0)).toBeNull();
    expect(eventLine(ev('HIT'), 0)).toBeNull();
  });
});
