<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useKoboStore } from '../../state/Kobo.store';
import { useKoboBootstrap } from '../../composables/useKoboBootstrap';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useRemovedRedirect } from '../../composables/useRemovedRedirect';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import { useGameDuration } from '../../composables/useGameDuration';
import { useConnectionStatus } from '../../composables/useConnectionStatus';
import { useNarrowScreen } from '../../composables/useNarrowScreen';
import GameLobby from '../../components/lobby/GameLobby.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import ForfeitBanner from '../../components/ForfeitBanner.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import GameTimer from '../../components/GameTimer.vue';
import KoboActionBar from '../../components/kobo/KoboActionBar.vue';
import KoboSeat from '../../components/kobo/KoboSeat.vue';
import KoboTableau from '../../components/kobo/KoboTableau.vue';
import KoboPiles from '../../components/kobo/KoboPiles.vue';
import KoboMatchHint from '../../components/kobo/KoboMatchHint.vue';
import { useKoboMatchCoach } from '../../composables/useKoboMatchCoach';
import KoboEventLog from '../../components/kobo/KoboEventLog.vue';
import KoboScoreSheet from '../../components/kobo/KoboScoreSheet.vue';
import KoboFinalTotals from '../../components/kobo/KoboFinalTotals.vue';
import KoboSoundToggle from '../../components/kobo/KoboSoundToggle.vue';
import { planHold } from '../../model/kobo/displayed';
import { planMovements, type MotionAnchor, type Movement } from '../../model/kobo/motion';
import { TEMPO } from '../../model/kobo/tempo';
import {
  CARD_RATIO, CardMotionLayer, canAnimate, defineAnchors, useTableMotion,
  type CardMotionLayerPlay, type MotionPlan,
} from '../../animation';
import type { KoboLogEntry } from '../../model/kobo/KoboEvents';
import type { KoboState } from '../../model/kobo/KoboState';
import type Card from '../../model/Card';
import { liveAnnouncement } from '../../model/kobo/announcement';
import { useKoboSound } from '../../composables/useKoboSound';
import { findGame } from '../../model/games';
import { seatDisplayName } from '../../model/table/displayName';
import { opponentsInPlayOrder } from '../../model/table/opponentOrder';
import { seatPositions } from '../../model/table/seatRing';
import { koboCardWidths } from '../../model/kobo/cardSizes';
import { opponentColumns } from '../../model/kobo/faces';
import { eventHighlights, headlineEvent } from '../../model/kobo/eventLog';
import { endSummary } from '../../model/kobo/endSummary';
import { finalStandings } from '../../model/kobo/roundScores';
import { useEndScreen } from '../../composables/useEndScreen';
import { useEscapeKey } from '../../composables/useEscapeKey';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';

const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.koboUi;
const store = useKoboStore();
const router = useRouter();
const game = findGame('kobo')!;
useKoboBootstrap(props.gameId);

// The roster outlives a forfeit (the player leaves `players`, the banner still names him).
// Names are user input: only ever rendered as text. A player without a typed name is "Player N" (from 1);
// a computer player is "Bot N" whatever its name (shared rule). The log, the score sheet and the end screen
// all name seats through this.
const displayName = (seat: number): string =>
  seatDisplayName(Object.values(store.roster).map(p => ({ ...p, id: String(p.seat) })), seat,
    { player: ui.playerLabel, bot: ui.botLabel });

// Leaving a running game forfeits (the others carry on), same guard as the other games.
useLeaveGuard({
  isInProgress: () => store.screen === 'playing',
  forfeit: () => store.forfeit(),
});

// The host removed me from the lobby: lead to the Game selection with a notice.
useRemovedRedirect(toRef(store, 'removedByHost'), () => { store.removedByHost = false; });

// Leaving frees the seat. A leave that could not be sent keeps the player in the lobby, with the error shown.
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

// Opponents in the order they play after me. On a wide screen they sit on the table ellipse; on a phone they
// wrap into a compact grid. A crowded table (4 or more opponents) uses the compact tableaux on both.
const opponents = computed(() => opponentsInPlayOrder(
  (store.game?.players ?? []).map(p => ({ ...p, id: String(p.seat) })), store.mySeat)
  .filter(p => p.seat !== store.mySeat));
