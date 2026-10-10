<template>
  <div class="opponent-seat" :class="{ 'opponent-seat--disconnected': disconnected }">
    <span :class="['seat-label', { 'seat-label--active': active }]" data-test="seat-label">{{ label }}</span>
    <div class="seat-card">
      <PlayingCard :hidden="true" rank="10" suit="spade" />
      <div class="seat-chip" data-test="seat-count">
        <CardCounter :count="handCount" />
      </div>
      <SeatDisconnectBadge
        v-if="disconnected"
        class="seat-disconnect"
        :seconds-remaining="secondsRemaining ?? null" />
    </div>
  </div>
</template>

<script setup lang="ts">
import PlayingCard from '../PlayingCard.vue';
import CardCounter from '../CardCounter.vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';

defineProps<{
  label: string;
  handCount: number;
  active: boolean;
  disconnected?: boolean;
  secondsRemaining?: number | null;
}>();
</script>

<style scoped>
.opponent-seat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.seat-card {
  position: relative;
}

/* Match the screen's fluid card sizing; height auto + intrinsic aspect ratio. */
.opponent-seat :deep(.playing_card) {
  width: var(--seat-card-w, 56px);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}

.seat-chip {
  position: absolute;
  bottom: -6px;
  right: -10px;
}

/* Dim a dropped opponent's card; the badge sits centred over it. */
.opponent-seat--disconnected .seat-card :deep(.playing_card) {
  filter: grayscale(1) brightness(0.6);
}
.seat-disconnect {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 1;
}

.seat-label {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

/* Active turn: crisp 1px ring + fixed dot, same as BatailleCorse's name tag. */
.seat-label--active {
  position: relative;
  padding-left: 20px;
  color: #ffffff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}

.seat-label--active::before {
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
</style>
