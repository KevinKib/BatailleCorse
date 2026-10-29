<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import { cardLabel } from '../../model/president/cardLabel';
import { totalDanger } from '../../model/ninetynine/totalView';
import { TOTAL_RESERVED_DIGITS } from '../../model/table/ringLayout';
import type Card from '../../model/Card';
import type { NinetyNineDirection } from '../../model/ninetynine/NinetyNineState';

// The middle of the felt: the running total (coloured as it nears 99), the card on top of the pile, the stock with its
// count, and a chip when the direction of play is reversed. Everything is the server's state; nothing is computed.
const props = defineProps<{
  total: number;
  direction: NinetyNineDirection;
  pileTop: Card | null;
  stockSize: number;
}>();
const ui = useI18n().ninetyNineUi;
const danger = computed(() => totalDanger(props.total));
const reversed = computed(() => props.direction === 'COUNTER_CLOCKWISE');
const pileLabel = computed(() => props.pileTop
  ? format(ui.table.pileTop, { card: cardLabel(props.pileTop, ui, false) })
  : ui.table.pileEmpty);
const stockLabel = computed(() => plural(ui.table.stockCount, props.stockSize));
</script>

<template>
  <div class="center" data-test="center">
    <span v-if="reversed" class="reversed" data-test="reversed">
      <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
        <path d="M9.5 4 A4 4 0 1 0 10 7" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
        <path d="M10.8 1.2 L9.8 4.6 L6.6 3.4 Z" fill="currentColor" />
      </svg>
      {{ ui.table.reversed }}
    </span>
    <div class="row">
      <div class="pile" data-test="pile" role="img" :aria-label="pileLabel">
        <PlayingCard v-if="pileTop" :rank="pileTop.rank" :suit="pileTop.suit" />
        <span v-else class="empty-pile" aria-hidden="true"></span>
      </div>
      <div class="total total--reserved" :class="`total--${danger}`" data-test="total" :style="{ '--total-digits': TOTAL_RESERVED_DIGITS }" role="status" :aria-label="format(ui.table.total, { total })">
        <strong class="total-value">{{ total }}</strong>
        <span class="total-limit">{{ ui.table.ofLimit }}</span>
      </div>
      <div class="stock" data-test="stock" role="img" :aria-label="stockLabel">
        <PlayingCard :hidden="true" rank="ace" suit="spade" />
        <span class="stock-count" data-test="stock-count" aria-hidden="true">{{ stockSize }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.center {
  --pile-w: var(--nn-pile-w, 38px);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
}
.reversed {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 22px;
  padding: 0 10px;
  box-sizing: border-box;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(245, 200, 66, 0.6);
  color: var(--gold);
  font-size: 0.6875rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.row { display: flex; align-items: center; gap: var(--nn-center-gap, 6px); }
.pile, .stock { position: relative; flex: none; width: var(--pile-w); }
.pile :deep(.playing_card), .stock :deep(.playing_card) {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
  /* The border sits inside the width: the card on the pile measures what the flying copy lands at. */
  box-sizing: border-box;
  border: 1px solid #000;
  border-radius: 6%;
  box-shadow: 0 2px 3px rgba(0, 0, 0, 0.55), 0 8px 22px rgba(0, 0, 0, 0.5);
}
.empty-pile {
  display: block;
  width: 100%;
  aspect-ratio: 167.575 / 243.1375;
  border: 1px dashed rgba(255, 255, 255, 0.35);
  border-radius: 6%;
}
.stock-count {
  position: absolute;
  right: -6px;
  bottom: -6px;
  min-width: 20px;
  height: 20px;
  padding: 0 4px;
  box-sizing: border-box;
  border-radius: 10px;
  background: #141413;
  border: 1px solid rgba(255, 255, 255, 0.3);
  font-size: 0.6875rem;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
/* The slot is as wide as three digits at all times (width from the table), so the cards beside it never move. */
.total { flex: none; width: var(--nn-total-w, 66px); box-sizing: border-box; display: flex; flex-direction: column; align-items: center; line-height: 1; min-width: 0; }
.total-value {
  display: block;
  width: 100%;
  text-align: center;
  white-space: nowrap;
  font-size: var(--nn-total-size, 36px);
  font-variant-numeric: tabular-nums;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.6);
}
.total-limit { font-size: 0.75rem; color: rgba(255, 255, 255, 0.7); }
.total--calm .total-value { color: #fff; }
.total--warm .total-value { color: var(--gold); }
.total--hot .total-value { color: #fb923c; }
</style>
