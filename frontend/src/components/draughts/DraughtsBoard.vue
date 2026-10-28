<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { useDraughtsGlide } from '../../composables/useDraughtsGlide';
import { format } from '../../locales/format';
import DraughtsPiece from './DraughtsPiece.vue';
import { pieceKindOf, type PieceKind } from './pieceKind';
import { moveFocus, squareAt, squareCoords, separatingSquares, type DraughtsOrientation, type FocusKey } from '../../model/draughts/board';
import { INSTANT_TEMPO, type DraughtsTempo } from '../../model/draughts/tempo';
import type {
  DraughtsBoard, DraughtsLegalMove, DraughtsMoveEventData,
} from '../../model/draughts/DraughtsState';

// The draughts board, presentational only: 50 real buttons on the dark squares of a 10 x 10 grid, the pieces drawn in
// them, and what the table asks to show (selectable pieces, destinations, the path and the pieces a capture would
// take, the chooser's numbered routes, the last move). It knows no rule and no store: a press is handed up as the
// square, and the table decides what it means. The pieces keep the squares' numbers of the FMJD (1 at the top-left
// for White, 50 for Black, whose board is turned by 180 degrees).
const props = withDefaults(defineProps<{
  board: DraughtsBoard;
  orientation: DraughtsOrientation;
  selectable?: number[];                 // the pieces I may pick now
  selected?: number | null;              // the piece I picked
  targets?: number[];                    // where the selected piece may go
  moves?: DraughtsLegalMove[];           // my legal moves: the preview reads the path and the captures from them
  choice?: DraughtsLegalMove[] | null;   // the candidates of an ambiguous destination, while the chooser is open
  lastMove?: { from: number; to: number } | null;  // marked until the next move
  motion?: DraughtsMoveEventData | null; // a move to play out (the opponent's), a new object per move
  tempo?: DraughtsTempo;                 // how long it takes; zero shows the result at once
  disabled?: boolean;                    // game over or offline: readable but inert
  interactive?: boolean;                 // false while it is not my turn: readable, inert
  announcement?: string;                 // read out by the live region
}>(), {
  selectable: () => [],
  selected: null,
  targets: () => [],
  moves: () => [],
  choice: null,
  lastMove: null,
  motion: null,
  tempo: () => INSTANT_TEMPO,
  disabled: false,
  interactive: true,
  announcement: '',
});

const emit = defineEmits<{
  select: [square: number];
  clear: [];
}>();

const ui = useI18n().draughtsUi.board;

const inert = computed(() => props.disabled || !props.interactive);

interface Cell { square: number; row: number; col: number }
// The dark squares in reading order of the visible board: the DOM order is the order Tab and screen readers follow.
const cells = computed<Cell[]>(() => {
  const out: Cell[] = [];
  for (let row = 0; row < 10; row++) {
    for (let col = 0; col < 10; col++) {
      const square = squareAt(row, col, props.orientation);
      if (square !== null) out.push({ square, row, col });
    }
  }
  return out;
});

// The final position, minus the arrival of a piece still on its way.
const { glide, arrival, ghosts } = useDraughtsGlide({
  board: () => props.board,
  motion: () => props.motion,
  tempo: () => props.tempo,
});
const kindAt = (square: number): PieceKind | null => {
  if (square === arrival.value) return null;
  const code = props.board[square - 1];
  return code ? pieceKindOf(code) : null;
};

// The routes of the chooser: a number on each piece that belongs to one candidate only.
const routeOf = computed(() => separatingSquares(props.choice ?? []));
const choosing = computed(() => (props.choice?.length ?? 0) > 0);

const hovered = ref<number | null>(null);
// What the path preview draws: the candidates of the open chooser, else those of the destination pointed at.
const preview = computed<DraughtsLegalMove[]>(() => {
  if (inert.value) return [];
  if (props.choice && props.choice.length > 0) return props.choice;
  if (props.selected === null || hovered.value === null || !props.targets.includes(hovered.value)) return [];
  return props.moves.filter(m => m.from === props.selected && m.to === hovered.value);
});
const takenSquares = computed(() => new Set(preview.value.flatMap(m => m.captures)));

