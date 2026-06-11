<template>
  <div class="app-root">
    <!-- Persistent felt background, shared by the title screens. It lives
         outside the RouterView so route transitions only fade the routed
         content (the card panel), not the background behind it. The oversized
         suit watermarks bleed off the corners; the game screen paints its own
         opaque felt on top, so they only ever show on the title screens. -->
    <div class="app-background">
      <span class="felt-watermark felt-watermark--tl">♠</span>
      <span class="felt-watermark felt-watermark--tr">♥</span>
      <span class="felt-watermark felt-watermark--bl">♦</span>
      <span class="felt-watermark felt-watermark--br">♣</span>
    </div>
    <RouterView v-slot="{ Component, route }">
      <Transition :css="false" @enter="onEnter" @leave="onLeave">
        <component :is="Component" :key="route.path" />
      </Transition>
    </RouterView>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';

const TRANSITION_MS = 800;

// iOS Safari ignores CSS touch-action on some elements; a JS guard is the
// only reliable fallback. passive: false is required for preventDefault to work.
// We also check position: a zoom double-tap lands in roughly the same spot,
// whereas intentional consecutive taps (Send → Slap) are usually far apart.
let lastTap = 0;
let lastTapX = 0;
let lastTapY = 0;
function blockDoubleTapZoom(e: TouchEvent) {
  const now = Date.now();
  const t = e.touches[0];
  const dx = t.clientX - lastTapX;
  const dy = t.clientY - lastTapY;
  const near = dx * dx + dy * dy < 30 * 30; // 30 px radius
  if (e.touches.length === 1 && now - lastTap < 350 && near) {
    e.preventDefault();
  }
  lastTap = now;
  lastTapX = t.clientX;
  lastTapY = t.clientY;
}
onMounted(() => {
  if (navigator.maxTouchPoints > 0)
    document.addEventListener('touchstart', blockDoubleTapZoom, { passive: false });
});
onUnmounted(() => document.removeEventListener('touchstart', blockDoubleTapZoom));

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

  /* Brand golds, promoted from the hard-coded values they used to be scattered
     across the title and overlay panels. */
  --gold: #f5c842;
  --gold-deep: #c8860a;
  --gold-soft: #e8c96d;

  /* Felt table palette: bright centre fading to the near-black rim. */
  --felt-center: #1e5c30;
  --felt-mid: #0d2e18;
  --felt-edge: #07160d;

  /* Fabric grain. An inline fractalNoise SVG (zero asset files) laid over the
     felt gradient at low opacity to kill the "smooth digital" look. */
  --felt-noise: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='180' height='180'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");

  /* Glass-felt panel look, referenced by every panel (title / waiting / end /
     disconnect) so they stay visually identical from one place. The gold top
     sheen reads as "lit from above". */
  --panel-bg: linear-gradient(180deg, rgba(14, 30, 20, 0.74) 0%, rgba(0, 0, 0, 0.66) 100%);
  --panel-border: rgba(255, 255, 255, 0.12);
  --panel-shadow:
    0 10px 60px rgba(0, 0, 0, 0.72),
    inset 0 1px 0 rgba(var(--accent-active-rgb), 0.14);

  /* Two-layer card shadow: a tight contact shadow plus a soft ambient one, so
     cards sit ON the felt instead of floating on a hard offset block. Shared by
     the static cards and the in-flight ghost/slap clones so they always match. */
  --card-shadow: 0 2px 3px rgba(0, 0, 0, 0.55), 0 8px 22px rgba(0, 0, 0, 0.5);
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

/* Prevent double-tap zoom on iOS Safari on every element. !important is
   required because PrimeVue injects its component styles dynamically after
   this stylesheet, which would otherwise override the * rule. */
* {
  touch-action: manipulation !important;
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
  overflow: hidden;
  background:
    /* table rail: a stronger, slightly higher vignette frames the centre like
       the padded rim of a real table */
    radial-gradient(ellipse at 50% 46%, transparent 12%, rgba(0, 0, 0, 0.85) 100%),
    radial-gradient(ellipse at 50% 40%, var(--felt-center) 0%, var(--felt-mid) 50%, var(--felt-edge) 100%);
}

/* Fabric grain over the felt. overlay blend lets the gradient show through. */
.app-background::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: var(--felt-noise);
  background-size: 180px 180px;
  opacity: 0.07;
  mix-blend-mode: overlay;
  pointer-events: none;
}

/* Slow ambient light drifting across the felt so the surface feels alive. */
.app-background::after {
  content: '';
  position: absolute;
  inset: -20%;
  background: radial-gradient(ellipse at 50% 45%, rgba(120, 220, 150, 0.10) 0%, transparent 45%);
  pointer-events: none;
  animation: felt-drift 26s ease-in-out infinite alternate;
}

@keyframes felt-drift {
  from { transform: translate(-4%, -3%) scale(1.05); }
  to   { transform: translate(4%, 3%) scale(1.14); }
}

.felt-watermark {
  position: absolute;
  font-size: clamp(180px, 38vmin, 460px);
  line-height: 1;
  color: rgba(255, 255, 255, 0.025);
  user-select: none;
  pointer-events: none;
}
.felt-watermark--tl { top: -6vmin; left: -4vmin; }
.felt-watermark--tr { top: -6vmin; right: -4vmin; }
.felt-watermark--bl { bottom: -10vmin; left: -4vmin; }
.felt-watermark--br { bottom: -10vmin; right: -4vmin; }

/* Tactile depth for PrimeVue buttons (solid severities only — text buttons like
   "Back" stay flat). A subtle lift on hover ties them to the premium feel. */
.p-button:not(.p-button-text):not(.p-button-link) {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.35), inset 0 1px 0 rgba(255, 255, 255, 0.18);
  transition: transform 0.12s ease, box-shadow 0.12s ease, filter 0.12s ease;
}
.p-button:not(.p-button-text):not(.p-button-link):not(:disabled):hover {
  transform: translateY(-1px);
  filter: brightness(1.06);
  box-shadow: 0 5px 16px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.22);
}
.p-button:not(.p-button-text):not(.p-button-link):not(:disabled):active {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.12);
}

@media (prefers-reduced-motion: reduce) {
  .app-background::after { animation: none; }
  .p-button { transition: none; }
  .p-button:hover { transform: none; }
}

.titlescreen {
  width: 100%;
  height: 100%;
}
</style>
