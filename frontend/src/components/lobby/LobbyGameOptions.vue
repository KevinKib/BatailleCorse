<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { optionGroupLabel, optionLabel } from '../../model/lobby/optionLabel';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

// The game options a lobby declares. The host edits each one as a radio group; everybody else reads
// it as a labelled value. Texts come from the locales through the descriptor label key.
const props = defineProps<{
  descriptors: OptionDescriptor[];
  options: Record<string, string>;
  editable: boolean;
}>();
const emit = defineEmits<{ 'set-option': [key: string, value: string] }>();

const messages = useI18n();
const valueOf = (d: OptionDescriptor) => props.options[d.key] ?? d.defaultValue;
const all = computed(() => Object.fromEntries(props.descriptors.map(d => [d.key, valueOf(d)])));
const groupLabel = (d: OptionDescriptor) => optionGroupLabel(messages, d);
const valueLabel = (d: OptionDescriptor, value: string) => optionLabel(messages, d, value, all.value);

// The lobby is the only source of truth: ask for the value, then draw the radios back on the value the lobby holds.
// An accepted change comes back as the next lobby view and moves the radio; a refused one leaves it where it was,
// instead of showing a choice the server did not take (Vue would not repaint, the checked prop being unchanged).
function pick(event: Event, d: OptionDescriptor, value: string) {
  emit('set-option', d.key, value);
  const group = (event.target as HTMLInputElement).closest('fieldset');
  group?.querySelectorAll<HTMLInputElement>('input[type="radio"]').forEach((input) => {
    input.checked = input.value === valueOf(d);
  });
}
</script>

<template>
  <div class="options" data-test="game-options">
    <template v-for="d in descriptors" :key="d.key">
      <fieldset v-if="editable" class="option option--edit" :data-test="`option-${d.key}`">
        <legend class="field-label">{{ groupLabel(d) }}</legend>
        <div class="choices">
          <label v-for="v in d.values" :key="v" class="choice" :class="{ 'choice--on': valueOf(d) === v }">
            <input
              type="radio"
              :name="`option-${d.key}`"
              :value="v"
              :checked="valueOf(d) === v"
              :data-test="`option-${d.key}-${v}`"
              @change="pick($event, d, v)" />
            <span>{{ valueLabel(d, v) }}</span>
          </label>
        </div>
      </fieldset>
      <div v-else class="option option--read" :data-test="`option-${d.key}`">
        <span class="field-label">{{ groupLabel(d) }}</span>
        <span class="option-value">{{ valueLabel(d, valueOf(d)) }}</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.options { width: 100%; display: flex; flex-direction: column; gap: 8px; }
.field-label {
  font-size: 0.875rem;
  font-weight: 700;
  line-height: 1rem;
  color: rgba(255, 255, 255, 0.6);
  margin: 0;
  padding: 0;
}
.option { min-width: 0; margin: 0; padding: 0; border: 0; }
.option--read {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 44px;
  padding: 2px 12px;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
}
.option-value { color: var(--gold); font-weight: 700; font-size: 0.875rem; line-height: 1.25rem; }
/* A block, not a flex column: a legend is not a flex item, so a gap would not apply to it. Its margin does. */
.option--edit { display: block; }
.option--edit .field-label { display: block; margin-bottom: var(--space-2); }
.choices { display: flex; gap: 8px; }
.choice {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px 8px;
  box-sizing: border-box;
  text-align: center;
  font-size: 0.8125rem;
  line-height: 1.1rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.25);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  cursor: pointer;
}
.choice--on { color: var(--gold); border-color: var(--gold); background: rgba(232, 201, 109, 0.12); }
/* The radio stays in the accessibility tree and keeps the focus ring, but is drawn by its label. */
.choice input { position: absolute; inset: 0; width: 100%; height: 100%; margin: 0; opacity: 0; cursor: pointer; }
.choice:has(input:focus-visible) { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 1px; }
</style>
