<script setup lang="ts">
import { computed } from 'vue';
import NinetyNineLives from './NinetyNineLives.vue';
import { useI18n } from '../../composables/useI18n';
import { ordinal } from '../../locales/format';
import type { NinetyNinePlayer } from '../../model/ninetynine/NinetyNineState';

// The final places, as the server ranks them (`place`: shared places in quick mode, 1st, 1st, 3rd...), with the lives
// each player kept. Names are user input: only ever rendered as text.
const props = defineProps<{
  players: NinetyNinePlayer[];
  nameOf: (seat: number) => string;
  mySeat: number;
  maxLives: number;
}>();
const ui = useI18n().ninetyNineUi;

const rows = computed(() => [...props.players]
  .sort((a, b) => (a.place ?? Infinity) - (b.place ?? Infinity) || a.seat - b.seat)
  .map(p => ({
    player: p,
    place: p.place === null ? '' : ordinal(p.place, ui.ordinal),
    name: p.seat === props.mySeat ? ui.you : props.nameOf(p.seat),
    first: p.place === 1,
  })));
</script>

<template>
  <ol class="places" :aria-label="ui.end.places" data-test="places">
    <li v-for="row in rows" :key="row.player.seat" class="place-row"
        :class="{ 'place-row--me': row.player.seat === mySeat, 'place-row--first': row.first }" data-test="place-row">
      <strong class="place" data-test="place">{{ row.place }}</strong>
      <span class="place-name" data-test="place-name" :title="row.name">{{ row.name }}</span>
      <NinetyNineLives :lives="row.player.lives" :max="maxLives" />
    </li>
  </ol>
</template>

<style scoped>
.places { width: 100%; margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: var(--space-1); }
.place-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-2);
  min-height: 34px;
  padding: 0 var(--space-3);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  font-size: 0.875rem;
}
.place-row--first { color: var(--gold); }
.place-row--me { background: rgba(245, 200, 66, 0.18); }
.place { text-align: left; font-variant-numeric: tabular-nums; }
.place-name { min-width: 0; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
