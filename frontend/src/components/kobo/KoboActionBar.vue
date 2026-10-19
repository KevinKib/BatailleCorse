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

// How many buttons the bar shows (1, 2 or 3 and more): it decides where they go around the text (see `.actions-buttons`).
const buttonCount = computed(() => Math.min(3, buttons.value.length + (props.pending.kind !== 'none' ? 1 : 0) + (props.canHide ? 1 : 0)));

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
    <div class="actions-row" :class="{ 'actions-row--bare': !hasButtons }">
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
      <div v-if="hasButtons" class="actions-buttons" :class="`actions-buttons--n${buttonCount}`">
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
  container-type: inline-size;
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
.kobo-actions--ready .actions-row { display: flex; flex-direction: column; justify-content: flex-start; gap: 0; }
.kobo-actions--ready .actions-text { flex: none; width: 100%; }
.kobo-actions--ready .actions-text :deep(.turn-indicator),
.kobo-actions--ready .actions-text :deep(.event-log) { display: none; }
.kobo-actions--ready .actions-text .line { min-height: 1.25em; }
/* Three tracks: the text is always the middle one, so it is centred on the bar (and so on my cards, which are
   centred in the same box) whatever the buttons are. The two side tracks are equal (1fr each), so a button never
   pushes the text off the axis. One button sits in the right track; two share the sides, one each (End turn on the
   left, Kobo! on the right), which keeps the text centred and the bar at its height; three would not fit beside the
   text on a phone, so they take a row of their own under it, centred (the bar grows, as it always did then). */
.actions-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) fit-content(var(--kobo-text-max, 11rem)) minmax(0, 1fr);
  align-items: center;
  column-gap: var(--space-2);
  min-height: 44px;
}
.actions-text {
  position: relative;
  grid-column: 2;
  grid-row: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}
/* No button: nothing to leave room for, the text takes the whole row (and stays centred in it). */
.actions-row--bare .actions-text { grid-column: 1 / -1; }
/* One or two buttons: the wrapper disappears and each button is placed in a side track. */
.actions-buttons--n1,
.actions-buttons--n2 { display: contents; }
.actions-buttons--n1 > .action-button { grid-column: 3; grid-row: 1; justify-self: start; }
.actions-buttons--n2 > .action-button:first-child { grid-column: 1; grid-row: 1; justify-self: end; }
.actions-buttons--n2 > .action-button:last-child { grid-column: 3; grid-row: 1; justify-self: start; }
/* A side track is narrow on a phone: a label stays on one line, with a little less padding. */
.actions-buttons--n1 > .action-button,
.actions-buttons--n2 > .action-button { white-space: nowrap; padding-inline: 0.7rem; }
.actions-buttons--n3 {
  grid-column: 1 / -1;
  grid-row: 2;
  justify-self: center;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-1);
}
/* A wider bar gives the text room for a whole prompt on one line (the sides still hold one button each). */
@container (min-width: 600px) {
  .actions-row { --kobo-text-max: 22rem; }
}
/* And one wide enough for the right track to hold three buttons keeps them all beside the text. */
@container (min-width: 900px) {
  .actions-buttons--n2,
  .actions-buttons--n3 {
    display: flex;
    grid-column: 3;
    grid-row: 1;
    justify-self: start;
    gap: var(--space-1);
  }
  .actions-buttons--n2 > .action-button:first-child,
  .actions-buttons--n2 > .action-button:last-child { grid-column: auto; grid-row: auto; justify-self: auto; }
}
/* Memorising: the one button is in the flow of the column, under the text. */
.kobo-actions--ready .actions-buttons { display: flex; justify-content: center; }
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
