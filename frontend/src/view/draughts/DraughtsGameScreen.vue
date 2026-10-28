<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useDraughtsStore } from '../../state/Draughts.store';
import { useDraughtsBootstrap } from '../../composables/useDraughtsBootstrap';
import { useEndScreen } from '../../composables/useEndScreen';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useRemovedRedirect } from '../../composables/useRemovedRedirect';
import { useGameDuration } from '../../composables/useGameDuration';
import { useConnectionStatus } from '../../composables/useConnectionStatus';
import { useNarrowScreen } from '../../composables/useNarrowScreen';
import { useI18n } from '../../composables/useI18n';
import { useAnimationSpeed } from '../../animation';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import ForfeitBanner from '../../components/ForfeitBanner.vue';
import DisconnectOverlay from '../../components/DisconnectOverlay.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import GameTimer from '../../components/GameTimer.vue';
import GameLobby from '../../components/lobby/GameLobby.vue';
import DraughtsBoard from '../../components/draughts/DraughtsBoard.vue';
import DraughtsCounterChips from '../../components/draughts/DraughtsCounterChips.vue';
import DraughtsLobbyOptions from '../../components/draughts/DraughtsLobbyOptions.vue';
import DraughtsMoveList from '../../components/draughts/DraughtsMoveList.vue';
import DraughtsPlayerPlate from '../../components/draughts/DraughtsPlayerPlate.vue';
import { findGame } from '../../model/games';
import { planMove } from '../../model/draughts/motion';
import { tempoFor } from '../../model/draughts/tempo';
import { nameWithBotLevel } from '../../model/lobby/botLevel';
import { rematchButtonFor } from '../../model/RematchButton';
import { format, plural } from '../../locales/format';

// The draughts table: the board between two plates, what to do now, the draw and resign controls and the move list.
// Every rule is the server's (`legalMoves`): the screen only shows and sends. Not routed yet: the entry PR registers
// the game.
const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.draughtsUi;
const store = useDraughtsStore();
const router = useRouter();
const game = findGame('draughts');
useDraughtsBootstrap(props.gameId, game);

/** After the last move, the table stays up this long before the end screen. */
const END_HOLD_MS = 800;
/** The end screen never waits longer than this for a move to finish playing. */
const END_SCREEN_MAX_WAIT_MS = 6000;
/** The reasons that follow a move: the last move is worth showing before the end screen. */
const ENDS_ON_A_MOVE = ['NO_PIECES', 'BLOCKED', 'DRAW_REPETITION', 'DRAW_KINGS_ONLY', 'DRAW_ENDGAME'];

// Names are user input: only ever rendered as text. A player without a typed name is "Player N" (from 1).
// A computer seat also says its level: "Bot 1 (Expert)".
const displayName = (seat: number): string => {
  const name = store.game?.players.find(p => p.seat === seat)?.name?.trim() || format(ui.playerLabel, { n: seat + 1 });
  return nameWithBotLevel(messages, 'options.draughts.level', ui.botWithLevel, name, store.botLevels[seat]);
};
const nameOrYou = (seat: number): string => (seat === store.mySeat ? ui.you : displayName(seat));
const colourOf = (seat: number): 'white' | 'black' => (store.colourOf(seat) === 'WHITE' ? 'white' : 'black');

const me = computed(() => store.game?.players.find(p => p.seat === store.mySeat));
const opponent = computed(() => store.game?.players.find(p => p.seat !== store.mySeat));

const { online } = useConnectionStatus();
watch(online, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) void store.hydrate();
});
const boardDisabled = computed(() => !online.value || store.phase !== 'playing');

const { speed } = useAnimationSpeed();
const tempo = computed(() => tempoFor(speed.value));
// The opponent's last move stays marked until the next one; my own is already on the board by the time it comes back.
const lastMoveMarks = computed(() => {
  const last = store.game?.lastMove;
  return last && last.seat !== store.mySeat ? { from: last.from, to: last.to } : null;
});

