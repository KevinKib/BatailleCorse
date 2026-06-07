<template>
  <button
    type="button"
    class="rules-toggle"
    :aria-expanded="isOpen"
    aria-controls="rules-panel"
    data-cy="rules-toggle"
    @click="toggle"
  >
    <i class="pi pi-info-circle" />
    <span>{{ messages.rules.toggleLabel }}</span>
  </button>

  <div
    v-show="isOpen"
    id="rules-panel"
    class="rules-panel"
    role="dialog"
    :aria-label="messages.rules.panelTitle"
    data-cy="rules-panel"
  >
    <div class="rules-panel__header">
      <h2 class="rules-panel__title">{{ messages.rules.panelTitle }}</h2>
      <button
        type="button"
        class="rules-panel__close"
        :aria-label="messages.rules.closeLabel"
        data-cy="rules-close"
        @click="close"
      >
        <i class="pi pi-times" />
      </button>
    </div>

    <div class="rules-panel__body">
      <section
        v-for="section in messages.rules.sections"
        :key="section.title"
        class="rules-section"
      >
        <h3 class="rules-section__title">{{ section.title }}</h3>
        <p
          v-for="line in section.body"
          :key="line"
          class="rules-section__line"
        >{{ line }}</p>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount } from 'vue';
import { useI18n } from '../composables/useI18n';
import { useDisclosure } from '../composables/useDisclosure';

const messages = useI18n();
const { isOpen, close, toggle } = useDisclosure();

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) close();
}

onMounted(() => document.addEventListener('keydown', handleKeydown));
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown));
</script>

<style scoped>
/* Toggle chip — top-right, themed to match the felt table. Sits above the
   cards (z 1000/1001) but below the waiting/end overlays (z 2000). */
.rules-toggle {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 1500;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 999px;
  padding: 6px 14px;
  cursor: pointer;
}

.rules-toggle:hover {
  background: rgba(0, 0, 0, 0.6);
}

.rules-panel {
  position: absolute;
  top: 56px;
  right: 16px;
  z-index: 1500;
  width: min(360px, calc(100vw - 32px));
  max-height: 70vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: rgba(0, 0, 0, 0.82);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}

.rules-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.rules-panel__title {
  font-family: "Gabarito", sans-serif;
  font-size: 1.1rem;
  font-weight: 700;
  color: #f5c842;
  margin: 0;
}

.rules-panel__close {
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  font-size: 1rem;
  cursor: pointer;
  padding: 4px;
  line-height: 1;
}

.rules-panel__close:hover {
  color: #fff;
}

.rules-panel__body {
  overflow-y: auto;
  padding: 8px 16px 16px;
}

.rules-section {
  margin-top: 14px;
}

.rules-section__title {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.55);
  margin: 0 0 4px;
}

.rules-section__line {
  font-size: 0.9rem;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.88);
  margin: 0 0 4px;
}
</style>
