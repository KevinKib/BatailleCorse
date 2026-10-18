<script setup lang="ts">
import { computed } from 'vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import PresidentHandFan from './PresidentHandFan.vue';
import type { SeatBubble } from '../../model/president/motion';
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
  /** An opponent's plate: the hand as a fan of card backs (my own hand is on screen). */
  fan?: boolean;
  /** The count shown, when it differs from the state's (cards still flying to the pile). */
  count?: number | null;
  /** Phone layout: a smaller fan. */
  narrow?: boolean;
  /** A bubble over the plate ("Pass"...); a new `key` pops it again. */
  bubble?: { kind: SeatBubble['kind']; key: number } | null;
  /** No pop (reduced motion, Instant). */
  still?: boolean;
}>();
const ui = useI18n().presidentUi;
const shownCount = computed(() => props.count ?? props.player.handSize);
const outText = computed(() => {
  const place = props.player.place;
  if (!props.player.forbiddenFinish) return format(ui.table.out, { n: place ?? '' });
  return place === null ? ui.table.outForbidden : format(ui.table.outForbiddenPlace, { n: place });
});
const title = computed(() => props.player.title ? ui.titles[titleKey(props.player.title) as PresidentTitleKey] : null);
</script>

<template>
  <div class="president-seat"
       :class="{
         'president-seat--out': player.out, 'president-seat--disconnected': disconnected,
         'president-seat--passed': player.passed && !player.out,
       }"
       :data-seat="player.seat">
    <span v-if="bubble" :key="bubble.key" class="seat-bubble" :class="[`seat-bubble--${bubble.kind}`, { 'seat-bubble--still': still }]"
          data-test="seat-bubble" :data-kind="bubble.kind" aria-hidden="true">{{ ui.motion.bubbles[bubble.kind] }}</span>
    <span class="seat-label" :class="{ 'seat-label--active': current }" data-test="seat-label" :title="label">{{ label }}</span>
    <span v-if="title" class="seat-title" data-test="seat-title">{{ title }}</span>
    <PresidentHandFan v-if="fan" :count="shownCount" :narrow="narrow" />
    <span v-else class="seat-stats" data-test="seat-cards">{{ plural(ui.table.cards, shownCount) }}</span>
    <span v-if="current || player.out || player.passed || (showReady && player.ready)" class="seat-markers">
      <span v-if="current" class="marker marker--current" data-test="seat-current">{{ ui.table.current }}</span>
      <span v-if="player.out" class="marker marker--out" :class="{ 'marker--forbidden': player.forbiddenFinish }"
            data-test="seat-out">{{ outText }}</span>
      <span v-else-if="player.passed" class="marker" data-test="seat-passed">{{ ui.table.passed }}</span>
      <span v-if="showReady && player.ready" class="marker marker--ready" data-test="seat-ready"><i class="pi pi-check" aria-hidden="true"></i>{{ ui.table.ready }}</span>
    </span>
    <SeatDisconnectBadge v-if="disconnected" :seconds-remaining="secondsRemaining ?? null" />
  </div>
</template>

<style scoped>
.president-seat {
  position: relative;
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
.marker--forbidden { color: rgb(var(--accent-negative-rgb)); }
.president-seat--out .seat-label { opacity: 0.7; }
/* A seat that passed sits back while the trick lasts. */
.president-seat--passed .seat-label, .president-seat--passed :deep(.fan-picture) { opacity: 0.55; }

/* The bubble of a move: a speech bubble over the plate that pops and stays a moment (the screen removes it). */
.seat-bubble {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  z-index: 3;
  transform: translateX(-50%);
  padding: 3px 10px;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  color: #1b1b1b;
  background: #f4f1e6;
  border-radius: 999px;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.5);
  pointer-events: none;
  animation: bubble-pop 220ms cubic-bezier(.2, .8, .2, 1.4) both;
}
.seat-bubble::after {
  content: "";
  position: absolute;
  top: 100%;
  left: 50%;
  margin-left: -5px;
  border: 5px solid transparent;
  border-top-color: #f4f1e6;
}
.seat-bubble--outForbidden { color: #fff; background: rgb(var(--accent-negative-rgb)); }
.seat-bubble--outForbidden::after { border-top-color: rgb(var(--accent-negative-rgb)); }
.seat-bubble--completed { background: var(--gold); }
.seat-bubble--completed::after { border-top-color: var(--gold); }
.seat-bubble--still { animation: none; }
@keyframes bubble-pop {
  from { opacity: 0; transform: translate(-50%, 6px) scale(0.6); }
  to { opacity: 1; transform: translate(-50%, 0) scale(1); }
}
@media (prefers-reduced-motion: reduce) { .seat-bubble { animation: none; } }
.president-seat--disconnected .seat-label { opacity: 0.6; }

@media (max-width: 767px) {
  .seat-label { font-size: 0.62rem; letter-spacing: 0.02em; padding: 2px 6px; }
  .seat-label--active { padding-left: 14px; }
  .seat-label--active::before { left: 5px; }
  .seat-title, .seat-stats { font-size: 0.6rem; }
  .marker { font-size: 0.56rem; padding: 1px 4px; }
}
</style>
