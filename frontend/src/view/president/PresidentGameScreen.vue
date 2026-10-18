<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { usePresidentStore } from '../../state/President.store';
import { usePresidentBootstrap } from '../../composables/usePresidentBootstrap';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useRemovedRedirect } from '../../composables/useRemovedRedirect';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import { useGameDuration } from '../../composables/useGameDuration';
import { useConnectionStatus } from '../../composables/useConnectionStatus';
import { useNarrowScreen } from '../../composables/useNarrowScreen';
import { useEndScreen } from '../../composables/useEndScreen';
import { useI18n } from '../../composables/useI18n';
import GameLobby from '../../components/lobby/GameLobby.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import ForfeitBanner from '../../components/ForfeitBanner.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import GameTimer from '../../components/GameTimer.vue';
import PresidentExchangeRecap from '../../components/president/PresidentExchangeRecap.vue';
import PresidentExchangePanel from '../../components/president/PresidentExchangePanel.vue';
import PresidentActionBar from '../../components/president/PresidentActionBar.vue';
import PresidentHand from '../../components/president/PresidentHand.vue';
import PresidentLobbyOptions from '../../components/president/PresidentLobbyOptions.vue';
import PresidentRoundSummary from '../../components/president/PresidentRoundSummary.vue';
import PresidentScores from '../../components/president/PresidentScores.vue';
import PresidentStandings from '../../components/president/PresidentStandings.vue';
import PresidentSeat from '../../components/president/PresidentSeat.vue';
import PresidentTrick from '../../components/president/PresidentTrick.vue';
import { findGame } from '../../model/games';
import { seatDisplayName } from '../../model/table/displayName';
import { opponentsInPlayOrder } from '../../model/table/opponentOrder';
import { seatPositions } from '../../model/table/seatRing';
import { rematchButtonFor } from '../../model/RematchButton';
import { ruleChips } from '../../model/president/chips';
import { hintText } from '../../model/president/hint';
import { format } from '../../locales/format';
import CardMotionLayer from '../../components/table/CardMotionLayer.vue';
import AnimationSpeedButton from '../../components/table/AnimationSpeedButton.vue';
import type { MotionRect, MotionTarget } from '../../components/table/motionTarget';
import { useAnimationSpeed } from '../../composables/useAnimationSpeed';
import { prefersReducedMotion } from '../../composables/usePileAnimation';
import { useSeatBubbles } from '../../composables/useSeatBubbles';
import { useInFlightCounts } from '../../composables/useInFlightCounts';
import { createTempoQueue } from '../../model/table/queue';
import { eventSpanMs } from '../../model/table/motion';
import {
  planBubbles, planMovements, planSweep, speedFactor, sweepStartMs, type PresidentAnchor, type PresidentMovement,
} from '../../model/president/motion';
import { closedPile, type PileView } from '../../model/president/trickView';
import { planExchangeFlights } from '../../model/president/exchangeMotion';
import { exchangeRecap } from '../../model/president/exchangeRecap';
import { PRESIDENT_TEMPO } from '../../model/president/tempo';
import type { PresidentLogEntry } from '../../model/president/eventLog';
import type { PresidentState } from '../../model/president/PresidentState';

// The President table: the opponents around the trick (a ring on a wide screen, a compact grid on a phone), my hand,
// and what I can do with it. Every rule is the server's (`legalPlays`): the screen only shows and sends. The exchange
// is the exchange panel; the round summary is the end-of-round sheet.
const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.presidentUi;
const store = usePresidentStore();
const router = useRouter();
const game = findGame('president')!;
usePresidentBootstrap(props.gameId);

// The roster outlives a forfeit (the player leaves `players`, the banner still names him). Names are user input:
// only ever rendered as text.
const displayName = (seat: number): string =>
  seatDisplayName(Object.values(store.roster).map(p => ({ ...p, id: String(p.seat) })), seat,
    { player: ui.playerLabel, bot: ui.botLabel });

// Leaving a running game forfeits (the others carry on), same guard as the other games.
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

