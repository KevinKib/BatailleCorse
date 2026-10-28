<script setup lang="ts">
// A 5x5 checkerboard in a rounded square, drawn in the current text colour. Decorative: the card carries its own text.
const cells = Array.from({ length: 25 }, (_, i) => ({ x: 3 + (i % 5) * 3.6, y: 3 + Math.floor(i / 5) * 3.6, filled: ((i % 5) + Math.floor(i / 5)) % 2 === 0 }))
  .filter(c => c.filled);
</script>

<template>
  <svg data-test="checkerboard-icon" viewBox="0 0 24 24" width="26" height="26" aria-hidden="true" focusable="false">
    <rect x="1" y="1" width="22" height="22" rx="4" fill="none" stroke="currentColor" stroke-width="1.5" />
    <rect v-for="c in cells" :key="`${c.x}-${c.y}`" :x="c.x" :y="c.y" width="3.6" height="3.6" fill="currentColor" />
  </svg>
</template>
