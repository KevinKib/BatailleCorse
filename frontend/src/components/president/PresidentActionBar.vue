<script setup lang="ts">
import { Button } from 'primevue';
import { useId } from 'vue';
import { useI18n } from '../../composables/useI18n';

// What I can do with my hand: the phase line, the Play button, Pass and Clear, and the hint line that says
// why the selection cannot be played. The screen decides what the primary button does; this only shows and emits.
// Everything has its place whether it is shown or not (the table holds still): Clear is always there, disabled while
// nothing is selected; the hint line is two lines high; a refusal notice and the Complete button float over that line.
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
    <div class="help-slot" data-test="help-slot">
      <p :id="hintId" class="play-hint" data-test="play-hint">{{ hint }}</p>
      <p v-if="notice" class="notice" data-test="notice" role="alert">{{ notice }}</p>
      <Button v-if="canComplete" data-test="complete" class="action--complete" :label="ui.complete"
              severity="warn" size="small" rounded @click="emit('complete')" />
    </div>
    <div class="buttons" data-test="buttons">
      <Button data-test="play" class="action action--play" :label="primaryLabel" severity="success" rounded
              :disabled="!canPrimary" :aria-describedby="hintId" @click="emit('primary')" />
      <Button data-test="pass" class="action" :label="ui.pass" severity="secondary" rounded
              :disabled="!canPass" @click="emit('pass')" />
      <Button data-test="clear" class="action" :label="ui.clear" severity="secondary" variant="text" rounded
              :disabled="!hasSelection" @click="emit('clear')" />
    </div>
  </div>
</template>

<style scoped>
.action-bar { display: flex; flex-direction: column; align-items: center; gap: 4px; width: 100%; }
.phase-text {
  margin: 0; width: 100%; height: 1.25rem; line-height: 1.25rem; font-size: 0.85rem; font-weight: 700; color: #fff;
  text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
/* Two lines of help, whether there is something to say or not. */
.help-slot { position: relative; width: 100%; height: 2.3rem; }
.play-hint, .notice {
  margin: 0; box-sizing: border-box; width: 100%; height: 100%; font-size: 0.75rem; line-height: 1.15rem; text-align: center;
  overflow: hidden;
}
.play-hint { color: rgba(255, 255, 255, 0.8); }
.notice {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: rgb(var(--accent-negative-rgb));
  background: rgba(0, 0, 0, 0.82); border-radius: 8px;
}
.action--complete.p-button {
  position: absolute; z-index: 2; left: 50%; top: 50%; transform: translate(-50%, -50%); min-height: 36px; white-space: nowrap;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.5);
}
.buttons { display: flex; flex-wrap: nowrap; justify-content: center; gap: 8px; }
.action.p-button { min-height: 44px; min-width: 88px; }
/* A phone has no room to spare: the same two lines, a little tighter. */
@media (max-width: 767px) {
  .phase-text { height: 1.1rem; line-height: 1.1rem; }
  .help-slot { height: 1.9rem; }
  .play-hint, .notice { line-height: 0.95rem; }
}
@media (max-width: 400px) { .action.p-button { min-width: 80px; } .buttons { gap: 6px; } }
</style>
