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
  createLobby: (name?: string, privateGame?: boolean) => Promise<string>;
}>();

const ui = useI18n().home;
const gameName = computed(() => gameChoice(useI18n(), props.game.key).name);
const route = useRoute();
const router = useRouter();
const { playerName } = storeToRefs(useSettingsStore());

// Set by a room link that could not be restored: the game is gone, or the stored seat no longer resolves.
const gone = computed(() => route.query.gone === '1');
const lost = computed(() => route.query.lost === '1');

const privateGame = ref(false);
const creating = ref(false);
const createError = ref(false);

async function newGame() {
  if (creating.value) return;
  creating.value = true;
  createError.value = false;
  try {
    const id = await props.createLobby(playerName.value || undefined, privateGame.value);
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
    <div class="new-game-block" data-test="new-game-block">
      <div class="private-row">
        <input id="home-private" v-model="privateGame" type="checkbox" class="private-box"
               data-test="private-game" aria-describedby="home-private-hint" />
        <div class="private-text">
          <label for="home-private" class="private-label">{{ ui.privateGame }}</label>
          <p id="home-private-hint" class="private-hint">{{ ui.privateGameHint }}</p>
        </div>
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
    </div>
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

<style scoped>
/* The "Private game" option only applies to New game: it is boxed together with that button, apart from Join game. */
.new-game-block {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-2);
  padding: var(--space-3);
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.12);
}
.private-row { width: 100%; display: flex; align-items: flex-start; gap: 12px; min-height: 44px; }
.private-box { flex: none; width: 24px; height: 24px; margin: 10px 0 0; accent-color: var(--gold); cursor: pointer; }
.private-text { display: flex; flex-direction: column; gap: 2px; padding-top: 10px; text-align: left; }
.private-label { font-size: 0.875rem; line-height: 1.25rem; font-weight: 600; color: rgba(255, 255, 255, 0.9); cursor: pointer; }
.private-hint { margin: 0; font-size: 0.8125rem; line-height: 1.2rem; color: rgba(255, 255, 255, 0.6); }
</style>
