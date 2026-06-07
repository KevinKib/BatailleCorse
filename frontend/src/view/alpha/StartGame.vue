<template>
  <div class="titlescreen" @click.self="cancelCapture">

    <div class="title-bg" />

    <div class="title-panel">

      <TitleCardFan />

      <div class="title-block">
        <div class="suit-row">
          <span class="suit-accent">♠</span>
          <span class="suit-accent">♥</span>
          <span class="suit-accent">♦</span>
          <span class="suit-accent">♣</span>
        </div>
        <h1 class="game-title">Bataille Corse</h1>
      </div>

      <div class="panel-divider" />

      <div v-if="screenMode === 'create'" class="field-group">
        <label class="field-label">Opponent</label>
        <div class="opponent-toggle">
          <button
            class="opponent-option"
            :class="{ 'opponent-option--active': !vsComputer }"
            @click="vsComputer = false"
          >Human</button>
          <button
            class="opponent-option"
            :class="{ 'opponent-option--active': vsComputer }"
            @click="vsComputer = true"
          >Computer</button>
        </div>
      </div>

      <div v-if="screenMode === 'join'" class="field-group">
        <label class="field-label" for="gameId">Game ID</label>
        <InputText
          id="gameId"
          v-model="joinGameId"
          placeholder="Paste game ID or link..."
          class="name-input"
        />
        <p v-if="joinError" class="conflict-warning">{{ joinError }}</p>
      </div>

      <div class="field-group">
        <label class="field-label" for="playerName">Your Name</label>
        <InputText
          id="playerName"
          v-model="playerName"
          placeholder="Enter your name..."
          class="name-input"
          maxlength="20"
        />
      </div>

      <div class="hotkey-section">
        <p class="field-label hotkey-section__label">Keybindings</p>
        <div class="hotkey-row">
          <div class="hotkey-group">
            <span class="field-label">Send Card</span>
            <button
              class="key-cap"
              :class="{ 'key-cap--capturing': capturing === 'send' }"
              @click.stop="startCapture('send')"
              :aria-label="capturing === 'send' ? 'Press any key' : 'Click to change Send key'"
            >
              <span v-if="capturing === 'send'" class="key-cap__listening">press a key</span>
              <span v-else class="key-cap__label">{{ formatKey(sendKey) }}</span>
            </button>
          </div>

          <div class="hotkey-group">
            <span class="field-label">Slap Pile</span>
            <button
              class="key-cap"
              :class="{ 'key-cap--capturing': capturing === 'slap' }"
              @click.stop="startCapture('slap')"
              :aria-label="capturing === 'slap' ? 'Press any key' : 'Click to change Slap key'"
            >
              <span v-if="capturing === 'slap'" class="key-cap__listening">press a key</span>
              <span v-else class="key-cap__label">{{ formatKey(slapKey) }}</span>
            </button>
          </div>
        </div>

        <p v-if="keysConflict" class="conflict-warning">
          ⚠ Send and Slap are bound to the same key
        </p>
      </div>

      <div class="field-group" v-if="screenMode === 'create' && vsComputer">
        <label class="field-label">Difficulty</label>
        <div class="difficulty-badge" :style="{ color: DIFFICULTY[difficulty].color }">
          {{ DIFFICULTY[difficulty].name }}
        </div>
        <input
          type="range"
          :min="MIN_DIFFICULTY"
          :max="MAX_DIFFICULTY"
          v-model.number="difficulty"
          class="difficulty-slider"
          :style="{ '--tier-color': DIFFICULTY[difficulty].color }"
        />
        <div class="difficulty-ends">
          <span>{{ DIFFICULTY[MIN_DIFFICULTY].name }}</span>
          <span>{{ DIFFICULTY[MAX_DIFFICULTY].name }}</span>
        </div>
      </div>

      <Button
        class="start-button"
        :label="screenMode === 'join' ? 'Join Game' : (vsComputer ? 'Deal Cards' : 'Create Game')"
        icon="pi pi-play"
        severity="success"
        size="large"
        rounded
        :disabled="keysConflict"
        @click="startGame"
      />

      <Button
        class="back-button"
        label="Back to Lobby"
        icon="pi pi-arrow-left"
        severity="secondary"
        size="small"
        text
        @click="router.push('/')"
      />


    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { storeToRefs } from 'pinia';
