<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { Button } from 'primevue';
import TitleCardFan from '../TitleCardFan.vue';
import LobbySeats from './LobbySeats.vue';
import LobbyGameOptions from './LobbyGameOptions.vue';
import LobbyInvite from './LobbyInvite.vue';
import { useI18n } from '../../composables/useI18n';
import { plural } from '../../locales/format';
import type { GameDefinition } from '../../model/games';
import type { LobbyView } from '../../model/lobby/LobbyView';

// Waiting room shared by every game: who is here, the game options (editable by the host), how to invite,
// and the Start button. It owns no state and imports no store: it shows the lobby it is given and emits what
// the player asks for, the screen sends it to the server and the next lobby view is the answer.
const props = defineProps<{
  game: GameDefinition;
  lobby: LobbyView;
  /** Set when the last command was refused or could not be sent. */
  error?: string | null;
}>();
const emit = defineEmits<{
  start: [];
  leave: [];
  'add-bot': [];
  'remove-bot': [seat: number];
  'set-seat-option': [seat: number, key: string, value: string];
  'set-option': [key: string, value: string];
  rename: [name: string];
}>();

const messages = useI18n();
const ui = messages.lobby;
const gameName = computed(() => messages.gamePicker[props.game.key].name);
const isHost = computed(() => props.lobby.mySeat === props.lobby.hostSeat);
const joinLink = computed(() => `${location.origin}${props.game.joinRoute(props.lobby.gameId)}`);
const descriptors = computed(() => props.lobby.optionDescriptors ?? []);
const joinedCount = computed(() => props.lobby.players.filter(p => p.joined).length);
const playersNeeded = computed(() => Math.max(0, props.lobby.minPlayers - joinedCount.value));

// The host seat can pass to me while I wait (the host left): say so to screen readers.
const hostNotice = ref('');
let noticeTimer: ReturnType<typeof setTimeout> | null = null;
watch(isHost, (now, before) => {
  if (!now || before) return;
  hostNotice.value = ui.nowHost;
  if (noticeTimer) clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => { hostNotice.value = ''; }, 6000);
});
onBeforeUnmount(() => { if (noticeTimer) clearTimeout(noticeTimer); });
</script>

<template>
  <div class="titlescreen">
    <div data-test="lobby" class="title-panel">
      <TitleCardFan class="lobby-fan" />

      <Button
        class="leave-lobby"
        data-test="leave-lobby"
        :label="ui.leave"
        icon="pi pi-arrow-left"
        severity="secondary"
        text
        size="small"
        @click="emit('leave')" />

      <div class="title-block">
        <div class="suit-row" aria-hidden="true">
          <span class="suit-accent">♠</span>
          <span class="suit-accent">♥</span>
          <span class="suit-accent">♦</span>
          <span class="suit-accent">♣</span>
        </div>
        <h1 class="game-title">{{ gameName }}</h1>
        <h2 class="lobby-subtitle" data-test="lobby-title">{{ ui.title }}</h2>
      </div>

      <p class="host-notice" data-test="host-notice" role="status" aria-live="polite">{{ hostNotice }}</p>

      <div class="panel-divider" />

      <LobbySeats
        :lobby="lobby"
        @add-bot="emit('add-bot')"
        @remove-bot="seat => emit('remove-bot', seat)"
        @rename="name => emit('rename', name)"
        @set-seat-option="(seat, key, value) => emit('set-seat-option', seat, key, value)" />

      <p v-if="error" class="error" role="alert" data-test="lobby-error">{{ ui.actionFailed }}</p>

      <div v-if="descriptors.length" class="field-group">
        <LobbyGameOptions
          :descriptors="descriptors"
          :options="lobby.options ?? {}"
          :editable="isHost && (lobby.canEditOptions ?? false)"
          @set-option="(key, value) => emit('set-option', key, value)" />
      </div>

      <LobbyInvite :join-link="joinLink" />

      <div class="actions">
        <template v-if="isHost">
          <Button
            data-test="start"
            class="start-button"
            :label="ui.startGame"
            icon="pi pi-play"
            severity="success"
            size="large"
            rounded
            :disabled="!lobby.canStart"
            @click="emit('start')" />
          <p v-if="!lobby.canStart" class="hint" data-test="start-hint">
            {{ plural(ui.waitingForPlayers, playersNeeded) }}
          </p>
        </template>
        <p v-else class="hint" data-test="waiting-host">{{ ui.waitingForHost }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Spacing follows a 4/8 px grid: 8 px inside a group and between adjacent controls, 24 px
   between sections, 16 px between the Start button and its hint. Every tap target is at least 44 px tall. Fonts only come from the
   --font-title / --font-ui tokens defined in App.vue. */
.titlescreen {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* The panel scrolls inside this screen when the lobby is taller than the viewport (6 seats with
     editable options on a phone): the page itself never scrolls, and never sideways. The decorative
     fan only appears when the viewport is tall enough to give it room. */
  overflow-x: hidden;
  overflow-y: auto;
  box-sizing: border-box;
  padding: 12px;
}
.lobby-fan { display: none; }
@media (min-height: 800px) {
  .titlescreen { padding: 100px 16px 24px; }
  .lobby-fan { display: block; }
}

.title-panel {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 20px;
  padding: 12px 32px 20px;
  box-shadow: var(--panel-shadow);
  width: 100%;
  max-width: 480px;
  box-sizing: border-box;
  /* `auto` margins centre a short panel and, unlike `align-items: center`, let a tall one start at the top. */
  margin: auto 0;
}

.leave-lobby.p-button { align-self: flex-start; min-height: 44px; margin-left: -8px; }

.title-block {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.suit-row { display: none; gap: 12px; margin-bottom: 4px; }
.suit-accent { font-size: 1rem; color: var(--gold-soft); opacity: 0.7; }
.game-title {
  font-family: var(--font-title);
  font-size: 2rem;
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
.lobby-subtitle {
  margin: 0;
  font-family: var(--font-title);
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.5;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}
/* Empty most of the time: it takes no room until it announces something. */
.host-notice { margin: 0; font-size: 0.875rem; line-height: 1.25rem; color: #4ade80; text-align: center; }
.host-notice:empty { display: none; }

.panel-divider {
  display: none;
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
}

.field-group { width: 100%; display: flex; flex-direction: column; gap: 8px; }

.error {
  width: 100%;
  margin: 0;
  padding: 8px 12px;
  box-sizing: border-box;
  font-size: 0.875rem;
  line-height: 1.25rem;
  color: rgb(var(--accent-negative-rgb));
  background: rgba(var(--accent-negative-rgb), 0.1);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.4);
  border-radius: 8px;
}

.actions { width: 100%; display: flex; flex-direction: column; align-items: center; gap: 16px; }
.start-button.p-button { width: 100%; min-height: 44px; letter-spacing: 0.08em; }
.hint { margin: 0; font-size: 0.875rem; line-height: 1.25rem; color: rgba(255, 255, 255, 0.6); text-align: center; }

/* Roomy viewports get the full decoration and the 24 px section rhythm back. */
@media (min-height: 900px) {
  .title-panel { gap: 24px; padding: 24px 40px 32px; }
  .suit-row { display: flex; }
  .panel-divider { display: block; }
  .game-title { font-size: 2.6rem; }
}

@media (max-width: 480px) {
  .titlescreen { padding: 8px; }
  .title-panel { padding: 8px 16px 12px; }
  .game-title { font-size: 1.75rem; }
}
@media (max-width: 480px) and (min-height: 800px) {
  .titlescreen { padding: 88px 12px 16px; }
}
</style>
