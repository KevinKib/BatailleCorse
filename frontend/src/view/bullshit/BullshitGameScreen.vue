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
import BullshitHand from '../../components/bullshit/BullshitHand.vue';
import BullshitLobby from '../../components/bullshit/BullshitLobby.vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import { seatDisplayName } from '../../model/bullshit/displayName';
import { claimSuit } from '../../model/bullshit/claimSuit';

const props = defineProps<{ gameId: string }>();
const messages = useI18n();
const ui = messages.bullshitUi;
const store = useBullshitStore();
// The roster outlives a forfeit (the player leaves `players`, the banner still names him).
// Seats are 0-based internally; the typed name wins, else "Bot N" / "Player N" (numbered from 1).
// Names are user input: only ever rendered as text.
const displayName = (seat: number) =>
  seatDisplayName(Object.values(store.roster), seat, { player: ui.playerLabel, bot: ui.botLabel });
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
// (Wide screens only: on narrow screens the seats flow in a row above the pile.)
function seatAngles(count: number): number[] {
  if (count <= 0) return [];
  if (count === 1) return [90];
  if (count === 2) return [135, 45];
  return Array.from({ length: count }, (_, i) => 180 - i * (180 / (count - 1)));
}

// Map each opponent to a point on the table ellipse (percent of the frame).
const RX = 44; // horizontal radius (%)
const RY = 36; // vertical radius (%): keeps the top seat inside the oval
const seatPositions = computed(() => {
  const angles = seatAngles(opponents.value.length);
  return angles.map(deg => {
    const r = (deg * Math.PI) / 180;
    return { left: 50 + RX * Math.cos(r), top: 50 - RY * Math.sin(r) };
  });
});

// Suit claims get their symbol next to the word (rank claims: null).
const targetSuit = computed(() => claimSuit(store.game?.currentTarget.label));
const myCardCount = computed(() => store.game?.myHand.length ?? 0);

const joinLink = computed(() => `${location.origin}/games/bullshit/join/${props.gameId}`);
</script>

<template>
  <div class="bullshit-screen" :class="{ 'bullshit-screen--lobby': store.phase === 'lobby' }">
    <BullshitLobby
      v-if="store.phase === 'lobby' && store.lobby"
      :lobby="store.lobby"
      :join-link="joinLink"
      :is-host="store.isHost"
      :can-start="store.canStart"
      @start="store.startGame()" />

    <EndGameOverlay
      v-else-if="store.phase === 'finished'"
      data-test="end"
      :did-i-win="store.iWon"
      :subtitle="store.iWon ? ui.end.youWon : ui.end.youLost"
      :rematch-button="{ label: ui.end.playAgain, disabled: false }"
      @play-again="store.playAgain()"
    />

    <!-- First server state not here yet (e.g. arriving through an invite link): a neutral
         felt screen, not the table, so the game UI never flashes before the lobby. -->
    <div v-else-if="store.phase === 'connecting'" class="connecting" data-test="connecting"
         role="status" aria-live="polite">
      <i class="pi pi-spin pi-spinner connecting-spinner" aria-hidden="true"></i>
      <span>{{ ui.connecting }}</span>
    </div>

    <template v-else>
      <RulesPanel :rules="messages.bullshit" />

      <div class="top-bar">
        <RouterLink :to="{ path: '/' }" class="leave-button" data-test="leave">
          <Button severity="secondary" :label="ui.back" icon="pi pi-undo" variant="text" rounded />
        </RouterLink>
      </div>

      <Transition name="forfeit-fade">
        <ForfeitBanner
          v-if="store.forfeitNotice"
          class="forfeit-notice"
          :label="format(ui.table.forfeited, { player: displayName(store.forfeitNotice.seat) })" />
      </Transition>

      <div class="table-frame">
        <div class="opponents-ring">
          <div
            v-for="(opp, i) in opponents"
            :key="opp.id"
            class="seat-slot"
            :style="{ left: seatPositions[i].left + '%', top: seatPositions[i].top + '%' }">
            <OpponentSeat
              :label="displayName(Number(opp.id))"
              :hand-count="opp.handCount"
              :active="opp.isCurrentPlayer"
              :disconnected="seatDisconnected(Number(opp.id))"
              :seconds-remaining="seatSecondsRemaining(Number(opp.id))" />
          </div>
        </div>

        <div class="table-center">
          <div class="claim-badge" data-test="claim-badge">
            {{ ui.table.claim }}
            <strong>
              <span v-if="targetSuit" data-test="claim-suit" aria-hidden="true"
                    :class="['claim-suit', { 'claim-suit--red': targetSuit.red }]">{{ targetSuit.glyph }}</span>
              {{ store.game?.currentTarget.label }}
            </strong>
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
                    caller: displayName(store.reveal.callerSeat),
                    claimant: displayName(store.reveal.claimantSeat),
                    picker: displayName(store.reveal.pickerSeat),
                  }) }}
                </p>
              </div>
            </Transition>
          </div>

          <p v-if="store.game?.table.state === 'CLAIM'" class="last-play" data-test="last-play">
            {{ plural(ui.table.playedFaceDown, store.game.table.count, { player: displayName(Number(store.game.table.claimantId)) }) }}
          </p>
        </div>
      </div>

      <div class="my-zone">
        <div class="my-id">
          <span :class="['my-tag', { 'my-tag--active': store.isMyTurn }]">{{ ui.you }}</span>
          <span class="my-count" data-test="my-count" role="img"
                :aria-label="plural(ui.table.myCards, myCardCount)">{{ myCardCount }}</span>
        </div>
        <BullshitHand
          :cards="store.game?.myHand ?? []"
          :selected="store.selectedCards"
          @toggle="store.toggleCard" />
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
/* The game screen is exactly one viewport tall and never scrolls: a top bar, a table that
   takes whatever height is left (flex:1, min-height:0) and a bottom zone with the hand and
   the actions, always on screen. */
