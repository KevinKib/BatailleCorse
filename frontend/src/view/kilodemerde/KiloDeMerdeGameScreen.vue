<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useKiloDeMerdeStore } from '../../state/KiloDeMerde.store';
import { useKiloDeMerdeBootstrap } from '../../composables/useKiloDeMerdeBootstrap';
import { useHotkeys } from '../../composables/useHotkeys';
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
import KiloDeMerdeHand from '../../components/kilodemerde/KiloDeMerdeHand.vue';
import KiloDeMerdeLobbyOptions from '../../components/kilodemerde/KiloDeMerdeLobbyOptions.vue';
import KiloDeMerdeRoundBanner from '../../components/kilodemerde/KiloDeMerdeRoundBanner.vue';
import KiloDeMerdeStandings from '../../components/kilodemerde/KiloDeMerdeStandings.vue';
import KiloDeMerdeTable from '../../components/kilodemerde/KiloDeMerdeTable.vue';
import { findGame, type GameDefinition } from '../../model/games';
import { seatDisplayName } from '../../model/table/displayName';
import { rematchButtonFor } from '../../model/RematchButton';
import { format, formatList, plural } from '../../locales/format';
import {
  AnimationSpeedButton, CardMotionLayer, defineAnchors, scaleMovements, useAnimationSpeed, useTableMotion,
  type CardMotionLayerPlay,
} from '../../animation';
import { countdown as countdownOf } from '../../model/kilodemerde/countdown';
import { faceLetters, kiloLabel } from '../../model/kilodemerde/kilo';
import { planMovements, type KiloDeMerdeAnchor, type KiloDeMerdeTableEvent } from '../../model/kilodemerde/motion';
import { roundLineText } from '../../model/kilodemerde/roundLineText';
import { roundSummary } from '../../model/kilodemerde/roundSummary';
import { KILO_DE_MERDE_TEMPO } from '../../model/kilodemerde/tempo';

// The Kilo de merde table: the ring of seats around the kilo pile (the slap target), my four cards (a tap passes one),
// the race and round banners. Every rule is the server's (`availableActions`): the screen only shows and sends.
// `game` is the registry entry of Kilo de merde; it defaults to the registered one, a test may hand its own.
const props = defineProps<{ gameId: string; game?: GameDefinition }>();
const messages = useI18n();
const ui = messages.kiloDeMerdeUi;
const letters = faceLetters(ui.ranks);
const store = useKiloDeMerdeStore();
const router = useRouter();
const definition = props.game ?? findGame('kilo-de-merde');
useKiloDeMerdeBootstrap(props.gameId, definition);

// The roster outlives a forfeit (the player leaves the active seats, the banner still names them). Names are user
// input: only ever rendered as text.
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

const disconnectCountdown = useSeatDisconnectCountdown({
  disconnections: () => store.liveDisconnections,
  isGameOver: () => store.screen === 'finished',
});
onBeforeUnmount(() => disconnectCountdown.cancel());
const seatDisconnected = (seat: number) => seat in store.liveDisconnections;
const seatSecondsRemaining = (seat: number) =>
  seatDisconnected(seat) ? disconnectCountdown.secondsRemainingFor(store.liveDisconnections[seat]) : null;

const { formattedDuration, cancel: cancelGameDuration } =
  useGameDuration(() => store.screen === 'playing', () => store.screen === 'finished');
onBeforeUnmount(() => cancelGameDuration());

const { online } = useConnectionStatus();
watch(online, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) void store.hydrate();
});

