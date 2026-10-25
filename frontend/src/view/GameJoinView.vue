<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import JoinGameForm from '../components/JoinGameForm.vue';
import { findGame } from '../model/games';
import { useGameRooms } from '../composables/useGameRooms';
import { usePublicLobbies } from '../composables/usePublicLobbies';

const route = useRoute();
// The router guard only lets registered games in.
const game = computed(() => findGame(route.params.game)!);
const initialId = computed(() => (route.params.id as string | undefined) ?? '');

const rooms = useGameRooms();
// The view is keyed by route, so it is remounted when the game changes.
const publicLobbies = usePublicLobbies(game.value);
const publicGames = computed(() => ({
  lobbies: publicLobbies.lobbies.value,
  total: publicLobbies.total.value,
  truncated: publicLobbies.truncated.value,
  loading: publicLobbies.loading.value,
  online: publicLobbies.online.value,
  announcement: publicLobbies.announcement.value,
}));

function join(id: string, name?: string): Promise<void> {
  return rooms(game.value).join(id, name);
}
</script>

<template>
  <JoinGameForm :game="game" :join="join" :initial-id="initialId" :public-games="publicGames" />
</template>
