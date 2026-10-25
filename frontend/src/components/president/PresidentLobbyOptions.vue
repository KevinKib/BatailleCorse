<script setup lang="ts">
import { computed } from 'vue';
import { Button } from 'primevue';
import LobbyGameOptions from '../lobby/LobbyGameOptions.vue';
import LobbyCustomise from '../lobby/LobbyCustomise.vue';
import LobbyPrivateOption from '../lobby/LobbyPrivateOption.vue';
import { useI18n } from '../../composables/useI18n';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';
import type { LobbyPrivacy } from '../../model/lobby/LobbyPrivacy';

// The options of a President lobby, through the shared lobby's options slot: one card that says whether the rules are
// Classic (the defaults) or Custom, and a "Customise" disclosure with every option the server declares, drawn by
// the generic option groups (so a key shipped by a later change is settable without a change here, only its texts).
// The host edits, everybody else reads. The lobby stays the source of truth: a change is asked for and drawn back
// on the value the next lobby view holds.
const props = defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
  /** The Private game switch, which sits at the top of the Customise panel. */
  privacy?: LobbyPrivacy;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();

const ui = useI18n().presidentUi.lobby;

const valueOf = (d: OptionDescriptor) => props.options[d.key] ?? d.defaultValue;
// Classic is the defaults: it changes no key.
const differing = computed(() => props.descriptors.filter(d => valueOf(d) !== d.defaultValue));
const isClassic = computed(() => differing.value.length === 0);

function backToClassic() {
  for (const d of differing.value) emit('set-option', d.key, d.defaultValue);
}
</script>

<template>
  <div class="president-options" data-test="president-options">
    <div class="preset" :class="{ 'preset--custom': !isClassic }" data-test="preset">
      <span class="preset-title" data-test="preset-title">{{ isClassic ? ui.classicTitle : ui.customTitle }}</span>
      <span class="preset-summary">{{ isClassic ? ui.classicSummary : ui.customSummary }}</span>
      <Button v-if="editable && !isClassic" data-test="preset-reset" class="preset-reset" :label="ui.reset"
              severity="secondary" variant="outlined" rounded size="small" @click="backToClassic" />
    </div>

    <LobbyCustomise panel-id="president-all-options">
      <LobbyPrivateOption v-if="privacy" :model-value="privacy.value" :editable="privacy.editable" @change="privacy.set" />
      <LobbyGameOptions
        :descriptors="descriptors"
        :options="options"
        :editable="editable"
        @set-option="(key, value) => emit('set-option', key, value)" />
    </LobbyCustomise>
  </div>
</template>

<style scoped>
.president-options { width: 100%; display: flex; flex-direction: column; gap: var(--space-2, 8px); }
.preset {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 44px;
  padding: 8px 12px;
  box-sizing: border-box;
  color: var(--gold);
  background: rgba(232, 201, 109, 0.12);
  border: 1px solid var(--gold);
  border-radius: 8px;
}
.preset--custom { color: rgba(255, 255, 255, 0.9); background: rgba(0, 0, 0, 0.25); border-color: rgba(255, 255, 255, 0.3); }
.preset-title { font-weight: 700; font-size: 0.9rem; line-height: 1.25rem; }
.preset-summary { font-size: 0.8125rem; line-height: 1.2rem; color: rgba(255, 255, 255, 0.85); }
.preset-reset.p-button { align-self: flex-start; min-height: 44px; margin-top: 4px; }
</style>
