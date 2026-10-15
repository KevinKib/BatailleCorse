<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import type { MangolaCell } from '../../model/mangola/board';
import { loopOf } from '../../model/mangola/geometry';
import type { MangolaSide } from '../../model/mangola/MangolaState';
import MangolaPit from './MangolaPit.vue';

// The placement editor, presentational only: the viewer lays out 32 seeds on their half (tap a pit to add a seed,
// the Remove toggle to take one back), the opponent's half is a greyed blank with "Placing..." then "Ready".
// `rows` is the board as the viewer sees it (own half at the bottom), `draft` the 16 counts in loop order. It
// counts nothing itself beyond what it is given; the store owns the draft and the server re-checks it.
export type PlacementPreset = 'checkerboard' | 'two-per-pit' | 'random' | 'clear';

const props = withDefaults(defineProps<{
  rows: MangolaCell[][];            // 4 rows of 8 cells, the viewer's half in rows 2 and 3
  side: MangolaSide;                // the viewer's side: orders the loop of the draft
  draft: number[];                  // my layout, 16 counts in loop order
  seedsLeft: number;                // 32 minus the sum of the draft
  canConfirm: boolean;              // 0 seeds left and still editable
  ready?: boolean;                  // I confirmed
  opponentReady?: boolean;
  opponentName?: string | null;
  draftOnlyEndless?: boolean;       // the draft has no finite move on an otherwise empty board
  error?: string | null;            // the refusal of my confirmation, already in words
}>(), {
  ready: false,
  opponentReady: false,
  opponentName: null,
  draftOnlyEndless: false,
  error: null,
});

const emit = defineEmits<{
  add: [index: number];
  remove: [index: number];
  preset: [preset: PlacementPreset];
  confirm: [];
  edit: [];
}>();

const ui = useI18n().mangolaUi;
const text = ui.placement;

const PRESETS: { key: PlacementPreset; label: string }[] = [
  { key: 'checkerboard', label: text.presets.checkerboard },
  { key: 'two-per-pit', label: text.presets.twoPerPit },
  { key: 'random', label: text.presets.random },
  { key: 'clear', label: text.presets.clear },
];

// Add (the default) or Remove: a toggle instead of a long press, which touch screens do not give reliably.
const removing = ref(false);

const loop = computed(() => loopOf(props.side));
const indexOf = (pit: number) => loop.value.indexOf(pit);
const countAt = (pit: number) => props.draft[indexOf(pit)] ?? 0;
const opponentRows = computed(() => props.rows.slice(0, 2));
const myRows = computed(() => props.rows.slice(2));
const first = computed(() => myRows.value[0]?.[0]?.pit ?? null);

function canAct(pit: number): boolean {
  if (props.ready) return false;
  return removing.value ? countAt(pit) > 0 : props.seedsLeft > 0;
}

function press(pit: number): void {
  if (!canAct(pit)) return;
  emit(removing.value ? 'remove' : 'add', indexOf(pit));
}

const seedsLeftText = computed(() =>
  props.seedsLeft === 0 ? text.allPlaced : plural(text.seedsLeft, props.seedsLeft));
const opponentStatus = computed(() => format(text.opponent, {
  player: props.opponentName || text.opponentAnonymous,
  status: props.opponentReady ? text.opponentReady : text.opponentPlacing,
}));
const hintOf = (pit: number): 'add' | 'remove' | null =>
  canAct(pit) ? (removing.value ? 'remove' : 'add') : null;
</script>

