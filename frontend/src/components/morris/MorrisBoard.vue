<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import {
  GEOMETRIES, neighbourByArrow, neighbourByPage, segmentsOnLine, type MorrisGeometry,
} from '../../model/threemenmorris/geometries';

// The Three Men's Morris board, presentational only: one real button per point of the variant's geometry (nine, or
// Picaria's thirteen), placed from its coordinates over a decorative SVG of its lines. Points are numbered row by row
// (0 1 2 / 3 4 5 / 6 7 8, the centre is 4), then Picaria's four crossings (9 to 12), like the backend. Picaria is the
// refined table: a plaque under the lines, the moves lit along the line from the selected piece, a mark on the pieces
// besides their colour, and a padlock on the point closed while placing. It knows nothing of sessions or stores: the
// table passes what to show and decides what an event means (place, select, move, deselect).
type Owner = 'white' | 'black' | null;

const props = withDefaults(defineProps<{
  cells: Owner[];                // who sits on each point
  geometry?: MorrisGeometry;     // where the points sit and what is drawn between them (Tapatan by default)
  selectable?: boolean[];        // points a click or Enter is accepted on (own movable pieces, free points, destinations)
  highlighted?: boolean[];       // points drawn as playable: free points, playable pieces, then the destinations
  selected?: number | null;      // the selected piece
  targets?: number[];            // the legal destinations of the selected piece (Picaria lights a path to each)
  locked?: number[];             // points shown as closed, with a padlock (Picaria's centre while placing)
  winningLine?: number[];        // the points of the winning line, held on screen at the end
  lastMove?: number[];           // the points of the opponent's last move, faintly ringed until the next state
  disabled?: boolean;            // game over or offline: readable but inert
  interactive?: boolean;         // false while it is not my turn: readable, nothing highlighted, inert
  announcement?: string;         // text read out by the live region (the last move)
}>(), {
  geometry: () => GEOMETRIES.tapatan,
  selectable: () => [],
  highlighted: () => [],
  selected: null,
  targets: () => [],
  locked: () => [],
  winningLine: () => [],
  lastMove: () => [],
  disabled: false,
  interactive: true,
  announcement: '',
});

const emit = defineEmits<{
  select: [cell: number];
  clear: [];
}>();

const ui = useI18n().morrisBoard;

const POINTS = computed(() => Array.from({ length: props.geometry.points.length }, (_, n) => n));
const refined = computed(() => props.geometry.refined);
const lineKey = (segment: readonly number[]): string => segment.join('-');
// A segment is drawn from its first point to its last: the three points of a line are aligned.
const lineEnds = (segment: readonly number[]) => {
  const { points } = props.geometry;
  const from = points[segment[0]];
  const to = points[segment[segment.length - 1]];
  return { x1: from.x, y1: from.y, x2: to.x, y2: to.y };
};
const pointStyle = (n: number) => {
  const { x, y } = props.geometry.points[n];
  return { left: `${x}%`, top: `${y}%` };
};
const isLastMove = (n: number): boolean => props.lastMove.includes(n);

const inert = computed(() => props.disabled || !props.interactive);
// The drawn segments on the winning line: its own segment on Tapatan and Achi, its edges cut at the crossings on Picaria.
const winningSegments = computed(() => new Set(segmentsOnLine(props.geometry, props.winningLine).map(lineKey)));
const isWinningPoint = (n: number): boolean => props.winningLine.includes(n);
const isCrossing = (n: number): boolean => props.geometry.crossings.includes(n);
const isLocked = (n: number): boolean => props.locked.includes(n) && !props.cells[n];

// The line from the selected piece to each of its destinations, lit on the refined board.
const litPaths = computed(() => {
  const from = props.selected;
  if (!refined.value || inert.value || from === null) return [];
  const { points } = props.geometry;
  return props.targets.map((to) => ({
    to, x1: points[from].x, y1: points[from].y, x2: points[to].x, y2: points[to].y,
  }));
});
const canAct = (n: number): boolean => !inert.value && props.selectable[n] === true;

function stateOf(n: number): 'selected' | 'highlighted' | 'idle' {
  if (props.selected === n) return 'selected';
  return !inert.value && props.highlighted[n] === true ? 'highlighted' : 'idle';
}

