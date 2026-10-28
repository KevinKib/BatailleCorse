<script setup lang="ts">
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { cardLabel } from '../../model/president/cardLabel';
import { effectCode } from '../../model/ninetynine/effect';
import type Card from '../../model/Card';
import type { NinetyNinePlayable } from '../../model/ninetynine/NinetyNineState';

// My hand, in the order the server sends it. The server says which card is safe (`playable`): an unsafe card is
// disabled and says why, in its name and under it; the client computes no rule. A card tapped without an Ace choice to
// make is played at once; the Ace with two values stays pressed while the chooser is open.
const props = defineProps<{
  cards: Card[];
  playable: NinetyNinePlayable[];
  /** It is my turn and the server accepts a play. */
  canPlay: boolean;
  /** The name (`SUIT_RANK`) of the Ace waiting for its value. */
  aceName?: string | null;
}>();
const emit = defineEmits<{ play: [card: Card] }>();
const ui = useI18n().ninetyNineUi;

// Safe only matters on my turn: off turn nothing is "over 99", the cards are just waiting.
const overLimit = (card: Card) =>
  props.canPlay && props.playable.find(p => p.card.name === card.name)?.safe !== true;
// The pressed state belongs to the Ace chooser only: an Ace with a choice of values. Every other card is a plain button.
const hasAceChoice = (card: Card) =>
  (props.playable.find(p => p.card.name === card.name)?.aceValues.length ?? 0) > 1;
const pressed = (card: Card) => (hasAceChoice(card) ? props.aceName === card.name : undefined);
// The effect label is display text only (the total after a play always comes from the server). The server's cards carry
// no effect label, so the rank-to-sentence table stays on the client, in model/ninetynine/effect.ts.
const effectText =(card: Card) => format(ui.effect[effectCode(card.rank)], { n: card.rank });

function labelOf(card: Card): string {
  const name = cardLabel(card, ui, props.aceName === card.name);
  if (overLimit(card)) return format(ui.hand.cardOver, { card: name });
  return format(ui.hand.cardEffect, { card: name, effect: effectText(card) });
}
</script>

<template>
  <div class="hand" data-test="hand" role="group" :aria-label="ui.table.yourCards" :class="{ 'hand--waiting': !canPlay }">
    <div v-for="card in cards" :key="card.name" class="hand-slot">
      <button
        type="button"
        class="hand-card"
        :class="{ 'hand-card--over': overLimit(card), 'hand-card--chosen': aceName === card.name }"
        :data-card="card.name"
        :aria-label="labelOf(card)"
        :aria-pressed="pressed(card)"
        :disabled="!canPlay || overLimit(card)"
        @click="emit('play', card)">
        <PlayingCard :rank="card.rank" :suit="card.suit" />
      </button>
      <span v-if="overLimit(card)" class="over-limit" :data-test="`over-limit-${card.name}`">{{ ui.hand.overLimit }}</span>
    </div>
  </div>
</template>

<style scoped>
.hand {
  --card-w: var(--nn-card-w, 70px);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  gap: var(--space-3);
  padding-top: var(--space-3);
  box-sizing: border-box;
  min-height: calc(var(--card-w) * 1.45 + var(--space-3));
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
.hand-card:disabled { cursor: default; }
.hand-card:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.hand-card--over { opacity: 0.45; }
/* Waiting for my turn: the cards are not dimmed as unplayable, only quiet. */
.hand--waiting .hand-card { opacity: 0.85; }
.hand-card--chosen {
  transform: translateY(calc(-1 * var(--space-3)));
  box-shadow: 0 0 0 3px var(--gold), 0 0 16px rgba(245, 200, 66, 0.6);
}
.hand-card :deep(.playing_card) { display: block; width: 100%; height: auto; aspect-ratio: 167.575 / 243.1375; }
.over-limit { font-size: 0.6875rem; line-height: 1; color: rgba(255, 255, 255, 0.75); white-space: nowrap; }

@media (prefers-reduced-motion: reduce) {
  .hand-card { transition: none; }
}
</style>
