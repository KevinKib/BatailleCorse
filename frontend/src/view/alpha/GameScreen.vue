<template>
  <div class="gamescreen flex">

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
      <div :class="['pile_slot', { 'pile-flash': isPileFlashing }]">
        <div class="card" ref="centerPileArea">
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
          <Button severity="danger" label="Back" icon="pi pi-undo" variant="" rounded />
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

    <div v-if="isWaiting" class="waiting-overlay">
      <div class="waiting-card">
        <h2 class="waiting-title">Waiting for opponent…</h2>
        <p class="waiting-sub">Share this link to invite a player</p>
        <div class="share-row">
          <InputText :value="shareLink" readonly class="share-input" />
          <Button label="Copy" icon="pi pi-copy" rounded @click="copyShareLink" />
        </div>
        <p v-if="copied" class="waiting-copied">Copied!</p>
      </div>
    </div>

    <div v-if="showEndOverlay" class="end-overlay" data-cy="end-overlay">
      <div :class="['end-card', didIWin ? 'end-card--victory' : 'end-card--defeat']">
        <div v-if="didIWin" class="end-trophy" data-cy="victory-flourish">🏆</div>
        <h1 class="end-title">{{ didIWin ? 'VICTORY' : 'DEFEAT' }}</h1>
        <p class="end-sub">
          {{ didIWin ? `You beat ${opponentLabel}!` : `${opponentLabel} won.` }}
        </p>
        <RouterLink to="/" class="end-home-button">
          <Button label="Back to home" icon="pi pi-home" rounded />
        </RouterLink>
      </div>
    </div>
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
import { Button, InputText } from 'primevue';
import { storeToRefs } from 'pinia';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { useSettingsStore } from '../../state/Settings.store';
import { DIFFICULTY } from '../../model/Difficulty';
import { useCardAnimation } from '../../composables/useCardAnimation';
import { useHotkeys } from '../../composables/useHotkeys';
import { useEndScreen } from '../../composables/useEndScreen';
import { Action } from '../../model/Action';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import webSocketService from '../../service/WebSocketService';
import type BatailleCorse from '../../model/BatailleCorse';

const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse, mode, myPlayerIndex, waiting, myName, opponentName,
        lastSend, lastGrab, lastSlap, lastSuccessfulSlap, lastErroneousSlap } = storeToRefs(batailleCorseStore);

const pile = useTemplateRef("pile");
const opponentCard = useTemplateRef("opponentCard");
const centerPile = useTemplateRef("centerPile");
const centerPileArea = useTemplateRef<HTMLDivElement>("centerPileArea");

const animation = useCardAnimation(
  () => pile.value?.rootCard,
  () => opponentCard.value?.rootCard,
  () => centerPile.value?.rootCard,
  () => centerPileArea.value,
);

const {
  ghostCard, slapGhosts, isPileAnimating, isPileFlashing, frozenPileCard, cardDeltaIndicator,
} = animation;

// During send animation, the server responds within a few ms with the new card face.
// Watch for that update and switch the ghost image immediately.
watch(() => batailleCorse.value?.pile.cards.at(0), (newCard) => {
  animation.onNewPileCard(newCard);
});

// SEND is optimistic: topCard is snapshotted in the store at call time, no flush:'sync' needed.
watch(lastSend, async (event) => {
  if (!event) return;
  const sourceEl = event.playerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
  if (!sourceEl) return;
  await nextTick();
  const destRect = animation.getCenterPileRect();
  if (!destRect || destRect.width === 0) return;
  animation.animateSend(sourceEl.getBoundingClientRect(), destRect, event.topCard);
  // SEND is non-blocking in the queue — no notifyAnimationComplete needed.
});

// GRAB: pileCards snapshot is embedded in the event by the store.
watch(lastGrab, async (event) => {
  if (!event) { animation.cancelPileAnimation(); batailleCorseStore.notifyAnimationComplete(); return; }
  const { pileCards, winnerPlayerIndex } = event;
  await nextTick();
  const destEl = winnerPlayerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
  const srcRect = animation.getCenterPileRect();
  if (!destEl || !srcRect || srcRect.width === 0) {
    animation.cancelPileAnimation();
    batailleCorseStore.notifyAnimationComplete();
    return;
  }
  animation.showDeltaOnGrab(pileCards.length, destEl);
  await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
  batailleCorseStore.notifyAnimationComplete();
});

// Immediate flash on any slap attempt, before the server responds.
watch(lastSlap, () => animation.flashPile());

// Successful slap: pileCards snapshot is embedded in the event by the store.
watch(lastSuccessfulSlap, async (event) => {
  if (!event) return;
  const { pileCards, winnerPlayerIndex } = event;
  await nextTick();
  const destEl = winnerPlayerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
  if (!destEl) { batailleCorseStore.notifyAnimationComplete(); return; }
  const srcRect = animation.getCenterPileRect();
  if (!srcRect || srcRect.width === 0) { batailleCorseStore.notifyAnimationComplete(); return; }
  animation.showDeltaOnSlap(pileCards.length, destEl);
  await animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
  batailleCorseStore.notifyAnimationComplete();
});

