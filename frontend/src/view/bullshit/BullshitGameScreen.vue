<script setup lang="ts">
import { computed, onBeforeUnmount } from 'vue';
import { Button } from 'primevue';
import { useBullshitStore } from '../../state/Bullshit.store';
import { useBullshitBootstrap } from '../../composables/useBullshitBootstrap';
import { useSeatDisconnectCountdown } from '../../composables/useSeatDisconnectCountdown';
import { useLeaveGuard } from '../../composables/useLeaveGuard';
import PlayingCard from '../../components/PlayingCard.vue';
import CardCounter from '../../components/CardCounter.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import ForfeitBanner from '../../components/ForfeitBanner.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import OpponentSeat from '../../components/bullshit/OpponentSeat.vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import type Card from '../../model/Card';

const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.bullshitUi;
// Seats are 0-based internally; players see them numbered from 1.
const playerLabel = (seat: number) => format(ui.playerLabel, { n: seat + 1 });
const store = useBullshitStore();
useBullshitBootstrap(props.gameId);

const countdown = useSeatDisconnectCountdown({
  disconnections: () => store.liveDisconnections,
  isGameOver: () => store.phase === 'finished',
});
onBeforeUnmount(() => countdown.cancel());

// Leaving an in-progress game forfeits (opponents win the seat), same as BatailleCorse.
// Reuses the shared leave guard: navigating away (Back button / browser back) prompts,
// and confirming publishes the resignation. A hard tab-close falls back to the server's
// disconnect grace timer (now that presence is registered).
useLeaveGuard({
  isInProgress: () => store.phase === 'playing',
  mode: () => 'multiplayer',
  forfeit: () => store.forfeit(),
});

