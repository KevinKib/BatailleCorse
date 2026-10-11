<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import GameHome from '../components/GameHome.vue';
import { findGame } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';

const route = useRoute();
// The router guard only lets registered games in.
const game = computed(() => findGame(route.params.game)!);

const bullshit = useBullshitStore();
// Only a game without a create page creates its lobby here; Bataille Corse keeps its own page for now.
const createLobby = computed(() => (game.value.key === 'bullshit' ? (name?: string) => bullshit.create(name) : undefined));
</script>

<template>
  <GameHome :game="game" :create-lobby="createLobby" />
</template>
