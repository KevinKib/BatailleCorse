<script setup lang="ts">
import { squareCoords } from '../../model/draughts/board';

// A small, decorative drawing of the opening position for the lobby: the dark squares and the 20 + 20 men.
const SQUARES = Array.from({ length: 50 }, (_, i) => i + 1);
const at = (square: number) => squareCoords(square, 'WHITE');
</script>

<template>
  <svg class="mini" data-test="draughts-mini-board" viewBox="0 0 100 100" aria-hidden="true" focusable="false">
    <rect class="mini-light" x="0" y="0" width="100" height="100" />
    <rect v-for="n in SQUARES" :key="`s${n}`" class="mini-dark" :x="at(n).col * 10" :y="at(n).row * 10" width="10" height="10" />
    <circle
      v-for="n in SQUARES.filter(s => s <= 20 || s >= 31)"
      :key="`p${n}`"
      class="mini-piece"
      data-test="mini-piece"
      :data-colour="n <= 20 ? 'black' : 'white'"
      :cx="at(n).col * 10 + 5"
      :cy="at(n).row * 10 + 5"
      r="3.6" />
  </svg>
</template>

<style scoped>
.mini { width: 64px; height: 64px; flex: none; border-radius: 4px; box-shadow: 0 0 0 2px #07160d; }
.mini-light { fill: #2c6a3e; }
.mini-dark { fill: #10331c; }
.mini-piece[data-colour='white'] { fill: #f4ecd8; }
.mini-piece[data-colour='black'] { fill: #231f1c; stroke: rgba(255, 255, 255, 0.45); stroke-width: 0.6; }
</style>
