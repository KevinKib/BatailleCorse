<script setup lang="ts">
import PlayingCard from './PlayingCard.vue';

// Decorative fanned hand peeking above the title panel. Shared by every
// title-style screen (lobby, create/join) so the fan is defined in one place.
// Geometry is derived from each card's index so the arc stays symmetric if
// the count ever changes.
const FAN_CARDS = [
  { rank: '10', suit: 'club' },
  { rank: 'jack', suit: 'diamond' },
  { rank: 'queen', suit: 'spade' },
  { rank: 'king', suit: 'heart' },
  { rank: 'ace', suit: 'spade' },
] as const;

const FAN_ANGLE_STEP = 11; // degrees between adjacent cards
const FAN_SPREAD = 40; // horizontal px between adjacent cards
const FAN_DIP = 4.5; // vertical px per (step-from-center)²; quadratic so the arc
                     // curves and inner cards sit nearer the center's height

function fanStyle(i: number) {
  const offset = i - (FAN_CARDS.length - 1) / 2;
  const x = offset * FAN_SPREAD;
  const y = offset * offset * FAN_DIP;
  const angle = offset * FAN_ANGLE_STEP;
  return {
    // Stack left-to-right so each card overlaps the one before it
    // (ace on top, then king, then queen, ...).
    transform: `translateX(calc(-50% + ${x}px)) translateY(${y}px) rotate(${angle}deg)`,
    zIndex: String(i),
  };
}
</script>

<template>
  <div class="deco-cards" aria-hidden="true">
    <PlayingCard
      v-for="(card, i) in FAN_CARDS"
      :key="`${card.rank}-${card.suit}`"
      :size="80"
      :rank="card.rank"
      :suit="card.suit"
      class="deco-card"
      :style="fanStyle(i)"
    />
  </div>
</template>

<style scoped>
.deco-cards {
  position: absolute;
  top: -95px;
  left: 50%;
  transform: translateX(-50%);
  width: 360px;
  height: 110px;
  pointer-events: none;
}

.deco-card {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform-origin: bottom center;
  /* Suppress PlayingCard's hard "sticker" box-shadow (very visible on the
     upright center card) and use a single soft, downward shadow instead. */
  box-shadow: none;
  filter: drop-shadow(0 7px 9px rgba(0, 0, 0, 0.4));
}
</style>
