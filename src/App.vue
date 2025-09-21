<template>
  <TitleScreen class="titlescreen"/>
  <!-- <Debug /> -->
</template>

<script setup>
import Debug from './view/debug/Debug.vue';
import TitleScreen from './view/alpha/TitleScreen.vue';

</script>

<style>
@import "tailwindcss";
@import "tailwindcss-primeui";

html, body, #app {
  width: 100%;
  height: 100%;
}

.titlescreen {
  width: 100%;
  height: 100%;
}
</style>