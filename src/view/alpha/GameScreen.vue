<template>
  <div class="gamescreen flex">

    <div class="gamescreen_top flex">
      <div class="left_side"></div>
      <div class="middle_side">
        <h1 class="player_tag">Computer (Easy)</h1>
        <div class="card">
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
      <div class="pile_slot">
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
        <div class="card">
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
        <h1 class="player_tag">SNP</h1>
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

  <img
    v-if="ghostCard.visible"
    :src="ghostCard.src"
    :class="['ghost-card', { transitioning: ghostCard.transitioning }]"
    :style="{
      top: ghostCard.y + 'px',
      left: ghostCard.x + 'px',
      width: ghostCard.width + 'px',
      height: ghostCard.height + 'px',
      '--ghost-duration': ghostCard.duration + 'ms',
    }"
  />

</template>

<script setup lang="ts">
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import { Button } from 'primevue';
import { storeToRefs } from 'pinia';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { Action } from '../../service/model/Action';
import { nextTick, onBeforeUnmount, onMounted, reactive, ref, useTemplateRef, watch } from 'vue';
import cardBackUrl from '/src/resources/cards/png/card_back.png?url';

const cardImages = import.meta.glob('/src/resources/cards/png/*.png', { eager: true, query: 'url' });

function getCardUrl(rank: string, suit: string): string {
  const key = `/src/resources/cards/png/card_${rank.toLowerCase()}_${suit.toLowerCase()}.png`;
  return (cardImages[key] as { default: string })?.default ?? cardBackUrl;
}

const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse, lastSend, lastGrab } = storeToRefs(batailleCorseStore);

const pile = useTemplateRef("pile");
const opponentCard = useTemplateRef("opponentCard");
const centerPile = useTemplateRef("centerPile");
const centerPileArea = useTemplateRef<HTMLDivElement>("centerPileArea");

const ghostCard = reactive({
  visible: false,
  transitioning: false,
  x: 0, y: 0, width: 0, height: 0,
  duration: 100,
  src: cardBackUrl,
});

const isPileAnimating = ref(false);
const frozenPileCard = reactive({ rank: '', suit: '' });

function freezePileCard() {
  const top = batailleCorse.value?.pile.cards.at(0);
  frozenPileCard.rank = top?.rank ?? '';
  frozenPileCard.suit = top?.suit ?? '';
}

function animateGhostCard(srcRect: DOMRect, destRect: DOMRect, duration: number, src = cardBackUrl) {
  ghostCard.visible = false;
  ghostCard.transitioning = false;
  ghostCard.src = src;
  ghostCard.x = srcRect.left;
  ghostCard.y = srcRect.top;
  ghostCard.width = srcRect.width;
  ghostCard.height = srcRect.height;
  ghostCard.duration = duration;
  ghostCard.visible = true;

  nextTick(() => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        ghostCard.transitioning = true;
        ghostCard.x = destRect.left;
        ghostCard.y = destRect.top;
        ghostCard.width = destRect.width;
        ghostCard.height = destRect.height;
      });
    });
  });

  setTimeout(() => {
    ghostCard.visible = false;
    ghostCard.transitioning = false;
    isPileAnimating.value = false;
  }, duration + 50);
}

function getCenterPileRect(): DOMRect | undefined {
  const el = centerPile.value?.rootCard;
  return (el && el.offsetParent !== null)
    ? el.getBoundingClientRect()
    : centerPileArea.value?.getBoundingClientRect();
}

// During send animation, the server responds within a few ms with the new card face.
// Watch for that update and switch the ghost image immediately.
watch(() => batailleCorse.value?.pile.cards.at(0), (newCard) => {
  if (isPileAnimating.value && newCard) {
    ghostCard.src = getCardUrl(newCard.rank, newCard.suit);
  }
});

watch(lastSend, (event) => {
  if (!event) return;

  const sourceEl = event.playerIndex === 0
    ? pile.value?.rootCard
    : opponentCard.value?.rootCard;

  if (!sourceEl) return;

  const srcRect = sourceEl.getBoundingClientRect();
  const destRect = getCenterPileRect();

  if (!destRect || destRect.width === 0) return;

  freezePileCard();
  isPileAnimating.value = true;
  animateGhostCard(srcRect, destRect, 100);
});

watch(lastGrab, (event) => {
  if (!event) return;

  const destEl = event.winnerPlayerIndex === 0
    ? pile.value?.rootCard
    : opponentCard.value?.rootCard;

  if (!destEl) return;

  const srcRect = getCenterPileRect();
  const destRect = destEl.getBoundingClientRect();

  if (!srcRect || srcRect.width === 0) return;

  const topCard = batailleCorse.value?.pile.cards.at(0);
  const src = topCard ? getCardUrl(topCard.rank, topCard.suit) : cardBackUrl;
  freezePileCard();
  isPileAnimating.value = true;
  animateGhostCard(srcRect, destRect, 200, src);
}, { flush: 'sync' });

onMounted(() => {
  document.addEventListener('keyup', setupHotkeys);
})

onBeforeUnmount(() => {
  document.removeEventListener('keyup', setupHotkeys);
})

function setupHotkeys(event: { key: string; }) {
  if (event.key == 'q' || event.key == 'c') {
    send(0);
  }
  if (event.key == 'd' || event.key == ' ') {
    slap(0);
  }
}

function slap(playerIndex: number) {
  batailleCorseStore.slap(playerIndex);
}

function send(playerIndex: number) {
  batailleCorseStore.send(playerIndex);

  // TODO: make sure send is successful
}

function isButtonDisabled(playerIndex: number, buttonLabel: Action) {
  return !batailleCorse.value?.players.at(playerIndex)?.availableActions.includes(buttonLabel.toLocaleUpperCase());
}


</script>

<style>

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
    margin-top: auto;
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
    margin-bottom: auto;
  }
}

.pile_slot {
  display: flex;
  align-items: center;
  justify-content: center;
  width: fit-content;
  padding: 20px 28px;
  margin: auto;
  border: 2px dashed rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.22);
  box-shadow: inset 0 2px 16px rgba(0, 0, 0, 0.5);
}

.screen_content {
  opacity: 100%;
}

.card_with_counter {
  display: flex;
  flex-direction: row;
  width: fit-content;
  margin-left: auto;
  margin-right: auto;
  
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
  position: relative;
  width: fit-content;
  margin-left: auto;
  margin-right: auto;
  margin-top: 8px;
  margin-bottom: 8px;
}

.action_buttons {
  width: fit-content;
  margin-top: auto;
  margin-bottom: 16px;
  margin-left: auto;
  margin-right: auto;
}

.action_button {
  margin-left: 8px;
  margin-right: 8px;
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

.cardmoving {
  position: absolute;
  transition: 1s top, 1s left;
}

.ghost-card {
  position: fixed;
  pointer-events: none;
  z-index: 1000;
  border: 1px solid black;
  border-radius: 6%;
  box-shadow: 3px 5px 0px rgba(0, 0, 0, 0.9), 4px 10px 24px rgba(0, 0, 0, 0.6);
  transition: none;
}

.ghost-card.transitioning {
  transition: top var(--ghost-duration, 100ms) ease-in,
              left var(--ghost-duration, 100ms) ease-in,
              width var(--ghost-duration, 100ms) ease-in,
              height var(--ghost-duration, 100ms) ease-in;
}

</style>