.bullshit-screen {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-2) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
  height: 100vh;
  height: 100dvh;
  overflow: hidden;
  /* Vertical rhythm of the bottom zone, each tunable from one token. "You" and the hand are
     one group (small gap, measured to the raised selected card); the hand and the actions
     are two groups (larger gap). */
  --you-hand-gap: var(--space-3);
  --hand-actions-gap: var(--space-4);
  /* Paint our own felt: the shared app-background is only a backdrop for title screens.
     Mirrors the BatailleCorse game screen, which also paints its own opaque felt. */
  background:
    radial-gradient(ellipse at 50% 42%, transparent 15%, rgba(0, 0, 0, 0.62) 100%),
    radial-gradient(ellipse at 50% 38%, var(--felt-center) 0%, var(--felt-mid) 48%, var(--felt-edge) 100%);
}
/* Neutral waiting screen: the felt of the screen shows through, centred spinner and text. */
.connecting {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  color: rgba(255, 255, 255, 0.85);
  font-family: var(--font-title);
  letter-spacing: 0.04em;
}
.connecting-spinner { font-size: 1.5rem; color: var(--gold); }
/* The lobby is a title-style screen: it sits on the shared felt background instead. */
.bullshit-screen--lobby {
  display: block;
  padding: 0;
  background: none;
}

.top-bar {
  flex: none;
  display: flex;
  align-items: center;
  min-height: 48px;
  /* Leaves the corner free for the rules toggle. */
  padding-right: 120px;
}
.leave-button { text-decoration: none; }

