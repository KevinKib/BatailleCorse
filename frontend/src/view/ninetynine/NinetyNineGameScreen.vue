<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useNinetyNineStore } from '../../state/NinetyNine.store';
import { useNinetyNineBootstrap } from '../../composables/useNinetyNineBootstrap';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useRemovedRedirect } from '../../composables/useRemovedRedirect';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import { useGameDuration } from '../../composables/useGameDuration';
import { useConnectionStatus } from '../../composables/useConnectionStatus';
import { useI18n } from '../../composables/useI18n';
import GameLobby from '../../components/lobby/GameLobby.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import ForfeitBanner from '../../components/ForfeitBanner.vue';
import GameTimer from '../../components/GameTimer.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import NinetyNineAceChooser from '../../components/ninetynine/NinetyNineAceChooser.vue';
import NinetyNineBustBanner from '../../components/ninetynine/NinetyNineBustBanner.vue';
import NinetyNineHand from '../../components/ninetynine/NinetyNineHand.vue';
import NinetyNineLobbyOptions from '../../components/ninetynine/NinetyNineLobbyOptions.vue';
import NinetyNineStandings from '../../components/ninetynine/NinetyNineStandings.vue';
import NinetyNineTable from '../../components/ninetynine/NinetyNineTable.vue';
import { findGame } from '../../model/games';
import { seatDisplayName } from '../../model/table/displayName';
import { rematchButtonFor } from '../../model/RematchButton';
import { format, formatList } from '../../locales/format';
import {
  AnimationSpeedButton, CardMotionLayer, defineAnchors, scaleMovements, useAnimationSpeed, useTableMotion,
  type CardMotionLayerPlay,
} from '../../animation';
import { planMovements, type NinetyNineAnchor } from '../../model/ninetynine/motion';
import { NINETY_NINE_TEMPO } from '../../model/ninetynine/tempo';
import { needsAceChoice, aceValuesFor } from '../../model/ninetynine/playable';
import { lastPlayLine } from '../../model/ninetynine/totalView';
import type { NinetyNineLogEntry } from '../../model/ninetynine/eventLog';
import type Card from '../../model/Card';

// The 99 table: the ring of seats around the total, my hand and, for an Ace that can be 1 or 11, its two values. Every
// rule is the server's (`playable`): the screen only shows and sends.
const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.ninetyNineUi;
const store = useNinetyNineStore();
const router = useRouter();
const game = findGame('ninety-nine');
useNinetyNineBootstrap(props.gameId, game);

// The roster outlives a forfeit (the player leaves `players`, the banner still names them). Names are user input:
// only ever rendered as text.
const displayName = (seat: number): string =>
  seatDisplayName(Object.values(store.roster).map(p => ({ ...p, id: String(p.seat) })), seat,
    { player: ui.playerLabel, bot: ui.botLabel });
const nameOrYou = (seat: number): string => seat === store.mySeat ? ui.you : displayName(seat);

useLeaveGuard({
  isInProgress: () => store.screen === 'playing',
  forfeit: () => store.forfeit(),
});
useRemovedRedirect(toRef(store, 'removedByHost'), () => { store.removedByHost = false; });

function leaveLobby() {
  // Replace, not push: Back must not return to a room the player no longer sits in.
  if (store.leave()) router.replace({ name: 'games' });
}

const countdown = useSeatDisconnectCountdown({
  disconnections: () => store.liveDisconnections,
  isGameOver: () => store.screen === 'finished',
});
onBeforeUnmount(() => countdown.cancel());
const seatDisconnected = (seat: number) => seat in store.liveDisconnections;
const seatSecondsRemaining = (seat: number) =>
  seatDisconnected(seat) ? countdown.secondsRemainingFor(store.liveDisconnections[seat]) : null;

const { formattedDuration, cancel: cancelGameDuration } =
  useGameDuration(() => store.screen === 'playing', () => store.screen === 'finished');
onBeforeUnmount(() => cancelGameDuration());

const { online } = useConnectionStatus();
watch(online, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) void store.hydrate();
});

