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
import { gameChoice } from '../locales/gameChoice';
import { joinErrorMessage } from '../composables/joinErrorMessage';
import PublicGameList from './PublicGameList.vue';
import type { PublicLobby } from '../model/lobby/PublicLobby';

const props = defineProps<{
  game: GameDefinition;
  /** Claims a seat in the game; a rejection keeps the player on the form. */
  join: (id: string, name?: string) => Promise<void>;
  /** The id of an invite link, shown in the field. */
  initialId?: string;
  /** The public game list (from `usePublicLobbies`); without it the form shows no list section. */
  publicGames?: {
    lobbies: PublicLobby[];
    total: number;
    truncated: boolean;
    loading: boolean;
    online: boolean;
    announcement: string;
  };
}>();

const ui = useI18n().join;
const gameName = computed(() => gameChoice(useI18n(), props.game.key).name);
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
  } catch (e) {
    busy.value = false;
    error.value = joinErrorMessage(e);
    return;
  }
  busy.value = false;
  router.push(props.game.roomRoute(id.value));
}

// A row of the public list: same join with the name field, and the same way into the room. A refusal
// propagates to the row, which shows it.
async function joinFromList(gameId: string) {
  await props.join(gameId, playerName.value || undefined);
  router.push(props.game.roomRoute(gameId));
}
</script>

<template>
  <TitlePanel tag="form" :title="gameName" :subtitle="ui.title" @submit="onSubmit">
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
    <template v-if="publicGames">
      <div class="or-divider" data-test="join-or"><span>{{ ui.or }}</span></div>
      <PublicGameList :game="game" v-bind="publicGames" :join="joinFromList" />
    </template>
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

<style scoped>
.or-divider {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.75rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.7);
}
.or-divider::before,
.or-divider::after { content: ''; flex: 1; height: 1px; background: rgba(255, 255, 255, 0.15); }
</style>
