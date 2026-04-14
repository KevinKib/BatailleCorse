<template>
  <div class="gamescreen flex">

    <div class="gamescreen_top flex">
      <div class="left_side"></div>
      <div class="middle_side">
        <h1 class="player_tag">Computer (Easy)</h1>
        <div class="card stacked">
          <PlayingCard
            ref="opponentCard"
            :size="90"
            :hidden="true"
            rank="10"
            suit="spade"
          />
          <div class="card_counter">
            <CardCounter :count="batailleCorse?.players.at(1)?.nbCards"/>
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
          <div class="card_counter">
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
        <h1 class="player_tag">SNP</h1>
        <div class="card stacked">
          <PlayingCard
            ref="pile"
            :size="90"
            :hidden="true"
            rank="10"
            suit="spade"
          />
          <div class="card_counter">
            <CardCounter :count="batailleCorse?.players.at(0)?.nbCards"/>
          </div>
        </div>
        <div class="action_buttons">
          <Button class="action_button" icon="pi pi-arrow-up" severity="success" label="Send" rounded
            @click="send(0)" :disabled="isButtonDisabled(0, 'send')"/>
          <Button class="action_button" icon="pi pi-hammer" severity="warn" label="Slap" rounded
            @click="slap(0)" :disabled="isButtonDisabled(0, 'slap')"/>
        </div>
      </div>

      <div class="right_side"></div>
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
import { Button } from 'primevue';
import { storeToRefs } from 'pinia';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { useCardAnimation, preloadAllCards } from '../../composables/useCardAnimation';
import { useHotkeys } from '../../composables/useHotkeys';
import { Action } from '../../service/model/Action';
import { onMounted, useTemplateRef, watch } from 'vue';

const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse, lastSend, lastGrab, lastSlap, lastSuccessfulSlap, lastErroneousSlap } = storeToRefs(batailleCorseStore);

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

watch(lastSend, (event) => {
  if (!event) return;
  const sourceEl = event.playerIndex === 0 ? pile.value?.rootCard : opponentCard.value?.rootCard;
  if (!sourceEl) return;
  const destRect = animation.getCenterPileRect();
  if (!destRect || destRect.width === 0) return;
  animation.animateSend(sourceEl.getBoundingClientRect(), destRect, batailleCorse.value?.pile.cards.at(0));
}, { flush: 'sync' });

// When GRAB fires, animate cards from center pile to winner's deck, then clear the pile display.
watch(lastGrab, (event) => {
  if (!event) { animation.cancelPileAnimation(); return; }
  const pileCards = [...(batailleCorse.value?.pile.cards ?? [])];
  const destEl = event.winnerPlayerIndex === 0 ? pile.value?.rootCard : opponentCard.value?.rootCard;
  const srcRect = animation.getCenterPileRect();
  if (!destEl || !srcRect || srcRect.width === 0) { animation.cancelPileAnimation(); return; }
  animation.showDeltaOnGrab(pileCards.length, destEl);
  animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
}, { flush: 'sync' });

// Immediate flash on any slap attempt, before the server responds.
watch(lastSlap, () => animation.flashPile());

// Successful slap: animate cards from pile to winner's deck.
watch(lastSuccessfulSlap, (event) => {
  if (!event) return;
  const destEl = event.winnerPlayerIndex === 0 ? pile.value?.rootCard : opponentCard.value?.rootCard;
  if (!destEl) return;
  const srcRect = animation.getCenterPileRect();
  if (!srcRect || srcRect.width === 0) return;
  const pileCards = [...(batailleCorse.value?.pile.cards ?? [])];
  animation.showDeltaOnSlap(pileCards.length, destEl);
  animation.animatePileToWinner(srcRect, destEl.getBoundingClientRect(), pileCards);
}, { flush: 'sync' });

// Erroneous slap: animate 2 ghost cards from slapper's deck to center, show -2 indicator.
watch(lastErroneousSlap, (event) => {
  if (!event) return;
  const srcEl = event.playerIndex === 0 ? pile.value?.rootCard : opponentCard.value?.rootCard;
  const destRect = animation.getCenterPileRect();
  if (!srcEl || !destRect) return;
  animation.animateErroneousSlap(srcEl.getBoundingClientRect(), destRect);
  animation.showDeltaAlways(-2, srcEl);
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

useHotkeys(
  () => { if (!isButtonDisabled(0, 'send')) send(0); },
  () => { if (!isButtonDisabled(0, 'slap')) slap(0); },
);

onMounted(() => {
  preloadAllCards();
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

</style>