<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useBlackjackStore } from '../../state/Blackjack.store';
import { useBlackjackBootstrap } from '../../composables/useBlackjackBootstrap';
import { useBlackjackDirector } from '../../composables/useBlackjackDirector';
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
  AnimationSpeedButton, CardMotionLayer, cardIn, useAnimationSpeed, visibleRect,
  type CardMotionLayerPlay, type MotionTarget,
} from '../../animation';
import type { BlackjackAnchor, BlackjackTableEvent } from '../../model/blackjack/motion';
import { eventLine } from '../../model/blackjack/eventLog';
import { opponentsOf } from '../../model/blackjack/seatOrder';
import { slotRect } from '../../model/blackjack/slots';

// The Blackjack table: the dealer zone and the shoe, the status rail, the opponents and my seat with its permanent dock
// (bets, or the actions: disabled, never hidden, when it is not my turn); under the felt a status line and the footer. Every rule is the server's (`availableActions`, `betLimits`): the screen only shows and sends.
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
// The server answers a whole turn at once; the director tells it in order (the deal card by card, the dealer's draws,
// the results for a few seconds). `shown` is the table as told so far, `store.game` the latest state: the buttons and
// the rules read the latter, but they are only offered once the table is calm, so nothing changes under the player.
const { speed, cycle: cycleSpeed } = useAnimationSpeed();
const screenEl = ref<HTMLElement | null>(null);
const motionLayer = ref<{ play: CardMotionLayerPlay<BlackjackAnchor> } | null>(null);
const director = useBlackjackDirector({
  game: () => store.game, events: () => store.events, mySeat: () => store.mySeat, speed, layer: motionLayer,
});
onBeforeUnmount(() => director.cancel());
const shown = director.shown;
const holding = director.holding;
const shownMe = computed(() => shown.value?.players.find(p => p.seat === store.mySeat) ?? null);
const shownOpponents = computed(() => opponentsOf(shown.value?.players ?? [], store.mySeat));

const handText = computed(() => shown.value
  ? format(ui.header.hand, { n: shown.value.handNumber, total: shown.value.handsTotal }) : '');
const lobbySolo = computed(() => (store.lobby?.players.filter(p => p.joined).length ?? 0) === 1);
const myTurn = computed(() => !holding.value && store.game?.phase === 'PLAYING' && store.game.currentSeat === store.mySeat);
const betting = computed(() => !holding.value && store.game?.phase === 'BETTING' && !!store.me && !store.me.eliminated && !store.me.forfeited);
// The dock of my seat is always there while I am in the game (disabled actions when it is not my turn, even through the
// dealer's turn, the results and the end of a betting), so it never jumps and leaves no empty band. Out of the game: no dock.
const idleBar = computed(() => !myTurn.value && !betting.value && !!shownMe.value && !shownMe.value.eliminated
  && !shownMe.value.forfeited && store.game?.phase !== 'FINISHED');