// --- What the table shows ------------------------------------------------------------------------------------------
const maxLives = computed(() => Number(store.game?.options.lives ?? 3) || 3);
const roundOver = computed(() => store.game?.phase === 'ROUND_OVER');
const roundText = computed(() => {
  const g = store.game;
  if (!g) return '';
  return format(roundOver.value ? ui.table.roundOver : ui.table.round, { n: g.round });
});
const canPlay = computed(() => store.can('PLAY') && store.isMyTurn && !store.busy);
const statusText = computed(() => {
  const g = store.game;
  if (!g || g.phase === 'FINISHED') return '';
  if (g.phase === 'ROUND_OVER') return format(ui.phase.roundOver, { n: g.round });
  return store.isMyTurn ? ui.phase.yourTurn : format(ui.phase.waiting, { player: displayName(g.currentSeat) });
});
const lastLine = computed(() => store.game ? lastPlayLine(store.game.lastPlay, nameOrYou, ui) : '');
const noticeText = computed(() => store.notice ? ui.notice[store.notice] : '');
const bust = computed(() => roundOver.value ? store.game?.lastBust ?? null : null);

// One polite region for the turn: cleared and set again so that two turns in a row are both read.
const liveText = computed(() => store.isMyTurn && store.game?.phase === 'PLAYING' ? ui.live.yourTurn : '');
const liveSpoken = ref(liveText.value);
watch(() => [store.game?.round, store.game?.currentSeat, store.game?.version, liveText.value].join('|'), () => {
  liveSpoken.value = '';
  if (liveText.value) queueMicrotask(() => { liveSpoken.value = liveText.value; });
});

// --- The hand and the Ace --------------------------------------------------------------------------------------------
// A card is played when tapped, except an Ace that can be 1 or 11: it waits for its value (a chooser under the table).
const aceCard = ref<Card | null>(null);
watch(() => store.game?.version, () => { aceCard.value = null; });
watch(canPlay, (can) => { if (!can) aceCard.value = null; });

function onPlay(card: Card) {
  const current = store.game;
  if (!current || !canPlay.value) return;
  if (needsAceChoice(current, card)) {
    aceCard.value = aceCard.value?.name === card.name ? null : card;
    return;
  }
  aceCard.value = null;
  store.play(card);
}
function chooseAce(value: number) {
  const card = aceCard.value;
  aceCard.value = null;
  if (card) store.play(card, value);
}
const aceValues = computed(() => store.game && aceCard.value ? aceValuesFor(store.game, aceCard.value) : []);

// --- The pictures of the table ---------------------------------------------------------------------------------------
// Purely visual: each new move of the log is played over the table (the card flies from its seat to the pile, the
// replacement from the stock, the pile goes back to the stock on a reshuffle, a ring marks a stuck seat). The state, the
// buttons and the clicks never wait for it, and a place that cannot be found just skips the picture.
const { speed, cycle: cycleSpeed } = useAnimationSpeed();
const screenEl = ref<HTMLElement | null>(null);
const motionLayer = ref<{ play: CardMotionLayerPlay<NinetyNineAnchor> } | null>(null);

const resolveAnchor = defineAnchors<NinetyNineAnchor>(() => screenEl.value, {
  seat: (anchor, q) => q.cardIn(q.find(`[data-test="seat-plate"][data-seat="${anchor.seat}"]`), 24),
  hand: (_, q) => q.cardIn(q.find('[data-test="hand"]')),
  pile: (_, q) => q.cardIn(q.find('[data-test="pile"]')),
  stock: (_, q) => q.cardIn(q.find('[data-test="stock"]')),
});

const motion = useTableMotion<NinetyNineLogEntry, NinetyNineAnchor>({
  layer: motionLayer,
  speed,
  bounds: NINETY_NINE_TEMPO.queue,
  log: () => store.events,
  plan: (entry, { factor }) => {
    const liveSeats = (store.game?.players ?? []).filter(p => !p.eliminated).map(p => p.seat);
    const movements = planMovements(entry, store.mySeat, { liveSeats });
    return movements.length > 0 ? { movements: scaleMovements(movements, factor) } : null;
  },
});
void motion;

