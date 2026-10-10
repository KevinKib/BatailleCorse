<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import TitleCardFan from '../components/TitleCardFan.vue';
import GameChoiceCard from '../components/GameChoiceCard.vue';
import { preloadAllCards } from '../composables/useCardAnimation';
import { useI18n } from '../composables/useI18n';

const router = useRouter();
const { gamePicker } = useI18n();

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

      <div class="panel-divider" />

      <section class="choices" :aria-label="gamePicker.choose">
        <GameChoiceCard
          game-key="bataillecorse"
          :choice="gamePicker.bataillecorse"
          icon="pi pi-play"
          severity="success"
          @play="router.push({ name: 'home' })"
        />
        <GameChoiceCard
          game-key="bullshit"
          :choice="gamePicker.bullshit"
          icon="pi pi-bolt"
          severity="warn"
          @play="router.push({ name: 'bullshit-create' })"
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

.panel-divider {
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
}

.choices {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 640px) {
  .titlescreen { padding: 88px 12px 16px; }
  .title-panel { padding: 44px 16px 16px; gap: 12px; }
  .game-title { font-size: 2rem; }
  .choices { grid-template-columns: minmax(0, 1fr); gap: 8px; }
}
</style>