const narrow = useNarrowScreen();
const compactOpponents = computed(() => narrow.value || opponents.value.length >= 4);
// A crowded table on a phone (3 opponents or more) has no height to spare: the bar reserves one line of text there.
const crowdedPhone = computed(() => narrow.value && opponents.value.length >= 3);
const cardWidths = computed(() => koboCardWidths(opponents.value.length, narrow.value));
const ring = computed(() => seatPositions(opponents.value.length));
const columns = computed(() => opponentColumns(opponents.value.length));

// What I may see comes from the server (and my "Hide"); what I may click comes from `koboInteraction`, and is
// frozen while a command is on its way.
const faces = computed(() => store.faces);
const clickable = computed<Record<string, boolean>>(() => {
  if (store.busy) return {};
  return Object.fromEntries(Object.keys(store.interaction.intents).map(key => [key, true]));
});
// The slots named by the event the log line says (the next notable one replaces them: state, not a timer).
const highlighted = computed(() => eventHighlights(headlineEvent(store.events)));
const selectedOwnSlot = computed(() => store.pending.kind === 'blind-own' ? store.pending.ownSlot : null);
useEscapeKey(() => store.cancelPending());

// A tap on a card goes to the store, which knows what it means (see `koboInteraction`). A tap that is a match,
// right after the hint was shown, is the lesson learnt: the first-time cue goes for good.
const matchCoach = useKoboMatchCoach();
function pick(seat: number, slot: number) {
  if (store.interaction.directMatch && store.interaction.intents[`${seat}:${slot}`]) matchCoach.dismiss();
  store.pickSlot(seat, slot);
}
// The rival card picked for a match, waiting for the card I give (marked on its grid, like my blind-swap pick).
const pickedRivalSlot = (seat: number): number | null =>
  store.pending.kind === 'match-give' && store.pending.target.seat === seat ? store.pending.target.slot : null;
const showsCoachRing = computed(() => !matchCoach.seen.value && store.interaction.matchHint);
const me = computed(() => store.game?.players.find(p => p.seat === store.mySeat) ?? null);
const isReady = (seat: number) => store.game?.readySeats.includes(seat) ?? false;
const isFinalTurn = (seat: number) => store.game?.finalTurnsRemaining.includes(seat) ?? false;
const isAnnouncer = (seat: number) => store.game?.announcer === seat;
// Ready checks only matter while the table waits for everybody (memorising, between rounds).
const showsReady = computed(() => store.game?.phase === 'MEMORISING' || store.game?.phase === 'ROUND_OVER');

const WAITING_PHASES = ['MEMORISING', 'ROUND_OVER', 'FINISHED'];
const phaseText = computed(() => {
  const g = store.game;
  if (!g) return '';
  const actor = g.phase === 'GIFT' ? g.announcer : g.currentSeat;
  if (WAITING_PHASES.includes(g.phase) || actor === store.mySeat) return ui.phase.mine[g.phase];
  return format(ui.phase.other[g.phase], { player: displayName(actor ?? g.currentSeat) });
});
const yourTurn = computed(() =>
  store.isMyTurn && !!store.game && !WAITING_PHASES.includes(store.game.phase));
const currentName = computed(() => displayName(store.game?.currentSeat ?? 0));

// The gift and the end of a round share one score sheet under the table; the table stays on screen above it.
const sheetOpen = computed(() => (store.game?.phase === 'GIFT' || store.game?.phase === 'ROUND_OVER') && !!store.game.lastRound);

