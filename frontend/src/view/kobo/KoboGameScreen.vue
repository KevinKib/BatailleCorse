<script setup lang="ts">
import { toRef } from 'vue';
import { Button } from 'primevue';
import { useRouter } from 'vue-router';
import { useKoboStore } from '../../state/Kobo.store';
import { useKoboBootstrap } from '../../composables/useKoboBootstrap';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import { useRemovedRedirect } from '../../composables/useRemovedRedirect';
import GameLobby from '../../components/lobby/GameLobby.vue';
import { findGame } from '../../model/games';
import { useI18n } from '../../composables/useI18n';

const props = defineProps<{ gameId: string }>();
const ui = useI18n().koboUi;
const store = useKoboStore();
const router = useRouter();
const game = findGame('kobo')!;
useKoboBootstrap(props.gameId);

// Leaving a running game forfeits (the others carry on), same guard as the other games.
useLeaveGuard({
  isInProgress: () => store.screen === 'playing',
  forfeit: () => store.forfeit(),
});

// The host removed me from the lobby: lead to the Game selection with a notice.
useRemovedRedirect(toRef(store, 'removedByHost'), () => { store.removedByHost = false; });

// Leaving frees the seat. A leave that could not be sent keeps the player in the lobby, with the error shown.
function leaveLobby() {
  // Replace, not push: Back must not return to a room the player no longer sits in.
  if (store.leave()) router.replace({ name: 'games' });
}
</script>

<template>
  <div class="kobo-screen" :class="{ 'kobo-screen--lobby': store.screen === 'lobby' }">
    <GameLobby
      v-if="store.screen === 'lobby' && store.lobby"
      :game="game"
      :lobby="store.lobby"
      :error="store.lobbyError"
      :starting="store.starting"
      @start="store.startGame()"
      @leave="leaveLobby"
      @remove-seat="seat => store.removeSeat(seat)"
      @rename="name => store.rename(name)" />

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral felt screen. -->
    <div v-else-if="store.screen === 'connecting'" class="connecting" data-test="connecting"
         role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <!-- PROVISIONAL: replaced by the Kobo table (plan PR K3). It carries no rule, only a way back. -->
    <div v-else class="provisional" data-test="kobo-provisional">
      <p role="status">{{ ui.provisional }}</p>
      <Button data-test="back" class="back-button" :label="ui.back" icon="pi pi-arrow-left"
              severity="secondary" rounded @click="router.push({ name: 'games' })" />
    </div>
  </div>
</template>

<style scoped>
.kobo-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  padding: var(--space-2);
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, var(--felt-center) 0%, var(--felt-mid) 48%, var(--felt-edge) 100%);
}
/* The lobby is a title-style screen: it sits on the shared felt background instead. */
.kobo-screen--lobby {
  display: block;
  padding: 0;
  background: none;
}
.connecting, .provisional {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  color: rgba(255, 255, 255, 0.85);
  font-family: var(--font-title);
  letter-spacing: 0.04em;
  text-align: center;
}
.connecting-spinner { font-size: 1.5rem; color: var(--gold); }
</style>
