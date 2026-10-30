<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format, formatList } from '../../locales/format';
import type { BlackjackLastHand } from '../../model/blackjack/BlackjackState';

// The previous hand, kept during the next betting: the dealer's total, my net and the best hand(s). Nets are signed
// and written out, never colour alone.
const props = defineProps<{ lastHand: BlackjackLastHand; mySeat: number; nameOf: (seat: number) => string }>();
const ui = useI18n().blackjackUi;

const signed = (n: number) => (n > 0 ? `+${n}` : String(n));
const dealerText = computed(() => {
  const d = props.lastHand.dealer;
  if (d.blackjack) return ui.summary.dealerBlackjack;
  if (d.bust) return format(ui.summary.dealerBust, { total: d.total });
  return format(ui.summary.dealer, { total: d.total });
});
const myNet = computed(() => props.lastHand.seats.find(s => s.seat === props.mySeat)?.net ?? null);
const best = computed(() => {
  const seats = props.lastHand.bestSeats;
  if (seats.length === 0) return '';
  const net = Math.max(...seats.map(s => props.lastHand.seats.find(o => o.seat === s)?.net ?? 0));
  const players = formatList(seats.map(props.nameOf), 'conjunction');
  return format(ui.summary.bestHand, { players, net: signed(net) });
});
</script>

<template>
  <div class="bj-summary" data-test="hand-summary" role="group" :aria-label="ui.summary.title">
    <span data-test="summary-dealer">{{ dealerText }}</span>
    <span v-if="myNet !== null" data-test="summary-you">{{ format(ui.summary.you, { net: signed(myNet) }) }}</span>
    <span v-if="best" class="bj-best" data-test="summary-best">{{ best }}</span>
    <span v-else class="bj-none" data-test="summary-none">{{ ui.summary.nobodyGained }}</span>
  </div>
</template>

<style scoped>
.bj-summary { display: flex; flex-wrap: wrap; justify-content: center; gap: var(--bj-space-1) var(--bj-space-3); font-size: 0.8125rem; color: rgba(255, 255, 255, 0.9); text-align: center; }
.bj-best { color: var(--gold); font-weight: 700; }
.bj-none { opacity: 0.7; }
</style>