const centre = (square: number): string => {
  const { row, col } = squareCoords(square, props.orientation);
  return `${col * 10 + 5},${row * 10 + 5}`;
};
const pathPoints = (move: DraughtsLegalMove | undefined): string =>
  move ? [move.from, ...move.path].map(centre).join(' ') : '';
const pathA = computed(() => pathPoints(preview.value[0]));
const pathB = computed(() => pathPoints(preview.value[1]));
const landings = computed(() => preview.value.slice(0, 2).flatMap(m => m.path.slice(0, -1)));
const landingAt = (square: number) => {
  const { row, col } = squareCoords(square, props.orientation);
  return { cx: col * 10 + 5, cy: row * 10 + 5 };
};

const isSelectable = (square: number) => !inert.value && props.selectable.includes(square);
const isTarget = (square: number) => !inert.value && props.targets.includes(square);
const isRoute = (square: number) => !inert.value && choosing.value && routeOf.value.has(square);
// A square answers to a press when it is a piece to pick, a destination, or a numbered piece of the chooser.
const isActionable = (square: number) => isSelectable(square) || isTarget(square) || isRoute(square) || props.selected === square;
const isLast = (square: number) => props.lastMove !== null && (props.lastMove.from === square || props.lastMove.to === square);
const isTaken = (square: number) => takenSquares.value.has(square);

const WORDS: Record<PieceKind, () => string> = {
  'white-man': () => ui.whiteMan,
  'white-king': () => ui.whiteKing,
  'black-man': () => ui.blackMan,
  'black-king': () => ui.blackKing,
};

function labelOf(square: number): string {
  const kind = kindAt(square);
  const words = [kind ? WORDS[kind]() : ui.empty];
  if (props.selected === square) words.push(ui.selected);
  else if (isSelectable(square)) words.push(ui.canMove);
  if (isTarget(square)) words.push(ui.destination);
  if (isTaken(square)) words.push(ui.taken);
  if (isRoute(square)) words.push(format(ui.route, { n: (routeOf.value.get(square) ?? 0) + 1 }));
  if (isLast(square)) words.push(ui.lastMove);
  return format(ui.squareLabel, { square, state: words.join(', ') });
}

function stateOf(square: number): 'selected' | 'target' | 'movable' | 'idle' {
  if (props.selected === square) return 'selected';
  if (isTarget(square)) return 'target';
  return isSelectable(square) ? 'movable' : 'idle';
}

// One tab stop for the whole board (roving tabindex): where focus last was, else the first piece to pick, else the
// selected piece, else the first square.
const roving = ref<number | null>(null);
const tabStop = computed(() => {
  if (roving.value !== null) return roving.value;
  const first = cells.value.find(c => isSelectable(c.square));
  return first?.square ?? props.selected ?? cells.value[0].square;
});
const buttons = useTemplateRef<HTMLButtonElement[]>('buttons');

async function focusSquare(square: number): Promise<void> {
  roving.value = square;
  await nextTick();
  buttons.value?.find(b => b.dataset.test === `square-${square}`)?.focus();
}

function press(square: number): void {
  if (!inert.value) emit('select', square);
}

const ARROWS: readonly string[] = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];
function onKeydown(event: KeyboardEvent, square: number): void {
  if (ARROWS.includes(event.key)) {
    event.preventDefault();
    void focusSquare(moveFocus(square, event.key as FocusKey, props.orientation));
    return;
  }
  if (event.key === 'Enter' || event.key === ' ') {
    // Handled here rather than through the native click, which a browser sends on key-up for Space.
    event.preventDefault();
    press(square);
  } else if (event.key === 'Escape' && !inert.value) {
    emit('clear');
  }
}
// Space would still click on key-up in some browsers once key-down is handled.
function onKeyup(event: KeyboardEvent): void {
  if (event.key === ' ') event.preventDefault();
}

