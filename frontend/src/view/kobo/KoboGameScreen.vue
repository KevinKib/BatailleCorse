<script setup lang="ts">
import { computed, onBeforeUnmount, toRef, watch } from 'vue';
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
import KoboEventLog from '../../components/kobo/KoboEventLog.vue';
import KoboScoreSheet from '../../components/kobo/KoboScoreSheet.vue';
import KoboFinalTotals from '../../components/kobo/KoboFinalTotals.vue';
import { findGame } from '../../model/games';
import { opponentsInPlayOrder } from '../../model/table/opponentOrder';
import { seatPositions } from '../../model/table/seatRing';
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
// Names are user input: only ever rendered as text. A player without a typed name is "Player N" (from 1).
const displayName = (seat: number): string =>
  store.roster[seat]?.name?.trim() || format(ui.playerLabel, { n: seat + 1 });

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
</script>

<template>
  <div class="kobo-screen" :class="{ 'kobo-screen--lobby': store.screen === 'lobby' }">
    <GameLobby
      v-if="store.screen === 'lobby' && store.lobby"
      :game="game"
      :lobby="store.lobby"
      :error="store.lobbyError"
      :starting="store.starting"
      @start="store.startGame()"
      @leave="leaveLobby"
      @remove-seat="seat => store.removeSeat(seat)"
      @rename="name => store.rename(name)" />

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

      <div class="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <span class="round-chip" data-test="round">{{ format(ui.table.round, { n: store.game.round }) }}</span>
      </div>

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
      <div class="table-frame" data-test="kobo-table"
           :class="{ 'table-frame--compact': compactOpponents, 'table-frame--round-end': sheetOpen }"
           :style="{ '--opponent-columns': columns }">
        <div class="table-center">
          <KoboPiles :game="store.game" :my-seat="store.mySeat" :current-name="currentName" />
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
              :highlighted="highlighted"
              :compact="compactOpponents"
              @pick="(seat: number, slot: number) => store.pickSlot(seat, slot)" />
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

      <div v-if="!sheetOpen" class="my-zone" data-test="my-zone">
        <KoboSeat
          v-if="me"
          :player="me"
          :label="ui.you"
          :ready="showsReady && isReady(me.seat)"
          :announcer="isAnnouncer(me.seat)"
          :final-turn="isFinalTurn(me.seat)" />
        <KoboTableau
          v-if="me"
          class="my-tableau"
          :player="me"
          :owner="ui.table.ownerYour"
          :group-label="ui.table.yourCards"
          :faces="faces"
          :clickable="clickable"
          :highlighted="highlighted"
          :selected="selectedOwnSlot"
          @pick="(seat: number, slot: number) => store.pickSlot(seat, slot)" />
        <KoboActionBar
          :game="store.game"
          :prompt="store.interaction.prompt"
          :phase-text="phaseText"
          :your-turn="yourTurn"
          :pending="store.pending"
          :busy="store.busy"
          :notice="store.notice"
          :can-hide="store.canHidePeek"
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
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, var(--felt-center) 0%, var(--felt-mid) 48%, var(--felt-edge) 100%);
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
  background:
    radial-gradient(ellipse at 50% 46%, transparent 18%, rgba(0, 0, 0, 0.55) 100%),
    radial-gradient(ellipse at 50% 42%, var(--felt-center) 0%, var(--felt-mid) 52%, var(--felt-edge) 100%);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 2px 30px rgba(0, 0, 0, 0.55), 0 10px 40px rgba(0, 0, 0, 0.45);
  isolation: isolate;
  --kobo-pile-card-w: clamp(48px, 7.5vh, 60px);
  --ring-top: 88px;
  --ring-bottom: 112px;
  --ring-h: 70px;
}
.table-frame--compact { --ring-top: 78px; --ring-h: 56px; }

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
  bottom: 8px;
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
  background: linear-gradient(to top, rgba(0, 0, 0, 0.30) 0%, rgba(0, 0, 0, 0.04) 100%);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px 14px 0 0;
  --kobo-card-w: clamp(48px, 8vh, 64px);
  --kobo-gap: 6px;
}
.my-zone :deep(.seat-label) { font-size: 0.8rem; padding: 4px 14px; }

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