const dockVisible = computed(() => betting.value || hasActions.value || idleBar.value);
const statusText = computed(() => {
  const g = shown.value;
  if (!g || !store.me) return '';
  if (holding.value) {
    const stage = director.stage.value;
    if (stage === 'bets' || stage === 'deal') return ui.wait.dealing;
    if (stage === 'dealer') return ui.wait.dealer;
    if (stage === 'results') return ui.wait.results;
  }
  if (g.phase === 'BETTING') return ui.wait.betting;
  if (myTurn.value) return ui.wait.yourTurn;
  if (g.currentSeat < 0 || g.players.every(p => p.hands.every(h => h.status !== 'ACTIVE' && h.status !== 'WAITING'))) {
    return ui.wait.dealer;
  }
  return format(ui.wait.for, { player: displayName(g.currentSeat) });
});
const bannerView = computed(() => {
  const b = director.banner.value;
  return b ? { key: b.key, text: ui.phase[b.kind] } : null;
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
// Purely visual: cards fly from the shoe to the next place of a hand, the hole card turns over. A place that cannot be
// found just skips the picture. The places are the card slots of the rows on screen (`slotRect`).
function resolveAnchor(anchor: BlackjackAnchor): MotionTarget | null {
  const root = screenEl.value;
  if (!root) return null;
  const find = (key: string) => root.querySelector<HTMLElement>(`[data-anchor="${key}"]`);
  if (anchor === 'shoe') {
    const host = find('shoe');
    const front = host?.querySelector<HTMLElement>('.bj-shoe-card--front') ?? null;
    const rect = visibleRect(front);
    return rect ? { rect, cards: [] } : cardIn(host, 56);
  }
  const cut = anchor.lastIndexOf(':');
  const index = Number(anchor.slice(cut + 1));
  const row = find(anchor.slice(0, cut))?.querySelector<HTMLElement>('[data-card-size]') ?? null;
  const box = visibleRect(row);
  if (!row || !box) return null;
  // A card already there is the place (a card turning over, a card leaving); an empty place is where the next one lands.
  const there = row.querySelectorAll<HTMLElement>('.playing_card')[index] ?? null;
  const thereRect = visibleRect(there);
  if (there && thereRect) return { rect: thereRect, cards: [there] };
  return { rect: slotRect(box, Number(row.dataset.cardSize) || 56, index), cards: [] };
}

// --- The end -----------------------------------------------------------------------------------------------------
// The end screen waits for the table to finish telling the last hand.
const showEnd = computed(() => store.screen === 'finished' && !holding.value);
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
      v-else-if="showEnd"
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

    <template v-else-if="store.game && shown">
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
        :game="shown"
        :opponents="shownOpponents"
        :me="shownMe"
        :my-seat="store.mySeat"
        :name-of="displayName"
        :disconnected="seatDisconnected"
        :seconds-remaining="seatSecondsRemaining"
        :tags="director.tags.value"
        :last-action="director.lastAction.value"
        :dealer-line="director.dealerLine.value"
        :banner="bannerView">
        <template v-if="dockVisible" #dock>
          <div class="controls" data-test="controls">
            <BlackjackBetPanel
              v-if="betting && store.game.betLimits"
              :limits="store.game.betLimits"
              :last-bet="store.game.lastBet"
              :placed="store.me?.betPlaced"
              :placed-amount="store.me?.bet"
              :stack="store.me?.stack"
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
            <BlackjackActionBar v-else :actions="[]" idle />
          </div>
        </template>
      </BlackjackTable>

      <div class="bottom-bar" data-test="bottom-bar">
        <span v-if="noticeText" class="status-notice" data-test="notice" role="alert">{{ noticeText }}</span>
        <span class="status-turn" :class="{ 'status-turn--mine': myTurn }" data-test="status">{{ statusText }}</span>
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
  --bj-hud-reserve: 7.5rem; /* room for the rules and settings buttons at the top right */
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--bj-space-2);
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  padding: var(--bj-space-2) var(--bj-space-3) calc(var(--bj-space-2) + env(safe-area-inset-bottom, 0px));
  background: var(--table-room-bg);
}
.bj-screen--lobby { display: block; padding: 0; background: none; }
.connecting { flex: 1 1 auto; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--bj-space-3);
  color: rgba(255, 255, 255, 0.85); font-family: var(--font-title); letter-spacing: 0.04em; text-align: center; }
.connecting-spinner { font-size: 1.5rem; color: var(--gold); }
.top-bar { flex: none; display: flex; align-items: center; gap: var(--bj-space-2); box-sizing: border-box;
  height: calc(var(--hud-inset) * 2 + var(--hud-chip-h) + env(safe-area-inset-top, 0px));
  padding: env(safe-area-inset-top, 0px) var(--bj-hud-reserve) 0 calc(var(--hud-inset) - var(--bj-space-3) + env(safe-area-inset-left, 0px));
  margin-top: calc(var(--bj-space-2) * -1); }
.hand-chip { display: inline-flex; align-items: center; height: var(--hud-chip-h); box-sizing: border-box; padding: 0 var(--bj-space-3); font-size: 0.8rem;
  font-weight: 700; font-family: var(--font-title); letter-spacing: 0.06em; text-transform: uppercase; color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 999px; }
/* Under the felt: the notice, the status line and the footer. The actions are in the dock of my seat, not here. */
.bottom-bar { flex: none; display: flex; flex-direction: column; align-items: center; gap: var(--bj-space-2); width: 100%; max-width: 1000px; margin: 0 auto; }
.controls { width: 100%; min-height: 3rem; display: flex; align-items: center; justify-content: center; }
.status-turn { font-size: 0.875rem; font-weight: 700; color: var(--gold); text-align: center; min-height: 1.25rem; transition: color 0.2s ease; }
.status-turn--mine { font-size: 1rem; }
.status-notice { font-size: 0.8125rem; color: rgb(var(--accent-negative-rgb)); font-weight: 700; }
.table-footer { width: 100%; display: flex; align-items: center; gap: var(--bj-space-2); }
.table-footer .speed { margin-left: auto; }
.table-footer :deep(.back-button.p-button) { min-height: 44px; }
.sr-only { position: absolute; width: 1px; height: 1px; margin: 0; padding: 0; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
.offline-notice { position: absolute; top: 56px; left: 50%; transform: translateX(-50%); z-index: 1800; width: max-content; max-width: calc(100% - 24px);
  box-sizing: border-box; font-size: 0.8rem; font-weight: 700; color: #fff; background: rgba(0, 0, 0, 0.82);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7); border-radius: 999px; padding: var(--bj-space-1) var(--bj-space-3); text-align: center; box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5); }
.forfeit-notice { position: absolute; top: 12px; left: 50%; transform: translateX(-50%); z-index: 1800; width: max-content;
  max-width: calc(100% - 24px); overflow-wrap: anywhere; text-align: center; }
.forfeit-fade-enter-active, .forfeit-fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.forfeit-fade-enter-from, .forfeit-fade-leave-to { opacity: 0; transform: translate(-50%, -8px); }
@media (prefers-reduced-motion: reduce) { .forfeit-fade-enter-active, .forfeit-fade-leave-active, .status-turn { transition: none; } }
</style>
