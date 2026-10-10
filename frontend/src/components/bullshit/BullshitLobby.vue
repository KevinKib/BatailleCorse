<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { Button, InputText } from 'primevue';
import { useBullshitStore } from '../../state/Bullshit.store';
import TitleCardFan from '../TitleCardFan.vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import { CLAIM_MODES, type ClaimMode } from '../../model/bullshit/claimMode';
import { DECK_SIZES, type DeckSize } from '../../model/bullshit/deckSize';
import type { LobbyPlayer, LobbyView } from '../../model/bullshit/LobbyView';

// Waiting room of a Bullshit game: who is here, how to invite, how the game will be
// played, and (host only) the Start button and the bot controls. The screen owns the lobby
// data and the start action; the bot actions (and their error) come straight from the store.
const props = defineProps<{
  lobby: LobbyView;
  joinLink: string;
  isHost: boolean;
  canStart: boolean;
}>();
const emit = defineEmits<{ start: [] }>();

const ui = useI18n().bullshitUi;
const store = useBullshitStore();

const joinedPlayers = computed(() => props.lobby.players.filter(p => p.joined));
const playersNeeded = computed(() => Math.max(0, props.lobby.minPlayers - joinedPlayers.value.length));
const claimMode = computed<ClaimMode | null>(() => {
  const mode = props.lobby.options?.claimMode;
  return CLAIM_MODES.find(m => m === mode) ?? null;
});
const deckSize = computed<DeckSize | null>(() => {
  const size = props.lobby.options?.deckSize;
  return DECK_SIZES.find(s => s === size) ?? null;
});
// Seats are 0-based internally; players see them numbered from 1.
const playerLabel = (seat: number) => format(ui.playerLabel, { n: seat + 1 });
const displayName = (name: string | null, seat: number) => name?.trim() || playerLabel(seat);

// Bots are labelled "Bot N" (N counts the bots in seat order) from the `bot` flag, whatever
// name the server stored, so the label follows the UI language.
const botNumbers = computed(() => {
  const numbers = new Map<number, number>();
  props.lobby.players.filter(p => p.joined && p.bot).forEach((p, i) => numbers.set(p.seat, i + 1));
  return numbers;
});
const rowName = (p: LobbyPlayer) =>
  p.bot ? format(ui.lobby.botName, { n: botNumbers.value.get(p.seat) ?? 1 }) : displayName(p.name, p.seat);

const isFull = computed(() => joinedPlayers.value.length >= props.lobby.maxPlayers);
const canRemove = (seat: number) => props.lobby.removableBotSeats?.includes(seat) ?? false;

const copied = ref(false);
const linkInput = ref<InstanceType<typeof InputText> | null>(null);
let copiedTimer: ReturnType<typeof setTimeout> | null = null;
onBeforeUnmount(() => { if (copiedTimer) clearTimeout(copiedTimer); });

async function copyLink() {
  try {
    await navigator.clipboard.writeText(props.joinLink);
  } catch {
    // Clipboard is unavailable on insecure origins: leave the link selected so Ctrl+C works.
    (linkInput.value?.$el as HTMLInputElement | undefined)?.select();
    return;
  }
  copied.value = true;
  if (copiedTimer) clearTimeout(copiedTimer);
  copiedTimer = setTimeout(() => { copied.value = false; }, 1500);
}

function selectAll(event: FocusEvent) {
  (event.target as HTMLInputElement).select();
}
</script>

