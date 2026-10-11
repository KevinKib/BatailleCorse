<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { Button } from 'primevue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type { HotkeyAction, HotkeyRejection, HotkeySettingsApi } from '../../composables/useHotkeySettings';

const props = defineProps<{ settings: HotkeySettingsApi }>();
const ui = useI18n().bullshitUi.hotkeys;

const open = ref(false);
const capturing = ref<HotkeyAction | null>(null);
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

function startCapture(action: HotkeyAction) {
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

const rows = [
  { action: 'discard' as const, label: ui.discard },
  { action: 'call' as const, label: ui.call },
];
const keyOf = (action: HotkeyAction) =>
  (action === 'discard' ? props.settings.discardKey.value : props.settings.callKey.value).toUpperCase();
</script>

<template>
  <div class="hotkey-settings" @keydown.esc="open = false">
    <Button
      data-test="hotkeys-button"
      severity="secondary"
      variant="text"
      rounded
      icon="pi pi-cog"
      :aria-label="ui.open"
      :title="ui.open"
      :aria-expanded="open"
      aria-controls="hotkeys-panel"
      @click="open = !open" />
    <div v-if="open" id="hotkeys-panel" class="panel" data-test="hotkeys-panel" role="group" :aria-label="ui.title">
      <Button
        data-test="hotkeys-enabled"
        class="enabled-switch"
        severity="secondary"
        size="small"
        :icon="settings.enabled.value ? 'pi pi-check-square' : 'pi pi-stop'"
        :label="ui.enabled"
        :aria-pressed="settings.enabled.value"
        @click="settings.toggleEnabled()" />
      <template v-if="settings.enabled.value">
        <div v-for="row in rows" :key="row.action" class="row">
          <span class="action-name">{{ row.label }}</span>
          <kbd :data-test="`hotkey-${row.action}-key`" class="key">{{ keyOf(row.action) }}</kbd>
          <Button
            :data-test="`hotkey-${row.action}-change`"
            severity="secondary"
            size="small"
            variant="outlined"
            :label="capturing === row.action ? ui.listening : ui.change"
            :aria-label="format(ui.changeAria, { action: row.label })"
            @click="startCapture(row.action)" />
        </div>
        <p class="note">{{ ui.cardsNote }}</p>
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
.note { margin: 0; font-size: 0.75rem; color: rgba(255, 255, 255, 0.6); }
.error { margin: 0; font-size: 0.8rem; color: #ffb4a8; }
</style>