.table-frame {
  position: relative;
  flex: 1 1 0;
  min-height: 0;
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
  /* Oval play area filling the room left between the top bar and the hand; it may stretch. */
  border-radius: 50% / 42%;
  background:
    radial-gradient(ellipse at 50% 46%, transparent 18%, rgba(0, 0, 0, 0.55) 100%),
    radial-gradient(ellipse at 50% 42%, var(--felt-center) 0%, var(--felt-mid) 52%, var(--felt-edge) 100%);
  border: 1px solid rgba(255, 255, 255, 0.06);
  box-shadow: inset 0 2px 30px rgba(0, 0, 0, 0.55), 0 10px 40px rgba(0, 0, 0, 0.45);
  isolation: isolate;
  container-type: size;
  /* Fluid card sizes consumed by seats and the center. */
  --seat-card-w: clamp(40px, 7vmin, 60px);
  /* The pile is the seat card token scaled up (1.5x): it stays the biggest card on the table
     without dwarfing the seats or the hand, at every viewport. */
  --pile-card-w: calc(var(--seat-card-w) * 1.5);
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

.table-center {
  position: absolute;
  /* Below the middle so it never runs into the seat at the top of the oval. */
  top: 64%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

/* Narrow screens: no room to spread seats around an oval, so they line up in a row at the top
   of the table (wrapping if they must) with the pile centered in the space below. */
@media (max-width: 700px) {
  .table-frame {
    display: flex;
    flex-direction: column;
    border-radius: 28px;
  }
  .opponents-ring {
    position: static;
    flex: none;
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: var(--space-2) var(--space-1);
    padding: var(--space-3) var(--space-1) 0;
    pointer-events: auto;
  }
  .seat-slot {
    position: static;
    transform: none;
    --seat-label-max: 6.5rem;
  }
  .table-center {
    position: static;
    transform: none;
    flex: 1 1 0;
    min-height: 0;
    justify-content: center;
    padding: var(--space-1) var(--space-2) var(--space-2);
    gap: var(--space-2);
  }
}

.claim-badge {
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.85);
  background: rgba(0, 0, 0, 0.45);
  border: 1px solid rgba(var(--accent-active-rgb), 0.4);
  border-radius: 999px;
  padding: var(--space-1) 14px;
}
.claim-badge strong { color: var(--gold); }
/* Suit glyph on the dark badge: light for spade/club, a lifted red for heart/diamond. */
.claim-suit { margin-right: 0.15em; color: rgba(255, 255, 255, 0.95); }
.claim-suit--red { color: #ff6b6b; }

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

/* The reveal is wider than the pile well so its caption stays readable; it is centered on
   the well with a negative margin (not a transform, which the fade transition animates). */
.reveal {
  --reveal-w: min(320px, 88vw);
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: var(--reveal-w);
  margin-left: calc(var(--reveal-w) / -2);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
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
  padding: var(--space-1) var(--space-4);
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
  padding: var(--space-1) var(--space-2);
  overflow-wrap: anywhere;
}

.last-play {
  margin: 0;
  font-size: 0.78rem;
  color: rgba(255, 255, 255, 0.7);
  overflow-wrap: anywhere;
}

.my-zone {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
  padding: var(--space-2) var(--space-2) 0;
  box-sizing: border-box;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.30) 0%, rgba(0, 0, 0, 0.04) 100%);
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 14px 14px 0 0;
}

.actions {
  display: flex;
  /* Between the two buttons: 8px, much less than the gap to the hand above. */
  gap: var(--space-2);
  width: 100%;
  justify-content: center;
  /* The room comes out of the table (flex: 1), never out of the page. */
  margin-top: var(--hand-actions-gap);
  padding-bottom: var(--space-1);
}
.actions :deep(.p-button) {
  flex: 1 1 0;
  /* Comfortable touch target. */
  min-height: 44px;
  max-width: 240px;
  min-width: 0;
  white-space: nowrap;
  padding-inline: var(--space-3);
}
@media (min-width: 400px) {
  .actions :deep(.p-button) { padding-inline: var(--space-4); }
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
  padding: var(--space-1) 14px;
}
.my-tag { position: relative; }
/* "You" and my card count form one row; the gap to the hand is carried by the row. */
.my-id {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--you-hand-gap);
}
.my-count {
  font-size: 0.8rem;
  font-weight: 700;
  font-family: var(--font-title);
  font-variant-numeric: tabular-nums;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid rgba(var(--accent-active-rgb), 0.55);
  border-radius: 999px;
  padding: var(--space-1) 10px;
  min-width: 2ch;
  text-align: center;
}
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

.forfeit-notice {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1800;
  width: max-content;
  max-width: calc(100% - 24px);
  overflow-wrap: anywhere;
  text-align: center;
}
.forfeit-fade-enter-active,
.forfeit-fade-leave-active { transition: opacity 0.25s ease, transform 0.25s ease; }
.forfeit-fade-enter-from,
.forfeit-fade-leave-to { opacity: 0; transform: translate(-50%, -8px); }
</style>
