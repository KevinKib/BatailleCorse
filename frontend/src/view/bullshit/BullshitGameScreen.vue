<script setup lang="ts">
import { computed } from 'vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { useBullshitBootstrap } from '../../composables/useBullshitBootstrap';
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import OpponentSeat from '../../components/bullshit/OpponentSeat.vue';
import type Card from '../../model/Card';

const props = defineProps<{ gameId: string }>();
const store = useBullshitStore();
useBullshitBootstrap(props.gameId);

// Opponents are everyone but me, in seat order.
const opponents = computed(() =>
  (store.game?.players ?? []).filter(p => p.id !== String(store.mySeat)));

// Angles (degrees) around the table for K opponents, skipping the bottom (my zone).
// 0 = right, 90 = top, 180 = left. Single opp sits at top; pairs sit up on the
// top corners; 3+ spread evenly from left, over the top, to the right.
function seatAngles(count: number): number[] {
  if (count <= 0) return [];
  if (count === 1) return [90];
  if (count === 2) return [135, 45];
  return Array.from({ length: count }, (_, i) => 180 - i * (180 / (count - 1)));
}

// Map each opponent to a point on the table ellipse (percent of the frame).
const RX = 44; // horizontal radius (%)
const RY = 40; // vertical radius (%)
const seatPositions = computed(() => {
  const angles = seatAngles(opponents.value.length);
  return angles.map(deg => {
    const r = (deg * Math.PI) / 180;
    return { left: 50 + RX * Math.cos(r), top: 50 - RY * Math.sin(r) };
  });
});

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

    <EndGameOverlay
      v-else-if="store.phase === 'finished'"
      data-test="end"
      :did-i-win="store.iWon"
      :subtitle="store.iWon ? 'You emptied your hand first.' : 'Another player emptied their hand first.'"
      :rematch-button="{ label: 'Play again', disabled: false }"
      @play-again="store.playAgain()"
    />

    <template v-else>
      <div class="table-frame">
      <div class="opponents-ring">
        <div
          v-for="(opp, i) in opponents"
          :key="opp.id"
          class="seat-slot"
          :style="{ left: seatPositions[i].left + '%', top: seatPositions[i].top + '%' }">
          <OpponentSeat
            :label="`Player ${Number(opp.id) + 1}`"
            :hand-count="opp.handCount"
            :active="opp.isCurrentPlayer" />
        </div>
      </div>

      <div class="table-center">
        <div class="claim-badge" data-test="claim-badge">
          Claim: <strong>{{ store.game?.currentTarget.label }}</strong>
        </div>

        <div class="pile-well">
          <PlayingCard :hidden="true" rank="10" suit="spade" />
          <div class="pile-chip">
            <CardCounter :count="store.game?.discardPileSize ?? 0" />
          </div>

          <div v-if="store.reveal" data-test="reveal" class="reveal">
            <div class="revealed-cards">
              <PlayingCard v-for="(c, i) in store.reveal.revealedCards" :key="i" :rank="c.rank" :suit="c.suit" />
            </div>
            <p class="reveal-caption">
              Player {{ store.reveal.callerSeat + 1 }} called bullshit on Player {{ store.reveal.claimantSeat + 1 }} —
              claim was {{ store.reveal.truthful ? 'TRUE' : 'FALSE' }} — Player {{ store.reveal.pickerSeat + 1 }} takes the pile
            </p>
          </div>
        </div>

        <p v-if="store.game?.table.state === 'CLAIM'" class="last-play" data-test="last-play">
          Player {{ Number(store.game.table.claimantId) + 1 }} played {{ store.game.table.count }} card(s) face-down
        </p>
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
.table-frame {
  position: relative;
  width: 100%;
  max-width: 900px;
  /* Oval play area; height tracks width so the ellipse math stays proportional. */
  aspect-ratio: 16 / 11;
  margin: 0 auto;
  border-radius: 50% / 42%;
  /* Felt: radial gradient + vignette, reusing the shared felt tokens. */
  background:
    radial-gradient(ellipse at 50% 46%, transparent 18%, rgba(0, 0, 0, 0.55) 100%),
    radial-gradient(ellipse at 50% 42%, var(--felt-center) 0%, var(--felt-mid) 52%, var(--felt-edge) 100%);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 2px 30px rgba(0, 0, 0, 0.55), 0 10px 40px rgba(0, 0, 0, 0.45);
  isolation: isolate;
  /* Fluid card sizes consumed by seats and the center. */
  --seat-card-w: clamp(40px, 7vmin, 60px);
  --pile-card-w: clamp(60px, 13vmin, 104px);
}

.opponents-ring {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.seat-slot {
  position: absolute;
  transform: translate(-50%, -50%);
}

/* Pass the fluid seat-card width down into each seat. */
.seat-slot :deep(.opponent-seat) {
  --seat-card-w: clamp(40px, 7vmin, 60px);
}

.opponents { display: flex; gap: 1rem; }
.opponent.active { outline: 2px solid var(--p-primary-color); border-radius: 0.5rem; }
.hand { display: flex; gap: 0.25rem; flex-wrap: wrap; justify-content: center; }
.hand-card { background: none; border: none; padding: 0; cursor: pointer; }
.hand-card.selected { transform: translateY(-12px); }
.actions { display: flex; gap: 1rem; }
.reveal { text-align: center; }
.revealed-cards { display: flex; gap: 0.25rem; justify-content: center; }

.table-center {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.claim-badge {
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(var(--accent-active-rgb), 0.4);
  border-radius: 999px;
  padding: 4px 14px;
}
.claim-badge strong { color: var(--gold); }

.pile-well {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(8px, 2vmin, 16px) clamp(12px, 3vmin, 22px);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px;
  background: radial-gradient(ellipse at 50% 45%, rgba(0, 0, 0, 0.28) 0%, rgba(0, 0, 0, 0.5) 100%);
  box-shadow: inset 0 3px 22px rgba(0, 0, 0, 0.65), inset 0 0 0 1px rgba(0, 0, 0, 0.35);
}
.pile-well :deep(.playing_card) {
  width: var(--pile-card-w);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}
.pile-chip {
  position: absolute;
  bottom: 4px;
  right: -10px;
}

.reveal {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: max-content;
  max-width: 60vw;
}
.revealed-cards { display: flex; gap: 0.25rem; justify-content: center; }
.revealed-cards :deep(.playing_card) {
  width: var(--seat-card-w);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}
.reveal-caption {
  margin: 0;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.7);
  border-radius: 8px;
  padding: 4px 8px;
}

.last-play {
  margin: 0;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.7);
}
</style>
