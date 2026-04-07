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
        top: ghost.y + 'px',
        left: ghost.x + 'px',
        width: ghost.width + 'px',
        height: ghost.height + 'px',
      }"
    />
  </template>

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
const { state: batailleCorse, lastSend, lastGrab, lastSlap, lastSuccessfulSlap } = storeToRefs(batailleCorseStore);

const SLAP_DURATION = 280;
const SLAP_STAGGER = 60;
const SLAP_OFFSETS = [{ x: 0, y: 0 }, { x: 6, y: -4 }, { x: -5, y: -7 }];

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
const isPileFlashing = ref(false);
const frozenPileCard = reactive({ rank: '', suit: '' });

const slapGhosts = SLAP_OFFSETS.map(() =>
  reactive({ visible: false, transitioning: false, x: 0, y: 0, width: 0, height: 0, src: cardBackUrl })
);

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

// When GRAB fires the pile is cleared — unfreeze so the empty state renders cleanly.
watch(lastGrab, () => {
  isPileAnimating.value = false;
}, { flush: 'sync' });

// Immediate flash on any slap attempt, before the server responds.
watch(lastSlap, () => {
  isPileFlashing.value = true;
  setTimeout(() => { isPileFlashing.value = false; }, 400);
});

// Successful slap response: animate 3 staggered ghost cards from pile to winner's deck.
// isPileAnimating stays true until GRAB fires (~1500ms later) to keep the pile visible.
watch(lastSuccessfulSlap, (event) => {
  if (!event) return;

  const destEl = event.winnerPlayerIndex === 0
    ? pile.value?.rootCard
    : opponentCard.value?.rootCard;

  if (!destEl) return;

  const srcRect = getCenterPileRect();
  const destRect = destEl.getBoundingClientRect();

  if (!srcRect || srcRect.width === 0) return;

  const topCard = batailleCorse.value?.pile.cards.at(0);

  freezePileCard();
  isPileAnimating.value = true;

  slapGhosts.forEach((ghost, i) => {
    const offset = SLAP_OFFSETS[i];

    setTimeout(() => {
      ghost.src = i === 0 && topCard ? getCardUrl(topCard.rank, topCard.suit) : cardBackUrl;
      ghost.visible = false;
      ghost.transitioning = false;
      ghost.x = srcRect.left + offset.x;
      ghost.y = srcRect.top + offset.y;
      ghost.width = srcRect.width;
      ghost.height = srcRect.height;
      ghost.visible = true;

      nextTick(() => {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            ghost.transitioning = true;
            ghost.x = destRect.left;
            ghost.y = destRect.top;
            ghost.width = destRect.width;
            ghost.height = destRect.height;
          });
        });
      });

      setTimeout(() => {
        ghost.visible = false;
        ghost.transitioning = false;
      }, SLAP_DURATION + 50);
    }, i * SLAP_STAGGER);
  });
}, { flush: 'sync' });

onMounted(() => {
  document.addEventListener('keyup', setupHotkeys);
})

onBeforeUnmount(() => {
  document.removeEventListener('keyup', setupHotkeys);
})

function setupHotkeys(event: { key: string; }) {
  if (event.key == 'q' || event.key == 'c') {
    if (!isButtonDisabled(0, 'send')) send(0);
  }
  if (event.key == 'd' || event.key == ' ') {
    if (!isButtonDisabled(0, 'slap')) slap(0);
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
  z-index: 0;
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

.cardmoving {
  position: absolute;
  transition: 1s top, 1s left;
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
  pointer-events: none;
  z-index: 1000;
  border: 1px solid black;
  border-radius: 6%;
  box-shadow: 3px 5px 0px rgba(0, 0, 0, 0.9), 4px 10px 24px rgba(0, 0, 0, 0.6);
  transition: none;
}

.slap-ghost.transitioning {
  transition: top 280ms ease-in-out, left 280ms ease-in-out,
              width 280ms ease-in-out, height 280ms ease-in-out;
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