// My own connection. While it is down the table says so; once it is back, events may have been missed.
const { online } = useConnectionStatus();
watch(online, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) void store.hydrate();
});

// --- The table ---------------------------------------------------------------------------------------------------
// Opponents in the order they play after me. Wide: on the ellipse around the trick. Phone: a grid of at most four
// plates a row (7 opponents are two rows) that scrolls inside its frame if it ever has to.
const opponents = computed(() => opponentsInPlayOrder(
  (store.game?.players ?? []).map(p => ({ ...p, id: String(p.seat) })), store.mySeat)
  .filter(p => p.seat !== store.mySeat));
const narrow = useNarrowScreen();
const ring = computed(() => seatPositions(opponents.value.length));
const columns = computed(() => Math.min(Math.max(opponents.value.length, 1), 4));
const chips = computed(() => store.game ? ruleChips(store.game) : []);
// What the last command did to the trick: who took it and why, and a forbidden finish. The text is the client's;
// the facts (winner, reason, forbidden) are the server's event.
const trickNotes = computed(() => {
  const data = store.lastEvent?.data;
  if (!data) return [];
  const notes: string[] = [];
  // a forbidden finish closes the trick too (a 2 always closes): its own sentence says both, the winner is the next leader
  if (!data.outForbidden && data.trickWinner !== null && data.trickWinner !== undefined && data.trickEndReason) {
    const template = ui.trickEnd[data.trickEndReason];
    if (template) notes.push(format(template, { player: displayName(data.trickWinner) }));
  }
  if (data.outForbidden) notes.push(format(ui.trickEnd.forbidden, { player: displayName(data.seat) }));
  return notes;
});

const roundText = computed(() => {
  const g = store.game;
  if (!g) return '';
  return g.rounds ? format(ui.table.roundOf, { n: g.round, total: g.rounds }) : format(ui.table.round, { n: g.round });
});

const isExchange = computed(() => store.game?.phase === 'EXCHANGE');
const isRoundOver = computed(() => store.game?.phase === 'ROUND_OVER');
const phaseText = computed(() => {
  const g = store.game;
  if (!g) return '';
  return store.isMyTurn ? ui.phase.yourTurn : format(ui.phase.waiting, { player: displayName(g.currentSeat) });
});
const hint = computed(() => hintText(store.playHint, ui));
const noticeText = computed(() => store.notice ? ui.notice[store.notice] : '');
const canReady = computed(() => store.can('READY') && !store.busy && !(store.me?.ready ?? false));

const receivedNames = computed(() => (store.game?.received ?? []).map(c => c.name));
const selectedNames = computed(() => store.selectedCards.map(c => c.name));

// One polite region for the turn: cleared and set again so that two turns in a row are both read.
const liveText = computed(() => store.isMyTurn && store.game?.phase === 'PLAYING' ? ui.live.yourTurn : '');
const liveSpoken = ref(liveText.value);
watch(() => [store.game?.round, store.game?.currentSeat, store.game?.version, liveText.value].join('|'), async () => {
  liveSpoken.value = '';
  if (!liveText.value) return;
  await nextTick();
  liveSpoken.value = liveText.value;
});

// --- The pictures of the table (spec §11 decision 7, option B) -------------------------------------------------------
// Purely visual: each new move of the log is played over the table (the cards fly from the seat to the pile, a bubble
// pops over a seat that passed, the winner of a closed trick is ringed). The state, the buttons and the clicks never
// wait for it, and a place that cannot be found just skips the picture. Reduced motion and Instant: still rings only.
const { speed, cycle: cycleSpeed } = useAnimationSpeed();
const still = computed(() => speed.value === 'instant' || prefersReducedMotion());
const screenEl = ref<HTMLElement | null>(null);
const motionLayer = ref<{ play: (m: PresidentMovement[], o?: { marksOnly?: boolean; startInMs?: number; still?: boolean }) => void } | null>(null);
const bubbles = useSeatBubbles({ gapMs: PRESIDENT_TEMPO.bubbleGapMs });
const inFlight = useInFlightCounts({ maxLagMs: PRESIDENT_TEMPO.countLagMaxMs });
// The cards of the exchange still flying into my hand: their place in the hand waits for them.
const landing = ref<string[]>([]);
const landingTimers = new Set<ReturnType<typeof setTimeout>>();
onBeforeUnmount(() => { bubbles.cancel(); inFlight.cancel(); landingTimers.forEach(clearTimeout); endHold(); });
const tempoQueue = createTempoQueue(() => Date.now(), PRESIDENT_TEMPO.queue);
const CARD_RATIO = 243.1375 / 167.575;

