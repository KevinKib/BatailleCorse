import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import AI from './AI';
import { buildGame, buildPile, buildPlayer, buildCard } from '../fixtures';

describe('AI.play', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('calls slap when pile has a rank-10 card on top', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [buildCard({ rank: '10' })] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1' })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.slap).toHaveBeenCalledOnce();
    expect(actions.send).not.toHaveBeenCalled();
  });

  it('calls slap when top two cards share the same rank', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [buildCard({ rank: '7' }), buildCard({ rank: '7' })] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1' })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.slap).toHaveBeenCalledOnce();
  });

  it('calls send when pile is not slap-worthy and SEND is available', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: ['SEND'] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.send).toHaveBeenCalledOnce();
    expect(actions.slap).not.toHaveBeenCalled();
  });

  it('does nothing when pile is not slap-worthy and SEND is unavailable', () => {
    const ai = new AI(1, 0);
    const state = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: [] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    vi.runAllTimers();

    expect(actions.send).not.toHaveBeenCalled();
    expect(actions.slap).not.toHaveBeenCalled();
  });

  it('cancel prevents the pending action from firing', () => {
    const ai = new AI(1, 500);
    const state = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: ['SEND'] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(state, actions);
    ai.cancel();
    vi.runAllTimers();

    expect(actions.send).not.toHaveBeenCalled();
  });

  it('a second play() call replaces the pending first call', () => {
    const ai = new AI(1, 500);
    const slapState = buildGame({
      pile: buildPile({ cards: [buildCard({ rank: '10' })] }),
    });
    const sendState = buildGame({
      pile: buildPile({ cards: [] }),
      players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1', availableActions: ['SEND'] })],
    });
    const actions = { send: vi.fn(), slap: vi.fn() };

    ai.play(slapState, actions);
    ai.play(sendState, actions); // replaces the pending slap
    vi.runAllTimers();

    expect(actions.slap).not.toHaveBeenCalled();
    expect(actions.send).toHaveBeenCalledOnce();
  });
});
