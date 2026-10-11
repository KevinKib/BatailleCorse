<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { Button, InputText } from 'primevue';
import TitlePanel from './TitlePanel.vue';
import type { GameDefinition } from '../model/games';
import { extractGameId } from '../model/extractGameId';
import { useSettingsStore } from '../state/Settings.store';
import { useI18n } from '../composables/useI18n';

const props = defineProps<{
  game: GameDefinition;
  /** Claims a seat in the game; a rejection keeps the player on the form. */
  join: (id: string, name?: string) => Promise<void>;
  /** The id of an invite link, shown in the field. */
  initialId?: string;
}>();

const ui = useI18n().join;
const gameName = computed(() => useI18n().gamePicker[props.game.key].name);
const router = useRouter();
const { playerName } = storeToRefs(useSettingsStore());

const joinId = ref(props.initialId ?? '');
const error = ref('');
const busy = ref(false);
const id = computed(() => extractGameId(joinId.value));

async function onSubmit() {
  if (!id.value || busy.value) return;
  error.value = '';
  busy.value = true;
  try {
    await props.join(id.value, playerName.value || undefined);
  } catch {
    busy.value = false;
    error.value = ui.failed;
    return;
  }
  busy.value = false;
  router.push(props.game.roomRoute(id.value));
}
</script>

<template>
  <TitlePanel tag="form" :title="gameName" :subtitle="ui.title" @submit="onSubmit">
    <div class="field-group">
      <label class="field-label" for="join-game-id">{{ ui.gameId }}</label>
      <InputText
        id="join-game-id"
        v-model="joinId"
        type="text"
        :placeholder="ui.gameIdPlaceholder"
        autocomplete="off"
        class="text-input"
        data-test="game-id" />
    </div>
    <div class="field-group">
      <label class="field-label" for="join-name">{{ ui.yourName }}</label>
      <InputText
        id="join-name"
        v-model="playerName"
        type="text"
        :placeholder="ui.namePlaceholder"
        maxlength="20"
        autocomplete="nickname"
        class="text-input"
        data-test="name" />
    </div>
    <p v-if="error" class="error" role="alert" data-test="join-error">{{ error }}</p>
    <Button
      data-test="submit"
      type="submit"
      class="start-button"
      :label="busy ? ui.joining : ui.submit"
      icon="pi pi-users"
      severity="success"
      size="large"
      rounded
      :disabled="!id"
      :loading="busy" />
    <Button
      data-test="back"
      class="back-button"
      :label="ui.back"
      icon="pi pi-arrow-left"
      severity="secondary"
      size="small"
      text
      @click="router.push(game.gameHomeRoute)" />
  </TitlePanel>
</template>
