<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import { seedDisplay, type Arrow } from '../../model/mangola/board';

// One pit of the Mangola board, presentational only: a real button whose whole grid cell is the hit area (the
// visible pit is inset inside it), exact seed dots up to eight, the written number above, a faint chevron for the
// direction a seed leaves it, and the marks of the opponent's last move. The board and the placement editor
// decide what a press means.
const props = withDefaults(defineProps<{
  name: string;                       // a1..h4
  count: number | null;               // null while the pit is hidden from me
  arrow: Arrow;                       // direction a seed leaves this pit, on screen
  mine?: boolean;                     // on my side of the board
  selectable?: boolean;               // a press is accepted (a legal pit, or an editable one)
  hint?: 'add' | 'remove' | null;     // editor: what a press does, said in the name
  lastStart?: boolean;                // the opponent's last move started here
  lastEnd?: boolean;                  // ... ended here
  captured?: boolean;                 // ... emptied this pit
  capturedSeeds?: number | null;      // seeds it took from it, when known
  tabbable?: boolean;                 // the one pit of the board that is a tab stop
  active?: boolean;                   // the travelling hand is over this pit (sowing animation)
  chosen?: boolean;                   // the opponent has just chosen this pit: the hand rests on it before the seeds leave
  capturing?: boolean;                // the hand is taking this pit's seeds (sowing animation)
}>(), {
  mine: false,
  selectable: false,
  hint: null,
  lastStart: false,
  lastEnd: false,
  captured: false,
  capturedSeeds: null,
  tabbable: true,
  active: false,
  chosen: false,
  capturing: false,
});

const emit = defineEmits<{ press: [] }>();

const ui = useI18n().mangolaUi;

// Dice-like places on a 3 x 3 grid (0..8, row by row): the same count always draws the same, and 7 and 8 differ.
const DOT_PLACES: number[][] = [
  [], [4], [0, 8], [0, 4, 8], [0, 2, 6, 8], [0, 2, 4, 6, 8], [0, 2, 3, 5, 6, 8], [0, 2, 3, 4, 5, 6, 8], [0, 1, 2, 3, 5, 6, 7, 8],
];

const display = computed(() => seedDisplay(props.count));
const dots = computed(() => (display.value.kind === 'dots' ? DOT_PLACES[display.value.count] : []));

const label = computed(() => {
  const words: string[] = [
    props.count === null ? ui.pit.hidden : plural(ui.pit.seeds, props.count),
  ];
  if (props.selectable && props.hint === null) words.push(ui.pit.canSow);
  if (props.hint === 'add') words.push(ui.pit.tapToAdd);
  if (props.hint === 'remove') words.push(ui.pit.tapToRemove);
  if (props.chosen) words.push(ui.pit.chosen);
  if (props.lastStart) words.push(ui.pit.lastStart);
  if (props.lastEnd) words.push(ui.pit.lastEnd);
  if (props.captured) {
    words.push(props.capturedSeeds === null ? ui.pit.capturedUnknown : plural(ui.pit.captured, props.capturedSeeds));
  }
  return format(ui.pit.label, { pit: props.name, state: words.join(', ') });
});

function press(): void {
  if (props.selectable) emit('press');
}
</script>

<template>
  <button
    type="button"
    class="mg-pit"
    :data-test="`pit-${name}`"
    :data-mine="mine ? 'true' : 'false'"
    :data-hidden="count === null ? 'true' : undefined"
    :data-selectable="selectable ? 'true' : undefined"
    :data-last-start="lastStart ? 'true' : undefined"
    :data-last-end="lastEnd ? 'true' : undefined"
    :data-captured="captured ? 'true' : undefined"
    :data-active="active ? 'true' : undefined"
    :data-chosen="chosen ? 'true' : undefined"
    :data-capturing="capturing ? 'true' : undefined"
    :aria-label="label"
    :aria-disabled="selectable ? undefined : 'true'"
    :tabindex="tabbable ? 0 : -1"
    @click="press"
  >
    <span class="mg-pit__bowl">
      <span v-if="display.kind === 'dots'" class="mg-pit__dots" aria-hidden="true">
        <span
          v-for="place in dots"
          :key="place"
          class="mg-pit__seed"
          data-test="seed"
          :style="{ gridRow: Math.floor(place / 3) + 1, gridColumn: (place % 3) + 1 }"
        ></span>
      </span>
      <span v-else-if="display.kind === 'number'" class="mg-pit__number" data-test="pit-number" aria-hidden="true">{{ display.count }}</span>
      <!-- Shown on hover and focus when the pit draws dots; the dots stay the exact count. -->
      <span v-if="display.kind === 'dots'" class="mg-pit__count" data-test="pit-count" aria-hidden="true">{{ display.count }}</span>
      <span
        v-if="captured && capturedSeeds !== null"
        class="mg-pit__taken"
        data-test="captured-seeds"
        aria-hidden="true"
      >-{{ capturedSeeds }}</span>
    </span>
    <svg class="mg-pit__chevron" data-test="chevron" :data-arrow="arrow" viewBox="0 0 10 10" aria-hidden="true" focusable="false">
      <path d="M3 1.5 L7 5 L3 8.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  </button>