const slotStyle = (square: number) => {
  const { row, col } = squareCoords(square, props.orientation);
  return { transform: `translate(${col * 100}%, ${row * 100}%)` };
};
const glideStyle = computed(() => ({
  ...(glide.value ? slotStyle(glide.value.square) : {}),
  transitionDuration: `${props.tempo.legMs}ms`,
}));
const ghostStyle = (square: number) => ({
  ...slotStyle(square),
  transitionDuration: `${props.tempo.removalMs}ms`,
});
</script>

<template>
  <div class="draughts-board" role="group" data-test="draughts-board" :aria-label="ui.label">
    <div class="board-grid">
      <button
        v-for="cell in cells"
        :key="cell.square"
        ref="buttons"
        type="button"
        class="square"
        :style="{ gridRow: cell.row + 1, gridColumn: cell.col + 1 }"
        :data-test="`square-${cell.square}`"
        :data-row="cell.row"
        :data-col="cell.col"
        :data-piece="kindAt(cell.square) ?? undefined"
        :data-state="stateOf(cell.square)"
        :data-target="isTarget(cell.square) ? 'true' : undefined"
        :data-taken="isTaken(cell.square) ? 'true' : undefined"
        :data-last="isLast(cell.square) ? 'true' : undefined"
        :aria-label="labelOf(cell.square)"
        :aria-pressed="selected === cell.square ? 'true' : 'false'"
        :aria-disabled="isActionable(cell.square) ? undefined : 'true'"
        :tabindex="tabStop === cell.square ? 0 : -1"
        @click="press(cell.square)"
        @keydown="onKeydown($event, cell.square)"
        @keyup="onKeyup"
        @focus="roving = cell.square; hovered = cell.square"
        @blur="hovered = null"
        @mouseenter="hovered = cell.square"
        @mouseleave="hovered = null">
        <span v-if="isLast(cell.square)" class="last-tint" aria-hidden="true"></span>
        <span v-if="isTarget(cell.square)" class="target-dot" data-test="target-dot" aria-hidden="true"></span>
        <DraughtsPiece v-if="kindAt(cell.square)" :kind="kindAt(cell.square)!" />
        <span
          v-if="isSelectable(cell.square) || selected === cell.square"
          class="piece-ring"
          :class="{ 'piece-ring--selected': selected === cell.square }"
          aria-hidden="true"></span>
        <span v-if="isTaken(cell.square)" class="taken-mark" data-test="taken-mark" aria-hidden="true">
          <svg viewBox="0 0 20 20" focusable="false">
            <path class="taken-halo" d="M5 5 L15 15 M15 5 L5 15" />
            <path class="taken-cross" d="M5 5 L15 15 M15 5 L5 15" />
          </svg>
        </span>
        <span v-if="isRoute(cell.square)" class="route-badge" data-test="route-badge" aria-hidden="true">{{ (routeOf.get(cell.square) ?? 0) + 1 }}</span>
      </button>
    </div>

    <svg v-if="preview.length > 0" class="paths" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
      <polyline v-if="pathB" class="path path--b" data-test="path-b" :points="pathB" />
      <polyline class="path path--a" data-test="path-a" :points="pathA" />
      <circle v-for="square in landings" :key="square" class="landing" r="1.9" v-bind="landingAt(square)" />
    </svg>

    <!-- Pieces in motion: the one gliding along its path, and the captured pieces that wait for their removal. -->
    <div class="motion-layer" aria-hidden="true">
      <div
        v-for="ghost in ghosts"
        :key="ghost.square"
        class="slot ghost"
        :class="{ 'ghost--fading': ghost.fading }"
        :style="ghostStyle(ghost.square)"
        :data-test="`ghost-${ghost.square}`">
        <DraughtsPiece :kind="pieceKindOf(ghost.code)" />
      </div>
      <div
        v-if="glide"
        class="slot glide"
        :style="glideStyle"
        data-test="glide-piece"
        :data-square="glide.square">
        <DraughtsPiece :kind="pieceKindOf(glide.code)" />
      </div>
    </div>

    <div class="live" role="status" data-test="board-live">{{ announcement }}</div>
  </div>
