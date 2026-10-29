<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import { canonicalRank } from '../../model/president/ranks';
import type { BlackjackDealer } from '../../model/blackjack/BlackjackState';

// The dealer strip: the shoe, the dealer's cards (the hole card face down while `holeHidden`), the total of the
// shown cards and the Blackjack / Bust tags. The hidden card is a back only: its value is never in the state.
const props = defineProps<{ dealer: BlackjackDealer }>();
const ui = useI18n().blackjackUi;

const hasCards = computed(() => props.dealer.cards.length > 0 || props.dealer.holeHidden);
const totalText = computed(() => format(ui.dealer.total, { total: props.dealer.total }));
</script>

<template>
  <section class="bj-dealer" data-test="dealer" :aria-label="ui.dealer.name">
    <div class="bj-shoe" data-test="shoe" data-anchor="shoe" aria-hidden="true">
      <PlayingCard :size="36" hidden />
    </div>
    <div class="bj-dealer-hand" data-anchor="dealer">
      <div class="bj-cards" data-test="dealer-cards">
        <PlayingCard v-for="c in dealer.cards" :key="c.name" :rank="canonicalRank(c.rank)" :suit="c.suit" :size="56" />
        <PlayingCard v-if="dealer.holeHidden" data-test="hole-card" :size="56" hidden :alt="ui.cardHidden" />
      </div>
      <div class="bj-meta">
        <span v-if="!hasCards" class="bj-name" data-test="dealer-name">{{ ui.dealer.name }}</span>
        <span v-if="hasCards" class="bj-total" data-test="dealer-total" :aria-label="format(ui.dealer.totalLabel, { total: dealer.total })">{{ totalText }}</span>
        <span v-if="dealer.blackjack" class="bj-tag" data-test="dealer-blackjack">{{ ui.dealer.blackjack }}</span>
        <span v-if="dealer.bust" class="bj-tag bj-tag--bust" data-test="dealer-bust">{{ ui.dealer.bust }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.bj-dealer { display: flex; align-items: center; justify-content: center; gap: var(--space-3); color: #fff; min-height: 110px; }
.bj-shoe { opacity: 0.7; }
.bj-dealer-hand { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.bj-cards { display: flex; min-height: 80px; }
.bj-cards :deep(.playing_card) { margin-left: -14px; }
.bj-cards :deep(.playing_card:first-child) { margin-left: 0; }
.bj-meta { display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 6px; font-size: 0.8125rem; }
.bj-name { opacity: 0.8; }
.bj-total { font-weight: 700; font-variant-numeric: tabular-nums; }
.bj-tag { padding: 0 8px; border-radius: 999px; background: rgba(255, 255, 255, 0.18); font-size: 0.6875rem; font-weight: 700; }
.bj-tag--bust { background: rgba(248, 113, 113, 0.35); }
</style>
