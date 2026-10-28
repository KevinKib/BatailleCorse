import { describe, expect, it } from 'vitest';
import {
  blackjackBettingState,
  blackjackDealerBlackjackState,
  blackjackPlayingState,
  blackjackSettledState,
  blackjackSoloEndState,
  blackjackState,
  blackjackTournamentEndState,
  card,
} from './fixtures';

describe('blackjack fixtures', () => {
  it('builds cards in the wire format of the other card games', () => {
    expect(card('ACE', 'HEART')).toEqual({ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' });
  });

  it('defaults to a 4-seat betting table with the default options', () => {
    const state = blackjackState();
    expect(state.phase).toBe('BETTING');
    expect(state.players).toHaveLength(4);
    expect(state.options).toEqual({ hands: '10', maxBet: '50', startingStack: 100, minBet: 1 });
  });

  it('seals the bets of the others during betting', () => {
    const others = blackjackBettingState.players.slice(1);
    expect(others.some((p) => p.betPlaced)).toBe(true);
    expect(others.every((p) => p.bet === null)).toBe(true);
    expect(blackjackBettingState.betLimits).toEqual({ min: 1, max: 50 });
    expect(blackjackBettingState.lastBet).toBe(10);
  });

  it('shows my split hands and the actions on offer while playing', () => {
    const me = blackjackPlayingState.players[0];
    expect(me.hands).toHaveLength(2);
    expect(me.hands[1].status).toBe('WAITING');
    expect(blackjackPlayingState.availableActions).toEqual(['HIT', 'STAND', 'DOUBLE']);
    expect(blackjackPlayingState.dealer.holeHidden).toBe(true);
    expect(blackjackPlayingState.dealer.cards).toHaveLength(1);
  });

  it('keeps a dealer blackjack and a best hand in the last hand', () => {
    expect(blackjackDealerBlackjackState.lastHand?.dealer.blackjack).toBe(true);
    expect(blackjackSettledState.lastHand?.bestSeats).toEqual([1]);
  });

  it('ends a tournament with shared places and a solo game with a net', () => {
    const end = blackjackTournamentEndState.outcome;
    expect(end.status === 'FINISHED' && end.places['0'] === end.places['1']).toBe(true);
    const solo = blackjackSoloEndState.outcome;
    expect(solo.status === 'FINISHED' && solo.net).toBe(23);
  });
});
