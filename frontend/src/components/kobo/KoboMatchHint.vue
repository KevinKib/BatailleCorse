<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';

// How to match. `hint`: the one line under the piles. `coach`: the first-time note (instead of the line), paired
// with a ring around my hand drawn by the screen. Both are static text, so reduced motion changes nothing; the one
// live region of the screen is not repeated here. Where it sits (under the piles, or beside my hand on a wide
// table that has no height to spare) is the screen's choice.
// With `reserve` the zone is always there, empty when there is nothing to say, so a line that comes or goes moves
// nothing around it (the screen sizes the zone); the first-time note then floats over what is under it.
defineProps<{ hint: boolean; coach: boolean; reserve?: boolean }>();
defineEmits<{ dismiss: [] }>();
const ui = useI18n().koboUi.match;
</script>

<template>
  <div v-if="hint || coach || reserve" class="match-hint-zone" :class="{ 'match-hint-zone--reserved': reserve }">
    <div v-if="coach" class="coach" role="note" data-test="match-coach">
      <p class="coach-text"><strong>{{ ui.coachTitle }}</strong> {{ ui.coachText }}</p>
      <button type="button" class="coach-dismiss" data-test="match-coach-dismiss" @click="$emit('dismiss')">
        {{ ui.gotIt }}
      </button>
    </div>
    <p v-else-if="hint" class="match-hint" data-test="match-hint">{{ ui.hint }}</p>
  </div>
</template>

<style scoped>
/* One line on a wide table, two on a phone. */
.match-hint-zone { display: flex; justify-content: center; max-width: min(560px, 100%); margin: 0 auto; }
/* Reserved: the height of the line (two lines on a phone), whatever is shown; the note floats over the table. */
.match-hint-zone--reserved { position: relative; box-sizing: border-box; min-height: calc(4px + 1.3 * 0.75rem); }
.match-hint-zone--reserved .coach { position: absolute; top: 0; left: 0; right: 0; z-index: 3; }
@media (max-width: 767px) {
  .match-hint-zone--reserved { min-height: calc(2px + 2 * 1.25 * 0.7rem); }
  .match-hint-zone--reserved .match-hint { margin-top: 2px; font-size: 0.7rem; line-height: 1.25; }
}
.match-hint {
  margin: 4px 0 0;
  font-size: 0.75rem;
  line-height: 1.3;
  text-align: center;
  color: rgba(255, 255, 255, 0.78);
}
.coach {
  display: flex;
  align-items: center;
  gap: 8px;
  box-sizing: border-box;
  margin-top: 4px;
  padding: 6px 6px 6px 12px;
  border-radius: 12px;
  background: #fbfaf4;
  color: #1b1b1b;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.5);
}
.coach-text { flex: 1; margin: 0; font-size: 0.78rem; line-height: 1.3; }
.coach-dismiss {
  flex: none;
  min-width: 72px;
  min-height: 44px;
  border: 0;
  border-radius: 22px;
  background: #0d2e18;
  color: #f5e29a;
  font: 700 0.8rem/1 inherit;
  cursor: pointer;
}
.coach-dismiss:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
</style>
