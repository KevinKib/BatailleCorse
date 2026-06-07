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

const FAN_ANGLE_STEP = 14; // degrees between adjacent cards
const FAN_SPREAD = 50; // horizontal px between adjacent cards
const FAN_DIP = 16; // px each card drops per step away from center

function fanStyle(i: number) {
  const offset = i - (FAN_CARDS.length - 1) / 2;
  const x = offset * FAN_SPREAD;
  const y = Math.abs(offset) * FAN_DIP;
  const angle = offset * FAN_ANGLE_STEP;
  return {
    transform: `translateX(calc(-50% + ${x}px)) translateY(${y}px) rotate(${angle}deg)`,
    zIndex: String(FAN_CARDS.length - Math.abs(offset)),
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
  filter: drop-shadow(0 6px 14px rgba(0, 0, 0, 0.6));
}
</style>