function seatDisconnected(seat: number): boolean {
  return seat in store.liveDisconnections;
}
function seatSecondsRemaining(seat: number): number | null {
  const deadline = store.liveDisconnections[seat];
  return deadline == null ? null : countdown.secondsRemainingFor(deadline);
}

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
      <h2>{{ ui.lobby.title }}</h2>
      <p class="count" data-test="player-count">
        {{ format(ui.lobby.playerCount, { joined: joinedPlayers.length, max: store.lobby?.maxPlayers ?? '' }) }}
      </p>
      <ul class="players">
        <li v-for="p in joinedPlayers" :key="p.seat">
          {{ format(ui.lobby.playerRow, { label: playerLabel(p.seat), name: p.name }) }}<span v-if="p.seat === store.mySeat">{{ ui.lobby.youSuffix }}</span>
        </li>
      </ul>

      <label class="share">
        {{ ui.lobby.inviteLabel }}
        <input :value="joinLink" readonly @focus="selectAll" />
      </label>

      <template v-if="store.isHost">
        <button
          data-test="start"
          type="button"
          class="btn primary"
          :disabled="!store.canStart"
          @click="store.startGame()">
          {{ ui.lobby.startGame }}
        </button>
        <p v-if="!store.canStart" class="hint" data-test="start-hint">
          {{ plural(ui.lobby.waitingForPlayers, playersNeeded) }}
        </p>
      </template>
      <p v-else class="hint">{{ ui.lobby.waitingForHost }}</p>
    </div>

    <EndGameOverlay
      v-else-if="store.phase === 'finished'"
      data-test="end"
      :did-i-win="store.iWon"
      :subtitle="store.iWon ? ui.end.youWon : ui.end.youLost"
      :rematch-button="{ label: ui.end.playAgain, disabled: false }"
      @play-again="store.playAgain()"
    />

    <template v-else>
      <RulesPanel :rules="messages.bullshit" />

      <RouterLink :to="{ path: '/' }" class="leave-button" data-test="leave">
        <Button severity="secondary" :label="ui.back" icon="pi pi-undo" variant="text" rounded />
      </RouterLink>

      <Transition name="forfeit-fade">
        <ForfeitBanner
          v-if="store.forfeitNotice"
          class="forfeit-notice"
          :label="format(ui.table.forfeited, { player: playerLabel(store.forfeitNotice.seat) })" />
      </Transition>
      <div class="table-frame">
      <div class="opponents-ring">
        <div
          v-for="(opp, i) in opponents"
          :key="opp.id"
          class="seat-slot"
          :style="{ left: seatPositions[i].left + '%', top: seatPositions[i].top + '%' }">
          <OpponentSeat
            :label="playerLabel(Number(opp.id))"
            :hand-count="opp.handCount"
            :active="opp.isCurrentPlayer"
            :disconnected="seatDisconnected(Number(opp.id))"
            :seconds-remaining="seatSecondsRemaining(Number(opp.id))" />
        </div>
      </div>

      <div class="table-center">
        <div class="claim-badge" data-test="claim-badge">
          {{ ui.table.claim }} <strong>{{ store.game?.currentTarget.label }}</strong>
        </div>

        <div class="pile-well">
          <PlayingCard :hidden="true" rank="10" suit="spade" />
          <div class="pile-chip">
            <CardCounter :count="store.game?.discardPileSize ?? 0" />
          </div>

          <Transition name="reveal-fade">
            <div v-if="store.reveal" data-test="reveal" class="reveal"
                 :style="{ '--n': store.reveal.revealedCards.length }">
              <div class="revealed-cards">
                <div v-for="(c, i) in store.reveal.revealedCards" :key="i" class="flip-card" :style="{ '--i': i }">
                  <div class="flip-inner">
                    <div class="flip-face flip-back"><PlayingCard :hidden="true" rank="10" suit="spade" /></div>
                    <div class="flip-face flip-front"><PlayingCard :rank="c.rank" :suit="c.suit" /></div>
                  </div>
                </div>
              </div>
              <div class="verdict" data-test="verdict"
                   :class="store.reveal.truthful ? 'verdict--truthful' : 'verdict--bluff'">
                {{ store.reveal.truthful ? ui.table.truthful : ui.table.bluff }}
              </div>
              <p class="reveal-caption">
                {{ format(ui.table.revealCaption, {
                  caller: playerLabel(store.reveal.callerSeat),
                  claimant: playerLabel(store.reveal.claimantSeat),
                  picker: playerLabel(store.reveal.pickerSeat),
                }) }}
              </p>
            </div>
          </Transition>
        </div>

        <p v-if="store.game?.table.state === 'CLAIM'" class="last-play" data-test="last-play">
          {{ plural(ui.table.playedFaceDown, store.game.table.count, { player: playerLabel(Number(store.game.table.claimantId)) }) }}
        </p>
      </div>

      </div>

      <div class="my-zone">
        <span :class="['my-tag', { 'my-tag--active': store.isMyTurn }]">{{ ui.you }}</span>
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
          <Button
            data-test="discard"
            :label="format(ui.table.discardAs, { target: store.game?.currentTarget.label ?? '' })"
            icon="pi pi-arrow-up"
            severity="success"
            rounded
            :disabled="!store.isMyTurn || store.selectedCards.length === 0"
            @click="store.discard()" />
          <Button
            data-test="call"
            :label="ui.table.callBullshit"
            icon="pi pi-flag"
            severity="danger"
            rounded
            :disabled="!store.canCallBullshit"
            @click="store.callBullshit()" />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.bullshit-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  align-items: center;
  box-sizing: border-box;
  /* Paint our own felt so it covers the full scrollable content, not just the
     first viewport — the shared app-background is only viewport-tall, which left
     a bare strip when the screen scrolled on small displays. Mirrors the
     BatailleCorse game screen, which also paints its own opaque felt. */
  min-height: 100vh;
  min-height: 100dvh;
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, var(--felt-center) 0%, var(--felt-mid) 48%, var(--felt-edge) 100%);
}
.panel { text-align: center; }
.lobby { display: flex; flex-direction: column; gap: 0.75rem; align-items: center; min-width: 22rem; }
.count { font-weight: 600; margin: 0; }
.players { list-style: none; padding: 0; margin: 0; }
.share { display: flex; flex-direction: column; gap: 0.25rem; width: 100%; font-size: 0.85rem; }
.share input { width: 100%; font-family: monospace; padding: 0.4rem; box-sizing: border-box; }
.hint { opacity: 0.7; margin: 0; }
.btn { padding: 0.6rem 1.4rem; border-radius: var(--button-radius); border: 1px solid var(--p-primary-color); font-size: 1rem; cursor: pointer; }
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

