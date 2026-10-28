<template>
  <!-- One root for every phase: the route fade then follows the room, and switching from the lobby to the
       table is a plain re-render instead of a cross-fade of two screens on top of each other. -->
  <div class="bc-room">
  <div v-if="phase === 'lobby' && lobby" class="room-screen">
    <GameLobby
      :game="game"
      :lobby="lobby"
      :error="lobbyError"
      :starting="starting"
      @start="batailleCorseStore.startGame()"
      @leave="leaveLobby"
      @add-bot="batailleCorseStore.addBot()"
      @remove-bot="seat => batailleCorseStore.removeBot(seat)"
      @remove-seat="seat => batailleCorseStore.removeSeat(seat)"
      @set-option="(key, value) => batailleCorseStore.setOption(key, value)"
      @set-seat-option="(seat, key, value) => batailleCorseStore.setSeatOption(seat, key, value)"
      @rename="name => batailleCorseStore.rename(name)"
      @set-private="value => batailleCorseStore.setPrivate(value)" />
  </div>

  <!-- First server answer not here yet (e.g. arriving through an invite link): a neutral felt screen, not the
       table, so the game UI never flashes before the lobby. -->
  <div v-else-if="phase === 'connecting'" class="room-screen connecting" data-test="connecting"
       role="status" aria-live="polite">
    <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
    <span>{{ ui.connecting }}</span>
  </div>

  <template v-else>
  <div class="gamescreen flex">

    <RulesPanel :rules="messages.rules" />

    <div class="top-bar">
      <GameTimer :time="formattedDuration" inline />
      <Button
        data-test="sound-toggle"
        class="sound-toggle"
        severity="secondary"
        variant="text"
        rounded
        :icon="soundEnabled ? 'pi pi-volume-up' : 'pi pi-volume-off'"
        :aria-label="soundEnabled ? ui.sound.mute : ui.sound.unmute"
        :aria-pressed="soundEnabled"
        @click="toggleSound" />
    </div>

    <div v-if="!online" class="offline-notice" data-test="offline" role="alert">
      <i class="pi pi-wifi" aria-hidden="true"></i>
      {{ ui.offline }}
    </div>

    <HotkeySettings class="gamescreen_hotkeys" :settings="hotkeySettings" :labels="messages.hotkeys.actions" />

    <div class="gamescreen_top flex">
      <div class="left_side"></div>
      <div class="middle_side">
        <h1 :class="['player_tag', { 'player_tag--active': showOpponentTurn }]" :title="opponentLabel">{{ opponentLabel }}</h1>
        <div :class="['card', 'stacked', { 'opponent-deck--disconnected': opponentDisconnected }]" data-test="opponent-deck">
          <PlayingCard
            ref="opponentCard"
            :size="90"
            :hidden="true"
            rank="10"
            suit="spade"
          />
          <div class="card_counter" data-cy="opponent-card-count">
            <CardCounter :count="batailleCorse?.players.at(opponentIndex)?.nbCards"/>
          </div>
          <SeatDisconnectBadge
            v-if="opponentDisconnected"
            class="seat-disconnect"
            :seconds-remaining="secondsRemaining" />
        </div>
      </div>
      <div class="right_side"></div>
      
    </div>

    <div class="gamescreen_middle flex">
      <div :class="['pile_slot', { 'pile-flash': isPileFlashing, 'pile_slot--empty': pileIsEmpty }]">
        <div :class="['card', { 'card-punch': slapImpact === 'success', 'card-shake': slapImpact === 'error' }]" ref="centerPileArea">
          <PlayingCard
            ref="centerPile"
            :size="125"
            :hidden="false"
            :suit="isPileAnimating ? frozenPileCard.suit : batailleCorse?.pile.cards.at(0)?.suit"
            :rank="isPileAnimating ? frozenPileCard.rank : batailleCorse?.pile.cards.at(0)?.rank"
          />
          <div class="card_counter" data-cy="pile-card-count">
            <CardCounter :count="batailleCorse?.pile.cards.length"/>
          </div>
        </div>
      </div>
    </div>

    <div class="gamescreen_bottom flex">
      <div class="left_side">
        <RouterLink to="/" class="back_button">
          <Button severity="secondary" :label="ui.back" icon="pi pi-undo" variant="text" rounded />
        </RouterLink>
      </div>

      <div class="middle_side">
        <div class="player_tag_wrap">
          <YourTurnIndicator class="turn-slot" :active="showFirstTurnHint" />
          <h1 :class="['player_tag', { 'player_tag--active': showMyTurn }]" :title="myLabel">{{ myLabel }}</h1>
        </div>
        <div class="card stacked">
          <PlayingCard
            ref="pile"
            :size="90"
            :hidden="true"
            rank="10"
            suit="spade"
          />
          <div class="card_counter" data-cy="player-card-count">
            <CardCounter :count="batailleCorse?.players.at(myPlayerIndex)?.nbCards"/>
          </div>
        </div>
        <div class="action_buttons">
          <Button class="action_button" icon="pi pi-arrow-up" severity="success" :label="ui.send" rounded
            @click="send()" :disabled="isButtonDisabled(myPlayerIndex, 'send')"/>
          <Button class="action_button" icon="pi pi-hammer" severity="warn" :label="ui.slap" rounded
            @click="slap()" :disabled="isButtonDisabled(myPlayerIndex, 'slap')"/>
        </div>
      </div>

      <div class="right_side"></div>
    </div>

    <EndGameOverlay
      v-if="showEndOverlay"
      :did-i-win="didIWin"
      :subtitle="endSubtitle"
      :rematch-button="rematchButton"
      :error="rematch === 'failed' ? ui.rematchFailed : undefined"
      @play-again="onPlayAgain"
    />
  </div>

  <template v-for="(ghost, i) in slapGhosts" :key="i">
    <img
      v-if="ghost.visible"
      :src="ghost.src"
      :class="['slap-ghost', { transitioning: ghost.transitioning }]"
      :style="{
        transform: `translate(${ghost.x}px, ${ghost.y}px) scale(${ghost.scaleX}, ${ghost.scaleY})`,
        width: ghost.width + 'px',
        height: ghost.height + 'px',
        '--slap-duration': ghost.duration + 'ms',
      }"
    />
  </template>

  <img
    v-if="ghostCard.visible"
    :src="ghostCard.src"
    :class="['ghost-card', { transitioning: ghostCard.transitioning }]"
    :style="{
      transform: `translate(${ghostCard.x}px, ${ghostCard.y}px) scale(${ghostCard.scaleX}, ${ghostCard.scaleY})`,
      width: ghostCard.width + 'px',
      height: ghostCard.height + 'px',
      '--ghost-duration': ghostCard.duration + 'ms',
    }"
  />

  <div
    v-if="cardDeltaIndicator.visible"
    class="card-delta"
    :class="{ 'card-delta--negative': cardDeltaIndicator.delta < 0 }"
    :style="{ left: cardDeltaIndicator.x + 'px', top: cardDeltaIndicator.y + 'px' }"
  >
    {{ cardDeltaIndicator.delta > 0 ? '+' : '' }}{{ cardDeltaIndicator.delta }}
  </div>
  </template>
  </div>

