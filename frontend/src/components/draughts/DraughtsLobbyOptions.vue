<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';
import LobbyGameOptions from '../lobby/LobbyGameOptions.vue';
import DraughtsMiniBoard from './DraughtsMiniBoard.vue';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

// The options of a draughts lobby: a small board with the one-line description, then the generic options (who plays
// White). The host edits, everybody else reads; the lobby stays the source of truth.
defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();

const ui = useI18n().draughtsUi;
</script>

<template>
  <div class="draughts-options">
    <div class="intro">
      <DraughtsMiniBoard />
      <p class="summary" data-test="draughts-summary">{{ ui.lobby.summary }}</p>
    </div>
    <LobbyGameOptions
      :descriptors="descriptors"
      :options="options"
      :editable="editable"
      @set-option="(key, value) => emit('set-option', key, value)" />
  </div>
</template>

<style scoped>
.draughts-options { width: 100%; display: flex; flex-direction: column; gap: var(--space-3); }
.intro { display: flex; align-items: center; gap: var(--space-3); }
.summary { margin: 0; min-width: 0; font-size: 0.8125rem; line-height: 1.2rem; color: rgba(255, 255, 255, 0.8); }
</style>
