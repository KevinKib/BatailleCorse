<template>
  <img :src="url" v-show="valid" :width="width" :height="height" class="playing_card" ref="rootCard"/>
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
const rootCard = useTemplateRef("rootCard");

defineExpose({
  rootCard
});

const defaultHeight = 486.275;
const defaultWidth = 167.575;

const width = computed(() => props.size.value);
const height = computed(() => props.size.value * defaultHeight / defaultWidth / 2);

import { computed, toRefs, useTemplateRef } from 'vue';

const valid = computed(() => {
  return props.rank.value != "" && props.suit.value != "";
});

const filename = computed(() => {

  if (!valid.value || props.hidden.value == true) {
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

<style>

.playing_card {
  border: 1px solid black;
  border-radius: 6%;
  box-shadow: 3px 5px 0px rgba(0, 0, 0, 0.9), 4px 10px 24px rgba(0, 0, 0, 0.6);
}
</style>