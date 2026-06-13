<template>
  <div class="gamescreen flex">

    <GameTimer :time="formattedDuration" />

    <RulesPanel />

    <div class="gamescreen_top flex">
      <div class="left_side"></div>
      <div class="middle_side">
        <h1 :class="['player_tag', { 'player_tag--active': showOpponentTurn }]">{{ opponentLabel }}</h1>
        <div class="card stacked">
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
        <RouterLink :to="{ name: 'home' }" class="back_button">
          <Button severity="secondary" label="Back" icon="pi pi-undo" variant="text" rounded />
        </RouterLink>
      </div>

      <div class="middle_side">
        <div class="player_tag_wrap">
          <Transition name="turn-fade">
            <div v-if="showFirstTurnHint" class="turn-hint" data-cy="turn-hint">
              <span class="turn-hint__dot"></span>{{ YOUR_TURN_LABEL }}
            </div>
          </Transition>
          <h1 :class="['player_tag', { 'player_tag--active': showMyTurn }]">{{ myName || settingsStore.playerName || 'You' }}</h1>
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
          <Button :class="['action_button', { 'action_button--my-turn': showMyTurn }]" icon="pi pi-arrow-up" severity="success" label="Send" rounded
            @click="send(myPlayerIndex)" :disabled="isButtonDisabled(myPlayerIndex, 'send')"/>
          <Button class="action_button" icon="pi pi-hammer" severity="warn" label="Slap" rounded
            @click="slap(myPlayerIndex)" :disabled="isButtonDisabled(myPlayerIndex, 'slap')"/>
        </div>
      </div>

      <div class="right_side"></div>
    </div>

    <WaitingOverlay v-if="isWaiting" />

    <DisconnectOverlay
      v-if="opponentDisconnected"
      :opponent-label="opponentLabel"
      :seconds-remaining="secondsRemaining"
    />

    <EndGameOverlay
      v-if="showEndOverlay"
      :did-i-win="didIWin"
      :subtitle="endSubtitle"
      :rematch-button="rematchButton"
      @play-again="onPlayAgain"
    />
  </div>

  <template v-for="(ghost, i) in slapGhosts" :key="i">
    <img
      v-if="ghost.visible"
      :src="ghost.src"
      :class="['slap-ghost', { transitioning: ghost.transitioning }]"
      :style="{
        transform: `translate(${ghost.x}px, ${ghost.y}px)`,
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
      transform: `translate(${ghostCard.x}px, ${ghostCard.y}px)`,
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

<script setup lang="ts">
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import GameTimer from '../../components/GameTimer.vue';
import WaitingOverlay from '../../components/WaitingOverlay.vue';
import DisconnectOverlay from '../../components/DisconnectOverlay.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import type { RematchButton } from '../../model/RematchButton';
import { Button } from 'primevue';
import { storeToRefs } from 'pinia';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { useSettingsStore } from '../../state/Settings.store';
import { DIFFICULTY } from '../../model/Difficulty';
import { useGameAnimations } from '../../composables/useGameAnimations';
import { useHotkeys } from '../../composables/useHotkeys';
import { useEndScreen } from '../../composables/useEndScreen';
import { useGameDuration } from '../../composables/useGameDuration';
import { useDisconnectCountdown } from '../../composables/useDisconnectCountdown';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useTurnIndicator } from '../../composables/useTurnIndicator';
import { useGameBootstrap } from '../../composables/useGameBootstrap';
import { Action } from '../../model/Action';
import { computed, onBeforeUnmount, useTemplateRef } from 'vue';
import { endGameMessage } from '../../model/endGameMessage';

const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse, mode, myPlayerIndex, waiting, myName, opponentName, opponentConnection,
        rematchState } = storeToRefs(batailleCorseStore);

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

function isButtonDisabled(playerIndex: number, buttonLabel: Action) {
  return !batailleCorse.value?.players.at(playerIndex)?.availableActions.includes(buttonLabel.toLocaleUpperCase());
}

function send(playerIndex: number) {
  batailleCorseStore.send(playerIndex);
}

function slap(playerIndex: number) {
  batailleCorseStore.slap(playerIndex);
}

const settingsStore = useSettingsStore();

const difficultyLabel = computed(() => DIFFICULTY[settingsStore.difficulty]?.name);

const opponentIndex = computed(() => 1 - myPlayerIndex.value);
const isSolo = computed(() => mode.value === 'solo');
const isWaiting = computed(() => waiting.value);
const opponentLabel = computed(() =>
  isSolo.value ? `Computer (${difficultyLabel.value})` : (opponentName.value ?? 'Opponent'));

const pileIsEmpty = computed(() => (batailleCorse.value?.pile.cards.length ?? 0) === 0);

const isGameOver = computed(() => batailleCorse.value?.isOver() ?? false);
const didIWin = computed(() => batailleCorse.value?.isWinnerAt(myPlayerIndex.value) ?? false);
const endSubtitle = computed(() =>
  endGameMessage(
    didIWin.value,
    opponentLabel.value,
    batailleCorse.value?.opponentForfeitReason(myPlayerIndex.value) ?? null,
  ));

