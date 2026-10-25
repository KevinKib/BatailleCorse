<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef } from 'vue';
import { useI18n } from '../../composables/useI18n';
import type { MangolaCell } from '../../model/mangola/board';
import { NO_BOARD_MARKS, type BoardMarks } from '../../model/mangola/marks';
import type { HandPosition } from '../../model/mangola/replay';
import { SPEED_PROFILES } from '../../model/mangola/trace';
import type { AnimationSpeed } from '../../animation';
import MangolaPit from './MangolaPit.vue';

// The Mangola board, presentational only: the 4 x 8 grid exactly as the viewer sees it (`rows` comes from the
// store, own half at the bottom), the chevrons, the divider between the inner rows and the marks of the
// opponent's last move. It knows nothing of sessions: it emits `sow(pit)` and the table decides what to do.
//
// Size: eight columns share the board width and each pit button is its whole cell, with no gap, so the hit area
// is width / 8 by the same height (at least 44 px from 352 px of board; the board takes the screen width less
// 8 px). The visible pit is inset inside its cell.
const props = withDefaults(defineProps<{
  rows: MangolaCell[][];            // 4 rows of 8 cells, top to bottom
  interactive?: boolean;            // it is my turn: legal pits can be sown
  disabled?: boolean;               // game over or offline: readable, inert
  marks?: BoardMarks;               // the opponent's last move, until my move replaces it
  announcement?: string;            // read out by the live region (the last move in words)
  animating?: boolean;              // a sowing is being played: pits are inert, a tap skips it
  hand?: HandPosition | null;       // the travelling hand: the pit it is over and the seeds it holds
  activePit?: number | null;        // the pit the hand is over
  capturing?: readonly number[];    // pits whose seeds the hand is taking now
  chosenPit?: number | null;        // the pit the opponent chose, shown before the seeds leave it
  speed?: AnimationSpeed;           // the animation speed (the button is the table's, under the player plate)
}>(), {
  interactive: true,
  disabled: false,
  marks: () => NO_BOARD_MARKS,
  announcement: '',
  animating: false,
  hand: null,
  activePit: null,
  capturing: () => [],
  chosenPit: null,
  speed: 'normal',
});

const emit = defineEmits<{ sow: [pit: number]; skip: [] }>();

const ui = useI18n().mangolaUi;

const inert = computed(() => props.disabled || !props.interactive || props.animating);
const canSow = (cell: MangolaCell): boolean => !inert.value && cell.legal;
const capturedOf = (pit: number) => props.marks.captured.find(c => c.pit === pit);

// Row of the divider: between the two inner rows, i.e. the middle of the four screen rows.
const ROW_COUNT = 4;
const COLUMN_COUNT = 8;

// One tab stop for the whole board (roving tabindex): where focus last was, else the first pit to sow from, else
// the first pit of the bottom row.
const roving = ref<number | null>(null);
const flat = computed(() => props.rows.flat());
const tabStop = computed(() => {
  if (roving.value !== null && flat.value.some(c => c.pit === roving.value)) return roving.value;
  const first = flat.value.find(canSow);
  return first?.pit ?? props.rows[ROW_COUNT - 1]?.[0]?.pit ?? null;
});

const root = useTemplateRef<HTMLElement>('root');

async function focusPit(pit: number): Promise<void> {
  roving.value = pit;
  await nextTick();
  root.value?.querySelector<HTMLElement>(`[data-pit="${pit}"]`)?.focus();
}

const MOVES: Record<string, [number, number]> = {
  ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1],
};

function onKeydown(event: KeyboardEvent, cell: MangolaCell, r: number, c: number): void {
  const move = MOVES[event.key];
  if (move) {
    event.preventDefault();
    const row = Math.min(ROW_COUNT - 1, Math.max(0, r + move[0]));
    const col = Math.min(COLUMN_COUNT - 1, Math.max(0, c + move[1]));
    void focusPit(props.rows[row][col].pit);
    return;
  }
  if (event.key === 'Enter' || event.key === ' ') {
    // Handled here: a browser sends the click of Space on key-up.
    event.preventDefault();
    press(cell);
  }
}

function onKeyup(event: KeyboardEvent): void {
  if (event.key === ' ') event.preventDefault();
}

function press(cell: MangolaCell): void {
  if (props.animating) emit('skip');
  else if (canSow(cell)) emit('sow', cell.pit);
}

// A tap anywhere on the grid while the hand travels jumps to the end (a pit's own press does not sow meanwhile).
function onGridClick(): void {
  if (props.animating) emit('skip');
}

// The gold flash of a count that changes lasts as long as three hand slides: none at all when instant.
const boardStyle = computed(() => ({ '--mg-flash': `${SPEED_PROFILES[props.speed].handSlideMs * 3}ms` }));

// Where the hand is: the screen cell of its pit, as a share of the grid (8 columns, 4 rows).
const handStyle = computed(() => {
  if (!props.hand) return null;
  for (let r = 0; r < props.rows.length; r++) {
    const c = props.rows[r].findIndex(cell => cell.pit === props.hand!.pit);
    if (c >= 0) {
      return {
        left: `${(c * 100) / COLUMN_COUNT}%`,
        top: `${(r * 100) / ROW_COUNT}%`,
        '--mg-slide': `${SPEED_PROFILES[props.speed].handSlideMs}ms`,
      };
    }
  }
  return null;
});
</script>