function labelOf(n: number): string {
  const owner = props.cells[n];
  const words = [owner === 'white' ? ui.white : owner === 'black' ? ui.black : ui.free];
  if (props.selected === n) words.push(ui.selected);
  if (canAct(n) && props.selected !== n) words.push(owner ? ui.canMove : ui.canPlace);
  if (isLocked(n)) words.push(ui.closed);
  return format(ui.pointLabel, { point: ui.pointNames[n], state: words.join(', ') });
}

// One tab stop for the whole board (roving tabindex): where focus last was, else the first point to act
// on, else the selected piece, else the centre.
const roving = ref<number | null>(null);
const tabStop = computed(() => {
  if (roving.value !== null) return roving.value;
  const first = POINTS.value.find((n) => canAct(n));
  return first ?? props.selected ?? 4;
});

const buttons = useTemplateRef<HTMLButtonElement[]>('buttons');

const ARROW_KEYS = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];
const PAGE_KEYS = ['PageUp', 'PageDown'];

async function focusPoint(n: number): Promise<void> {
  roving.value = n;
  await nextTick();
  buttons.value?.find((b) => b.dataset.test === `point-${n}`)?.focus();
}

function press(n: number): void {
  if (inert.value) return;
  if (canAct(n)) emit('select', n);
  else emit('clear');
}

function onKeydown(event: KeyboardEvent, n: number): void {
  if (ARROW_KEYS.includes(event.key)) {
    event.preventDefault();
    const next = neighbourByArrow(props.geometry, n, event.key);
    if (next !== null) void focusPoint(next);
    return;
  }
  if (PAGE_KEYS.includes(event.key)) {
    // The arrows never leave the grid for a crossing; these two keys visit every point in order.
    event.preventDefault();
    const next = neighbourByPage(props.geometry, n, event.key);
    if (next !== null) void focusPoint(next);
    return;
  }
  if (event.key === 'Enter' || event.key === ' ') {
    // Handled here rather than through the native click, which a browser sends on key-up for Space.
    event.preventDefault();
    press(n);
  } else if (event.key === 'Escape' && !inert.value) {
    emit('clear');
  }
}

// Space would still click on key-up in some browsers once key-down is handled.
function onKeyup(event: KeyboardEvent): void {
  if (event.key === ' ') event.preventDefault();
}

</script>

<template>
  <div
    class="morris-board"
    role="group"
    data-test="morris-board"
    :data-variant="geometry.key"
    :data-refined="refined ? 'true' : undefined"
    :aria-label="ui.boardLabel">
    <div v-if="refined" class="morris-plaque" data-test="plaque" aria-hidden="true"></div>
    <svg class="morris-lines" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
      <line
        v-for="segment in geometry.segments"
        :key="lineKey(segment)"
        class="morris-line"
        :class="{ 'morris-line--winning': winningSegments.has(lineKey(segment)) }"
        :data-line="lineKey(segment)"
        :data-winning="winningSegments.has(lineKey(segment)) ? 'true' : undefined"
        v-bind="lineEnds(segment)"
      />
    </svg>
    <svg v-if="litPaths.length" class="morris-lines" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
      <line
        v-for="path in litPaths"
        :key="path.to"
        class="morris-path"
        data-test="lit-path"
        :data-to="path.to"
        :x1="path.x1" :y1="path.y1" :x2="path.x2" :y2="path.y2"
      />
    </svg>
    <div class="morris-grid">
      <button
        v-for="n in POINTS"
        :key="n"
        ref="buttons"
        type="button"
        class="morris-point"
        :style="pointStyle(n)"
        :data-test="`point-${n}`"
        :data-owner="cells[n] ?? 'none'"
        :data-state="stateOf(n)"
        :data-crossing="isCrossing(n) ? 'true' : undefined"
        :data-locked="isLocked(n) ? 'true' : undefined"
        :data-winning="isWinningPoint(n) ? 'true' : undefined"
        :data-last="isLastMove(n) ? 'true' : undefined"
        :aria-label="labelOf(n)"
        :aria-pressed="selected === n ? 'true' : 'false'"
        :aria-disabled="inert ? 'true' : undefined"
        :tabindex="tabStop === n ? 0 : -1"
        @click="press(n)"
        @keydown="onKeydown($event, n)"
        @keyup="onKeyup"
        @focus="roving = n"
      >
        <template v-if="isLocked(n)">
          <span class="morris-lock-ring" aria-hidden="true"></span>
          <svg class="morris-lock" data-test="lock" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            <rect x="5" y="11" width="14" height="10" rx="2" />
            <path d="M8 11V8a4 4 0 0 1 8 0v3" />
          </svg>
        </template>
        <Transition name="piece">
          <span v-if="cells[n]" class="morris-piece" :class="`morris-piece--${cells[n]}`" data-test="piece">
            <span
              v-if="refined"
              class="morris-piece-mark"
              data-test="piece-mark"
              :data-mark="cells[n] === 'white' ? 'ring' : 'diamond'"></span>
          </span>
        </Transition>
      </button>
    </div>
    <div class="morris-live" role="status" data-test="board-live">{{ announcement }}</div>
  </div>
