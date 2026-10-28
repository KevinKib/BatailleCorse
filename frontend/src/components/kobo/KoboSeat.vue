<script setup lang="ts">
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import type { KoboPlayer } from '../../model/kobo/KoboState';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';

// The name plate of a player: name, total points, number of cards, and the markers of the moment. Names are user
// input: only ever rendered as text.
defineProps<{
  player: KoboPlayer;
  label: string;
  ready?: boolean;
  announcer?: boolean;
  finalTurn?: boolean;
  disconnected?: boolean;
  secondsRemaining?: number | null;
}>();
const ui = useI18n().koboUi.table;
</script>

<template>
  <div class="kobo-seat" :class="{ 'kobo-seat--disconnected': disconnected }" :data-seat="player.seat">
    <span class="seat-label" :class="{ 'seat-label--active': player.current }" data-test="seat-label"
          :title="label">{{ label }}</span>
    <span class="seat-line">
    <span class="seat-stats">
      <span data-test="seat-total">{{ format(ui.total, { n: player.total }) }}</span>
      <span class="seat-sep" aria-hidden="true">·</span>
      <span data-test="seat-cards">{{ plural(ui.cards, player.handSize) }}</span>
    </span>
    <!-- Always there (its line is reserved): a marker that comes or goes moves nothing below the plate. -->
    <span class="seat-markers">
      <span v-if="player.current" class="marker marker--current" data-test="seat-current">{{ ui.current }}</span>
      <span v-if="ready" class="marker marker--ready" data-test="seat-ready"><i class="pi pi-check" aria-hidden="true"></i>{{ ui.ready }}</span>
      <span v-if="announcer" class="marker marker--announcer" data-test="seat-announcer">{{ ui.announcer }}</span>
      <span v-if="finalTurn" class="marker marker--final" data-test="seat-final-turn">{{ ui.finalTurn }}</span>
    </span>
    </span>
    <SeatDisconnectBadge v-if="disconnected" :seconds-remaining="secondsRemaining ?? null" />
  </div>
</template>

<style scoped>
.kobo-seat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  min-width: 0;
  max-width: 100%;
}
.seat-label {
  max-width: var(--seat-label-max, 9rem);
  box-sizing: border-box;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 3px 10px;
}
.seat-label--active {
  position: relative;
  padding-left: 22px;
  color: #fff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}
.seat-label--active::before {
  content: "";
  position: absolute;
  left: 8px;
  top: 50%;
  width: var(--turn-dot-size);
  height: var(--turn-dot-size);
  margin-top: calc(var(--turn-dot-size) / -2);
  border-radius: 50%;
  background: var(--gold);
}
.seat-stats {
  display: inline-flex;
  gap: 4px;
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.75);
  white-space: nowrap;
}
.seat-markers { display: inline-flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 2px 4px; }
.marker {
  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.5);
  border-radius: 999px;
  padding: 1px 6px;
  white-space: nowrap;
}
.marker .pi { font-size: 0.55rem; margin-right: 3px; }
.marker--current { color: rgb(var(--accent-active-rgb)); }
.marker--ready { color: rgb(var(--accent-positive-rgb)); }
.marker--announcer { color: var(--gold); border: 1px solid var(--gold); }
.marker--final { color: rgb(var(--accent-negative-rgb)); }
.kobo-seat--disconnected .seat-label { opacity: 0.6; }

/* The stats and the markers share one line (its height is reserved: a marker that comes or goes moves nothing). */
.seat-line { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 2px 4px; min-height: 1.1rem; }
.seat-markers:empty { display: none; }
@media (max-width: 767px) {
  .seat-label { font-size: 0.62rem; letter-spacing: 0.02em; padding: 2px 6px; }
  .seat-label--active { padding-left: 18px; }
  .seat-label--active::before { left: 6px; }
  .seat-stats { font-size: 0.6rem; }
  .seat-line { min-height: 0.9rem; }
}
</style>
