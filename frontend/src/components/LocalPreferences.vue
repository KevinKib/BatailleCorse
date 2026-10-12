<script setup lang="ts">
import { computed } from 'vue';
import HotkeySettings from './HotkeySettings.vue';
import type { GameDefinition } from '../model/games';
import { useGameHotkeySettings } from '../composables/useGameHotkeySettings';
import { useI18n } from '../composables/useI18n';

// The per-device preferences of a game (today its keyboard shortcuts), reachable before the game
// starts: the same gear and panel as on the table, in the same corner of the Game home and the lobby.
// The settings are read once; a table mounted later reads the same storage.
const props = defineProps<{ game: GameDefinition }>();
const messages = useI18n();
const settings = useGameHotkeySettings(props.game.key);
const labels = computed(() => messages.hotkeys.actions);
const note = computed(() => (props.game.hotkeys.fixedDigits ? messages.hotkeys.bullshitNote : undefined));
</script>

<template>
  <div class="local-preferences">
    <HotkeySettings :settings="settings" :labels="labels" :note="note" />
  </div>
</template>

<style scoped>
/* The top right corner of the panel it sits in (the panel is positioned): out of the flow, so it never
   moves the content. The opened settings stay inside the screen at any width. */
.local-preferences {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  z-index: 3;
}
.local-preferences :deep(.hotkey-settings) { margin-left: 0; }
/* Opens right under the gear, never over it, and stays inside the screen at phone width. */
.local-preferences :deep(.panel) {
  position: absolute;
  top: calc(100% + var(--space-1));
  left: auto;
  right: 0;
}
</style>
