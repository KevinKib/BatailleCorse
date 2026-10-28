<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import CardCounter from '../CardCounter.vue';
import type { KoboState } from '../../model/kobo/KoboState';
import { useI18n } from '../../composables/useI18n';
import { useKoboCardName } from '../../composables/useKoboCardName';
import { format, plural } from '../../locales/format';

// The middle of the table: the draw pile (a back and its count), the discard pile (its top card face up and
// its count) and the zone of the drawn card (face up for its holder, a back for the others). All of it comes
// from the state; the zone keeps its place when empty so the table does not jump. When a click on a pile is an
// action (`canDraw`, `canDiscard`, from the interaction), that pile is a named button; the bar's buttons stay.
const props = withDefaults(
  defineProps<{ game: KoboState; mySeat: number; currentName: string; canDraw?: boolean; canDiscard?: boolean; busy?: boolean }>(),
  { canDraw: false, canDiscard: false, busy: false },
);
const emit = defineEmits<{ draw: []; discard: [] }>();
const ui = useI18n().koboUi.table;
const cardName = useKoboCardName();

const discardLabel = computed(() => {
  const top = props.game.discardTop;
  if (!top) return props.canDiscard ? ui.discardActionEmpty : ui.discardEmpty;
  const values = { card: cardName(top), n: props.game.discardSize };
  return format(props.canDiscard ? ui.discardAction : ui.discardPile, values);
});
const drawLabel = computed(() => plural(props.canDraw ? ui.drawAction : ui.drawPile, props.game.drawPileSize));
const drawnLabel = computed(() => {
  if (!props.game.hasDrawn) return ui.drawnNone;
  return props.game.drawnCard
    ? format(ui.drawnYours, { card: cardName(props.game.drawnCard) })
    : format(ui.drawnOther, { player: props.currentName });
});
</script>

<template>
  <div class="kobo-piles" data-test="kobo-piles">
    <component :is="canDraw ? 'button' : 'figure'" class="pile" :class="{ 'pile--action': canDraw }"
               data-test="draw-pile" :type="canDraw ? 'button' : undefined" :role="canDraw ? undefined : 'img'"
               :aria-label="drawLabel" :disabled="canDraw && busy ? true : undefined" @click="canDraw && emit('draw')">
      <div class="pile-well">
        <PlayingCard :hidden="true" rank="ace" suit="spade" />
        <div class="pile-chip" data-test="draw-count" aria-hidden="true"><CardCounter :count="game.drawPileSize" /></div>
      </div>
      <span class="pile-caption" aria-hidden="true">{{ ui.drawLabel }}</span>
    </component>

    <component :is="canDiscard ? 'button' : 'figure'" class="pile" :class="{ 'pile--action': canDiscard }"
               data-test="discard-pile" :type="canDiscard ? 'button' : undefined" :role="canDiscard ? undefined : 'img'"
               :aria-label="discardLabel" :disabled="canDiscard && busy ? true : undefined" @click="canDiscard && emit('discard')">
      <div class="pile-well">
        <PlayingCard v-if="game.discardTop" :rank="game.discardTop.rank" :suit="game.discardTop.suit"
                     :alt="cardName(game.discardTop)" />
        <span v-else class="pile-empty"></span>
        <div class="pile-chip" data-test="discard-count" aria-hidden="true"><CardCounter :count="game.discardSize" /></div>
      </div>
      <span class="pile-caption" aria-hidden="true">{{ ui.discardLabel }}</span>
    </component>

    <figure class="pile" data-test="drawn-zone" role="img" :aria-label="drawnLabel">
      <div class="pile-well">
        <PlayingCard v-if="game.hasDrawn" :hidden="game.drawnCard === null"
                     :rank="game.drawnCard?.rank ?? 'ace'" :suit="game.drawnCard?.suit ?? 'spade'"
                     :alt="game.drawnCard ? cardName(game.drawnCard) : ''" />
        <span v-else class="pile-empty"></span>
      </div>
      <span class="pile-caption" aria-hidden="true">{{ ui.drawnLabel }}</span>
    </figure>
  </div>
</template>

<style scoped>
.kobo-piles {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: var(--kobo-pile-gap, 14px);
}
/* A fixed width, wider than any caption: a pile that turns into a button (or back) never changes the row. */
.pile {
  margin: 0; display: flex; flex-direction: column; align-items: center; gap: 2px; flex: none;
  width: max(var(--kobo-pile-card-w, 72px), 3.6rem);
}
/* A pile a click acts on: a plain button around the same picture (no border, no background of its own). The cue is
   the pointer, a caption in gold and a slight lift under the pointer; the focus ring is the browser's. */
button.pile { padding: 0; border: 0; background: transparent; font: inherit; color: inherit; cursor: default; }
button.pile--action { cursor: pointer; }
button.pile:disabled { cursor: default; }
.pile--action .pile-caption { color: var(--gold); }
@media (hover: hover) {
  .pile--action:not(:disabled):hover .pile-well { filter: brightness(1.12); transform: translateY(-2px); }
}
@media (prefers-reduced-motion: reduce) {
  .pile-well { transition: none; }
  .pile--action:not(:disabled):hover .pile-well { transform: none; }
}
.pile-caption {
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.7);
}
.pile-well { transition: transform 0.1s; position: relative; display: flex; align-items: center; justify-content: center; }
.pile-well :deep(.playing_card), .pile-empty {
  width: var(--kobo-pile-card-w, 72px);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
  display: block;
}
.pile-empty {
  box-sizing: border-box;
  border: 1px dashed rgba(255, 255, 255, 0.3);
  border-radius: 6%;
}
.pile-chip { position: absolute; bottom: -6px; right: -10px; }
</style>
