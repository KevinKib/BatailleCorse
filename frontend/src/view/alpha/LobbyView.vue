<template>
  <div class="titlescreen">
    <div class="title-panel">

      <TitleCardFan />

      <div class="title-block">
        <div class="suit-row">
          <span class="suit-accent">♠</span>
          <span class="suit-accent">♥</span>
          <span class="suit-accent">♦</span>
          <span class="suit-accent">♣</span>
        </div>
        <h1 class="game-title">Bataille Corse</h1>
      </div>

      <div class="panel-divider" />

      <Button
        class="menu-button"
        label="New Game"
        icon="pi pi-plus"
        severity="success"
        size="large"
        rounded
        @click="router.push({ name: 'create' })"
      />

      <Button
        class="menu-button"
        label="Join Game"
        icon="pi pi-users"
        severity="secondary"
        size="large"
        rounded
        @click="router.push({ name: 'join' })"
      />

      <Button
        class="back-button"
        data-test="back"
        :label="gamePicker.back"
        icon="pi pi-arrow-left"
        severity="secondary"
        size="small"
        text
        @click="router.push({ name: 'games' })"
      />

    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Button } from 'primevue';
import TitleCardFan from '../../components/TitleCardFan.vue';
import { preloadAllCards } from '../../composables/useCardAnimation';

import { useI18n } from '../../composables/useI18n';

const router = useRouter();
const { gamePicker } = useI18n();

onMounted(() => preloadAllCards());
</script>

<style scoped>
.titlescreen {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.title-panel {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 20px;
  padding: 48px 52px 36px;
  box-shadow: var(--panel-shadow);
  /* Fluid: 380px on wide screens, never wider than the viewport minus a 12px gutter. */
  min-width: min(380px, calc(100% - 24px));
  max-width: min(480px, calc(100% - 24px));
  margin-top: 60px;
}

@media (max-width: 480px) {
  .title-panel {
    padding: 40px 28px 32px;
  }
}

.title-block {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.suit-row {
  display: flex;
  gap: 10px;
  margin-bottom: 4px;
}

.suit-accent {
  font-size: 1rem;
  color: var(--gold-soft);
  opacity: 0.7;
}

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

.menu-button {
  width: 100%;
  letter-spacing: 0.08em;
}

</style>
