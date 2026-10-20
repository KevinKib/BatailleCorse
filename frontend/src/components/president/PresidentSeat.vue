<script setup lang="ts">
import { computed } from 'vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import PresidentHandFan from './PresidentHandFan.vue';
import PresidentSeatMarkers from './PresidentSeatMarkers.vue';
import type { SeatBubble } from '../../model/president/motion';
import type { PresidentPlayer } from '../../model/president/PresidentState';
import { titleKey } from '../../model/president/titles';
import { useI18n } from '../../composables/useI18n';
import { plural } from '../../locales/format';
import type { PresidentTitleKey } from '../../locales/Messages';

// The name plate of an opponent (or of me): name, title as text, cards held, and the markers of the moment.
// Names are user input: only ever rendered as text.
// Every part has its place whether it is shown or not (the turn dot, the title line, the fan of a seat that is out,
// the markers drawn over the fan or beside my name): a text that comes or goes never moves the plate.
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
  /** Phone layout, tight: Ready is a check (the text stays for screen readers). */
  compact?: boolean;
  /** The match has titles to come (more than one round): the line under the name is kept free for them. */
  reserveTitle?: boolean;
  /** A bubble over the plate ("Pass"...); a new `key` pops it again. */
  bubble?: { kind: SeatBubble['kind']; key: number } | null;
  /** No pop (reduced motion, Instant). */
  still?: boolean;
}>();
const ui = useI18n().presidentUi;
const shownCount = computed(() => props.count ?? props.player.handSize);
const title = computed(() => props.player.title ? ui.titles[titleKey(props.player.title) as PresidentTitleKey] : null);
const hasMarkers = computed(() =>
  Boolean(props.current || props.player.out || props.player.passed || (props.showReady && props.player.ready)));
</script>

<template>
  <div class="president-seat"
       :class="{
         'president-seat--out': player.out, 'president-seat--disconnected': disconnected,
         'president-seat--passed': player.passed && !player.out, 'president-seat--mine': !fan,
       }"
       :data-seat="player.seat">
    <span v-if="bubble" :key="bubble.key" class="seat-bubble" :class="[`seat-bubble--${bubble.kind}`, { 'seat-bubble--still': still }]"
          data-test="seat-bubble" :data-kind="bubble.kind" aria-hidden="true">{{ ui.motion.bubbles[bubble.kind] }}</span>
    <span class="seat-label" :class="{ 'seat-label--active': current }" data-test="seat-label" :title="label"><i class="turn-dot" :class="{ 'turn-dot--on': current }" aria-hidden="true"></i>{{ label }}</span>
    <template v-if="fan">
      <span v-if="title" class="seat-title" data-test="seat-title">{{ title }}</span>
      <span v-else-if="reserveTitle" class="seat-title" data-test="seat-title-slot" aria-hidden="true">&nbsp;</span>
    </template>
    <span class="seat-hand">
      <PresidentHandFan v-if="fan" :count="shownCount" :narrow="narrow">
        <!-- On the fan, which a seat that is out no longer has: the markers take its place and the plate keeps its size. -->
        <PresidentSeatMarkers v-if="hasMarkers" class="seat-markers seat-markers--overlay" :player="player" :current="current"
                              :show-ready="showReady" :narrow="narrow" :compact="compact" />
      </PresidentHandFan>
      <span v-else class="seat-stats" data-test="seat-cards">{{ plural(ui.table.cards, shownCount) }}</span>
    </span>
    <!-- Mine: the title and the markers stand beside the name, the count on its other side. -->
    <span v-if="!fan" class="seat-side">
      <span v-if="title" class="seat-title" data-test="seat-title">{{ title }}</span>
      <PresidentSeatMarkers v-if="hasMarkers" class="seat-markers seat-markers--side" :player="player" :current="current"
                            :show-ready="showReady" :narrow="narrow" :compact="compact" />
    </span>
    <span v-if="disconnected" class="seat-badge-slot"><SeatDisconnectBadge :seconds-remaining="secondsRemaining ?? null" /></span>
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
  position: relative;
  max-width: var(--seat-label-max, 9rem);
  box-sizing: border-box;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  line-height: 1.2;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  /* The room of the turn dot is always there, lit or not. */
  padding: 3px 10px 3px 20px;
}
.seat-label--active {
  color: #fff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}
.turn-dot {
  position: absolute;
  left: 8px;
  top: 50%;
  width: var(--turn-dot-size);
  height: var(--turn-dot-size);
  margin-top: calc(var(--turn-dot-size) / -2);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
}
.turn-dot--on { background: var(--gold); }
.seat-title {
  max-width: 100%;
  font-size: 0.68rem;
  font-weight: 700;
  line-height: 1.2;
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
.seat-hand { position: relative; display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 0; max-width: 100%; }
.seat-markers--overlay { pointer-events: none; }
.president-seat--out .seat-label { opacity: 0.7; }
/* A seat that passed sits back while the trick lasts. */
.president-seat--passed .seat-label, .president-seat--passed :deep(.fan-picture) { opacity: 0.55; }
.seat-badge-slot { position: absolute; top: 100%; left: 50%; z-index: 2; transform: translateX(-50%); }

/* Mine: one row of fixed height, the name always at the middle (the count on its left, the title and the markers on its
   right, in columns of equal width), so that nothing that comes or goes moves the name. */
.president-seat--mine {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  column-gap: 8px;
  width: 100%;
  height: 30px;
}
.president-seat--mine .seat-label { grid-column: 2; grid-row: 1; }
.president-seat--mine .seat-hand { grid-column: 1; grid-row: 1; align-items: flex-end; }
.president-seat--mine .seat-side {
  grid-column: 3; grid-row: 1; min-width: 0; display: flex; align-items: center; gap: 6px; justify-content: flex-start; overflow: hidden;
}
.president-seat--mine .seat-title { flex: 0 1 auto; min-width: 0; }

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
  .seat-label { font-size: 0.62rem; letter-spacing: 0.02em; padding: 2px 6px 2px 14px; }
  .turn-dot { left: 5px; }
  .seat-title, .seat-stats { font-size: 0.6rem; line-height: 1.1; }
  .president-seat { gap: 1px; }
  .president-seat--mine { gap: 0; }
  .president-seat--mine .seat-label { font-size: 0.8rem; padding: 4px 14px 4px 22px; }
  /* No room above a seat at the top of the felt: the bubble sits over the fan instead. */
  .seat-bubble { bottom: auto; top: 14px; font-size: 0.68rem; box-shadow: 0 2px 10px rgba(0, 0, 0, 0.6); }
  .seat-bubble::after { display: none; }
}
</style>
