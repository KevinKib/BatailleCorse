<template>
  <div class="seat-disconnect-badge" data-test="seat-disconnect-badge">
    <span class="dot" aria-hidden="true"></span>
    <span class="text">
      Reconnecting<template v-if="secondsRemaining !== null">… {{ secondsRemaining }}s</template>
    </span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ secondsRemaining: number | null }>();
</script>

<style scoped>
.seat-disconnect-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  white-space: nowrap;
  color: #fff;
  background: rgba(0, 0, 0, 0.72);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.75);
  border-radius: 999px;
  padding: 3px 9px;
  box-shadow: 0 0 12px 1px rgba(var(--accent-negative-rgb), 0.45);
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: rgb(var(--accent-negative-rgb));
  animation: badge-pulse 1.2s ease-in-out infinite;
}
@keyframes badge-pulse {
  0%, 100% { opacity: 1; }
  50%      { opacity: 0.35; }
}
@media (prefers-reduced-motion: reduce) {
  .dot { animation: none; }
}
</style>
