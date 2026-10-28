<script setup lang="ts">
import BlackjackHand from './BlackjackHand.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type { BlackjackSeat } from '../../model/blackjack/BlackjackState';

// My seat, larger, at the bottom: stack and standing, my bet, my one or two hands side by side (the active one
// outlined) with their totals and, once settled, their nets.
defineProps<{ player: BlackjackSeat; current?: boolean; activeHandIndex?: number }>();
const ui = useI18n().blackjackUi;
</script>

<template>
  <section class="bj-mine" :class="{ 'bj-mine--current': current }" data-test="my-seat" :data-seat="player.seat"
           :aria-label="ui.table.yourSeat">
    <div class="bj-stats">
      <strong data-test="my-stack" :aria-label="format(ui.table.stackLabel, { n: player.stack })">{{ format(ui.table.stack, { n: player.stack }) }}</strong>
      <span data-test="my-standing">{{ format(ui.table.standing, { place: player.standing }) }}</span>
      <span v-if="player.bet !== null" data-test="my-bet">{{ format(ui.table.bet, { n: player.bet }) }}</span>
      <span v-else-if="player.betPlaced" data-test="my-bet">{{ ui.table.betPlaced }}</span>
    </div>
    <div class="bj-hands">
      <BlackjackHand v-for="(h, i) in player.hands" :key="i" :hand="h" :card-size="64"
                     :anchor="`seat-${player.seat}-hand-${i}`" :active="current && activeHandIndex === i && player.hands.length > 1" />
    </div>
  </section>
</template>

<style scoped>
.bj-mine { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 8px; border-radius: 12px; color: #fff;
  background: rgba(0, 0, 0, 0.35); border: 1px solid rgba(245, 200, 66, 0.6); min-height: 120px; }
.bj-mine--current { box-shadow: 0 0 0 2px rgba(245, 200, 66, 0.7); }
.bj-stats { display: flex; gap: 12px; align-items: baseline; font-size: 0.875rem; font-variant-numeric: tabular-nums; }
.bj-hands { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px; }
</style>
