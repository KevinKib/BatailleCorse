<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';

// A pile of chips for a bet: more chips and warmer colours as the amount grows. A sealed bet shows the pile with a "?"
// instead of the amount. Once the hand is settled, `result` plays the payout: a win lifts the chips toward the stack,
// a loss sweeps them toward the dealer, a push leaves them in place. Purely visual: the amount is always written.
const props = defineProps<{
  amount: number | null;
  /** The bet is placed but its amount is not told yet. */
  sealed?: boolean;
  result?: 'win' | 'lose' | 'push' | null;
  /** The pile is smaller (the plate of an opponent). */
  compact?: boolean;
}>();
const ui = useI18n().blackjackUi;

const discs = computed(() => {
  const n = props.amount ?? 0;
  if (props.sealed || n <= 0) return 2;
  if (n < 5) return 1;
  if (n < 10) return 2;
  if (n < 25) return 3;
  if (n < 50) return 4;
  return 5;
});
const tier = computed(() => {
  const n = props.amount ?? 0;
  if (props.sealed || n <= 0) return 'sealed';
  if (n < 10) return 'low';
  if (n < 25) return 'mid';
  if (n < 50) return 'high';
  return 'top';
});
const label = computed(() => props.sealed || props.amount === null
  ? ui.table.chipsSealed
  : format(ui.table.chips, { n: props.amount }));
</script>

<template>
  <div class="bj-chips" :class="[`bj-chips--${tier}`, { 'bj-chips--compact': compact }, result ? `bj-chips--${result}` : '']"
       data-test="bet-chips" :data-sealed="sealed ? 'true' : undefined" :data-result="result ?? undefined" role="img" :aria-label="label">
    <span class="bj-pile" aria-hidden="true">
      <span v-for="i in discs" :key="i" class="bj-disc" :style="{ '--i': i - 1 }"></span>
    </span>
    <span :key="sealed ? 'sealed' : amount ?? 0" class="bj-amount" data-test="bet-chips-amount" aria-hidden="true">{{ sealed || amount === null ? '?' : amount }}</span>
  </div>
</template>

<style scoped>
.bj-chips { --disc: 20px; --disc-face: #b91c1c; --disc-edge: #fecaca; display: inline-flex; align-items: flex-end; gap: var(--bj-space-2); animation: chips-drop 360ms cubic-bezier(.2, .8, .2, 1) both; }
.bj-chips--compact { --disc: 15px; gap: var(--bj-space-1); }
.bj-chips--low { --disc-face: #e5e7eb; --disc-edge: #6b7280; }
.bj-chips--mid { --disc-face: #dc2626; --disc-edge: #fecaca; }
.bj-chips--high { --disc-face: #2563eb; --disc-edge: #bfdbfe; }
.bj-chips--top { --disc-face: #111827; --disc-edge: var(--gold); }
.bj-chips--sealed { --disc-face: #374151; --disc-edge: #9ca3af; }
.bj-pile { position: relative; display: inline-block; width: var(--disc); height: calc(var(--disc) * 0.55 + 14px); }
.bj-disc {
  position: absolute; left: 0; bottom: calc(var(--i) * 3px); width: var(--disc); height: calc(var(--disc) * 0.55); border-radius: 50%;
  background:
    radial-gradient(ellipse at 50% 50%, transparent 38%, rgba(255, 255, 255, 0.35) 40%, transparent 44%),
    repeating-conic-gradient(var(--disc-edge) 0 14deg, var(--disc-face) 14deg 45deg);
  border: 1px solid rgba(0, 0, 0, 0.55); box-shadow: 0 1px 2px rgba(0, 0, 0, 0.6);
}
.bj-amount { font-size: 0.8125rem; font-weight: 800; font-variant-numeric: tabular-nums; color: #fff; text-shadow: 0 1px 2px rgba(0, 0, 0, 0.8); animation: amount-pop 420ms ease-out both; }
.bj-chips--compact .bj-amount { font-size: 0.75rem; }
.bj-chips--win { animation: chips-win 1300ms ease-in-out both; }
.bj-chips--lose { animation: chips-lose 1200ms ease-in both; }
.bj-chips--push { animation: chips-push 700ms ease-out both; }
@keyframes chips-drop { from { transform: translateY(-18px) scale(1.25); opacity: 0; } to { transform: none; opacity: 1; } }
@keyframes amount-pop { from { transform: scale(1.6); opacity: 0; } to { transform: none; opacity: 1; } }
@keyframes chips-win { 0% { transform: none; filter: none; } 35% { transform: scale(1.18); filter: drop-shadow(0 0 8px rgba(74, 222, 128, 0.9)); } 100% { transform: translateY(-38px) scale(0.7); opacity: 0.15; } }
@keyframes chips-lose { 0% { transform: none; opacity: 1; } 100% { transform: translateY(-60px) scale(0.6); opacity: 0; } }
@keyframes chips-push { 0% { transform: none; } 40% { transform: translateY(-5px); } 100% { transform: none; } }
@media (prefers-reduced-motion: reduce) {
  .bj-chips, .bj-amount, .bj-chips--win, .bj-chips--lose, .bj-chips--push { animation: none; }
  .bj-chips--lose { opacity: 0.4; }
}
</style>
