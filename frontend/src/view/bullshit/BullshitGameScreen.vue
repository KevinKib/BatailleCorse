<script setup lang="ts">
import { computed } from 'vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { useBullshitBootstrap } from '../../composables/useBullshitBootstrap';
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import type Card from '../../model/Card';

const props = defineProps<{ gameId: string }>();
const store = useBullshitStore();
useBullshitBootstrap(props.gameId);

const opponents = computed(() =>
  (store.game?.players ?? []).filter(p => p.id !== String(store.mySeat)));
const isSelected = (card: Card) => store.selectedCards.some(c => c.name === card.name);
const joinLink = computed(() => `${location.origin}/games/bullshit/join/${props.gameId}`);

const joinedPlayers = computed(() => (store.lobby?.players ?? []).filter(p => p.joined));
const playersNeeded = computed(() =>
  Math.max(0, (store.lobby?.minPlayers ?? 0) - joinedPlayers.value.length));

function selectAll(event: FocusEvent) {
  (event.target as HTMLInputElement).select();
}
</script>

<template>
  <div class="bullshit-screen">
    <div v-if="store.phase === 'lobby'" data-test="lobby" class="panel lobby">
      <h2>Lobby</h2>
      <p class="count" data-test="player-count">
        {{ joinedPlayers.length }} / {{ store.lobby?.maxPlayers }} players
      </p>
      <ul class="players">
        <li v-for="p in joinedPlayers" :key="p.seat">
          Player {{ p.seat + 1 }}: {{ p.name }}<span v-if="p.seat === store.mySeat"> (you)</span>
        </li>
      </ul>

      <label class="share">
        Invite players with this link:
        <input :value="joinLink" readonly @focus="selectAll" />
      </label>

      <template v-if="store.isHost">
        <button
          data-test="start"
          type="button"
          class="btn primary"
          :disabled="!store.canStart"
          @click="store.startGame()">
          Start game
        </button>
        <p v-if="!store.canStart" class="hint" data-test="start-hint">
          Waiting for {{ playersNeeded }} more player{{ playersNeeded === 1 ? '' : 's' }} to start…
        </p>
      </template>
      <p v-else class="hint">Waiting for the host to start…</p>
    </div>

    <div v-else-if="store.phase === 'finished'" data-test="end" class="panel">
      <h2>{{ store.iWon ? 'You win!' : 'You lose' }}</h2>
    </div>

    <template v-else>
      <div class="opponents">
        <div v-for="opp in opponents" :key="opp.id" class="opponent" :class="{ active: opp.isCurrentPlayer }">
          <span class="seat">Player {{ opp.id }}</span>
          <CardCounter :count="opp.handCount" />
        </div>
      </div>

      <div class="table">
        <p class="claim">Claim: {{ store.game?.currentTarget.label }}</p>
        <p v-if="store.game?.table.state === 'CLAIM'" class="last-claim">
          Player {{ store.game.table.claimantId }} played {{ store.game.table.count }} card(s) face-down
        </p>
        <p class="pile">Discard pile: {{ store.game?.discardPileSize }}</p>
      </div>

      <div v-if="store.reveal" data-test="reveal" class="reveal">
        <p>
          Player {{ store.reveal.callerSeat }} called bullshit on Player {{ store.reveal.claimantSeat }} —
          claim was {{ store.reveal.truthful ? 'TRUE' : 'FALSE' }} — Player {{ store.reveal.pickerSeat }} takes the pile
        </p>
        <div class="revealed-cards">
          <PlayingCard v-for="(c, i) in store.reveal.revealedCards" :key="i" :rank="c.rank" :suit="c.suit" />
        </div>
      </div>

      <div class="hand">
        <button
          v-for="(card, i) in store.game?.myHand ?? []"
          :key="card.name"
          :data-test="`hand-card-${i}`"
          class="hand-card"
          :class="{ selected: isSelected(card) }"
          type="button"
          @click="store.toggleCard(card)">
          <PlayingCard :rank="card.rank" :suit="card.suit" />
        </button>
      </div>

      <div class="actions">
        <button
          data-test="discard"
          type="button"
          :disabled="!store.isMyTurn || store.selectedCards.length === 0"
          @click="store.discard()">
          Discard as {{ store.game?.currentTarget.label }}
        </button>
        <button
          data-test="call"
          type="button"
          :disabled="!store.canCallBullshit"
          @click="store.callBullshit()">
          Call Bullshit
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bullshit-screen { display: flex; flex-direction: column; gap: 1rem; padding: 1rem; align-items: center; }
.panel { text-align: center; }
.lobby { display: flex; flex-direction: column; gap: 0.75rem; align-items: center; min-width: 22rem; }
.count { font-weight: 600; margin: 0; }
.players { list-style: none; padding: 0; margin: 0; }
.share { display: flex; flex-direction: column; gap: 0.25rem; width: 100%; font-size: 0.85rem; }
.share input { width: 100%; font-family: monospace; padding: 0.4rem; box-sizing: border-box; }
.hint { opacity: 0.7; margin: 0; }
.btn { padding: 0.6rem 1.4rem; border-radius: 0.5rem; border: 1px solid var(--p-primary-color); font-size: 1rem; cursor: pointer; }
.btn.primary { background: var(--p-primary-color); color: var(--p-primary-contrast-color, #fff); }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.opponents { display: flex; gap: 1rem; }
.opponent.active { outline: 2px solid var(--p-primary-color); border-radius: 0.5rem; }
.hand { display: flex; gap: 0.25rem; flex-wrap: wrap; justify-content: center; }
.hand-card { background: none; border: none; padding: 0; cursor: pointer; }
.hand-card.selected { transform: translateY(-12px); }
.actions { display: flex; gap: 1rem; }
.reveal { text-align: center; }
.revealed-cards { display: flex; gap: 0.25rem; justify-content: center; }
</style>
