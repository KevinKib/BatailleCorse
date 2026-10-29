<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { cardLabel } from '../../model/president/cardLabel';
import PlayingCard from '../PlayingCard.vue';
import type Card from '../../model/Card';

// My hand: a tap on a card passes it. Once I chose, the card lifts and says "Passing…" and the others are disabled until
// the step resolves; the card I just received glows with "from <name>". The server says whether a pass is possible.
const props = defineProps<{
  cards: Card[];
  /** The card I chose this step, else null. */
  myChoice: Card | null;
  canPass: boolean;
  /** The card I received at the last step and who gave it, while it glows; else null. */
  received: { card: Card; fromName: string } | null;
}>();
const emit = defineEmits<{ pass: [card: Card] }>();
const ui = useI18n().kiloDeMerdeUi;

const chosen = (card: Card) => props.myChoice?.name === card.name;
const open = () => props.canPass && props.myChoice === null;
const isReceived = (card: Card) => props.received?.card.name === card.name;
const labelOf = (card: Card) => chosen(card)
  ? cardLabel(card, ui, true)
  : format(ui.hand.passCard, { card: cardLabel(card, ui, false) });
</script>

<template>
  <div class="hand" data-test="hand" role="group" :aria-label="ui.table.yourCards">
    <div v-for="(card, slot) in cards" :key="card.name" class="hand-slot" :data-slot="slot">
      <button
        type="button"
        class="hand-card"
        :class="{ 'hand-card--chosen': chosen(card), 'hand-card--received': isReceived(card) }"
        :data-card="card.name"
        :aria-label="labelOf(card)"
        :disabled="!open()"
        @click="emit('pass', card)">
        <PlayingCard :rank="card.rank" :suit="card.suit" />
      </button>
      <span v-if="chosen(card)" class="hand-note" data-test="passing">{{ ui.hand.passing }}</span>
      <span v-else-if="isReceived(card)" class="hand-note hand-note--received" data-test="received">{{
        format(ui.hand.received, { name: received!.fromName }) }}</span>
    </div>
  </div>
</template>

<style scoped>
.hand {
  --card-w: var(--kdm-card-w, 76px);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  gap: var(--space-2);
  padding-top: var(--space-3);
  box-sizing: border-box;
  min-height: calc(var(--card-w) * 1.45 + var(--space-3) + 1rem);
}
.hand-slot { display: flex; flex-direction: column; align-items: center; gap: var(--space-1); }
.hand-card {
  flex: none;
  width: var(--card-w);
  min-height: 44px;
  padding: 0;
  background: none;
  border: none;
  border-radius: 6%;
  cursor: pointer;
  transition: transform 0.16s ease-out, box-shadow 0.16s ease-out, opacity 0.16s ease-out;
}
.hand-card:disabled { cursor: default; opacity: 0.85; }
.hand-card:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.hand-card--chosen { opacity: 0.6; transform: translateY(calc(-1 * var(--space-3))); filter: grayscale(0.6); }
.hand-card--received { box-shadow: 0 0 0 3px var(--gold), 0 0 16px rgba(245, 200, 66, 0.6); opacity: 1; }
.hand-card :deep(.playing_card) { display: block; width: 100%; height: auto; aspect-ratio: 167.575 / 243.1375; }
.hand-note { font-size: 0.6875rem; line-height: 1rem; color: rgba(255, 255, 255, 0.8); white-space: nowrap; }
.hand-note--received { color: var(--gold); font-weight: 700; }
@media (prefers-reduced-motion: reduce) {
  .hand-card { transition: none; }
}
</style>
