<script lang="ts">
export type { MotionRect, MotionTarget } from '../table/motionTarget';
</script>

<script setup lang="ts">
import { ref } from 'vue';
import CardMotionLayer from '../table/CardMotionLayer.vue';
import type { MotionTarget } from '../table/motionTarget';
import type { Movement, MotionAnchor } from '../../model/kobo/motion';
import { TEMPO } from '../../model/kobo/tempo';

// Kobo's travelling cards: the shared layer (`components/table/CardMotionLayer.vue`) with Kobo's places and the ring
// durations of Kobo's tempo. Everything it does is the shared layer's.
defineProps<{ resolve: (anchor: MotionAnchor) => MotionTarget | null }>();

const layer = ref<{ play: (movements: Movement[], options?: { marksOnly?: boolean; startInMs?: number }) => void } | null>(null);

function play(movements: Movement[], options: { marksOnly?: boolean; startInMs?: number } = {}) {
  layer.value?.play(movements, options);
}

defineExpose({ play });
</script>

<template>
  <CardMotionLayer ref="layer" :resolve="resolve" :mark-ms="TEMPO.markMs" :mark-reduced-ms="TEMPO.markReducedMs"
                   arrow-id="kobo-arrow-head" />
</template>