import { Button, InputText } from 'primevue';

import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { useSettingsStore } from '../../state/Settings.store';
import { DIFFICULTY, MIN_DIFFICULTY, MAX_DIFFICULTY } from '../../model/Difficulty';
import TitleCardFan from '../../components/TitleCardFan.vue';

const router = useRouter();
const batailleCorseStore = useBatailleCorseStore();
const settingsStore = useSettingsStore();
const { playerName, sendKey, slapKey, difficulty } = storeToRefs(settingsStore);

const route = useRoute();
const screenMode = computed<'create' | 'join'>(() =>
  route.path.startsWith('/join') ? 'join' : 'create');

// New Game toggle: true = solo vs computer, false = 2-player vs human
const vsComputer = ref(true);

// Join: the game id to join (pre-filled from the share link)
const joinGameId = ref<string>((route.params.id as string) ?? '');
const joinError = ref<string>('');


const capturing = ref<'send' | 'slap' | null>(null);
let currentCaptureListener: ((e: KeyboardEvent) => void) | null = null;

const keysConflict = computed(() => sendKey.value === slapKey.value);

function formatKey(key: string): string {
  if (key === ' ') return 'Space';
  if (key === 'Enter') return 'Enter';
  if (key === 'Escape') return 'Esc';
  if (key === 'ArrowUp') return '↑';
  if (key === 'ArrowDown') return '↓';
  if (key === 'ArrowLeft') return '←';
  if (key === 'ArrowRight') return '→';
  if (key === 'Backspace') return '⌫';
  if (key === 'Tab') return 'Tab';
  return key.toUpperCase();
}

function startCapture(target: 'send' | 'slap') {
  cancelCapture();
  capturing.value = target;

  function onKey(e: KeyboardEvent) {
    e.preventDefault();
    if (e.key === 'Escape') {
      capturing.value = null;
    } else if (!['Shift', 'Control', 'Alt', 'Meta'].includes(e.key)) {
      if (target === 'send') settingsStore.sendKey = e.key;
      else settingsStore.slapKey = e.key;
      capturing.value = null;
    }
    document.removeEventListener('keydown', onKey);
    currentCaptureListener = null;
  }

  currentCaptureListener = onKey;
  document.addEventListener('keydown', onKey);
}

function cancelCapture() {
  if (currentCaptureListener) {
    document.removeEventListener('keydown', currentCaptureListener);
    currentCaptureListener = null;
  }
  capturing.value = null;
}

onBeforeUnmount(() => cancelCapture());

function startGame() {
  if (screenMode.value === 'join') {
    joinGame();
    return;
  }
  const gameMode = vsComputer.value ? 'solo' : 'multiplayer';
  batailleCorseStore.create(gameMode, playerName.value || undefined);
  const unwatch = watch(() => batailleCorseStore.gameId, (id) => {
    if (id) {
      unwatch();
      router.push(`/room/${id}`);
    }
  });
}

async function joinGame() {
  const id = joinGameId.value.trim();
  if (!id) return;
  try {
    await batailleCorseStore.join(id, playerName.value || undefined);
    router.push(`/room/${id}`);
  } catch (e) {
    joinError.value = 'Could not join this game. Check the ID and try again.';
  }
}
</script>

<style scoped>
.titlescreen {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.title-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 50% 50%, transparent 15%, rgba(0, 0, 0, 0.8) 100%),
    radial-gradient(ellipse at 50% 40%, #1e5c30 0%, #0d2e18 50%, #07160d 100%);
  z-index: 0;
}

.title-panel {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22px;
  background: rgba(0, 0, 0, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.10);
  border-radius: 20px;
  padding: 48px 52px 36px;
  box-shadow: 0 8px 64px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.07);
  min-width: 380px;
  max-width: 480px;
  margin-top: 60px;
}

