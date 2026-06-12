<template>
  <div class="game-timer" data-cy="game-timer">
    <i class="pi pi-clock game-timer__icon" aria-hidden="true"></i>
    <span class="game-timer__time">{{ time }}</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ time: string }>();
</script>

<style scoped>
/* Top-right HUD chip, styled to match the .player_tag felt pills. Pinned inside
   .gamescreen (position: relative). z-index sits above the board but below the
   waiting/disconnect/end overlays (1900-2000) so those cover it. */
.game-timer {
  position: absolute;
  top: calc(10px + env(safe-area-inset-top, 0px));
  right: calc(10px + env(safe-area-inset-right, 0px));
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 4px 12px;
  pointer-events: none;
}

.game-timer__icon {
  font-size: 0.78rem;
  opacity: 0.7;
}
</style>
