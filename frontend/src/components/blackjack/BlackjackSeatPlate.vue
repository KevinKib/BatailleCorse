<script setup lang="ts">
import { computed } from 'vue';
import BlackjackChips from './BlackjackChips.vue';
import BlackjackHand from './BlackjackHand.vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import { useI18n } from '../../composables/useI18n';
import { format, ordinal } from '../../locales/format';
import type { BlackjackSeat } from '../../model/blackjack/BlackjackState';
import type { TagKind } from '../../model/blackjack/motion';
import { handKey, roundNetOf } from '../../model/blackjack/shown';

// An opponent: name (bot level), stack, standing, bet (chips; "Bet placed" while bets are sealed, the amount after), its
// hands as small cards, the turn highlight, what it just did (a tag that pops, then the last action stays), the place
// once out and the disconnection badge. Names are user input: only ever rendered as text.
const props = defineProps<{
  player: BlackjackSeat;
  label: string;
  current?: boolean;
  activeHandIndex?: number;
  disconnected?: boolean;
  secondsRemaining?: number | null;
  /** What the seat just did, popped over the plate for a moment. */
  tag?: { kind: TagKind; key: number } | null;
  /** The last thing the seat did this hand, kept on the plate. */
  lastAction?: TagKind | null;
}>();
const messages = useI18n();
const ui = messages.blackjackUi;

const level = computed(() => {
  if (!props.player.bot || !props.player.level) return '';
  const levels = messages.options.blackjack.level as unknown as Record<string, string>;
  return levels[props.player.level] ?? props.player.level;
});
const outText = computed(() => props.player.place === null
  ? ui.table.out
  : format(ui.table.outPlace, { place: ordinal(props.player.place, ui.ordinal) }));
const betText = computed(() => {
  const p = props.player;
  if (p.bet !== null) return format(ui.table.bet, { n: p.bet });
  return p.betPlaced ? ui.table.betPlaced : '';
});
const result = computed(() => {
  const net = roundNetOf(props.player);
  return net === null ? null : net > 0 ? 'win' : net < 0 ? 'lose' : 'push';
});
</script>

<template>
  <div class="bj-plate" :class="{ 'bj-plate--out': player.eliminated || player.forfeited, 'bj-plate--current': current }"
       role="group" :aria-label="`${label}, ${format(ui.table.stackLabel, { n: player.stack })}`"
       :aria-current="current ? 'true' : undefined" data-test="seat-plate" :data-seat="player.seat"
       :data-current="current ? 'true' : undefined">
    <div class="bj-top">
      <span class="bj-name" data-test="seat-name" :title="label">{{ label }}</span>
      <span v-if="level" class="bj-level" data-test="seat-level">{{ level }}</span>
    </div>
    <div class="bj-stats">
      <span data-test="seat-stack">{{ format(ui.table.stack, { n: player.stack }) }}</span>
      <span v-if="player.standing > 0" data-test="seat-standing">{{ format(ui.table.standing, { place: player.standing }) }}</span>
    </div>
    <span v-if="player.eliminated || player.forfeited" class="bj-out" data-test="seat-out">{{ player.forfeited ? ui.end.forfeited : outText }}</span>
    <template v-else>
      <div class="bj-bet-row">
        <BlackjackChips v-if="player.betPlaced || player.bet !== null" :amount="player.bet" :sealed="player.bet === null"
                        :result="result" compact />
      </div>
      <span v-if="betText" class="bj-bet" data-test="seat-bet">{{ betText }}</span>
      <span v-if="current" class="bj-turn" data-test="seat-turn">{{ ui.table.current }}</span>
      <span v-else-if="lastAction" class="bj-last" data-test="seat-last">{{ ui.tags[lastAction] }}</span>
      <div class="bj-hands">
        <BlackjackHand v-for="(h, i) in player.hands" :key="i" :hand="h" :card-size="34"
                       :anchor="handKey(player.seat, i)" :active="current && activeHandIndex === i && player.hands.length > 1" />
      </div>
    </template>
    <Transition name="tag">
      <span v-if="tag" :key="tag.key" class="bj-tag-pop" :class="`bj-tag-pop--${tag.kind}`" data-test="seat-tag" :data-kind="tag.kind">{{ ui.tags[tag.kind] }}</span>
    </Transition>
    <span v-if="disconnected" class="bj-badge"><SeatDisconnectBadge :seconds-remaining="secondsRemaining ?? null" /></span>
  </div>
</template>

<style scoped>
.bj-plate { position: relative; box-sizing: border-box; min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 6px; border-radius: 10px; background: rgba(0, 0, 0, 0.55); border: 1px solid rgba(255, 255, 255, 0.14); color: #fff;
  backdrop-filter: blur(2px); transition: border-color 0.2s ease, box-shadow 0.2s ease; }
.bj-plate--out { opacity: 0.55; }
.bj-plate--current { border-color: var(--gold); box-shadow: 0 0 0 2px rgba(245, 200, 66, 0.7), 0 0 18px rgba(245, 200, 66, 0.35); }
.bj-top { display: flex; align-items: baseline; gap: 4px; max-width: 100%; }
.bj-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.8125rem; font-weight: 700; }
.bj-level { font-size: 0.625rem; opacity: 0.7; }
.bj-stats { display: flex; gap: 8px; font-size: 0.75rem; font-variant-numeric: tabular-nums; }
.bj-bet, .bj-turn, .bj-out, .bj-last { font-size: 0.6875rem; font-weight: 700; min-height: 1rem; }
.bj-bet-row { min-height: 24px; display: flex; align-items: flex-end; }
.bj-last { color: rgba(255, 255, 255, 0.75); }
.bj-turn { color: var(--gold); }
.bj-hands { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px; }
.bj-tag-pop { position: absolute; z-index: 3; top: -12px; left: 50%; transform: translateX(-50%); white-space: nowrap; padding: 2px 12px; border-radius: 999px;
  font-size: 0.8125rem; font-weight: 800; color: #1b1405; background: var(--gold); box-shadow: 0 3px 12px rgba(0, 0, 0, 0.55); }
.bj-tag-pop--bust { background: #f87171; color: #fff; }
.bj-tag-pop--stand { background: #e5e7eb; }
.tag-enter-active { transition: opacity 0.2s ease, transform 0.25s cubic-bezier(.2, .8, .2, 1); }
.tag-leave-active { transition: opacity 0.3s ease; }
.tag-enter-from { opacity: 0; transform: translate(-50%, 8px) scale(0.8); }
.tag-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) { .tag-enter-active, .tag-leave-active, .bj-plate { transition: none; } }
.bj-badge { position: absolute; top: 100%; left: 50%; z-index: 2; transform: translateX(-50%); }
</style>
