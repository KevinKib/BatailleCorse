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
const props = withDefaults(defineProps<{
  game: KoboState;
  prompt: KoboPromptKey | null;
  phaseText: string;
  yourTurn: boolean;
  pending: KoboPending;
  busy: boolean;
  notice: KoboNotice | null;
  canHide: boolean;
  /** A tap on a card does not mean Match right now (a turn action wants it, or the mode is on): the button is the way. */
  matchButton?: boolean;
}>(), { matchButton: false });
const emit = defineEmits<{
  ready: []; draw: []; discard: []; skip: []; 'end-turn': []; announce: []; match: []; cancel: []; hide: [];
}>();

const ui = useI18n().koboUi;
// Option B: a tap on a card is the match, so the Match button only shows when a tap is not (see `matchButton`).
const buttons = computed(() => barButtons(props.game).filter(id => id !== 'match' || props.matchButton));
// No button, no column: an empty one would still cost the row gap and push the title off the centre of the bar.
const hasButtons = computed(() => buttons.value.length > 0 || props.pending.kind !== 'none' || props.canHide);

// "I have memorised" alone: the button goes under the text, so the text is centred in the whole bar.
const readyOnly = computed(() => buttons.value.length === 1 && buttons.value[0] === 'ready' && props.pending.kind === 'none' && !props.canHide);

const EVENT: Record<KoboBarButton, 'ready' | 'draw' | 'discard' | 'skip' | 'end-turn' | 'announce' | 'match'> = {
  ready: 'ready', draw: 'draw', discard: 'discard', skip: 'skip', endTurn: 'end-turn', announce: 'announce',
  match: 'match',
};
const SEVERITY: Record<KoboBarButton, 'success' | 'secondary' | 'warn' | 'danger'> = {
  ready: 'success', draw: 'success', discard: 'secondary', skip: 'secondary', endTurn: 'success', announce: 'danger',
  match: 'warn',
};
// The matching discard is a race against the other players, so a turn command in flight never disables it.
const armed = computed(() => props.pending.kind === 'match-armed' || props.pending.kind === 'match-give');
</script>

<template>
  <div class="kobo-actions" :class="{ 'kobo-actions--ready': readyOnly }" data-test="kobo-actions">
    <div class="actions-row">
      <div class="actions-text">
        <!-- Floats over the text of the bar (never over its buttons) instead of pushing the table. -->
        <p v-if="notice" class="notice" data-test="notice" role="alert">{{ ui.notice[notice] }}</p>
        <YourTurnIndicator :active="yourTurn" />
        <!-- Not live themselves: the screen's one live region says what I am to do (no sentence twice). -->
        <p v-if="prompt" class="line prompt" data-test="prompt">{{ ui.prompt[prompt] }}</p>
        <p v-else class="line" data-test="phase">{{ phaseText }}</p>
        <!-- Under the phase line, in the room the text column leaves free in the first row of the bar. -->
        <slot name="log" />
      </div>
      <div v-if="hasButtons" class="actions-buttons">
        <Button
          v-for="id in buttons"
          :key="id"
          :data-test="`action-${id}`"
          class="action-button"
          :label="ui.actions[id]"
          :severity="SEVERITY[id]"
          rounded
          :disabled="busy && id !== 'match'"
          :aria-pressed="id === 'match' ? String(armed) : undefined"
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
/* The bar keeps one height in every state of the turn: the turn cue (1 line), the phase or prompt (2 lines on a
   phone, 1 wide) and the log (1 line) are all reserved, so a line that comes or goes moves nothing above it. A
   notice floats over the bar instead of pushing it. The memorise state (text, then its button) has the same height. */
.kobo-actions {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 2px;
  width: 100%;
  min-height: var(--kobo-bar-min, 64px);
}
.actions-text :deep(.turn-indicator) { min-height: 1rem; }
.actions-text .line { min-height: calc(var(--kobo-bar-lines, 2) * 1.25em); }
@media (min-width: 768px) {
  .kobo-actions { min-height: 52px; }
  .actions-text .line { min-height: 1.25em; }
  /* Wide: there is room at the side, so the button leaves the flow and the text stays centred in the whole bar. */
  .kobo-actions--ready .actions-row { position: static; }
  .kobo-actions--ready .actions-buttons { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); }
}
/* Memorising: nothing to log and no turn cue; the text is centred in the bar and the button sits under it. */
.kobo-actions--ready .actions-row { flex-direction: column; justify-content: flex-start; gap: 0; }
.kobo-actions--ready .actions-text { flex: none; width: 100%; }
.kobo-actions--ready .actions-text :deep(.turn-indicator),
.kobo-actions--ready .actions-text :deep(.event-log) { display: none; }
.kobo-actions--ready .actions-text .line { min-height: 1.25em; }
.actions-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0 var(--space-2);
  min-height: 44px;
}
/* The text takes what the buttons leave and wraps; the buttons keep their size and wrap below it if they must. */
.actions-text {
  position: relative;
  flex: 1 1 8rem;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
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
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 2;
  margin: 0;
  padding: 4px 12px;
  font-size: 0.8rem;
  font-weight: 700;
  text-align: center;
  color: #fff;
  background: color-mix(in srgb, rgb(var(--accent-negative-rgb)) 45%, #121414);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7);
  border-radius: 999px;
}
</style>
