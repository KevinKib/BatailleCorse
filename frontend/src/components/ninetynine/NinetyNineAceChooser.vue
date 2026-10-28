<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue';
import { useI18n } from '../../composables/useI18n';

// The Ace's two values, after the Ace is tapped: +1 and +11. A value the server does not allow is disabled and struck
// through (the server says which values are allowed; nothing is computed here). Escape or Cancel closes it.
defineProps<{
  /** The Ace values the server allows. */
  values: number[];
}>();
const emit = defineEmits<{ choose: [value: number]; cancel: [] }>();
const ui = useI18n().ninetyNineUi;
const root = ref<HTMLElement | null>(null);

// The keyboard lands on the first value that can be chosen.
onMounted(async () => {
  await nextTick();
  root.value?.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus();
});
</script>

<template>
  <div ref="root" class="ace" role="group" :aria-label="ui.ace.group" data-test="ace-chooser" @keydown.esc.stop="emit('cancel')">
    <span class="ace-title">{{ ui.ace.title }}</span>
    <div class="ace-buttons">
      <button v-for="v in [1, 11]" :key="v" type="button" class="ace-button" :class="{ 'ace-button--off': !values.includes(v) }"
              :data-test="`ace-${v}`" :aria-label="v === 1 ? ui.ace.oneLabel : ui.ace.elevenLabel"
              :disabled="!values.includes(v)" @click="emit('choose', v)">{{ v === 1 ? ui.ace.one : ui.ace.eleven }}</button>
      <button type="button" class="ace-cancel" data-test="ace-cancel" @click="emit('cancel')">{{ ui.ace.cancel }}</button>
    </div>
  </div>
</template>

<style scoped>
.ace {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2);
  border-radius: 10px;
  background: #141413;
  border: 1px solid rgba(245, 200, 66, 0.7);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.6);
}
.ace-title { font-size: 0.75rem; color: rgba(255, 255, 255, 0.75); }
.ace-buttons { display: flex; gap: var(--space-2); }
.ace-button, .ace-cancel {
  min-height: 44px;
  min-width: 44px;
  padding: 0 var(--space-3);
  border-radius: 6px;
  font: inherit;
  font-size: 0.875rem;
  cursor: pointer;
}
.ace-button { border: 0; background: var(--gold); color: #141413; font-weight: 700; }
.ace-button--off { border: 1px solid rgba(255, 255, 255, 0.15); background: transparent; color: rgba(255, 255, 255, 0.45); text-decoration: line-through; cursor: default; }
.ace-cancel { border: 1px solid rgba(255, 255, 255, 0.25); background: transparent; color: #fff; }
.ace-button:focus-visible, .ace-cancel:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }
</style>