</template>

<script setup lang="ts">
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import HotkeySettings from '../../components/HotkeySettings.vue';
import GameTimer from '../../components/GameTimer.vue';
import YourTurnIndicator from '../../components/YourTurnIndicator.vue';
import GameLobby from '../../components/lobby/GameLobby.vue';
import SeatDisconnectBadge from '../../components/SeatDisconnectBadge.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import { rematchButtonFor } from '../../model/RematchButton';
import { Button } from 'primevue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { useSettingsStore } from '../../state/Settings.store';
import { findGame } from '../../model/games';
import { optionLabel } from '../../model/lobby/optionLabel';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';
import { format } from '../../locales/format';
import type SessionSeat from '../../model/SessionSeat';
import { useGameAnimations } from '../../composables/useGameAnimations';
import { useHotkeys } from '../../composables/useHotkeys';
import { useHotkeySettings } from '../../composables/useHotkeySettings';
import { useEndScreen } from '../../composables/useEndScreen';
import { useGameDuration } from '../../composables/useGameDuration';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useRemovedRedirect } from '../../composables/useRemovedRedirect';
import { useTurnIndicator } from '../../composables/useTurnIndicator';
import { useBatailleCorseBootstrap } from '../../composables/useBatailleCorseBootstrap';
import { useI18n } from '../../composables/useI18n';
import { Action } from '../../model/Action';
import { computed, onBeforeUnmount, toRef, useTemplateRef, watch } from 'vue';
import { useSoundPreference } from '../../composables/useSoundPreference';
import { useConnectionStatus } from '../../composables/useConnectionStatus';
import { cardSound } from '../../composables/useCardSound';
import { endGameMessage } from '../../model/endGameMessage';