// End screen: shown a beat after the game is over (no animation to wait for); reloading into a finished game
// shows it at once. The final totals go in the overlay's slot.
const endScreen = useEndScreen(() => store.screen === 'finished', () => false);
endScreen.revealImmediatelyIfOver();
onBeforeUnmount(() => endScreen.cancel());
const summary = computed(() => (store.game ? endSummary(store.game, store.mySeat) : null));
const standings = computed(() => finalStandings(store.game?.players ?? []));
const winnerLine = computed(() => {
  const winners = summary.value?.winners ?? [];
  if (winners.length === 0) return undefined;
  const players = winners.map(displayName).join(', ');
  return format(winners.length > 1 ? ui.end.winners : ui.end.winner, { players });
});
const endSubtitle = computed(() => {
  const s = summary.value;
  if (!s) return '';
  if (!s.byForfeit) return s.lowestTotal === null ? '' : plural(ui.end.lowestTotal, s.lowestTotal);
  const left = store.lastForfeit;
  if (left?.reason === 'RESIGNED') return format(ui.end.youWonResigned, { player: displayName(left.seat) });
  if (left?.reason === 'DISCONNECTED') return format(ui.end.youWonDisconnected, { player: displayName(left.seat) });
  return ui.end.youWonOthersLeft;
});
const rematchButton = computed(() => ({
  label: store.rematch === 'pending' ? ui.end.rematchPending : ui.end.playAgain,
  disabled: store.rematch === 'pending',
}));

// --- Sound ---------------------------------------------------------------------------------------------------
// Off until the player turns it on; a short beep when the turn becomes mine.
const sound = useKoboSound();
watch(yourTurn, (mine, was) => { if (mine && !was) sound.play('turn'); });

// --- Cards that travel -----------------------------------------------------------------------------------------
// Purely visual: each new event of the log is played over the table as flying copies of the cards it moved. The
// state, the clicks and the one-click match never wait for it (nothing here is read back), and a place that cannot
// be found just skips the picture.
const screenEl = ref<HTMLElement | null>(null);
const motionLayer = ref<{ play: CardMotionLayerPlay<MotionAnchor> } | null>(null);
const SEAT_CARD_W = 34;

// Kobo's places, one resolver per kind of anchor (the shared `cardIn` is the card inside a place: its picture, the
// outline of an empty pile, or a card-sized rectangle in the middle of the place).
const resolveAnchor = defineAnchors<MotionAnchor>(() => screenEl.value, {
  draw: (_, q) => q.cardIn(q.find('[data-test="draw-pile"]')),
  discard: (_, q) => q.cardIn(q.find('[data-test="discard-pile"]')),
  hand: (_, q) => q.cardIn(q.find('[data-test="drawn-zone"]')),
  slot: (a, q) => q.cardIn(q.find(`[data-test="slot-${a.seat}-${a.slot}"]`)
    ?? q.find(`[data-test="slot-empty-${a.seat}-${a.slot}"]`)),
  newSlot: (a, q) => {
    const prefix = `slot-${a.seat}-`;
    const number = (el: HTMLElement) => Number(el.dataset.test?.slice(prefix.length));
    const last = q.findAll(`button[data-test^="${prefix}"]`)
      .reduce<HTMLElement | null>((best, el) => (!best || number(el) > number(best) ? el : best), null);
    return q.cardIn(last);
  },
  seat: (a, q) => {
    const host = a.seat === store.mySeat ? q.find('[data-test="my-zone"]') : q.find(`.seat-slot[data-seat="${a.seat}"]`);
    const box = q.rect(host);
    if (!box) return null;
    return { rect: { left: box.left + (box.width - SEAT_CARD_W) / 2, top: box.top, width: SEAT_CARD_W, height: SEAT_CARD_W * CARD_RATIO }, cards: [] };
  },
  table: (_, q) => {
    const cards = q.findAll('button[data-test^="slot-"] .playing_card').filter(card => q.rect(card) !== null);
    return cards.length > 0 ? { rect: { left: 0, top: 0, width: 1, height: 1 }, cards } : null;
  },
});

const nextFrame = () => new Promise<void>(resolve => {
  if (typeof requestAnimationFrame === 'function') requestAnimationFrame(() => resolve());
  else setTimeout(resolve, 16);
});