</template>

<style scoped>
.draughts-board {
  --light: #2c6a3e;
  --dark: #10331c;
  --gold-line: #f5c842;
  position: relative;
  /* The table sets --draughts-board-size from the room it has; the fallback keeps a loose board on the screen. */
  width: var(--draughts-board-size, min(92vw, 52vh, 640px));
  aspect-ratio: 1;
  flex: none;
  box-sizing: content-box;
  padding: 2px;
  border-radius: 10px;
  background: #07160d;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.06), 0 10px 40px rgba(0, 0, 0, 0.45);
}

.board-grid {
  position: absolute;
  inset: 2px;
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  grid-template-rows: repeat(10, 1fr);
  overflow: hidden;
  border-radius: 4px;
  /* The light squares are the board itself; the buttons are the dark ones. */
  background: var(--light);
}

.square {
  position: relative;
  display: grid;
  place-items: center;
  min-width: 0;
  min-height: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: var(--dark);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.square[aria-disabled='true'] { cursor: default; }
.square:focus-visible { outline: 3px solid var(--gold-line); outline-offset: -3px; z-index: 2; }

.last-tint {
  position: absolute;
  inset: 0;
  background: rgba(245, 200, 66, 0.3);
  pointer-events: none;
}
.target-dot {
  position: absolute;
  width: 30%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--gold-line);
  box-shadow: 0 0 0 3px rgba(245, 200, 66, 0.3), 0 0 0 1px #141413;
  pointer-events: none;
}
.piece-ring {
  position: absolute;
  width: 92%;
  aspect-ratio: 1;
  box-sizing: border-box;
  border-radius: 50%;
  border: 2px solid var(--gold-line);
  pointer-events: none;
}
.piece-ring--selected { border-width: 3px; box-shadow: 0 0 10px rgba(245, 200, 66, 0.75); }

.taken-mark { position: absolute; inset: 0; pointer-events: none; }
.taken-mark svg { width: 100%; height: 100%; }
.taken-halo { stroke: #141413; stroke-width: 5; stroke-linecap: round; fill: none; }
.taken-cross { stroke: #f87171; stroke-width: 2.6; stroke-linecap: round; fill: none; }

.route-badge {
  position: absolute;
  right: -2px;
  top: -2px;
  z-index: 1;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  box-sizing: border-box;
  border-radius: 8px;
  background: var(--gold-line);
  color: #141413;
  font-size: 11px;
  font-weight: 700;
  line-height: 16px;
  text-align: center;
  box-shadow: 0 0 0 1.5px #141413;
  pointer-events: none;
}

.paths {
  position: absolute;
  inset: 2px;
  width: calc(100% - 4px);
  height: calc(100% - 4px);
  pointer-events: none;
}
.path { fill: none; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.1; }
.path--a { stroke: var(--gold-line); }
.path--b { stroke: #e8c96d; stroke-opacity: 0.75; stroke-dasharray: 2.6 2.2; }
.landing { fill: rgba(20, 20, 20, 0.35); stroke: var(--gold-line); stroke-width: 0.6; }

.motion-layer { position: absolute; inset: 2px; pointer-events: none; }
.slot {
  position: absolute;
  left: 0;
  top: 0;
  width: 10%;
  height: 10%;
  display: grid;
  place-items: center;
}
.glide { z-index: 3; transition-property: transform; transition-timing-function: linear; }
.ghost { z-index: 1; transition-property: opacity; transition-timing-function: ease-out; }
.ghost--fading { opacity: 0; }

.live {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

@media (prefers-reduced-motion: reduce) {
  .glide, .ghost { transition: none; }
}
</style>
