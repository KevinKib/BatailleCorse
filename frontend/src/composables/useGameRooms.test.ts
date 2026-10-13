import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useGameRooms } from './useGameRooms';
import { GAMES, findGame, type GameDefinition } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';
import { useKoboStore } from '../state/Kobo.store';
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

  it('givenKobo_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useKoboStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g3');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    // Kobo is not on the Game selection until its screen exists: its definition is built by hand here.
    const kobo = { ...findGame('bullshit')!, key: 'kobo' } as GameDefinition;
    const room = useGameRooms()(kobo);
    expect(await room.create('Alice')).toBe('g3');
    await room.join('g3', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g3', 'Bob');
  });

  it('givenAGameWithNoRoomClient_thenItFailsLoudlyInsteadOfJoiningAnotherGame', () => {
    const unwired = { ...findGame('bullshit')!, key: 'three-men-morris' } as GameDefinition;
    expect(() => useGameRooms()(unwired)).toThrow(/three-men-morris/);
  });
});
