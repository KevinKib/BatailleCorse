<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';

// Under the piles: how to match, in one line, while a tap on a card is a match (`hint`). The first time, the same
// room holds a note instead (`coach`), paired with a ring around my hand drawn by the screen. Both are static
// text, so reduced motion changes nothing; the one live region of the screen is not repeated here.
defineProps<{ hint: boolean; coach: boolean }>();
defineEmits<{ dismiss: [] }>();
const ui = useI18n().koboUi.match;
</script>

<template>
  <div v-if="hint" class="match-hint-zone">
    <div v-if="coach" class="coach" role="note" data-test="match-coach">
      <p class="coach-text"><strong>{{ ui.coachTitle }}</strong> {{ ui.coachText }}</p>
      <button type="button" class="coach-dismiss" data-test="match-coach-dismiss" @click="$emit('dismiss')">
        {{ ui.gotIt }}
      </button>
    </div>
    <p v-else class="match-hint" data-test="match-hint">{{ ui.hint }}</p>
  </div>
</template>

<style scoped>
.match-hint-zone { display: flex; justify-content: center; max-width: 330px; margin: 0 auto; }
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
