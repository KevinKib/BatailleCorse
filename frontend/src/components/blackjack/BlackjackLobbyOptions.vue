<script setup lang="ts">
import LobbyGameOptions from '../lobby/LobbyGameOptions.vue';
import LobbyPrivateOption from '../lobby/LobbyPrivateOption.vue';
import { useI18n } from '../../composables/useI18n';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';
import type { LobbyPrivacy } from '../../model/lobby/LobbyPrivacy';

// The lobby options of Blackjack: hands and maximum bet as the generic option groups, the fixed line (100 points,
// minimum bet 1) and, alone in the room, the hint that Start plays solo against the dealer.
defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
  privacy?: LobbyPrivacy;
  /** One seat is taken: Start plays solo. */
  solo?: boolean;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();
const ui = useI18n().blackjackUi;
</script>

<template>
  <div class="bj-options" data-test="blackjack-options">
    <LobbyPrivateOption v-if="privacy" :model-value="privacy.value" :editable="privacy.editable" @change="privacy.set" />
    <LobbyGameOptions :descriptors="descriptors" :options="options" :editable="editable"
                      @set-option="(key, value) => emit('set-option', key, value)" />
    <p class="bj-hint" data-test="fixed-line">{{ ui.lobby.fixed }}</p>
    <p v-if="solo" class="bj-hint" data-test="solo-hint">{{ ui.lobby.soloHint }}</p>
  </div>
</template>

<style scoped>
.bj-options { width: 100%; display: flex; flex-direction: column; gap: var(--bj-space-2); }
.bj-hint { margin: 0; font-size: 0.75rem; line-height: 1rem; color: rgba(255, 255, 255, 0.7); }
</style>
