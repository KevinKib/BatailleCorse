<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { canonicalRank } from '../../model/president/ranks';
import type { BlackjackHand } from '../../model/blackjack/BlackjackState';

// One hand: its cards, its total (a soft total reads "7 / 17"), its tags and, once settled, its net. Every number is
// the server's; the client counts nothing. The net is always written (+10 / -10 / Push), never colour alone.
const props = defineProps<{
  hand: BlackjackHand;
  /** Card width in px. */
  cardSize?: number;
  /** The hand being played. */
  active?: boolean;
  /** The place the flights use (`seat-<n>-hand-<i>`). */
  anchor?: string;
}>();
const ui = useI18n().blackjackUi;

const totalText = computed(() => props.hand.soft && props.hand.total <= 21
  ? format(ui.table.soft, { low: props.hand.total - 10, high: props.hand.total })
  : String(props.hand.total));
const tags = computed(() => {
  const out: { key: string; text: string }[] = [];
  const h = props.hand;
  if (h.status === 'BLACKJACK') out.push({ key: 'blackjack', text: ui.status.blackjack });
  if (h.status === 'BUST') out.push({ key: 'bust', text: ui.status.bust });
  if (h.doubled) out.push({ key: 'double', text: ui.status.double });
  if (h.fromSplit) out.push({ key: 'split', text: ui.status.split });
  return out;
});
const netText = computed(() => {
  const net = props.hand.net;
  if (net === undefined) return '';
  if (net > 0) return `${ui.status.win} +${net}`;
  if (net < 0) return `${ui.status.lose} ${net}`;
  return ui.status.push;
});
</script>

<template>
  <div class="bj-hand" :class="{ 'bj-hand--active': active }" data-test="hand" :data-anchor="anchor"
       :data-active="active ? 'true' : undefined">
    <div class="bj-cards">
      <PlayingCard v-for="c in hand.cards" :key="c.name" :rank="canonicalRank(c.rank)" :suit="c.suit" :size="cardSize ?? 56" />
    </div>
    <div class="bj-meta">
      <span class="bj-total" data-test="hand-total" :aria-label="format(ui.table.total, { total: totalText })">{{ totalText }}</span>
      <span v-for="t in tags" :key="t.key" class="bj-tag" :data-test="`tag-${t.key}`">{{ t.text }}</span>
      <span v-if="netText" class="bj-net" :class="{ 'bj-net--win': (hand.net ?? 0) > 0, 'bj-net--lose': (hand.net ?? 0) < 0 }"
            data-test="hand-net">{{ netText }}</span>
    </div>
  </div>
</template>

<style scoped>
.bj-hand { display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 4px; border-radius: 8px; border: 2px solid transparent; }
.bj-hand--active { border-color: var(--gold); }
.bj-cards { display: flex; justify-content: center; }
.bj-cards :deep(.playing_card) { margin-left: -14px; }
.bj-cards :deep(.playing_card:first-child) { margin-left: 0; }
.bj-meta { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 4px; font-size: 0.75rem; color: #fff; }
.bj-total { font-weight: 700; font-variant-numeric: tabular-nums; }
.bj-tag { padding: 0 6px; border-radius: 999px; background: rgba(255, 255, 255, 0.18); font-size: 0.6875rem; font-weight: 700; }
.bj-net { font-weight: 700; padding: 0 6px; border-radius: 999px; background: rgba(0, 0, 0, 0.5); }
.bj-net--win { color: #4ade80; }
.bj-net--lose { color: #f87171; }
</style>
