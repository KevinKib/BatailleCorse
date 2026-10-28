<script setup lang="ts">
import BlackjackDealer from './BlackjackDealer.vue';
import BlackjackHandSummary from './BlackjackHandSummary.vue';
import BlackjackMySeat from './BlackjackMySeat.vue';
import BlackjackSeatPlate from './BlackjackSeatPlate.vue';
import { useI18n } from '../../composables/useI18n';
import type { BlackjackSeat, BlackjackState } from '../../model/blackjack/BlackjackState';

// The table, top to bottom: the dealer strip, the last hand summary (during betting), the opponents (2 columns on a
// phone, one row on a wide felt; none in solo) and my seat. The server's state decides everything shown.
const props = defineProps<{
  game: BlackjackState;
  opponents: BlackjackSeat[];
  me: BlackjackSeat | null;
  mySeat: number;
  nameOf: (seat: number) => string;
  disconnected: (seat: number) => boolean;
  secondsRemaining: (seat: number) => number | null;
}>();
const ui = useI18n().blackjackUi;
const playing = () => props.game.phase === 'PLAYING';
</script>

<template>
  <div class="bj-table" data-test="blackjack-table">
    <BlackjackDealer :dealer="game.dealer" />
    <BlackjackHandSummary v-if="game.phase === 'BETTING' && game.lastHand" :last-hand="game.lastHand" :my-seat="mySeat" :name-of="nameOf" />
    <div v-if="opponents.length > 0" class="bj-opponents" data-test="opponents" role="group" :aria-label="ui.table.opponents">
      <BlackjackSeatPlate v-for="o in opponents" :key="o.seat" :player="o" :label="nameOf(o.seat)"
                          :current="playing() && game.currentSeat === o.seat" :active-hand-index="game.activeHandIndex"
                          :disconnected="disconnected(o.seat)" :seconds-remaining="secondsRemaining(o.seat)" />
    </div>
    <BlackjackMySeat v-if="me" :player="me" :current="playing() && game.currentSeat === me.seat" :active-hand-index="game.activeHandIndex" />
  </div>
</template>

<style scoped>
.bj-table { flex: 1 1 auto; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: var(--space-2); width: 100%; max-width: 1000px; margin: 0 auto; }
.bj-opponents { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-2); }
@media (min-width: 900px) {
  .bj-opponents { grid-template-columns: repeat(5, minmax(0, 1fr)); }
}
</style>
