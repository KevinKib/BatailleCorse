<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import TitleCardFan from '../components/TitleCardFan.vue';
import GameChoiceCard from '../components/GameChoiceCard.vue';
import { preloadAllCards } from '../composables/useCardAnimation';
import { useI18n } from '../composables/useI18n';
import { gameChoice } from '../locales/gameChoice';
import { GAMES } from '../model/games';

const router = useRouter();
const route = useRoute();
const messages = useI18n();
const { gamePicker, lobby } = messages;
const choices = GAMES.map(game => ({ game, choice: gameChoice(messages, game.key) }));
// Set when the host removed this player from a lobby.
const removed = computed(() => route.query.removed === '1');

onMounted(() => preloadAllCards());
</script>

<template>
  <div class="titlescreen">
    <main class="title-panel">
      <TitleCardFan />

      <div class="title-block">
        <div class="suit-row" aria-hidden="true">
          <span class="suit-accent">♠</span>
          <span class="suit-accent">♥</span>
          <span class="suit-accent">♦</span>
          <span class="suit-accent">♣</span>
        </div>
        <h1 class="game-title" data-test="picker-title">{{ gamePicker.title }}</h1>
      </div>

      <p v-if="removed" class="removed-notice" role="status" data-test="removed-notice">{{ lobby.removedByHost }}</p>

      <div class="panel-divider" />

      <section class="choices" :aria-label="gamePicker.choose">
        <GameChoiceCard
          v-for="{ game, choice } in choices"
          :key="game.key"
          :game-key="game.key"
          :choice="choice"
          :icon="game.picker.icon"
          :severity="game.picker.severity"
          @play="router.push(game.gameHomeRoute)"
        />
      </section>
    </main>
  </div>
</template>

<style scoped>
.titlescreen {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow-x: hidden;
  overflow-y: auto;
  box-sizing: border-box;
  padding: 100px 16px 24px;
}

.title-panel {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 20px;
  padding: 40px 40px 32px;
  box-shadow: var(--panel-shadow);
  width: 100%;
  max-width: 720px;
  box-sizing: border-box;
  margin: auto 0;
}

.title-block {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.suit-row { display: flex; gap: 10px; margin-bottom: 4px; }
.suit-accent { font-size: 1rem; color: var(--gold-soft); opacity: 0.7; }
.game-title {
  font-family: var(--font-title);
  font-size: 2.6rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  background: linear-gradient(135deg, var(--gold) 0%, var(--gold-deep) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin: 0;
  line-height: 1.1;
}

.removed-notice {
  width: 100%;
  margin: 0;
  padding: 8px 12px;
  box-sizing: border-box;
  text-align: center;
  font-size: 0.875rem;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.panel-divider {
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
}

.choices {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(240px, 100%), 1fr));
  gap: 16px;
}

@media (max-width: 640px) {
  .titlescreen { padding: 88px 12px 16px; }
  .title-panel { padding: 44px 16px 16px; gap: 12px; }
  .game-title { font-size: 2rem; }
  .choices { grid-template-columns: minmax(0, 1fr); gap: 8px; }
}
</style>
