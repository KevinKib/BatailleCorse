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
// from the state; the zone keeps its place when empty so the table does not jump.
const props = defineProps<{ game: KoboState; mySeat: number; currentName: string }>();
const ui = useI18n().koboUi.table;
const cardName = useKoboCardName();

const discardLabel = computed(() => props.game.discardTop
  ? format(ui.discardPile, { card: cardName(props.game.discardTop), n: props.game.discardSize })
  : ui.discardEmpty);
const drawnLabel = computed(() => {
  if (!props.game.hasDrawn) return ui.drawnNone;
  return props.game.drawnCard
    ? format(ui.drawnYours, { card: cardName(props.game.drawnCard) })
    : format(ui.drawnOther, { player: props.currentName });
});
</script>

<template>
  <div class="kobo-piles" data-test="kobo-piles">
    <figure class="pile" data-test="draw-pile" role="img" :aria-label="plural(ui.drawPile, game.drawPileSize)">
      <div class="pile-well">
        <PlayingCard :hidden="true" rank="ace" suit="spade" />
        <div class="pile-chip" data-test="draw-count" aria-hidden="true"><CardCounter :count="game.drawPileSize" /></div>
      </div>
      <figcaption aria-hidden="true">{{ ui.drawLabel }}</figcaption>
    </figure>

    <figure class="pile" data-test="discard-pile" role="img" :aria-label="discardLabel">
      <div class="pile-well">
        <PlayingCard v-if="game.discardTop" :rank="game.discardTop.rank" :suit="game.discardTop.suit"
                     :alt="cardName(game.discardTop)" />
        <span v-else class="pile-empty"></span>
        <div class="pile-chip" data-test="discard-count" aria-hidden="true"><CardCounter :count="game.discardSize" /></div>
      </div>
      <figcaption aria-hidden="true">{{ ui.discardLabel }}</figcaption>
    </figure>

    <figure class="pile" data-test="drawn-zone" role="img" :aria-label="drawnLabel">
      <div class="pile-well">
        <PlayingCard v-if="game.hasDrawn" :hidden="game.drawnCard === null"
                     :rank="game.drawnCard?.rank ?? 'ace'" :suit="game.drawnCard?.suit ?? 'spade'"
                     :alt="game.drawnCard ? cardName(game.drawnCard) : ''" />
        <span v-else class="pile-empty"></span>
      </div>
      <figcaption aria-hidden="true">{{ ui.drawnLabel }}</figcaption>
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
.pile { margin: 0; display: flex; flex-direction: column; align-items: center; gap: 2px; }
.pile figcaption {
  font-size: 0.6rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}
.pile-well { position: relative; display: flex; align-items: center; justify-content: center; }
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
