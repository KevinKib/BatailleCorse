<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBullshitStore } from '../../state/Bullshit.store';

const route = useRoute();
const router = useRouter();
const store = useBullshitStore();

const name = ref('');
const joinId = ref((route.params.id as string) ?? '');
const isJoin = ref(route.name === 'bullshit-join');

watch(() => store.gameId, (id) => {
  if (id) router.push(`/games/bullshit/room/${id}`);
});

function onCreate() {
  store.create(name.value || undefined);
}

async function onJoin() {
  await store.join(joinId.value, name.value || undefined);
  router.push(`/games/bullshit/room/${joinId.value}`);
}
</script>

<template>
  <div class="start">
    <h1>Bullshit</h1>
    <label>Your name <input v-model="name" type="text" /></label>

    <template v-if="!isJoin">
      <button type="button" @click="onCreate">Create 2-player game</button>
    </template>
    <template v-else>
      <label>Game ID <input v-model="joinId" type="text" /></label>
      <button type="button" :disabled="!joinId" @click="onJoin">Join game</button>
    </template>
  </div>
</template>

<style scoped>
.start { display: flex; flex-direction: column; gap: 1rem; padding: 2rem; max-width: 28rem; margin: 0 auto; }
</style>