.hand { display: flex; gap: 0.25rem; flex-wrap: wrap; justify-content: center; }
.hand-card { background: none; border: none; padding: 0; cursor: pointer; }
.hand-card.selected { transform: translateY(-12px); }
.actions { display: flex; gap: 1rem; }

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
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  pointer-events: none;
}

.revealed-cards { display: flex; gap: 0.3rem; justify-content: center; perspective: 800px; }

.flip-card {
  width: var(--seat-card-w);
  aspect-ratio: 167.575 / 243.1375;
}
.flip-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transform-style: preserve-3d;
  transform: rotateY(180deg);
  animation: card-flip 520ms ease-out forwards;
  animation-delay: calc(var(--i) * 120ms);
}
.flip-face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.flip-face :deep(.playing_card) {
  width: 100%;
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}
.flip-front { transform: rotateY(0deg); }
.flip-back { transform: rotateY(180deg); }

@keyframes card-flip {
  from { transform: rotateY(180deg); }
  to   { transform: rotateY(0deg); }
}

.verdict {
  font-weight: 800;
  letter-spacing: 0.12em;
  font-size: 0.95rem;
  color: #fff;
  padding: 4px 16px;
  border-radius: 999px;
  opacity: 0;
  animation: verdict-in 360ms ease-out forwards;
  animation-delay: calc((var(--n) - 1) * 120ms + 520ms);
}
/* Opaque fills so the flipped cards never show through the verdict pill. */
.verdict--truthful {
  background: rgb(var(--accent-positive-rgb));
  color: #06210f;
  box-shadow: 0 0 18px 2px rgba(var(--accent-positive-rgb), 0.55);
}
.verdict--bluff {
  background: rgb(var(--accent-negative-rgb));
  color: #2a0606;
  box-shadow: 0 0 18px 2px rgba(var(--accent-negative-rgb), 0.55);
}
@keyframes verdict-in {
  from { opacity: 0; transform: scale(0.8); }
  to   { opacity: 1; transform: scale(1); }
}

.reveal-fade-enter-active { transition: opacity 0.2s ease; }
.reveal-fade-leave-active { transition: opacity 0.35s ease, transform 0.35s ease; }
.reveal-fade-enter-from { opacity: 0; }
.reveal-fade-leave-to { opacity: 0; transform: scale(0.92); }

@media (prefers-reduced-motion: reduce) {
  .flip-inner { animation: none; transform: rotateY(0deg); }
  .verdict { animation: none; opacity: 1; }
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

.my-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  width: 100%;
  max-width: 900px;
  padding: 12px 0 calc(8px + env(safe-area-inset-bottom, 0px));
  background: linear-gradient(to top, rgba(0, 0, 0, 0.30) 0%, rgba(0, 0, 0, 0.04) 100%);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px 14px 0 0;
}

.my-tag {
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 4px 14px;
}
.my-tag { position: relative; }
.my-tag--active {
  padding-left: 22px;
  color: #ffffff;
  border-color: var(--turn-ring-color);
  box-shadow: var(--turn-ring);
}
.my-tag--active::before {
  content: "";
  position: absolute;
  left: 8px;
  top: 50%;
  width: var(--turn-dot-size);
  height: var(--turn-dot-size);
  margin-top: calc(var(--turn-dot-size) / -2);
  border-radius: 50%;
  background: var(--gold);
}

.hand :deep(.playing_card) {
  width: clamp(48px, 9vmin, 76px);
  height: auto;
  aspect-ratio: 167.575 / 243.1375;
}

.forfeit-notice {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1800;
}
.forfeit-fade-enter-active,
.forfeit-fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.forfeit-fade-enter-from,
.forfeit-fade-leave-to { opacity: 0; transform: translate(-50%, -8px); }

.leave-button {
  position: absolute;
  left: 10px;
  bottom: calc(10px + env(safe-area-inset-bottom, 0px));
  z-index: 1700;
  text-decoration: none;
}
</style>
