<script setup lang="ts">
import { computed, onBeforeUnmount, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useMangolaStore, STUCK_START_KEY } from '../../state/Mangola.store';
import { useMangolaBootstrap } from '../../composables/useMangolaBootstrap';
import { useSowingAnimation } from '../../composables/useSowingAnimation';
import { AnimationSpeedButton, useAnimationSpeed } from '../../animation';
import { useEndScreen } from '../../composables/useEndScreen';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useRemovedRedirect } from '../../composables/useRemovedRedirect';
import { useGameDuration } from '../../composables/useGameDuration';
import { useConnectionStatus } from '../../composables/useConnectionStatus';
import { useI18n } from '../../composables/useI18n';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import ForfeitBanner from '../../components/ForfeitBanner.vue';
import DisconnectOverlay from '../../components/DisconnectOverlay.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import GameTimer from '../../components/GameTimer.vue';
import YourTurnIndicator from '../../components/YourTurnIndicator.vue';
import GameLobby from '../../components/lobby/GameLobby.vue';
import MangolaLobbyOptions from '../../components/mangola/MangolaLobbyOptions.vue';
import MangolaBoard from '../../components/mangola/MangolaBoard.vue';
import MangolaPlacementEditor from '../../components/mangola/MangolaPlacementEditor.vue';
import { findGame } from '../../model/games';
import { seatNameWithBotLevel } from '../../model/lobby/botLevel';
import { rowsWithBoard } from '../../model/mangola/board';
import { moveSentence } from '../../model/mangola/announce';
import { NO_BOARD_MARKS } from '../../model/mangola/marks';
import { rematchButtonFor } from '../../model/RematchButton';
import { format, plural } from '../../locales/format';

/** The end screen never waits longer than this for a sowing to finish (a very long one is capped by the replay). */
const END_SCREEN_MAX_WAIT_MS = 20000;

const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.mangolaUi;
const store = useMangolaStore();
const router = useRouter();
const game = findGame('mangola')!;
useMangolaBootstrap(props.gameId, game);

// Names are user input: only ever rendered as text. A player without a typed name is "Player N" (from 1), a computer seat "Bot N" (the server sends neither).
// A computer seat also says its level: "Bot 1 (Expert)".
const displayName = (seat: number): string =>
  seatNameWithBotLevel(messages, 'options.mangola.level', ui.botWithLevel, store.game?.players ?? [], seat, store.botLevels,
    { player: ui.playerLabel, bot: messages.lobby.botName });

const { online } = useConnectionStatus();
watch(online, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) void store.hydrate();
});

// Each state the store receives goes through the animation, which says what the board shows right now.
const { speed, cycle: cycleSpeed } = useAnimationSpeed();
const animation = useSowingAnimation({ speed: () => speed.value, mySeat: () => store.mySeat });
watch(() => store.game, state => animation.push(state), { immediate: true, flush: 'sync' });
const animating = computed(() => animation.animating.value);

const rows = computed(() => rowsWithBoard(store.rows, animation.displayBoard.value));
// Only the opponent's move is marked: I just played mine.
const marks = computed(() =>
  animation.shownState.value?.lastMove?.seat === store.mySeat ? NO_BOARD_MARKS : animation.marks.value);
const announcement = computed(() => {
  const announced = animation.announced.value;
  return announced ? moveSentence(announced.move, displayName(announced.move.seat), ui) : '';
});

// What each side holds as drawn right now (a capture is not counted before the hand takes it).
const seedsOf = (mine: boolean): number | null => {
  const cells = rows.value.flat().filter(c => c.mine === mine);
  return cells.length === 0 || cells.some(c => c.count === null) ? null : cells.reduce((sum, c) => sum + (c.count ?? 0), 0);
};
const mySeeds = computed(() => seedsOf(true));
const opponentSeeds = computed(() => seedsOf(false));
const seedsText = (seeds: number | null) => (seeds === null ? '' : plural(ui.table.seeds, seeds));

const placementError = computed(() => (store.placementError === STUCK_START_KEY ? ui.errors.stuckStart : null));
const boardDisabled = computed(() => !online.value || store.phase !== 'playing');

// A computer opponent chooses after a delay of its own; the game view does not say which seat is a computer, so the
// store remembers it from the lobby. Silent while the move plays and for a human, whose "Waiting for" line says it.
const thinkingText = computed(() => {
  if (store.phase !== 'playing' || store.isMyTurn || animating.value) return '';
  const current = store.game?.players.find(p => p.current);
  return current && store.botSeats.includes(current.seat) ? format(ui.table.thinking, { player: displayName(current.seat) }) : '';
});

