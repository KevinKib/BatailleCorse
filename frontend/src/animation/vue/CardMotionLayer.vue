<script setup lang="ts" generic="Anchor">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';
import PlayingCard from '../../components/PlayingCard.vue';
import type Card from '../../model/Card';
import type { TableMovement } from '../core/movement';
import { canAnimate, prefersReducedMotion } from '../core/reducedMotion';
import type { MotionRect, MotionTarget } from '../dom/rect';

// The cards that travel, shared by every table that animates (Kobo, President, Bullshit). A layer over the whole screen
// (fixed, no pointer events, hidden from assistive technology) where each movement of the table is played as a copy
// of the card flying from where it was to where it lands. It is only a picture of what the state already says:
// nothing waits for it, nothing reads it back, a movement whose places cannot be found is skipped, and what the table
// shows at the end is the state's alone (the card hidden under a landing copy is shown again however the animation
// ends). Under reduced motion, nothing moves. The places are the table's own (`Anchor`): `resolve` finds them.
type Movement = TableMovement<Anchor>;
type MotionAnchor = Anchor;

const props = withDefaults(defineProps<{
  resolve: (anchor: Anchor) => MotionTarget | null;
  /** How long the ring stays on every place a move touched (longer when the move itself lasts longer). */
  markMs?: number;
  /** The same ring when it does not animate (reduced motion, or a still play): a plain outline. */
  markReducedMs?: number;
  /** `soft`: a thin tinted contour hugging the card (no halo, no pop); default: the glowing ring. */
  markStyle?: 'glow' | 'soft';
  /** The id of the arrow head marker (unique on the page). */
  arrowId?: string;
  /** The level of the layer over the page; default (the style sheet's 1600): over the table and under the notices. */
  zIndex?: number;
}>(), { markMs: 2000, markReducedMs: 1500, markStyle: 'glow', arrowId: 'table-arrow-head', zIndex: undefined });
/** How far a ring stands off the card it surrounds. */
const markPad = computed(() => props.markStyle === 'soft' ? 2 : 4);

interface Ghost { id: number; movement: Movement; from: MotionRect; to: MotionRect; hide: HTMLElement[]; waitMs: number }
/** A card the table keeps showing in a place until a copy lands on it (or leaves it), so the place is never blank. */
interface Under { id: number; owner: number; rect: MotionRect; card: Card | null; until: 'land' | 'leave' }
interface Arrow { id: number; x1: number; y1: number; x2: number; y2: number; delayMs: number }
/** A ring on a slot a move touched: a plain outline, kept for a moment so the changed cards can be found. */
interface Mark { id: number; rect: MotionRect; role: 'from' | 'to'; lifeMs: number; still: boolean }

const EASING = 'cubic-bezier(.2,.8,.2,1)';
const MAX_LIVE = 12;
const MAX_MARKS = 24;
const ghosts = ref<Ghost[]>([]);
const arrows = ref<Arrow[]>([]);
const marks = ref<Mark[]>([]);
const unders = ref<Under[]>([]);
const markTimers = new Set<ReturnType<typeof setTimeout>>();
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

/** Rings every place the movements touch (source dashed, destination solid), under reduced motion too. */
function showMarks(movements: Movement[], forceStill = false) {
  const places = new Map<string, { anchor: MotionAnchor; role: 'from' | 'to' }>();
  for (const movement of movements) {
    for (const anchor of movement.marks) {
      const key = JSON.stringify(anchor);
      const role = JSON.stringify(anchor) === JSON.stringify(movement.from) ? 'from' : 'to';
      if (places.get(key)?.role !== 'to') places.set(key, { anchor, role });
    }
  }
  const still = forceStill || prefersReducedMotion();
  const end = movements.reduce((m, x) => Math.max(m, x.delayMs + x.durationMs + x.holdAfterMs), 0);
  const lifeMs = still ? props.markReducedMs : Math.max(props.markMs, end);
  for (const { anchor, role } of places.values()) {
    if (marks.value.length >= MAX_MARKS) break;
    const target = props.resolve(anchor);
    if (!target || target.rect.width <= 0) continue;
    const id = ++counter;
    marks.value.push({ id, rect: target.rect, role, lifeMs, still });
    const timer = setTimeout(() => {
      markTimers.delete(timer);
      marks.value = marks.value.filter(m => m.id !== id);
    }, lifeMs);
    markTimers.add(timer);
  }
}

