<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { canonicalRank } from '../../model/president/ranks';
import { overlapOf } from '../../model/blackjack/slots';
import { CARD_RATIO } from '../../animation';
import type { BlackjackDealer } from '../../model/blackjack/BlackjackState';
import type { DealerLineKind } from '../../model/blackjack/motion';

// The dealer strip: the shoe the cards come from, the dealer's cards (the hole card face down while `holeHidden`), the
// total of the shown cards, the Blackjack / Bust tags and a status line ("The dealer draws a card"). The hidden card is
// a back only: its value is never in the state. The shoe and the cards are the same size, so a card leaves the shoe and
// lands in the dealer's row without changing size.
const props = defineProps<{ dealer: BlackjackDealer; line?: { kind: DealerLineKind; total: number } | null }>();
const ui = useI18n().blackjackUi;

const CARD = 56;
const rowStyle = {
  '--bj-card-w': `${CARD}px`, '--bj-card-h': `${Math.round(CARD * CARD_RATIO)}px`, '--bj-overlap': `${overlapOf(CARD)}px`,
};
const hasCards = computed(() => props.dealer.cards.length > 0 || props.dealer.holeHidden);
const showTotal = computed(() => hasCards.value && (!props.dealer.pending || props.dealer.holeHidden));
const totalText = computed(() => format(ui.dealer.total, { total: props.dealer.total }));
const lineText = computed(() => {
  const l = props.line;
  return l ? format(ui.dealerLine[l.kind], { total: l.total }) : '';
});
</script>

<template>
  <section class="bj-dealer" data-test="dealer" :aria-label="ui.dealer.name">
    <div class="bj-shoe" data-test="shoe" data-anchor="shoe" role="img" :aria-label="ui.table.shoe">
      <PlayingCard class="bj-shoe-card bj-shoe-card--back" :size="CARD" hidden />
      <PlayingCard class="bj-shoe-card bj-shoe-card--mid" :size="CARD" hidden />
      <PlayingCard class="bj-shoe-card bj-shoe-card--front" :size="CARD" hidden />
      <span class="bj-shoe-label" aria-hidden="true">{{ ui.table.shoe }}</span>
    </div>
    <div class="bj-dealer-hand" data-anchor="dealer">
      <div class="bj-cards" data-test="dealer-cards" :style="rowStyle" :data-card-size="CARD">
        <PlayingCard v-for="(c, i) in dealer.cards" :key="`c${i}`" :rank="canonicalRank(c.rank)" :suit="c.suit" :size="CARD" />
        <PlayingCard v-if="dealer.holeHidden" :key="`c${dealer.cards.length}`" data-test="hole-card" :size="CARD" hidden :alt="ui.cardHidden" />
      </div>
      <div class="bj-meta">
        <span v-if="!hasCards" class="bj-name" data-test="dealer-name">{{ ui.dealer.name }}</span>
        <span v-if="showTotal" class="bj-total" data-test="dealer-total" :aria-label="format(ui.dealer.totalLabel, { total: dealer.total })">{{ totalText }}</span>
        <span v-if="dealer.blackjack" class="bj-tag" data-test="dealer-blackjack">{{ ui.dealer.blackjack }}</span>
        <span v-if="dealer.bust" class="bj-tag bj-tag--bust" data-test="dealer-bust">{{ ui.dealer.bust }}</span>
      </div>
      <p class="bj-line" data-test="dealer-status" role="status" aria-live="polite">
        <Transition name="line" mode="out-in"><span v-if="lineText" :key="`${line?.kind}-${line?.total}`">{{ lineText }}</span></Transition>
      </p>
    </div>
  </section>
</template>

<style scoped>
.bj-dealer { display: flex; align-items: flex-start; justify-content: center; gap: var(--space-4, 24px); color: #fff; min-height: 150px; padding-top: var(--space-1); }
.bj-shoe { position: relative; width: 76px; height: 96px; flex: none; margin-top: 4px; }
.bj-shoe :deep(.bj-shoe-card) { position: absolute; top: 0; box-shadow: var(--card-shadow); border-radius: 4px; }
.bj-shoe :deep(.bj-shoe-card--back) { left: 12px; top: 0; filter: brightness(0.7); }
.bj-shoe :deep(.bj-shoe-card--mid) { left: 6px; top: 0; filter: brightness(0.85); }
.bj-shoe :deep(.bj-shoe-card--front) { left: 0; top: 0; }
.bj-shoe-label { position: absolute; left: 0; right: 0; bottom: -2px; text-align: center; font-size: 0.625rem; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: rgba(255, 255, 255, 0.65); }
.bj-dealer-hand { display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 120px; }
.bj-cards { display: flex; justify-content: center; min-width: var(--bj-card-w); min-height: var(--bj-card-h); }
.bj-cards :deep(.playing_card) { margin-left: calc(var(--bj-overlap) * -1); box-shadow: var(--card-shadow); border-radius: 4px; }
.bj-cards :deep(.playing_card:first-child) { margin-left: 0; }
.bj-meta { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 6px; min-height: 1.4rem; font-size: 0.8125rem; }
.bj-name { opacity: 0.8; }
.bj-total { font-weight: 700; font-variant-numeric: tabular-nums; padding: 0 8px; border-radius: 999px; background: rgba(0, 0, 0, 0.45); }
.bj-tag { padding: 0 8px; border-radius: 999px; background: rgba(255, 255, 255, 0.18); font-size: 0.6875rem; font-weight: 700; }
.bj-tag--bust { background: rgba(248, 113, 113, 0.35); }
.bj-line { margin: 0; min-height: 1.25rem; font-size: 0.8125rem; font-weight: 700; color: var(--gold); text-align: center; }
.line-enter-active, .line-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.line-enter-from { opacity: 0; transform: translateY(4px); }
.line-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) { .line-enter-active, .line-leave-active { transition: none; } }
</style>
