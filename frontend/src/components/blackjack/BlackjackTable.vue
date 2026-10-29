<script setup lang="ts">
import BlackjackDealer from './BlackjackDealer.vue';
import BlackjackHandSummary from './BlackjackHandSummary.vue';
import BlackjackMySeat from './BlackjackMySeat.vue';
import BlackjackSeatPlate from './BlackjackSeatPlate.vue';
import { useI18n } from '../../composables/useI18n';
import type { BlackjackSeat, BlackjackState } from '../../model/blackjack/BlackjackState';
import type { DealerLineKind, TagKind } from '../../model/blackjack/motion';

// The table, top to bottom, on its felt: the dealer strip with the shoe, the last hand summary (during betting), the
// opponents (2 columns on a phone, one row on a wide felt; none in solo) and my seat. The server's state decides
// everything shown; `game` is the state as the table has told it so far. A banner over the felt announces each beat of
// a hand (bets, deal, dealer, results).
const props = defineProps<{
  game: BlackjackState;
  opponents: BlackjackSeat[];
  me: BlackjackSeat | null;
  mySeat: number;
  nameOf: (seat: number) => string;
  disconnected: (seat: number) => boolean;
  secondsRemaining: (seat: number) => number | null;
  tags?: Record<number, { kind: TagKind; key: number }>;
  lastAction?: Record<number, TagKind>;
  dealerLine?: { kind: DealerLineKind; total: number } | null;
  /** The text of the banner of the beat, when there is one. */
  banner?: { key: number; text: string } | null;
}>();
const ui = useI18n().blackjackUi;
const playing = () => props.game.phase === 'PLAYING';
</script>

<template>
  <div class="bj-table" data-test="blackjack-table">
    <div class="bj-felt" aria-hidden="true"></div>
    <div class="bj-rim" aria-hidden="true"></div>
    <div class="bj-content">
      <BlackjackDealer :dealer="game.dealer" :line="dealerLine" />
      <BlackjackHandSummary v-if="game.phase === 'BETTING' && game.lastHand" :last-hand="game.lastHand" :my-seat="mySeat" :name-of="nameOf" />
      <div v-if="opponents.length > 0" class="bj-opponents" data-test="opponents" role="group" :aria-label="ui.table.opponents">
        <BlackjackSeatPlate v-for="o in opponents" :key="o.seat" :player="o" :label="nameOf(o.seat)"
                            :current="playing() && game.currentSeat === o.seat" :active-hand-index="game.activeHandIndex"
                            :disconnected="disconnected(o.seat)" :seconds-remaining="secondsRemaining(o.seat)"
                            :tag="tags?.[o.seat] ?? null" :last-action="lastAction?.[o.seat] ?? null" />
      </div>
      <BlackjackMySeat v-if="me" :player="me" :current="playing() && game.currentSeat === me.seat" :active-hand-index="game.activeHandIndex"
                       :tag="tags?.[me.seat] ?? null" />
    </div>
    <Transition name="banner">
      <div v-if="banner" :key="banner.key" class="bj-banner" data-test="phase-banner" role="status">{{ banner.text }}</div>
    </Transition>
  </div>
</template>

<style scoped>
/* The felt: the table look every card table shares (room and felt tokens), a gold rim, a faint arc as on a casino table. */
.bj-table { position: relative; isolation: isolate; display: flex; flex-direction: column; flex: 1 1 auto; min-height: 0; overflow: hidden; width: 100%; max-width: 1000px; margin: 0 auto;
  border-radius: 28px; }
.bj-felt { position: absolute; inset: 0; z-index: -2; border-radius: inherit; background: var(--table-felt-bg); border: var(--table-felt-border); box-shadow: var(--table-felt-shadow); }
.bj-felt::after { content: ''; position: absolute; inset: 0; border-radius: inherit; background-image: var(--felt-noise); opacity: 0.06; mix-blend-mode: overlay; pointer-events: none; }
.bj-rim { position: absolute; inset: 6px; z-index: -1; border-radius: 22px; border: 1px solid rgba(245, 200, 66, 0.28); pointer-events: none;
  background: radial-gradient(ellipse 60% 22% at 50% 34%, transparent 62%, rgba(245, 200, 66, 0.07) 64%, transparent 66%); }
.bj-content { flex: 1 1 auto; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-3) var(--space-2) var(--space-2); }
/* The dealer and my seat stay in view while the plates scroll between them: the cards arrive where they can be seen. */
.bj-content :deep(.bj-dealer) { position: sticky; top: 0; z-index: 2; background: linear-gradient(var(--felt-mid), rgba(13, 46, 24, 0.9)); border-radius: 14px; }
.bj-content :deep(.bj-mine) { position: sticky; bottom: 0; z-index: 2; background: linear-gradient(rgba(0, 0, 0, 0.4), rgba(0, 0, 0, 0.4)), var(--felt-mid); }
.bj-opponents { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-3) var(--space-2); }
@media (min-width: 900px) {
  .bj-opponents { grid-template-columns: repeat(5, minmax(0, 1fr)); }
}
.bj-banner { position: absolute; z-index: 5; left: 50%; top: 38%; transform: translate(-50%, -50%); pointer-events: none; padding: 10px 28px; border-radius: 999px;
  font-family: var(--font-title); font-size: 1.25rem; font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; color: var(--gold); white-space: nowrap;
  background: rgba(0, 0, 0, 0.72); border: 1px solid rgba(245, 200, 66, 0.6); box-shadow: 0 8px 30px rgba(0, 0, 0, 0.6); }
.banner-enter-active { transition: opacity 0.25s ease, transform 0.3s cubic-bezier(.2, .8, .2, 1); }
.banner-leave-active { transition: opacity 0.35s ease; }
.banner-enter-from { opacity: 0; transform: translate(-50%, -30%) scale(0.9); }
.banner-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) { .banner-enter-active, .banner-leave-active { transition: none; } }
</style>