// What the table showed before the latest state: the pile top a card is about to cover, the card I was holding
// (the movement plan needs them; the state itself only has the result).
let gameBefore: KoboState | null = null;
let lastDrawnFace: Card | null = null;
watch(() => store.game, (next, previous) => {
  gameBefore = previous ?? null;
  const seen = next?.drawnCard ?? previous?.drawnCard ?? null;
  if (seen) lastDrawnFace = seen;
}, { flush: 'sync' });

// The state before each event, kept for the hold that follows its flights (read when the event is admitted).
const gameBeforeEntry = new WeakMap<KoboLogEntry, KoboState | null>();

/** The movements an event stands for (and its sounds): the shared orchestrator asks for them as each entry arrives. */
function planEvent(entry: KoboLogEntry): MotionPlan<MotionAnchor> | null {
  const data = entry.data;
  const missed = data?.action === 'MATCH' && data.success === false;
  if (missed) sound.play('miss');
  const before = gameBefore;
  const moves = planMovements(entry, store.mySeat, {
    drawnFace: before?.drawnCard ?? lastDrawnFace,
    discardBefore: before ? before.discardTop : undefined,
  });
  if (moves.length === 0) return null;
  if (moves.some(m => m.kind !== 'reveal') && !missed) sound.play('card');
  gameBeforeEntry.set(entry, before);
  return { movements: moves };
}

// The face of the card I draw comes with the state, which may land a moment after the event: wait one frame for it.
async function awaitFace(_entry: KoboLogEntry, plan: MotionPlan<MotionAnchor>): Promise<MotionPlan<MotionAnchor>> {
  await nextTick();
  const withFace = (list: Movement[]) => list.map(m => (m.flip && !m.card ? { ...m, card: store.game?.drawnCard ?? null } : m));
  let ready = withFace(plan.movements);
  if (ready.some(m => m.flip && !m.card)) { await nextFrame(); ready = withFace(plan.movements); }
  return { movements: ready };
}

// Events of a burst play one after the other, within bounds (see `TEMPO.queue`): past them an event only gets its
// rings, at once. Under reduced motion nothing flies. The table already shows the state of an event, so its copies are
// set up at once (they hold the places and show the old cards there) and only start flying when their turn comes.
useTableMotion<KoboLogEntry, MotionAnchor>({
  layer: motionLayer,
  bounds: TEMPO.queue,
  log: () => store.events,
  plan: planEvent,
  prepare: awaitFace,
  // Until its copies have landed the table still shows the old cards: a tap on one is judged on what is shown.
  onPlayed: (played, entry) => {
    if (!played.motion || !canAnimate() || !entry) return;
    store.holdDisplayed(planHold(gameBeforeEntry.get(entry) ?? null, played.plan.movements, played.endsAt));
  },
});

// --- Announcements ---------------------------------------------------------------------------------------------
// One polite region, always in the page, for what the event log does not say (see `liveAnnouncement`). The text is
// cleared and set again when the situation changes so that the same sentence twice in a row is read twice.
const liveText = computed(() => {
  const g = store.game;
  if (!g) return '';
  return liveAnnouncement({
    phase: g.phase,
    actor: g.phase === 'GIFT' ? g.announcer : g.currentSeat,
    mySeat: store.mySeat,
    promptText: store.interaction.prompt ? ui.prompt[store.interaction.prompt] : null,
    phaseText: phaseText.value,
    roundText: format(ui.live.round, { n: g.round }),
  });
});
const liveSpoken = ref(liveText.value);
watch(() => [store.game?.round, store.game?.phase, store.game?.currentSeat, liveText.value].join('|'), async () => {
  liveSpoken.value = '';
  if (!liveText.value) return;
  await nextTick();
  liveSpoken.value = liveText.value;
});
</script>

