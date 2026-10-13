import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useGameRooms } from './useGameRooms';
import { GAMES, findGame, type GameDefinition } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

describe('useGameRooms', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('givenBullshit_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useBullshitStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g1');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('bullshit')!);
    expect(await room.create('Alice')).toBe('g1');
    await room.join('g1', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g1', 'Bob');
  });

  it('givenBatailleCorse_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useBatailleCorseStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g2');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('bataillecorse')!);
    expect(await room.create('Alice')).toBe('g2');
    await room.join('g2', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g2', 'Bob');
  });

  it('givenEveryRegisteredGame_thenItHasARoomClient', () => {
    const rooms = useGameRooms();
    GAMES.forEach(g => expect(() => rooms(g)).not.toThrow());
  });

  it('givenAGameWithNoRoomClient_thenItFailsLoudlyInsteadOfJoiningAnotherGame', () => {
    const unwired = { ...findGame('bullshit')!, key: 'kobo' } as GameDefinition;
    expect(() => useGameRooms()(unwired)).toThrow(/kobo/);
  });
});
