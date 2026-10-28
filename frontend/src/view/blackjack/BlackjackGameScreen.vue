<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useBlackjackStore } from '../../state/Blackjack.store';
import { useBlackjackBootstrap } from '../../composables/useBlackjackBootstrap';
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
import BlackjackActionBar from '../../components/blackjack/BlackjackActionBar.vue';
import BlackjackBetPanel from '../../components/blackjack/BlackjackBetPanel.vue';
import BlackjackLobbyOptions from '../../components/blackjack/BlackjackLobbyOptions.vue';
import BlackjackStandings from '../../components/blackjack/BlackjackStandings.vue';
import BlackjackTable from '../../components/blackjack/BlackjackTable.vue';
import { findGame } from '../../model/games';
import { seatDisplayName } from '../../model/table/displayName';
import { rematchButtonFor } from '../../model/RematchButton';
import { format, formatList } from '../../locales/format';
import {
  AnimationSpeedButton, CardMotionLayer, cardIn, scaleMovements, useAnimationSpeed, useTableMotion,
  type CardMotionLayerPlay, type MotionTarget,
} from '../../animation';
import { planMovements, type BlackjackAnchor, type BlackjackTableEvent } from '../../model/blackjack/motion';
import { eventLine } from '../../model/blackjack/eventLog';
import { BLACKJACK_TEMPO } from '../../model/blackjack/tempo';

// The Blackjack table: the dealer strip, the opponents, my seat and a bar at the bottom (bets, actions or a status
// line). Every rule is the server's (`availableActions`, `betLimits`): the screen only shows and sends.
const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.blackjackUi;
const store = useBlackjackStore();
const router = useRouter();
const game = findGame('blackjack');
useBlackjackBootstrap(props.gameId, game);

// Names are user input: only ever rendered as text. The roster outlives a forfeit.
const displayName = (seat: number): string =>
  seatDisplayName(Object.values(store.roster).map(p => ({ ...p, id: String(p.seat) })), seat,
    { player: ui.playerLabel, bot: ui.botLabel });
const nameOrYou = (seat: number): string => seat === store.mySeat ? ui.you : displayName(seat);

useLeaveGuard({ isInProgress: () => store.screen === 'playing', forfeit: () => store.forfeit() });
useRemovedRedirect(toRef(store, 'removedByHost'), () => { store.removedByHost = false; });

