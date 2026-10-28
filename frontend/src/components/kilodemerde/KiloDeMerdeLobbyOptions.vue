<script setup lang="ts">
import { computed } from 'vue';
import LobbyGameOptions from '../lobby/LobbyGameOptions.vue';
import LobbyPrivateOption from '../lobby/LobbyPrivateOption.vue';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';
import type { LobbyPrivacy } from '../../model/lobby/LobbyPrivacy';

// The options of a Kilo de merde lobby, through the shared lobby's options slot: the number of rounds as a generic option
// group (any key a later change ships is settable without a change here, only its texts). The host edits, everybody else
// reads; the lobby stays the source of truth.
const props = defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
  /** The Private game switch, which sits at the top of the panel. */
  privacy?: LobbyPrivacy;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();

// The server lists the default first (10, 5, 15, 20); numbers read better in ascending order.
const sorted = computed<OptionDescriptor[]>(() => props.descriptors.map(d =>
  d.values.every(v => /^\d+$/.test(v)) ? { ...d, values: [...d.values].sort((a, b) => Number(a) - Number(b)) } : d));
</script>

<template>
  <div class="kdm-options" data-test="kilo-de-merde-options">
    <LobbyPrivateOption v-if="privacy" :model-value="privacy.value" :editable="privacy.editable" @change="privacy.set" />
    <LobbyGameOptions :descriptors="sorted" :options="options" :editable="editable"
                      @set-option="(key, value) => emit('set-option', key, value)" />
  </div>
</template>

<style scoped>
.kdm-options { width: 100%; display: flex; flex-direction: column; gap: var(--space-2); }
</style>
