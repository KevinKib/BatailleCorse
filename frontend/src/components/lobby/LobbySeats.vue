<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Button, InputText } from 'primevue';
import LobbySeatOptions from './LobbySeatOptions.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type { LobbyPlayer, LobbyView } from '../../model/lobby/LobbyView';

// Who is in the lobby: one row per joined seat (my own name is editable), the host's bot controls, and the
// free-seat line (Add bot for the host when the game has bots, "Waiting for a player" otherwise).
const props = defineProps<{
  lobby: LobbyView;
  /** Whether the game offers bots (`GameDefinition.supportsBots`): without them there is no Add bot, for anybody. */
  supportsBots: boolean;
}>();
const emit = defineEmits<{
  'add-bot': [];
  'remove-bot': [seat: number];
  'remove-seat': [seat: number];
  rename: [name: string];
  'set-seat-option': [seat: number, key: string, value: string];
}>();

const ui = useI18n().lobby;

const isHost = computed(() => props.lobby.mySeat === props.lobby.hostSeat);
const joinedPlayers = computed(() => props.lobby.players.filter(p => p.joined));
const isFull = computed(() => joinedPlayers.value.length >= props.lobby.maxPlayers);

// Seats are 0-based internally; players see them numbered from 1.
const playerLabel = (seat: number) => format(ui.playerLabel, { n: seat + 1 });
const displayName = (name: string | null, seat: number) => name?.trim() || playerLabel(seat);

// Bots are labelled "Bot N" (N counts the bots in seat order) from the `bot` flag, whatever name the
// server stored, so the label follows the UI language.
const botNumbers = computed(() => {
  const numbers = new Map<number, number>();
  props.lobby.players.filter(p => p.joined && p.bot).forEach((p, i) => numbers.set(p.seat, i + 1));
  return numbers;
});
const rowName = (p: LobbyPlayer) =>
  p.bot ? format(ui.botName, { n: botNumbers.value.get(p.seat) ?? 1 }) : displayName(p.name, p.seat);

const canRemove = (seat: number) => props.lobby.removableBotSeats?.includes(seat) ?? false;
const seatOptionDescriptors = computed(() => props.lobby.seatOptionDescriptors ?? []);

// My own name: edited in place, committed on blur or Enter, reverted on Escape. The server's answer
// (the next lobby view) is what changes the name everybody sees.
const myRow = computed(() => joinedPlayers.value.find(p => p.seat === props.lobby.mySeat && !p.bot) ?? null);
const confirmedName = computed(() => (myRow.value ? rowName(myRow.value) : ''));
const draft = ref(confirmedName.value);
let baseline = confirmedName.value;
watch(confirmedName, (name) => { draft.value = name; baseline = name; });

function commitName() {
  const name = draft.value.trim();
  if (name === baseline) return;
  baseline = name;
  emit('rename', name);
}
function revertName() {
  draft.value = baseline;
}
</script>

