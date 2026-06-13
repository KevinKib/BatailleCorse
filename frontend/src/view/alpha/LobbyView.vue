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


    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Button } from 'primevue';
import TitleCardFan from '../../components/TitleCardFan.vue';
import { preloadAllCards } from '../../composables/useCardAnimation';

const router = useRouter();

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
  min-width: 380px;
  max-width: 480px;
  margin-top: 60px;
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
  font-family: "Gabarito", sans-serif;
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
  filter: drop-shadow(0 2px 10px rgba(200, 134, 10, 0.5));
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
