<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { Button } from 'primevue';
import { useI18n } from '../composables/useI18n';
import { format } from '../locales/format';
import type { HotkeyPanelApi, HotkeyRejection } from '../composables/useHotkeySettings';

// The default slot holds other settings (the language row), shown above the shortcuts; `title` then
// renames the gear and panel from "Keyboard shortcuts" to the general name.
const props = defineProps<{ settings: HotkeyPanelApi; labels: Record<string, string>; title?: string }>();
const ui = useI18n().hotkeys;

const open = ref(false);
const capturing = ref<string | null>(null);
const error = ref<HotkeyRejection | null>(null);

// The key press that sets a key is swallowed on the way down and on the way up, so it never
// reaches the game shortcuts (which listen to keyup on the document).
function onCaptureKeyDown(e: KeyboardEvent) {
  if (e.repeat) return;
  e.preventDefault();
  e.stopPropagation();
  const action = capturing.value;
  stopCapture();
  window.addEventListener('keyup', swallowKeyUp, { capture: true, once: true });
  if (!action || e.key === 'Escape') return;
  if (e.ctrlKey || e.metaKey || e.altKey) { error.value = 'reserved'; return; }
  const result = props.settings.setKey(action, e);
  error.value = result.ok ? null : result.reason;
}
function swallowKeyUp(e: KeyboardEvent) { e.stopPropagation(); }

function startCapture(action: string) {
  stopCapture();
  error.value = null;
  capturing.value = action;
  window.addEventListener('keydown', onCaptureKeyDown, true);
}
function stopCapture() {
  capturing.value = null;
  window.removeEventListener('keydown', onCaptureKeyDown, true);
}
function reset() {
  stopCapture();
  error.value = null;
  props.settings.reset();
}

watch(open, (isOpen) => { if (!isOpen) { stopCapture(); error.value = null; } });
onBeforeUnmount(() => {
  stopCapture();
  window.removeEventListener('keyup', swallowKeyUp, true);
});

const keyOf = (id: string) => {
  const key = props.settings.actions.find((a) => a.id === id)?.key.value ?? '';
  return key === ' ' ? ui.space : key.toUpperCase();
};
</script>

<template>
  <div class="hotkey-settings" @keydown.esc="open = false">
    <Button
      data-test="hotkeys-button"
      severity="secondary"
      variant="text"
      rounded
      icon="pi pi-cog"
      :aria-label="title ?? ui.open"
      :title="title ?? ui.open"
      :aria-expanded="open"
      aria-controls="hotkeys-panel"
      @click="open = !open" />
    <div v-if="open" id="hotkeys-panel" class="panel" data-test="hotkeys-panel" role="group" :aria-label="title ?? ui.title">
      <template v-if="$slots.default">
        <slot />
        <div class="divider" />
        <div class="section-heading" data-test="hotkeys-heading">{{ ui.title }}</div>
      </template>
      <Button
        v-if="settings.enabled"
        data-test="hotkeys-enabled"
        class="enabled-switch"
        severity="secondary"
        size="small"
        :icon="settings.enabled.value ? 'pi pi-check-square' : 'pi pi-stop'"
        :label="ui.enabled"
        :aria-pressed="settings.enabled.value"
        @click="settings.toggleEnabled?.()" />
      <template v-if="settings.enabled?.value ?? true">
        <div v-for="action in settings.actions" :key="action.id" class="row">
          <span class="action-name">{{ labels[action.id] }}</span>
          <kbd :data-test="`hotkey-${action.id}-key`" class="key">{{ keyOf(action.id) }}</kbd>
          <Button
            :data-test="`hotkey-${action.id}-change`"
            severity="secondary"
            size="small"
            variant="outlined"
            :label="capturing === action.id ? ui.listening : ui.change"
            :aria-label="format(ui.changeAria, { action: labels[action.id] })"
            @click="startCapture(action.id)" />
        </div>
        <p v-if="error" class="error" data-test="hotkeys-error" role="alert">{{ ui.errors[error] }}</p>
        <Button
          data-test="hotkeys-reset"
          severity="secondary"
          size="small"
          variant="text"
          icon="pi pi-refresh"
          :label="ui.reset"
          @click="reset" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.hotkey-settings { display: inline-flex; margin-left: var(--space-1); }
/* Fixed so it never changes the layout of the table (no scroll) and fits any width. */
.panel {
  position: fixed;
  top: 56px;
  left: 8px;
  z-index: 1400;
  width: min(340px, calc(100vw - 16px));
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-1);
  padding: var(--space-2);
  border-radius: 12px;
  background: rgba(20, 24, 22, 0.96);
  color: #fff;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.5);
}
.divider { height: 1px; margin: var(--space-1) 0; background: rgba(255, 255, 255, 0.15); }
.section-heading {
  padding: var(--space-1) var(--space-1) 0;
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.6);
}
.row { display: flex; align-items: center; gap: var(--space-1); }
.action-name { flex: 1 1 auto; }
.key {
  min-width: 2rem;
  padding: 2px 8px;
  text-align: center;
  border-radius: 6px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  font-family: inherit;
}
.error { margin: 0; font-size: 0.8rem; color: #ffb4a8; }
</style>
