<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { Button, InputText } from 'primevue';
import TitlePanel from './TitlePanel.vue';
import type { GameDefinition } from '../model/games';
import { useSettingsStore } from '../state/Settings.store';
import { useI18n } from '../composables/useI18n';
import { preloadAllCards } from '../composables/useCardAnimation';

const props = defineProps<{ game: GameDefinition }>();

const ui = useI18n().home;
const gameName = computed(() => useI18n().gamePicker[props.game.key].name);
const route = useRoute();
const router = useRouter();
const { playerName } = storeToRefs(useSettingsStore());

// Set by a room link that could not be restored (spec 4.0).
const gone = computed(() => route.query.gone === '1');

onMounted(() => preloadAllCards());
</script>

<template>
  <TitlePanel :title="gameName">
    <p v-if="gone" class="notice" role="status" data-test="gone-notice">{{ ui.gameGone }}</p>
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
      :label="ui.newGame"
      icon="pi pi-plus"
      severity="success"
      size="large"
      rounded
      @click="router.push(game.newGameRoute)" />
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
