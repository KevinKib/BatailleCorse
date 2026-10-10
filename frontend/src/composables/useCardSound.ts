// A short "card laid on felt" sound synthesized with WebAudio (no audio asset): a burst of
// low-passed noise (the slap of the card) over a quick low sine thump.
const MAX_THUMPS = 4;
const THUMP_SPACING = 0.07;

export function createCardSound(getContext: () => AudioContext | null) {
  function play(cardCount: number): void {
    try {
      const ctx = getContext();
      if (!ctx) return;
      if (ctx.state === 'suspended') void ctx.resume();
      const thumps = Math.max(1, Math.min(cardCount, MAX_THUMPS));
      for (let i = 0; i < thumps; i++) thump(ctx, ctx.currentTime + i * THUMP_SPACING);
    } catch {
      /* audio is a nicety: never break the game */
    }
  }
  return { play };
}

function thump(ctx: AudioContext, at: number): void {
  const length = Math.floor(ctx.sampleRate * 0.06);
  const buffer = ctx.createBuffer(1, length, ctx.sampleRate);
  const data = buffer.getChannelData(0);
  for (let i = 0; i < length; i++) data[i] = (Math.random() * 2 - 1) * (1 - i / length);
  const noise = ctx.createBufferSource();
  noise.buffer = buffer;
  const filter = ctx.createBiquadFilter();
  filter.type = 'lowpass';
  filter.frequency.value = 1800;
  const noiseGain = ctx.createGain();
  noiseGain.gain.setValueAtTime(0.35, at);
  noiseGain.gain.exponentialRampToValueAtTime(0.001, at + 0.06);
  noise.connect(filter);
  filter.connect(noiseGain);
  noiseGain.connect(ctx.destination);
  noise.start(at);

  const osc = ctx.createOscillator();
  osc.type = 'sine';
  osc.frequency.setValueAtTime(170, at);
  osc.frequency.exponentialRampToValueAtTime(70, at + 0.09);
  const oscGain = ctx.createGain();
  oscGain.gain.setValueAtTime(0.4, at);
  oscGain.gain.exponentialRampToValueAtTime(0.001, at + 0.1);
  osc.connect(oscGain);
  oscGain.connect(ctx.destination);
  osc.start(at);
  osc.stop(at + 0.11);
}

let shared: AudioContext | null | undefined;
// One lazily created context for the page; created from a user gesture path (the first play
// follows a click), which satisfies the browsers' autoplay policy.
export function sharedAudioContext(): AudioContext | null {
  if (shared === undefined) {
    try {
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      shared = Ctor ? new Ctor() : null;
    } catch {
      shared = null;
    }
  }
  return shared;
}

export const cardSound = createCardSound(sharedAudioContext);
