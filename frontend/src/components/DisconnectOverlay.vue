<template>
  <div class="disconnect-overlay" data-cy="disconnect-overlay">
    <div class="disconnect-card">
      <h2 class="disconnect-title">{{ format(ui.title, { player: opponentLabel }) }}</h2>
      <p class="disconnect-sub">{{ ui.waiting }}</p>
      <p class="disconnect-countdown" data-cy="disconnect-countdown">
        {{ format(ui.countdown, { n: secondsRemaining }) }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from '../composables/useI18n';
import { format } from '../locales/format';

interface Props {
  opponentLabel: string;
  secondsRemaining: number;
}

defineProps<Props>();
const ui = useI18n().disconnect;
</script>

<style scoped>
.disconnect-overlay {
  position: absolute;
  inset: 0;
  z-index: 1900;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  pointer-events: none;
  padding-top: 12vh;
}

.disconnect-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  background: var(--panel-bg);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.45);
  box-shadow: var(--panel-shadow);
  border-radius: 14px;
  padding: 18px 28px;
  text-align: center;
}

.disconnect-title {
  font-family: var(--font-title);
  font-size: 1.2rem;
  font-weight: 700;
  color: #f87171;
  margin: 0;
}

.disconnect-sub {
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
}

.disconnect-countdown {
  font-size: 1rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--gold);
  margin: 4px 0 0;
}
</style>