</template>

<style scoped>
.mg-pit {
  /* The whole grid cell is the target; the board makes cells at least 44 px wide at 375 px. */
  position: relative;
  display: grid;
  place-items: center;
  min-width: 0;
  min-height: 44px;
  aspect-ratio: 1;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  font: inherit;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.mg-pit[aria-disabled='true'] { cursor: default; }

.mg-pit__bowl {
  position: relative;
  display: grid;
  place-items: center;
  width: 84%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, rgba(255, 255, 255, 0.1), rgba(0, 0, 0, 0.35));
  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.22);
}
.mg-pit[data-mine='true'] .mg-pit__bowl { box-shadow: inset 0 0 0 1.5px rgba(var(--accent-active-rgb), 0.45); }
.mg-pit[data-hidden='true'] .mg-pit__bowl { opacity: 0.45; border: 1.5px dashed rgba(255, 255, 255, 0.35); box-shadow: none; }
/* A pit I may act on: a solid gold ring (the name also says it can sow). */
.mg-pit[data-selectable='true'] .mg-pit__bowl {
  box-shadow: inset 0 0 0 2.5px var(--gold), 0 0 8px rgba(var(--accent-active-rgb), 0.5);
}

.mg-pit__dots {
  display: grid;
  grid-template: repeat(3, 1fr) / repeat(3, 1fr);
  place-items: center;
  width: 68%;
  height: 68%;
}
.mg-pit__seed {
  width: 78%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fffdf6, var(--gold-soft) 60%, var(--gold-deep));
}
.mg-pit__number {
  font-size: clamp(0.85rem, 4.2vw, 1.15rem);
  font-weight: 700;
  line-height: 1;
  color: var(--gold-soft);
}

.mg-pit__count {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 1.2em;
  padding: 0 0.25em;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.82);
  color: #fff;
  font-size: 0.7rem;
  line-height: 1.3;
  text-align: center;
  opacity: 0;
  pointer-events: none;
}
.mg-pit:hover .mg-pit__count,
.mg-pit:focus-visible .mg-pit__count { opacity: 1; }

.mg-pit:focus-visible { outline: 2px solid var(--gold); outline-offset: -2px; border-radius: 0.4rem; }

/* The opponent's last move: a solid ring on where it started and on where it ended, a dashed ring with -n on
   each pit it emptied. */
.mg-pit[data-last-start='true'] .mg-pit__bowl { outline: 2.5px solid #fff; outline-offset: 1px; }
.mg-pit[data-last-end='true'] .mg-pit__bowl { outline: 2.5px double #fff; outline-offset: 1px; }
.mg-pit[data-captured='true'] .mg-pit__bowl { outline: 2px dashed rgb(var(--accent-negative-rgb)); outline-offset: 1px; }
.mg-pit[data-active='true'] .mg-pit__bowl { box-shadow: inset 0 0 0 2.5px var(--gold), 0 0 10px rgba(var(--accent-active-rgb), 0.7); }
/* The pit the opponent chose: the same gold ring as an active pit, with a ring around it that pulses (a fixed ring
   under reduced motion), so the decision is seen before the seeds leave. */
.mg-pit[data-chosen='true'] .mg-pit__bowl {
  box-shadow: inset 0 0 0 2.5px var(--gold), 0 0 10px rgba(var(--accent-active-rgb), 0.7);
  outline: 3px solid var(--gold);
  outline-offset: 2px;
}
.mg-pit[data-capturing='true'] .mg-pit__bowl { outline: 2px dashed rgb(var(--accent-negative-rgb)); outline-offset: 1px; }
.mg-pit__taken {
  position: absolute;
  font-size: clamp(0.75rem, 3.6vw, 1rem);
  font-weight: 700;
  color: rgb(var(--accent-negative-rgb));
}

/* The path cue: faint, always visible, on the edge a seed leaves by. */
.mg-pit__chevron {
  position: absolute;
  width: 22%;
  height: 22%;
  color: #fff;
  opacity: 0.4;
  pointer-events: none;
}
.mg-pit__chevron[data-arrow='right'] { right: -1%; top: 39%; }
.mg-pit__chevron[data-arrow='left'] { left: -1%; top: 39%; transform: rotate(180deg); }
.mg-pit__chevron[data-arrow='down'] { bottom: -1%; left: 39%; transform: rotate(90deg); }
.mg-pit__chevron[data-arrow='up'] { top: -1%; left: 39%; transform: rotate(-90deg); }

@media (prefers-reduced-motion: no-preference) {
  .mg-pit__count { transition: opacity 120ms ease-out; }
  .mg-pit[data-chosen='true'] .mg-pit__bowl { animation: mg-chosen-pulse 700ms ease-in-out infinite; }
}
@keyframes mg-chosen-pulse {
  0%, 100% { outline-offset: 2px; outline-color: var(--gold); }
  50% { outline-offset: 5px; outline-color: rgba(255, 255, 255, 0.55); }
}
</style>