const messages = useI18n();
const ui = messages.batailleCorseUi;
const game = findGame('bataillecorse')!;
const route = useRoute();
const router = useRouter();
const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse, lobby, phase, lobbyError, starting, rematch, myPlayerIndex, seats, mySeatInfo,
        opponentSeat, liveDisconnections } = storeToRefs(batailleCorseStore);

const pile = useTemplateRef("pile");
const opponentCard = useTemplateRef("opponentCard");
const centerPile = useTemplateRef("centerPile");
const centerPileArea = useTemplateRef<HTMLDivElement>("centerPileArea");

const {
  ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard, cardDeltaIndicator,
  slapImpact, cancel: cancelAnimations,
} = useGameAnimations({
  playerDeckEl: () => pile.value?.rootCard,
  opponentDeckEl: () => opponentCard.value?.rootCard,
  centerPileEl: () => centerPile.value?.rootCard,
  centerPileAreaEl: () => centerPileArea.value,
});

// My own connection. While it is down Send and Slap are disabled (a publish would throw); once it is back,
// events may have been missed in between, so the state is fetched again.
const { online } = useConnectionStatus();
watch(online, (isOnline, wasOnline) => {
  if (isOnline && !wasOnline) void batailleCorseStore.hydrate();
});

function isButtonDisabled(playerIndex: number, action: Action) {
  return !online.value || !(batailleCorse.value?.canPlayerAct(playerIndex, action) ?? false);
}

// A card laid on the pile (mine or the opponent's) makes a sound, unless muted. The preference is stored
// once for the device and shared by every game.
const { enabled: soundEnabled, toggle: toggleSound } = useSoundPreference();
// The first state of the table (a load, a reload) is not a card being laid.
watch(() => batailleCorse.value?.pile.cards.length, (size, previous) => {
  if (size !== undefined && previous !== undefined && size > previous && soundEnabled.value) cardSound.play(1);
});

// The buttons and the shortcuts act for my own seat only.
function send() {
  batailleCorseStore.send();
}

function slap() {
  batailleCorseStore.slap();
}

const settingsStore = useSettingsStore();

const opponentIndex = computed(() => 1 - myPlayerIndex.value);

// How a seat is called at the table: a bot is "Bot N (tier)", a human the name typed in the lobby, else
// "Player N". Names are user input: only ever rendered as text.
const tierDescriptor: OptionDescriptor = {
  key: 'tier', type: 'CHOICE', values: [], defaultValue: '1', labelKey: 'options.bataillecorse.tier',
};
function seatLabel(seat: SessionSeat): string {
  if (seat.bot) {
    const botNumber = seats.value.filter(s => s.bot && s.id <= seat.id).length;
    const bot = format(ui.botLabel, { n: botNumber });
    const tier = seat.seatOptions?.tier;
    return tier === undefined ? bot : format(ui.botWithTier, { bot, tier: optionLabel(messages, tierDescriptor, tier, {}) });
  }
  return seat.name?.trim() || format(ui.playerLabel, { n: seat.id + 1 });
}
const opponentLabel = computed(() => (opponentSeat.value ? seatLabel(opponentSeat.value) : ui.opponent));
const myLabel = computed(() => mySeatInfo.value?.name?.trim() || settingsStore.playerName || ui.you);

const pileIsEmpty = computed(() => (batailleCorse.value?.pile.cards.length ?? 0) === 0);

