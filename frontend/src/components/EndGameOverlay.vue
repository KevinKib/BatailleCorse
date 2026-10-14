<template>
  <div class="end-overlay" data-cy="end-overlay">
    <div :class="['end-card', `end-card--${outcome}`]">
      <div v-if="outcome === 'victory'" class="end-trophy" data-cy="victory-flourish">🏆</div>
      <h1 class="end-title">{{ ui[outcome] }}</h1>
      <p v-if="winnerLine" class="end-winner" data-test="end-winner">{{ winnerLine }}</p>
      <p class="end-sub">{{ subtitle }}</p>
      <!-- Extra content under the subtitle (a game's scores); nothing is rendered without it. -->
      <div v-if="$slots.default" class="end-extra" data-test="end-extra"><slot /></div>
      <p v-if="error" class="end-error" data-test="rematch-error" role="alert">{{ error }}</p>
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
        <RouterLink to="/games" class="end-home-button">
          <Button :label="ui.backToHome" icon="pi pi-home" rounded />
        </RouterLink>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Button } from 'primevue';
import type { RematchButton } from '../model/RematchButton';
import { useI18n } from '../composables/useI18n';

interface Props {
  didIWin: boolean;
  /** How the game ended for me; defaults to victory or defeat from `didIWin`. A draw has no winner. */
  result?: 'victory' | 'defeat' | 'draw';
  subtitle: string;
  /** Names who won ("Winner: Alice"); omitted by games that do not need it. */
  winnerLine?: string;
  rematchButton: RematchButton;
  /** Why the rematch could not start (shown above the buttons, announced). */
  error?: string;
}

interface Emits {
  playAgain: [];
}

const props = defineProps<Props>();
const outcome = computed(() => props.result ?? (props.didIWin ? 'victory' : 'defeat'));
const ui = useI18n().endScreen;
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
  font-family: var(--font-title);
  font-size: 2.4rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  margin: 0;
}

.end-winner {
  font-size: 1.15rem;
  font-weight: 600;
  margin: 0;
  overflow-wrap: anywhere;
}

.end-sub {
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.75);
  margin: 0;
}

.end-extra {
  width: 100%;
}
.end-error {
  font-size: 0.85rem;
  color: #ffb4b4;
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

/* Victory: gold accent (crisp 1px ring, no glow) + a brief trophy bounce. */
.end-card--victory {
  border-color: rgba(var(--accent-active-rgb), 0.55);
  box-shadow: var(--panel-shadow), var(--turn-ring);
}

.end-card--victory .end-title {
  color: var(--gold);
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

/* Draw: neutral, no flourish and no win or loss colour. */
.end-card--draw .end-title {
  color: #d9dedb;
}

@media (prefers-reduced-motion: reduce) {
  .end-trophy { animation: none; }
}
</style>
