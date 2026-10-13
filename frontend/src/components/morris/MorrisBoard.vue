<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';

// The Three Men's Morris board, presentational only: nine real buttons on a CSS grid over a decorative
// SVG of the eight lines. Points are numbered row by row (0 1 2 / 3 4 5 / 6 7 8, the centre is 4), like
// the backend. It knows nothing of sessions or stores: the table passes what to show and decides what an
// event means (place, select, move, deselect).
type Owner = 'white' | 'black' | null;

const props = withDefaults(defineProps<{
  cells: Owner[];                // who sits on each point
  selectable?: boolean[];        // points a click or Enter is accepted on (own movable pieces, free points, destinations)
  highlighted?: boolean[];       // points drawn as playable: free points, playable pieces, then the destinations
  selected?: number | null;      // the selected piece
  winningLine?: number[];        // the three points of the winning line, held on screen at the end
  disabled?: boolean;            // game over or offline: readable but inert
  interactive?: boolean;         // false while it is not my turn: readable, nothing highlighted, inert
  announcement?: string;         // text read out by the live region (the last move)
}>(), {
  selectable: () => [],
  highlighted: () => [],
  selected: null,
  winningLine: () => [],
  disabled: false,
  interactive: true,
  announcement: '',
});

const emit = defineEmits<{
  select: [cell: number];
  clear: [];
}>();

const ui = useI18n().morrisBoard;

const POINTS = Array.from({ length: 9 }, (_, n) => n);
// The 8 lines of board B: three rows, three columns and the two diagonals through the centre.
const LINES: number[][] = [
  [0, 1, 2], [3, 4, 5], [6, 7, 8],
  [0, 3, 6], [1, 4, 7], [2, 5, 8],
  [0, 4, 8], [2, 4, 6],
];
const centre = (n: number): number => (n * 2 + 1) * (100 / 6);
const lineKey = (line: number[]): string => line.join('-');

const inert = computed(() => props.disabled || !props.interactive);
const winning = computed(() => props.winningLine.join('-'));
const isWinningPoint = (n: number): boolean => props.winningLine.includes(n);
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
  return format(ui.pointLabel, { point: ui.pointNames[n], state: words.join(', ') });
}

// One tab stop for the whole board (roving tabindex): where focus last was, else the first point to act
// on, else the selected piece, else the centre.
const roving = ref<number | null>(null);
const tabStop = computed(() => {
  if (roving.value !== null) return roving.value;
  const first = POINTS.find((n) => canAct(n));
  return first ?? props.selected ?? 4;
});

const buttons = useTemplateRef<HTMLButtonElement[]>('buttons');

const ARROWS: Record<string, [number, number]> = {
  ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1],
};

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
  const arrow = ARROWS[event.key];
  if (arrow) {
    event.preventDefault();
    const row = Math.floor(n / 3) + arrow[0];
    const col = (n % 3) + arrow[1];
    if (row >= 0 && row < 3 && col >= 0 && col < 3) void focusPoint(row * 3 + col);
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
  <div class="morris-board" role="group" data-test="morris-board" :aria-label="ui.boardLabel">
    <svg class="morris-lines" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
      <line
        v-for="line in LINES"
        :key="lineKey(line)"
        class="morris-line"
        :class="{ 'morris-line--winning': winning === lineKey(line) }"
        :data-line="lineKey(line)"
        :data-winning="winning === lineKey(line) ? 'true' : undefined"
        :x1="centre(line[0] % 3)"
        :y1="centre(Math.floor(line[0] / 3))"
        :x2="centre(line[2] % 3)"
        :y2="centre(Math.floor(line[2] / 3))"
      />
    </svg>
    <div class="morris-grid">
      <button
        v-for="n in POINTS"
        :key="n"
        ref="buttons"
        type="button"
        class="morris-point"
        :data-test="`point-${n}`"
        :data-owner="cells[n] ?? 'none'"
        :data-state="stateOf(n)"
        :data-winning="isWinningPoint(n) ? 'true' : undefined"
        :aria-label="labelOf(n)"
        :aria-pressed="selected === n ? 'true' : 'false'"
        :aria-disabled="inert ? 'true' : undefined"
        :tabindex="tabStop === n ? 0 : -1"
        @click="press(n)"
        @keydown="onKeydown($event, n)"
        @keyup="onKeyup"
        @focus="roving = n"
      >
        <Transition name="piece">
          <span v-if="cells[n]" class="morris-piece" :class="`morris-piece--${cells[n]}`" data-test="piece"></span>
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
  width: min(88vw, 52vh, 480px);
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
  display: grid;
  grid-template: repeat(3, 1fr) / repeat(3, 1fr);
}

.morris-point {
  position: relative;
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