const isGameOver = computed(() => batailleCorse.value?.isOver() ?? false);
const didIWin = computed(() => batailleCorse.value?.isWinnerAt(myPlayerIndex.value) ?? false);
const endSubtitle = computed(() =>
  endGameMessage(
    didIWin.value,
    opponentLabel.value,
    batailleCorse.value?.opponentForfeitReason(myPlayerIndex.value) ?? null,
    messages.endGame,
  ));

const rematchButton = computed(() =>
  rematchButtonFor(rematch.value, { playAgain: ui.playAgain, pending: ui.rematchPending }));

// Play again reopens the lobby (the bot and its tier come back); the screen follows the phase.
function onPlayAgain() {
  void batailleCorseStore.playAgain(mySeatInfo.value?.name ?? (settingsStore.playerName || undefined));
}

// The host removed me from the lobby: lead to the Game selection with a notice.
useRemovedRedirect(toRef(batailleCorseStore, 'removedByHost'), () => { batailleCorseStore.removedByHost = false; });

// Leaving frees the seat: the leaver goes to the Game selection like every other exit. A leave that could not
// be sent keeps the player in the lobby, with the error shown.
function leaveLobby() {
  // Replace, not push: Back must not return to a room the player no longer sits in.
  if (batailleCorseStore.leave()) router.replace({ name: 'games' });
}

// A game can end on ANY move — a winning grab/slap or a SEND that empties the
// sender's hand. The winner only lands in state via the post-move state-update,
// so the overlay is driven by isGameOver (state), gated on the pile animation
// settling, rather than wired into each action's watcher.
const { showEndOverlay, revealImmediatelyIfOver, cancel: cancelEndScreen } =
  useEndScreen(() => isGameOver.value, () => isPileAnimating.value);

useBatailleCorseBootstrap(route.params.id as string, revealImmediatelyIfOver);

// An in-progress game: state loaded, not yet over. Drives both the leave-confirmation guard and the
// cosmetic game-duration timer.
const isInProgress = computed(() => phase.value === 'playing');

// Cosmetic game-duration timer; the composable freezes the value at game over.
const { formattedDuration, cancel: cancelGameDuration } =
  useGameDuration(() => isInProgress.value, () => isGameOver.value);

// BC has a single opponent, so the disconnections map holds at most one entry.
// liveDisconnections already excludes my own seat via the store's presentSeats narrowing.
const { secondsRemainingFor, cancel: cancelDisconnectCountdown } = useSeatDisconnectCountdown({
  disconnections: () => liveDisconnections.value,
  isGameOver: () => isGameOver.value,
});

const disconnectedSeat = computed(() => {
  const seats = Object.keys(liveDisconnections.value);
  return seats.length ? Number(seats[0]) : null;
});
const opponentDisconnected = computed(() => disconnectedSeat.value !== null && !isGameOver.value);
const secondsRemaining = computed(() =>
  disconnectedSeat.value === null
    ? 0
    : secondsRemainingFor(liveDisconnections.value[disconnectedSeat.value]));

useLeaveGuard({
  isInProgress: () => isInProgress.value,
  forfeit: () => batailleCorseStore.forfeit(),
});

const { showMyTurn, showOpponentTurn, showFirstTurnHint, cancel: cancelTurnIndicator } = useTurnIndicator({
  state: () => batailleCorse.value,
  myPlayerIndex: () => myPlayerIndex.value,
  opponentIndex: () => opponentIndex.value,
  showEndOverlay: () => showEndOverlay.value,
});

const hotkeySettings = useHotkeySettings('bataillecorse');

useHotkeys(
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'send')) send(); },
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'slap')) slap(); },
  () => [settingsStore.sendKey],
  () => [settingsStore.slapKey],
);

onBeforeUnmount(() => {
  cancelAnimations();
  cancelEndScreen();
  cancelGameDuration();
  cancelDisconnectCountdown();
  cancelTurnIndicator();
});
</script>

<style scoped>

