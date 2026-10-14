<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';

// The sound button of the top bar: a round chip like its neighbours, named "Sound effects", pressed when the
// sound is on (the state is also its tooltip and its icon, never the colour alone).
defineProps<{ enabled: boolean }>();
defineEmits<{ toggle: [] }>();
const ui = useI18n().koboUi.sound;
</script>

<template>
  <button type="button" class="sound-toggle" data-test="sound-toggle" :aria-label="ui.label"
          :aria-pressed="enabled ? 'true' : 'false'" :title="enabled ? ui.on : ui.off" @click="$emit('toggle')">
    <i class="pi" :class="enabled ? 'pi-volume-up' : 'pi-volume-off'" aria-hidden="true"></i>
  </button>
</template>

<style scoped>
.sound-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  box-sizing: border-box;
  width: var(--hud-chip-h);
  height: var(--hud-chip-h);
  padding: 0;
  font-size: 0.8rem;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  cursor: pointer;
}
/* The chip stays as small as its neighbours; its touch target is a 44 px square around it. */
.sound-toggle { position: relative; }
.sound-toggle::after { content: ''; position: absolute; inset: -8px; }
.sound-toggle[aria-pressed='true'] { color: var(--gold); }
.sound-toggle:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
</style>
