<template>
  <div class="app-root">
    <!-- Persistent felt background, shared by the title screens. It lives
         outside the RouterView so route transitions only fade the routed
         content (the card panel), not the background behind it. -->
    <div class="app-background" />
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
  div.style.willChange = 'opacity';
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
  div.style.willChange = 'opacity';
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

:root {
  /* Semantic accent roles (RGB channel triplets so alpha stays controllable).
     active = attention / "your move" (brand gold); positive = go / gain;
     negative = loss. Single source for the game-screen cue colours. */
  --accent-active-rgb: 245, 200, 66;
  --accent-positive-rgb: 74, 222, 128;
  --accent-negative-rgb: 248, 113, 113;
}

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
  /* Prevent double-tap zoom globally on iOS Safari (user-scalable=no is ignored
     since iOS 10; touch-action: manipulation is the reliable cross-platform fix). */
  touch-action: manipulation;
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
  /* Own stacking context so the background layer (z-index:-1) sits behind the
     routed views without escaping app-root. */
  isolation: isolate;
}

/* Shared title-screen felt, persistent across route transitions. The game
   screen paints its own opaque background on top, so this only shows on the
   title/lobby/setup screens and during transitions between them. */
.app-background {
  position: absolute;
  inset: 0;
  z-index: -1;
  background:
    radial-gradient(ellipse at 50% 50%, transparent 15%, rgba(0, 0, 0, 0.8) 100%),
    radial-gradient(ellipse at 50% 40%, #1e5c30 0%, #0d2e18 50%, #07160d 100%);
}

.titlescreen {
  width: 100%;
  height: 100%;
}
</style>