// --- What the table shows ------------------------------------------------------------------------------------------
const roundLine = computed(() => {
  const g = store.game;
  return g ? format(ui.table.roundLine, { round: g.round, total: g.roundsTotal, step: g.step }) : '';
});
const canPass = computed(() => store.can('PASS') && !store.busy);
const canSlap = computed(() => store.can('SLAP') && !store.busy);
const mePlayer = computed(() => store.game?.players.find(p => p.seat === store.mySeat) ?? null);
// The two waits where every seat confirms it is ready (before the first round, between two rounds).
const confirming = computed(() => store.game?.phase === 'AWAITING_START' || store.game?.phase === 'ROUND_OVER');
const canReady = computed(() => store.can('READY') && !store.busy);
const stillWaitedFor = computed(() => (store.game?.players ?? []).filter(p => p.active && !p.ready).length);
const readyPrompt = computed(() => store.game?.phase === 'AWAITING_START' ? ui.readyGate.startPrompt : ui.readyGate.roundPrompt);
const racing = computed(() => store.game?.phase === 'RACE' && mePlayer.value?.slapRank === null);
const roundOver = computed(() => store.game?.phase === 'ROUND_OVER');
const lines = computed(() => roundOver.value ? roundSummary(store.game?.lastOutcome ?? null) : []);
const hands = computed(() => roundOver.value ? store.game?.lastOutcome?.hands ?? null : null);
const lastSeats = computed(() => lines.value.flatMap(l => l.seat === null ? [] : [l.seat]));
const raceOpen = computed(() => store.game?.phase === 'RACE' ? store.game.race : null);
const noticeText = computed(() => {
  if (!store.notice) return '';
  return store.notice === 'connection' ? ui.notice.connection : ui.errors.wrongPhase;
});

// The clock of the countdown ring: the server sends the end of the timer, this ticks the seconds left.
const now = ref(Date.now());
let ticker: ReturnType<typeof setInterval> | null = null;
function stopTicker() { if (ticker !== null) { clearInterval(ticker); ticker = null; } }
watch(() => [store.game?.passTimerEndsAt ?? null, store.game?.raceEndsAt ?? null, store.game?.readyEndsAt ?? null], ([pass, race, ready]) => {
  stopTicker();
  if (pass === null && race === null && ready === null) return;
  now.value = Date.now();
  ticker = setInterval(() => { now.value = Date.now(); }, 250);
}, { immediate: true });
onBeforeUnmount(stopTicker);
const PASS_TIMER_MS = 15000;
const RACE_TIMER_MS = 5000;
const READY_TIMER_MS = 60000;
const countdown = computed(() => {
  const g = store.game;
  if (!g) return null;
  if (confirming.value) return countdownOf(g.readyEndsAt, now.value, READY_TIMER_MS);
  return g.phase === 'RACE' ? countdownOf(g.raceEndsAt, now.value, RACE_TIMER_MS) : countdownOf(g.passTimerEndsAt, now.value, PASS_TIMER_MS);
});

// The standings fold away on a phone: they sit over the table when asked for.
const standingsOpen = ref(false);

// --- The card I received -------------------------------------------------------------------------------------------
// After a step the card I received glows for a moment with the name of the one who gave it.
const receivedGlow = ref(false);
let glowTimer: ReturnType<typeof setTimeout> | null = null;
function clearGlowTimer() { if (glowTimer !== null) { clearTimeout(glowTimer); glowTimer = null; } }
onBeforeUnmount(clearGlowTimer);
const received = computed(() => {
  const r = store.game?.lastReceived;
  if (!receivedGlow.value || !r || !store.game?.hand.some(c => c.name === r.card.name)) return null;
  return { card: r.card, fromName: nameOrYou(r.from) };
});

// --- Live regions --------------------------------------------------------------------------------------------------
// A polite one for what I received and the round, an assertive one for a slap and the result of a round.
const politeText = ref('');
const assertiveText = ref('');
function speak(target: typeof politeText, text: string) {
  target.value = '';
  if (text) queueMicrotask(() => { target.value = text; });
}
watch(() => store.lastEvent?.id, () => {
  const event: KiloDeMerdeTableEvent | null = store.lastEvent;
  const g = store.game;
  if (!event || !g) return;
  const seat = event.data?.seat ?? null;
  switch (event.eventType) {
    case 'ROUND_START':
      speak(politeText, format(ui.live.round, { n: event.data?.round ?? g.round }));
      break;
    case 'STEP':
      clearGlowTimer();
      receivedGlow.value = true;
      glowTimer = setTimeout(() => { receivedGlow.value = false; glowTimer = null; }, KILO_DE_MERDE_TEMPO.receivedGlowMs);
      if (g.lastReceived) {
        speak(politeText, format(ui.live.received, { card: kiloLabel(g.lastReceived.card, letters), name: nameOrYou(g.lastReceived.from) }));
      }
      break;
    case 'SLAP':
      if (seat !== null) {
        speak(assertiveText, seat === store.mySeat ? ui.race.slappedYou : format(ui.live.slapped, { name: displayName(seat) }));
      }
      break;
    case 'DRAW':
      if (seat !== null && event.data?.kilosAfter != null) {
        speak(politeText, format(seat === store.mySeat ? ui.live.kilosChangeYou : ui.live.kilosChange,
          { name: displayName(seat), kg: event.data.kilosAfter }));
      }
      break;
    case 'ROUND_OVER':
      speak(assertiveText, roundSummary(g.lastOutcome).map(l => roundLineText(l, nameOrYou, ui.outcome, store.mySeat, letters)).join('. '));
      break;
  }
});

