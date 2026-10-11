<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import JoinGameForm from '../components/JoinGameForm.vue';
import { findGame } from '../model/games';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import { useBullshitStore } from '../state/Bullshit.store';

const route = useRoute();
// The router guard only lets registered games in.
const game = computed(() => findGame(route.params.game)!);
const initialId = computed(() => (route.params.id as string | undefined) ?? '');

const batailleCorse = useBatailleCorseStore();
const bullshit = useBullshitStore();

function join(id: string, name?: string): Promise<void> {
  return game.value.key === 'bullshit' ? bullshit.join(id, name) : batailleCorse.join(id, name);
}
</script>

<template>
  <JoinGameForm :game="game" :join="join" :initial-id="initialId" />
</template>
