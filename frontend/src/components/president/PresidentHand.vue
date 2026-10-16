<script setup lang="ts">
import { computed, ref } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useHandLayout } from '../../composables/useHandLayout';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { cardLabel } from '../../model/president/cardLabel';
import type Card from '../../model/Card';

// My hand, in the order the server sends it (sorted by the strength in force: the client never sorts). Each card
// is a toggle button named after the card and its state. Cards overlap on one row, or on two when that would hide
// too much of each.
const props = defineProps<{
  cards: Card[];
  /** The names (`SUIT_RANK`) of the tapped cards. */
  selected: string[];
  /** The names of the cards received in the exchange: tagged until the round is played. */
  received?: string[];
}>();
const emit = defineEmits<{ toggle: [name: string] }>();

const ui = useI18n().presidentUi;
const root = ref<HTMLElement | null>(null);
const { layout } = useHandLayout(root, () => props.cards.length);

const rows = computed(() => {
  const indexed = props.cards.map((card, index) => ({ card, index }));
  return layout.value.rows === 1
    ? [indexed]
    : [indexed.slice(0, layout.value.perRow), indexed.slice(layout.value.perRow)];
});
const isSelected = (card: Card) => props.selected.includes(card.name);
const isReceived = (card: Card) => props.received?.includes(card.name) ?? false;
const labelOf = (card: Card) => {
  const label = cardLabel(card, ui, isSelected(card));
  return isReceived(card) ? format(ui.cardReceived, { card: label }) : label;
};
</script>

<template>
  <div ref="root" class="hand" data-test="hand" :style="{ '--card-w': layout.cardWidth + 'px', '--step': layout.step + 'px' }">
    <div v-for="(row, r) in rows" :key="r" class="hand-row">
      <button
        v-for="{ card, index } in row"
        :key="card.name"
        :data-test="`hand-card-${index}`"
        :data-card="card.name"
        class="hand-card"
        :class="{ selected: isSelected(card), received: isReceived(card) }"
        type="button"
        :data-received="isReceived(card) ? 'true' : undefined"
        :aria-label="labelOf(card)"
        :aria-pressed="isSelected(card)"
        @click="emit('toggle', card.name)">
        <PlayingCard :rank="card.rank" :suit="card.suit" />
        <span v-if="isReceived(card)" class="received-tag" data-test="received-tag" aria-hidden="true">{{ ui.receivedTag }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.hand {
  display: flex;
  flex-direction: column;
  width: 100%;
  /* Room above the row for the raised (selected) card. */
  padding-top: var(--space-3);
  box-sizing: border-box;
}
.hand-row { display: flex; flex-wrap: nowrap; justify-content: center; }
.hand-row + .hand-row { position: relative; margin-top: calc(var(--card-w) * -0.7); }
.hand-card {
  flex: none;
  position: relative;
  width: var(--card-w);
  min-height: 44px;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  border-radius: 6%;
  transition: transform 0.16s ease-out, box-shadow 0.16s ease-out;
}
.hand-card + .hand-card { margin-left: calc(var(--step) - var(--card-w)); }
.hand-card:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.hand-card.selected {
  transform: translateY(calc(-1 * var(--space-3)));
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.4);
}
/* A card received in the exchange wears a small gold tag (text, not colour alone). */
.received-tag {
  position: absolute;
  top: -2px;
  left: 50%;
  transform: translateX(-50%);
  padding: 0 5px;
  font-size: 0.6rem;
  font-weight: 700;
  line-height: 1.3;
  color: #000;
  background: var(--gold);
  border-radius: 999px;
  pointer-events: none;
}
/* Raised is not the only cue: a selected card also gets a gold outline. */
.hand-card.selected :deep(.playing_card) { outline: 2px solid var(--gold); outline-offset: -2px; }
.hand-card :deep(.playing_card) {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}

@media (prefers-reduced-motion: reduce) {
  .hand-card { transition: none; }
}
</style>
