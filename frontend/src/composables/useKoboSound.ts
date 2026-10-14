import { ref } from 'vue';
import { cardSound, sharedAudioContext } from './useCardSound';

// Kobo's sounds: the card laid on felt, a short high beep when it becomes my turn and a low one for a missed
// match. All synthesised (no audio asset), off by default, and the choice is remembered in this browser only.
// The audio context is only created once the sound is on and is resumed from the click that turned it on, which
// is what the browsers' autoplay policy asks for.
export const SOUND_STORAGE_KEY = 'kobo.sound';
export type KoboSoundKind = 'card' | 'turn' | 'miss';

interface Options {
  getContext: () => AudioContext | null;
  storage: Pick<Storage, 'getItem' | 'setItem'> | null;
  card: (count: number) => void;
}

const BEEPS: Record<'turn' | 'miss', { hz: number; seconds: number }> = {
  turn: { hz: 880, seconds: 0.09 },
  miss: { hz: 196, seconds: 0.16 },
};

export function createKoboSound(options: Options) {
  const read = (): boolean => {
    try { return options.storage?.getItem(SOUND_STORAGE_KEY) === 'on'; } catch { return false; }
  };
  const enabled = ref(read());

  function toggle(): void {
    enabled.value = !enabled.value;
    try { options.storage?.setItem(SOUND_STORAGE_KEY, enabled.value ? 'on' : 'off'); } catch { /* not remembered */ }
    if (!enabled.value) return;
    try {
      const ctx = options.getContext();
      if (ctx && ctx.state === 'suspended') void ctx.resume();
    } catch { /* audio is a nicety */ }
  }

  function play(kind: KoboSoundKind): void {
    if (!enabled.value) return;
    try {
      if (kind === 'card') { options.card(1); return; }
      const ctx = options.getContext();
      if (!ctx) return;
      if (ctx.state === 'suspended') void ctx.resume();
      beep(ctx, BEEPS[kind]);
    } catch {
      /* never break the game for a sound */
    }
  }

  return { enabled, toggle, play };
}

function beep(ctx: AudioContext, { hz, seconds }: { hz: number; seconds: number }): void {
  const at = ctx.currentTime;
  const osc = ctx.createOscillator();
  osc.type = 'sine';
  osc.frequency.setValueAtTime(hz, at);
  const gain = ctx.createGain();
  gain.gain.setValueAtTime(0.12, at);
  gain.gain.exponentialRampToValueAtTime(0.001, at + seconds);
  osc.connect(gain);
  gain.connect(ctx.destination);
  osc.start(at);
  osc.stop(at + seconds + 0.01);
}

let shared: ReturnType<typeof createKoboSound> | undefined;
/** The one sound of the page (one preference, shared by every component that asks). */
export function useKoboSound() {
  shared ??= createKoboSound({
    getContext: sharedAudioContext,
    storage: (() => { try { return window.localStorage; } catch { return null; } })(),
    card: (count) => cardSound.play(count),
  });
  return shared;
}
