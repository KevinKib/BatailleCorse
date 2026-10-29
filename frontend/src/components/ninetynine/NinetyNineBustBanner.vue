<script setup lang="ts">
import { computed } from 'vue';
import PlayingCard from '../PlayingCard.vue';
import { useI18n } from '../../composables/useI18n';
import { format, plural } from '../../locales/format';
import { cardLabel } from '../../model/president/cardLabel';
import { NINETY_NINE_TEMPO } from '../../model/ninetynine/tempo';
import type { NinetyNineLastBust } from '../../model/ninetynine/NinetyNineState';

// The end of a round: who is stuck and at what total, the stuck hand turned face up (it proves that no card could be
// played), the life lost (or the elimination) and, while a next round is coming, the pause before it. The server
// decides all of it; this only shows it. Names are user input: only ever rendered as text.
const props = defineProps<{
  bust: NinetyNineLastBust;
  /** The stuck player, as the table names them. */
  label: string;
  /** The stuck player is me. */
  mine: boolean;
}>();
const ui = useI18n().ninetyNineUi;

const title = computed(() => format(props.mine ? ui.bust.stuckYou : ui.bust.stuck, { player: props.label, total: props.bust.totalBefore }));
const outcome = computed(() => props.bust.eliminated
  ? format(props.mine ? ui.bust.eliminatedYou : ui.bust.eliminated, { player: props.label })
  : plural(ui.bust.livesLeft, props.bust.livesLeft));
const eliminatedText = computed(() => format(props.mine ? ui.bust.eliminatedBannerYou : ui.bust.eliminatedBanner, { player: props.label }));
</script>

<template>
  <div class="bust" :class="{ 'bust--eliminated': bust.eliminated }" role="alert" data-test="bust">
    <strong v-if="bust.eliminated" class="bust-eliminated" data-test="bust-eliminated">
      <i class="pi pi-times-circle" aria-hidden="true"></i>
      {{ eliminatedText }}
    </strong>
    <strong class="bust-title" data-test="bust-title">{{ title }}</strong>
    <div class="bust-hand" data-test="bust-hand" role="group" :aria-label="mine ? ui.bust.handLabelYou : format(ui.bust.handLabel, { player: label })">
      <span v-for="card in bust.hand" :key="card.name" class="bust-card" data-test="bust-card" role="img"
            :aria-label="cardLabel(card, ui, false)">
        <PlayingCard :rank="card.rank" :suit="card.suit" />
      </span>
    </div>
    <span class="bust-reason" data-test="bust-reason">{{ ui.bust.reason }}</span>
    <span class="bust-lives" data-test="bust-lives">{{ outcome }}</span>
    <div class="bust-bar" aria-hidden="true"><div class="bust-bar-fill" data-test="bust-bar-fill" :style="{ animationDuration: `${NINETY_NINE_TEMPO.bustShownMs}ms` }"></div></div>
    <span class="bust-next" data-test="bust-next">{{ ui.bust.next }}</span>
  </div>
</template>

<style scoped>
.bust {
  box-sizing: border-box;
  /* Narrow enough to sit between the side plates of a 12-seat ring on a phone (the table sets it wider on a wide felt). */
  width: min(100%, var(--nn-bust-w, 11rem));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: 14px;
  background: rgba(10, 11, 11, 0.92);
  border: 1px solid rgba(248, 113, 113, 0.7);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.6);
  text-align: center;
}
/* An elimination cannot be missed: a solid red band across the top of the banner, a thicker red border. */
.bust--eliminated { border-width: 2px; border-color: #f87171; }
.bust-eliminated {
  box-sizing: border-box;
  width: calc(100% + var(--space-3) * 2);
  margin: calc(var(--space-3) * -1) calc(var(--space-3) * -1) 0;
  padding: var(--space-2) var(--space-3);
  border-radius: 12px 12px 0 0;
  background: #f87171;
  color: #141413;
  font-size: 1.0625rem;
  overflow-wrap: anywhere;
}
.bust-title { font-size: 1rem; overflow-wrap: anywhere; }
.bust-hand { display: flex; gap: var(--space-2); }
.bust-card { display: block; width: 44px; }
.bust-card :deep(.playing_card) { display: block; width: 100%; height: auto; aspect-ratio: 167.575 / 243.1375; border: 1px solid #000; border-radius: 6%; }
.bust-reason { font-size: 0.75rem; color: rgba(255, 255, 255, 0.8); }
.bust-lives { font-size: 0.8125rem; font-weight: 700; color: #fecaca; overflow-wrap: anywhere; }
.bust-bar { width: 100%; height: 4px; border-radius: 2px; background: rgba(255, 255, 255, 0.15); overflow: hidden; }
/* The pause before the next deal, drawn as a bar that fills; a picture only, the deal never waits for it. */
.bust-bar-fill { width: 100%; height: 100%; background: var(--gold); transform-origin: left; animation: bust-fill 7.5s linear both; /* duration set inline from NINETY_NINE_TEMPO.bustShownMs */ }
.bust-next { font-size: 0.6875rem; color: rgba(255, 255, 255, 0.75); }
@keyframes bust-fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
@media (prefers-reduced-motion: reduce) { .bust-bar-fill { animation: none; } }
</style>
