<script setup lang="ts">
import { computed } from 'vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import type { PresidentPlayer } from '../../model/president/PresidentState';
import { titleKey } from '../../model/president/titles';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import type { PresidentTitleKey } from '../../locales/Messages';

// The name plate of an opponent (or of me): name, title as text, cards held, and the markers of the moment.
// Names are user input: only ever rendered as text.
const props = defineProps<{
  player: PresidentPlayer;
  label: string;
  current?: boolean;
  /** Ready checks only matter at the round-over barrier. */
  showReady?: boolean;
  disconnected?: boolean;
  secondsRemaining?: number | null;
}>();
const ui = useI18n().presidentUi;
const title = computed(() => props.player.title ? ui.titles[titleKey(props.player.title) as PresidentTitleKey] : null);
</script>

<template>
  <div class="president-seat" :class="{ 'president-seat--out': player.out, 'president-seat--disconnected': disconnected }"
       :data-seat="player.seat">
    <span class="seat-label" :class="{ 'seat-label--active': current }" data-test="seat-label" :title="label">{{ label }}</span>
    <span v-if="title" class="seat-title" data-test="seat-title">{{ title }}</span>
    <span class="seat-stats" data-test="seat-cards">{{ plural(ui.table.cards, player.handSize) }}</span>
    <span v-if="current || player.out || player.passed || (showReady && player.ready)" class="seat-markers">
      <span v-if="current" class="marker marker--current" data-test="seat-current">{{ ui.table.current }}</span>
      <span v-if="player.out" class="marker marker--out" data-test="seat-out">{{ format(ui.table.out, { n: player.place ?? '' }) }}</span>
      <span v-else-if="player.passed" class="marker" data-test="seat-passed">{{ ui.table.passed }}</span>
      <span v-if="showReady && player.ready" class="marker marker--ready" data-test="seat-ready"><i class="pi pi-check" aria-hidden="true"></i>{{ ui.table.ready }}</span>
    </span>
    <SeatDisconnectBadge v-if="disconnected" :seconds-remaining="secondsRemaining ?? null" />
  </div>
</template>

<style scoped>
.president-seat {
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
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 3px 10px;
}
.seat-label--active {
  position: relative;
  padding-left: 20px;
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
.seat-title {
  max-width: 100%;
  font-size: 0.68rem;
  font-weight: 700;
  color: var(--gold);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.seat-stats {
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.8);
  white-space: nowrap;
}
.seat-markers { display: inline-flex; flex-wrap: wrap; justify-content: center; gap: 2px 4px; }
.marker {
  font-size: 0.62rem;
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
.marker--out { color: var(--gold); }
.president-seat--out .seat-label { opacity: 0.7; }
.president-seat--disconnected .seat-label { opacity: 0.6; }

@media (max-width: 767px) {
  .seat-label { font-size: 0.62rem; letter-spacing: 0.02em; padding: 2px 6px; }
  .seat-label--active { padding-left: 14px; }
  .seat-label--active::before { left: 5px; }
  .seat-title, .seat-stats { font-size: 0.6rem; }
  .marker { font-size: 0.56rem; padding: 1px 4px; }
}
</style>