<template>
  <div ref="screenEl" class="kobo-screen" :class="{ 'kobo-screen--lobby': store.screen === 'lobby', 'kobo-screen--duel': opponents.length === 1 }">
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
      @set-private="value => store.setPrivate(value)" />

    <EndGameOverlay
      v-else-if="store.screen === 'finished' && endScreen.showEndOverlay.value && summary"
      data-test="end"
      :did-i-win="summary.iWon"
      :winner-line="winnerLine"
      :subtitle="endSubtitle"
      :rematch-button="rematchButton"
      :error="store.rematch === 'failed' ? ui.end.rematchFailed : undefined"
      @play-again="store.playAgain()">
      <KoboFinalTotals :rows="standings" :name-of="displayName" :my-seat="store.mySeat" />
    </EndGameOverlay>

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral felt screen. -->
    <div v-else-if="store.screen === 'connecting'" class="connecting" data-test="connecting"
         role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else-if="store.game">
      <RulesPanel :rules="messages.kobo" />

      <div class="top-bar" data-test="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <span class="round-chip" data-test="round">{{ format(ui.table.round, { n: store.game.round }) }}</span>
        <KoboSoundToggle :enabled="sound.enabled.value" @toggle="sound.toggle()" />
      </div>

      <!-- The cards that travel (a picture only, hidden from assistive technology), and the one live region. -->
      <CardMotionLayer ref="motionLayer" :resolve="resolveAnchor" :mark-ms="TEMPO.markMs"
                       :mark-reduced-ms="TEMPO.markReducedMs" arrow-id="kobo-arrow-head" />
      <p class="sr-only" role="status" aria-live="polite" aria-atomic="true" data-test="kobo-live">{{ liveSpoken }}</p>

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

      <!-- The table. On a phone it scrolls vertically inside its frame (never sideways, never the page). -->
      <div class="table-frame" data-test="kobo-table" role="region" :aria-label="ui.regions.table"
           :class="{
             'table-frame--compact': compactOpponents, 'table-frame--round-end': sheetOpen,
             'table-frame--duel': opponents.length === 1,
             'table-frame--hint': store.interaction.matchHint, 'table-frame--coach': showsCoachRing,
           }"
           :style="{ '--opponent-columns': columns }">
        <div class="table-center">
          <KoboPiles
            :game="store.game" :my-seat="store.mySeat" :current-name="currentName"
            :can-draw="store.interaction.piles.draw" :can-discard="store.interaction.piles.discard" :busy="store.busy"
            @draw="store.pickPile('draw')" @discard="store.pickPile('discard')" />
          <!-- On a wide table there is no height to spare under the piles: the first-time note goes beside my hand. -->
          <KoboMatchHint
            :reserve="!crowdedPhone"
            :hint="store.interaction.matchHint && !(showsCoachRing && !narrow)"
            :coach="showsCoachRing && narrow"
            @dismiss="matchCoach.dismiss()" />
        </div>

        <div class="opponents-ring" data-test="opponents">
          <div
            v-for="(opponent, i) in opponents"
            :key="opponent.seat"
            class="seat-slot"
            data-test="opponent-seat"
            :data-seat="opponent.seat"
            :style="{ '--seat-left': ring[i].left + '%', '--seat-top': ring[i].top + '%' }">
            <KoboSeat
              :player="opponent"
              :label="displayName(opponent.seat)"
              :ready="showsReady && isReady(opponent.seat)"
              :announcer="isAnnouncer(opponent.seat)"
              :final-turn="isFinalTurn(opponent.seat)"
              :disconnected="seatDisconnected(opponent.seat)"
              :seconds-remaining="seatSecondsRemaining(opponent.seat)" />
            <KoboTableau
              :player="opponent"
              :owner="format(ui.table.ownerOf, { player: displayName(opponent.seat) })"
              :group-label="format(ui.table.cardsOf, { player: displayName(opponent.seat) })"
              :faces="faces"
              :clickable="clickable"
              :match-mode="store.interaction.directMatch"
              :rival="true"
              :picked="pickedRivalSlot(opponent.seat)"
              :highlighted="highlighted"
              :compact="compactOpponents"
              :card-width="cardWidths.opponent"
              @pick="pick" />
          </div>
        </div>
      </div>

      <!-- The gift and the end of a round: the scores rise under the table, which stays above them. -->
      <div v-if="sheetOpen" class="sheet-slot" data-test="sheet-slot">
        <KoboScoreSheet
          :game="store.game"
          :my-seat="store.mySeat"
          :name-of="displayName"
          :busy="store.busy"
          :notice="store.notice"
          @give="seat => store.giveTen(seat)"
          @next-round="store.ready()" />
      </div>

      <div v-if="!sheetOpen" class="my-zone" :class="{ 'my-zone--duel': opponents.length === 1 }" data-test="my-zone" role="region" :aria-label="ui.regions.yours"
           :style="{ ...(cardWidths.mine ? { '--kobo-card-w': `${cardWidths.mine}px` } : {}), ...(crowdedPhone ? { '--kobo-bar-min': '48px', '--kobo-bar-lines': 1 } : {}) }">
        <KoboSeat
          v-if="me"
          :player="me"
          :label="ui.you"
          :ready="showsReady && isReady(me.seat)"
          :announcer="isAnnouncer(me.seat)"
          :final-turn="isFinalTurn(me.seat)" />
        <KoboMatchHint v-if="showsCoachRing && !narrow" class="coach-side" :hint="false" :coach="true" @dismiss="matchCoach.dismiss()" />
        <!-- The first-time cue rings my hand: a plain outline, no animation. -->
        <div class="my-hand" :class="{ 'coach-ring': showsCoachRing }" data-test="my-tableau-ring">
          <KoboTableau
            v-if="me"
            class="my-tableau"
            :player="me"
            :owner="ui.table.ownerYour"
            :group-label="ui.table.yourCards"
            :faces="faces"
            :clickable="clickable"
            :match-mode="store.interaction.directMatch"
            :highlighted="highlighted"
            :selected="selectedOwnSlot"
            @pick="pick" />
        </div>
        <KoboActionBar
          :game="store.game"
          :prompt="store.interaction.prompt"
          :phase-text="phaseText"
          :your-turn="yourTurn"
          :pending="store.pending"
          :busy="store.busy"
          :notice="store.notice"
          :can-hide="store.canHidePeek"
          :match-button="store.interaction.matchButton"
          @ready="store.ready()"
          @draw="store.draw()"
          @discard="store.discardDrawn()"
          @skip="store.skipPower()"
          @end-turn="store.endTurn()"
          @announce="store.announce()"
          @match="store.armMatch()"
          @cancel="store.cancelPending()"
          @hide="store.hidePeek()">
          <template #log>
            <KoboEventLog :events="store.events" :my-seat="store.mySeat" :name-of="displayName" />
          </template>
        </KoboActionBar>
      </div>

      <div class="table-footer" data-test="table-footer">
        <Button data-test="back" class="back-button" :label="ui.back" icon="pi pi-undo"
                severity="secondary" variant="text" rounded @click="router.push({ name: 'games' })" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.kobo-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  padding: var(--space-2) var(--space-2) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  /* A neutral room (anthracite, no green): the felt of the table stands out by its hue alone (shared tokens). */
  background: var(--table-room-bg);
}
/* The lobby is a title-style screen: it sits on the shared felt background instead. */
.kobo-screen--lobby {
  display: block;
  padding: 0;
  background: none;
}
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