/* The lobby and the connecting screen sit on the shared felt background, full height. */
.room-screen {
  height: 100vh;
  height: 100dvh;
}
.connecting {
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

/* Bottom-right corner, mirroring Back: the top row (timer, opponent label, rules) has no room left
   at phone width. The panel opens upwards from it. */
.gamescreen_hotkeys {
  position: absolute;
  right: calc(10px + env(safe-area-inset-right, 0px));
  bottom: calc(10px + env(safe-area-inset-bottom, 0px));
  z-index: 1400;
  margin: 0;
}
.gamescreen_hotkeys :deep(.panel) {
  top: auto;
  left: auto;
  right: 8px;
  bottom: 56px;
}

.gamescreen {
  /* Two-layer background: vignette on top, deep felt below. Calmer than the
     title screens (no watermarks/drift) so the cards stay the focus; the felt
     grain is added statically via ::before below. */
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, var(--felt-center) 0%, var(--felt-mid) 48%, var(--felt-edge) 100%);
  /* One fluid sizing source: every card/pile/gap derives from these so the
     whole board scales coherently off the viewport. Clamp bounds are tuned
     here; deck:pile width ratio mirrors the original 90:125. */
  --deck-card-w: clamp(48px, 14vmin, 90px);
  --pile-card-w: clamp(70px, 19vmin, 125px);
  --card-aspect: 167.575 / 243.1375; /* matches PlayingCard intrinsic ratio */
  --pile-card-h: calc(var(--pile-card-w) * 243.1375 / 167.575); /* card height at current width */
  --band-pad: clamp(8px, 2.5vh, 20px);
  --stack-gap: clamp(6px, 1.5vh, 10px);
  width: 100%;
  height: 100vh;     /* fallback for browsers without dvh */
  height: 100dvh;    /* tracks the real visible area as mobile chrome shows/hides */
  display: flex;
  flex-direction: column;
  position: relative;
  /* Own stacking context so the felt grain (::before, z-index:-1) layers above
     the gradient background but stays behind every card and overlay. */
  isolation: isolate;
}

/* Static felt grain over the board — same fabric texture as the title screens
   but no drift, keeping the play area calm. */
.gamescreen::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: var(--felt-noise);
  background-size: 180px 180px;
  opacity: 0.06;
  mix-blend-mode: overlay;
  pointer-events: none;
}

/* CSS width/aspect-ratio override PlayingCard's px width/height attributes
   (CSS beats HTML attributes — no !important needed). PlayingCard is untouched,
   so shared consumers like TitleCardFan are unaffected. */
.gamescreen :deep(.playing_card) {
  height: auto;
  aspect-ratio: var(--card-aspect);
}

.gamescreen_top :deep(.playing_card),
.gamescreen_bottom :deep(.playing_card) {
  width: var(--deck-card-w);
}

.gamescreen_middle :deep(.playing_card) {
  width: var(--pile-card-w);
}

.gamescreen_top {
  /* Size to content; the pile row absorbs the slack. min-height:0 lets it
     shrink on short screens so the board still fits without scrolling. */
  flex: 0 0 auto;
  min-height: 0;
  /* Placemat: a soft gradient darker at the outer edge reads as the player's
     zone on the table rather than a flat tinted strip. */
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.30) 0%, rgba(0, 0, 0, 0.05) 100%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 -1px 0 rgba(255, 255, 255, 0.04);
  padding-bottom: var(--band-pad);

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;
    gap: var(--stack-gap);
    margin: 0;
  }
}

.gamescreen_middle {
  flex: 1 1 auto;
  min-height: 0;
}

.gamescreen_bottom {
  flex: 0 0 auto;
  min-height: 0;
  /* Placemat, mirrored: darkest at the bottom edge (the player's near side). */
  background: linear-gradient(to top, rgba(0, 0, 0, 0.30) 0%, rgba(0, 0, 0, 0.05) 100%);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  padding-top: var(--band-pad);
  transition: border-color 0.4s ease;
  /* Keep the Send/Slap buttons (and the Back button) off the bottom edge, the
     same clearance the Back button used, plus the phone safe-area inset. */
  padding-bottom: calc(var(--band-pad) + env(safe-area-inset-bottom, 0px));

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
    gap: var(--stack-gap);
    margin: 0;
  }
}

