<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Button } from 'primevue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { BET_CHIPS, addChip, chipsLeft, clampBet, defaultBet, isBetAllowed, maxOf, parseBetInput } from '../../model/blackjack/betting';
import type { BlackjackBetLimits } from '../../model/blackjack/BlackjackState';

// The sealed bet: chip buttons add to the amount, Max and Clear, and the main "Bet N" prefilled with the last bet
// (the first hand: 10) so that repeating a bet is one tap. Disabled outside the limits the server sent. Targets 44 px.
const props = defineProps<{
  limits: BlackjackBetLimits | null;
  lastBet: number | null;
  /** My bet is in: the panel only waits for the others. */
  placed?: boolean;
  placedAmount?: number | null;
  /** My stack, to write the chips left once the bet is in. */
  stack?: number;
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
// The field: digits only, never above the maximum while typing; an empty field stays empty (Bet is disabled) until
// it is left, where the amount is raised to the minimum.
function typed(event: Event) {
  const el = event.target as HTMLInputElement;
  const n = parseBetInput(el.value);
  amount.value = props.limits ? Math.min(n, props.limits.max) : n;
  el.value = amount.value > 0 ? String(amount.value) : '';
}
function settle() { if (props.limits) amount.value = clampBet(amount.value, props.limits); }
const left = computed(() => props.stack === undefined ? null : chipsLeft(props.stack, amount.value));
function place() { if (allowed.value) emit('bet', amount.value); }
</script>

<template>
  <div v-if="placed" class="bj-bet bj-bet--placed" data-test="bet-panel" role="status">
    <span v-if="placedAmount" data-test="your-bet">{{ format(ui.bet.yourBet, { n: placedAmount }) }}</span>
    <span data-test="waiting-bets">{{ ui.bet.waitingOthers }}</span>
  </div>
  <div v-else-if="limits" class="bj-bet" data-test="bet-panel" role="group" :aria-label="ui.bet.title">
    <p class="bj-limits" data-test="bet-limits">{{ format(ui.bet.limits, { min: limits.min, max: limits.max }) }}</p>
    <input class="bj-field" data-test="bet-input" type="text" inputmode="numeric" pattern="[0-9]*" autocomplete="off"
           enterkeyhint="done" :aria-label="ui.bet.amount" :value="amount > 0 ? amount : ''"
           @input="typed" @blur="settle" @keydown.enter.prevent="place" />
    <p v-if="left" class="bj-left" data-test="bet-left-box" aria-live="polite">
      <span data-test="bet-left">{{ format(ui.bet.left, { n: left.after }) }}</span>
      <span v-if="left.afterDouble !== null" class="bj-left-double" data-test="bet-left-double">{{ format(ui.bet.leftDouble, { n: left.afterDouble }) }}</span>
    </p>
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
.bj-field { box-sizing: border-box; width: 120px; min-height: 48px; text-align: center; font-size: 1.25rem; font-weight: 700;
  font-variant-numeric: tabular-nums; color: #fff; background: rgba(0, 0, 0, 0.5); border: 1px solid rgba(255, 255, 255, 0.4); border-radius: 999px; }
.bj-field:focus-visible { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 1px; }
.bj-left { margin: 0; display: flex; flex-direction: column; align-items: center; font-size: 0.75rem; }
.bj-left-double { opacity: 0.7; }
.bj-chips { display: flex; flex-wrap: wrap; justify-content: center; gap: 6px; }
.bj-chip { min-width: 44px; min-height: 44px; padding: 0 10px; border-radius: 999px; border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(0, 0, 0, 0.5); color: #fff; font-weight: 700; cursor: pointer; }
.bj-place { min-height: 48px; min-width: 160px; }
</style>