// Erroneous slap: animate 2 ghost cards from slapper's deck to center, show -2 indicator.
watch(lastErroneousSlap, async (event) => {
  if (!event) return;
  await nextTick();
  const srcEl = event.playerIndex === myPlayerIndex.value ? pile.value?.rootCard : opponentCard.value?.rootCard;
  const destRect = animation.getCenterPileRect();
  if (!srcEl || !destRect) { batailleCorseStore.notifyAnimationComplete(); return; }
  await animation.animateErroneousSlap(srcEl.getBoundingClientRect(), destRect);
  animation.showDeltaAlways(-2, srcEl);
  batailleCorseStore.notifyAnimationComplete();
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

const copied = ref(false);
async function copyShareLink() {
  await navigator.clipboard.writeText(shareLink.value);
  copied.value = true;
  setTimeout(() => { copied.value = false; }, 1500);
}

const settingsStore = useSettingsStore();
const route = useRoute();
const router = useRouter();

const difficultyLabel = computed(() => DIFFICULTY[settingsStore.difficulty]?.name);

const opponentIndex = computed(() => 1 - myPlayerIndex.value);
const isSolo = computed(() => mode.value === 'solo');
const isWaiting = computed(() => waiting.value);
const opponentLabel = computed(() =>
  isSolo.value ? `Computer (${difficultyLabel.value})` : (opponentName.value ?? 'Opponent'));
const shareLink = computed(() =>
  `${window.location.origin}/join/${route.params.id}`);

const isGameOver = computed(() => batailleCorse.value?.isOver() ?? false);
const didIWin = computed(() => batailleCorse.value?.isWinnerAt(myPlayerIndex.value) ?? false);

// A game can end on ANY move — a winning grab/slap or a SEND that empties the
// sender's hand. The winner only lands in state via the post-move state-update,
// so the overlay is driven by isGameOver (state), gated on the pile animation
// settling, rather than wired into each action's watcher.
const { showEndOverlay, revealImmediatelyIfOver, cancel: cancelEndScreen } =
  useEndScreen(() => isGameOver.value, () => isPileAnimating.value);

// --- Turn indicator ---
// Permanent cue: the active player's name tag glows. (isTurnOf + the glow CSS
// are per-seat and N-player-ready; the view currently wires only two seats via
// opponentIndex = 1 - myPlayerIndex. A future 4-player layout would iterate
// players and call isTurnOf(i) per seat.)
// A glow is a learned signal, so we teach it once: the YOUR TURN hint shows the
// first time it becomes your turn in a game, then auto-dismisses for good —
// keeping the steady state uncluttered in a fast-reaction game.
const YOUR_TURN_LABEL = 'YOUR TURN';

const isMyTurn = computed(() => batailleCorse.value?.isTurnOf(myPlayerIndex.value) ?? false);
const isOpponentTurn = computed(() => batailleCorse.value?.isTurnOf(opponentIndex.value) ?? false);

// Suppress turn cues while an overlay owns the screen (waiting / game over).
const showTurnCues = computed(() => !isWaiting.value && !showEndOverlay.value);
const showMyTurn = computed(() => showTurnCues.value && isMyTurn.value);
const showOpponentTurn = computed(() => showTurnCues.value && isOpponentTurn.value);

// One-time onboarding hint: visible only during the player's first turn of the
// game and tied to turn state (not a timer), so it vanishes the instant they
// play — even if that's within a second — and never returns. Bootstraps the
// meaning of the name-tag glow.
const showFirstTurnHint = ref(false);
let firstTurnHintConsumed = false;

watch(showMyTurn, (mine) => {
  if (firstTurnHintConsumed) return;
  if (mine) {
    showFirstTurnHint.value = true;
  } else if (showFirstTurnHint.value) {
    showFirstTurnHint.value = false;
    firstTurnHintConsumed = true;
  }
}, { immediate: true });

useHotkeys(
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'send')) send(myPlayerIndex.value); },
  () => { if (!isButtonDisabled(myPlayerIndex.value, 'slap')) slap(myPlayerIndex.value); },
  () => [settingsStore.sendKey],
  () => [settingsStore.slapKey],
);

onMounted(async () => {
  const gameId = route.params.id as string;

  const stored = localStorage.getItem(`tokens:${gameId}`);
  if (!stored) {
    router.replace('/');
    return;
  }

  const response = await fetch(`/api/game/${gameId}`);
  if (!response.ok) {
    router.replace('/');
    return;
  }

  const gameState = await response.json() as BatailleCorse;
  batailleCorseStore.hydrate(gameId, gameState);
  revealImmediatelyIfOver();
  batailleCorseStore.restoreSession(JSON.parse(stored));
  await batailleCorseStore.loadSessionView(gameId);
  webSocketService.subscribeToGame(gameId);
});