// What the opponent (or I) just did, for the live region. A reload has no last move: nothing to say.
const announcement = computed(() => {
  const notice = store.drawNotice;
  if (notice) {
    return format(notice.kind === 'offered' ? ui.draw.offeredBy : ui.draw.declinedBy, { player: nameOrYou(notice.seat ?? -1) });
  }
  const move = store.lastMove;
  if (!move) return '';
  const history = store.game?.history ?? [];
  const notation = history[history.length - 1] ?? `${move.from}${move.captures.length > 0 ? 'x' : '-'}${move.to}`;
  const vars = { player: nameOrYou(move.seat), move: notation };
  return move.captures.length > 0 ? plural(ui.announce.movedTaking, move.captures.length, vars) : format(ui.announce.moved, vars);
});

const waitingFor = computed(() => {
  const current = store.game?.players.find(p => p.current);
  return current ? format(ui.table.waitingFor, { player: displayName(current.seat) }) : '';
});
const hint = computed(() => {
  if (!store.isMyTurn) return waitingFor.value;
  return store.selected !== null ? ui.table.hintDestination : ui.table.hintMove;
});
const captureHint = computed(() => (store.captureHint === null ? '' : plural(ui.table.mustCapture, store.captureHint)));
const noMoveYet = computed(() => (store.game?.history.length ?? 0) === 0);

// The chooser: the routes of an ambiguous destination, as a list (the numbered pieces on the board are the other way).
const choice = computed(() => store.choice ?? []);
const chooserTarget = computed(() => choice.value[0]?.to ?? 0);

const showDrawBar = computed(() => store.phase === 'playing' && (store.game?.drawOffersAllowed ?? false));
const drawOffererName = computed(() => (store.drawOfferedBy === null ? '' : nameOrYou(store.drawOfferedBy)));
const answeringDraw = computed(() => store.phase === 'playing' && store.incomingDrawOffer && store.canAnswerDraw);

// Resigning asks first, like leaving the table does: the same confirmation, the same forfeit.
function resign() {
  if (window.confirm(messages.leaveGuard.confirm)) store.forfeit();
}

// The move list sits beside the board on a wide screen and over the bottom of a phone, closed until asked for.
const narrow = useNarrowScreen(999);
const movesOpen = ref(!narrow.value);

const countdown = useSeatDisconnectCountdown({
  disconnections: () => store.liveDisconnections,
  isGameOver: () => store.phase === 'finished',
});
onBeforeUnmount(() => countdown.cancel());
const disconnectedSeat = computed(() => {
  const seats = Object.keys(store.liveDisconnections);
  return seats.length ? Number(seats[0]) : null;
});
const secondsRemaining = computed(() =>
  disconnectedSeat.value === null ? 0 : countdown.secondsRemainingFor(store.liveDisconnections[disconnectedSeat.value]));

const { formattedDuration, cancel: cancelGameDuration } =
  useGameDuration(() => store.phase === 'playing', () => store.phase === 'finished');
onBeforeUnmount(() => cancelGameDuration());

// Leaving an in-progress game forfeits (the opponent wins the seat), like the other tables.
useLeaveGuard({
  isInProgress: () => store.phase === 'playing',
  forfeit: () => store.forfeit(),
});

// A game that ended on a move keeps the table up while that move plays out, then a beat, before the end screen. A game
// found already over (a reload), or ended by a resignation or an agreement, shows the end screen at once.
const holdingLastMove = ref(false);
let holdTimer: ReturnType<typeof setTimeout> | null = null;
function stopHold() {
  if (holdTimer !== null) { clearTimeout(holdTimer); holdTimer = null; }
  holdingLastMove.value = false;
}
watch(() => store.phase, (phase) => {
  stopHold();
  const outcome = store.game?.outcome;
  const move = store.lastMove;
  if (phase !== 'finished' || !move || outcome?.status !== 'FINISHED' || !ENDS_ON_A_MOVE.includes(outcome.reason)) return;
  holdingLastMove.value = true;
  holdTimer = setTimeout(stopHold, planMove(move, tempo.value).totalMs + END_HOLD_MS);
});
const endScreen = useEndScreen(() => store.phase === 'finished', () => holdingLastMove.value, undefined, END_SCREEN_MAX_WAIT_MS);
endScreen.revealImmediatelyIfOver();
onBeforeUnmount(() => { stopHold(); endScreen.cancel(); });
const endVisible = computed(() => store.phase === 'finished' && endScreen.showEndOverlay.value);

