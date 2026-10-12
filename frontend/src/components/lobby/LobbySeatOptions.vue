<script setup lang="ts">
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { optionGroupLabel, optionLabel } from '../../model/lobby/optionLabel';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

// Options of one computer seat (for example a difficulty tier), editable by the host. Renders nothing for
// a game whose bots declare none.
const props = defineProps<{
  seat: number;
  seatName: string;
  descriptors: OptionDescriptor[];
  values: Record<string, string>;
}>();
const emit = defineEmits<{ change: [seat: number, key: string, value: string] }>();

const messages = useI18n();
const ui = messages.lobby;
const valueOf = (d: OptionDescriptor) => props.values[d.key] ?? d.defaultValue;
const accessibleName = (d: OptionDescriptor) =>
  format(ui.seatOptionLabel, { option: optionGroupLabel(messages, d), name: props.seatName });

function onChange(d: OptionDescriptor, event: Event) {
  emit('change', props.seat, d.key, (event.target as HTMLSelectElement).value);
}
</script>

<template>
  <select
    v-for="d in descriptors"
    :key="d.key"
    class="seat-option"
    :data-test="`seat-option-${seat}-${d.key}`"
    :aria-label="accessibleName(d)"
    :value="valueOf(d)"
    @change="onChange(d, $event)">
    <option v-for="v in d.values" :key="v" :value="v" :selected="valueOf(d) === v">
      {{ optionLabel(messages, d, v, values) }}
    </option>
  </select>
</template>

<style scoped>
.seat-option {
  flex: none;
  min-height: 44px;
  max-width: 7rem;
  padding: 0 8px;
  font-family: var(--font-ui);
  font-size: 0.75rem;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
}
</style>
