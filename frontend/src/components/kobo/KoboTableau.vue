<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import type Card from '../../model/Card';
import type { KoboPlayer } from '../../model/kobo/KoboState';
import { slotKey } from '../../model/kobo/interaction';
import { slotPosition } from '../../model/kobo/slotPosition';
import { useI18n } from '../../composables/useI18n';
import { useKoboCardName } from '../../composables/useKoboCardName';
import { format } from '../../locales/format';

// One player's grid of cards. A card is drawn face up only when `faces` (what the server lets the viewer see)
// holds it: this component remembers nothing. Slots are `<button>`s so the actions of the next steps only have
// to enable them; with no `clickable` entry they stay disabled.
const props = withDefaults(defineProps<{
  player: KoboPlayer;
  /** Owner part of a slot name: "Your" or "Bob's". */
  owner: string;
  /** Accessible name of the whole grid. */
  groupLabel: string;
  faces: Record<string, Card>;
  clickable?: Record<string, boolean>;
  compact?: boolean;
  /** The slot the player already picked in a two-click action (the first card of a blind swap). */
  selected?: number | null;
  /** Slots (by `slotKey`) named by the last event: marked until the next event replaces them. */
  highlighted?: string[];
}>(), { clickable: () => ({}), compact: false, selected: null, highlighted: () => [] });
const emit = defineEmits<{ pick: [seat: number, slot: number] }>();

const ui = useI18n().koboUi.table;
const cardName = useKoboCardName();

const cells = computed(() => props.player.slots.map(s => ({ ...s, ...slotPosition(s.slot) })));
const columns = computed(() => Math.max(2, ...cells.value.map(c => c.col + 1)));

const cellStyle = (cell: { row: number; col: number }) => ({
  gridRow: String(cell.row + 1),
  gridColumn: String(cell.col + 1),
});
const isHighlighted = (slot: number): true | undefined =>
  props.highlighted.includes(slotKey(props.player.seat, slot)) ? true : undefined;
const faceOf = (slot: number): Card | null => props.faces[slotKey(props.player.seat, slot)] ?? null;
const slotLabel = (slot: number): string => {
  const face = faceOf(slot);
  return face
    ? format(ui.slotUp, { owner: props.owner, n: slot + 1, card: cardName(face) })
    : format(ui.slotDown, { owner: props.owner, n: slot + 1 });
};
</script>

<template>
  <div class="tableau" :class="{ 'tableau--compact': compact }" role="group" :aria-label="groupLabel"
       :style="{ '--columns': columns }">
    <template v-for="cell in cells" :key="cell.slot">
      <button
        v-if="cell.occupied"
        type="button"
        class="slot"
        :class="{ 'slot--selected': selected === cell.slot, 'slot--event': isHighlighted(cell.slot) }"
        :data-highlighted="isHighlighted(cell.slot)"
        :aria-pressed="selected === cell.slot ? 'true' : undefined"
        :data-test="`slot-${player.seat}-${cell.slot}`"
        :style="cellStyle(cell)"
        :aria-label="slotLabel(cell.slot)"
        :disabled="!clickable[slotKey(player.seat, cell.slot)]"
        @click="emit('pick', player.seat, cell.slot)">
        <PlayingCard
          :hidden="faceOf(cell.slot) === null"
          :rank="faceOf(cell.slot)?.rank ?? 'ace'"
          :suit="faceOf(cell.slot)?.suit ?? 'spade'" />
      </button>
      <span v-else class="slot-empty" :class="{ 'slot--event': isHighlighted(cell.slot) }" aria-hidden="true"
            :data-highlighted="isHighlighted(cell.slot)"
            :data-test="`slot-empty-${player.seat}-${cell.slot}`" :style="cellStyle(cell)"></span>
    </template>
  </div>
</template>

<style scoped>
/* Card width per variant; the buttons are never smaller than a 44 px target, the card inside may be. */
.tableau {
  --card-w: var(--kobo-card-w, 48px);
  display: grid;
  grid-template-columns: repeat(var(--columns), auto);
  grid-auto-rows: auto;
  gap: var(--kobo-gap, 4px);
  justify-content: center;
}
.tableau--compact { --card-w: 34px; }

.slot, .slot-empty {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  padding: 2px;
  border-radius: 8px;
}
.slot {
  border: 1px solid transparent;
  background: transparent;
  cursor: default;
}
/* A disabled button greys its content by default: the card must stay readable. */
.slot:disabled { opacity: 1; }
.slot:not(:disabled) { cursor: pointer; border-color: rgba(var(--accent-active-rgb), 0.7); }
.slot.slot--selected { border-color: var(--gold); background: rgba(255, 255, 255, 0.12); }
.slot:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.slot-empty { border: 1px dashed rgba(255, 255, 255, 0.25); }
/* The slot the last event touched: a ring, replaced by the next event (state, not a timer). */
.slot--event { box-shadow: 0 0 0 2px var(--gold), 0 0 10px 1px rgba(255, 255, 255, 0.35); }

.slot :deep(.playing_card) {
  width: var(--card-w);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
  display: block;
}
</style>
