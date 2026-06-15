import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { resolveBullshitSession } from './useBullshitBootstrap';

describe('resolveBullshitSession', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('returns the stored seat and token for a game', () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    expect(resolveBullshitSession('g1')).toEqual({ seat: 1, token: 'tok-1' });
  });

  it('returns null when no token is stored', () => {
    expect(resolveBullshitSession('g1')).toBeNull();
  });
});
