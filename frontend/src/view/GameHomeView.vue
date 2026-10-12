<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GameHome from '../components/GameHome.vue';
import { findGame } from '../model/games';
import { useLobbyCreator } from '../composables/useLobbyCreator';

const route = useRoute();
// The router guard only lets registered games in.
const game = computed(() => findGame(route.params.game)!);

const creatorFor = useLobbyCreator();
const createLobby = computed(() => creatorFor(game.value));
</script>

<template>
  <GameHome :game="game" :create-lobby="createLobby" />
</template>
