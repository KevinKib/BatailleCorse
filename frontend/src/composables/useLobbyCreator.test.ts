import { describe, it, expect, beforeEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useLobbyCreator } from './useLobbyCreator';
import { findGame } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';

describe('useLobbyCreator', () => {
  beforeEach(() => { setActivePinia(createPinia()); });

  it('gives Bullshit a creator that opens the lobby through its store', async () => {
    const create = vi.spyOn(useBullshitStore(), 'create').mockResolvedValue('g1');
    const creator = useLobbyCreator()(findGame('bullshit')!);
    expect(creator).toBeDefined();
    expect(await creator!('Alice')).toBe('g1');
    expect(create).toHaveBeenCalledWith('Alice');
  });

  it('gives none to a game that still has its own create page', () => {
    expect(useLobbyCreator()(findGame('bataillecorse')!)).toBeUndefined();
  });
});
