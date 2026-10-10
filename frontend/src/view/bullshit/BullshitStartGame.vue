<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Button, InputText } from 'primevue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { CLAIM_MODES, DEFAULT_CLAIM_MODE, type ClaimMode } from '../../model/bullshit/claimMode';
import { DECK_SIZES, DEFAULT_DECK_SIZE, type DeckSize } from '../../model/bullshit/deckSize';
import { extractGameId } from '../../model/bullshit/gameId';
import { useI18n } from '../../composables/useI18n';
import TitleCardFan from '../../components/TitleCardFan.vue';

const messages = useI18n().bullshitUi;
const ui = messages.start;
const route = useRoute();
const router = useRouter();
const store = useBullshitStore();

// How long we wait for the server to confirm a new game before telling the player it failed.
const CREATE_TIMEOUT_MS = 8000;

const name = ref('');
const claimMode = ref<ClaimMode>(DEFAULT_CLAIM_MODE);
const deckSize = ref<DeckSize>(DEFAULT_DECK_SIZE);
const joinId = ref((route.params.id as string) ?? '');
const isJoin = ref(route.name === 'bullshit-join');
const error = ref('');
const busy = ref(false);
let createTimer: ReturnType<typeof setTimeout> | null = null;

function clearCreateTimer() {
  if (createTimer) clearTimeout(createTimer);
  createTimer = null;
}
onBeforeUnmount(clearCreateTimer);

watch(() => store.gameId, (id) => {
  if (id) {
    clearCreateTimer();
    router.push(`/games/bullshit/room/${id}`);
  }
});

function fail(message: string) {
  clearCreateTimer();
  busy.value = false;
  error.value = message;
}

function onCreate() {
  error.value = '';
  busy.value = true;
  try {
    store.create(name.value || undefined, claimMode.value, deckSize.value);
  } catch {
    fail(ui.errors.createFailed);
    return;
  }
  createTimer = setTimeout(() => fail(ui.errors.createFailed), CREATE_TIMEOUT_MS);
}

async function onJoin() {
  const id = extractGameId(joinId.value);
  if (!id) return;
  error.value = '';
  busy.value = true;
  try {
    await store.join(id, name.value || undefined);
  } catch {
    fail(ui.errors.joinFailed);
    return;
  }
  busy.value = false;
  router.push(`/games/bullshit/room/${id}`);
}
</script>

<template>
  <div class="titlescreen">
    <form class="title-panel" data-test="start-panel" @submit.prevent="isJoin ? onJoin() : onCreate()">
      <TitleCardFan />

      <div class="title-block">
        <div class="suit-row" aria-hidden="true">
          <span class="suit-accent">♠</span>
          <span class="suit-accent">♥</span>
          <span class="suit-accent">♦</span>
          <span class="suit-accent">♣</span>
        </div>
        <h1 class="game-title">{{ ui.title }}</h1>
        <h2 v-if="isJoin" class="subtitle">{{ ui.joinTitle }}</h2>
      </div>

      <div class="panel-divider" />

      <div v-if="isJoin" class="field-group">
        <label class="field-label" for="bullshit-game-id">{{ ui.gameId }}</label>
        <InputText
          id="bullshit-game-id"
          v-model="joinId"
          type="text"
          :placeholder="ui.gameIdPlaceholder"
          autocomplete="off"
          class="text-input"
          data-test="game-id" />
      </div>

      <div class="field-group">
        <label class="field-label" for="bullshit-name">{{ ui.yourName }}</label>
        <InputText
          id="bullshit-name"
          v-model="name"
          type="text"
          :placeholder="ui.namePlaceholder"
          maxlength="20"
          autocomplete="nickname"
          class="text-input"
          data-test="name" />
      </div>

      <fieldset v-if="!isJoin" class="field-group claim-mode" data-test="claim-mode-field">
        <legend class="field-label">{{ ui.claimModeLegend }}</legend>
        <div class="mode-toggle">
          <label
            v-for="mode in CLAIM_MODES"
            :key="mode"
            class="mode-option"
            :class="{ 'mode-option--active': claimMode === mode }">
            <input v-model="claimMode" type="radio" name="claimMode" :value="mode" class="mode-radio" />
            <span>{{ ui.claimModes[mode] }}</span>
          </label>
        </div>
      </fieldset>

      <fieldset v-if="!isJoin" class="field-group claim-mode" data-test="deck-size">
        <legend class="field-label">{{ ui.deckSizeLegend }}</legend>
        <div class="mode-toggle">
          <label
            v-for="size in DECK_SIZES"
            :key="size"
            class="mode-option"
            :class="{ 'mode-option--active': deckSize === size }">
            <input v-model="deckSize" type="radio" name="deckSize" :value="size" class="mode-radio" />
            <span>{{ ui.deckSizes[size] }}</span>
          </label>
        </div>
      </fieldset>

      <p v-if="error" class="error" role="alert" data-test="start-error">{{ error }}</p>

      <Button
        v-if="!isJoin"
        data-test="submit"
        type="submit"
        class="start-button"
        :label="ui.createGame"
        icon="pi pi-plus"
        severity="success"
        size="large"
        rounded
        :loading="busy" />
      <Button
        v-else
        data-test="submit"
        type="submit"
        class="start-button"
        :label="busy ? ui.joining : ui.joinGame"
        icon="pi pi-users"
        severity="success"
        size="large"
        rounded
        :disabled="!extractGameId(joinId)"
        :loading="busy" />

      <Button
        class="back-button"
        data-test="back"
        :label="messages.back"
        icon="pi pi-arrow-left"
        severity="secondary"
        size="small"
        text
        @click="router.push('/games')" />
    </form>
  </div>
