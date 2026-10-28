<script setup lang="ts">
import { computed } from 'vue';
import NinetyNineLives from './NinetyNineLives.vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import type { NinetyNinePlayer } from '../../model/ninetynine/NinetyNineState';
import { useI18n } from '../../composables/useI18n';
import { format, ordinal, plural } from '../../locales/format';

// The plate of a seat at the table: its name, its lives as hearts and the cards it holds; out, it shows its place.
// Two shapes: the plate of the ring (full on a wide felt, compact on a phone: no card count, the accessible name keeps
// it) and the chip of the rail (turn number, name, hearts). Names are user input: only ever rendered as text.
const props = defineProps<{
  player: NinetyNinePlayer;
  label: string;
  /** The lives each player started with. */
  maxLives: number;
  current?: boolean;
  mine?: boolean;
  /** Phone plate of the ring: no visible card count. */
  compact?: boolean;
  variant?: 'plate' | 'chip';
  /** The turn number shown on a chip. */
  order?: number;
  /** This seat just lost a life. */
  hit?: boolean;
  disconnected?: boolean;
  secondsRemaining?: number | null;
}>();
const ui = useI18n().ninetyNineUi;

const outText = computed(() => format(ui.table.outPlace, {
  place: props.player.place === null ? '' : ordinal(props.player.place, ui.ordinal),
}).replace(/\s*·\s*$/, ''));
const cardsText = computed(() => plural(ui.table.cards, props.player.handSize));
const accessibleName = computed(() => {
  if (props.player.eliminated) return `${props.label}, ${outText.value}`;
  const parts = [props.label, plural(ui.table.lives, props.player.lives), cardsText.value];
  return parts.join(', ');
});
const isChip = computed(() => props.variant === 'chip');
</script>

<template>
  <div class="plate"
       :class="{
         'plate--out': player.eliminated, 'plate--mine': mine, 'plate--current': current, 'plate--hit': hit,
         'plate--chip': isChip, 'plate--compact': compact && !isChip, 'plate--disconnected': disconnected,
       }"
       role="group"
       :aria-label="accessibleName"
       :aria-current="current ? 'true' : undefined"
       data-test="seat-plate"
       :data-seat="player.seat"
       :data-current="current ? 'true' : undefined">
    <span class="plate-top">
      <span v-if="isChip && order !== undefined && !player.eliminated" class="plate-order" data-test="seat-order" aria-hidden="true">{{ order }}</span>
      <span class="plate-name" data-test="seat-name" :title="label">{{ label }}</span>
    </span>
    <span v-if="player.eliminated" class="plate-out" data-test="seat-out" aria-hidden="true">{{ outText }}</span>
    <span v-else class="plate-stats" aria-hidden="true">
      <NinetyNineLives :lives="player.lives" :max="maxLives" :size="isChip || compact ? 10 : 12" />
      <span v-if="!compact && !isChip" class="plate-cards" data-test="seat-cards">{{ cardsText }}</span>
    </span>
    <span v-if="hit" class="plate-hit" data-test="seat-hit" aria-hidden="true">{{ ui.bust.hit }}</span>
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
  gap: 3px;
  min-width: 0;
  padding: 0 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #fff;
}
.plate--compact { align-items: center; gap: 2px; padding: 0 var(--space-1); border-radius: 8px; }
.plate--mine { border-color: rgba(245, 200, 66, 0.95); box-shadow: inset 0 0 0 1px rgba(245, 200, 66, 0.95); }
.plate--mine .plate-name { color: var(--gold); }
.plate--current { border-color: var(--turn-ring-color, var(--gold)); box-shadow: var(--turn-ring, 0 0 0 2px rgba(245, 200, 66, 0.7)); }
.plate--hit { border: 1.5px solid #f87171; }
.plate--out { opacity: 0.55; }
.plate--disconnected .plate-name { opacity: 0.6; }
.plate-top { display: flex; align-items: baseline; gap: var(--space-1); min-width: 0; max-width: 100%; }
.plate-name {
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.8125rem;
  font-weight: 700;
}
.plate-stats { display: flex; align-items: center; gap: var(--space-1); font-size: 0.6875rem; color: rgba(255, 255, 255, 0.7); }
.plate-cards { margin-left: var(--space-1); white-space: nowrap; }
.plate-out { font-size: 0.6875rem; font-weight: 700; letter-spacing: 0.08em; color: rgba(255, 255, 255, 0.85); white-space: nowrap; }
.plate--compact .plate-name { font-size: 0.6875rem; }
.plate--compact .plate-out { font-size: 0.625rem; letter-spacing: 0.04em; }
.plate-hit {
  position: absolute;
  right: -8px;
  top: -10px;
  z-index: 1;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f87171;
  color: #141413;
  font-size: 0.6875rem;
  font-weight: 700;
}
.plate-badge { position: absolute; top: 100%; left: 50%; z-index: 2; transform: translateX(-50%); }

/* The chip of the rail: a row of fixed height, the turn number before the name. */
.plate--chip { height: 44px; gap: 3px; padding: 0 6px; border-radius: 8px; background: rgba(255, 255, 255, 0.06); }
.plate--chip .plate-name { font-size: 0.75rem; }
.plate-order { font-size: 0.625rem; opacity: 0.7; font-variant-numeric: tabular-nums; }
.plate--chip.plate--mine { background: var(--gold); border-color: var(--gold); box-shadow: none; color: #141413; }
.plate--chip.plate--mine .plate-name, .plate--chip.plate--mine .plate-stats { color: #141413; }
.plate--chip.plate--current { background: rgba(245, 200, 66, 0.18); }
.plate--chip.plate--mine.plate--current { background: var(--gold); }
</style>
