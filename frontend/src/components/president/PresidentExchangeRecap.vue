<script setup lang="ts">
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { cardLabel } from '../../model/president/cardLabel';
import type { ExchangeRecapLine } from '../../model/president/exchangeRecap';
import { format, plural } from '../../locales/format';

// What the exchange of the round came to, once the play has begun: a line per pair. My own pair shows the faces
// (the cards I gave and the ones that came back); the others say how many cards went each way. It sits over the
// action bar until it is closed, so nothing about the exchange is only a movement.
defineProps<{
  lines: ExchangeRecapLine[];
  nameOf: (seat: number) => string;
}>();
const emit = defineEmits<{ close: [] }>();

const ui = useI18n().presidentUi;
const recap = ui.exchange.recap;
</script>

<template>
  <section class="recap" data-test="exchange-recap" role="region" :aria-label="recap.title">
    <header class="head">
      <h3 class="title">{{ recap.title }}</h3>
      <button type="button" class="close" data-test="recap-close" :aria-label="recap.close" :title="recap.close"
              @click="emit('close')">
        <i class="pi pi-times" aria-hidden="true"></i>
      </button>
    </header>
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
  </section>
</template>

<style scoped>
.recap {
  display: flex; flex-direction: column; gap: 4px; width: 100%; box-sizing: border-box; padding: 6px 10px;
  background: rgba(0, 0, 0, 0.45); border: 1px solid rgba(255, 255, 255, 0.12); border-radius: 12px;
}
.head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.title {
  margin: 0; font-size: 0.7rem; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--gold);
}
.close {
  display: inline-flex; align-items: center; justify-content: center; width: 44px; height: 28px; margin: -4px -8px -4px 0;
  border: 0; background: none; color: rgba(255, 255, 255, 0.8); cursor: pointer; font-size: 0.8rem;
}
.lines { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.line {
  display: flex; flex-wrap: wrap; align-items: center; gap: 2px 14px; font-size: 0.75rem; color: rgba(255, 255, 255, 0.75);
}
.line.mine { color: #fff; font-weight: 700; }
.leg { display: inline-flex; align-items: center; gap: 6px; }
.cards { display: inline-flex; gap: 3px; }
.cards :deep(.playing_card) { display: block; width: 26px; height: auto; aspect-ratio: 167.575 / 243.1375; }
</style>
