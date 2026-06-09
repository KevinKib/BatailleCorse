<template>
  <div class="counter">
    <p class="count">{{ props.count }}</p>
  </div>
</template>

<script setup lang="ts">
import { toRefs } from 'vue';

const nonReactiveProps = defineProps({
  count: {
    default: 0,
    type: Number,
    required: true,
  }
});

const props = toRefs(nonReactiveProps);

</script>

<style>

.counter {
  height: 32px;
  width: 32px;
  /* Poker-chip token: dark felt disc, thin gold rim, light text. The
     clip-path is dropped so the drop shadow can render (clip-path would clip
     it); border-radius:50% keeps the circle. */
  background-color: rgba(0, 0, 0, 0.6);
  color: rgba(255, 255, 255, 0.92);
  border: 2px solid rgba(var(--accent-active-rgb), 0.55);
  border-radius: 50%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.5);
}

.count {
  width: fit-content;
  font-weight: bold;
  position: relative;
  left: 50%;
  top: 50%;
  -webkit-transform: translate(-50%, -50%);
  transform: translate(-50%, -50%);
}
</style>