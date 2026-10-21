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
import { format, plural } from '../../locales/format';

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

const cells = computed(() => (store.game?.board ?? Array(store.geometry.points.length).fill(null))
  .map((owner: number | null) => (owner === null ? null : colourOf(owner))));
// What a click is accepted on: my free points or movable pieces, then the destinations of the selection, or the pieces
// I may take after a mill. The same points are drawn as playable: the server decides, the board only shows.
const playable = computed(() => Array.from({ length: store.geometry.points.length }, (_, n) =>
  store.selectablePoints.includes(n) || store.targetPoints.includes(n)));

// Six Men's Morris. During my removal the pieces I may take get a ring instead of the playable mark, and the
// opponent's pieces left out of the server's list are the ones a mill protects (the list holds them all when every
// piece is in a mill, so then none is shielded). While the opponent takes a piece, the mill it closed stays lit.
const isSix = computed(() => store.variant === 'six');
const removable = computed(() => (store.iMustRemove ? store.selectablePoints : []));
const protectedPoints = computed(() => {
  if (removable.value.length === 0) return [];
  return (store.game?.board ?? []).flatMap((owner: number | null, n: number) =>
    owner !== null && owner !== store.mySeat && !removable.value.includes(n) ? [n] : []);
});
const highlighted = computed(() => (store.iMustRemove ? playable.value.map(() => false) : playable.value));
const millPoints = computed(() => (store.removalPending ? store.game?.removal?.mill ?? [] : []));
const piecesOnBoard = (seat: number): number => store.game?.board.filter((owner: number | null) => owner === seat).length ?? 0;
const piecesLeft = (seat: number, inHand: number): number => piecesOnBoard(seat) + inHand;
const countsOf = (player: { seat: number; piecesToPlace: number; lost?: number }) => isSix.value
  ? { onBoard: piecesOnBoard(player.seat), lost: player.lost ?? 0, warn: piecesLeft(player.seat, player.piecesToPlace) === 3 }
  : {};