// --- Keys ----------------------------------------------------------------------------------------------------------
// Space slaps, 1 to 4 pass the card in that slot, R confirms I am ready. All only when the server allows the action. (Remapping lives with
// the game's registration, which declares the shortcuts.)
function slap() { if (canSlap.value) store.slap(); }
function ready() { if (canReady.value) store.ready(); }
function passSlot(slot: number) {
  const card = store.hand[slot];
  if (card && canPass.value && store.game?.myChoice === null) store.pass(card);
}
useHotkeys(
  () => {},
  slap,
  () => [],
  () => [' '],
  (key) => {
    if (/^[1-4]$/.test(key)) passSlot(Number(key) - 1);
    else if (key === 'r') ready();
  },
  () => store.screen === 'playing',
);

// --- The pictures of the table -------------------------------------------------------------------------------------
// Purely visual: a step sends one face-down card from every seat to its left neighbour, a draw flies the kilo card from
// the pile to the drawer, a flush sends the drawer's cards back. The state, the buttons and the clicks never wait for it.
const { speed, cycle: cycleSpeed } = useAnimationSpeed();
const screenEl = ref<HTMLElement | null>(null);
const motionLayer = ref<{ play: CardMotionLayerPlay<KiloDeMerdeAnchor> } | null>(null);

const resolveAnchor = defineAnchors<KiloDeMerdeAnchor>(() => screenEl.value, {
  seat: (anchor, q) => q.cardIn(q.find(`[data-test="seat-plate"][data-seat="${anchor.seat}"]`), 24),
  // The card of the slot that changed; the first card of the zone would be another card than the one swapped.
  hand: (anchor, q) => q.cardIn(q.find(
    anchor.slot === undefined ? '[data-test="hand"]' : `[data-test="hand"] [data-slot="${anchor.slot}"]`)),
  pile: (_, q) => q.cardIn(q.find('[data-test="pile"]')),
});

const motion = useTableMotion<KiloDeMerdeTableEvent, KiloDeMerdeAnchor>({
  layer: motionLayer,
  speed,
  bounds: KILO_DE_MERDE_TEMPO.queue,
  log: () => store.events,
  plan: (entry, { factor }) => {
    const movements = planMovements(entry, store.mySeat);
    return movements.length > 0 ? { movements: scaleMovements(movements, factor) } : null;
  },
});
void motion;

// --- The end -------------------------------------------------------------------------------------------------------
const winners = computed(() => (store.game?.result?.standings ?? []).filter(p => p.place === 1).map(p => p.seat));
const iWon = computed(() => winners.value.includes(store.mySeat));
const winnerLine = computed(() => {
  if (winners.value.length === 0) return undefined;
  const players = formatList(winners.value.map(nameOrYou), 'conjunction');
  return format(winners.value.length > 1 ? ui.end.winners : ui.end.winner, { players });
});
const endSubtitle = computed(() => {
  const g = store.game;
  const result = g?.result;
  if (!g || !result) return '';
  if (result.reason === 'ROUNDS_PLAYED') return format(ui.end.reasons.roundsPlayed, { n: g.roundsTotal });
  if (result.reason === 'PILE_EMPTY') return ui.end.reasons.pileEmpty;
  const left = g.players.filter(p => p.active);
  return left.length === 1 && left[0].seat === store.mySeat ? ui.end.youWonForfeit : ui.end.reasons.forfeit;
});
const rematchButton = computed(() =>
  rematchButtonFor(store.rematch, { playAgain: ui.end.playAgain, pending: ui.end.rematchPending }));
</script>

