import { onBeforeUnmount, ref, watch } from 'vue';
import { planMove } from '../model/draughts/motion';
import type { DraughtsTempo } from '../model/draughts/tempo';
import type { DraughtsBoard, DraughtsMoveEventData, DraughtsPieceCode } from '../model/draughts/DraughtsState';

/** The first leg waits this long so that the browser has drawn the piece on its start square before it moves. */
export const GLIDE_START_DELAY_MS = 20;

export interface GlideSource {
  /** The board as the server last said. */
  board: () => DraughtsBoard;
  /** The move to play out: a new object per move. Null when there is nothing to show. */
  motion: () => DraughtsMoveEventData | null;
  tempo: () => DraughtsTempo;
}

export interface GhostPiece {
  square: number;
  code: DraughtsPieceCode;
  /** The piece is on its way out (the removal has started). */
  fading: boolean;
}

/**
 * Plays out one move on the board: the piece glides along its path, then the captured pieces leave in capture
 * order. The board already holds the final position; this keeps the arrival square empty while the piece is on its
 * way and keeps the captured pieces drawn as ghosts until their turn. With a zero tempo nothing is shown at all.
 */
export function useDraughtsGlide(source: GlideSource) {
  /** The piece in flight and the square it is on now. */
  const glide = ref<{ code: DraughtsPieceCode; square: number } | null>(null);
  /** The square where the piece lands: its static piece waits for the glide to end. */
  const arrival = ref<number | null>(null);
  const ghosts = ref<GhostPiece[]>([]);

  let timers: ReturnType<typeof setTimeout>[] = [];
  // The board before the last change, to tell which piece moved and what the captured pieces were.
  let previous = source.board();
  watch(source.board, (_now, before) => { previous = before; }, { flush: 'sync' });

  function after(ms: number, run: () => void) {
    timers.push(setTimeout(run, ms));
  }

  function stop() {
    timers.forEach(clearTimeout);
    timers = [];
    glide.value = null;
    arrival.value = null;
    ghosts.value = [];
  }

  function play(move: DraughtsMoveEventData | null) {
    stop();
    if (!move) return;
    const plan = planMove(move, source.tempo());
    if (plan.totalMs === 0) return;
    const code = previous[move.from - 1] ?? source.board()[move.to - 1];
    if (!code) return;

    glide.value = { code, square: move.from };
    arrival.value = move.to;
    ghosts.value = move.captures.flatMap((square) => {
      const taken = previous[square - 1];
      return taken ? [{ square, code: taken, fading: false }] : [];
    });

    for (const leg of plan.glides) {
      after(GLIDE_START_DELAY_MS + leg.atMs, () => {
        if (glide.value) glide.value = { ...glide.value, square: leg.to };
      });
    }
    const last = plan.glides[plan.glides.length - 1];
    const glideEnd = last ? last.atMs + last.durationMs : 0;
    after(GLIDE_START_DELAY_MS + glideEnd, () => { glide.value = null; arrival.value = null; });

    for (const removal of plan.removals) {
      after(GLIDE_START_DELAY_MS + removal.atMs, () => {
        ghosts.value = ghosts.value.map(g => (g.square === removal.square ? { ...g, fading: true } : g));
      });
      after(GLIDE_START_DELAY_MS + removal.atMs + removal.durationMs, () => {
        ghosts.value = ghosts.value.filter(g => g.square !== removal.square);
      });
    }
  }

  // After the render: the board and the move of one message arrive together, and the board must be the new one.
  watch(source.motion, play, { flush: 'post' });
  onBeforeUnmount(stop);

  return { glide, arrival, ghosts };
}