/**
 * Plays the movements of one event. Never throws, never waits: a failure only costs the picture. `marksOnly`: the
 * backlog is full, so the rings show at once and the flights are skipped. `still`: the rings do not animate either
 * (a table set to play no animation). Returns when its flights are over, counted from now (`endsInMs`: the longest
 * wait + delay + flight + hold after landing it scheduled, 0 when it played nothing).
 */
function play(movements: Movement[], options: { marksOnly?: boolean; startInMs?: number; still?: boolean } = {}): { endsInMs: number } {
  let endsInMs = 0;
  // `startInMs`: this event waits behind the previous one. It is set up at once (the table already shows the new
  // state: the places the copies land on are hidden and show their old card meanwhile) but its copies stay
  // invisible until their turn, and its rings come with them.
  const wait = options.marksOnly || prefersReducedMotion() ? 0 : Math.max(0, options.startInMs ?? 0);
  const ring = () => {
    try {
      showMarks(movements, options.still === true);
    } catch {
      /* a picture is never worth an error */
    }
  };
  if (wait > 0) {
    const timer = setTimeout(() => { markTimers.delete(timer); ring(); }, wait);
    markTimers.add(timer);
  } else {
    ring();
  }
  try {
    if (options.marksOnly || !canAnimate()) return { endsInMs };
    const endOf = (m: Movement) => wait + m.delayMs + m.durationMs + m.holdAfterMs;
    for (const movement of movements) {
      // A ring is only its marks, already shown above.
      if (movement.kind === 'ring') continue;
      if (movement.kind === 'reveal') {
        endsInMs = Math.max(endsInMs, endOf(movement));
        if (wait > 0) {
          const timer = setTimeout(() => { markTimers.delete(timer); playReveal(movement); }, wait);
          markTimers.add(timer);
        } else {
          playReveal(movement);
        }
        continue;
      }
      if (ghosts.value.length >= MAX_LIVE) break;
      const from = props.resolve(movement.from);
      const to = props.resolve(movement.to);
      if (!from || !to || from.rect.width <= 0 || to.rect.width <= 0) continue;
      const id = ++counter;
      endsInMs = Math.max(endsInMs, endOf(movement));
      const hideCards = movement.kind === 'travel' ? to.cards : [];
      ghosts.value.push({ id, movement, from: from.rect, to: to.rect, hide: hideCards, waitMs: wait });
      if (movement.kind === 'travel') {
        if (movement.restsOn !== undefined) unders.value.push({ id: ++counter, owner: id, rect: to.rect, card: movement.restsOn, until: 'land' });
        if (movement.leavesBack) {
          const under: Under = { id: ++counter, owner: id, rect: from.rect, card: null, until: 'leave' };
          unders.value.push(under);
          const timer = setTimeout(() => {
            markTimers.delete(timer);
            unders.value = unders.value.filter(u => u.id !== under.id);
          }, wait + movement.delayMs + 80);
          markTimers.add(timer);
        }
      }
      if (movement.byOpponent) {
        const center = (r: MotionRect) => ({ x: r.left + r.width / 2, y: r.top + r.height / 2 });
        const a = center(from.rect);
        const b = center(to.rect);
        if (Math.hypot(b.x - a.x, b.y - a.y) > 28) {
          arrows.value.push({ id, x1: a.x, y1: a.y, x2: b.x, y2: b.y, delayMs: wait + movement.delayMs });
        }
      }
      void nextTick(() => launch(id));
    }
  } catch {
    /* a picture is never worth an error */
  }
  return { endsInMs };
}

