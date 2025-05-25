<template>

  <!-- <component :is="{...card}" :key="filename" v-if="valid" :width="width" :height="height"/> -->

  <!-- <img :src="`../../resources/cards/png/${filename}.png`"/> -->
  <!-- <img :src="`/src/resources/cards/png/card_1_diamond.png`"/> -->
  <img :src="`/src/resources/cards/png/${filename}.png`" v-if="valid"/>

  {{ props.rank }}
  {{ props.suit }}

</template>

<script setup lang="ts">

const nonReactiveProps = defineProps({
  size: {
    default: 50,
    type: Number
  },
  hidden: {
    default: false,
    type: Boolean,
  },
  rank: {
    default: "",
    type: String,
  },
  suit: {
    default: "",
    type: String,
  }
})

const props = toRefs(nonReactiveProps);

const defaultHeight = 486.275;
const defaultWidth = 167.575;

const width = computed(() => props.size.value);
const height = computed(() => props.size.value * defaultHeight / defaultWidth / 2);

import { computed, defineAsyncComponent, ref, toRefs, watchEffect } from 'vue';

const valid = computed(() => {
  return props.rank.value != "" && props.suit.value != "";
});

const card = ref();

watchEffect(() => {
  card.value = defineAsyncComponent(() => import(`../../resources/cards/svg/${filename.value}.svg`));
})

const filename = computed(() => {
  if (props.hidden.value == true) {
    return `card_back`;
  }

  return `card_${props.rank.value.toLocaleLowerCase()}_${props.suit.value.toLocaleLowerCase()}`;
});

// const card = computed(() => {
//   console.log("computed card");
//   return defineAsyncComponent(() => import(`../../resources/cards/${filename.value}.svg`));
// });

</script>