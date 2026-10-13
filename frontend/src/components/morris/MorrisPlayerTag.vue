<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';

// One player of the Three Men's Morris table: the name (text only, it is user input), the colour in words
// next to a chip, the pieces still to place as small discs, and a glow while it is that player's turn.
const props = defineProps<{
  name: string;
  colour: 'white' | 'black';
  piecesToPlace: number;
  active: boolean;
}>();

const ui = useI18n().threeMenMorrisUi;
const colourLabel = computed(() => (props.colour === 'white' ? ui.white : ui.black));
const label = computed(() => format(ui.table.colourTag, { name: props.name, colour: colourLabel.value }));
const reserveLabel = computed(() => plural(ui.table.piecesLeft, props.piecesToPlace));
</script>

<template>
  <div
    class="morris-tag"
    :class="{ 'morris-tag--active': active }"
    data-test="player-tag"
    :data-colour="colour"
    :data-active="active ? 'true' : 'false'"
    role="group"
    :aria-label="label">
    <span class="tag-chip" data-test="tag-chip" :data-colour="colour" aria-hidden="true"></span>
    <span class="tag-name" data-test="tag-name">{{ name }}</span>
    <span class="tag-colour" data-test="tag-colour">{{ colourLabel }}</span>
    <span class="tag-reserve" data-test="tag-reserve" role="img" :aria-label="reserveLabel">
      <span
        v-for="n in piecesToPlace"
        :key="n"
        class="tag-chip tag-chip--small"
        data-test="reserve-piece"
        :data-colour="colour"></span>
    </span>
  </div>
</template>

<style scoped>
.morris-tag {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  max-width: 100%;
  box-sizing: border-box;
  min-height: 36px;
  padding: var(--space-1) 14px;
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.morris-tag--active {
  color: #ffffff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}

.tag-chip {
  flex: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
}
.tag-chip[data-colour='white'] {
  background: radial-gradient(circle at 35% 30%, #fffdf6 0%, #f4ecd8 55%, #bfae85 100%);
}
.tag-chip[data-colour='black'] {
  background: radial-gradient(circle at 35% 30%, #4a433d 0%, #231f1c 60%, #0d0b0a 100%);
  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.4);
}
.tag-chip--small { width: 10px; height: 10px; }

.tag-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
  font-size: 0.85rem;
  letter-spacing: 0.04em;
}
.tag-colour {
  flex: none;
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.6);
}
.tag-reserve {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 10px;
}
</style>
