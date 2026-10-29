<script setup lang="ts">
import { computed } from 'vue';
import BlackjackHand from './BlackjackHand.vue';
import SeatDisconnectBadge from '../SeatDisconnectBadge.vue';
import { useI18n } from '../../composables/useI18n';
import { format, ordinal } from '../../locales/format';
import type { BlackjackSeat } from '../../model/blackjack/BlackjackState';

// An opponent: name (bot level), stack, standing, bet ("Bet placed" while bets are sealed, the amount after), its
// hands as small cards, the turn highlight, the place once out and the disconnection badge. Names are user input:
// only ever rendered as text.
const props = defineProps<{
  player: BlackjackSeat;
  label: string;
  current?: boolean;
  activeHandIndex?: number;
  disconnected?: boolean;
  secondsRemaining?: number | null;
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
      <span v-if="betText" class="bj-bet" data-test="seat-bet">{{ betText }}</span>
      <span v-if="current" class="bj-turn" data-test="seat-turn">{{ ui.table.current }}</span>
      <div class="bj-hands">
        <BlackjackHand v-for="(h, i) in player.hands" :key="i" :hand="h" :card-size="34"
                       :anchor="`seat-${player.seat}-hand-${i}`" :active="current && activeHandIndex === i && player.hands.length > 1" />
      </div>
    </template>
    <span v-if="disconnected" class="bj-badge"><SeatDisconnectBadge :seconds-remaining="secondsRemaining ?? null" /></span>
  </div>
</template>

<style scoped>
.bj-plate { position: relative; box-sizing: border-box; min-width: 0; display: flex; flex-direction: column; align-items: center; gap: 2px;
  padding: 6px; border-radius: 10px; background: rgba(0, 0, 0, 0.6); border: 1px solid rgba(255, 255, 255, 0.12); color: #fff; }
.bj-plate--out { opacity: 0.55; }
.bj-plate--current { border-color: var(--gold); box-shadow: 0 0 0 2px rgba(245, 200, 66, 0.7); }
.bj-top { display: flex; align-items: baseline; gap: 4px; max-width: 100%; }
.bj-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 0.8125rem; font-weight: 700; }
.bj-level { font-size: 0.625rem; opacity: 0.7; }
.bj-stats { display: flex; gap: 8px; font-size: 0.75rem; font-variant-numeric: tabular-nums; }
.bj-bet, .bj-turn, .bj-out { font-size: 0.6875rem; font-weight: 700; }
.bj-turn { color: var(--gold); }
.bj-hands { display: flex; flex-wrap: wrap; justify-content: center; gap: 4px; }
.bj-badge { position: absolute; top: 100%; left: 50%; z-index: 2; transform: translateX(-50%); }
</style>
