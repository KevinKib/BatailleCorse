<script setup lang="ts">
import { computed } from 'vue';
import LobbyGameOptions from '../lobby/LobbyGameOptions.vue';
import LobbyPrivateOption from '../lobby/LobbyPrivateOption.vue';
import { useI18n } from '../../composables/useI18n';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';
import type { LobbyPrivacy } from '../../model/lobby/LobbyPrivacy';

// The options of a 99 lobby, through the shared lobby's options slot: lives (2 to 4) and quick mode (off / on) as the
// generic option groups, with a line that says what quick mode does. The host edits, everybody else reads; the lobby
// stays the source of truth. Like the other seat games, a key shipped by a later change is settable without a change
// here, only its texts.
const props = defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
  /** The Private game switch, which sits at the top of the panel. */
  privacy?: LobbyPrivacy;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();

const ui = useI18n().ninetyNineUi;
// The server lists the default first (3, 2, 4); numbers read better in ascending order.
const sorted = computed<OptionDescriptor[]>(() => props.descriptors.map(d =>
  d.values.every(v => /^\d+$/.test(v)) ? { ...d, values: [...d.values].sort((a, b) => Number(a) - Number(b)) } : d));
</script>

<template>
  <div class="nn-options" data-test="ninety-nine-options">
    <LobbyPrivateOption v-if="privacy" :model-value="privacy.value" :editable="privacy.editable" @change="privacy.set" />
    <LobbyGameOptions :descriptors="sorted" :options="options" :editable="editable"
                      @set-option="(key, value) => emit('set-option', key, value)" />
    <p class="quick-hint" data-test="quick-hint">{{ ui.lobby.quickHint }}</p>
  </div>
</template>

<style scoped>
.nn-options { width: 100%; display: flex; flex-direction: column; gap: var(--space-2); }
.quick-hint { margin: 0; font-size: 0.75rem; line-height: 1rem; color: rgba(255, 255, 255, 0.7); }
</style>