const endSubtitle = computed(() => {
  const outcome = store.game?.outcome;
  if (outcome?.status !== 'FINISHED') return '';
  if (outcome.winner === null) {
    switch (outcome.reason) {
      case 'DRAW_REPETITION': return ui.draw.byRepetition;
      case 'DRAW_KINGS_ONLY': return ui.draw.byKingsOnly;
      case 'DRAW_ENDGAME': return ui.draw.byShortEnding;
      default: return ui.draw.agreed;
    }
  }
  const winner = displayName(outcome.winner);
  const loserSeat = outcome.winner === store.mySeat ? opponent.value?.seat ?? 0 : store.mySeat;
  if (outcome.reason === 'NO_PIECES') {
    return store.iWon ? format(ui.end.noPiecesWon, { player: displayName(loserSeat) }) : format(ui.end.noPiecesLost, { player: winner });
  }
  if (outcome.reason === 'BLOCKED') {
    return store.iWon ? format(ui.end.blockedWon, { player: displayName(loserSeat) }) : format(ui.end.blockedLost, { player: winner });
  }
  if (!store.iWon) return ui.end.youLostForfeit;
  const left = store.lastForfeit;
  if (left?.reason === 'RESIGNED') return format(ui.end.youWonResigned, { player: displayName(left.seat) });
  if (left?.reason === 'DISCONNECTED') return format(ui.end.youWonDisconnected, { player: displayName(left.seat) });
  return ui.end.youWonForfeit;
});
const winnerLine = computed(() => {
  const outcome = store.game?.outcome;
  return outcome?.status === 'FINISHED' && outcome.winner !== null
    ? format(ui.end.winner, { player: displayName(outcome.winner) })
    : undefined;
});
const endSummary = computed(() => {
  const history = store.game?.history ?? [];
  if (history.length === 0) return '';
  return format(ui.end.summary, { moves: plural(ui.end.moves, history.length), move: history[history.length - 1] });
});
const rematchButton = computed(() =>
  rematchButtonFor(store.rematch, { playAgain: ui.end.playAgain, pending: ui.end.rematchPending }));

// The host removed me from the lobby: lead to the Game selection with a notice.
useRemovedRedirect(toRef(store, 'removedByHost'), () => { store.removedByHost = false; });

// Leaving frees the seat: the leaver goes to the Game selection like every other exit.
function leaveLobby() {
  // Replace, not push: Back must not return to a room the player no longer sits in.
  if (store.leave()) router.replace({ name: 'games' });
}
</script>

