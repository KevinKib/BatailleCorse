<script setup lang="ts">
import { computed } from 'vue';
import LobbyGameOptions from '../lobby/LobbyGameOptions.vue';
import LobbyCustomise from '../lobby/LobbyCustomise.vue';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

// The options of a Mangola lobby, through the shared lobby's options slot. The common ones (who starts) stay in
// sight; the sowing direction sits behind the "Customise" disclosure, since almost every game keeps the default.
// Both players must know the direction, though: once it is not the default the disclosure opens by itself, for the
// guest as for the host, so the value is never hidden (the guest reads it as plain text).
const props = defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();

/** The option keys kept behind Customise. */
const CUSTOMISED_KEYS = ['direction'];

const visible = computed(() => props.descriptors.filter(d => !CUSTOMISED_KEYS.includes(d.key)));
const customised = computed(() => props.descriptors.filter(d => CUSTOMISED_KEYS.includes(d.key)));
const nonDefault = computed(() =>
  customised.value.some(d => (props.options[d.key] ?? d.defaultValue) !== d.defaultValue));
</script>

<template>
  <div class="mangola-options" data-test="mangola-options">
    <LobbyGameOptions
      v-if="visible.length"
      :descriptors="visible"
      :options="options"
      :editable="editable"
      @set-option="(key, value) => emit('set-option', key, value)" />
    <LobbyCustomise v-if="customised.length" panel-id="mangola-all-options" :reveal="nonDefault">
      <LobbyGameOptions
        :descriptors="customised"
        :options="options"
        :editable="editable"
        @set-option="(key, value) => emit('set-option', key, value)" />
    </LobbyCustomise>
  </div>
</template>

<style scoped>
.mangola-options { width: 100%; display: flex; flex-direction: column; gap: var(--space-2, 8px); }
</style>