const phaseHint = computed(() => {
  if (store.phase !== 'playing') return '';
  if (store.isMyTurn) return ui.table.hintSow;
  const current = store.game?.players.find(p => p.current);
  return current ? format(ui.table.waitingFor, { player: displayName(current.seat) }) : '';
});

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
  useGameDuration(() => store.phase === 'placement' || store.phase === 'playing', () => store.phase === 'finished');
onBeforeUnmount(() => cancelGameDuration());

// Leaving an in-progress game forfeits (the opponent wins the seat), like the other tables.
useLeaveGuard({
  isInProgress: () => store.phase === 'placement' || store.phase === 'playing',
  forfeit: () => store.forfeit(),
});

// The end screen waits for the last sowing to be shown; a game found already over (a reload) shows it at once.
const endScreen = useEndScreen(() => store.phase === 'finished', () => animating.value, undefined, END_SCREEN_MAX_WAIT_MS);
endScreen.revealImmediatelyIfOver();
onBeforeUnmount(() => endScreen.cancel());

const endSubtitle = computed(() => {
  const outcome = store.game?.outcome;
  if (outcome?.status !== 'FINISHED') return '';
  if (outcome.winner === null) return outcome.reason === 'REPETITION' ? ui.end.drawRepetition : ui.end.drawEndless;
  const loserSeat = outcome.winner === store.mySeat ? store.opponent?.seat ?? 0 : store.mySeat;
  if (outcome.reason === 'NO_MOVE') {
    return store.iWon ? format(ui.end.noMoveWon, { player: displayName(loserSeat) }) : ui.end.noMoveLost;
  }
  if (outcome.reason === 'CANNOT_MOVE_AGAIN') {
    return store.iWon ? format(ui.end.blockedWon, { player: displayName(loserSeat) }) : ui.end.blockedLost;
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
  <div class="mangola-screen" :class="{ 'mangola-screen--lobby': store.phase === 'lobby' }">
    <GameLobby
      v-if="store.phase === 'lobby' && store.lobby"
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
        <MangolaLobbyOptions
          :descriptors="descriptors"
          :options="options"
          :editable="editable"
          :privacy="privacy"
          @set-option="setOption" />
      </template>
    </GameLobby>

    <EndGameOverlay
      v-else-if="store.phase === 'finished' && endScreen.showEndOverlay.value"
      data-test="end"
      :did-i-win="store.iWon"
      :result="store.isDraw ? 'draw' : undefined"
      :winner-line="winnerLine"
      :subtitle="endSubtitle"
      :rematch-button="rematchButton"
      :error="store.rematch === 'failed' ? ui.end.rematchFailed : undefined"
      @play-again="store.playAgain()"
    />

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral felt screen, not the
         table, so the game UI never flashes before the lobby. -->
    <div v-else-if="store.phase === 'connecting'" class="connecting" data-test="connecting"
         role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else>
      <RulesPanel :rules="messages.mangola" />

      <div class="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <span class="game-name" data-test="game-name">{{ messages.gamePicker.games.mangola?.name }}</span>
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

      <div class="table">
        <MangolaPlacementEditor
          v-if="store.phase === 'placement'"
          :rows="store.rows"
          :side="store.mySide"
          :draft="store.draft"
          :seeds-left="store.seedsLeft"
          :can-confirm="store.canConfirm"
          :ready="store.myReady"
          :opponent-ready="store.opponentReady"
          :opponent-name="store.opponent ? displayName(store.opponent.seat) : null"
          :draft-only-endless="store.draftOnlyEndless"
          :error="placementError"
          @add="index => store.addSeed(index)"
          @remove="index => store.removeSeed(index)"
          @preset="preset => store.applyPreset(preset)"
          @confirm="store.confirmLayout()"
          @edit="store.editLayout()" />

        <template v-else>
          <div v-if="store.opponent" class="tag" data-test="player-tag" :data-active="!store.isMyTurn && store.phase === 'playing'">
            <span class="tag__name" data-test="tag-name">{{ displayName(store.opponent.seat) }}</span>
            <span class="tag__seeds" data-test="tag-seeds">{{ seedsText(opponentSeeds) }}</span>
          </div>

          <MangolaBoard
            :rows="rows"
            :interactive="store.isMyTurn"
            :disabled="boardDisabled"
            :marks="marks"
            :announcement="announcement"
            :animating="animating"
            :hand="animation.hand.value"
            :active-pit="animation.activePit.value"
            :capturing="animation.capturing.value"
            :chosen-pit="animation.chosenPit.value"
            :speed="speed"
            @sow="pit => store.sow(pit)"
            @skip="animation.skip()" />

          <div v-if="store.me" class="tag" data-test="player-tag" :data-active="store.isMyTurn">
            <span class="tag__name" data-test="tag-name">{{ displayName(store.me.seat) }}</span>
            <span class="tag__seeds" data-test="tag-seeds">{{ seedsText(mySeeds) }}</span>
          </div>

          <!-- Under the plate, not in the board's legend line: it never touches "Your side". -->
          <div class="speed-row">
            <AnimationSpeedButton :speed="speed" :names="ui.board.speeds" :label="ui.board.speed" @cycle="cycleSpeed()" />
          </div>

          <!-- One reserved place for both cues, so that neither moves the board: "Your turn", or "Bob is thinking…". -->
          <div class="turn-slot">
            <YourTurnIndicator :active="store.isMyTurn && !animating" />
            <p class="thinking" data-test="thinking" role="status" aria-live="polite">{{ thinkingText }}</p>
          </div>
          <p v-if="store.phase === 'playing'" class="phase-hint" data-test="phase-hint">{{ phaseHint }}</p>
          <p v-if="store.repeatedTwice" class="repetition-hint" data-test="repetition-hint" role="status">
            {{ ui.table.repetition }}
          </p>
        </template>
      </div>

      <div class="table-footer" data-test="table-footer">
        <RouterLink :to="{ name: 'games' }" class="leave-button" data-test="leave">
          <Button severity="secondary" :label="ui.back" icon="pi pi-undo" variant="text" rounded />
        </RouterLink>
      </div>

      <DisconnectOverlay
        v-if="disconnectedSeat !== null && (store.phase === 'playing' || store.phase === 'placement')"
        :opponent-label="displayName(disconnectedSeat)"
        :seconds-remaining="secondsRemaining" />
    </template>
  </div>
</template>

<style scoped>
/* One viewport tall: a top bar, the table in what is left and the Back row. The placement editor is taller than a
   phone and scrolls inside the screen; the board (8 columns) never does. The side gutter is 4 px, so that the eight
   pits of the board are at least 44 px wide from 360 px. */
.mangola-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) 4px calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow-x: hidden;
  overflow-y: auto;
  /* Paints its own felt, like the other tables. */
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, var(--felt-center) 0%, var(--felt-mid) 48%, var(--felt-edge) 100%);
}
/* The lobby is a title-style screen: it sits on the shared felt background instead. */
.mangola-screen--lobby {
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

/* The same top row as the other tables: the duration chip at the left, the rules toggle pinned at the right. */
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
.game-name {
  flex: none;
  padding: 0 var(--space-2);
  font-family: var(--font-title);
  font-size: 0.85rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  white-space: nowrap;
}

.table {
  flex: 1 0 auto;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: center;
  gap: var(--space-2);
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
}

.tag {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.78);
}
.tag[data-active='true'] { color: #fff; }
.tag__name { font-weight: 700; overflow-wrap: anywhere; }
.tag__seeds { font-size: 0.8rem; opacity: 0.8; }

.speed-row { flex: none; display: flex; justify-content: center; margin-top: var(--space-2); }
.turn-slot { flex: none; display: grid; min-height: 1.2em; }
.turn-slot > * { grid-area: 1 / 1; }
.thinking {
  margin: 0;
  text-align: center;
  font-size: 0.82rem;
  letter-spacing: 0.04em;
  color: rgba(255, 255, 255, 0.78);
  overflow-wrap: anywhere;
}
.phase-hint,
.repetition-hint {
  flex: none;
  margin: 0;
  text-align: center;
  font-size: 0.82rem;
  color: rgba(255, 255, 255, 0.78);
  overflow-wrap: anywhere;
}
.repetition-hint { font-size: 0.78rem; color: #f5e29a; }

.table-footer {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.leave-button { text-decoration: none; }
/* A touch target of 44 px, like the board pits. */
.leave-button :deep(.p-button) { min-height: 44px; }

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

.offline-notice {
  flex: none;
  align-self: center;
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

@media (prefers-reduced-motion: reduce) {
  .forfeit-fade-enter-active,
  .forfeit-fade-leave-active { transition: none; }
}
</style>