// --- The end -------------------------------------------------------------------------------------------------------
const winners = computed(() => {
  const outcome = store.game?.outcome;
  return outcome?.status === 'FINISHED' ? outcome.winners : [];
});
const iWon = computed(() => winners.value.includes(store.mySeat));
const winnerLine = computed(() => {
  if (winners.value.length === 0) return undefined;
  const players = formatList(winners.value.map(nameOrYou), 'conjunction');
  return format(winners.value.length > 1 ? ui.end.winners : ui.end.winner, { players });
});
const endSubtitle = computed(() => {
  const outcome = store.game?.outcome;
  if (outcome?.status !== 'FINISHED') return '';
  if (outcome.reason === 'FORFEIT') {
    const left = store.lastForfeit;
    if (left?.reason === 'RESIGNED') return format(ui.end.youWonResigned, { player: displayName(left.seat) });
    if (left?.reason === 'DISCONNECTED') return format(ui.end.youWonDisconnected, { player: displayName(left.seat) });
    return ui.end.youWonOthersLeft;
  }
  if (outcome.reason === 'QUICK') {
    const out = store.game?.lastBust;
    return out ? format(ui.end.quick, { player: nameOrYou(out.seat) }) : ui.end.lastStanding;
  }
  return ui.end.lastStanding;
});
const rematchButton = computed(() =>
  rematchButtonFor(store.rematch, { playAgain: ui.end.playAgain, pending: ui.end.rematchPending }));
</script>

<template>
  <div ref="screenEl" class="nn-screen" :class="{ 'nn-screen--lobby': store.screen === 'lobby' }">
    <GameLobby
      v-if="store.screen === 'lobby' && store.lobby && game"
      :game="game"
      :lobby="store.lobby"
      :error="store.lobbyError"
      :starting="store.starting"
      @start="store.startGame()"
      @leave="leaveLobby"
      @add-bot="store.addBot()"
      @remove-bot="seat => store.removeBot(seat)"
      @remove-seat="seat => store.removeSeat(seat)"
      @set-seat-option="(seat, key, value) => store.setSeatOption(seat, key, value)"
      @rename="name => store.rename(name)"
      @set-private="value => store.setPrivate(value)"
      privacy-in-options
      @set-option="(key, value) => store.setOption(key, value)">
      <template #options="{ descriptors, options, editable, setOption, privacy }">
        <NinetyNineLobbyOptions
          :descriptors="descriptors"
          :options="options"
          :editable="editable"
          :privacy="privacy"
          @set-option="setOption" />
      </template>
    </GameLobby>

    <EndGameOverlay
      v-else-if="store.screen === 'finished'"
      data-test="end"
      :did-i-win="iWon"
      :winner-line="winnerLine"
      :subtitle="endSubtitle"
      :rematch-button="rematchButton"
      :error="store.rematch === 'failed' ? ui.end.rematchFailed : undefined"
      @play-again="store.playAgain()">
      <NinetyNineStandings :players="store.game?.players ?? []" :name-of="displayName" :my-seat="store.mySeat" :max-lives="maxLives" />
    </EndGameOverlay>

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral felt screen. -->
    <div v-else-if="store.screen === 'connecting'" class="connecting" data-test="connecting" role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else-if="store.game">
      <RulesPanel :rules="messages.ninetyNine" />

      <div class="top-bar" data-test="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <span class="round-chip" data-test="round">{{ roundText }}</span>
      </div>

      <!-- The cards that travel (a picture only, hidden from assistive technology), and the one live region. -->
      <CardMotionLayer ref="motionLayer" :resolve="resolveAnchor" :mark-ms="NINETY_NINE_TEMPO.markMs"
                       :mark-reduced-ms="NINETY_NINE_TEMPO.markReducedMs" mark-style="soft" arrow-id="ninety-nine-arrow-head" />
      <p class="sr-only" role="status" aria-live="polite" aria-atomic="true" data-test="ninety-nine-live">{{ liveSpoken }}</p>

      <Transition name="forfeit-fade">
        <ForfeitBanner
          v-if="store.forfeitNotice"
          class="forfeit-notice"
          :label="format(ui.table.forfeited, { player: displayName(store.forfeitNotice.seat) })" />
      </Transition>

      <div v-if="!online" class="offline-notice" data-test="offline" role="alert">
        <i class="pi pi-wifi" aria-hidden="true"></i>
        {{ ui.table.offline }}
      </div>

      <NinetyNineTable
        :players="store.game.players"
        :my-seat="store.mySeat"
        :current-seat="store.game.phase === 'PLAYING' ? store.game.currentSeat : -1"
        :direction="store.game.direction"
        :total="store.game.total"
        :pile-top="store.game.pileTop"
        :stock-size="store.game.stockSize"
        :max-lives="maxLives"
        :name-of="displayName"
        :hit-seat="bust?.seat ?? null"
        :disconnected="seatDisconnected"
        :seconds-remaining="seatSecondsRemaining">
        <template #overlay>
          <NinetyNineBustBanner v-if="bust" :bust="bust" :label="nameOrYou(bust.seat)" :mine="bust.seat === store.mySeat" :pause="true" />
        </template>
      </NinetyNineTable>

      <!-- What the table says: whose turn it is, the last move, a refused move. -->
      <div class="status" data-test="status">
        <span class="status-turn" :class="{ 'status-turn--mine': store.isMyTurn && !roundOver }">{{ statusText }}</span>
        <span v-if="lastLine" class="status-last" data-test="last-play">{{ lastLine }}</span>
        <span v-if="noticeText" class="status-notice" data-test="notice" role="alert">{{ noticeText }}</span>
      </div>

      <div class="my-area" data-test="my-zone">
        <div class="ace-slot">
          <NinetyNineAceChooser v-if="aceCard" :values="aceValues" @choose="chooseAce" @cancel="aceCard = null" />
        </div>
        <NinetyNineHand :cards="store.hand" :playable="store.game.playable" :can-play="canPlay"
                        :ace-name="aceCard?.name ?? null" @play="onPlay" />
      </div>

      <div class="table-footer" data-test="table-footer">
        <Button data-test="back" class="back-button" :label="ui.back" icon="pi pi-undo"
                severity="secondary" variant="text" rounded @click="router.push({ name: 'games' })" />
        <AnimationSpeedButton class="speed" :speed="speed" :names="ui.motion.speeds" :label="ui.motion.speed"
                              @cycle="cycleSpeed()" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.nn-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  padding: var(--space-2) var(--space-2) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  background: var(--table-room-bg);
}
.nn-screen--lobby { display: block; padding: 0; background: none; }
.connecting {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  color: rgba(255, 255, 255, 0.85);
  font-family: var(--font-title);
  letter-spacing: 0.04em;
  text-align: center;
}
.connecting-spinner { font-size: 1.5rem; color: var(--gold); }