.pile_slot .card {
  /* Reserve the full card box even when the pile is empty (the img is
     v-show-hidden then), so the slot never shrinks with no card in it. */
  min-width: var(--pile-card-w);
  min-height: var(--pile-card-h);
}

/* Empty well gets a faint suit so it reads as an intentional card spot rather
   than a missing card. Sits behind the (hidden) card image. */
.pile_slot--empty::after {
  content: '♠';
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(var(--pile-card-w) * 0.62);
  line-height: 1;
  color: rgba(255, 255, 255, 0.05);
  pointer-events: none;
}

/* Slap juice: the central card reacts to the outcome of a slap. */
.card.card-punch { animation: card-punch 320ms ease-out; }
.card.card-shake { animation: card-shake 380ms ease-in-out; }

@keyframes card-punch {
  0%   { transform: scale(1); }
  35%  { transform: scale(1.09); }
  100% { transform: scale(1); }
}

@keyframes card-shake {
  0%, 100% { transform: translateX(0); }
  18%      { transform: translateX(-6px) rotate(-1.5deg); }
  38%      { transform: translateX(5px) rotate(1.2deg); }
  58%      { transform: translateX(-4px) rotate(-0.8deg); }
  78%      { transform: translateX(3px); }
}

.pile_slot {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  /* Size around the fluid pile card instead of fixed px so it never overflows. */
  padding: clamp(10px, 2.5vmin, 20px) clamp(14px, 3.5vmin, 28px);
  margin: auto;
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  /* Recessed felt well: inner gradient slightly darker than the felt plus a
     deep inset shadow, so it reads as a carved card spot, not a placeholder. */
  background: radial-gradient(ellipse at 50% 45%, rgba(0, 0, 0, 0.28) 0%, rgba(0, 0, 0, 0.5) 100%);
  box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35);
}


.card {
  position: relative;
  width: fit-content;
  margin-left: auto;
  margin-right: auto;
  margin-top: auto;
  margin-bottom: auto;
}

.card_counter {
  position: absolute;
  bottom: 0;
  left: 100%;
  margin-left: 4px;
}

.player_tag {
  width: fit-content;
  /* A long typed name is cut with an ellipsis instead of widening the tag. */
  max-width: 14rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  box-sizing: border-box;
  margin-left: auto;
  margin-right: auto;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: var(--space-1) 14px;
}

.action_buttons {
  width: fit-content;
  margin-left: auto;
  margin-right: auto;
}

.action_button {
  margin-left: 6px;
  margin-right: 6px;
}

.card.stacked {
  z-index: 1001;
}

.card.stacked::before,
.card.stacked::after {
  content: '';
  position: absolute;
  inset: 0;
  border: 1px solid rgba(0, 0, 0, 0.25);
  border-radius: 6%;
  background: #ede8df;
  z-index: -1;
}

.card.stacked::before {
  transform: translate(-3px, -3px);
  box-shadow: 1px 2px 6px rgba(0, 0, 0, 0.35);
}

.card.stacked::after {
  transform: translate(-6px, -6px);
  background: #e4dfd6;
  box-shadow: 1px 2px 6px rgba(0, 0, 0, 0.25);
}

.left_side {
  /* Side columns flex and may collapse; the center sizes to its content so the
     Send + Slap buttons can never be squeezed into wrapping or clipping. */
  flex: 1 1 0;
  min-width: 0;
  display: flex;
}

.middle_side {
  flex: 0 0 auto;
}

.right_side {
  flex: 1 1 0;
  min-width: 0;
}

.back_button {
  margin-left: 16px;
  margin-right: 16px;
  margin-top: auto;
}


/* The flash ring lives on a pseudo-element whose opacity is animated: animating box-shadow
   directly repaints the slot on every frame (measured: 3 to 10x the paint and GPU work during
   a pile grab), while opacity stays on the compositor. */
@keyframes pile-flash {
  0%   { opacity: 0; }
  35%  { opacity: 1; }
  100% { opacity: 0; }
}

.pile_slot.pile-flash::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  box-shadow: var(--turn-ring);
  pointer-events: none;
  animation: pile-flash 380ms ease-out;
}

