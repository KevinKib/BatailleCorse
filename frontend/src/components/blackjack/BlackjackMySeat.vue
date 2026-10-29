<script setup lang="ts">
import { computed } from 'vue';
import BlackjackChips from './BlackjackChips.vue';
import BlackjackHand from './BlackjackHand.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type { BlackjackSeat } from '../../model/blackjack/BlackjackState';
import type { TagKind } from '../../model/blackjack/motion';
import { handKey, roundNetOf } from '../../model/blackjack/shown';

// My seat, larger, at the bottom: stack and standing, my bet as chips, my one or two hands side by side (the active one
// outlined) with their totals and, once settled, their nets. When it is my turn the seat glows and says so; the line
// keeps its room the rest of the time, so nothing moves when the turn comes.
const props = defineProps<{
  player: BlackjackSeat;
  current?: boolean;
  activeHandIndex?: number;
  /** What I just did, popped over my seat for a moment. */
  tag?: { kind: TagKind; key: number } | null;
}>();
const ui = useI18n().blackjackUi;

// Once every hand is settled, the round's total net sits next to the stack (signed, never colour alone).
const roundNet = computed(() => roundNetOf(props.player));
const roundNetText = computed(() => roundNet.value === null ? '' : roundNet.value > 0 ? `+${roundNet.value}` : String(roundNet.value));
const result = computed(() => roundNet.value === null ? null : roundNet.value > 0 ? 'win' : roundNet.value < 0 ? 'lose' : 'push');
</script>

<template>
  <section class="bj-mine" :class="{ 'bj-mine--current': current }" data-test="my-seat" :data-seat="player.seat"
           :aria-label="ui.table.yourSeat">
    <p class="bj-turn" :class="{ 'bj-turn--on': current }" data-test="my-turn" :aria-hidden="current ? undefined : 'true'">{{ current ? ui.wait.yourTurn : ' ' }}</p>
    <div class="bj-stats">
      <strong data-test="my-stack" :aria-label="format(ui.table.stackLabel, { n: player.stack })">{{ format(ui.table.stack, { n: player.stack }) }}</strong>
      <span v-if="roundNet !== null" class="bj-round-net" :class="{ 'bj-round-net--win': roundNet > 0, 'bj-round-net--lose': roundNet < 0 }"
            data-test="my-net">{{ roundNetText }}</span>
      <span v-if="player.standing > 0" data-test="my-standing">{{ format(ui.table.standing, { place: player.standing }) }}</span>
      <span v-else-if="player.eliminated || player.forfeited" data-test="my-out">{{ ui.table.out }}</span>
      <span v-if="player.bet !== null" data-test="my-bet">{{ format(ui.table.bet, { n: player.bet }) }}</span>
      <span v-else-if="player.betPlaced" data-test="my-bet">{{ ui.table.betPlaced }}</span>
    </div>
    <div class="bj-bet-row">
      <BlackjackChips v-if="player.betPlaced || player.bet !== null" :amount="player.bet" :sealed="player.bet === null" :result="result" />
    </div>
    <div class="bj-hands">
      <BlackjackHand v-for="(h, i) in player.hands" :key="i" :hand="h" :card-size="64"
                     :anchor="handKey(player.seat, i)" :active="current && activeHandIndex === i && player.hands.length > 1" />
    </div>
    <Transition name="tag">
      <span v-if="tag" :key="tag.key" class="bj-tag-pop" :class="`bj-tag-pop--${tag.kind}`" data-test="my-tag" :data-kind="tag.kind">{{ ui.tagsYou[tag.kind] }}</span>
    </Transition>
  </section>
</template>

<style scoped>
.bj-mine { position: relative; display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 8px; border-radius: 14px; color: #fff;
  background: rgba(0, 0, 0, 0.4); border: 1px solid rgba(245, 200, 66, 0.55); min-height: 176px; transition: box-shadow 0.25s ease, border-color 0.25s ease; }
.bj-mine--current { border-color: var(--gold); box-shadow: 0 0 0 2px rgba(245, 200, 66, 0.8), 0 0 28px rgba(245, 200, 66, 0.45); animation: my-glow 1.6s ease-in-out infinite; }
.bj-turn { margin: 0; min-height: 1.25rem; font-size: 0.8125rem; font-weight: 800; letter-spacing: 0.04em; text-transform: uppercase; color: transparent; }
.bj-turn--on { color: var(--gold); }
.bj-stats { display: flex; gap: 12px; align-items: baseline; font-size: 0.875rem; font-variant-numeric: tabular-nums; }
.bj-round-net { font-weight: 700; }
.bj-round-net--win { color: #4ade80; }
.bj-round-net--lose { color: #f87171; }
.bj-bet-row { min-height: 28px; display: flex; align-items: flex-end; }
.bj-hands { display: flex; flex-wrap: wrap; justify-content: center; gap: 12px; }
.bj-tag-pop { position: absolute; z-index: 3; top: -14px; left: 50%; transform: translateX(-50%); white-space: nowrap; padding: 3px 14px; border-radius: 999px;
  font-size: 0.9375rem; font-weight: 800; color: #1b1405; background: var(--gold); box-shadow: 0 3px 12px rgba(0, 0, 0, 0.55); }
.bj-tag-pop--bust { background: #f87171; color: #fff; }
.bj-tag-pop--stand { background: #e5e7eb; }
.tag-enter-active { transition: opacity 0.2s ease, transform 0.25s cubic-bezier(.2, .8, .2, 1); }
.tag-leave-active { transition: opacity 0.3s ease; }
.tag-enter-from { opacity: 0; transform: translate(-50%, 8px) scale(0.8); }
.tag-leave-to { opacity: 0; }
@keyframes my-glow { 0%, 100% { box-shadow: 0 0 0 2px rgba(245, 200, 66, 0.8), 0 0 18px rgba(245, 200, 66, 0.3); } 50% { box-shadow: 0 0 0 3px rgba(245, 200, 66, 1), 0 0 34px rgba(245, 200, 66, 0.6); } }
@media (prefers-reduced-motion: reduce) { .bj-mine, .bj-mine--current { animation: none; transition: none; } .tag-enter-active, .tag-leave-active { transition: none; } }
</style>