const visibleRect = (el: Element | null | undefined): MotionRect | null => {
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return r.width > 0 && r.height > 0 ? { left: r.left, top: r.top, width: r.width, height: r.height } : null;
};
const find = (selector: string) => screenEl.value?.querySelector<HTMLElement>(selector) ?? null;
/** A card-sized rectangle in the middle of a place (my hand zone, a seat without a fan). */
const cardAt = (box: MotionRect, width: number): MotionRect =>
  ({ left: box.left + (box.width - width) / 2, top: box.top + (box.height - width * CARD_RATIO) / 2, width, height: width * CARD_RATIO });

function resolveAnchor(anchor: PresidentAnchor): MotionTarget | null {
  switch (anchor.kind) {
    case 'seat': {
      if (anchor.seat === store.mySeat) {
        const box = visibleRect(find('[data-test="my-zone"] [data-test="hand"]')) ?? visibleRect(find('[data-test="my-zone"]'));
        return box ? { rect: cardAt(box, 44), cards: [] } : null;
      }
      const slot = find(`.seat-slot[data-seat="${anchor.seat}"]`);
      const fan = visibleRect(slot?.querySelector('.fan-backs'));
      // A card the height of the fan, so that the rings stay on the backs and never cover the count under them.
      if (fan) return { rect: cardAt(fan, Math.min(fan.width, fan.height / CARD_RATIO)), cards: [] };
      const box = visibleRect(slot);
      return box ? { rect: cardAt(box, 24), cards: [] } : null;
    }
    case 'pile': {
      const rect = visibleRect(find('[data-test="trick-cards"]'));
      return rect ? { rect, cards: [] } : null;
    }
    case 'pileSlot': {
      // The plays under the top one are not drawn: they leave from the middle of the pile, at the size of a card.
      const box = visibleRect(find('[data-test="trick-cards"]'));
      const card = visibleRect(find('[data-test="trick-cards"] .playing_card'));
      if (!box || !card) return null;
      return { rect: { left: box.left + (box.width - card.width) / 2, top: card.top, width: card.width, height: card.height }, cards: [] };
    }
    case 'rect':
      return { rect: { left: anchor.left, top: anchor.top, width: anchor.width, height: anchor.height }, cards: [] };
    case 'pileCard': {
      const card = find('[data-test="trick-cards"]')?.children[anchor.index]?.querySelector<HTMLElement>('.playing_card');
      const rect = visibleRect(card);
      return card && rect ? { rect, cards: [card] } : null;
    }
  }
}

// A closed trick stays on the pile as a picture (its closing play, dimmed, with the badge) until its cards have swept
// away toward the winner, even when the next play is already in the state: that play flies in once the sweep is over.
// Only a picture: the state, the buttons and the clicks never wait for it, and it ends by itself (timers, unmount).
const hold = ref<{ pile: PileView; id: number } | null>(null);
// The entry whose closed trick has been swept: its pile stays empty (badge only) until the next play, held or not.
const sweptId = ref<number | null>(null);
let holdDone: Promise<void> | null = null;
let releaseHold: (() => void) | null = null;
const holdTimers = new Set<ReturnType<typeof setTimeout>>();
const shownPile = computed<PileView>(() => hold.value?.pile ?? store.pile);
const pileSwept = computed(() => sweptId.value !== null && sweptId.value === (hold.value?.id ?? store.events[0]?.id));