const removableCount = computed(() => removable.value.length);
const removalOpponentName = computed(() => (opponent.value ? displayName(opponent.value.seat) : ''));
const removerName = computed(() => {
  const seat = store.game?.removal?.seat;
  return seat === undefined ? '' : displayName(seat);
});
// "One more loss and Bob loses." for each player down to three pieces; "N moves without a capture before a draw".
const lastPiecesNames = computed(() => {
  if (!isSix.value || store.phase !== 'playing') return [];
  return [opponent.value, me.value].flatMap(p => (p && piecesLeft(p.seat, p.piecesToPlace) === 3 ? [displayName(p.seat)] : []));
});
const quietMovesLeft = computed<number | null>(() => {
  const g = store.game;
  if (!isSix.value || store.phase !== 'playing' || g?.quietPlies === undefined || g.quietPlyLimit === undefined) return null;
  return Math.max(g.quietPlyLimit - g.quietPlies, 0);
});

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
  const names = isSix.value ? messages.morrisBoard.sixPointNames : messages.morrisBoard.pointNames;
  const removal = store.lastRemoval;
  if (removal) return format(ui.table.removed, { player: displayName(removal.seat), point: names[removal.point] });
  const move = store.lastMove;
  if (!move) return '';
  const closed = move.mill && move.mill.length > 0 ? ` ${format(ui.table.closedMill, { player: displayName(move.seat) })}` : '';
  if (move.action === 'PLACE' && move.point !== null) {
    return format(ui.table.placed, { player: displayName(move.seat), point: names[move.point] }) + closed;
  }
  if (move.action === 'MOVE' && move.from !== null && move.to !== null) {
    return format(ui.table.moved, { player: displayName(move.seat), from: names[move.from], to: names[move.to] }) + closed;
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

// A game won by a line, or by taking a piece down to two (Six), keeps the table, with the line or the capture drawn, for
// a moment before the end screen. A game found already over (a reload) shows the end screen at once.
const holdingLine = ref(false);
let holdTimer: ReturnType<typeof setTimeout> | null = null;
function stopHold() {
  if (holdTimer !== null) { clearTimeout(holdTimer); holdTimer = null; }
  holdingLine.value = false;
}
watch(() => store.phase, (phase) => {
  stopHold();
  const outcome = store.game?.outcome;
  const reduced = outcome?.status === 'FINISHED' && outcome.reason === 'REDUCED';
  if (phase !== 'finished' || ((store.game?.winningLine.length ?? 0) === 0 && !reduced)) return;
  holdingLine.value = true;
  holdTimer = setTimeout(stopHold, WINNING_LINE_HOLD_MS);
});
const endScreen = useEndScreen(() => store.phase === 'finished', () => holdingLine.value, undefined, WINNING_LINE_HOLD_MS + 3000);
endScreen.revealImmediatelyIfOver();
onBeforeUnmount(() => { stopHold(); endScreen.cancel(); });

const endSubtitle = computed(() => {
  const outcome = store.game?.outcome;
  if (outcome?.status !== 'FINISHED') return '';
  if (outcome.winner === null) {
    if (outcome.reason === 'DRAW_REPETITION') return ui.draw.byRepetition;
    return outcome.reason === 'DRAW_MOVE_LIMIT' ? ui.draw.byMoveLimit : ui.draw.agreed;
  }
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
  if (outcome.reason === 'REDUCED') {
    return store.iWon ? format(ui.end.reducedWon, { player: displayName(loserSeat) }) : format(ui.end.reducedLost, { player: winner });
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

      <div class="table">
        <MorrisPlayerTag
          v-if="opponent"
          class="tag-slot"
          :name="displayName(opponent.seat)"
          :colour="colourOf(opponent.seat)"
          :pieces-to-place="opponent.piecesToPlace"
          :marked="store.geometry.refined"
          v-bind="countsOf(opponent)"
          :active="opponent.current && store.phase === 'playing'" />

        <!-- The board takes what the fixed rows leave (see .board-slot): nothing that comes and goes resizes it. -->
        <div class="board-slot">
          <MorrisBoard
            :cells="cells"
            :geometry="store.geometry"
            :last-move="store.opponentLastMove"
            :selectable="playable"
            :highlighted="highlighted"
            :selected="store.selected"
            :winning-line="store.game?.winningLine ?? []"
            :mill="millPoints"
            :removable="removable"
            :protected-points="protectedPoints"
            :captured="store.capturedPoint"
            :disabled="boardDisabled"
            :interactive="store.isMyTurn"
            :announcement="announcement"
            @select="point => store.pointClicked(point)"
            @clear="store.clearSelection()" />
        </div>

        <MorrisPlayerTag
          v-if="me"
          class="tag-slot"
          :name="displayName(me.seat)"
          :colour="colourOf(me.seat)"
          :pieces-to-place="me.piecesToPlace"
          :marked="store.geometry.refined"
          v-bind="countsOf(me)"
          :active="store.isMyTurn" />

        <!-- The turn cue and the notes are one block of fixed height, whatever it says (the banner, the hint, the
             warnings). A draw offer for me takes its place while it lasts; the texts it covers stay in the page, hidden. -->
        <div class="lower" :class="{ 'lower--offer': showDrawBar && store.incomingDrawOffer }">
          <div class="lower-text">
            <YourTurnIndicator class="turn-slot" :active="store.isMyTurn" />
            <div class="notes" data-test="notes">
              <div
                v-if="store.removalPending && store.phase === 'playing'"
                class="removal-banner"
                :class="{ 'removal-banner--info': !store.iMustRemove }"
                data-test="removal-banner"
                role="status">
                <svg class="removal-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" aria-hidden="true" focusable="false">
                  <circle cx="12" cy="12" r="8" stroke-dasharray="3 3" />
                  <path d="M12 2v4M12 18v4M2 12h4M18 12h4" />
                </svg>
                <div class="removal-text">
                  <template v-if="store.iMustRemove">
                    <div class="removal-title" data-test="removal-title">{{ format(ui.table.removeTitleMine, { player: removalOpponentName }) }}</div>
                    <div class="removal-sub">
                      <span data-test="removal-can-take">{{ plural(ui.table.removeCanTake, removableCount) }}</span>
                      <span v-if="protectedPoints.length > 0" data-test="removal-protected">{{ format(ui.table.removeProtected, { player: removalOpponentName }) }}</span>
                    </div>
                  </template>
                  <template v-else>
                    <div class="removal-title" data-test="removal-title">{{ format(ui.table.removeTitleTheirs, { player: removerName }) }}</div>
                    <div class="removal-sub" data-test="removal-sub">{{ ui.table.removeSubTheirs }}</div>
                  </template>
                </div>
              </div>
              <p v-if="store.phase === 'playing' && !store.removalPending" class="phase-hint" data-test="phase-hint">{{ phaseHint }}</p>
              <p v-if="noPieceDownYet && store.phase === 'playing'" class="colour-hint" data-test="colour-hint">
                {{ format(ui.table.youPlay, { colour: store.myColour === 'WHITE' ? ui.white : ui.black }) }}
              </p>
              <p v-if="store.repeatedTwice" class="repetition-hint" data-test="repetition-hint" role="status">
                {{ ui.draw.repetition }}
              </p>
              <p v-for="name in lastPiecesNames" :key="name" class="warning-hint" data-test="last-pieces" role="status">
                {{ format(ui.table.lastPieces, { player: name }) }}
              </p>
              <p v-if="quietMovesLeft !== null" class="warning-hint" data-test="quiet-warning" role="status">
                {{ plural(ui.table.quietMoves, quietMovesLeft) }}
              </p>
            </div>
          </div>
          <div v-if="showDrawBar && store.incomingDrawOffer" class="draw-incoming" data-test="draw-incoming"
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
        </div>
      </div>

      <!-- The draw button's row exists for the whole game (empty during the placement phase and once it is over). -->
      <div class="draw-bar" data-test="draw-bar">
        <button v-if="showDrawBar" type="button" class="draw-btn draw-btn--offer" data-test="draw-offer"
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

.repetition-hint,
.warning-hint {
  flex: none;
  margin: 0;
  text-align: center;
  font-size: 0.78rem;
  color: #f5e29a;
  overflow-wrap: anywhere;
}

/* After a mill (Six): the instruction under my tag, in a banner; the other player reads who is taking a piece. */
.removal-banner {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  box-sizing: border-box;
  max-width: 100%;
  padding: 8px 14px;
  border-radius: 14px;
  color: #ffffff;
  background: rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(245, 200, 66, 0.55);
}
.removal-banner--info { border-color: rgba(255, 255, 255, 0.22); }
.removal-icon { flex: none; width: 22px; height: 22px; color: var(--gold); }
.removal-text { min-width: 0; }
.removal-title { font-weight: 700; font-size: 0.9rem; overflow-wrap: anywhere; }
.removal-sub { margin-top: 2px; font-size: 0.78rem; color: rgba(255, 255, 255, 0.75); overflow-wrap: anywhere; }
.removal-sub span + span { margin-left: 0.35em; }

/* The table holds still: this screen has fixed rows (the tags, the turn cue and notes block, the draw button row, Back) and
   the board takes what they leave, so a text, a banner or a draw offer that comes and goes never moves or resizes it. */
.board-slot {
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* Without container units the board keeps a share of the viewport height. */
  --morris-board-vh: 40vh;
}
@supports (height: 1cqh) {
  .board-slot {
    container-type: size;
    --morris-board-vh: 100cqh;
  }
}

/* The turn cue and the notes under the tags: room for the worst case, a mill banner with the last-pieces, repetition and
   draw warnings (Six) or the hint and the repetition warning (the other variants). */
.lower {
  --notes-h: 9.5rem;
  position: relative;
  flex: none;
  width: 100%;
  height: calc(1.2em + var(--space-2) + var(--notes-h));
}
.lower-text { display: flex; flex-direction: column; gap: var(--space-2); height: 100%; }
.notes {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  height: var(--notes-h);
}
/* A draw offer for me takes the place of the block (its texts stay in the page, hidden, until it is answered). */
.lower--offer .lower-text { visibility: hidden; }
.lower--offer .draw-incoming { position: absolute; left: 0; right: 0; bottom: 0; }

/* The draw button's row, under the table, above Back (the validated "Table A" layout). */
.draw-bar {
  flex: none;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 44px;
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

@media (prefers-reduced-motion: reduce) {
  .forfeit-fade-enter-active,
  .forfeit-fade-leave-active { transition: none; }
}
</style>
