<script setup lang="ts">
import { computed } from 'vue';
import { Button } from 'primevue';
import YourTurnIndicator from '../YourTurnIndicator.vue';
import type { KoboState } from '../../model/kobo/KoboState';
import type { KoboPending, KoboPromptKey } from '../../model/kobo/interaction';
import { barButtons, type KoboBarButton } from '../../model/kobo/actions';
import type { KoboNotice } from '../../state/Kobo.store';
import { useI18n } from '../../composables/useI18n';

// The bar under my cards: the turn cue, one line (the instruction while a click on a card is expected, else the
// phase), the buttons the server lists, and why my last command failed. It decides nothing: the buttons come from
// `availableActions` (see `barButtons`), a click on a card is the table's business. Cancel and Hide are local:
// they only drop a half-made selection or turn the peeked card face down.
const props = defineProps<{
  game: KoboState;
  prompt: KoboPromptKey | null;
  phaseText: string;
  yourTurn: boolean;
  pending: KoboPending;
  busy: boolean;
  notice: KoboNotice | null;
  canHide: boolean;
}>();
const emit = defineEmits<{
  ready: []; draw: []; discard: []; skip: []; 'end-turn': []; announce: []; cancel: []; hide: [];
}>();

const ui = useI18n().koboUi;
const buttons = computed(() => barButtons(props.game));

const EVENT: Record<KoboBarButton, 'ready' | 'draw' | 'discard' | 'skip' | 'end-turn' | 'announce'> = {
  ready: 'ready', draw: 'draw', discard: 'discard', skip: 'skip', endTurn: 'end-turn', announce: 'announce',
};
const SEVERITY: Record<KoboBarButton, 'success' | 'secondary' | 'warn' | 'danger'> = {
  ready: 'success', draw: 'success', discard: 'secondary', skip: 'secondary', endTurn: 'success', announce: 'danger',
};
</script>

<template>
  <div class="kobo-actions" data-test="kobo-actions">
    <p v-if="notice" class="notice" data-test="notice" role="alert">{{ ui.notice[notice] }}</p>
    <div class="actions-row">
      <div class="actions-text">
        <YourTurnIndicator :active="yourTurn" />
        <p v-if="prompt" class="line prompt" data-test="prompt" aria-live="polite">{{ ui.prompt[prompt] }}</p>
        <p v-else class="line" data-test="phase" aria-live="polite">{{ phaseText }}</p>
      </div>
      <div class="actions-buttons">
        <Button
          v-for="id in buttons"
          :key="id"
          :data-test="`action-${id}`"
          class="action-button"
          :label="ui.actions[id]"
          :severity="SEVERITY[id]"
          rounded
          :disabled="busy"
          @click="emit(EVENT[id])" />
        <Button
          v-if="pending.kind !== 'none'"
          data-test="action-cancel"
          class="action-button"
          :label="ui.actions.cancel"
          severity="secondary"
          variant="outlined"
          rounded
          @click="emit('cancel')" />
        <Button
          v-if="canHide"
          data-test="action-hide"
          class="action-button"
          :label="ui.actions.hide"
          icon="pi pi-eye-slash"
          severity="secondary"
          variant="outlined"
          rounded
          @click="emit('hide')" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.kobo-actions {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  width: 100%;
}
.actions-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--space-1) var(--space-2);
  min-height: 44px;
}
/* The text takes what the buttons leave and wraps; the buttons keep their size and wrap below it if they must. */
.actions-text {
  flex: 1 1 8rem;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.actions-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-1);
}
.action-button { min-height: 44px; min-width: 44px; }
.line {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.25;
  text-align: center;
  color: rgba(255, 255, 255, 0.85);
}
.prompt { color: var(--gold); font-weight: 600; }
.notice {
  margin: 0;
  padding: 4px 12px;
  font-size: 0.8rem;
  font-weight: 700;
  text-align: center;
  color: #fff;
  background: rgba(var(--accent-negative-rgb), 0.35);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7);
  border-radius: 999px;
}
</style>