function beginHold(pile: PileView, id: number) {
  hold.value = { pile, id };
  holdDone = new Promise<void>((resolve) => {
    releaseHold = () => { holdDone = null; releaseHold = null; resolve(); };
  });
}
function endHold() {
  holdTimers.forEach(clearTimeout);
  holdTimers.clear();
  hold.value = null;
  releaseHold?.();
}
/** The cards leave the pile at `sweepAtMs` (it shows no card from then on); the held picture ends at `endMs`. */
function scheduleHold(pile: PileView, id: number, sweepAtMs: number, endMs: number) {
  const later = (ms: number, action: () => void) => {
    const timer = setTimeout(() => { holdTimers.delete(timer); if (hold.value?.id === id) action(); }, ms);
    holdTimers.add(timer);
  };
  later(sweepAtMs, () => { sweptId.value = id; });
  later(endMs, endHold);
}

async function playEntry(entry: PresidentLogEntry) {
  const factor = speedFactor(speed.value);
  const isStill = still.value;
  for (const bubble of planBubbles(entry)) bubbles.show(bubble, PRESIDENT_TEMPO.bubbleMs * (factor || 1));
  const moves = planMovements(entry, store.mySeat, factor || 1);
  if (moves.length === 0) return;
  // A trick this move closes is swept toward its winner after the badge's hold: the sweep takes its time in the queue
  // (the next move starts once it is over), and when the queue is full there is no sweep.
  const closed = !isStill && motionLayer.value ? closedPile(entry) : null;
  const sweep = closed ? planSweep(entry, store.mySeat, factor || 1) : [];
  const sweepAt = sweepStartMs(entry, store.mySeat, factor || 1);
  const spanMs = sweep.length > 0 ? sweepAt + eventSpanMs(sweep) + PRESIDENT_TEMPO.sweep.landHoldMs : eventSpanMs(moves);
  const admission = isStill ? { startInMs: 0, motion: false } : tempoQueue.admit(spanMs);
  const arrivedAt = Date.now();
  // The pile of the trick before this one is held by its sweep: where this move lands is known only once it clears.
  while (holdDone) await holdDone;
  const startInMs = Math.max(0, admission.startInMs - (Date.now() - arrivedAt));
  const sweeps = closed !== null && admission.motion && sweep.length > 0;
  // Keep the closed trick's pile on screen from now on, before the DOM moves on to the next state.
  if (closed && sweeps) beginHold(closed, entry.id);
  // The pile shows the new play once the DOM has the new state.
  await nextTick();
  if (closed && sweeps) scheduleHold(closed, entry.id, startInMs + sweepAt, startInMs + spanMs);
  if (admission.motion && entry.seat !== store.mySeat) {
    const flights = moves.filter(m => m.kind === 'travel');
    inFlight.add(entry.seat, flights.length, startInMs + eventSpanMs(flights));
  }
  motionLayer.value?.play(moves, { marksOnly: !admission.motion, startInMs, still: isStill });
  // The cards of the closed trick leave once the badge has had its moment (their copies wait out of sight until then).
  if (sweeps) motionLayer.value?.play(sweep, { startInMs: startInMs + sweepAt, still: false });
}

// The log is newest first: what is new is what comes before the head I already knew.
watch(() => store.events, (events, previous) => {
  const known = previous?.[0] ?? null;
  const fresh: PresidentLogEntry[] = [];
  for (const entry of events) {
    if (entry === known) break;
    fresh.push(entry);
  }
  fresh.reverse().forEach(entry => { void playEntry(entry); });
});

