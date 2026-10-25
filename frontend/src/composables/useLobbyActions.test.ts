import { describe, it, expect, vi } from 'vitest';
import { useLobbyActions } from './useLobbyActions';

function setup(identity: { gameId: string; token: string } | null = { gameId: 'g1', token: 'tok' }) {
  const published: { dest: string; body: string }[] = [];
  const actions = useLobbyActions({
    publish: (dest, body) => { published.push({ dest, body }); },
    identity: () => identity,
  });
  return { actions, published };
}

describe('useLobbyActions', () => {
  it('setOption sends only the changed key to /app/lobby/setOptions', () => {
    const { actions, published } = setup();
    actions.setOption('deckSize', '52');
    expect(published).toEqual([{ dest: '/app/lobby/setOptions', body: JSON.stringify({ gameId: 'g1', token: 'tok', values: { deckSize: '52' } }) }]);
  });

  it('setSeatOption names the seat, key and value', () => {
    const { actions, published } = setup();
    actions.setSeatOption(2, 'tier', '4');
    expect(published).toEqual([{ dest: '/app/lobby/setSeatOption', body: JSON.stringify({ gameId: 'g1', token: 'tok', seat: 2, key: 'tier', value: '4' }) }]);
  });

  it('setPrivate sends the flag to /app/lobby/setPrivate', () => {
    const { actions, published } = setup();
    actions.setPrivate(true);
    expect(published).toEqual([{ dest: '/app/lobby/setPrivate', body: JSON.stringify({ gameId: 'g1', token: 'tok', privateGame: true }) }]);
  });

  it('rename sends the new name to /app/lobby/rename', () => {
    const { actions, published } = setup();
    actions.rename('Alice');
    expect(published).toEqual([{ dest: '/app/lobby/rename', body: JSON.stringify({ gameId: 'g1', token: 'tok', name: 'Alice' }) }]);
  });

  it('removeSeat names the seat to /app/lobby/removeSeat', () => {
    const { actions, published } = setup();
    actions.removeSeat(2);
    expect(published).toEqual([{ dest: '/app/lobby/removeSeat', body: JSON.stringify({ gameId: 'g1', token: 'tok', seat: 2 }) }]);
  });

  it('addBot, removeBot and leave use the /app/lobby destinations', () => {
    const { actions, published } = setup();
    actions.addBot();
    actions.removeBot(3);
    actions.leave();
    expect(published.map(p => p.dest)).toEqual(['/app/lobby/addBot', '/app/lobby/removeBot', '/app/lobby/leave']);
    expect(JSON.parse(published[1].body)).toEqual({ gameId: 'g1', token: 'tok', seat: 3 });
    expect(JSON.parse(published[2].body)).toEqual({ gameId: 'g1', token: 'tok' });
  });

  it('sends nothing before the player holds a seat', () => {
    const { actions, published } = setup(null);
    const publish = vi.fn();
    actions.addBot();
    expect(published).toEqual([]);
    expect(publish).not.toHaveBeenCalled();
  });
});
