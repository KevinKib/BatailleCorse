<script setup lang="ts">
import { computed } from 'vue';
import { Button } from 'primevue';
import type { PresidentPlayer, PresidentRound, PresidentTitle } from '../../model/president/PresidentState';
import { seatsByPlace, titleKey } from '../../model/president/titles';
import { useI18n } from '../../composables/useI18n';
import { format } from '../../locales/format';
import type { PresidentTitleKey } from '../../locales/Messages';

// Minimal round-over state so the table never dead-ends: the finishing order and the "next round" barrier button.
// The full round summary (title changes, points, standings) is its own change and replaces this component.
const props = defineProps<{
  players: PresidentPlayer[];
  round: number;
  lastRound?: PresidentRound | null;
  nameOf: (seat: number) => string;
  /** False once I pressed Ready: the button gives way to a waiting line. */
  canReady: boolean;
}>();
const emit = defineEmits<{ ready: [] }>();

const ui = useI18n().presidentUi;
// The title a seat has just earned is in `lastRound`; a seat's own `title` is the one it played this round with.
const earnedTitle = (p: PresidentPlayer): PresidentTitle | null =>
  props.lastRound?.players.find(r => r.seat === p.seat)?.title ?? p.title;
const titleText = (title: PresidentTitle | null) => title ? ui.titles[titleKey(title) as PresidentTitleKey] : '';
const ordered = computed(() => seatsByPlace(props.players).map(p => ({
  seat: p.seat,
  line: [
    format(ui.roundOver.place, { n: p.place ?? '', player: props.nameOf(p.seat) }),
    titleText(earnedTitle(p)),
  ].filter(Boolean).join(' '),
})));
</script>

<template>
  <section class="round-over" data-test="round-over">
    <h2 class="round-over-title" data-test="round-over-title">{{ format(ui.phase.roundOver, { n: round }) }}</h2>
    <ol class="places">
      <li v-for="p in ordered" :key="p.seat" data-test="round-over-place">{{ p.line }}</li>
    </ol>
    <Button v-if="canReady" data-test="ready" class="ready" :label="ui.roundOver.ready" severity="success" rounded
            @click="emit('ready')" />
    <p v-else class="waiting" data-test="round-over-waiting" role="status">{{ ui.roundOver.waiting }}</p>
  </section>
</template>

<style scoped>
.round-over {
  display: flex; flex-direction: column; align-items: center; gap: var(--space-2);
  padding: var(--space-2); background: rgba(0, 0, 0, 0.45); border-radius: 14px;
}
.round-over-title { margin: 0; font-family: var(--font-title); font-size: 1rem; color: var(--gold); }
.places { margin: 0; padding: 0; list-style: none; font-size: 0.85rem; color: rgba(255, 255, 255, 0.9); }
.ready.p-button { min-height: 44px; min-width: 140px; }
.waiting { margin: 0; font-size: 0.85rem; color: rgba(255, 255, 255, 0.8); }
</style>