// The exchange (spec §11 decision 7): the cards the exchange moved fly between the hands, from the two successive
// states (the events carry counts, never cards). The state already has them in their new hand: the seats' counts and
// my own hand wait for the landing. Same queue, speed and rings as the plays.
async function playExchange(change: { before: PresidentState; after: PresidentState }) {
  const factor = speedFactor(speed.value);
  const isStill = still.value;
  const moves = planExchangeFlights(change.before, change.after, store.mySeat, factor || 1);
  if (moves.length === 0) return;
  // The summary comes in once the flights and their rings are over: it moves the hand, which the flights and the rings
  // were measured on.
  recapHeld.value = true;
  await nextTick();
  const admission = isStill ? { startInMs: 0, motion: false } : tempoQueue.admit(eventSpanMs(moves));
  const endMs = isStill ? PRESIDENT_TEMPO.markReducedMs
    : Math.max(PRESIDENT_TEMPO.markMs, eventSpanMs(moves) + Math.max(...moves.map(m => m.holdAfterMs)));
  const release = setTimeout(() => { landingTimers.delete(release); recapHeld.value = false; }, admission.startInMs + endMs);
  landingTimers.add(release);
  if (admission.motion) {
    const flights = moves.filter(m => m.kind === 'travel');
    for (const m of flights) {
      const landsInMs = admission.startInMs + m.delayMs + m.durationMs;
      if (m.from.kind === 'seat' && m.from.seat !== store.mySeat) inFlight.add(m.from.seat, 1, landsInMs);
      if (m.to.kind === 'seat' && m.to.seat !== store.mySeat) inFlight.add(m.to.seat, -1, landsInMs);
      if (m.to.kind === 'seat' && m.to.seat === store.mySeat && m.card) {
        const name = m.card.name;
        landing.value = [...landing.value, name];
        const timer = setTimeout(() => {
          landingTimers.delete(timer);
          const at = landing.value.indexOf(name);
          if (at >= 0) landing.value = landing.value.filter((_, i) => i !== at);
        }, Math.min(landsInMs, PRESIDENT_TEMPO.countLagMaxMs));
        landingTimers.add(timer);
      }
    }
  }
  motionLayer.value?.play(moves, { marksOnly: !admission.motion, startInMs: admission.startInMs, still: isStill });
}
watch(() => store.stateChange, change => { if (change) void playExchange(change); });

// The summary of the exchange stays over the action bar until it is closed (once per round).
const closedRecapRound = ref<number | null>(null);
const recapHeld = ref(false);
const RECAP_PHONE_MS = 8000;
let recapTimer: ReturnType<typeof setTimeout> | null = null;
onBeforeUnmount(() => { if (recapTimer) clearTimeout(recapTimer); });
const recap = computed(() => store.game ? exchangeRecap(store.game, store.mySeat) : []);
const showRecap = computed(() => recap.value.length > 0 && closedRecapRound.value !== store.game?.round && !recapHeld.value);
// On a phone the summary is a banner over the felt: it goes by itself after a few seconds (the cross closes it sooner).
watch(showRecap, shown => {
  if (recapTimer) clearTimeout(recapTimer);
  recapTimer = null;
  if (!shown || !narrow.value) return;
  const round = store.game?.round ?? null;
  recapTimer = setTimeout(() => { closedRecapRound.value = round; }, RECAP_PHONE_MS);
}, { immediate: true });

// --- The end -------------------------------------------------------------------------------------------------------
const endScreen = useEndScreen(() => store.screen === 'finished', () => false);
endScreen.revealImmediatelyIfOver();
onBeforeUnmount(() => endScreen.cancel());
const winners = computed(() => {
  const outcome = store.game?.outcome;
  return outcome?.status === 'FINISHED' ? outcome.winners : [];
});
const iWon = computed(() => winners.value.includes(store.mySeat));
const winnerLine = computed(() => {
  if (winners.value.length === 0) return undefined;
  const players = winners.value.map(displayName).join(', ');
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
  const points = store.game?.standings.find(s => s.seat === winners.value[0])?.points;
  return points === undefined ? '' : format(ui.end.standing, { n: points });
});
const rematchButton = computed(() =>
  rematchButtonFor(store.rematch, { playAgain: ui.end.playAgain, pending: ui.end.rematchPending }));
</script>