function launch(id: number) {
  const ghost = ghosts.value.find(g => g.id === id);
  const el = root.value?.querySelector<HTMLElement>(`[data-ghost="${id}"]`);
  const finish = () => {
    ghosts.value = ghosts.value.filter(g => g.id !== id);
    arrows.value = arrows.value.filter(a => a.id !== id);
    unders.value = unders.value.filter(u => u.owner !== id);
  };
  if (!ghost || !el) { finish(); return; }
  const { movement, from, to, waitMs } = ghost;
  const startMs = waitMs + movement.delayMs;
  const centre = (r: MotionRect, scale: number) =>
    `translate(${r.left}px, ${r.top}px) scale(${scale})`;
  const landing = movement.kind === 'travel' ? movement.landing : undefined;
  const fitted = to.width / from.width;
  const scale = landing?.scaleTo === 'centre' ? Math.min(1, Math.max(0.3, fitted)) : fitted;
  const start = centre(from, 1);
  // `centre`: the copy shrinks about its own centre, so its full-size box is placed on the centre of the place.
  const land = landing?.scaleTo === 'centre'
    ? centre({ ...to, left: to.left + to.width / 2 - from.width / 2, top: to.top + to.height / 2 - from.height / 2 }, scale)
    : centre({ ...to, left: to.left + (to.width - from.width * scale) / 2, top: to.top + (to.height - from.height * scale) / 2 }, scale);
  const easing = movement.easing ?? EASING;
  // One animation: the hold before is its delay (the copy waits on its source), then the flight, then the hold
  // after landing (the copy stays on its destination, so its face can be read). Keyframe offsets cut the three.
  const total = movement.durationMs + movement.holdAfterMs;
  const flight = movement.durationMs / total;
  let frames: Keyframe[];
  if (movement.kind === 'glance') {
    frames = [
      { transform: start, easing },
      { transform: midway(from, to), offset: flight * 0.35 },
      { transform: midway(from, to), offset: flight * 0.65, easing },
      { transform: start, offset: flight },
      { transform: start },
    ];
  } else if (landing) {
    // Dropped on the destination: it turns by `tiltDeg` on the way and, once landed, may fade away.
    const tilted = `${land} rotate(${landing.tiltDeg}deg)`;
    const seen = landing.fade ? { opacity: 1 } : {};
    frames = [
      { transform: `${start} rotate(0deg)`, ...seen },
      { transform: tilted, ...seen, offset: flight },
      { transform: tilted, ...(landing.fade ? { opacity: 0 } : {}) },
    ];
  } else {
    frames = [{ transform: start, easing }, { transform: land, offset: flight }, { transform: land }];
  }

  ghost.hide.forEach(hide);
  const release = () => { ghost.hide.forEach(show); finish(); };
  // The cards come back whatever happens to the animation (cut, late, never started).
  const safety = setTimeout(release, startMs + total + 250);
  // A dropped copy (`landing`) is eased as a whole, its fade included; the other movements ease their flight only.
  const options: KeyframeAnimationOptions = landing
    ? { duration: total, delay: startMs, easing, fill: 'both' }
    : { duration: total, delay: startMs, fill: 'both' };
  try {
    track(el.animate(frames, options), () => { clearTimeout(safety); release(); });
    // Behind another movement: the copy is not seen until its turn comes.
    if (waitMs > 0) track(el.animate([{ opacity: 0 }, { opacity: 0 }], { duration: waitMs, delay: 0, fill: 'none' }));
    if (movement.flip && movement.card) flipFaces(el, movement.durationMs, startMs);
    if (movement.turnDown && movement.card) turnFacesDown(el, movement.durationMs, total, startMs);
    const arrow = root.value?.querySelector<SVGElement>(`[data-arrow="${id}"]`);
    if (arrow) {
      // Visible from the hold before to the end of the hold after, so the direction can be read.
      track(arrow.animate([{ opacity: 0 }, { opacity: 0.8, offset: 0.12 }, { opacity: 0.8, offset: 0.85 }, { opacity: 0 }],
        { duration: total, delay: startMs, fill: 'both' }));
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

// The card I saw turns face down once it has landed: the face narrows to nothing, then the back opens.
function turnFacesDown(el: HTMLElement, flight: number, total: number, delay: number) {
  const back = el.querySelector<HTMLElement>('[data-face="back"]');
  const front = el.querySelector<HTMLElement>('[data-face="front"]');
  const landed = flight / total;
  const closed = landed + (1 - landed) / 2;
  const options = { duration: total, delay, fill: 'both' as const };
  if (front) track(front.animate([{ transform: 'scaleX(1)' }, { transform: 'scaleX(1)', offset: landed }, { transform: 'scaleX(0)', offset: closed }, { transform: 'scaleX(0)' }], options));
  if (back) track(back.animate([{ transform: 'scaleX(0)' }, { transform: 'scaleX(0)', offset: closed }, { transform: 'scaleX(1)' }], options));
}

onBeforeUnmount(() => {
  for (const animation of running) { try { animation.cancel(); } catch { /* already over */ } }
  running.clear();
  for (const timer of markTimers) clearTimeout(timer);
  markTimers.clear();
  unders.value = [];
  for (const el of hidden.keys()) el.style.removeProperty('opacity');
  hidden.clear();
});

defineExpose({ play });
</script>

<template>
  <div ref="root" class="motion-layer" data-test="motion-layer" aria-hidden="true" :style="zIndex === undefined ? undefined : { zIndex }">
    <svg class="motion-arrows" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <marker :id="arrowId" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M0 0 L10 5 L0 10 z" fill="currentColor" />
        </marker>
      </defs>
      <line v-for="a in arrows" :key="a.id" class="motion-arrow" data-test="motion-arrow" :data-arrow="a.id"
            :x1="a.x1" :y1="a.y1" :x2="a.x2" :y2="a.y2" :marker-end="`url(#${arrowId})`" style="opacity: 0" />
    </svg>
    <div v-for="m in marks" :key="m.id" class="mark" data-test="motion-mark" :data-role="m.role"
         :data-still="m.still ? 'true' : undefined" :data-style="markStyle === 'soft' ? 'soft' : undefined"
         :style="{ left: m.rect.left - markPad + 'px', top: m.rect.top - markPad + 'px', width: m.rect.width + 2 * markPad + 'px', height: m.rect.height + 2 * markPad + 'px', animationDuration: m.lifeMs + 'ms' }"></div>
    <div v-for="u in unders" :key="u.id" class="under" data-test="motion-under"
         :style="{ left: u.rect.left + 'px', top: u.rect.top + 'px', width: u.rect.width + 'px', height: u.rect.height + 'px' }">
      <PlayingCard v-if="u.card" class="face" :rank="u.card.rank" :suit="u.card.suit" />
      <PlayingCard v-else class="face" :hidden="true" rank="ace" suit="spade" />
    </div>
    <div v-for="g in ghosts" :key="g.id" class="ghost" data-test="motion-card" :data-ghost="g.id"
         :data-kind="g.movement.kind" :style="{ width: g.from.width + 'px', height: g.from.height + 'px', transform: `translate(${g.from.left}px, ${g.from.top}px)`, transformOrigin: g.movement.landing?.scaleTo === 'centre' ? 'center' : undefined }">
      <template v-if="(g.movement.flip || g.movement.turnDown) && g.movement.card">
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
/* The ring of a card a move touched: an outline that glows and fades over its life; the source is dashed. Under
   reduced motion it does not animate: a still outline that the script removes after a shorter time. */
.mark {
  position: absolute;
  box-sizing: border-box;
  border: 3px solid #ffd23f;
  border-radius: 8px;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.55), 0 0 14px 3px rgba(255, 210, 63, 0.75);
  animation: mark-glow ease-out both;
}
.mark[data-role="from"] { border-style: dashed; box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.55), 0 0 10px 2px rgba(255, 210, 63, 0.5); }
.mark[data-still="true"] { animation: none; }
/* The soft contour (President): Kobo's touched-slot look, a thin gold line over a faint tint, hugging the card. No
   halo, no pop; it only fades in and out, and the source is dashed over no tint. */
.mark[data-style="soft"] {
  border: 1.5px solid color-mix(in srgb, var(--gold, #f5c842) 95%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--gold, #f5c842) 12%, transparent);
  box-shadow: none;
  animation-name: mark-fade;
}
.mark[data-style="soft"][data-role="from"] { border-style: dashed; background: none; box-shadow: none; }
.mark[data-style="soft"][data-still="true"] { animation: none; }
@keyframes mark-fade {
  0% { opacity: 0; }
  8% { opacity: 1; }
  70% { opacity: 1; }
  100% { opacity: 0; }
}
@keyframes mark-glow {
  0% { opacity: 0; transform: scale(1.12); }
  8% { opacity: 1; transform: scale(1); }
  70% { opacity: 1; }
  100% { opacity: 0; }
}
@media (prefers-reduced-motion: reduce) {
  .mark { animation: none; }
}
.under { position: absolute; }
.under :deep(.playing_card) { display: block; width: 100%; height: 100%; box-shadow: 0 3px 10px rgba(0, 0, 0, 0.45); border-radius: 6%; }
.ghost { position: absolute; left: 0; top: 0; transform-origin: top left; will-change: transform; }
.ghost :deep(.playing_card) { display: block; width: 100%; height: 100%; box-shadow: 0 3px 10px rgba(0, 0, 0, 0.45); border-radius: 6%; }
.ghost :deep(.playing_card[data-face]) { position: absolute; inset: 0; }
</style>
