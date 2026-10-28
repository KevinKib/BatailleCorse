<script setup lang="ts">
import { Button } from 'primevue';
import { useI18n } from '../composables/useI18n';
import { format } from '../locales/format';
import type { GameChoiceMessages } from '../locales/Messages';
import type { GamePickerStyle } from '../model/games';

// One game offered on the picker page: name, short pitch, player count and a Play button.
// Props down, event up: the page decides where "play" leads.
defineProps<{
  choice: GameChoiceMessages;
  icon: string;
  severity: GamePickerStyle['severity'];
  gameKey: string;
}>();

defineEmits<{ play: [] }>();

const { gamePicker } = useI18n();
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
      :aria-label="format(gamePicker.playAria, { play: choice.play, name: choice.name })"
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

/* Phones: cards sit two per row (about 150px wide), so the head stacks and the type shrinks a little. */
@media (max-width: 640px) {
  .game-choice { gap: 6px; padding: 12px 10px; }
  .choice-head { flex-direction: column; align-items: flex-start; gap: 2px; }
  .choice-name { font-size: 1rem; line-height: 1.15; letter-spacing: 0.03em; overflow-wrap: anywhere; }
  .choice-players { font-size: 0.68rem; letter-spacing: 0.08em; }
  .choice-description { font-size: 0.8rem; line-height: 1.35; overflow-wrap: anywhere; }
  .choice-play { margin-top: 2px; }
  /* An odd count leaves the last card alone on its row: it takes the whole row, with the Play button beside the text,
     so a fifth game costs a short row instead of a tall one (the picker must still fit a 375x667 phone). */
  .game-choice:last-child:nth-child(odd) {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    column-gap: 12px;
    align-items: center;
  }
  .game-choice:last-child:nth-child(odd) .choice-head { grid-column: 1; flex-direction: row; flex-wrap: wrap; align-items: baseline; gap: 0 8px; }
  .game-choice:last-child:nth-child(odd) .choice-name { overflow-wrap: normal; }
  .game-choice:last-child:nth-child(odd) .choice-description { grid-column: 1; }
  .game-choice:last-child:nth-child(odd) .choice-play { grid-column: 2; grid-row: 1 / span 2; width: auto; min-width: 112px; margin: 0; }
}
/* Short phones (375x667, 360x640): five games in three rows, so each card gives back a little room. */
@media (max-width: 640px) and (max-height: 700px) {
  .game-choice { gap: 4px; padding: 8px 10px; }
  .choice-description { font-size: 0.75rem; line-height: 1.25; }
}
</style>