/* Same top row as the Bullshit table: the duration chip, then the round, with the right corner free for the
   rules toggle. */
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
  --kobo-pile-card-w: clamp(48px, 7.5vh, 60px);
  --ring-top: 88px;
  --ring-bottom: 128px;
  --ring-h: 70px;
}
.table-frame--compact { --ring-top: 78px; --ring-h: 56px; }
/* A crowded phone reserves no hint zone under the piles, so its ring keeps the original room. */
@media (max-width: 767px) { .table-frame--compact { --ring-bottom: 112px; } }
/* The match hint (one or two lines) or the first-time note sits under the piles: the ring leaves room for it. */

/* Wide screens: opponents on the ellipse, in an inner box so a whole tableau stays inside the frame. */
.opponents-ring { position: absolute; inset: var(--ring-top) var(--ring-h) var(--ring-bottom); }
.seat-slot {
  position: absolute;
  left: var(--seat-left);
  top: var(--seat-top);
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.table-center {
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
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
  /* No panel around my zone (no fill, no border): the grid, the plate and the turn marker stand on the page. */
  --kobo-card-w: clamp(48px, 8vh, 64px);
  --kobo-gap: 6px;
}
/* Beside my hand, in the room the wide layout leaves free: it takes no height from the table above. */
.my-zone { position: relative; }
/* Two players: the bigger cards take height, which the table above gives back from my zone's top margin. */
.my-zone--duel { padding-top: 0; }
.my-zone .coach-side { position: absolute; left: 8px; top: 40px; width: 280px; margin: 0; z-index: 2; }
.my-hand { border-radius: 14px; padding: 2px; }
.my-hand.coach-ring { outline: 2px solid var(--gold); outline-offset: 0; box-shadow: 0 0 0 5px rgba(245, 200, 66, 0.2); }
.my-zone :deep(.seat-label) { font-size: 0.8rem; padding: 4px 14px; }
/* The dot of the turn ring sits clear of the name. */
.my-zone :deep(.seat-label--active) { padding-left: 22px; }

.table-footer { flex: none; display: flex; align-items: center; }

/* The score sheet takes the place of my zone: its room is its content, up to a share of the screen, and it scrolls
   inside itself beyond that. The table above gives way. */
.sheet-slot {
  flex: none;
  display: flex;
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
  max-height: min(420px, 62%);
}
/* The piles say nothing at the round end and the sheet leaves the table little room: the opponents' cards (all
   face up now) get all of it. */
.table-frame--round-end .table-center { display: none; }

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

/* Two players on a wide screen: the bigger cards need the height, so the lone opponent and the piles each move a few
   pixels toward their edge of the table (the layout is the same). */
@media (min-width: 768px) {
  .table-frame--duel .seat-slot { transform: translate(-50%, calc(-50% - 16px)); }
}

/* Phones: no ring. The piles stay at the top of the table, the opponents wrap into a compact grid below and
   scroll vertically inside the frame when there are many (or when a tableau grows). */
@media (max-width: 767px) {
  .table-frame {
    display: flex;
    flex-direction: column;
    border-radius: 20px;
    overflow-x: hidden;
    overflow-y: auto;
    overscroll-behavior: contain;
    --kobo-pile-card-w: 44px;
  }
  .table-center {
    position: static;
    transform: none;
    flex: none;
    padding: var(--space-2) var(--space-2) 0;
    --kobo-pile-gap: 16px;
  }
  .opponents-ring {
    position: static;
    display: grid;
    grid-template-columns: repeat(var(--opponent-columns), minmax(0, 1fr));
    gap: var(--space-2) var(--space-1);
    padding: var(--space-2) var(--space-1);
    align-items: start;
  }
  /* Two players: the cards are bigger, so the frame trims its own margins to keep the whole grid in view. */
  .table-frame--duel .table-center { padding-top: 0; }
  .kobo-screen--duel { gap: var(--space-1); }
  .kobo-screen--duel .my-zone { gap: 2px; }
  .table-frame--duel .seat-slot :deep(.kobo-seat) { flex-direction: row; flex-wrap: wrap; justify-content: center; gap: 2px 8px; }
  .table-frame--duel .seat-slot { gap: 2px; }
  .kobo-screen--duel .my-zone :deep(.seat-label) { padding-top: 2px; padding-bottom: 2px; }
  .table-frame--duel .opponents-ring { padding-top: 0; padding-bottom: 0; }
  .seat-slot {
    position: static;
    transform: none;
    min-width: 0;
    --seat-label-max: 6.5rem;
  }
  .my-zone { --kobo-card-w: 46px; --kobo-gap: 4px; padding-top: var(--space-1); }
  /* My plate on one line: the height is worth more to the table above. */
  .my-zone :deep(.kobo-seat) { flex-direction: row; flex-wrap: wrap; justify-content: center; gap: 2px 8px; }
}

@media (prefers-reduced-motion: reduce) {
  .forfeit-fade-enter-active, .forfeit-fade-leave-active { transition: none; }
}
</style>