<template>
  <div class="field-group">
    <div class="players-head">
      <span class="field-label">{{ ui.playersLabel }}</span>
      <span class="player-count" data-test="player-count">
        {{ format(ui.playerCount, { joined: joinedPlayers.length, max: lobby.maxPlayers }) }}
      </span>
    </div>
    <ul class="players">
      <li
        v-for="p in joinedPlayers"
        :key="p.seat"
        class="player"
        :class="{ 'player--me': p.seat === lobby.mySeat }">
        <span class="player-seat">{{ p.seat + 1 }}</span>
        <InputText
          v-if="myRow && p.seat === myRow.seat"
          v-model="draft"
          class="player-name player-name--edit"
          data-test="rename"
          maxlength="20"
          autocomplete="nickname"
          :aria-label="ui.renameLabel"
          @blur="commitName"
          @keydown.enter.prevent="commitName"
          @keydown.esc.prevent="revertName" />
        <span v-else class="player-name">{{ rowName(p) }}</span>
        <span v-if="p.seat === lobby.mySeat" class="chip chip--you" data-test="badge-you">{{ ui.youBadge }}</span>
        <span v-if="p.bot" class="chip chip--bot" data-test="badge-bot">
          <i class="pi pi-microchip" aria-hidden="true" />{{ ui.botBadge }}
        </span>
        <span v-if="p.seat === lobby.hostSeat" class="chip chip--host">{{ ui.hostBadge }}</span>
        <span v-if="p.disconnected" class="chip chip--offline" data-test="badge-offline">{{ ui.offlineBadge }}</span>
        <LobbySeatOptions
          v-if="isHost && p.bot && seatOptionDescriptors.length"
          :seat="p.seat"
          :seat-name="rowName(p)"
          :descriptors="seatOptionDescriptors"
          :values="p.seatOptions ?? {}"
          @change="(seat, key, value) => emit('set-seat-option', seat, key, value)" />
        <Button
          v-if="isHost && p.bot"
          class="remove-bot"
          :data-test="`remove-bot-${p.seat}`"
          icon="pi pi-times"
          severity="secondary"
          text
          rounded
          :aria-label="format(ui.removeBot, { name: rowName(p) })"
          :disabled="!canRemove(p.seat)"
          @click="emit('remove-bot', p.seat)" />
        <Button
          v-if="isHost && !p.bot && p.seat !== lobby.hostSeat"
          class="remove-bot remove-seat"
          :data-test="`remove-seat-${p.seat}`"
          icon="pi pi-times"
          severity="secondary"
          text
          rounded
          :aria-label="format(ui.removeSeat, { name: rowName(p) })"
          @click="emit('remove-seat', p.seat)" />
      </li>
    </ul>
    <Button
      v-if="isHost && supportsBots"
      class="add-bot"
      data-test="add-bot"
      type="button"
      :label="ui.addBot"
      icon="pi pi-plus"
      severity="secondary"
      variant="outlined"
      rounded
      :disabled="isFull"
      @click="emit('add-bot')" />
    <p v-else-if="!isFull" class="free-seat" data-test="waiting-for-player">{{ ui.waitingForPlayer }}</p>
  </div>
</template>

<style scoped>
.field-group { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.field-label {
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1rem;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}
.players-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.player-count { font-weight: 700; font-size: 0.875rem; line-height: 1rem; color: var(--gold-soft); font-variant-numeric: tabular-nums; }

.players { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
.player {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
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
/* The seat number has a fixed column; the name starts the same distance after it whether it is a field (mine) or
   plain text (the others, bots): both carry the same 1px border and 6px padding. */
.player-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 700; box-sizing: border-box; padding: 0 6px; border: 1px solid transparent; }
/* My own name is a field that looks like the other names until it is focused. */
:deep(.player-name--edit.p-inputtext) {
  align-self: stretch;
  min-height: 0;
  padding: 0 6px;
  font-family: var(--font-ui);
  font-size: 1rem;
  font-weight: 700;
  color: inherit;
  background: transparent;
  border-color: transparent;
  border-radius: 6px;
}
:deep(.player-name--edit.p-inputtext:hover) { border-color: rgba(255, 255, 255, 0.2); }
:deep(.player-name--edit.p-inputtext:focus-visible) {
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(232, 201, 109, 0.6);
  outline: 2px solid rgba(232, 201, 109, 0.5);
  outline-offset: 1px;
}
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
.chip--offline { color: rgba(255, 255, 255, 0.6); border-style: dashed; }

/* On a phone the badge, the seat option and the remove cross crowd the name ("Bo…"): tighten the gaps and the
   badge padding so the name keeps its room. Touch targets stay 44 px and nothing is dropped. */
@media (max-width: 480px) {
  .player { gap: 4px; }
  .chip { padding: 4px 6px; }
}

.remove-bot.p-button { flex: none; width: 44px; height: 44px; padding: 0; }
.add-bot.p-button { width: 100%; min-height: 44px; }
.free-seat { margin: 0; min-height: 44px; display: flex; align-items: center; justify-content: center; font-size: 0.875rem; color: rgba(255, 255, 255, 0.6); border: 1px dashed rgba(255, 255, 255, 0.2); border-radius: 8px; }
</style>
