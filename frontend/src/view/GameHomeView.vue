<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GameHome from '../components/GameHome.vue';
import { findGame } from '../model/games';

const route = useRoute();
// The router guard only lets registered games in.
const game = computed(() => findGame(route.params.game)!);
</script>

<template>
  <GameHome :game="game" />
</template>