<template>
  <div ref="screenEl" class="president-screen" :class="{ 'president-screen--lobby': store.screen === 'lobby' }">
    <GameLobby
      v-if="store.screen === 'lobby' && store.lobby"
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
      @set-option="(key, value) => store.setOption(key, value)">
      <template #options="{ descriptors, options, editable, setOption }">
        <PresidentLobbyOptions
          :descriptors="descriptors"
          :options="options"
          :editable="editable"
          @set-option="setOption" />
      </template>
    </GameLobby>

    <EndGameOverlay
      v-else-if="store.screen === 'finished' && endScreen.showEndOverlay.value"
      data-test="end"
      :did-i-win="iWon"
      :winner-line="winnerLine"
      :subtitle="endSubtitle"
      :rematch-button="rematchButton"
      :error="store.rematch === 'failed' ? ui.end.rematchFailed : undefined"
      @play-again="store.playAgain()">
      <PresidentStandings :standings="store.game?.standings ?? []" :name-of="displayName" :my-seat="store.mySeat" />
    </EndGameOverlay>

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral felt screen. -->
    <div v-else-if="store.screen === 'connecting'" class="connecting" data-test="connecting"
         role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else-if="store.game">
      <RulesPanel :rules="messages.president" />

      <div class="top-bar" data-test="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <span class="round-chip" data-test="round">{{ roundText }}</span>
        <PresidentScores :standings="store.game.standings" :name-of="displayName" :my-seat="store.mySeat" />
      </div>

      <!-- The cards that travel (a picture only, hidden from assistive technology), and the one live region. -->
      <CardMotionLayer ref="motionLayer" :resolve="resolveAnchor" :mark-ms="PRESIDENT_TEMPO.markMs"
                       :mark-reduced-ms="PRESIDENT_TEMPO.markReducedMs" arrow-id="president-arrow-head" />
      <p class="sr-only" role="status" aria-live="polite" aria-atomic="true" data-test="president-live">{{ liveSpoken }}</p>

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

      <div class="table-frame" data-test="president-table" role="region" :aria-label="ui.table.opponents"
           :style="{ '--opponent-columns': columns }">
        <div class="opponents-ring" data-test="opponents">
          <div
            v-for="(opponent, i) in opponents"
            :key="opponent.seat"
            class="seat-slot"
            data-test="opponent-seat"
            :data-seat="opponent.seat"
            :style="narrow ? undefined : { '--seat-left': ring[i].left + '%', '--seat-top': ring[i].top + '%' }">
            <PresidentSeat
              :player="opponent"
              :label="displayName(opponent.seat)"
              :current="store.game.phase === 'PLAYING' && store.game.currentSeat === opponent.seat"
              :show-ready="isRoundOver"
              :disconnected="seatDisconnected(opponent.seat)"
              :seconds-remaining="seatSecondsRemaining(opponent.seat)"
              fan
              :count="inFlight.shown(opponent.seat, opponent.handSize)"
              :narrow="narrow"
              :compact="narrow"
              :bubble="bubbles.bySeat.value[opponent.seat] ?? null"
              :still="still" />
          </div>
        </div>
        <div class="table-center" role="region" :aria-label="ui.table.pile">
          <PresidentTrick :trick="store.game.trick" :pile="shownPile" :swept="pileSwept" :still="still" :my-seat="store.mySeat"
                          :name-of="displayName" :chips="chips" :notes="trickNotes" />
        </div>
      </div>

      <div class="my-zone" data-test="my-zone" role="region" :aria-label="ui.table.yourCards">
        <PresidentSeat
          v-if="store.me"
          :player="store.me"
          :label="ui.you"
          :current="store.isMyTurn && store.game.phase === 'PLAYING'"
          :show-ready="isRoundOver"
          :bubble="bubbles.bySeat.value[store.mySeat] ?? null"
          :still="still" />
        <PresidentHand :cards="store.hand" :selected="selectedNames" :received="receivedNames" :landing="landing" @toggle="name => store.toggleCard(name)" />
        <PresidentRoundSummary
          v-if="isRoundOver"
          :players="store.game.players"
          :round="store.game.round"
          :last-round="store.game.lastRound"
          :name-of="displayName"
          :can-ready="canReady"
          @ready="store.ready()" />
        <PresidentExchangePanel
          v-else-if="isExchange"
          :pairs="store.game.exchange"
          :my-seat="store.mySeat"
          :title="store.me?.title ?? null"
          :received-count="store.game.received.length"
          :selected-count="store.selectedCards.length"
          :can-give="store.canGiveBack"
          :name-of="displayName"
          @give="store.giveBack()"
          @suggest="store.suggestGiveBack()"
          @clear="store.clearSelection()" />
        <PresidentExchangeRecap v-if="showRecap" :lines="recap" :name-of="displayName"
                                @close="closedRecapRound = store.game.round" />
        <PresidentActionBar
          v-if="!isRoundOver && !isExchange"
          :phase-text="phaseText"
          :primary-label="ui.actions.play"
          :can-primary="store.canPlay"
          :can-pass="store.can('PASS') && !store.busy"
          :has-selection="store.selectedCards.length > 0"
          :hint="hint"
          :notice="noticeText"
          :can-complete="store.canComplete"
          @complete="store.complete()"
          @primary="store.play()"
          @pass="store.pass()"
          @clear="store.clearSelection()" />
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
.president-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  padding: var(--space-2) var(--space-2) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  /* The room and the felt are the shared ones (Kobo's): one look for every card table. */
  background: var(--table-room-bg);
}
.president-screen--lobby { display: block; padding: 0; background: none; }
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