<template>
  <div ref="root" class="mg-board" role="group" data-test="mangola-board" :aria-label="ui.board.label" :style="boardStyle">
    <p class="mg-board__legend mg-board__legend--top">{{ ui.board.opponentSide }}</p>
    <div class="mg-board__grid" @click="onGridClick">
      <div v-for="(row, r) in rows" :key="r" class="mg-board__row" data-test="board-row" :data-half="r < 2 ? 'top' : 'bottom'">
        <MangolaPit
          v-for="(cell, c) in row"
          :key="cell.pit"
          :name="cell.name"
          :count="cell.count"
          :arrow="cell.arrow"
          :mine="cell.mine"
          :selectable="canSow(cell)"
          :last-start="marks.start === cell.pit"
          :last-end="marks.end === cell.pit"
          :active="animating && activePit === cell.pit"
          :chosen="animating && chosenPit === cell.pit"
          :capturing="capturing.includes(cell.pit)"
          :captured="capturedOf(cell.pit) !== undefined"
          :captured-seeds="capturedOf(cell.pit)?.seeds ?? null"
          :tabbable="tabStop === cell.pit"
          :data-pit="cell.pit"
          :data-inner="cell.inner ? 'true' : undefined"
          @press="press(cell)"
          @keydown="onKeydown($event, cell, r, c)"
          @keyup="onKeyup"
          @focus="roving = cell.pit"
        />
      </div>
      <span class="mg-board__divider" data-test="inner-divider" aria-hidden="true"></span>
      <!-- The travelling hand: decoration only, the move is read out once by the live region below. -->
      <span v-if="hand && handStyle" class="mg-board__hand" data-test="sowing-hand" aria-hidden="true" :data-pit="hand.pit" :style="handStyle">
        <span class="mg-board__hand-seeds">{{ hand.seeds }}</span>
      </span>
    </div>
    <div class="mg-board__footer">
      <p class="mg-board__legend mg-board__legend--bottom">{{ ui.board.yourSide }}</p>
      <button v-if="animating" type="button" class="mg-board__skip" data-test="skip-sowing" @click="emit('skip')">{{ ui.board.skip }}</button>
    </div>
    <div class="mg-live" role="status" data-test="board-live">{{ announcement }}</div>
  </div>
</template>

<style scoped>
/* Tall enough for the 44 px Skip button, which then sits under the grid instead of over its last row. */
.mg-board__footer { position: relative; min-height: 44px; display: flex; align-items: center; justify-content: center; }
.mg-board__footer .mg-board__legend { margin: 0; }
.mg-board__skip {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  min-width: 44px;
  min-height: 44px;
  padding: 0 var(--space-3, 0.75rem);
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.55);
  color: inherit;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}
.mg-board__skip:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }

/* The hand covers exactly one cell and slides to the next one: it never takes a tap. */
.mg-board__hand {
  position: absolute;
  display: grid;
  place-items: start center; /* the top edge of the pit: the seed that lands in the bowl stays visible */
  width: 12.5%;
  height: 25%;
  pointer-events: none;
}
.mg-board__hand-seeds {
  display: grid;
  place-items: center;
  min-width: 1.5em;
  height: 1.5em;
  padding: 0 0.3em;
  border-radius: 999px;
  background: var(--gold);
  color: #1b1b1b;
  font-size: 0.8rem;
  font-weight: 700;
  line-height: 1;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.55);
  transform: translateY(-50%); /* centred on the edge of the cell */
}
@media (prefers-reduced-motion: no-preference) {
  .mg-board__hand { transition: left var(--mg-slide, 90ms) linear, top var(--mg-slide, 90ms) linear; }
}
.mg-board {
  /* The screen gutter is 4 px a side, so eight cells are 45.9 px wide at 375 px. */
  width: min(100%, 560px);
  margin: 0 auto;
}

.mg-board__grid {
  position: relative;
  display: grid;
  border-radius: 0.8rem;
  background: rgba(0, 0, 0, 0.25);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
}
.mg-board__row {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
}
/* The opponent's half is a little dimmer than mine. */
.mg-board__row[data-half='top'] { background: rgba(0, 0, 0, 0.12); }
.mg-board__row[data-half='top']:first-child { border-radius: 0.8rem 0.8rem 0 0; }
.mg-board__row[data-half='bottom']:last-of-type { border-radius: 0 0 0.8rem 0.8rem; }

/* The line between the two inner rows, which face each other. */
.mg-board__divider {
  position: absolute;
  left: 2%;
  right: 2%;
  top: 50%;
  height: 0;
  border-top: 2px solid rgba(var(--accent-active-rgb), 0.55);
  pointer-events: none;
}

.mg-board__legend {
  margin: var(--space-1) 0;
  font-size: 0.75rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  text-align: center;
  opacity: 0.75;
}

.mg-live {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
