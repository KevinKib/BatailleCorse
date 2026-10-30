import { describe, expect, it } from 'vitest';
import {
  buildNinetyNineBust,
  buildNinetyNineFourSeatRound,
  buildNinetyNineFreePlayRound,
  buildNinetyNineQuickEnd,
  buildNinetyNineTwelveSeatRound,
} from './ninetynine';

describe('99 fixtures', () => {
  it('builds a 4-seat round where only the viewer sees a hand', () => {
    const state = buildNinetyNineFourSeatRound();
    expect(state.gameType).toBe('ninety-nine');
    expect(state.players).toHaveLength(4);
    expect(state.hand).toHaveLength(3);
    expect(state.playable).toHaveLength(3);
    expect(state.players.every((p) => p.handSize === 3)).toBe(true);
    expect(state.phase).toBe('PLAYING');
  });

  it('builds a 12-seat round at 94 with a reversed direction', () => {
    const state = buildNinetyNineTwelveSeatRound();
    expect(state.players).toHaveLength(12);
    expect(state.total).toBe(94);
    expect(state.direction).toBe('COUNTER_CLOCKWISE');
  });

  it('builds a free-play round where every card is playable and the Ace allows both values', () => {
    const state = buildNinetyNineFreePlayRound();
    expect(state.options.freePlay).toBe('on');
    expect(state.playable.every((p) => p.safe)).toBe(true);
    expect(state.playable[2].aceValues).toEqual([1, 11]);
  });

  it('builds a bust that reveals what the busted seat still holds', () => {
    const state = buildNinetyNineBust();
    expect(state.phase).toBe('ROUND_OVER');
    expect(state.lastBust?.hand).toHaveLength(2);
    expect(state.lastBust?.totalBefore).toBeGreaterThan(99);
    expect(state.lastBust?.eliminated).toBe(false);
  });

  it('builds a quick-mode end with a shared first place', () => {
    const state = buildNinetyNineQuickEnd();
    expect(state.phase).toBe('FINISHED');
    expect(state.outcome).toMatchObject({ status: 'FINISHED', reason: 'QUICK' });
    const places = state.players.map((p) => p.place);
    expect(places.filter((p) => p === 1)).toHaveLength(2);
    expect(places).toContain(3);
  });
});
