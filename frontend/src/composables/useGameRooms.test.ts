import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useGameRooms } from './useGameRooms';
import { GAMES, findGame, type GameDefinition } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';
import { useKoboStore } from '../state/Kobo.store';
import { useThreeMenMorrisStore } from '../state/ThreeMenMorris.store';
import { usePresidentStore } from '../state/President.store';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import { useMangolaStore } from '../state/Mangola.store';
import { useNinetyNineStore } from '../state/NinetyNine.store';
import { useBlackjackStore } from '../state/Blackjack.store';
import { useDraughtsStore } from '../state/Draughts.store';
import { useKiloDeMerdeStore } from '../state/KiloDeMerde.store';

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

  it('givenThreeMenMorris_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useThreeMenMorrisStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g4');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('three-men-morris')!);
    expect(await room.create('Alice')).toBe('g4');
    await room.join('g4', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g4', 'Bob');
  });

  it('givenPresident_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = usePresidentStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g5');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('president')!);
    expect(await room.create('Alice')).toBe('g5');
    await room.join('g5', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g5', 'Bob');
  });

  it('givenMangola_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useMangolaStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g6');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('mangola')!);
    expect(await room.create('Alice')).toBe('g6');
    await room.join('g6', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g6', 'Bob');
  });

  it('givenNinetyNine_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useNinetyNineStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g7');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('ninety-nine')!);
    expect(await room.create('Alice')).toBe('g7');
    await room.join('g7', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g7', 'Bob');
  });

  it('givenKiloDeMerde_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useKiloDeMerdeStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g9');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('kilo-de-merde')!);
    expect(await room.create('Alice')).toBe('g9');
    await room.join('g9', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g9', 'Bob');
  });

  it('givenDraughts_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useDraughtsStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g8');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('draughts')!);
    expect(await room.create('Alice')).toBe('g8');
    await room.join('g8', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g8', 'Bob');
  });

  it('givenBlackjack_thenCreateAndJoinGoThroughItsStore', async () => {
    const store = useBlackjackStore();
    const create = vi.spyOn(store, 'create').mockResolvedValue('g9');
    const join = vi.spyOn(store, 'join').mockResolvedValue();
    const room = useGameRooms()(findGame('blackjack')!);
    expect(await room.create('Alice')).toBe('g9');
    await room.join('g9', 'Bob');
    expect(create).toHaveBeenCalledWith('Alice');
    expect(join).toHaveBeenCalledWith('g9', 'Bob');
  });

  it('givenAGameWithNoRoomClient_thenItFailsLoudlyInsteadOfJoiningAnotherGame', () => {
    const unwired = { ...findGame('bullshit')!, key: 'chess' } as unknown as GameDefinition;
    expect(() => useGameRooms()(unwired)).toThrow(/chess/);
  });

  it('givenEveryGame_thenCreateForwardsThePrivateFlagToItsStoreAndOnlyWhenGiven', async () => {
    const stores = {
      bataillecorse: useBatailleCorseStore(), bullshit: useBullshitStore(), kobo: useKoboStore(),
      'three-men-morris': useThreeMenMorrisStore(), president: usePresidentStore(), mangola: useMangolaStore(),
      'ninety-nine': useNinetyNineStore(), draughts: useDraughtsStore(),
      blackjack: useBlackjackStore(),
      'kilo-de-merde': useKiloDeMerdeStore(),
    };
    for (const g of GAMES) {
      const create = vi.spyOn(stores[g.key], 'create').mockResolvedValue('g');
      const room = useGameRooms()(g);
      await room.create('Ann', true);
      await room.create('Ann');
      expect(create.mock.calls, g.key).toEqual([['Ann', true], ['Ann']]);
    }
  });
});
