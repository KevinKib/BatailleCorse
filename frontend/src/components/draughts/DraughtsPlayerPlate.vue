<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';

// One player of the draughts table: a pill with the colour chip, the name (text only, it is user input), the colour
// in words and what the player has left: men, kings (each left out at zero) and the pieces taken so far. The pill
// glows while it is that player's turn.
const props = defineProps<{
  name: string;
  colour: 'white' | 'black';
  active: boolean;
  men: number;
  kings: number;
  captured: number;
}>();

const ui = useI18n().draughtsUi;
const colourLabel = computed(() => (props.colour === 'white' ? ui.white : ui.black));
const counts = computed(() => [
  ...(props.men > 0 ? [plural(ui.table.men, props.men)] : []),
  ...(props.kings > 0 ? [plural(ui.table.kings, props.kings)] : []),
  format(ui.table.took, { n: props.captured }),
].join(' · '));
const label = computed(() => format(ui.table.plateLabel, { name: props.name, colour: colourLabel.value, counts: counts.value }));
</script>

<template>
  <div
    class="plate"
    :class="{ 'plate--active': active }"
    data-test="plate"
    :data-colour="colour"
    :data-active="active ? 'true' : 'false'"
    role="group"
    :aria-label="label">
    <span class="plate-dot" aria-hidden="true"></span>
    <span class="plate-chip" data-test="plate-chip" :data-colour="colour" aria-hidden="true"></span>
    <span class="plate-name" data-test="plate-name">{{ name }}</span>
    <span class="plate-colour" data-test="plate-colour">{{ colourLabel }}</span>
    <span class="plate-counts" data-test="plate-counts">{{ counts }}</span>
  </div>
</template>

<style scoped>
.plate {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--space-1) var(--space-2);
  max-width: 100%;
  box-sizing: border-box;
  min-height: 36px;
  padding: var(--space-1) 14px;
  border-radius: 18px;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 0.875rem;
}
.plate--active {
  color: #ffffff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}
.plate-dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
}
.plate--active .plate-dot { background: var(--gold); }
.plate-chip {
  flex: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
}
.plate-chip[data-colour='white'] {
  background: radial-gradient(circle at 35% 30%, #fffdf6 0%, #f4ecd8 55%, #bfae85 100%);
}
.plate-chip[data-colour='black'] {
  background: radial-gradient(circle at 35% 30%, #4a433d 0%, #231f1c 60%, #0d0b0a 100%);
  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.4);
}
.plate-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}
.plate-colour { flex: none; color: rgba(255, 255, 255, 0.7); }
.plate-counts {
  flex: none;
  color: rgba(255, 255, 255, 0.8);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
