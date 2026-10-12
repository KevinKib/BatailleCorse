import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useLobbyCreator } from './useLobbyCreator';
import { findGame } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

describe('useLobbyCreator', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('gives Bullshit a creator that opens the lobby through its store', async () => {
    const create = vi.spyOn(useBullshitStore(), 'create').mockResolvedValue('g1');
    const creator = useLobbyCreator()(findGame('bullshit')!);
    expect(creator).toBeDefined();
    expect(await creator!('Alice')).toBe('g1');
    expect(create).toHaveBeenCalledWith('Alice');
  });

  it('gives Bataille Corse a creator that opens the lobby through its store', async () => {
    const create = vi.spyOn(useBatailleCorseStore(), 'create').mockResolvedValue('g2');
    const creator = useLobbyCreator()(findGame('bataillecorse')!);
    expect(await creator('Alice')).toBe('g2');
    expect(create).toHaveBeenCalledWith('Alice');
  });
});
