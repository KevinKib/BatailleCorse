<script setup lang="ts">
import { computed } from 'vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { kiloLabel } from '../../model/kilodemerde/kilo';
import type Card from '../../model/Card';
import type { KiloDeMerdePlayer } from '../../model/kilodemerde/KiloDeMerdeState';

// The plate of a seat: its name (and bot level), its kilos, a check once it has chosen its card, during a race a hand
// mark with its slap rank, at the end of a round a "last" tag and its revealed hand. Tapping the kilos asks for the kilo
// cards. Names are user input: only ever rendered as text.
const props = defineProps<{
  player: KiloDeMerdePlayer;
  label: string;
  mine?: boolean;
  /** Phone plate: the same facts, smaller. */
  compact?: boolean;
  /** The seat has chosen its card for this step. */
  ready?: boolean;
  /** The seat drew the kilo card of the round. */
  last?: boolean;
  /** The hand to show (round over), else nothing. */
  hand?: Card[] | null;
  disconnected?: boolean;
  secondsRemaining?: number | null;
}>();
defineEmits<{ 'show-kilos': [] }>();
const ui = useI18n().kiloDeMerdeUi;

const levelText = computed(() => {
  const level = props.player.level as keyof typeof ui.levels | undefined;
  return props.player.bot && level ? ui.levels[level] ?? level : '';
});
const kilosText = computed(() => format(ui.table.kilos, { kg: props.player.kilos }));
const handText = computed(() => props.hand?.map(card => kiloLabel(card)).join(' ') ?? '');
const accessibleName = computed(() => {
  const parts = [format(ui.table.kilosLabel, { player: props.label, kg: props.player.kilos })];
  if (levelText.value) parts.push(levelText.value);
  if (props.ready) parts.push(format(ui.table.ready, { player: props.label }));
  if (props.player.slapRank !== null) parts.push(format(ui.race.rank, { n: props.player.slapRank }));
  if (props.last) parts.push(ui.outcome.lastTag);
  if (props.hand) parts.push(`${format(ui.hand.revealed, { player: props.label })}: ${handText.value}`);
  return parts.join(', ');
});
</script>

<template>
  <div class="plate"
       :class="{ 'plate--mine': mine, 'plate--compact': compact, 'plate--disconnected': disconnected, 'plate--ready': ready }"
       role="group"
       :aria-label="accessibleName"
       data-test="seat-plate"
       :data-seat="player.seat">
    <span class="plate-top">
      <span class="plate-name" data-test="seat-name" :title="label">{{ label }}</span>
      <span v-if="levelText && !compact" class="plate-level" data-test="seat-level">{{ levelText }}</span>
    </span>
    <span v-if="levelText && compact" class="sr-only" data-test="seat-level">{{ levelText }}</span>
    <!-- On a phone plate the revealed hand takes the place of the kilos line for the pause (the kilos stay named). -->
    <button type="button" class="plate-kilos" :class="{ 'plate-kilos--covered': hand && compact }" data-test="seat-kilos"
            @click="$emit('show-kilos')">{{ kilosText }}</button>
    <span v-if="hand" class="plate-hand" data-test="seat-hand">{{ handText }}</span>
    <span v-if="ready" class="plate-ready" data-test="seat-ready" aria-hidden="true"><i class="pi pi-check"></i></span>
    <span v-if="player.slapRank !== null" class="plate-rank" data-test="seat-rank" aria-hidden="true">{{ player.slapRank }}</span>
    <span v-if="last" class="plate-last" data-test="seat-last" aria-hidden="true">{{ ui.outcome.lastTag }}</span>
    <span v-if="disconnected" class="plate-badge"><SeatDisconnectBadge :seconds-remaining="secondsRemaining ?? null" /></span>
  </div>
</template>

<style scoped>
.plate {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  min-width: 0;
  padding: 0 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fff;
}
.plate--compact { align-items: center; padding: 0 var(--space-1); border-radius: 8px; gap: 1px; }
.plate--mine { border-color: rgba(245, 200, 66, 0.95); box-shadow: inset 0 0 0 1px rgba(245, 200, 66, 0.95); }
.plate--mine .plate-name { color: var(--gold); }
.plate--ready { border-color: rgba(var(--accent-active-rgb), 0.9); }
.plate--disconnected .plate-name { opacity: 0.6; }
.plate-top { display: flex; align-items: baseline; gap: var(--space-1); min-width: 0; max-width: 100%; }
.plate-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.8125rem;
  font-weight: 700;
}
.plate--compact .plate-name { font-size: 0.6875rem; }
.plate-level { font-size: 0.625rem; color: rgba(255, 255, 255, 0.65); white-space: nowrap; }
.plate-kilos {
  align-self: flex-start;
  padding: 0;
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.85);
  font: inherit;
  font-size: 0.75rem;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.plate--compact .plate-kilos { align-self: center; font-size: 0.6875rem; }
.plate-kilos--covered { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
.plate-kilos:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.plate-hand { font-size: 0.6875rem; font-weight: 700; color: var(--gold); white-space: nowrap; font-variant-numeric: tabular-nums; }
.plate--compact .plate-hand { font-size: 0.5625rem; }
.plate-ready {
  position: absolute; top: -8px; right: -6px; z-index: 1;
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px; border-radius: 50%;
  background: rgb(var(--accent-active-rgb)); color: #141413; font-size: 0.625rem;
}
.plate-rank {
  position: absolute; top: -10px; left: -6px; z-index: 1;
  min-width: 20px; height: 20px; padding: 0 4px; box-sizing: border-box;
  border-radius: 10px; background: var(--gold); color: #141413;
  font-size: 0.75rem; font-weight: 700; line-height: 20px; text-align: center; font-variant-numeric: tabular-nums;
}
.plate-last {
  position: absolute; bottom: -9px; left: 50%; z-index: 1; transform: translateX(-50%);
  padding: 0 6px; border-radius: 999px; background: #f87171; color: #141413;
  font-size: 0.625rem; font-weight: 700; line-height: 16px; white-space: nowrap;
}
.plate-badge { position: absolute; top: 100%; left: 50%; z-index: 2; transform: translateX(-50%); }
.sr-only {
  position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}
</style>
