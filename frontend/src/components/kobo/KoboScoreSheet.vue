<script setup lang="ts">
import { computed } from 'vue';
import { Button } from 'primevue';
import KoboRoundScores from './KoboRoundScores.vue';
import type { KoboState } from '../../model/kobo/KoboState';
import type { KoboNotice } from '../../state/Kobo.store';
import { roundRows, roundVerdict } from '../../model/kobo/roundScores';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';

// The score sheet: it rises under the table at the gift and at the end of a round, the table and the revealed
// cards staying on screen above it. Verdict, scores of the round, then the one thing to do (give the 10 points,
// or "Next round"). It decides nothing: the buttons come from `availableActions`, the numbers from `lastRound`.
const props = defineProps<{
  game: KoboState;
  mySeat: number;
  nameOf: (seat: number) => string;
  busy: boolean;
  /** Why my last command did not go through (the action bar, which says it elsewhere, is not on screen now). */
  notice?: KoboNotice | null;
}>();
const emit = defineEmits<{ give: [seat: number]; 'next-round': [] }>();
const ui = useI18n().koboUi;
const sheet = ui.sheet;

const round = computed(() => props.game.lastRound);
const rows = computed(() => (round.value ? roundRows(round.value) : []));
const verdict = computed(() => (round.value ? roundVerdict(round.value) : 'none'));
const announcer = computed(() => round.value?.announcer ?? null);
const announcerIsMe = computed(() => announcer.value === props.mySeat);
const announcerName = computed(() => (announcer.value === null ? '' : props.nameOf(announcer.value)));

const title = computed(() => {
  if (verdict.value === 'none') return sheet.title.none;
  const key = verdict.value === 'won' ? (announcerIsMe.value ? 'wonYou' : 'won') : (announcerIsMe.value ? 'lostYou' : 'lost');
  return format(sheet.title[key], { player: announcerName.value });
});

const subtitle = computed(() => {
  if (verdict.value === 'lost') return sheet.subtitle.lost;
  if (verdict.value !== 'won') return '';
  const giftTo = round.value?.giftTo ?? null;
  if (props.game.phase === 'GIFT' || giftTo === null) {
    return announcerIsMe.value
      ? sheet.subtitle.giftChoose
      : format(sheet.subtitle.giftWaiting, { player: announcerName.value });
  }
  return format(sheet.subtitle.gave, {
    giver: announcerIsMe.value ? ui.you : announcerName.value,
    receiver: giftTo === props.mySeat ? ui.youObject : props.nameOf(giftTo),
  });
});

const receivers = computed(() =>
  props.game.availableActions.includes('GIVE_TEN')
    ? props.game.players.filter(p => p.seat !== props.mySeat).map(p => p.seat)
    : []);
const canNextRound = computed(() => props.game.phase === 'ROUND_OVER' && props.game.availableActions.includes('READY'));
const waitingForOthers = computed(() =>
  props.game.phase === 'ROUND_OVER' && props.game.readySeats.includes(props.mySeat));
const readySeats = computed(() => (props.game.phase === 'ROUND_OVER' ? props.game.readySeats : null));
const hasActions = computed(() => receivers.value.length > 0 || canNextRound.value || waitingForOthers.value);
</script>

<template>
  <section class="sheet" data-test="score-sheet" :aria-label="format(sheet.label, { n: game.round })">
    <div class="sheet-body" data-test="sheet-body">
      <header class="sheet-head" aria-live="polite">
        <span v-if="verdict !== 'none'" class="badge" data-test="sheet-badge">{{ sheet.badge }}</span>
        <h2 class="title" data-test="sheet-title">{{ title }}</h2>
        <p v-if="subtitle" class="subtitle" data-test="sheet-subtitle">{{ subtitle }}</p>
      </header>
      <KoboRoundScores :rows="rows" :name-of="nameOf" :my-seat="mySeat" :ready-seats="readySeats" />
    </div>
    <p v-if="notice" class="notice" data-test="notice" role="alert">{{ ui.notice[notice] }}</p>
    <div v-if="hasActions" class="sheet-actions" data-test="sheet-actions">
      <Button
        v-for="seat in receivers"
        :key="seat"
        :data-test="`give-${seat}`"
        class="sheet-button"
        :label="format(sheet.giveTen, { player: nameOf(seat) })"
        severity="success"
        rounded
        :disabled="busy"
        @click="emit('give', seat)" />
      <Button
        v-if="canNextRound"
        data-test="action-next-round"
        class="sheet-button"
        :label="sheet.nextRound"
        severity="success"
        rounded
        :disabled="busy"
        @click="emit('next-round')" />
      <p v-if="waitingForOthers" class="wait" data-test="sheet-wait" role="status">{{ sheet.waitingOthers }}</p>
    </div>
  </section>
</template>

<style scoped>
.sheet {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  max-height: 100%;
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-bottom: 0;
  border-radius: 18px 18px 0 0;
  box-shadow: 0 -10px 30px rgba(0, 0, 0, 0.5);
  color: #fff;
  animation: sheet-rise 0.25s cubic-bezier(.2, .8, .2, 1) backwards;
}
/* Verdict and scores scroll when a crowded table needs more than the room; the action zone below never does. */
.sheet-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-2) var(--space-2) var(--space-1);
}
.sheet-head { display: flex; flex-direction: column; align-items: center; gap: 2px; margin-bottom: var(--space-1); text-align: center; }
.badge {
  padding: 0 12px;
  font-family: var(--font-title);
  font-size: 0.78rem;
  font-weight: 700;
  line-height: 1.7;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #1a1a1a;
  background: var(--gold);
  border-radius: 999px;
}
.title { margin: 0; font-family: var(--font-title); font-size: 1.05rem; line-height: 1.25; overflow-wrap: anywhere; }
.subtitle { margin: 0; font-size: 0.8rem; color: rgba(255, 255, 255, 0.75); overflow-wrap: anywhere; }
.notice {
  flex: none;
  margin: 0 var(--space-2) var(--space-1);
  padding: 4px 12px;
  font-size: 0.8rem;
  font-weight: 700;
  text-align: center;
  background: rgba(var(--accent-negative-rgb), 0.35);
  border: 1px solid rgba(var(--accent-negative-rgb), 0.7);
  border-radius: 999px;
}
.sheet-actions {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-1);
  padding: var(--space-1) var(--space-2) calc(var(--space-2) + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}
.sheet-button { min-height: 44px; min-width: 44px; flex: 1 1 9rem; max-width: 16rem; }
.wait { margin: 0; align-self: center; font-size: 0.85rem; color: rgba(255, 255, 255, 0.8); text-align: center; }

@keyframes sheet-rise {
  from { transform: translateY(16px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .sheet { animation: none; }
}
</style>