.slap-ghost {
  position: fixed;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
  pointer-events: none;
  z-index: 1000;
  border: 1px solid black;
  border-radius: 6%;
  box-shadow: var(--card-shadow);
  transition: none;
}

.slap-ghost.transitioning {
  transition: transform var(--slap-duration, 280ms) ease-in-out;
}

.ghost-card {
  position: fixed;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
  pointer-events: none;
  z-index: 1000;
  border: 1px solid black;
  border-radius: 6%;
  box-shadow: var(--card-shadow);
  transition: none;
}

.ghost-card.transitioning {
  transition: transform var(--ghost-duration, 100ms) ease-in;
}

@keyframes card-delta-float {
  0%   { opacity: 0; transform: translateY(0); }
  12%  { opacity: 1; }
  70%  { opacity: 1; transform: translateY(-28px); }
  100% { opacity: 0; transform: translateY(-44px); }
}

.card-delta {
  position: fixed;
  pointer-events: none;
  z-index: 1001;
  font-size: 1.6rem;
  font-weight: 700;
  color: rgb(var(--accent-positive-rgb));
  font-variant-numeric: tabular-nums;
  animation: card-delta-float 1.4s ease-out forwards;
}

.card-delta--negative {
  color: rgb(var(--accent-negative-rgb));
}

/* Top row of the table, as on Bullshit: the duration chip, then the sound toggle, on one centre line (the
   rules toggle, pinned at the right, is centred on it too). Same height and side gutter as the chips. */
.top-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  box-sizing: border-box;
  height: calc(var(--hud-inset) * 2 + var(--hud-chip-h) + env(safe-area-inset-top, 0px));
  padding: env(safe-area-inset-top, 0px) 0 0 calc(var(--hud-inset) + env(safe-area-inset-left, 0px));
  /* Leaves the right corner free for the rules toggle. */
  padding-right: 120px;
}

/* A dropped opponent: the deck is dimmed and the reconnecting badge sits centred over it. */
.opponent-deck--disconnected :deep(.playing_card) {
  filter: grayscale(1) brightness(0.6);
}
.seat-disconnect {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 1;
}

/* Under the top bar; it only shows while the socket is down. */
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
  padding: 6px 18px; /* same pill as the Bullshit notice */
  text-align: center;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.5);
}

/* --- Turn indicator --- */
.player_tag--active {
  position: relative;
  padding-left: 20px;
  color: #ffffff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}

.player_tag--active::before {
  content: "";
  position: absolute;
  left: 8px;
  top: 50%;
  width: var(--turn-dot-size);
  height: var(--turn-dot-size);
  margin-top: calc(var(--turn-dot-size) / -2);
  border-radius: 50%;
  background: var(--gold);
}

/* Wrapper lets the one-time hint float above the name tag without shifting layout. */
.player_tag_wrap {
  position: relative;
  display: flex;
  justify-content: center;
}

/* Where the shared "Your turn" cue sits: just above the name tag, out of the flow. */
.turn-slot {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 6px;
}

@media (prefers-reduced-motion: reduce) {
  .card.card-punch,
  .card.card-shake { animation: none; }
}

/* --- Narrow-screen (phone) adjustments --- */
@media (max-width: 480px) {
  /* Slightly larger floors relative to width so cards stay legible on phones. */
  .gamescreen {
    --deck-card-w: clamp(44px, 18vw, 72px);
    --pile-card-w: clamp(64px, 26vw, 104px);
  }

  /* Tighten the action buttons so Send + Slap stay side-by-side and on-screen. */
  .action_button {
    margin-left: 4px;
    margin-right: 4px;
  }

  .action_buttons :deep(.p-button) {
    padding: 0.45rem 0.7rem;
    font-size: 0.85rem;
  }

  /* Take Back out of the bottom flow so it never competes with the action
     buttons for width; pin it to the safe bottom-left corner. */
  .back_button {
    position: absolute;
    left: 0;
    bottom: 0;
    margin: 8px;
    margin-left: calc(8px + env(safe-area-inset-left, 0px));
    margin-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
    z-index: 1500;
  }
}

</style>
