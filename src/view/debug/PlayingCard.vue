<template>

  <!-- <component :is="{...card}" :key="filename" v-if="valid" :width="width" :height="height"/> -->

  <!-- <img :src="`../../resources/cards/png/${filename}.png`"/> -->
  <!-- <img :src="`/src/resources/cards/png/card_1_diamond.png`"/> -->
  <!-- <img :src="`/src/resources/cards/png/${filename}.png`" v-if="valid"/> -->
  <img :src="url" v-if="valid"/>

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

// const card = ref();

// watchEffect(() => {
//   card.value = defineAsyncComponent(() => import(`../../resources/cards/svg/${filename.value}.svg`));
// })

const filename = computed(() => {
  if (props.hidden.value == true) {
    return `card_back`;
  }

  return `card_${props.rank.value.toLocaleLowerCase()}_${props.suit.value.toLocaleLowerCase()}`;
});

const images = import.meta.glob('/src/resources/cards/png/*.png', {
  eager: true,
  query: 'url',
});

const cache = new Map();

interface URL {
  default: string,
}

for (const [path, url] of (Object.entries(images) as [string, URL][])) {
  const img = new Image();
  img.src = url.default;
  cache.set(path, img);
}

const url = computed(() => {
  const imageUrl : URL = images[`/src/resources/cards/png/${filename.value}.png`] as URL;

  return imageUrl.default;
});

</script>