</template>

<style scoped>
.titlescreen {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow-x: hidden;
  overflow-y: auto;
  box-sizing: border-box;
  padding: 100px 16px 24px;
}

.title-panel {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 20px;
  padding: 44px 44px 30px;
  box-shadow: var(--panel-shadow);
  width: 100%;
  max-width: 480px;
  box-sizing: border-box;
  margin: auto 0;
}

.title-block {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.suit-row { display: flex; gap: 10px; margin-bottom: 4px; }
.suit-accent { font-size: 1rem; color: var(--gold-soft); opacity: 0.7; }
.game-title {
  font-family: var(--font-title);
  font-size: 2.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  background: linear-gradient(135deg, var(--gold) 0%, var(--gold-deep) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
  line-height: 1.1;
}
.subtitle {
  margin: 4px 0 0;
  font-family: var(--font-title);
  font-size: 0.875rem;
  font-weight: 600;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}

.panel-divider {
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
}

.field-group { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.field-label {
  font-size: 0.75rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
  padding: 0;
}

.text-input { width: 100%; }
:deep(.text-input.p-inputtext) {
  min-height: 44px;
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
}
:deep(.text-input.p-inputtext:focus),
:deep(.text-input.p-inputtext:focus-visible) {
  border-color: rgba(232, 201, 109, 0.6);
  box-shadow: 0 0 0 2px rgba(232, 201, 109, 0.2);
  outline: none;
}
:deep(.text-input.p-inputtext::placeholder) { color: rgba(255, 255, 255, 0.3); }

/* Claim mode: two big selectable options, same look as BatailleCorse's opponent toggle.
   The native radio stays in the tab order (visually hidden) so the keyboard still works. */
.claim-mode { border: none; padding: 0; margin: 0; min-width: 0; }
.claim-mode .field-label { display: block; margin-bottom: 8px; line-height: 1rem; }
.mode-toggle { display: flex; gap: 8px; }
.mode-option {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 8px;
  min-height: 44px;
  box-sizing: border-box;
  border-radius: 8px;
  border: 1.5px solid rgba(255, 255, 255, 0.18);
  background: rgba(0, 0, 0, 0.35);
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.8rem;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}
.mode-option:hover { border-color: rgba(255, 255, 255, 0.35); }
.mode-option--active {
  background: rgba(232, 201, 109, 0.14);
  border-color: rgba(232, 201, 109, 0.55);
  color: #f5c842;
}
.mode-option:focus-within { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 2px; }
.mode-radio {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  cursor: pointer;
}

.error {
  width: 100%;
  margin: 0;
  padding: 8px 12px;
  box-sizing: border-box;
  font-size: 0.875rem;
  color: rgb(var(--accent-negative-rgb));
  background: rgba(var(--accent-negative-rgb), 0.1);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.4);
  border-radius: 8px;
}

.start-button.p-button { width: 100%; min-height: 48px; letter-spacing: 0.08em; }
/* 24 px panel gap minus 8 px = 16 px between Create/Join and Back. */
.back-button.p-button { min-height: 44px; margin-top: -8px; }

@media (max-width: 480px) {
  .titlescreen { padding: 88px 12px 16px; }
  .title-panel { padding: 32px 20px 24px; }
  .game-title { font-size: 2.1rem; }
}

@media (prefers-reduced-motion: reduce) {
  .mode-option { transition: none; }
}
</style>
