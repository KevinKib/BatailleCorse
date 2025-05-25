<template>
  
  <!-- <CardSvg :width="width" :height="height"/> -->

  <component :is="card" :width="width" :height="height"/>
</template>

<script setup lang="ts">

const props = defineProps({
  size: {
    default: 50,
    type: Number
  },
  name: {
    default: "back",
    type: String,
  }
})

const defaultHeight = 486.275;
const defaultWidth = 167.575;

const width = computed(() => props.size);
const height = computed(() => props.size * defaultHeight / defaultWidth / 2);

import { computed, defineAsyncComponent } from 'vue';

const card = computed(() => {
  return defineAsyncComponent(() => import(`../../resources/cards/${props.name}.svg`));
});

</script>