<script setup lang="ts">
import TitleCardFan from './TitleCardFan.vue';

// The shared frame of the Game home and the join form: card fan, suits, game title, then the
// slotted content. The field and button styles of that content live here (`:deep`).
withDefaults(defineProps<{ title: string; subtitle?: string; tag?: 'div' | 'form' }>(), {
  subtitle: undefined,
  tag: 'div',
});
const emit = defineEmits<{ submit: [] }>();
</script>

<template>
  <div class="titlescreen">
    <component :is="tag" class="title-panel" data-test="start-panel" @submit.prevent="emit('submit')">
      <TitleCardFan />
      <div class="title-block">
        <div class="suit-row" aria-hidden="true">
          <span class="suit-accent">♠</span>
          <span class="suit-accent">♥</span>
          <span class="suit-accent">♦</span>
          <span class="suit-accent">♣</span>
        </div>
        <h1 class="game-title">{{ title }}</h1>
        <h2 v-if="subtitle" class="subtitle">{{ subtitle }}</h2>
      </div>
      <div class="panel-divider" />
      <slot />
    </component>
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
  gap: 24px;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 20px;
  padding: 44px 44px 30px;
  box-shadow: var(--panel-shadow);
  width: 100%;
  max-width: 480px;
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
.subtitle {
  margin: 4px 0 0;
  font-family: var(--font-title);
  font-size: 0.875rem;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}
.panel-divider {
  width: 100%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.15), transparent);
}

:deep(.field-group) { width: 100%; display: flex; flex-direction: column; gap: 8px; }
:deep(.field-label) {
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
  padding: 0;
}
:deep(.text-input) { width: 100%; }
:deep(.text-input.p-inputtext) {
  min-height: 44px;
  background: rgba(0, 0, 0, 0.4);
  border-color: rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
}
:deep(.text-input.p-inputtext:focus),
:deep(.text-input.p-inputtext:focus-visible) {
  border-color: rgba(232, 201, 109, 0.6);
  box-shadow: 0 0 0 2px rgba(232, 201, 109, 0.2);
  outline: none;
}
:deep(.text-input.p-inputtext::placeholder) { color: rgba(255, 255, 255, 0.3); }
:deep(.notice),
:deep(.error) {
  width: 100%;
  margin: 0;
  padding: 8px 12px;
  box-sizing: border-box;
  font-size: 0.875rem;
  border-radius: 8px;
}
:deep(.notice) {
  color: rgba(255, 255, 255, 0.85);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.2);
}
:deep(.error) {
  color: rgb(var(--accent-negative-rgb));
  background: rgba(var(--accent-negative-rgb), 0.1);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.4);
}
:deep(.start-button.p-button) { width: 100%; min-height: 48px; letter-spacing: 0.08em; }
/* 24 px panel gap minus 8 px = 16 px between the last action and Back. */
:deep(.back-button.p-button) { min-height: 44px; margin-top: -8px; }

@media (max-width: 480px) {
  .titlescreen { padding: 88px 12px 16px; }
  .title-panel { padding: 32px 20px 24px; }
  .game-title { font-size: 2.1rem; }
}
</style>
