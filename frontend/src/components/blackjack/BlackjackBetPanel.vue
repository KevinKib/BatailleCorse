<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Button } from 'primevue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { BET_CHIPS, addChip, defaultBet, isBetAllowed, maxOf } from '../../model/blackjack/betting';
import type { BlackjackBetLimits } from '../../model/blackjack/BlackjackState';

// The sealed bet: chip buttons add to the amount, Max and Clear, and the main "Bet N" prefilled with the last bet
// (the first hand: 10) so that repeating a bet is one tap. Disabled outside the limits the server sent. Targets 44 px.
const props = defineProps<{
  limits: BlackjackBetLimits | null;
  lastBet: number | null;
  /** My bet is in: the panel only waits for the others. */
  placed?: boolean;
  placedAmount?: number | null;
  busy?: boolean;
  /** Changes with every betting phase, so the amount is prefilled again. */
  resetKey?: string | number;
}>();
const emit = defineEmits<{ bet: [amount: number] }>();
const ui = useI18n().blackjackUi;

const amount = ref(defaultBet(props.lastBet, props.limits));
watch(() => [props.resetKey, props.lastBet, props.limits?.min, props.limits?.max], () => {
  amount.value = defaultBet(props.lastBet, props.limits);
});

const allowed = computed(() => !props.busy && isBetAllowed(amount.value, props.limits));
function add(chip: number) { if (props.limits) amount.value = addChip(amount.value, chip, props.limits); }
function max() { amount.value = maxOf(props.limits); }
function clear() { amount.value = 0; }
function place() { if (allowed.value) emit('bet', amount.value); }
</script>

<template>
  <div v-if="placed" class="bj-bet bj-bet--placed" data-test="bet-panel" role="status">
    <span v-if="placedAmount" data-test="your-bet">{{ format(ui.bet.yourBet, { n: placedAmount }) }}</span>
    <span data-test="waiting-bets">{{ ui.bet.waitingOthers }}</span>
  </div>
  <div v-else-if="limits" class="bj-bet" data-test="bet-panel" role="group" :aria-label="ui.bet.title">
    <p class="bj-limits" data-test="bet-limits">{{ format(ui.bet.limits, { min: limits.min, max: limits.max }) }}</p>
    <div class="bj-chips">
      <button v-for="chip in BET_CHIPS" :key="chip" type="button" class="bj-chip" :data-test="`chip-${chip}`"
              :aria-label="format(ui.bet.chip, { n: chip })" @click="add(chip)">+{{ chip }}</button>
      <button type="button" class="bj-chip" data-test="chip-max" @click="max">{{ ui.bet.max }}</button>
      <button type="button" class="bj-chip" data-test="chip-clear" @click="clear">{{ ui.bet.clear }}</button>
    </div>
    <Button class="bj-place" data-test="bet-place" :label="format(ui.bet.place, { n: amount })" :disabled="!allowed"
            severity="success" rounded @click="place" />
  </div>
</template>

<style scoped>
.bj-bet { display: flex; flex-direction: column; align-items: center; gap: var(--space-2); width: 100%; color: #fff; font-size: 0.875rem; }
.bj-limits { margin: 0; font-size: 0.75rem; opacity: 0.75; }
.bj-chips { display: flex; flex-wrap: wrap; justify-content: center; gap: 6px; }
.bj-chip { min-width: 44px; min-height: 44px; padding: 0 10px; border-radius: 999px; border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(0, 0, 0, 0.5); color: #fff; font-weight: 700; cursor: pointer; }
.bj-place { min-height: 48px; min-width: 160px; }
</style>
