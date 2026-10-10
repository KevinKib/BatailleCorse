<script setup lang="ts">
import { Button } from 'primevue';
import type { GameChoiceMessages } from '../locales/Messages';

// One game offered on the picker page: name, short pitch, player count and a Play button.
// Props down, event up: the page decides where "play" leads.
defineProps<{
  choice: GameChoiceMessages;
  icon: string;
  severity: 'success' | 'warn';
  gameKey: string;
}>();

defineEmits<{ play: [] }>();
</script>

<template>
  <article class="game-choice" :data-test="`choice-${gameKey}`">
    <header class="choice-head">
      <h2 class="choice-name">{{ choice.name }}</h2>
      <span class="choice-players">{{ choice.players }}</span>
    </header>
    <p class="choice-description">{{ choice.description }}</p>
    <Button
      class="choice-play"
      :data-test="`play-${gameKey}`"
      :label="choice.play"
      :icon="icon"
      :severity="severity"
      :aria-label="`${choice.play} ${choice.name}`"
      rounded
      @click="$emit('play')"
    />
  </article>
</template>

<style scoped>
.game-choice {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.28);
  border: 1px solid var(--panel-border);
  border-radius: 16px;
  text-align: left;
}

.choice-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.choice-name {
  margin: 0;
  font-family: var(--font-title);
  font-size: 1.35rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--gold);
}

.choice-players {
  flex-shrink: 0;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}

.choice-description {
  flex: 1;
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.82);
}

.choice-play {
  width: 100%;
  min-height: 44px;
  margin-top: 4px;
  letter-spacing: 0.08em;
}

@media (max-width: 640px) {
  .game-choice { gap: 4px; padding: 12px 16px; }
  .choice-name { font-size: 1.15rem; }
  .choice-description { font-size: 0.85rem; line-height: 1.3; }
  .choice-play { margin-top: 4px; }
}
</style>