.top-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  box-sizing: border-box;
  height: calc(var(--hud-inset) * 2 + var(--hud-chip-h) + env(safe-area-inset-top, 0px));
  padding: env(safe-area-inset-top, 0px) 0 0 calc(var(--hud-inset) - var(--space-2) + env(safe-area-inset-left, 0px));
  margin-top: calc(var(--space-2) * -1);
  padding-right: 120px;
}
.round-chip {
  display: inline-flex;
  align-items: center;
  height: var(--hud-chip-h);
  box-sizing: border-box;
  padding: 0 12px;
  font-size: 0.8rem;
  font-weight: 700;
  font-family: var(--font-title);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
}

.status {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: baseline;
  gap: var(--space-1) var(--space-3);
  min-height: 1.25rem;
  font-size: 0.8125rem;
  color: rgba(255, 255, 255, 0.8);
  text-align: center;
}
.status-turn--mine { font-weight: 700; color: var(--gold); }
.status-last { color: rgba(255, 255, 255, 0.8); }
.status-notice { color: rgb(var(--accent-negative-rgb)); font-weight: 700; }

.my-area {
  position: relative;
  flex: none;
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
}
/* The chooser opens over the hand's top, so that the table above never resizes. */
.ace-slot { position: absolute; z-index: 4; bottom: 100%; left: 50%; transform: translateX(-50%); margin-bottom: var(--space-1); }

.table-footer { flex: none; display: flex; align-items: center; gap: var(--space-2); }
.table-footer .speed { margin-left: auto; }
/* 44 px touch target (frontend-spacing). */
.table-footer :deep(.back-button.p-button) { min-height: 44px; }

.sr-only {
  position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0;
  overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0;
}
.offline-notice {
  position: absolute;
  top: 56px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1800;
  width: max-content;
  max-width: calc(100% - 24px);
  box-sizing: border-box;
  font-size: 0.8rem;
  font-weight: 700;
  color: #fff;
  background: rgba(0, 0, 0, 0.82);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7);
  border-radius: 999px;
  padding: 6px 18px;
  text-align: center;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
}
.forfeit-notice {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1800;
  width: max-content;
  max-width: calc(100% - 24px);
  overflow-wrap: anywhere;
  text-align: center;
}
.forfeit-fade-enter-active,
.forfeit-fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.forfeit-fade-enter-from,
.forfeit-fade-leave-to { opacity: 0; transform: translate(-50%, -8px); }
@media (prefers-reduced-motion: reduce) {
  .forfeit-fade-enter-active, .forfeit-fade-leave-active { transition: none; }
}
</style>
