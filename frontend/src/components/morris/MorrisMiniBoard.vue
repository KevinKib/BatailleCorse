<script setup lang="ts">
import { computed } from 'vue';
import { GEOMETRIES } from '../../model/threemenmorris/geometries';
import type { MorrisVariantKey } from '../../model/threemenmorris/MorrisState';

// A small, decorative drawing of a variant's board for the lobby cards: its points and the segments between them.
const props = defineProps<{ variant: MorrisVariantKey }>();
const geometry = computed(() => GEOMETRIES[props.variant]);
const end = (segment: readonly number[], last: boolean) =>
  geometry.value.points[last ? segment[segment.length - 1] : segment[0]];
</script>

<template>
  <svg class="mini" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
    <line
      v-for="segment in geometry.segments"
      :key="segment.join('-')"
      :x1="end(segment, false).x"
      :y1="end(segment, false).y"
      :x2="end(segment, true).x"
      :y2="end(segment, true).y" />
    <circle
      v-for="(p, n) in geometry.points"
      :key="n"
      :cx="p.x"
      :cy="p.y"
      :r="geometry.crossings.includes(n) ? 3.5 : 5" />
  </svg>
</template>

<style scoped>
.mini { width: 56px; height: 56px; flex: none; }
.mini line { stroke: currentColor; stroke-opacity: 0.7; stroke-width: 4; stroke-linecap: round; }
.mini circle { fill: currentColor; }
</style>
