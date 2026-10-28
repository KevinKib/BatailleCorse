<script setup lang="ts">
import { computed } from 'vue';
import HotkeySettings from './HotkeySettings.vue';
import LanguagePicker from './LanguagePicker.vue';
import type { GameDefinition } from '../model/games';
import { SUPPORTED_LOCALES } from '../locales';
import { useGameHotkeySettings } from '../composables/useGameHotkeySettings';
import { useI18n } from '../composables/useI18n';

// The per-device preferences of a game, reachable before the game starts: the same gear and panel as on
// the table, in the same corner of the Game home and the lobby. A game with shortcuts gets the gear, whose
// panel also holds the language row; a game without gets a globe menu in the same corner. With only one
// language offered, the gear is the shortcuts alone and a game without shortcuts shows nothing.
// The settings are read once; a table mounted later reads the same storage.
const props = defineProps<{ game: GameDefinition }>();
const messages = useI18n();
const settings = useGameHotkeySettings(props.game);
const labels = computed(() => messages.hotkeys.actions);
const languages = SUPPORTED_LOCALES.length > 1;
</script>

<template>
  <div v-if="settings || languages" class="local-preferences">
    <HotkeySettings v-if="settings" :settings="settings" :labels="labels" :title="languages ? messages.language.settings : undefined">
      <LanguagePicker v-if="languages" variant="row" />
    </HotkeySettings>
    <LanguagePicker v-else variant="menu" />
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
