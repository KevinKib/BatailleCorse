import { describe, expect, it, vi } from 'vitest';
import { ANIMATION_SPEED_KEY, nextAnimationSpeed, useAnimationSpeed, type SpeedStorage } from './useAnimationSpeed';

function memory(initial: Record<string, string> = {}): SpeedStorage & { data: Record<string, string> } {
  const data = { ...initial };
  return {
    data,
    getItem: key => data[key] ?? null,
    setItem: (key, value) => { data[key] = value; },
  };
}

describe('nextAnimationSpeed', () => {
  it('cycles Normal, Fast, Instant, Normal', () => {
    expect(nextAnimationSpeed('normal')).toBe('fast');
    expect(nextAnimationSpeed('fast')).toBe('instant');
    expect(nextAnimationSpeed('instant')).toBe('normal');
  });
});

describe('useAnimationSpeed', () => {
  it('defaults to Normal', () => {
    expect(useAnimationSpeed({ storage: memory(), reducedMotion: () => false }).speed.value).toBe('normal');
  });

  it('cycle() moves to the next speed and stores it', () => {
    const storage = memory();
    const { speed, cycle } = useAnimationSpeed({ storage, reducedMotion: () => false });
    cycle();
    expect(speed.value).toBe('fast');
    expect(storage.data[ANIMATION_SPEED_KEY]).toBe('fast');
    cycle();
    expect(speed.value).toBe('instant');
    cycle();
    expect(speed.value).toBe('normal');
    expect(storage.data[ANIMATION_SPEED_KEY]).toBe('normal');
  });

  it('reads the stored speed back in the next instance', () => {
    const storage = memory();
    useAnimationSpeed({ storage, reducedMotion: () => false }).cycle();
    expect(useAnimationSpeed({ storage, reducedMotion: () => false }).speed.value).toBe('fast');
  });

  it('ignores a stored value it does not know', () => {
    const storage = memory({ [ANIMATION_SPEED_KEY]: 'turbo' });
    expect(useAnimationSpeed({ storage, reducedMotion: () => false }).speed.value).toBe('normal');
  });

  it('works without storage: reading throws, writing throws', () => {
    const broken: SpeedStorage = {
      getItem: () => { throw new Error('denied'); },
      setItem: () => { throw new Error('denied'); },
    };
    const { speed, cycle } = useAnimationSpeed({ storage: broken, reducedMotion: () => false });
    expect(speed.value).toBe('normal');
    expect(() => cycle()).not.toThrow();
    expect(speed.value).toBe('fast');
  });

  it('works when the storage itself is unavailable', () => {
    const { speed, cycle } = useAnimationSpeed({ storage: null, reducedMotion: () => false });
    cycle();
    expect(speed.value).toBe('fast');
  });

  it('defaults to Instant when the device asks for reduced motion and the player never chose', () => {
    expect(useAnimationSpeed({ storage: memory(), reducedMotion: () => true }).speed.value).toBe('instant');
  });

  it('an explicit choice wins over reduced motion', () => {
    const storage = memory({ [ANIMATION_SPEED_KEY]: 'normal' });
    expect(useAnimationSpeed({ storage, reducedMotion: () => true }).speed.value).toBe('normal');
  });

  it('after a cycle from the reduced-motion default, the next speed is Normal and it is remembered', () => {
    const storage = memory();
    const { speed, cycle } = useAnimationSpeed({ storage, reducedMotion: () => true });
    cycle();
    expect(speed.value).toBe('normal');
    expect(storage.data[ANIMATION_SPEED_KEY]).toBe('normal');
    expect(useAnimationSpeed({ storage, reducedMotion: () => true }).speed.value).toBe('normal');
  });

  it('uses window.localStorage and the media query by default', () => {
    localStorage.clear();
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    const { speed, cycle } = useAnimationSpeed();
    expect(speed.value).toBe('normal');
    cycle();
    expect(localStorage.getItem(ANIMATION_SPEED_KEY)).toBe('fast');
    vi.unstubAllGlobals();
    localStorage.clear();
  });
});
