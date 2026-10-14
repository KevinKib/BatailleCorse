<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import type { Movement, MotionAnchor } from '../../model/kobo/motion';
import { prefersReducedMotion } from '../../composables/usePileAnimation';

// The cards that travel. A layer over the whole screen (fixed, no pointer events, hidden from assistive
// technology) where each movement of the table is played as a copy of the card flying from where it was to where
// it lands. It is only a picture of what the state already says: nothing waits for it, nothing reads it back, a
// movement whose places cannot be found is skipped, and what the table shows at the end is the state's alone (the
// card hidden under a landing copy is shown again however the animation ends). Under reduced motion, nothing moves.
export interface MotionRect { left: number; top: number; width: number; height: number }
export interface MotionTarget {
  rect: MotionRect;
  /** Cards of the table hidden while a copy lands on them (the real card is under it); shown again at the end. */
  cards: HTMLElement[];
}

const props = defineProps<{ resolve: (anchor: MotionAnchor) => MotionTarget | null }>();

interface Ghost { id: number; movement: Movement; from: MotionRect; to: MotionRect; hide: HTMLElement[] }
interface Arrow { id: number; x1: number; y1: number; x2: number; y2: number; delayMs: number }

const EASING = 'cubic-bezier(.2,.8,.2,1)';
const ARROW_MS = 560;
const MAX_LIVE = 12;
const ghosts = ref<Ghost[]>([]);
const arrows = ref<Arrow[]>([]);
const root = ref<HTMLElement | null>(null);
let counter = 0;
const running = new Set<Animation>();
const hidden = new Map<HTMLElement, number>();

function hide(el: HTMLElement) {
  hidden.set(el, (hidden.get(el) ?? 0) + 1);
  el.style.opacity = '0';
}
function show(el: HTMLElement) {
  const left = (hidden.get(el) ?? 1) - 1;
  if (left > 0) { hidden.set(el, left); return; }
  hidden.delete(el);
  el.style.removeProperty('opacity');
}

function canAnimate(): boolean {
  return !prefersReducedMotion() && typeof Element.prototype.animate === 'function';
}

function playReveal(movement: Movement) {
  const table = props.resolve(movement.from);
  if (!table) return;
  for (const card of table.cards) {
    const animation = card.animate(
      [{ transform: 'scaleX(1)' }, { transform: 'scaleX(0.05)', offset: 0.45 }, { transform: 'scaleX(1)' }],
      { duration: movement.durationMs, easing: 'ease-in-out' });
    track(animation);
  }
}

function track(animation: Animation, onEnd?: () => void) {
  running.add(animation);
  const done = () => { running.delete(animation); onEnd?.(); };
  animation.finished.then(done, done);
}

/** Plays the movements of one event. Never throws, never waits: a failure only costs the picture. */
function play(movements: Movement[]) {
  try {
    if (!canAnimate()) return;
    for (const movement of movements) {
      if (movement.kind === 'reveal') { playReveal(movement); continue; }
      if (ghosts.value.length >= MAX_LIVE) break;
      const from = props.resolve(movement.from);
      const to = props.resolve(movement.to);
      if (!from || !to || from.rect.width <= 0 || to.rect.width <= 0) continue;
      const id = ++counter;
      const hideCards = movement.kind === 'travel' ? to.cards : [];
      ghosts.value.push({ id, movement, from: from.rect, to: to.rect, hide: hideCards });
      if (movement.byOpponent) {
        const center = (r: MotionRect) => ({ x: r.left + r.width / 2, y: r.top + r.height / 2 });
        const a = center(from.rect);
        const b = center(to.rect);
        if (Math.hypot(b.x - a.x, b.y - a.y) > 28) {
          arrows.value.push({ id, x1: a.x, y1: a.y, x2: b.x, y2: b.y, delayMs: movement.delayMs });
        }
      }
      void nextTick(() => launch(id));
    }
  } catch {
    /* a picture is never worth an error */
  }
}