</template>

<style scoped>
.morris-board {
  /* Piece colours: ivory and ebony read on the green felt and on its dark rim alike. */
  --morris-ivory: #f4ecd8;
  --morris-ivory-edge: #bfae85;
  --morris-ebony: #231f1c;
  --morris-ebony-edge: rgba(255, 255, 255, 0.4);
  --morris-line: rgba(var(--accent-active-rgb), 0.5);
  --morris-hold: 1.2s;
  position: relative;
  /* The screen lowers --morris-board-vh to make room for the draw bar. */
  width: min(88vw, var(--morris-board-vh, 52vh), 480px);
  aspect-ratio: 1;
  flex: none;
}

.morris-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}
.morris-line {
  stroke: var(--morris-line);
  stroke-width: 1.4;
  stroke-linecap: round;
}
.morris-line--winning {
  stroke: var(--gold);
  stroke-width: 3;
  filter: drop-shadow(0 0 3px rgba(var(--accent-active-rgb), 0.8));
  animation: line-glow var(--morris-hold) ease-out;
}
@keyframes line-glow {
  from { stroke-width: 1.4; opacity: 0.4; }
  to { stroke-width: 3; opacity: 1; }
}

.morris-grid {
  position: absolute;
  inset: 0;
}

.morris-point {
  /* Placed from the geometry: the centre of the button sits on the point. A third of the board wide on the
     nine-point boards; Picaria's thirteen points narrow it (see below). */
  position: absolute;
  width: 33.333%;
  height: 33.333%;
  transform: translate(-50%, -50%);
  display: grid;
  place-items: center;
  min-width: 44px;
  min-height: 44px;
  padding: 0;
  border: 0;
  background: none;
  border-radius: 50%;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.morris-point[aria-disabled='true'] { cursor: default; }

/* The point itself: a small gold dot where a line crosses, a soft ring when it can be played. */
.morris-point::before {
  content: '';
  position: absolute;
  width: 16%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--gold-soft);
  opacity: 0.7;
}
.morris-point[data-owner='white']::before,
.morris-point[data-owner='black']::before { display: none; }
.morris-point[data-state='highlighted']::before {
  width: 46%;
  background: rgba(var(--accent-active-rgb), 0.18);
  box-shadow: inset 0 0 0 2px rgba(var(--accent-active-rgb), 0.85);
  opacity: 1;
}
.morris-point[data-owner='white'][data-state='highlighted']::before,
.morris-point[data-owner='black'][data-state='highlighted']::before {
  display: block;
  width: 68%;
  background: none;
}
.morris-point[data-state='selected']::before {
  display: block;
  width: 74%;
  background: none;
  box-shadow: 0 0 0 3px var(--gold), 0 0 14px rgba(var(--accent-active-rgb), 0.7);
  opacity: 1;
}
/* The opponent's last move: a faint ring on where the piece came from and where it went. */
.morris-point[data-last='true']::after {
  content: '';
  position: absolute;
  width: 74%;
  aspect-ratio: 1;
  border-radius: 50%;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.45);
  pointer-events: none;
}
.morris-point:focus-visible { outline: 2px solid var(--gold); outline-offset: -2px; }

