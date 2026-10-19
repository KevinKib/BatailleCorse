<script setup lang="ts">
import { computed } from 'vue';
import type { PresidentPlayer } from '../../model/president/PresidentState';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';

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
}>();
const ui = useI18n().presidentUi;
const outText = computed(() => {
  const place = props.player.place;
  if (!props.player.forbiddenFinish) return format(ui.table.out, { n: place ?? '' });
  return place === null ? ui.table.outForbidden : format(ui.table.outForbiddenPlace, { n: place });
});
</script>

<template>
  <span class="markers">
    <!-- A narrow plate has no room for it: the lit name says it on screen, the marker stays for screen readers. -->
    <span v-if="current" class="marker marker--current" :class="{ 'sr-only': narrow }" data-test="seat-current">{{ ui.table.current }}</span>
    <span v-if="player.out" class="marker marker--out" :class="{ 'marker--forbidden': player.forbiddenFinish }"
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
.marker--current { color: rgb(var(--accent-active-rgb)); }
.marker--ready { color: rgb(var(--accent-positive-rgb)); }
.marker--out { color: var(--gold); }
.marker--forbidden { color: rgb(var(--accent-negative-rgb)); }
@media (max-width: 767px) {
  .marker { font-size: 0.56rem; padding: 1px 4px; white-space: normal; }
  .marker--ready { padding: 1px 3px; }
  .marker--ready .pi { margin-right: 0; }
}
</style>
