<template>
  <div class="app-root">
    <RouterView v-slot="{ Component, route }">
      <Transition :css="false" @enter="onEnter" @leave="onLeave">
        <component :is="Component" :key="route.path" />
      </Transition>
    </RouterView>
  </div>
</template>

<script setup lang="ts">
const TRANSITION_MS = 800;

function onEnter(el: Element, done: () => void) {
  const div = el as HTMLElement;
  div.style.position = 'absolute';
  div.style.inset = '0';
  div.style.zIndex = '1';
  div.style.opacity = '0';
  requestAnimationFrame(() => requestAnimationFrame(() => {
    div.style.transition = `opacity ${TRANSITION_MS}ms ease`;
    div.style.opacity = '1';
    div.addEventListener('transitionend', () => {
      div.style.cssText = '';
      done();
    }, { once: true });
  }));
}

function onLeave(el: Element, done: () => void) {
  const div = el as HTMLElement;
  div.style.position = 'absolute';
  div.style.inset = '0';
  div.style.zIndex = '0';
  requestAnimationFrame(() => requestAnimationFrame(() => {
    div.style.transition = `opacity ${TRANSITION_MS}ms ease`;
    div.style.opacity = '0';
    div.addEventListener('transitionend', () => done(), { once: true });
  }));
}
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

.app-root {
  position: relative;
  width: 100%;
  height: 100%;
  background: #07160d;
}

.titlescreen {
  width: 100%;
  height: 100%;
}
</style>
