<template>
  <RouterView />
</template>

<script setup>
import Debug from './view/debug/Debug.vue';
import GameScreen from './view/alpha/GameScreen.vue';
import StartGame from './view/alpha/StartGame.vue';

</script>

<style>
@import "tailwindcss";
@import "tailwindcss-primeui";
@import 'primeicons/primeicons.css';

.gabarito-font {
  font-family: "Gabarito", sans-serif;
  font-optical-sizing: auto;
  font-weight: 500;
  font-style: normal;
}

.bricolage-grotesque-font {
  font-family: "Bricolage Grotesque", sans-serif;
  font-optical-sizing: auto;
  font-weight: 200;
  font-style: normal;
  font-variation-settings:
    "wdth" 100;
}

html, body, #app {
  width: 100%;
  height: 100%;
  /* font-family: "Gabarito", sans-serif;
  font-optical-sizing: auto;
  font-weight: 400;
  font-style: normal; */
  font-family: "Bricolage Grotesque", sans-serif;
  font-optical-sizing: auto;
  font-weight: 500;
  font-style: normal;
  font-variation-settings: "wdth" 100;
}

h1              { font-size: 2em; margin: .67em 0 }
h2              { font-size: 1.5em; margin: .75em 0 }
h3              { font-size: 1.17em; margin: .83em 0 }
h5              { font-size: .83em; margin: 1.5em 0 }
h6              { font-size: .75em; margin: 1.67em 0 }
h1, h2, h3, h4,
h5, h6          { font-weight: bolder }

.titlescreen {
  width: 100%;
  height: 100%;
}
</style>