<template>
  <div class="draughts-screen" :class="{ 'draughts-screen--lobby': store.phase === 'lobby' }">
    <GameLobby
      v-if="store.phase === 'lobby' && store.lobby && game"
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
      @set-option="(key, value) => store.setOption(key, value)">
      <template #options="{ descriptors, options, editable, setOption }">
        <DraughtsLobbyOptions
          :descriptors="descriptors"
          :options="options"
          :editable="editable"
          @set-option="setOption" />
      </template>
    </GameLobby>

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral felt screen, not the
         table, so the game UI never flashes before the lobby. -->
    <div v-else-if="store.phase === 'connecting'" class="connecting" data-test="connecting"
         role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else>
      <RulesPanel :rules="messages.draughts" />

      <div class="top-bar">
        <RouterLink :to="{ name: 'games' }" class="back-link" data-test="leave" :aria-label="ui.back">
          <span class="back-chip" aria-hidden="true"><i class="pi pi-chevron-left"></i></span>
        </RouterLink>
        <GameTimer :time="formattedDuration" inline />
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

      <div class="table" :class="{ 'table--moves': movesOpen }" :inert="endVisible">
        <DraughtsPlayerPlate
          v-if="opponent"
          class="area-opponent"
          :name="displayName(opponent.seat)"
          :colour="colourOf(opponent.seat)"
          :active="opponent.current && store.phase === 'playing'"
          :men="opponent.men"
          :kings="opponent.kings"
          :captured="opponent.captured" />

        <!-- The hint and the counts are one block of reserved height, whatever it says; a draw offer for me takes its place
             while it lasts, so nothing that comes and goes resizes the board. -->
        <div class="lower area-notes" :class="{ 'lower--offer': answeringDraw }">
          <div class="notes" data-test="notes">
            <p v-if="captureHint" class="capture-hint" data-test="capture-hint" role="status">{{ captureHint }}</p>
            <p v-if="store.phase === 'playing'" class="hint" data-test="hint">{{ hint }}</p>
            <p v-if="store.phase === 'playing' && noMoveYet" class="colour-hint" data-test="colour-hint">
              {{ format(ui.table.youPlay, { colour: store.myColour === 'WHITE' ? ui.white : ui.black }) }}
            </p>
            <DraughtsCounterChips
              v-if="store.phase === 'playing'"
              :counters="store.game?.drawCounters"
              :repeated-twice="store.repeatedTwice" />
          </div>
          <div v-if="answeringDraw" class="draw-incoming" data-test="draw-incoming"
               role="alertdialog" aria-labelledby="draw-incoming-title" aria-describedby="draw-incoming-hint">
            <p id="draw-incoming-title" class="draw-title">{{ format(ui.draw.incoming, { player: drawOffererName }) }}</p>
            <p id="draw-incoming-hint" class="sr-only">{{ ui.draw.incomingHint }}</p>
            <button type="button" class="btn btn--accept" data-test="draw-accept" @click="store.acceptDraw()">{{ ui.draw.accept }}</button>
            <button type="button" class="btn" data-test="draw-decline" @click="store.declineDraw()">{{ ui.draw.decline }}</button>
          </div>
        </div>

        <!-- The board takes what the fixed rows leave: nothing that comes and goes resizes it. -->
        <div class="board-slot area-board">
          <DraughtsBoard
            v-if="store.game"
            :board="store.game.board"
            :orientation="store.orientation"
            :selectable="store.selectableSquares"
            :selected="store.selected"
            :targets="store.targetSquares"
            :moves="store.game.legalMoves"
            :choice="store.choice"
            :last-move="lastMoveMarks"
            :motion="store.opponentLastMove"
            :tempo="tempo"
            :disabled="boardDisabled"
            :interactive="store.isMyTurn"
            :announcement="announcement"
            @select="square => store.squareClicked(square)"
            @clear="store.clearSelection()" />
        </div>

        <DraughtsPlayerPlate
          v-if="me"
          class="area-me"
          :name="ui.you"
          :colour="colourOf(me.seat)"
          :active="store.isMyTurn"
          :men="me.men"
          :kings="me.kings"
          :captured="me.captured" />

        <div class="actions area-actions" data-test="actions">
          <button v-if="showDrawBar" type="button" class="btn" data-test="draw-offer"
                  :disabled="!store.canOfferDraw" @click="store.offerDraw()">
            {{ store.myDrawOfferPending ? ui.draw.offered : ui.draw.offer }}
          </button>
          <button v-if="store.phase === 'playing'" type="button" class="btn btn--resign" data-test="resign" @click="resign()">
            {{ ui.table.resign }}
          </button>
          <button type="button" class="btn" data-test="moves-toggle" :aria-expanded="movesOpen ? 'true' : 'false'" aria-controls="draughts-moves"
                  @click="movesOpen = !movesOpen">
            <i class="pi pi-list" aria-hidden="true"></i>
            {{ ui.table.movesToggle }}
          </button>
        </div>

        <div v-if="movesOpen" id="draughts-moves" class="area-moves moves-panel">
          <DraughtsMoveList :history="store.game?.history ?? []" @close="movesOpen = false" />
        </div>
      </div>

      <!-- The routes of an ambiguous destination: over the bottom of the screen, so the board keeps its place. -->
      <div v-if="store.choice" class="chooser" data-test="chooser" role="group" aria-labelledby="chooser-intro">
        <div class="chooser-head">
          <p id="chooser-intro" class="chooser-intro">{{ format(ui.table.chooseIntro, { to: chooserTarget }) }}</p>
          <button type="button" class="chooser-cancel" data-test="choice-cancel" :aria-label="ui.table.chooseCancel"
                  @click="store.clearSelection()">
            <i class="pi pi-times" aria-hidden="true"></i>
          </button>
        </div>
        <div class="chooser-options">
          <button
            v-for="(candidate, index) in choice"
            :key="index"
            type="button"
            class="chooser-option"
            :data-test="`choice-option-${index}`"
            @click="store.chooseCandidate(index)">
            <span class="chooser-number" data-test="choice-number">{{ index + 1 }}</span>
            <span data-test="choice-takes">{{ format(ui.table.takes, { squares: candidate.captures.join(', ') }) }}</span>
          </button>
        </div>
      </div>

      <DisconnectOverlay
        v-if="disconnectedSeat !== null && store.phase === 'playing'"
        :opponent-label="displayName(disconnectedSeat)"
        :seconds-remaining="secondsRemaining" />

      <EndGameOverlay
        v-if="endVisible"
        data-test="end"
        :did-i-win="store.iWon"
        :result="store.isDraw ? 'draw' : undefined"
        :winner-line="winnerLine"
        :subtitle="endSubtitle"
        :rematch-button="rematchButton"
        :error="store.rematch === 'failed' ? ui.end.rematchFailed : undefined"
        @play-again="store.playAgain()">
        <p v-if="endSummary" class="end-summary" data-test="end-summary">{{ endSummary }}</p>
      </EndGameOverlay>
    </template>
  </div>
