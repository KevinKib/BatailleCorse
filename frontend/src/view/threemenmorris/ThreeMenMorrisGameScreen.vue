<script setup lang="ts">
import { computed, onBeforeUnmount, ref, toRef, watch } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useThreeMenMorrisStore } from '../../state/ThreeMenMorris.store';
import { useMorrisBootstrap } from '../../composables/useMorrisBootstrap';
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
import MorrisBoard from '../../components/morris/MorrisBoard.vue';
import MorrisPlayerTag from '../../components/morris/MorrisPlayerTag.vue';
import { findGame } from '../../model/games';
import MorrisLobbyOptions from '../../components/morris/MorrisLobbyOptions.vue';
import { WINNING_LINE_HOLD_MS } from '../../model/threemenmorris/board';
import { rematchButtonFor } from '../../model/RematchButton';
import { format } from '../../locales/format';

const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.threeMenMorrisUi;
const store = useThreeMenMorrisStore();
const router = useRouter();
const game = findGame('three-men-morris')!;
useMorrisBootstrap(props.gameId, game);

// Names are user input: only ever rendered as text. A player without a typed name is "Player N" (from 1).
const displayName = (seat: number): string =>
  store.game?.players.find(p => p.seat === seat)?.name?.trim() || format(ui.playerLabel, { n: seat + 1 });
const colourOf = (seat: number): 'white' | 'black' => (store.colourOf(seat) === 'WHITE' ? 'white' : 'black');

const me = computed(() => store.game?.players.find(p => p.seat === store.mySeat));
const opponent = computed(() => store.game?.players.find(p => p.seat !== store.mySeat));

const cells = computed(() => (store.game?.board ?? Array(9).fill(null))
  .map((owner: number | null) => (owner === null ? null : colourOf(owner))));
// What a click is accepted on: my free points or movable pieces, then the destinations of the selection.
// The same points are drawn as playable: the server decides, the board only shows.
const playable = computed(() => Array.from({ length: store.geometry.points.length }, (_, n) =>
  store.selectablePoints.includes(n) || store.targetPoints.includes(n)));

const { online } = useConnectionStatus();
watch(online, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) void store.hydrate();
});
const boardDisabled = computed(() => !online.value || store.phase !== 'playing');

// Rules and name of the variant being played; Tapatan's rules are the original panel.
const variantName = computed(() => messages.morrisLobby.variants[store.variant].name);
const rules = computed(() =>
  store.variant === 'tapatan' ? messages.threeMenMorris : messages.morrisVariantRules[store.variant]);

const noPieceDownYet = computed(() => store.game?.board.every(owner => owner === null) ?? false);

// The draw bar is part of the movement phase only; it shows the offer I may answer and my own offer button.
const showDrawBar = computed(() => store.phase === 'playing' && store.gamePhase === 'MOVEMENT');
const drawOffererName = computed(() =>
  store.drawOfferedBy === null ? '' : displayName(store.drawOfferedBy));

// What the opponent (or I) just did, for the live region. A reload has no last move: nothing to say.
const announcement = computed(() => {
  const notice = store.drawNotice;
  if (notice) {
    return format(notice.kind === 'offered' ? ui.draw.offeredBy : ui.draw.declinedBy, { player: displayName(notice.seat) });
  }
  const move = store.lastMove;
  if (!move) return '';
  const names = messages.morrisBoard.pointNames;
  if (move.action === 'PLACE' && move.point !== null) {
    return format(ui.table.placed, { player: displayName(move.seat), point: names[move.point] });
  }
  if (move.action === 'MOVE' && move.from !== null && move.to !== null) {
    return format(ui.table.moved, { player: displayName(move.seat), from: names[move.from], to: names[move.to] });
  }
  return '';
});