<template>
  <div ref="screenEl" class="kdm-screen" :class="{ 'kdm-screen--lobby': store.screen === 'lobby' }">
    <GameLobby
      v-if="store.screen === 'lobby' && store.lobby && definition"
      :game="definition"
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
        <KiloDeMerdeLobbyOptions
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
      <KiloDeMerdeStandings v-if="store.game" :players="store.game.players" :places="store.game.result?.standings ?? null"
                            :name-of="displayName" :my-seat="store.mySeat" />
    </EndGameOverlay>

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral felt screen. -->
    <div v-else-if="store.screen === 'connecting'" class="connecting" data-test="connecting" role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else-if="store.game">
      <RulesPanel :rules="messages.kiloDeMerde" />

      <div class="top-bar" data-test="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <button type="button" class="standings-toggle" data-test="standings-toggle" :aria-expanded="standingsOpen"
                :aria-label="ui.standings.toggle" @click="standingsOpen = !standingsOpen">
          <i class="pi pi-list" aria-hidden="true"></i>
          <span>{{ ui.table.standings }}</span>
        </button>
      </div>

      <!-- The cards that travel (a picture only, hidden from assistive technology), and the two live regions. -->
      <CardMotionLayer ref="motionLayer" :resolve="resolveAnchor" arrow-id="kilo-de-merde-arrow-head" mark-style="soft" />
      <p class="sr-only" role="status" aria-live="polite" aria-atomic="true" data-test="kilo-live-polite">{{ politeText }}</p>
      <p class="sr-only" role="alert" aria-live="assertive" aria-atomic="true" data-test="kilo-live-assertive">{{ assertiveText }}</p>

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

      <div v-if="standingsOpen" class="standings" data-test="standings">
        <KiloDeMerdeStandings :players="store.game.players" :places="null" :name-of="displayName" :my-seat="store.mySeat" />
      </div>

      <KiloDeMerdeTable
        :players="store.game.players"
        :my-seat="store.mySeat"
        :name-of="displayName"
        :round-line="roundLine"
        :countdown="countdown"
        :pile-size="store.game.pileSize"
        :can-slap="canSlap"
        :racing="racing"
        :ready="store.game.phase === 'PASSING'"
        :confirming="confirming"
        :countdown-kind="confirming ? 'ready' : store.game.phase === 'RACE' ? 'race' : 'pass'"
        :hands="hands"
        :last-seats="lastSeats"
        :disconnected="seatDisconnected"
        :seconds-remaining="seatSecondsRemaining"
        @slap="slap">
        <template #overlay>
          <KiloDeMerdeRoundBanner :race="raceOpen" :lines="lines" :name-of="nameOrYou" :my-seat="store.mySeat" />
        </template>
      </KiloDeMerdeTable>

      <div v-if="confirming" class="ready-gate" data-test="ready-gate">
        <template v-if="canReady || !store.me?.ready">
          <p class="ready-prompt" data-test="ready-prompt">{{ readyPrompt }}</p>
          <Button data-test="ready" class="ready-button" :label="ui.readyGate.button" icon="pi pi-check" aria-keyshortcuts="R"
                  :disabled="!canReady" @click="ready()" />
        </template>
        <p v-else class="ready-prompt" data-test="ready-waiting">
          {{ stillWaitedFor > 0 ? plural(ui.readyGate.waitingFor, stillWaitedFor) : ui.hand.waiting }}
        </p>
      </div>

      <div class="status" data-test="status">
        <span v-if="store.game.myChoice" class="status-wait">{{ ui.hand.waiting }}</span>
        <span v-if="noticeText" class="status-notice" data-test="notice" role="alert">{{ noticeText }}</span>
      </div>

      <div class="my-area" data-test="my-zone">
        <KiloDeMerdeHand :cards="store.hand" :my-choice="store.game.myChoice" :can-pass="canPass" :received="received"
                         @pass="card => store.pass(card)" />
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
.kdm-screen {
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
.kdm-screen--lobby { display: block; padding: 0; background: none; }
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
.standings-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 44px;
  box-sizing: border-box;
  padding: 0 12px;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  cursor: pointer;
}
.standings-toggle:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.standings {
  position: absolute;
  z-index: 5;
  top: calc(var(--hud-inset) * 2 + var(--hud-chip-h) + var(--space-2));
  left: var(--space-2);
  max-height: 50%;
  overflow-y: auto;
  padding: var(--space-2);
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.ready-gate {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: var(--space-2) var(--space-3);
  text-align: center;
}
.ready-prompt { margin: 0; font-size: 0.8125rem; color: rgba(255, 255, 255, 0.9); max-width: 32rem; }
/* 44 px touch target (frontend-spacing). */
.ready-gate :deep(.ready-button.p-button) { min-height: 44px; min-width: 7rem; }
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