</template>

<style scoped>
/* The screen is exactly one viewport tall and never scrolls: a top bar, the table centred in what is left. The
   board takes what the fixed rows leave (see .board-slot), the rest is a few lines of text and three buttons. */
.draughts-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  /* Paints its own felt, like the other tables. */
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, var(--felt-center) 0%, var(--felt-mid) 48%, var(--felt-edge) 100%);
}
/* The lobby is a title-style screen: it sits on the shared felt background instead. */
.draughts-screen--lobby {
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
}
.connecting-spinner { font-size: 1.5rem; color: var(--gold); }

/* Back at the left, the duration chip, and room at the right for the rules toggle the panel pins there. */
.top-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  box-sizing: border-box;
  min-height: 44px;
  padding: env(safe-area-inset-top, 0px) 120px 0 0;
}
.back-link {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-left: calc(var(--space-2) * -1);
  color: #ffffff;
  text-decoration: none;
}
.back-link:focus-visible { outline: 2px solid var(--gold); outline-offset: -4px; border-radius: 50%; }
.back-chip {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.12);
  font-size: 0.75rem;
}

/* A phone: one column. Wide screens put the plates, the hint and the buttons on the left and the moves on the right. */
.table {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  width: 100%;
  max-width: 640px;
  margin: 0 auto;
}
.area-opponent, .area-me { flex: none; }
.area-moves { display: none; }
.moves-panel { display: block; }

.lower {
  position: relative;
  flex: none;
  width: 100%;
}
/* Room for the worst case: the capture line, the hint and one row of counts. */
.notes {
  min-height: 4.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  text-align: center;
}
.notes p { margin: 0; overflow-wrap: anywhere; }
.capture-hint { color: var(--gold); font-weight: 700; font-size: 0.95rem; }
.hint { font-size: 0.875rem; color: rgba(255, 255, 255, 0.85); }
.colour-hint { font-size: 0.75rem; color: rgba(255, 255, 255, 0.6); }

/* A draw offer for me takes the place of the block (its texts stay in the page, hidden, until it is answered). */
.lower--offer .notes { visibility: hidden; }
.draw-incoming {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  box-sizing: border-box;
  padding: 0 var(--space-1) 0 14px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(245, 200, 66, 0.7);
}
.draw-title { margin: 0; flex: 1 1 auto; min-width: 0; font-size: 0.875rem; font-weight: 700; color: var(--gold); overflow-wrap: anywhere; }

.board-slot {
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* Without container units the board keeps a share of the viewport. */
  --draughts-board-size: min(88vw, 44vh, 640px);
}
@supports (height: 1cqh) {
  .board-slot {
    container-type: size;
    --draughts-board-size: calc(min(100cqw, 100cqh) - 4px);
  }
}

