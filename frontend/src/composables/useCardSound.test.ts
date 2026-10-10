import { describe, it, expect, vi } from 'vitest';
import { createCardSound } from './useCardSound';

function fakeContext() {
  const node = () => ({ connect: vi.fn(), start: vi.fn(), stop: vi.fn(), frequency: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
    gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() }, type: '', buffer: null as unknown, });
  return {
    currentTime: 0, state: 'running', destination: {}, sampleRate: 8000,
    resume: vi.fn(),
    createBuffer: vi.fn((_c: number, len: number) => ({ getChannelData: () => new Float32Array(len) })),
    createBufferSource: vi.fn(node), createBiquadFilter: vi.fn(() => ({ ...node(), frequency: { value: 0 }, Q: { value: 0 } })),
    createOscillator: vi.fn(node), createGain: vi.fn(node),
  };
}

describe('createCardSound', () => {
  it('synthesizes a sound with WebAudio when enabled', () => {
    const ctx = fakeContext();
    const sound = createCardSound(() => ctx as unknown as AudioContext);
    sound.play(1);
    expect(ctx.createBufferSource).toHaveBeenCalled();
    expect(ctx.createOscillator).toHaveBeenCalled();
  });

  it('resumes a suspended context (autoplay policy)', () => {
    const ctx = fakeContext(); ctx.state = 'suspended';
    createCardSound(() => ctx as unknown as AudioContext).play(1);
    expect(ctx.resume).toHaveBeenCalled();
  });

  it('plays one thump per card up to a cap', () => {
    const ctx = fakeContext();
    createCardSound(() => ctx as unknown as AudioContext).play(10);
    expect(ctx.createOscillator.mock.calls.length).toBeLessThanOrEqual(4);
    expect(ctx.createOscillator.mock.calls.length).toBeGreaterThan(1);
  });

  it('is silent and safe when WebAudio is unavailable or throws', () => {
    expect(() => createCardSound(() => null).play(1)).not.toThrow();
    expect(() => createCardSound(() => { throw new Error('nope'); }).play(1)).not.toThrow();
  });
});
