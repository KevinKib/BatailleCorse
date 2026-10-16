<script setup lang="ts">
import { ref } from 'vue';
import type { PresidentStanding } from '../../model/president/PresidentState';
import { useI18n } from '../../composables/useI18n';
import PresidentStandings from './PresidentStandings.vue';

// The "Scores" button of the top bar and the panel it opens: the running standings, at any time of the match
// (the only scoreboard of an endless match).
defineProps<{
  standings: PresidentStanding[];
  nameOf: (seat: number) => string;
  mySeat: number;
}>();
const ui = useI18n().presidentUi;
const open = ref(false);
</script>

<template>
  <div class="scores">
    <button type="button" class="scores-button" data-test="scores" :aria-expanded="open"
            :aria-label="ui.standings.scores" @click="open = !open">
      <i class="pi pi-list" aria-hidden="true"></i><span class="scores-label">{{ ui.standings.scores }}</span>
    </button>
    <section v-if="open" class="scores-panel" data-test="scores-panel" role="region" :aria-label="ui.standings.title">
      <h2 class="scores-title">{{ ui.standings.title }}</h2>
      <PresidentStandings :standings="standings" :name-of="nameOf" :my-seat="mySeat" />
      <button type="button" class="scores-close" data-test="scores-close" @click="open = false">{{ ui.standings.close }}</button>
    </section>
  </div>
</template>

<style scoped>
.scores-button {
  display: inline-flex; align-items: center; gap: 6px; height: var(--hud-chip-h); box-sizing: border-box;
  padding: 0 12px; font: inherit; font-size: 0.8rem; font-weight: 700; font-family: var(--font-title);
  letter-spacing: 0.06em; text-transform: uppercase; color: rgba(255, 255, 255, 0.8); cursor: pointer;
  background: rgba(0, 0, 0, 0.45); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 999px;
}
/* Phones: the top bar is full, the button keeps its icon and its accessible name. */
@media (max-width: 420px) {
  .scores-label { display: none; }
  .scores-button { width: var(--hud-chip-h); justify-content: center; padding: 0; }
}
.scores-panel {
  position: absolute; top: 56px; left: 50%; transform: translateX(-50%); z-index: 1700;
  display: flex; flex-direction: column; align-items: center; gap: var(--space-2);
  width: min(320px, calc(100% - 24px)); box-sizing: border-box; padding: var(--space-3);
  max-height: 70%; overflow-y: auto;
  background: var(--panel-bg); border: 1px solid var(--panel-border); box-shadow: var(--panel-shadow); border-radius: 14px;
}
.scores-title { margin: 0; font-family: var(--font-title); font-size: 1rem; color: var(--gold); }
.scores-close {
  min-height: 36px; padding: 0 16px; font: inherit; color: inherit; cursor: pointer;
  background: transparent; border: 1px solid rgba(255, 255, 255, 0.25); border-radius: 999px;
}
</style>