function leaveLobby() {
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

// --- What the table shows ---------------------------------------------------------------------------------------
const handText = computed(() => store.game
  ? format(ui.header.hand, { n: store.game.handNumber, total: store.game.handsTotal }) : '');
const lobbySolo = computed(() => (store.lobby?.players.filter(p => p.joined).length ?? 0) === 1);
const myTurn = computed(() => store.game?.phase === 'PLAYING' && store.game.currentSeat === store.mySeat);
const betting = computed(() => store.game?.phase === 'BETTING' && !!store.me && !store.me.eliminated && !store.me.forfeited);
const statusText = computed(() => {
  const g = store.game;
  if (!g || !store.me) return '';
  if (g.phase === 'BETTING') return ui.wait.betting;
  if (myTurn.value) return ui.wait.yourTurn;
  if (g.currentSeat < 0 || g.players.every(p => p.hands.every(h => h.status !== 'ACTIVE' && h.status !== 'WAITING'))) {
    return ui.wait.dealer;
  }
  return format(ui.wait.for, { player: displayName(g.currentSeat) });
});
const noticeText = computed(() => store.notice ? ui.notice[store.notice] : '');
const hasActions = computed(() => myTurn.value && (store.game?.availableActions.length ?? 0) > 0);

// One polite region: each table event read once (the key of `model/blackjack/eventLog`, seats named here).
const liveSpoken = ref('');
function eventText(event: BlackjackTableEvent): string {
  const line = eventLine(event, store.mySeat);
  if (!line) return '';
  const { seat, ...rest } = line.params;
  const params: Record<string, string | number> = { ...rest };
  if (seat !== undefined) params.player = nameOrYou(seat);
  const template = (ui.events as Record<string, string>)[line.key.split('.').pop() ?? ''];
  return template ? format(template, params) : '';
}
watch(() => store.lastEvent, (event) => {
  liveSpoken.value = '';
  if (!event) return;
  const text = eventText(event);
  if (text) queueMicrotask(() => { liveSpoken.value = text; });
});

// --- The pictures of the table ----------------------------------------------------------------------------------
// Purely visual: cards fly from the shoe to a hand, the hole card turns over. The state and the buttons never wait
// for it, and a place that cannot be found just skips the picture.
const { speed, cycle: cycleSpeed } = useAnimationSpeed();
const screenEl = ref<HTMLElement | null>(null);
const motionLayer = ref<{ play: CardMotionLayerPlay<BlackjackAnchor> } | null>(null);

function resolveAnchor(anchor: BlackjackAnchor): MotionTarget | null {
  const root = screenEl.value;
  if (!root) return null;
  const host = Array.from(root.querySelectorAll<HTMLElement>('[data-anchor]')).find(e => e.dataset.anchor === anchor) ?? null;
  return cardIn(host, 40);
}

const motion = useTableMotion<BlackjackTableEvent, BlackjackAnchor>({
  layer: motionLayer,
  speed,
  bounds: BLACKJACK_TEMPO.queue,
  log: () => store.events,
  plan: (entry, { factor }) => {
    const movements = planMovements(entry, store.mySeat);
    return movements.length > 0 ? { movements: scaleMovements(movements, factor) } : null;
  },
});
void motion;

// --- The end -----------------------------------------------------------------------------------------------------
const outcome = computed(() => {
  const o = store.game?.outcome;
  return o?.status === 'FINISHED' ? o : null;
});
const winners = computed(() => {
  const o = outcome.value;
  if (!o || store.game?.solo) return [];
  return Object.entries(o.places).filter(([, place]) => place === 1).map(([seat]) => Number(seat));
});
const iWon = computed(() => store.game?.solo ? (outcome.value?.net ?? 0) > 0 : winners.value.includes(store.mySeat));
const winnerLine = computed(() => {
  if (store.game?.solo) return undefined;
  if (winners.value.length === 0) return undefined;
  const players = formatList(winners.value.map(nameOrYou), 'conjunction');
  return format(winners.value.length > 1 ? ui.end.winners : ui.end.winner, { players });
});
const endSubtitle = computed(() => {
  const o = outcome.value;
  if (!o) return '';
  if (store.game?.solo) return (o.net ?? 0) > 0 ? ui.end.soloWon : ui.end.soloLost;
  return ui.end.reasons[o.reason] ?? '';
});
const rematchButton = computed(() =>
  rematchButtonFor(store.rematch, { playAgain: ui.end.playAgain, pending: ui.end.rematchPending }));
</script>

<template>
  <div ref="screenEl" class="bj-screen" :class="{ 'bj-screen--lobby': store.screen === 'lobby' }">
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
        <BlackjackLobbyOptions :descriptors="descriptors" :options="options" :editable="editable"
                               :privacy="privacy" :solo="lobbySolo" @set-option="setOption" />
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
      <BlackjackStandings :players="store.game?.players ?? []" :name-of="displayName" :my-seat="store.mySeat"
                          :solo="store.game?.solo ?? false" :net="outcome?.net" />
    </EndGameOverlay>

    <div v-else-if="store.screen === 'connecting'" class="connecting" data-test="connecting" role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else-if="store.game">
      <RulesPanel :rules="messages.blackjack" />

      <div class="top-bar" data-test="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <span class="hand-chip" data-test="hand-number">{{ handText }}</span>
      </div>

      <CardMotionLayer ref="motionLayer" :resolve="resolveAnchor" arrow-id="blackjack-arrow-head" mark-style="soft" />
      <p class="sr-only" role="status" aria-live="polite" aria-atomic="true" data-test="blackjack-live">{{ liveSpoken }}</p>

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

      <BlackjackTable
        :game="store.game"
        :opponents="store.opponents"
        :me="store.me"
        :my-seat="store.mySeat"
        :name-of="displayName"
        :disconnected="seatDisconnected"
        :seconds-remaining="seatSecondsRemaining" />

      <div class="bottom-bar" data-test="bottom-bar">
        <span v-if="noticeText" class="status-notice" data-test="notice" role="alert">{{ noticeText }}</span>
        <BlackjackBetPanel
          v-if="betting && store.game.betLimits"
          :limits="store.game.betLimits"
          :last-bet="store.game.lastBet"
          :placed="store.me?.betPlaced"
          :placed-amount="store.me?.bet"
          :busy="store.busy"
          :reset-key="store.game.handNumber"
          @bet="amount => store.bet(amount)" />
        <BlackjackBetPanel v-else-if="betting && store.me?.betPlaced" :limits="null" :last-bet="null" placed
                           :placed-amount="store.me?.bet" />
        <BlackjackActionBar
          v-else-if="hasActions"
          :actions="store.game.availableActions"
          :busy="store.busy"
          @hit="store.hit()" @stand="store.stand()" @double="store.doubleDown()" @split="store.split()" />
        <span v-else class="status-turn" data-test="status">{{ statusText }}</span>
        <div class="table-footer" data-test="table-footer">
          <Button data-test="back" class="back-button" :label="ui.back" icon="pi pi-undo"
                  severity="secondary" variant="text" rounded @click="router.push({ name: 'games' })" />
          <AnimationSpeedButton class="speed" :speed="speed" :names="ui.motion.speeds" :label="ui.motion.speed"
                                @cycle="cycleSpeed()" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bj-screen {
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
.bj-screen--lobby { display: block; padding: 0; background: none; }
.connecting { flex: 1 1 auto; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-3);
  color: rgba(255, 255, 255, 0.85); font-family: var(--font-title); letter-spacing: 0.04em; text-align: center; }
.connecting-spinner { font-size: 1.5rem; color: var(--gold); }
.top-bar { flex: none; display: flex; align-items: center; gap: var(--space-2); box-sizing: border-box;
  height: calc(var(--hud-inset) * 2 + var(--hud-chip-h) + env(safe-area-inset-top, 0px));
  padding: env(safe-area-inset-top, 0px) 120px 0 calc(var(--hud-inset) - var(--space-2) + env(safe-area-inset-left, 0px));
  margin-top: calc(var(--space-2) * -1); }
.hand-chip { display: inline-flex; align-items: center; height: var(--hud-chip-h); box-sizing: border-box; padding: 0 12px; font-size: 0.8rem;
  font-weight: 700; font-family: var(--font-title); letter-spacing: 0.06em; text-transform: uppercase; color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 999px; }
/* The thumb zone: sticky at the bottom, the table scrolls above it. */
.bottom-bar { flex: none; display: flex; flex-direction: column; align-items: center; gap: var(--space-2); width: 100%; max-width: 1000px; margin: 0 auto; }
.status-turn { font-size: 0.875rem; font-weight: 700; color: var(--gold); text-align: center; min-height: 1.25rem; }
.status-notice { font-size: 0.8125rem; color: rgb(var(--accent-negative-rgb)); font-weight: 700; }
.table-footer { width: 100%; display: flex; align-items: center; gap: var(--space-2); }
.table-footer .speed { margin-left: auto; }
.table-footer :deep(.back-button.p-button) { min-height: 44px; }
.sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
.offline-notice { position: absolute; top: 56px; left: 50%; transform: translateX(-50%); z-index: 1800; width: max-content; max-width: calc(100% - 24px);
  box-sizing: border-box; font-size: 0.8rem; font-weight: 700; color: #fff; background: rgba(0, 0, 0, 0.82);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7); border-radius: 999px; padding: 6px 18px; text-align: center; box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5); }
.forfeit-notice { position: absolute; top: 12px; left: 50%; transform: translateX(-50%); z-index: 1800; width: max-content;
  max-width: calc(100% - 24px); overflow-wrap: anywhere; text-align: center; }
.forfeit-fade-enter-active, .forfeit-fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.forfeit-fade-enter-from, .forfeit-fade-leave-to { opacity: 0; transform: translate(-50%, -8px); }
@media (prefers-reduced-motion: reduce) { .forfeit-fade-enter-active, .forfeit-fade-leave-active { transition: none; } }
</style>
