import { describe, expect, it, vi } from 'vitest';
import { createKoboSound, SOUND_STORAGE_KEY } from './useKoboSound';

function memoryStorage(initial: Record<string, string> = {}) {
  const data = { ...initial };
  return {
    getItem: (k: string) => data[k] ?? null,
    setItem: (k: string, v: string) => { data[k] = v; },
    data,
  };
}
function fakeContext() {
  const osc = { type: '', frequency: { setValueAtTime: vi.fn() }, connect: vi.fn(), start: vi.fn(), stop: vi.fn() };
  const gain = { gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() }, connect: vi.fn() };
  return {
    ctx: {
      state: 'running', currentTime: 0, destination: {}, resume: vi.fn(),
      createOscillator: vi.fn(() => osc), createGain: vi.fn(() => gain),
    } as unknown as AudioContext,
    osc,
  };
}

describe('createKoboSound', () => {
  it('isOffByDefault', () => {
    const sound = createKoboSound({ getContext: () => null, storage: memoryStorage(), card: vi.fn() });
    expect(sound.enabled.value).toBe(false);
  });

  it('remembersTheChoiceInStorage', () => {
    const storage = memoryStorage();
    const sound = createKoboSound({ getContext: () => null, storage, card: vi.fn() });
    sound.toggle();
    expect(sound.enabled.value).toBe(true);
    expect(storage.data[SOUND_STORAGE_KEY]).toBe('on');
    expect(createKoboSound({ getContext: () => null, storage, card: vi.fn() }).enabled.value).toBe(true);
    sound.toggle();
    expect(storage.data[SOUND_STORAGE_KEY]).toBe('off');
  });

  it('givenAStorageThatThrows_thenItStillWorksForThePage', () => {
    const storage = { getItem: () => { throw new Error('blocked'); }, setItem: () => { throw new Error('blocked'); } };
    const sound = createKoboSound({ getContext: () => null, storage, card: vi.fn() });
    expect(sound.enabled.value).toBe(false);
    expect(() => sound.toggle()).not.toThrow();
    expect(sound.enabled.value).toBe(true);
  });

  it('givenNoStorageAtAll_thenItStillWorks', () => {
    const sound = createKoboSound({ getContext: () => null, storage: null, card: vi.fn() });
    expect(() => sound.toggle()).not.toThrow();
  });

  it('givenSoundOff_thenNothingPlaysAndNoAudioContextIsCreated', () => {
    const getContext = vi.fn(() => null);
    const card = vi.fn();
    const sound = createKoboSound({ getContext, storage: memoryStorage(), card });
    sound.play('card');
    sound.play('turn');
    expect(getContext).not.toHaveBeenCalled();
    expect(card).not.toHaveBeenCalled();
  });

  it('givenSoundOn_thenACardMovePlaysTheCardSound', () => {
    const card = vi.fn();
    const sound = createKoboSound({ getContext: () => null, storage: memoryStorage({ [SOUND_STORAGE_KEY]: 'on' }), card });
    sound.play('card');
    expect(card).toHaveBeenCalled();
  });

  it('givenSoundOn_thenTheTurnAndTheMissPlayAShortBeepOfTheirOwn', () => {
    const { ctx, osc } = fakeContext();
    const sound = createKoboSound({ getContext: () => ctx, storage: memoryStorage({ [SOUND_STORAGE_KEY]: 'on' }), card: vi.fn() });
    sound.play('turn');
    sound.play('miss');
    expect(osc.start).toHaveBeenCalledTimes(2);
    const [turn, miss] = osc.frequency.setValueAtTime.mock.calls.map(c => c[0] as number);
    expect(turn).toBeGreaterThan(miss);
  });

  it('turningItOnResumesTheContextFromTheClickThatAskedForIt', () => {
    const { ctx } = fakeContext();
    (ctx as unknown as { state: string }).state = 'suspended';
    const sound = createKoboSound({ getContext: () => ctx, storage: memoryStorage(), card: vi.fn() });
    sound.toggle();
    expect(ctx.resume).toHaveBeenCalled();
  });

  it('givenAnAudioFailure_thenPlayingNeverThrows', () => {
    const sound = createKoboSound({
      getContext: () => { throw new Error('no audio'); }, storage: memoryStorage({ [SOUND_STORAGE_KEY]: 'on' }), card: vi.fn(),
    });
    expect(() => sound.play('turn')).not.toThrow();
  });
});