.table-frame {
  position: relative;
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
  box-sizing: border-box;
  border-radius: 50% / 42%;
  background: var(--table-felt-bg);
  border: var(--table-felt-border);
  box-shadow: var(--table-felt-shadow);
  isolation: isolate;
  --president-pile-card-w: clamp(48px, 7.5vh, 62px);
}
/* Wide screens: opponents on the ellipse, the trick in the middle. */
.opponents-ring { position: absolute; inset: 64px 90px 64px; }
.seat-slot {
  position: absolute;
  left: var(--seat-left);
  top: var(--seat-top);
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
}
.table-center {
  position: absolute;
  left: 50%;
  top: 56%;
  transform: translate(-50%, -50%);
  z-index: 1;
}

.my-zone {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--space-2) var(--space-2) 0;
  box-sizing: border-box;
  /* No panel around my zone (as Kobo's): the hand, the plate and the bar stand on the room. */
}
.my-zone :deep(.seat-label) { font-size: 0.8rem; padding: 4px 14px; }

.table-footer { flex: none; display: flex; align-items: center; gap: var(--space-2); }
.table-footer .speed { margin-left: auto; }

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

/* Phones: no ring. The opponents sit in a grid of at most four plates a row, the trick under them; the frame
   scrolls vertically inside itself if the content is taller (never sideways, never the page). */
@media (max-width: 767px) {
  .table-frame {
    display: flex;
    flex-direction: column;
    border-radius: 20px;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
    --president-pile-card-w: 44px;
  }
  .opponents-ring {
    position: static;
    display: grid;
    grid-template-columns: repeat(var(--opponent-columns), minmax(0, 1fr));
    gap: var(--space-2) var(--space-1);
    padding: var(--space-2) var(--space-1) 0;
    align-items: start;
    flex: none;
  }
  .seat-slot { position: static; transform: none; min-width: 0; --seat-label-max: 5.5rem; }
  /* My own plate is one line (name, cards, marker) rather than a stack: the room goes to the felt. */
  .my-zone :deep(.president-seat), .my-zone :deep(.seat-hand) {
    flex-direction: row; flex-wrap: wrap; justify-content: center; align-items: center; gap: 2px 8px;
  }
  .table-center { position: static; transform: none; flex: 1 1 auto; display: flex; align-items: center; justify-content: center; padding: var(--space-2); }
}

@media (prefers-reduced-motion: reduce) {
  .forfeit-fade-enter-active, .forfeit-fade-leave-active { transition: none; }
}
</style>