onBeforeUnmount(() => {
  animation.cancelAllAnimations();
  batailleCorseStore.cancelAutoGrab();
  webSocketService.unsubscribeFromGame();
  cancelEndScreen();
});
</script>

<style scoped>

.gamescreen {
  /* Two-layer background: vignette on top, deep felt below */
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, #1e5c30 0%, #0d2e18 48%, #07160d 100%);
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.gamescreen_top {
  height: 30%;
  background: rgba(0, 0, 0, 0.10);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  padding-bottom: 20px;

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-end;
    gap: 10px;
    margin: 0;
  }
}

.gamescreen_middle {
  height: 40%;
}

.gamescreen_bottom {
  height: 30%;
  background: rgba(0, 0, 0, 0.10);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  padding-top: 20px;

  .middle_side {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    margin: 0;
  }
}

.pile_slot .card {
  min-width: 125px;
  min-height: 182px;
}

.pile_slot {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 181px;  /* 125px card + 28px padding × 2 */
  min-height: 222px; /* 182px card + 20px padding × 2 */
  padding: 20px 28px;
  margin: auto;
  border: 2px dashed rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.22);
  box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5);
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
  width: 30%;
  display: flex;
}

.middle_side {
  width: 40%;
}

.right_side {
  width: 30%;
}

.back_button {
  margin-left: 16px;
  margin-right: 16px;
  margin-top: auto;
  margin-bottom: 16px;
}


@keyframes pile-flash {
  0%   { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
  35%  { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5), 0 0 32px 10px rgba(255, 210, 40, 0.45); }
  100% { box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5); }
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
  box-shadow: 3px 5px 0px rgba(0, 0, 0, 0.9), 4px 10px 24px rgba(0, 0, 0, 0.6);
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
  box-shadow: 3px 5px 0px rgba(0, 0, 0, 0.9), 4px 10px 24px rgba(0, 0, 0, 0.6);
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
  color: #4ade80;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.85);
  animation: card-delta-float 1.4s ease-out forwards;
}

.card-delta--negative {
  color: #f87171;
}

.waiting-overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.78);
  backdrop-filter: blur(3px);
}

.waiting-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  padding: 36px 40px;
  max-width: 460px;
}

.waiting-title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.6rem;
  font-weight: 700;
  color: #f5c842;
  margin: 0;
}

.waiting-sub {
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

.share-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.share-input {
  flex: 1;
}

.waiting-copied {
  font-size: 0.72rem;
  color: #4ade80;
  margin: 0;
}

.end-overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.82);
  backdrop-filter: blur(4px);
}

.end-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  padding: 40px 48px;
  max-width: 460px;
  text-align: center;
}

.end-title {
  font-family: "Gabarito", sans-serif;
  font-size: 2.4rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  margin: 0;
}

.end-sub {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.75);
  margin: 0;
}

.end-home-button {
  margin-top: 10px;
}

/* Victory: gold accent + a brief trophy bounce / glow pulse. */
.end-card--victory {
  border-color: rgba(245, 200, 66, 0.55);
  box-shadow: 0 0 48px 6px rgba(245, 200, 66, 0.25);
}

.end-card--victory .end-title {
  color: #f5c842;
  text-shadow: 0 2px 16px rgba(245, 200, 66, 0.45);
}

.end-trophy {
  font-size: 3.2rem;
  line-height: 1;
  animation: trophy-bounce 1.6s ease-in-out infinite;
}

@keyframes trophy-bounce {
  0%, 100% { transform: translateY(0) scale(1); }
  30%      { transform: translateY(-10px) scale(1.06); }
  60%      { transform: translateY(0) scale(1); }
}

/* Defeat: muted / somber, no flourish. */
.end-card--defeat {
  border-color: rgba(248, 113, 113, 0.35);
}

.end-card--defeat .end-title {
  color: #cbd5d1;
}

/* --- Turn indicator --- */
.player_tag--active {
  color: #ffffff;
  border-color: rgba(74, 222, 128, 0.9);
  box-shadow: 0 0 16px 3px rgba(74, 222, 128, 0.55);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

@keyframes turn-glow-pulse {
  0%, 100% { box-shadow: 0 0 12px 2px rgba(74, 222, 128, 0.40); }
  50%      { box-shadow: 0 0 22px 6px rgba(74, 222, 128, 0.70); }
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
  color: #4ade80;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.7);
}

.turn-hint__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #4ade80;
  box-shadow: 0 0 8px 2px rgba(74, 222, 128, 0.8);
  animation: turn-glow-pulse 1.8s ease-in-out infinite;
}

/* Same 1.8s ease-in-out as the name-tag glow so the two pulses stay in sync. */
.action_button--my-turn {
  animation: send-pulse 1.8s ease-in-out infinite;
}

@keyframes send-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(74, 222, 128, 0); }
  50%      { box-shadow: 0 0 16px 3px rgba(74, 222, 128, 0.65); }
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
  .end-trophy,
  .player_tag--active,
  .turn-hint__dot,
  .action_button--my-turn { animation: none; }
}

</style>