.morris-piece {
  position: relative;
  width: 58%;
  aspect-ratio: 1;
  border-radius: 50%;
  box-shadow: var(--card-shadow);
}
.morris-piece--white {
  background: radial-gradient(circle at 35% 30%, #fffdf6 0%, var(--morris-ivory) 55%, var(--morris-ivory-edge) 100%);
}
.morris-piece--black {
  background: radial-gradient(circle at 35% 30%, #4a433d 0%, var(--morris-ebony) 60%, #0d0b0a 100%);
  box-shadow: var(--card-shadow), inset 0 0 0 1.5px var(--morris-ebony-edge);
}
.morris-point[data-winning='true'] .morris-piece {
  box-shadow: var(--card-shadow), 0 0 0 3px var(--gold), 0 0 16px rgba(var(--accent-active-rgb), 0.7);
}

/* A newly placed piece fades and grows in; a piece present at mount, or one that moves, just appears. */
.piece-enter-active { transition: opacity 150ms ease-out, transform 150ms ease-out; }
.piece-enter-from { opacity: 0; transform: scale(0.6); }

/* Picaria, the refined table. Thirteen points leave 23.57% of the board between the closest two, so a point is a
   circle of 23% of the board (at least 44 px from a board of 191 px) and a piece 18%. Sizes below are percentages
   of that circle: 14.8% is a 3.4% dot, 56.5% a 13% ring, 78.3% an 18% piece. */
.morris-plaque {
  position: absolute;
  inset: 4%;
  border-radius: 6%;
  background: rgba(0, 0, 0, 0.28);
  box-shadow: inset 0 0 0 1px rgba(var(--accent-active-rgb), 0.24), inset 0 2px 14px rgba(0, 0, 0, 0.4);
  pointer-events: none;
}
.morris-board[data-refined='true'] .morris-line:not(.morris-line--winning) {
  stroke: rgba(var(--accent-active-rgb), 0.62);
  stroke-width: 1;
}
/* The move from the selected piece to a destination, lit along its line. */
.morris-path {
  stroke: var(--gold);
  stroke-width: 2.4;
  stroke-linecap: round;
  opacity: 0.95;
}
.morris-board[data-refined='true'] .morris-point {
  width: 23%;
  height: 23%;
}
.morris-board[data-refined='true'] .morris-point::before { width: 14.8%; }
/* A crossing is a small diamond, a grid point a dot. */
.morris-board[data-refined='true'] .morris-point[data-crossing='true']::before {
  width: 13.6%;
  border-radius: 1px;
  transform: rotate(45deg);
  background: var(--gold-soft);
  opacity: 0.85;
}
.morris-board[data-refined='true'] .morris-point[data-state='highlighted']::before {
  width: 56.5%;
  border-radius: 50%;
  transform: none;
  background: rgba(var(--accent-active-rgb), 0.18);
  opacity: 1;
}
.morris-board[data-refined='true'] .morris-point[data-owner='white'][data-state='highlighted']::before,
.morris-board[data-refined='true'] .morris-point[data-owner='black'][data-state='highlighted']::before {
  width: 93.5%;
  background: none;
}
.morris-board[data-refined='true'] .morris-point[data-state='selected']::before {
  width: 97.8%;
  border-radius: 50%;
  transform: none;
  background: none;
  opacity: 1;
}
.morris-board[data-refined='true'] .morris-point[data-last='true']::after { width: 89%; }
.morris-board[data-refined='true'] .morris-piece { width: 78.3%; }

/* Besides their colour, White has an engraved ring and Black a small light diamond. */
.morris-piece-mark {
  position: absolute;
  left: 50%;
  top: 50%;
  box-sizing: border-box;
}
.morris-piece-mark[data-mark='ring'] {
  width: 46%;
  height: 46%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  border: 2px solid rgba(120, 96, 44, 0.6);
}
.morris-piece-mark[data-mark='diamond'] {
  width: 24%;
  height: 24%;
  transform: translate(-50%, -50%) rotate(45deg);
  border-radius: 1px;
  background: rgba(244, 236, 216, 0.78);
}

/* A point closed while pieces are placed: a dashed ring and a padlock instead of the dot. */
.morris-point[data-locked='true']::before { display: none; }
.morris-lock-ring {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 52%;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  box-sizing: border-box;
  border: 1.5px dashed rgba(255, 255, 255, 0.42);
  pointer-events: none;
}
.morris-lock {
  position: absolute;
  left: 50%;
  top: 50%;
  width: max(14px, 21.7%);
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  fill: none;
  stroke: rgba(255, 255, 255, 0.7);
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
  pointer-events: none;
}

.morris-live {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}

@media (prefers-reduced-motion: reduce) {
  .piece-enter-active { transition: none; }
  .morris-line--winning { animation: none; }
}
</style>
