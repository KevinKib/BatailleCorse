<script setup lang="ts">
import { ref } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useHandLayout } from '../../composables/useHandLayout';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type Card from '../../model/Card';

// The player's hand, always on ONE row: cards overlap (and shrink a little for very big
// hands) so any number of them fits the available width. A selected card is raised above
// its neighbours.
const props = defineProps<{
  cards: Card[];
  selected: Card[];
}>();
const emit = defineEmits<{ toggle: [card: Card] }>();

const ui = useI18n().bullshitUi;
const root = ref<HTMLElement | null>(null);
const { layout } = useHandLayout(root, () => props.cards.length);

const isSelected = (card: Card) => props.selected.some(c => c.name === card.name);
const cardLabel = (card: Card) =>
  format(ui.table.cardLabel, { rank: card.rank.toLowerCase(), suit: card.suit.toLowerCase() });
</script>

<template>
  <div
    ref="root"
    class="hand"
    :style="{ '--card-w': layout.cardWidth + 'px', '--step': layout.step + 'px' }">
    <button
      v-for="(card, i) in cards"
      :key="card.name"
      :data-test="`hand-card-${i}`"
      class="hand-card"
      :class="{ selected: isSelected(card) }"
      type="button"
      :aria-label="cardLabel(card)"
      :aria-pressed="isSelected(card)"
      @click="emit('toggle', card)">
      <PlayingCard :rank="card.rank" :suit="card.suit" />
    </button>
  </div>
</template>

<style scoped>
.hand {
  display: flex;
  flex-wrap: nowrap;
  justify-content: center;
  width: 100%;
  /* Room above the row for the raised (selected) card. */
  padding-top: var(--space-3);
  box-sizing: border-box;
}
.hand-card {
  flex: none;
  /* Positioned for every card, so a raised (transformed) card does not paint over the
     cards after it: stacking stays in DOM order. */
  position: relative;
  width: var(--card-w);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  border-radius: 6%;
  transition: transform 0.12s ease;
}
/* Neighbours overlap by (card width - step); a negative margin, or the plain gap when spaced. */
.hand-card + .hand-card { margin-left: calc(var(--step) - var(--card-w)); }
.hand-card:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
/* Raised, but kept in DOM order: bringing it to the front would hide the index of the cards
   it overlaps and make them untappable in a crowded hand. */
.hand-card.selected { transform: translateY(calc(-1 * var(--space-3))); }
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