.actions {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-2);
  width: 100%;
}
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  min-height: 44px;
  padding: 0 var(--space-4);
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  background: rgba(0, 0, 0, 0.35);
  color: #ffffff;
  font: inherit;
  font-size: 0.875rem;
  cursor: pointer;
}
.btn:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.btn:disabled { opacity: 0.55; cursor: default; }
.btn--accept { border: 0; background: #4ade80; color: #08210f; font-weight: 700; }
.btn--resign { border-color: rgba(248, 113, 113, 0.6); color: #fca5a5; }

/* On a phone the move list is a sheet over the bottom of the table. */
.moves-panel {
  position: absolute;
  left: var(--space-2);
  right: var(--space-2);
  bottom: calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  z-index: 20;
  max-height: 45%;
  display: flex;
  flex-direction: column;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);
  background: rgba(7, 22, 13, 0.96);
  border-radius: 12px;
}

.chooser {
  position: absolute;
  left: var(--space-2);
  right: var(--space-2);
  bottom: calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  z-index: 30;
  max-width: 560px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  box-sizing: border-box;
  max-height: 60%;
  overflow-y: auto;
  padding: var(--space-3) var(--space-3) var(--space-3) var(--space-4);
  border-radius: 12px;
  color: #ffffff;
  background: rgba(7, 22, 13, 0.97);
  border: 1px solid rgba(245, 200, 66, 0.55);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);
}
.chooser-head { display: flex; align-items: flex-start; gap: var(--space-2); }
.chooser-intro { margin: 0; flex: 1 1 auto; min-width: 0; padding-top: var(--space-2); font-size: 0.8125rem; color: rgba(255, 255, 255, 0.85); }
.chooser-cancel {
  flex: none;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  background: none;
  border: 0;
  color: rgba(255, 255, 255, 0.8);
  cursor: pointer;
}
.chooser-cancel:focus-visible { outline: 2px solid var(--gold); outline-offset: -4px; }
.chooser-options { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: var(--space-2); }
.chooser-option {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-3);
  border-radius: 6px;
  border: 1px solid var(--gold);
  background: rgba(245, 200, 66, 0.14);
  color: #ffffff;
  font: inherit;
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;
}
.chooser-option:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.chooser-number {
  flex: none;
  width: 18px;
  height: 18px;
  display: grid;
  place-items: center;
  border-radius: 9px;
  background: var(--gold);
  color: #141413;
  font-size: 0.75rem;
  font-weight: 700;
}

.end-summary { margin: 0; font-size: 0.8125rem; color: rgba(255, 255, 255, 0.6); font-variant-numeric: tabular-nums; }
.sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }

.forfeit-notice {
  position: absolute;
  top: 64px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1600;
}
.forfeit-fade-enter-active,
.forfeit-fade-leave-active { transition: opacity 0.25s ease; }
.forfeit-fade-enter-from,
.forfeit-fade-leave-to { opacity: 0; }

/* Over the table, like the forfeit notice: it comes and goes without resizing anything. */
.offline-notice {
  position: absolute;
  top: 104px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1600;
  white-space: nowrap;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 0.8rem;
  color: #fff;
  background: rgba(0, 0, 0, 0.7);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7);
  border-radius: 999px;
  padding: 6px 14px;
}

/* A wide screen: the plates, the hint and the buttons in a column of 260 px, the board, the moves beside it. */
@media (min-width: 1000px) and (min-height: 560px) {
  .draughts-screen { padding: var(--space-2) var(--space-6) var(--space-4); }
  .table {
    display: grid;
    max-width: 1100px;
    grid-template-columns: 260px minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr) auto auto;
    grid-template-areas:
      'opponent board'
      'notes    board'
      'me       board'
      'actions  board';
    column-gap: var(--space-6);
    align-items: stretch;
  }
  .table--moves {
    grid-template-columns: 260px minmax(0, 1fr) 240px;
    grid-template-areas:
      'opponent board moves'
      'notes    board moves'
      'me       board moves'
      'actions  board moves';
  }
  .area-opponent { grid-area: opponent; align-self: start; justify-self: start; }
  .area-notes { grid-area: notes; align-self: center; }
  .area-me { grid-area: me; justify-self: start; }
  .area-actions { grid-area: actions; justify-content: flex-start; }
  .area-board { grid-area: board; }
  .area-moves { grid-area: moves; display: block; }
  .moves-panel { position: static; max-height: none; box-shadow: none; background: none; border-radius: 0; display: flex; flex-direction: column; min-height: 0; margin: var(--space-2) 0; }
  .moves-panel :deep(.moves) { flex: 1 1 auto; }
  .notes { align-items: flex-start; text-align: left; }
  .chooser { left: var(--space-6); right: auto; bottom: var(--space-6); width: 260px; margin: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .forfeit-fade-enter-active,
  .forfeit-fade-leave-active { transition: none; }
}
</style>
