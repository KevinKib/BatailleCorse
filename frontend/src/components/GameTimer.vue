<template>
  <div :class="['game-timer', { 'game-timer--inline': inline }]" data-cy="game-timer">
    <i class="pi pi-clock game-timer__icon" aria-hidden="true"></i>
    <span class="game-timer__time">{{ time }}</span>
  </div>
</template>

<script setup lang="ts">
// `inline`: flows in a row of the table (the Bullshit top bar) instead of being pinned to the corner.
defineProps<{ time: string; inline?: boolean }>();
</script>

<style scoped>
/* Top-left HUD chip, styled to match the .player_tag felt pills. Pinned inside
   .gamescreen (position: relative); kept clear of the rules toggle, which owns
   the top-right corner. z-index sits above the board but below the
   waiting/disconnect/end overlays (1900-2000) so those cover it. */
.game-timer {
  position: absolute;
  top: calc(var(--hud-inset) + env(safe-area-inset-top, 0px));
  left: calc(var(--hud-inset) + env(safe-area-inset-left, 0px));
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.8rem;
  font-weight: 700;
  font-family: var(--font-title);
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.06em;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  box-sizing: border-box;
  height: var(--hud-chip-h);
  padding: 0 12px;
  pointer-events: none;
}

.game-timer--inline {
  position: static;
}

.game-timer__icon {
  font-size: 0.78rem;
  opacity: 0.7;
}
</style>