const rematchButton = computed<RematchButton>(() => {
  if (isSolo.value) return { label: 'Play Again', disabled: false };
  switch (rematchState.value) {
    case 'requested-by-me':       return { label: 'Waiting for opponent…', disabled: true };
    case 'requested-by-opponent': return { label: 'Accept Rematch', disabled: false };
    default:                      return { label: 'Play Again', disabled: false };
  }
});

function onPlayAgain() {
  batailleCorseStore.rematch();
}

// A game can end on ANY move — a winning grab/slap or a SEND that empties the
// sender's hand. The winner only lands in state via the post-move state-update,
// so the overlay is driven by isGameOver (state), gated on the pile animation
// settling, rather than wired into each action's watcher.
const { showEndOverlay, revealImmediatelyIfOver, cancel: cancelEndScreen } =
  useEndScreen(() => isGameOver.value, () => isPileAnimating.value);

useGameBootstrap({ revealImmediatelyIfOver });

// An in-progress game: state loaded, not waiting, not yet over. Drives both the
// leave-confirmation guard and the cosmetic game-duration timer.
const isInProgress = computed(() =>
  !!batailleCorse.value && !isGameOver.value && !isWaiting.value);

// Cosmetic game-duration timer; the composable freezes the value at game over.
const { formattedDuration, cancel: cancelGameDuration } =
  useGameDuration(() => isInProgress.value, () => isGameOver.value);

const { opponentDisconnected, secondsRemaining, cancel: cancelDisconnectCountdown } = useDisconnectCountdown({
  mode: () => mode.value,
  opponentConnection: () => opponentConnection.value,
  myPlayerIndex: () => myPlayerIndex.value,
  isGameOver: () => isGameOver.value,
});

useLeaveGuard({
  isInProgress: () => isInProgress.value,
  mode: () => mode.value,
  forfeit: () => batailleCorseStore.forfeit(myPlayerIndex.value),
});

const { showMyTurn, showOpponentTurn, showFirstTurnHint, YOUR_TURN_LABEL, cancel: cancelTurnIndicator } = useTurnIndicator({
  state: () => batailleCorse.value,
  myPlayerIndex: () => myPlayerIndex.value,
  opponentIndex: () => opponentIndex.value,
  isWaiting: () => isWaiting.value,
  showEndOverlay: () => showEndOverlay.value,
});

useHotkeys(
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'send')) send(myPlayerIndex.value); },
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'slap')) slap(myPlayerIndex.value); },
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
  padding: 4px 14px;
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


@keyframes pile-flash {
  0%   { box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35); }
  35%  { box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35), 0 0 32px 10px rgba(var(--accent-active-rgb), 0.45); }
  100% { box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35); }
}

.pile_slot.pile-flash {
  animation: pile-flash 380ms ease-out;
}

.slap-ghost {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 1000;
  border: 1px solid black;
  border-radius: 6%;
  box-shadow: var(--card-shadow);
  transition: none;
}

.slap-ghost.transitioning {
  transition: transform var(--slap-duration, 280ms) ease-in-out,
              width var(--slap-duration, 280ms) ease-in-out,
              height var(--slap-duration, 280ms) ease-in-out;
}

.ghost-card {
  position: fixed;
  top: 0;
  left: 0;
  pointer-events: none;
  z-index: 1000;
  border: 1px solid black;
  border-radius: 6%;
  box-shadow: var(--card-shadow);
  transition: none;
}

.ghost-card.transitioning {
  transition: transform var(--ghost-duration, 100ms) ease-in,
              width var(--ghost-duration, 100ms) ease-in,
              height var(--ghost-duration, 100ms) ease-in;
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
  font-weight: 800;
  color: rgb(var(--accent-positive-rgb));
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.85);
  animation: card-delta-float 1.4s ease-out forwards;
}

.card-delta--negative {
  color: rgb(var(--accent-negative-rgb));
}

/* --- Turn indicator --- */
.player_tag--active {
  color: #ffffff;
  border-color: rgba(var(--accent-active-rgb), 0.9);
  box-shadow: 0 0 16px 3px rgba(var(--accent-active-rgb), 0.55);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

@keyframes turn-glow-pulse {
  0%, 100% { box-shadow: 0 0 12px 2px rgba(var(--accent-active-rgb), 0.40); }
  50%      { box-shadow: 0 0 22px 6px rgba(var(--accent-active-rgb), 0.70); }
}

/* Wrapper lets the one-time hint float above the name tag without shifting layout. */
.player_tag_wrap {
  position: relative;
  display: flex;
  justify-content: center;
}

.turn-hint {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 6px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  font-family: "Gabarito", sans-serif;
  font-size: 0.82rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: rgb(var(--accent-active-rgb));
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.7);
}

.turn-hint__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: rgb(var(--accent-active-rgb));
  box-shadow: 0 0 8px 2px rgba(var(--accent-active-rgb), 0.8);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

/* Same 1.8s ease-in-out as the name-tag glow so the two pulses stay in sync. */
.action_button--my-turn {
  animation: send-pulse 1.8s ease-in-out infinite;
}

@keyframes send-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(var(--accent-active-rgb), 0); }
  50%      { box-shadow: 0 0 16px 3px rgba(var(--accent-active-rgb), 0.65); }
}

.turn-fade-enter-active,
.turn-fade-leave-active {
  transition: opacity 0.25s ease;
}

.turn-fade-enter-from,
.turn-fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .player_tag--active,
  .turn-hint__dot,
  .action_button--my-turn,
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
