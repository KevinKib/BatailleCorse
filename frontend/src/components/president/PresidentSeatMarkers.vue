<script setup lang="ts">
import { computed } from 'vue';
import type { PresidentPlayer } from '../../model/president/PresidentState';
import { useI18n } from '../../composables/useI18n';
import { format, ordinal } from '../../locales/format';

// What a seat says about the moment: playing now, passed, out (and in which place), ready. Drawn over the fan of an
// opponent and beside my own name, never on a line of its own: a marker that comes or goes moves nothing.
const props = defineProps<{
  player: PresidentPlayer;
  current?: boolean;
  /** Ready checks only matter at the round-over barrier. */
  showReady?: boolean;
  /** A phone: the lit name says "playing" on screen, and Ready is a check (the text stays for screen readers). */
  narrow?: boolean;
  compact?: boolean;
  /** Mine: the markers stand beside my name, so the medal is the compact variant. */
  side?: boolean;
}>();
const ui = useI18n().presidentUi;
/** Out for good (a forbidden finish keeps its red marker): a gold medal with the place, as ordinal text. */
const medal = computed(() => {
  const place = props.player.place;
  if (!props.player.out || props.player.forbiddenFinish || place === null) return null;
  const text = ordinal(place, ui.ordinal);
  return { place, text, label: format(ui.table.medal.label, { place: text }) };
});
const outText = computed(() => {
  const place = props.player.place;
  return place === null ? ui.table.outForbidden : format(ui.table.outForbiddenPlace, { n: place });
});
</script>

<template>
  <span class="markers">
    <!-- A narrow plate has no room for it: the lit name says it on screen, the marker stays for screen readers. -->
    <span v-if="current" class="marker marker--current" :class="{ 'sr-only': narrow }" data-test="seat-current">{{ ui.table.current }}</span>
    <span v-if="medal" class="medal" :class="{ 'medal--side': side }" data-test="seat-out" :data-place="medal.place">
      <span class="medal-disc" data-test="seat-medal-place" aria-hidden="true">{{ medal.text }}</span>
      <span class="medal-word" data-test="seat-medal-word" aria-hidden="true">{{ ui.table.medal.out }}</span>
      <span class="sr-only">{{ medal.label }}</span>
    </span>
    <span v-else-if="player.out && player.forbiddenFinish" class="marker marker--out marker--forbidden"
          data-test="seat-out">{{ outText }}</span>
    <span v-else-if="player.passed" class="marker" data-test="seat-passed">{{ ui.table.passed }}</span>
    <span v-if="showReady && player.ready" class="marker marker--ready" data-test="seat-ready"><i class="pi pi-check" aria-hidden="true"></i><span class="ready-text" :class="{ 'sr-only': compact }">{{ ui.table.ready }}</span></span>
  </span>
</template>

<style scoped>
.markers { display: inline-flex; flex-wrap: nowrap; align-items: center; justify-content: center; gap: 2px 4px; max-width: 100%; }
.marker {
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.82);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  padding: 1px 6px;
  line-height: 1.1;
  text-align: center;
  white-space: nowrap;
}
.marker .pi { font-size: 0.55rem; margin-right: 3px; }
.sr-only {
  position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}
/* The medal of a seat that went out: a gold disc as tall as the fan it replaces (--fan-h, set by the fan: 23 px on a
   phone, 32 px wide) with the place, and "Out" beside it. Mine: a compact disc beside my name. It never exceeds the
   fan's height, so nothing moves. */
.medal { display: inline-flex; align-items: center; gap: 4px; max-width: 100%; }
.medal-disc {
  display: inline-flex; align-items: center; justify-content: center; flex: none; box-sizing: border-box;
  width: var(--fan-h, 32px); height: var(--fan-h, 32px); border-radius: 50%;
  font-size: 0.7rem; font-weight: 800; line-height: 1;
  color: #1b1b1b; background: var(--gold); box-shadow: 0 1px 3px rgba(0, 0, 0, 0.55);
}
.medal-word {
  font-size: 0.62rem; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; line-height: 1.1;
  color: var(--gold); background: rgba(0, 0, 0, 0.82); border-radius: 8px; padding: 1px 5px; white-space: nowrap;
}
.medal--side .medal-disc { width: 18px; height: 18px; font-size: 0.58rem; }
.marker--current { color: rgb(var(--accent-active-rgb)); }
.marker--ready { color: rgb(var(--accent-positive-rgb)); }
.marker--out { color: var(--gold); }
.marker--forbidden { color: rgb(var(--accent-negative-rgb)); }
@media (max-width: 767px) {
  .medal-disc { font-size: 0.56rem; }
  .medal-word { font-size: 0.56rem; padding: 1px 3px; }
  .medal--side .medal-disc { font-size: 0.52rem; }
  .marker { font-size: 0.56rem; padding: 1px 4px; white-space: normal; }
  .marker--ready { padding: 1px 3px; }
  .marker--ready .pi { margin-right: 0; }
}
</style>
