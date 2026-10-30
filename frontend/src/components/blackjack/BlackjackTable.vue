<script setup lang="ts">
import { computed } from 'vue';
import BlackjackDealer from './BlackjackDealer.vue';
import BlackjackHandSummary from './BlackjackHandSummary.vue';
import BlackjackMySeat from './BlackjackMySeat.vue';
import BlackjackSeatPlate from './BlackjackSeatPlate.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type { BlackjackSeat, BlackjackState } from '../../model/blackjack/BlackjackState';
import type { DealerLineKind, TagKind } from '../../model/blackjack/motion';

// The table, top to bottom, on its felt (layout "B" of the design canvas):
//   1. the dealer zone: label + total, then the cards and the shoe;
//   2. the status rail, ONE row of fixed height between the dealer and the opponents. Every announcement is told
//      here and nowhere else: the banner of the beat (bets, deal, dealer, results), what a seat just did (hit, stand...)
//      and what the dealer does. Status text never sits over a card, so cards and text never share a box;
//   3. the last hand summary (during betting) and the opponents (3 columns on a phone, one row on a wide felt; none in
//      solo): their cards and a one-line total or result;
//   4. my seat: header, cards and the permanent dock (`dock` slot: bets, or actions that are only disabled when it is
//      not my turn; to the right of the cards on a wide felt).
// The server's state decides everything shown; `game` is the state as the table has told it so far.
//
// SPACING (the Blackjack scale, defined once in App.vue, rem on a 4/8 grid). Every margin, padding and gap of the
// Blackjack components uses one of these, and a test enforces it:
//   --bj-space-1  4 px  inside one item (a card to its total, tags of one hand)
//   --bj-space-2  8 px  between items of one group (plate content, cards of a row)
//   --bj-space-3 16 px  inner padding of a panel; gutter between zones; 16 px page gutter at 375 px
//   --bj-space-4 24 px  between the big pieces on a wide felt (shoe and cards, plates)
//   --bj-space-5 32 px  rare, page level
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

// What the rail says, one thing at a time: the banner of the beat first, then the latest action of a seat, then the
// dealer's line.
const railTag = computed(() => {
  const entries = Object.entries(props.tags ?? {});
  if (entries.length === 0) return null;
  const [seat, tag] = entries.reduce((a, b) => (b[1].key > a[1].key ? b : a));
  return { seat: Number(seat), ...tag };
});
const railTagText = computed(() => {
  const t = railTag.value;
  if (!t) return '';
  return t.seat === props.mySeat ? ui.tagsYou[t.kind] : `${props.nameOf(t.seat)} · ${ui.tags[t.kind]}`;
});
const railLineText = computed(() => {
  const l = props.dealerLine;
  return l ? format(ui.dealerLine[l.kind], { total: l.total }) : '';
});
</script>

<template>
  <div class="bj-table" data-test="blackjack-table">
    <div class="bj-felt" aria-hidden="true"></div>
    <div class="bj-rim" aria-hidden="true"></div>
    <div class="bj-content">
      <BlackjackDealer :dealer="game.dealer" />
      <div class="bj-rail" data-test="status-rail">
        <Transition name="rail" mode="out-in">
          <div v-if="banner" :key="`b${banner.key}`" class="bj-banner" data-test="phase-banner" role="status">{{ banner.text }}</div>
          <div v-else-if="railTag" :key="`t${railTag.key}`" class="bj-banner bj-banner--tag" :data-test="railTag.seat === mySeat ? 'my-tag' : 'seat-tag'"
               :data-seat="railTag.seat" :data-kind="railTag.kind">{{ railTagText }}</div>
          <div v-else-if="railLineText" :key="`d${dealerLine?.kind}-${dealerLine?.total}`" class="bj-banner bj-banner--line" data-test="dealer-status"
               role="status">{{ railLineText }}</div>
        </Transition>
      </div>
      <BlackjackHandSummary v-if="game.phase === 'BETTING' && game.lastHand" :last-hand="game.lastHand" :my-seat="mySeat" :name-of="nameOf" />
      <div v-if="opponents.length > 0" class="bj-opponents" data-test="opponents" role="group" :aria-label="ui.table.opponents">
        <BlackjackSeatPlate v-for="o in opponents" :key="o.seat" :player="o" :label="nameOf(o.seat)"
                            :current="playing() && game.currentSeat === o.seat" :active-hand-index="game.activeHandIndex"
                            :disconnected="disconnected(o.seat)" :seconds-remaining="secondsRemaining(o.seat)"
                            :last-action="lastAction?.[o.seat] ?? null" />
      </div>
      <BlackjackMySeat v-if="me" :player="me" :current="playing() && game.currentSeat === me.seat" :active-hand-index="game.activeHandIndex">
        <template v-if="$slots.dock" #dock><slot name="dock" /></template>
      </BlackjackMySeat>
    </div>
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
/* The zones are 16 px apart; the felt keeps the same 16 px inside its edge. */
.bj-content { flex: 1 1 auto; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: var(--bj-space-3); padding: var(--bj-space-3); }
/* The dealer and my seat stay in view while the plates scroll between them: the cards arrive where they can be seen. */
.bj-content :deep(.bj-dealer) { position: sticky; top: 0; z-index: 2; background: linear-gradient(var(--felt-mid), rgba(13, 46, 24, 0.9)); border-radius: 14px; }
.bj-content :deep(.bj-mine) { position: sticky; bottom: 0; z-index: 2; background: linear-gradient(rgba(0, 0, 0, 0.4), rgba(0, 0, 0, 0.4)), var(--felt-mid); }
/* The status rail: 48 px high whatever it says, bordered in gold at 25 %, always there so nothing jumps. */
.bj-rail { flex: none; box-sizing: border-box; height: 3rem; display: flex; align-items: center; justify-content: center; min-width: 0;
  border-top: 1px solid rgba(245, 200, 66, 0.25); border-bottom: 1px solid rgba(245, 200, 66, 0.25); }
.bj-banner { max-width: 100%; box-sizing: border-box; overflow: hidden; text-overflow: ellipsis; padding: var(--bj-space-1) var(--bj-space-4); border-radius: 999px;
  font-family: var(--font-title); font-size: 0.875rem; font-weight: 800; letter-spacing: 0.08em; text-transform: uppercase; color: var(--gold); white-space: nowrap;
  background: rgba(0, 0, 0, 0.6); border: 1px solid rgba(245, 200, 66, 0.6); }
.bj-banner--tag, .bj-banner--line { letter-spacing: 0.04em; }
.bj-opponents { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--bj-space-2); }
@media (min-width: 900px) {
  .bj-opponents { grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--bj-space-4); }
}
.rail-enter-active { transition: opacity 0.25s ease; }
.rail-leave-active { transition: opacity 0.2s ease; }
.rail-enter-from, .rail-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) { .rail-enter-active, .rail-leave-active { transition: none; } }
</style>