const phaseHint = computed(() => {
  if (!store.isMyTurn) {
    const current = store.game?.players.find(p => p.current);
    return current ? format(ui.table.waitingFor, { player: displayName(current.seat) }) : '';
  }
  return store.game?.availableActions.includes('MOVE') ? ui.table.hintMove : ui.table.hintPlace;
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
  useGameDuration(() => store.phase === 'playing', () => store.phase === 'finished');
onBeforeUnmount(() => cancelGameDuration());

// Leaving an in-progress game forfeits (the opponent wins the seat), like the other tables.
useLeaveGuard({
  isInProgress: () => store.phase === 'playing',
  forfeit: () => store.forfeit(),
});

// A game won by a line keeps the table, with the line drawn, for a moment before the end screen. A game found
// already over (a reload) shows the end screen at once.
const holdingLine = ref(false);
let holdTimer: ReturnType<typeof setTimeout> | null = null;
function stopHold() {
  if (holdTimer !== null) { clearTimeout(holdTimer); holdTimer = null; }
  holdingLine.value = false;
}
watch(() => store.phase, (phase) => {
  stopHold();
  if (phase !== 'finished' || (store.game?.winningLine.length ?? 0) === 0) return;
  holdingLine.value = true;
  holdTimer = setTimeout(stopHold, WINNING_LINE_HOLD_MS);
});
const endScreen = useEndScreen(() => store.phase === 'finished', () => holdingLine.value, undefined, WINNING_LINE_HOLD_MS + 3000);
endScreen.revealImmediatelyIfOver();
onBeforeUnmount(() => { stopHold(); endScreen.cancel(); });

const endSubtitle = computed(() => {
  const outcome = store.game?.outcome;
  if (outcome?.status !== 'FINISHED') return '';
  if (outcome.winner === null) return outcome.reason === 'DRAW_REPETITION' ? ui.draw.byRepetition : ui.draw.agreed;
  const winner = displayName(outcome.winner);
  const loserSeat = outcome.winner === store.mySeat ? opponent.value?.seat ?? 0 : store.mySeat;
  if (outcome.reason === 'LINE') {
    return store.iWon ? ui.end.youWonLine : format(ui.end.youLostLine, { player: winner });
  }
  if (outcome.reason === 'BLOCKED') {
    return store.iWon
      ? format(ui.end.blockedWon, { player: displayName(loserSeat) })
      : format(ui.end.blockedLost, { player: winner });
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
  <div class="morris-screen" :class="{ 'morris-screen--lobby': store.phase === 'lobby' }">
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
      @set-option="(key, value) => store.setOption(key, value)">
      <template #options="{ descriptors, options, editable, setOption }">
        <MorrisLobbyOptions
          :descriptors="descriptors"
          :options="options"
          :editable="editable"
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
      <RulesPanel :rules="rules" />

      <div class="top-bar">
        <GameTimer :time="formattedDuration" inline />
        <span class="variant-name" data-test="variant-name">{{ variantName }}</span>
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

      <div class="table" :class="{ 'table--draw-bar': showDrawBar, 'table--draw-incoming': store.incomingDrawOffer }">
        <MorrisPlayerTag
          v-if="opponent"
          class="tag-slot"
          :name="displayName(opponent.seat)"
          :colour="colourOf(opponent.seat)"
          :pieces-to-place="opponent.piecesToPlace"
          :active="opponent.current && store.phase === 'playing'" />

        <MorrisBoard
          :cells="cells"
          :geometry="store.geometry"
          :last-move="store.opponentLastMove"
          :selectable="playable"
          :highlighted="playable"
          :selected="store.selected"
          :winning-line="store.game?.winningLine ?? []"
          :disabled="boardDisabled"
          :interactive="store.isMyTurn"
          :announcement="announcement"
          @select="point => store.pointClicked(point)"
          @clear="store.clearSelection()" />

        <MorrisPlayerTag
          v-if="me"
          class="tag-slot"
          :name="displayName(me.seat)"
          :colour="colourOf(me.seat)"
          :pieces-to-place="me.piecesToPlace"
          :active="store.isMyTurn" />

        <YourTurnIndicator class="turn-slot" :active="store.isMyTurn" />
        <p v-if="store.phase === 'playing'" class="phase-hint" data-test="phase-hint">{{ phaseHint }}</p>
        <p v-if="noPieceDownYet && store.phase === 'playing'" class="colour-hint" data-test="colour-hint">
          {{ format(ui.table.youPlay, { colour: store.myColour === 'WHITE' ? ui.white : ui.black }) }}
        </p>
        <p v-if="store.repeatedTwice" class="repetition-hint" data-test="repetition-hint" role="status">
          {{ ui.draw.repetition }}
        </p>
      </div>

      <div v-if="showDrawBar" class="draw-bar" data-test="draw-bar">
        <div v-if="store.incomingDrawOffer" class="draw-incoming" data-test="draw-incoming"
             role="alertdialog" aria-labelledby="draw-incoming-title" aria-describedby="draw-incoming-hint">
          <p id="draw-incoming-title" class="draw-title">{{ format(ui.draw.incoming, { player: drawOffererName }) }}</p>
          <p id="draw-incoming-hint" class="draw-sub">{{ ui.draw.incomingHint }}</p>
          <div class="draw-answers">
            <button type="button" class="draw-btn draw-btn--accept" data-test="draw-accept" @click="store.acceptDraw()">
              {{ ui.draw.accept }}
            </button>
            <button type="button" class="draw-btn draw-btn--decline" data-test="draw-decline" @click="store.declineDraw()">
              {{ ui.draw.decline }}
            </button>
          </div>
        </div>
        <button type="button" class="draw-btn draw-btn--offer" data-test="draw-offer"
                :disabled="!store.canOfferDraw" @click="store.offerDraw()">
          {{ store.myDrawOfferPending ? ui.draw.offered : ui.draw.offer }}
        </button>
      </div>

      <div class="table-footer" data-test="table-footer">
        <RouterLink :to="{ name: 'games' }" class="leave-button" data-test="leave">
          <Button severity="secondary" :label="ui.back" icon="pi pi-undo" variant="text" rounded />
        </RouterLink>
      </div>

      <DisconnectOverlay
        v-if="disconnectedSeat !== null && store.phase === 'playing'"
        :opponent-label="displayName(disconnectedSeat)"
        :seconds-remaining="secondsRemaining" />
    </template>
  </div>
</template>

<style scoped>
/* The screen is exactly one viewport tall and never scrolls: a top bar, the table centred in what is left, and
   the Back row. The board scales with the viewport (see MorrisBoard), the rest is a few lines of text. */
.morris-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
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
.morris-screen--lobby {
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

.table {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
}
.variant-name {
  flex: none;
  padding: 0 var(--space-2);
  font-family: var(--font-title);
  font-size: 0.85rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  white-space: nowrap;
}
.tag-slot { flex: none; }
.turn-slot { flex: none; min-height: 1.2em; }
.phase-hint,
.colour-hint {
  flex: none;
  margin: 0;
  text-align: center;
  font-size: 0.82rem;
  color: rgba(255, 255, 255, 0.78);
  overflow-wrap: anywhere;
}
.colour-hint { font-size: 0.75rem; color: rgba(255, 255, 255, 0.6); }

.repetition-hint {
  flex: none;
  margin: 0;
  text-align: center;
  font-size: 0.78rem;
  color: #f5e29a;
  overflow-wrap: anywhere;
}

/* The draw bar sits under the table, above Back (the validated "Table A" layout). The board gives it room. */
.table--draw-bar { --morris-board-vh: 46vh; }
.table--draw-incoming { --morris-board-vh: 36vh; }
.table--draw-incoming .turn-slot { display: none; }
.draw-bar {
  flex: none;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  width: 100%;
  max-width: 560px;
  margin: 0 auto;
  box-sizing: border-box;
}
.draw-incoming {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--space-2);
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.18);
}
.draw-title { margin: 0; text-align: center; font-size: 0.95rem; font-weight: 700; overflow-wrap: anywhere; }
.draw-sub { margin: 0 0 4px; text-align: center; font-size: 0.78rem; color: rgba(255, 255, 255, 0.78); }
.draw-answers { display: flex; gap: var(--space-2); }
.draw-btn {
  flex: 1 1 0;
  min-height: 44px;
  padding: 0 var(--space-2);
  border-radius: 22px;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  cursor: pointer;
}
.draw-btn:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.draw-btn--accept { border: 0; background: #4ade80; color: #052e16; }
.draw-btn--decline {
  border: 1px solid rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.1);
  color: #f4f1e6;
}
.draw-btn--offer {
  border: 1px solid rgba(245, 200, 66, 0.55);
  background: rgba(245, 200, 66, 0.1);
  color: #f5e29a;
}
.draw-btn--offer:disabled { opacity: 0.55; cursor: default; }

.table-footer {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.leave-button { text-decoration: none; }
/* A touch target of 44 px, like the board points. */
.leave-button :deep(.p-button) { min-height: 44px; }
/* A touch target of 44 px, like the board points. */
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
