<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';

// The "Private game" switch of a lobby: on, the game is hidden from the public list and only people with the invite
// link can join. The host flips it, everybody else sees it disabled (guests also get a read-only chip next to the
// invite link). It owns no state: the lobby view is the source of truth and the switch is drawn from it.
defineProps<{
  modelValue: boolean;
  editable: boolean;
}>();
const emit = defineEmits<{ change: [value: boolean] }>();

const ui = useI18n().lobby;
const hintId = 'lobby-private-hint';
</script>

<template>
  <div class="private-option">
    <button
      type="button"
      role="switch"
      class="private-switch"
      data-test="private-switch"
      :aria-checked="modelValue ? 'true' : 'false'"
      :aria-describedby="hintId"
      :disabled="!editable"
      @click="emit('change', !modelValue)">
      <span class="private-label">{{ ui.privateGame }}</span>
      <span class="track" :class="{ 'track--on': modelValue }" aria-hidden="true"><span class="thumb"></span></span>
    </button>
    <p :id="hintId" class="private-hint" data-test="private-hint">{{ ui.privateHint }}</p>
  </div>
</template>

<style scoped>
.private-option { width: 100%; display: flex; flex-direction: column; gap: 4px; }
.private-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 44px;
  padding: 4px 12px;
  box-sizing: border-box;
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  cursor: pointer;
}
.private-switch:disabled { cursor: default; color: rgba(255, 255, 255, 0.6); }
.private-switch:focus-visible { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 1px; }
.track {
  flex: none;
  width: 40px;
  height: 22px;
  padding: 2px;
  box-sizing: border-box;
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
}
.track--on { background: var(--gold); justify-content: flex-end; }
.thumb { width: 18px; height: 18px; border-radius: 50%; background: #fff; }
.private-hint { margin: 0; font-size: 0.8125rem; line-height: 1.2rem; color: rgba(255, 255, 255, 0.6); }
</style>