<template>
  <div class="titlescreen">
    <div data-test="lobby" class="title-panel">
      <TitleCardFan />

      <div class="title-block">
        <div class="suit-row" aria-hidden="true">
          <span class="suit-accent">♠</span>
          <span class="suit-accent">♥</span>
          <span class="suit-accent">♦</span>
          <span class="suit-accent">♣</span>
        </div>
        <h1 class="game-title">{{ ui.start.title }}</h1>
        <h2 class="lobby-subtitle" data-test="lobby-title">{{ ui.lobby.title }}</h2>
      </div>

      <div class="panel-divider" />

      <div class="field-group">
        <div class="players-head">
          <span class="field-label">{{ ui.lobby.playersLabel }}</span>
          <span class="player-count" data-test="player-count">
            {{ format(ui.lobby.playerCount, { joined: joinedPlayers.length, max: lobby.maxPlayers }) }}
          </span>
        </div>
        <ul class="players">
          <li
            v-for="p in joinedPlayers"
            :key="p.seat"
            class="player"
            :class="{ 'player--me': p.seat === lobby.mySeat }">
            <span class="player-seat">{{ p.seat + 1 }}</span>
            <span class="player-name">{{ rowName(p) }}</span>
            <span v-if="p.seat === lobby.mySeat" class="chip chip--you" data-test="badge-you">{{ ui.lobby.youBadge }}</span>
            <span v-if="p.bot" class="chip chip--bot" data-test="badge-bot">
              <i class="pi pi-microchip" aria-hidden="true" />{{ ui.lobby.botBadge }}
            </span>
            <span v-if="p.seat === lobby.hostSeat" class="chip chip--host">{{ ui.lobby.hostBadge }}</span>
            <Button
              v-if="isHost && p.bot"
              class="remove-bot"
              :data-test="`remove-bot-${p.seat}`"
              icon="pi pi-times"
              severity="secondary"
              text
              rounded
              :aria-label="format(ui.lobby.removeBot, { name: rowName(p) })"
              :disabled="!canRemove(p.seat)"
              @click="store.removeBot(p.seat)" />
          </li>
        </ul>
        <Button
          v-if="isHost"
          class="add-bot"
          data-test="add-bot"
          type="button"
          :label="ui.lobby.addBot"
          icon="pi pi-plus"
          severity="secondary"
          variant="outlined"
          rounded
          :disabled="isFull"
          @click="store.addBot()" />
        <p v-if="isHost && store.botError" class="error" role="alert" data-test="bot-error">{{ ui.lobby.botActionFailed }}</p>
      </div>

      <div v-if="claimMode" class="field-group claim-row" data-test="claim-mode">
        <span class="field-label">{{ ui.lobby.claimModeLabel }}</span>
        <span class="claim-value">{{ ui.start.claimModes[claimMode] }}</span>
      </div>

      <div v-if="deckSize" class="field-group claim-row" data-test="deck-size">
        <span class="field-label">{{ ui.lobby.deckSizeLabel }}</span>
        <span class="claim-value">{{ ui.start.deckSizes[deckSize] }}</span>
      </div>

      <div class="field-group">
        <label class="field-label" for="invite-link">{{ ui.lobby.inviteLabel }}</label>
        <div class="share-row">
          <InputText
            id="invite-link"
            ref="linkInput"
            :value="joinLink"
            readonly
            class="share-input"
            data-test="invite-link"
            @focus="selectAll" />
          <Button
            data-test="copy-link"
            :label="ui.lobby.copy"
            icon="pi pi-copy"
            rounded
            @click="copyLink" />
        </div>
        <p class="copied" data-test="copied" role="status" aria-live="polite">
          <template v-if="copied">{{ ui.lobby.copied }}</template>
        </p>
      </div>

      <div class="actions">
        <template v-if="isHost">
          <Button
            data-test="start"
            class="start-button"
            :label="ui.lobby.startGame"
            icon="pi pi-play"
            severity="success"
            size="large"
            rounded
            :disabled="!canStart"
            @click="emit('start')" />
          <p v-if="!canStart" class="hint" data-test="start-hint">
            {{ plural(ui.lobby.waitingForPlayers, playersNeeded) }}
          </p>
        </template>
        <p v-else class="hint" data-test="waiting-host">{{ ui.lobby.waitingForHost }}</p>
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
  /* Tall lobbies (6 players on a short phone) scroll inside the panel area, never the page. */
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
  padding: 40px 40px 32px;
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
.suit-row { display: flex; gap: 12px; margin-bottom: 4px; }
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
.lobby-subtitle {
  margin: 4px 0 0;
  font-family: var(--font-title);
  font-size: 0.875rem;
  font-weight: 600;
  line-height: 1.5;
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
  line-height: 1rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}

.players-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.player-count { font-weight: 600; font-size: 0.875rem; line-height: 1rem; color: var(--gold-soft); font-variant-numeric: tabular-nums; }

.players { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.player {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 0 0 0 12px;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.92);
  min-width: 0;
}
.player--me { border-color: rgba(var(--accent-active-rgb), 0.55); background: rgba(var(--accent-active-rgb), 0.1); }
.player-seat {
  flex: none;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  font-family: var(--font-title);
  font-size: 0.75rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.85);
  border: 1px solid rgba(255, 255, 255, 0.25);
}
.player-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
/* Keep the text aligned when the row has no trailing control. */
.player:not(:has(.remove-bot)) { padding-right: 12px; }

.chip {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 0.6875rem;
  font-weight: 700;
  line-height: 1rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  padding: 4px 8px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.8);
}
.chip--you { color: #1b1400; background: var(--gold); border-color: var(--gold); }
.chip--bot { color: rgb(var(--accent-positive-rgb)); border-color: rgba(var(--accent-positive-rgb), 0.5); }
.chip--bot .pi { font-size: 0.75rem; }

.remove-bot.p-button { flex: none; width: 44px; height: 44px; padding: 0; }
.add-bot.p-button { width: 100%; min-height: 44px; }

/* Claim mode: a label on the left and its value on the right, in a row as tall and padded as a player row. */
.claim-row {
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  min-height: 44px;
  padding: 0 12px;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
}
.claim-value { color: var(--gold); font-weight: 600; font-size: 0.875rem; line-height: 1.25rem; text-align: right; }

.share-row { display: flex; gap: 8px; width: 100%; }
.share-input { flex: 1; min-width: 0; }
:deep(.share-input.p-inputtext) {
  min-height: 44px;
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  font-family: var(--font-ui);
  font-size: 0.875rem;
}
:deep(.share-input.p-inputtext:focus-visible) {
  border-color: rgba(232, 201, 109, 0.6);
  outline: 2px solid rgba(232, 201, 109, 0.5);
  outline-offset: 1px;
}
.share-row :deep(.p-button) { flex: none; min-height: 44px; }
.copied { min-height: 16px; margin: 0; font-size: 0.75rem; line-height: 16px; color: #4ade80; }
/* The status line is empty most of the time: pull it into the group's 8 px rhythm. */
.field-group .copied { margin-top: -8px; }

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
.start-button.p-button { width: 100%; min-height: 48px; letter-spacing: 0.08em; }
.hint { margin: 0; font-size: 0.875rem; line-height: 1.25rem; color: rgba(255, 255, 255, 0.6); text-align: center; }

@media (max-width: 480px) {
  .titlescreen { padding: 88px 12px 16px; }
  .title-panel { padding: 32px 20px 24px; }
  .game-title { font-size: 2.1rem; }
}
</style>