function launch(id: number) {
  const ghost = ghosts.value.find(g => g.id === id);
  const el = root.value?.querySelector<HTMLElement>(`[data-ghost="${id}"]`);
  const finish = () => {
    ghosts.value = ghosts.value.filter(g => g.id !== id);
    arrows.value = arrows.value.filter(a => a.id !== id);
  };
  if (!ghost || !el) { finish(); return; }
  const { movement, from, to } = ghost;
  const centre = (r: MotionRect, scale: number) =>
    `translate(${r.left}px, ${r.top}px) scale(${scale})`;
  const scale = to.width / from.width;
  const start = centre(from, 1);
  const land = centre({ ...to, left: to.left + (to.width - from.width * scale) / 2, top: to.top + (to.height - from.height * scale) / 2 }, scale);
  const frames: Keyframe[] = movement.kind === 'glance'
    ? [{ transform: start }, { transform: midway(from, to), offset: 0.4 }, { transform: midway(from, to), offset: 0.6 }, { transform: start }]
    : [{ transform: start }, { transform: land }];

  ghost.hide.forEach(hide);
  const release = () => { ghost.hide.forEach(show); finish(); };
  // The cards come back whatever happens to the animation (cut, late, never started).
  const safety = setTimeout(release, movement.delayMs + movement.durationMs + 250);
  const options: KeyframeAnimationOptions = { duration: movement.durationMs, delay: movement.delayMs, easing: EASING, fill: 'both' };
  try {
    track(el.animate(frames, options), () => { clearTimeout(safety); release(); });
    if (movement.flip && movement.card) flipFaces(el, movement.durationMs, movement.delayMs);
    const arrow = root.value?.querySelector<SVGElement>(`[data-arrow="${id}"]`);
    if (arrow) {
      track(arrow.animate([{ opacity: 0 }, { opacity: 0.7, offset: 0.25 }, { opacity: 0.7, offset: 0.6 }, { opacity: 0 }],
        { duration: ARROW_MS, delay: movement.delayMs, fill: 'both' }));
    }
  } catch {
    clearTimeout(safety);
    release();
  }
}

function midway(from: MotionRect, to: MotionRect): string {
  const x = from.left + (to.left + to.width / 2 - from.left - from.width / 2) * 0.85;
  const y = from.top + (to.top + to.height / 2 - from.top - from.height / 2) * 0.85;
  return `translate(${x}px, ${y}px) scale(1)`;
}

// The card turns over halfway: the back narrows to nothing, then the face opens.
function flipFaces(el: HTMLElement, duration: number, delay: number) {
  const back = el.querySelector<HTMLElement>('[data-face="back"]');
  const front = el.querySelector<HTMLElement>('[data-face="front"]');
  const options = { duration, delay, fill: 'both' as const };
  if (back) track(back.animate([{ transform: 'scaleX(1)' }, { transform: 'scaleX(0)', offset: 0.5 }, { transform: 'scaleX(0)' }], options));
  if (front) track(front.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(0)', offset: 0.5 }, { transform: 'scaleX(1)' }], options));
}

onBeforeUnmount(() => {
  for (const animation of running) { try { animation.cancel(); } catch { /* already over */ } }
  running.clear();
  for (const el of hidden.keys()) el.style.removeProperty('opacity');
  hidden.clear();
});

defineExpose({ play });
</script>

<template>
  <div ref="root" class="motion-layer" data-test="motion-layer" aria-hidden="true">
    <svg class="motion-arrows" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <marker id="kobo-arrow-head" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M0 0 L10 5 L0 10 z" fill="currentColor" />
        </marker>
      </defs>
      <line v-for="a in arrows" :key="a.id" class="motion-arrow" data-test="motion-arrow" :data-arrow="a.id"
            :x1="a.x1" :y1="a.y1" :x2="a.x2" :y2="a.y2" marker-end="url(#kobo-arrow-head)" style="opacity: 0" />
    </svg>
    <div v-for="g in ghosts" :key="g.id" class="ghost" data-test="motion-card" :data-ghost="g.id"
         :data-kind="g.movement.kind" :style="{ width: g.from.width + 'px', height: g.from.height + 'px', transform: `translate(${g.from.left}px, ${g.from.top}px)` }">
      <template v-if="g.movement.flip && g.movement.card">
        <PlayingCard data-face="back" :hidden="true" rank="ace" suit="spade" class="face" />
        <PlayingCard data-face="front" :rank="g.movement.card.rank" :suit="g.movement.card.suit" class="face" />
      </template>
      <PlayingCard v-else-if="g.movement.card" class="face" :rank="g.movement.card.rank" :suit="g.movement.card.suit" />
      <PlayingCard v-else class="face" :hidden="true" rank="ace" suit="spade" />
    </div>
  </div>
</template>

<style scoped>
/* Over the table and under the notices; it never takes a pointer event and never makes the page scroll. */
.motion-layer {
  position: fixed;
  inset: 0;
  z-index: 1600;
  overflow: hidden;
  pointer-events: none;
}
.motion-arrows { position: absolute; inset: 0; width: 100%; height: 100%; overflow: hidden; color: rgba(255, 255, 255, 0.8); }
.motion-arrow { stroke: currentColor; stroke-width: 1.5; stroke-linecap: round; stroke-dasharray: 2 5; }
.ghost { position: absolute; left: 0; top: 0; transform-origin: top left; will-change: transform; }
.ghost :deep(.playing_card) { display: block; width: 100%; height: 100%; box-shadow: 0 3px 10px rgba(0, 0, 0, 0.45); border-radius: 6%; }
.ghost :deep(.playing_card[data-face]) { position: absolute; inset: 0; }
</style>
