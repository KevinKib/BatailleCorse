<script setup lang="ts">
import { computed } from 'vue';
import { isKing, isWhitePiece, type PieceKind } from './pieceKind';

// One man or king. The colour is ivory or ebony; a king also carries a crown, so the two kinds never differ by
// colour alone. Decorative: the square that holds it says what it is.
const props = defineProps<{ kind: PieceKind }>();
const king = computed(() => isKing(props.kind));
const white = computed(() => isWhitePiece(props.kind));
</script>

<template>
  <span class="piece" :class="white ? 'piece--white' : 'piece--black'" :data-kind="kind" aria-hidden="true">
    <span class="piece-groove"></span>
    <svg v-if="king" class="piece-crown" data-test="king-mark" viewBox="0 0 20 14" focusable="false">
      <path d="M2 11 L1 3 L6 7 L10 1 L14 7 L19 3 L18 11 Z" stroke-width="1" stroke-linejoin="round" />
      <rect x="2" y="11.6" width="16" height="2" rx="0.6" />
    </svg>
  </span>
</template>

<style scoped>
.piece {
  position: relative;
  display: block;
  width: 80%;
  aspect-ratio: 1;
  border-radius: 50%;
  pointer-events: none;
}
.piece--white {
  background: radial-gradient(circle at 35% 30%, #fffdf6 0%, #f4ecd8 55%, #bfae85 100%);
  box-shadow: 0 2px 3px rgba(0, 0, 0, 0.55), 0 5px 10px rgba(0, 0, 0, 0.35);
}
.piece--black {
  background: radial-gradient(circle at 35% 30%, #4a433d 0%, #231f1c 60%, #0d0b0a 100%);
  box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.4), 0 2px 3px rgba(0, 0, 0, 0.55), 0 5px 10px rgba(0, 0, 0, 0.35);
}
.piece-groove {
  position: absolute;
  left: 19%;
  top: 19%;
  width: 62%;
  height: 62%;
  box-sizing: border-box;
  border-radius: 50%;
  border: 1.5px solid rgba(120, 96, 44, 0.3);
}
.piece--black .piece-groove { border-color: rgba(255, 255, 255, 0.14); }

.piece-crown {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 52%;
  height: 36%;
  transform: translate(-50%, -55%);
}
.piece--white .piece-crown { fill: #c8860a; stroke: #7a4f05; }
.piece--black .piece-crown { fill: #f5c842; stroke: #7a5a10; }
.piece--white .piece-crown rect,
.piece--black .piece-crown rect { stroke: none; }
</style>
