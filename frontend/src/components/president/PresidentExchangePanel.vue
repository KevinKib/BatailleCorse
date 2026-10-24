<script setup lang="ts">
import { computed } from 'vue';
import { Button } from 'primevue';
import { useI18n } from '../../composables/useI18n';
import { exchangeView } from '../../model/president/exchange';
import { titleKey } from '../../model/president/titles';
import type { PresidentExchangePair, PresidentTitle } from '../../model/president/PresidentState';
import type { PresidentTitleKey } from '../../locales/Messages';
import { format, plural } from '../../locales/format';

// The exchange step at the table (round 2 and later): who I am, what I got, what I owe and to whom (the pairs of
// the others are the board's, in the pile's slot). A high rank picks the cards in their own hand (the ones received are tagged there) and gives
// them with the button; Suggest only selects. Everyone else reads a waiting line. The server judges every give.
const props = defineProps<{
  pairs: PresidentExchangePair[];
  mySeat: number;
  /** My title this round. */
  title: PresidentTitle | null;
  /** The best cards I took from my partner at the deal. */
  receivedCount: number;
  selectedCount: number;
  /** The exact number of cards is selected and nothing is in flight. */
  canGive: boolean;
  nameOf: (seat: number) => string;
}>();
const emit = defineEmits<{ give: []; suggest: []; clear: [] }>();

const ui = useI18n().presidentUi;
const view = computed(() => exchangeView(props.pairs, props.mySeat));
const partner = computed(() => view.value.partner === null ? '' : props.nameOf(view.value.partner));
const roleText = computed(() => props.title ? ui.exchange.youAre[titleKey(props.title) as PresidentTitleKey] : '');
const myPairDone = computed(() => props.pairs.find(p => p.low === props.mySeat)?.done ?? false);

const vars = computed(() => ({ player: partner.value }));
const instruction = computed(() => {
  const { role, count } = view.value;
  switch (role) {
    case 'giving': return plural(ui.exchange.choose, count, vars.value);
    case 'gave': return plural(ui.exchange.gave, count, vars.value);
    case 'taken': return plural(myPairDone.value ? ui.exchange.returned : ui.exchange.taken, count, vars.value);
    default: return ui.exchange.idle;
  }
});
const receivedText = computed(() =>
  props.receivedCount > 0 && (view.value.role === 'giving' || view.value.role === 'gave')
    ? plural(ui.exchange.received, props.receivedCount, vars.value)
    : '');
const giveLabel = computed(() => plural(ui.exchange.give, view.value.count, vars.value));
</script>

<template>
  <section class="exchange" data-test="exchange-panel" role="region" :aria-label="ui.exchange.label">
    <p class="role" data-test="exchange-role">{{ roleText }}</p>
    <p v-if="receivedText" class="received" data-test="exchange-received">{{ receivedText }}</p>
    <p class="instruction" data-test="exchange-instruction" role="status" aria-live="polite">{{ instruction }}</p>
    <div v-if="view.role === 'giving'" class="buttons">
      <Button data-test="exchange-give" class="action" :label="giveLabel" severity="success" rounded
              :disabled="!canGive" @click="emit('give')" />
      <Button data-test="exchange-suggest" class="action" :label="ui.exchange.suggest" severity="secondary"
              variant="text" rounded @click="emit('suggest')" />
      <Button v-if="selectedCount > 0" data-test="exchange-clear" class="action" :label="ui.actions.clear"
              severity="secondary" variant="text" rounded @click="emit('clear')" />
    </div>
  </section>
</template>

<style scoped>
/* The panel fills the place of the action bar (the controls place of my zone has one height for every phase), so the
   felt above never resizes when the exchange begins or ends. */
.exchange { display: flex; flex-direction: column; align-items: center; justify-content: space-between; gap: 2px; width: 100%; height: 100%; min-height: 0; overflow: hidden; }
.exchange p { margin: 0; text-align: center; }
.role { font-size: 0.85rem; font-weight: 700; color: var(--gold); }
.received { font-size: 0.7rem; line-height: 1.15; color: rgba(255, 255, 255, 0.85); }
.instruction { font-size: 0.85rem; font-weight: 700; color: #fff; }
.buttons { display: flex; flex-wrap: nowrap; justify-content: center; gap: 8px; }
.action.p-button { min-height: 44px; min-width: 88px; }
/* A phone: my title stands beside my name, so the role line is for screen readers (the board in the pile's slot says the pairs). */
@media (max-width: 767px) {
  .role {
    position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0);
    white-space: nowrap; border: 0;
  }
  .exchange { position: relative; }
}
</style>
