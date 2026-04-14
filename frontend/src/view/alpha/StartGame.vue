<template>
  <div>

    <RouterLink to="game">
      <Button severity="primary" @click="createGame()">Create 2 player game</Button>
    </RouterLink>
    <RouterLink to="debug">
      <Button severity="info">Debug game</Button>
    </RouterLink>

  </div>
</template>

<script setup lang="ts">
import { Button } from 'primevue';
import { RouterLink } from 'vue-router';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { storeToRefs } from 'pinia';

const batailleCorseStore = useBatailleCorseStore();
const { state: batailleCorse } = storeToRefs(batailleCorseStore);

function createGame() {
  batailleCorseStore.create();
}

</script>

<style>
</style>