/* Title block */
.title-block {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.suit-row {
  display: flex;
  gap: 10px;
  margin-bottom: 4px;
}

.suit-accent {
  font-size: 1rem;
  color: #e8c96d;
  opacity: 0.7;
}

.game-title {
  font-family: "Gabarito", sans-serif;
  font-size: 2.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  background: linear-gradient(135deg, #f5c842 0%, #c8860a 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
  line-height: 1.1;
  filter: drop-shadow(0 2px 10px rgba(200, 134, 10, 0.5));
}

.game-subtitle {
  font-size: 0.7rem;
  letter-spacing: 0.25em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.35);
  margin: 0;
}

.panel-divider {
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
}

/* Form fields */
.field-group {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 0.62rem;
  letter-spacing: 0.15em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.42);
  margin: 0;
}

.name-input {
  width: 100%;
}

:deep(.name-input.p-inputtext) {
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
}

:deep(.name-input.p-inputtext:focus) {
  border-color: rgba(232, 201, 109, 0.5);
  box-shadow: 0 0 0 2px rgba(232, 201, 109, 0.15);
}

:deep(.name-input.p-inputtext::placeholder) {
  color: rgba(255, 255, 255, 0.25);
}

/* Hotkeys */
.hotkey-section {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.hotkey-section__label {
  align-self: flex-start;
}

.hotkey-row {
  display: flex;
  gap: 32px;
  justify-content: center;
}

.hotkey-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.key-cap {
  min-width: 80px;
  min-height: 56px;
  background: rgba(0, 0, 0, 0.35);
  border: 1.5px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.85);
  font-family: "Gabarito", monospace;
  font-size: 1.1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
}

.key-cap:hover {
  background: rgba(255, 255, 255, 0.07);
  border-color: rgba(255, 255, 255, 0.35);
}

.key-cap--capturing {
  background: rgba(232, 201, 109, 0.14);
  border-color: rgba(232, 201, 109, 0.55);
  color: #f5c842;
}

.key-cap__label {
  pointer-events: none;
}

.key-cap__listening {
  color: rgba(220, 180, 60, 0.85);
  font-size: 0.62rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  animation: blink 1s step-start infinite;
  pointer-events: none;
}

@keyframes blink {
  50% { opacity: 0; }
}

.conflict-warning {
  font-size: 0.68rem;
  color: rgba(220, 150, 50, 0.85);
  margin: 0;
  letter-spacing: 0.04em;
}

/* Start button */
.start-button {
  width: 100%;
  letter-spacing: 0.08em;
}

/* Difficulty slider */
.difficulty-badge {
  text-align: center;
  font-family: "Gabarito", sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  text-shadow: 0 0 12px currentColor;
  transition: color 0.2s;
}

.difficulty-slider {
  width: 100%;
  height: 6px;
  margin-bottom: 6px;
  -webkit-appearance: none;
  appearance: none;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 3px;
  outline: none;
  cursor: pointer;
}

.difficulty-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--tier-color, #fff);
  box-shadow: 0 0 8px var(--tier-color, #fff), 0 2px 6px rgba(0, 0, 0, 0.5);
  cursor: pointer;
  transition: background 0.2s, box-shadow 0.2s;
}

.difficulty-slider::-moz-range-thumb {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: var(--tier-color, #fff);
  box-shadow: 0 0 8px var(--tier-color, #fff), 0 2px 6px rgba(0, 0, 0, 0.5);
  cursor: pointer;
  transition: background 0.2s, box-shadow 0.2s;
}

.difficulty-slider::-webkit-slider-runnable-track {
  height: 6px;
  border-radius: 3px;
}

.difficulty-ends {
  display: flex;
  justify-content: space-between;
  font-size: 0.58rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.3);
}

.opponent-toggle {
  display: flex;
  gap: 8px;
}

.opponent-option {
  flex: 1;
  padding: 10px;
  border-radius: 8px;
  border: 1.5px solid rgba(255, 255, 255, 0.18);
  background: rgba(0, 0, 0, 0.35);
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
}

.opponent-option--active {
  background: rgba(232, 201, 109, 0.14);
  border-color: rgba(232, 201, 109, 0.55);
  color: #f5c842;
}
</style>