<template>
  <section class="mg-editor" data-test="placement-editor" :aria-labelledby="'mg-editor-title'">
    <h2 id="mg-editor-title" class="mg-editor__title">{{ text.title }}</h2>
    <p class="mg-editor__intro">{{ text.intro }}</p>

    <div class="mg-editor__opponent" data-test="opponent-half" :data-ready="opponentReady ? 'true' : 'false'">
      <p class="mg-editor__opponent-status" role="status" data-test="opponent-status">{{ opponentStatus }}</p>
      <div v-for="(row, r) in opponentRows" :key="r" class="mg-editor__row">
        <span v-for="cell in row" :key="cell.pit" class="mg-editor__ghost" data-test="ghost-pit" aria-hidden="true"></span>
      </div>
    </div>

    <div class="mg-editor__mine" role="group" data-test="my-half" :aria-label="text.boardLabel">
      <div v-for="(row, r) in myRows" :key="r" class="mg-editor__row">
        <MangolaPit
          v-for="cell in row"
          :key="cell.pit"
          :name="cell.name"
          :count="countAt(cell.pit)"
          :arrow="cell.arrow"
          mine
          :selectable="canAct(cell.pit)"
          :hint="hintOf(cell.pit)"
          :tabbable="cell.pit === first"
          @press="press(cell.pit)"
        />
      </div>
    </div>

    <div class="mg-editor__bar">
      <p class="mg-editor__left" role="status" data-test="seeds-left">{{ seedsLeftText }}</p>
      <div class="mg-editor__modes" role="group" :aria-label="text.boardLabel">
        <button
          type="button" class="mg-editor__btn" data-test="mode-add"
          :aria-pressed="removing ? 'false' : 'true'" :disabled="ready" @click="removing = false"
        >{{ text.add }}</button>
        <button
          type="button" class="mg-editor__btn" data-test="mode-remove"
          :aria-pressed="removing ? 'true' : 'false'" :disabled="ready" @click="removing = true"
        >{{ text.remove }}</button>
      </div>
    </div>

    <div class="mg-editor__presets" role="group" :aria-label="text.presets.label">
      <button
        v-for="preset in PRESETS"
        :key="preset.key"
        type="button"
        class="mg-editor__btn"
        :data-test="`preset-${preset.key}`"
        :disabled="ready"
        @click="emit('preset', preset.key)"
      >{{ preset.label }}</button>
    </div>

    <p v-if="draftOnlyEndless" class="mg-editor__warning" data-test="endless-warning" role="status">{{ text.endlessWarning }}</p>
    <p v-if="error" class="mg-editor__error" data-test="placement-error" role="alert">{{ error }}</p>

    <div class="mg-editor__confirm">
      <template v-if="ready">
        <p class="mg-editor__hint">{{ text.confirmedWaiting }}</p>
        <button type="button" class="mg-editor__btn mg-editor__btn--main" data-test="edit-layout" @click="emit('edit')">{{ text.editLayout }}</button>
      </template>
      <template v-else>
        <p v-if="!canConfirm" class="mg-editor__hint">{{ format(text.confirmDisabledHint, { n: seedsLeft }) }}</p>
        <button
          type="button" class="mg-editor__btn mg-editor__btn--main" data-test="confirm"
          :disabled="!canConfirm" @click="emit('confirm')"
        >{{ text.confirm }}</button>
      </template>
    </div>
  </section>
</template>

<style scoped>
.mg-editor {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  width: min(100%, 560px);
  margin: 0 auto;
}
.mg-editor__title { margin: 0; font-size: 1.25rem; text-align: center; }
.mg-editor__intro { margin: 0; font-size: 0.9rem; text-align: center; opacity: 0.85; }

.mg-editor__row {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
}
.mg-editor__mine {
  border-radius: 0.8rem;
  background: rgba(0, 0, 0, 0.25);
  box-shadow: inset 0 0 0 1px rgba(var(--accent-active-rgb), 0.35);
}

/* The opponent's half: greyed, blank, with its status as text. */
.mg-editor__opponent {
  position: relative;
  border-radius: 0.8rem;
  background: rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
  filter: grayscale(1);
}
.mg-editor__opponent-status {
  margin: 0;
  padding: var(--space-2) var(--space-3) 0;
  font-size: 0.9rem;
  font-weight: 700;
  text-align: center;
}
.mg-editor__opponent[data-ready='true'] .mg-editor__opponent-status { color: rgb(var(--accent-positive-rgb)); }
.mg-editor__ghost {
  aspect-ratio: 1;
  min-height: 44px;
  display: grid;
  place-items: center;
}
.mg-editor__ghost::before {
  content: '';
  width: 84%;
  aspect-ratio: 1;
  border-radius: 50%;
  border: 1.5px dashed rgba(255, 255, 255, 0.28);
}

.mg-editor__bar { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); flex-wrap: wrap; }
.mg-editor__left { margin: 0; font-size: 1.05rem; font-weight: 700; }
.mg-editor__modes, .mg-editor__presets { display: flex; gap: var(--space-2); flex-wrap: wrap; }
.mg-editor__presets { justify-content: center; }

.mg-editor__btn {
  min-height: 44px;
  min-width: 44px;
  padding: 0 var(--space-4);
  border: 1.5px solid rgba(255, 255, 255, 0.35);
  border-radius: var(--button-radius);
  background: rgba(0, 0, 0, 0.3);
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.mg-editor__btn[aria-pressed='true'] {
  border-color: var(--gold);
  background: rgba(var(--accent-active-rgb), 0.2);
  font-weight: 700;
}
.mg-editor__btn:disabled { opacity: 0.45; cursor: not-allowed; }
.mg-editor__btn:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.mg-editor__btn--main { border-color: var(--gold); font-weight: 700; }

.mg-editor__confirm { display: flex; flex-direction: column; align-items: center; gap: var(--space-2); }
.mg-editor__hint { margin: 0; font-size: 0.85rem; text-align: center; opacity: 0.85; }
.mg-editor__warning, .mg-editor__error {
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--button-radius);
  font-size: 0.9rem;
}
.mg-editor__warning { border: 1.5px solid var(--gold); background: rgba(var(--accent-active-rgb), 0.12); }
.mg-editor__error { border: 1.5px solid rgb(var(--accent-negative-rgb)); background: rgba(var(--accent-negative-rgb), 0.14); font-weight: 700; }
</style>
