<template>
  <div class="end-overlay" data-cy="end-overlay">
    <div :class="['end-card', didIWin ? 'end-card--victory' : 'end-card--defeat']">
      <div v-if="didIWin" class="end-trophy" data-cy="victory-flourish">🏆</div>
      <h1 class="end-title">{{ didIWin ? 'VICTORY' : 'DEFEAT' }}</h1>
      <p class="end-sub">{{ subtitle }}</p>
      <div class="end-actions">
        <Button
          class="end-replay-button"
          :label="rematchButton.label"
          :disabled="rematchButton.disabled"
          icon="pi pi-replay"
          severity="success"
          rounded
          data-cy="play-again"
          @click="emit('playAgain')"
        />
        <RouterLink :to="{ name: 'home' }" class="end-home-button">
          <Button label="Back to home" icon="pi pi-home" rounded />
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Button } from 'primevue';
import type { RematchButton } from '../model/RematchButton';

interface Props {
  didIWin: boolean;
  subtitle: string;
  rematchButton: RematchButton;
}

interface Emits {
  playAgain: [];
}

defineProps<Props>();
const emit = defineEmits<Emits>();
</script>

<style scoped>
.end-overlay {
  position: absolute;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.82);
  backdrop-filter: blur(4px);
}

.end-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  box-shadow: var(--panel-shadow);
  border-radius: 16px;
  padding: 40px 48px;
  max-width: 460px;
  text-align: center;
}

.end-title {
  font-family: "Gabarito", sans-serif;
  font-size: 2.4rem;
  font-weight: 800;
  letter-spacing: 0.06em;
  margin: 0;
}

.end-sub {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.75);
  margin: 0;
}

.end-home-button {
  margin-top: 10px;
}

.end-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

/* Victory: gold accent + a brief trophy bounce / glow pulse. */
.end-card--victory {
  border-color: rgba(var(--accent-active-rgb), 0.55);
  box-shadow: var(--panel-shadow), 0 0 48px 6px rgba(var(--accent-active-rgb), 0.25);
}

.end-card--victory .end-title {
  color: var(--gold);
  text-shadow: 0 2px 16px rgba(var(--accent-active-rgb), 0.45);
}

.end-trophy {
  font-size: 3.2rem;
  line-height: 1;
  animation: trophy-bounce 1.6s ease-in-out infinite;
}

@keyframes trophy-bounce {
  0%, 100% { transform: translateY(0) scale(1); }
  30%      { transform: translateY(-10px) scale(1.06); }
  60%      { transform: translateY(0) scale(1); }
}

/* Defeat: muted / somber, no flourish. */
.end-card--defeat {
  border-color: rgba(var(--accent-negative-rgb), 0.35);
}

.end-card--defeat .end-title {
  color: #cbd5d1;
}

@media (prefers-reduced-motion: reduce) {
  .end-trophy { animation: none; }
}
</style>
