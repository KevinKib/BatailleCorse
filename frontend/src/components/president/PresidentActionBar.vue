<script setup lang="ts">
import { Button } from 'primevue';
import { useId } from 'vue';
import { useI18n } from '../../composables/useI18n';

// What I can do with my hand: the phase line, the Play button, Pass and Clear, and the hint line that says
// why the selection cannot be played. The screen decides what the primary button does; this only shows and emits.
defineProps<{
  phaseText: string;
  primaryLabel: string;
  canPrimary: boolean;
  canPass: boolean;
  hasSelection: boolean;
  hint: string;
  notice: string;
  /** The completion window is open for me, in turn or out of it. */
  canComplete?: boolean;
}>();
const emit = defineEmits<{ primary: []; pass: []; clear: []; complete: [] }>();

const ui = useI18n().presidentUi.actions;
const hintId = `president-hint-${useId()}`;
</script>

<template>
  <div class="action-bar" data-test="action-bar">
    <p class="phase-text" data-test="phase-text">{{ phaseText }}</p>
    <p :id="hintId" class="play-hint" data-test="play-hint">{{ hint }}</p>
    <p v-if="notice" class="notice" data-test="notice" role="alert">{{ notice }}</p>
    <div class="buttons">
      <Button v-if="canComplete" data-test="complete" class="action action--complete" :label="ui.complete"
              severity="warn" rounded @click="emit('complete')" />
      <Button data-test="play" class="action action--play" :label="primaryLabel" severity="success" rounded
              :disabled="!canPrimary" :aria-describedby="hintId" @click="emit('primary')" />
      <Button data-test="pass" class="action" :label="ui.pass" severity="secondary" rounded
              :disabled="!canPass" @click="emit('pass')" />
      <Button v-if="hasSelection" data-test="clear" class="action" :label="ui.clear" severity="secondary" variant="text"
              rounded @click="emit('clear')" />
    </div>
  </div>
</template>

<style scoped>
.action-bar { display: flex; flex-direction: column; align-items: center; gap: 4px; width: 100%; }
.phase-text { margin: 0; font-size: 0.85rem; font-weight: 700; color: #fff; text-align: center; }
.play-hint { margin: 0; min-height: 1.1rem; font-size: 0.75rem; color: rgba(255, 255, 255, 0.8); text-align: center; }
.notice {
  margin: 0; font-size: 0.75rem; color: rgb(var(--accent-negative-rgb)); text-align: center;
}
.buttons { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.action.p-button { min-height: 44px; min-width: 88px; }
</style>
