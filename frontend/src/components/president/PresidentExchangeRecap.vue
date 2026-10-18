<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { cardLabel } from '../../model/president/cardLabel';
import type { ExchangeRecapLine } from '../../model/president/exchangeRecap';
import { format, plural } from '../../locales/format';

// What the exchange of the round came to, once the play has begun: a line per pair. My own pair shows the faces
// (the cards I gave and the ones that came back); the others say how many cards went each way. It sits over the
// action bar until it is closed, so nothing about the exchange is only a movement. On a phone only my own pair is drawn, as a banner over
// the top of the felt (the felt never scrolls inside itself, so the summary takes no height from it); the other pairs, and the whole summary of a
// seat outside every pair, stay in the page for assistive technology.
const props = defineProps<{
  lines: ExchangeRecapLine[];
  nameOf: (seat: number) => string;
}>();
const emit = defineEmits<{ close: [] }>();

const ui = useI18n().presidentUi;
const recap = ui.exchange.recap;
const hasMine = computed(() => props.lines.some(l => l.mine));
</script>

<template>
  <section class="recap" :class="{ 'no-mine': !hasMine }" data-test="exchange-recap" role="region" :aria-label="recap.title">
    <h3 class="title">{{ recap.title }}</h3>
    <ul class="lines">
      <li v-for="line in lines" :key="`${line.high}-${line.low}`" class="line" :class="{ mine: line.mine }"
          data-test="recap-line">
        <template v-if="line.best && line.back">
          <span class="leg">
            <span class="said" data-test="recap-given">{{ format(recap.given, { low: nameOf(line.low), high: nameOf(line.high) }) }}</span>
            <span class="cards" data-test="recap-given-cards">
              <PlayingCard v-for="c in line.best" :key="c.name" :rank="c.rank" :suit="c.suit" :alt="cardLabel(c, ui, false)" />
            </span>
          </span>
          <span class="leg">
            <span class="said" data-test="recap-returned">{{ format(recap.returned, { high: nameOf(line.high) }) }}</span>
            <span class="cards" data-test="recap-returned-cards">
              <PlayingCard v-for="c in line.back" :key="c.name" :rank="c.rank" :suit="c.suit" :alt="cardLabel(c, ui, false)" />
            </span>
          </span>
        </template>
        <span v-else class="said" data-test="recap-counts">
          {{ plural(recap.counts, line.count, { low: nameOf(line.low), high: nameOf(line.high) }) }}
        </span>
      </li>
    </ul>
    <button type="button" class="close" data-test="recap-close" :aria-label="recap.close" :title="recap.close"
            @click="emit('close')">
      <i class="pi pi-times" aria-hidden="true"></i>
    </button>
  </section>
</template>

<style scoped>
/* Compact: on a phone the table above gives up every pixel this takes, so the title is only a name for assistive
   technology there and the close button sits in the corner instead of a row of its own. */
.recap {
  position: relative; display: flex; align-items: center; gap: 4px 10px; width: 100%; box-sizing: border-box;
  padding: 4px 38px 4px 8px; min-height: 40px;
  background: rgba(0, 0, 0, 0.45); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 12px;
}
.title {
  margin: 0; font-size: 0.7rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--gold);
  position: absolute; width: 1px; height: 1px; margin: -1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap;
}
.close {
  position: absolute; top: 50%; right: -4px; transform: translateY(-50%); display: inline-flex; align-items: center;
  justify-content: center; width: 44px; height: 44px; border: 0; background: none; color: rgba(255, 255, 255, 0.8); cursor: pointer;
  font-size: 0.8rem;
}
.lines { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.line {
  display: flex; flex-wrap: wrap; align-items: center; gap: 2px 10px; font-size: 0.7rem; color: rgba(255, 255, 255, 0.75);
}
.line.mine { color: #fff; font-weight: 700; }
.leg { display: inline-flex; align-items: center; gap: 4px; }
.cards { display: inline-flex; gap: 2px; }
.cards :deep(.playing_card) { display: block; width: 22px; height: auto; aspect-ratio: 167.575 / 243.1375; }
@media (min-width: 768px) {
  .title { position: static; width: auto; height: auto; margin: 0; clip: auto; overflow: visible; }
  .recap { padding-left: 12px; }
  .line { font-size: 0.75rem; }
  .cards :deep(.playing_card) { width: 26px; }
}
@media (max-width: 767px) {
  .line:not(.mine), .recap.no-mine, .recap.no-mine .close {
    position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0);
    white-space: nowrap; border: 0;
  }
  .recap.no-mine { background: none; min-height: 0; }
  /* A banner over the top of the felt, so that it takes no height from it (the felt never scrolls inside itself); the
     screen closes it on its own after a few seconds. */
  .recap:not(.no-mine) {
    position: fixed; z-index: 1700; width: auto; box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5); background: rgba(0, 0, 0, 0.9);
    top: calc(var(--hud-inset) * 2 + var(--hud-chip-h) + env(safe-area-inset-top, 0px));
    left: var(--space-2); right: var(--space-2);
  }
}
</style>
