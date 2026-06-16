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
</script>

<template>
  <div class="bullshit-screen">
    <div v-if="store.phase === 'lobby'" data-test="lobby" class="panel">
      <h2>Lobby</h2>
      <ul class="players">
        <li v-for="p in store.lobby?.players.filter(pl => pl.joined) ?? []" :key="p.seat">
          Player {{ p.seat + 1 }}: {{ p.name }}
        </li>
      </ul>
      <p class="share">Share: <code>{{ joinLink }}</code></p>
      <button
        v-if="store.isHost"
        data-test="start"
        type="button"
        :disabled="!store.canStart"
        @click="store.startGame()">
        Start game
      </button>
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
.share code { word-break: break-all; }
.players { list-style: none; padding: 0; }
.hint { opacity: 0.7; }
.opponents { display: flex; gap: 1rem; }
.opponent.active { outline: 2px solid var(--p-primary-color); border-radius: 0.5rem; }
.hand { display: flex; gap: 0.25rem; flex-wrap: wrap; justify-content: center; }
.hand-card { background: none; border: none; padding: 0; cursor: pointer; }
.hand-card.selected { transform: translateY(-12px); }
.actions { display: flex; gap: 1rem; }
.reveal { text-align: center; }
.revealed-cards { display: flex; gap: 0.25rem; justify-content: center; }
</style>
