<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { Button, InputText } from 'primevue';
import TitlePanel from './TitlePanel.vue';
import LocalPreferences from './LocalPreferences.vue';
import type { GameDefinition } from '../model/games';
import { useSettingsStore } from '../state/Settings.store';
import { useI18n } from '../composables/useI18n';
import { gameChoice } from '../locales/gameChoice';
import { preloadAllCards } from '../composables/useCardAnimation';

const props = defineProps<{
  game: GameDefinition;
  /** Creates the game's lobby and resolves with its id. */
  createLobby: (name?: string) => Promise<string>;
}>();

const ui = useI18n().home;
const gameName = computed(() => gameChoice(useI18n(), props.game.key).name);
const route = useRoute();
const router = useRouter();
const { playerName } = storeToRefs(useSettingsStore());

// Set by a room link that could not be restored: the game is gone, or the stored seat no longer resolves.
const gone = computed(() => route.query.gone === '1');
const lost = computed(() => route.query.lost === '1');

const creating = ref(false);
const createError = ref(false);

async function newGame() {
  if (creating.value) return;
  creating.value = true;
  createError.value = false;
  try {
    const id = await props.createLobby(playerName.value || undefined);
    router.push(props.game.roomRoute(id));
  } catch {
    createError.value = true;
  } finally {
    creating.value = false;
  }
}

onMounted(() => preloadAllCards());
</script>

<template>
  <TitlePanel :title="gameName">
    <LocalPreferences :game="game" />
    <p v-if="gone" class="notice" role="status" data-test="gone-notice">{{ ui.gameGone }}</p>
    <p v-else-if="lost" class="notice" role="status" data-test="lost-notice">{{ ui.seatLost }}</p>
    <div class="field-group">
      <label class="field-label" for="home-name">{{ ui.yourName }}</label>
      <InputText
        id="home-name"
        v-model="playerName"
        type="text"
        :placeholder="ui.namePlaceholder"
        maxlength="20"
        autocomplete="nickname"
        class="text-input"
        data-test="name" />
    </div>
    <Button
      data-test="new-game"
      class="start-button"
      :label="creating ? ui.creating : ui.newGame"
      :icon="creating ? 'pi pi-spin pi-spinner' : 'pi pi-plus'"
      severity="success"
      size="large"
      rounded
      :disabled="creating"
      @click="newGame" />
    <p v-if="createError" class="error" role="alert" data-test="create-error">{{ ui.createFailed }}</p>
    <Button
      data-test="join-game"
      class="start-button"
      :label="ui.joinGame"
      icon="pi pi-users"
      severity="secondary"
      size="large"
      rounded
      @click="router.push(game.joinRoute())" />
    <Button
      data-test="back"
      class="back-button"
      :label="ui.back"
      icon="pi pi-arrow-left"
      severity="secondary"
      size="small"
      text
      @click="router.push({ name: 'games' })" />
  </TitlePanel>
</template>
