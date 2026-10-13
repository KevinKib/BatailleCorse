<script setup lang="ts">
import { computed } from 'vue';
import type { KoboLogEntry } from '../../model/kobo/KoboEvents';
import { describeEvent, headlineEvent, type KoboLogLine } from '../../model/kobo/eventLog';
import { useI18n } from '../../composables/useI18n';
import { useKoboCardName } from '../../composables/useKoboCardName';
import { format } from '../../locales/format';

// The one-line log: what the last notable thing at the table was, said by a polite live region that is always in
// the page (so the text it receives is announced). The events the table already shows (draw, end of turn, ready)
// do not take the line, so an opponent's discard or match is still readable when his turn ends. One line, cut with
// an ellipsis (the full text is the title): the table never changes height when a longer sentence comes.
const props = defineProps<{
  /** Newest first, as the store keeps them. */
  events: readonly KoboLogEntry[];
  mySeat: number;
  nameOf: (seat: number) => string;
}>();

const ui = useI18n().koboUi;
const cardName = useKoboCardName();

const headline = computed(() => headlineEvent(props.events));
const actorName = (seat: number) => seat === props.mySeat ? ui.you : props.nameOf(seat);
const ownerName = (seat: number) =>
  seat === props.mySeat ? ui.log.ownerYour : format(ui.table.ownerOf, { player: props.nameOf(seat) });

function sentence(line: KoboLogLine): string {
  const slotNumber = (slot: number | null) => (slot ?? 0) + 1;
  const owner = line.owner !== null ? ownerName(line.owner) : '';
  const params = {
    actor: actorName(line.actor),
    owner,
    n: slotNumber(line.key === 'swap' || line.key === 'blindSwap' || line.key === 'kingSwap' ? line.slot : line.targetSlot),
    m: slotNumber(line.key === 'matchOther' ? line.givenSlot : line.targetSlot),
    card: line.card ? cardName(line.card) : '',
  };
  // The receiver of a gift is a person, not a possessive: "gave 10 points to you".
  if (line.key === 'giveTen' && line.owner !== null) {
    params.owner = line.owner === props.mySeat ? ui.you.toLowerCase() : props.nameOf(line.owner);
  }
  return format(ui.log[line.key], params);
}

const text = computed(() => {
  const line = headline.value ? describeEvent(headline.value) : null;
  return line ? sentence(line) : '';
});
// Colour is never the only signal (the sentence says it); this only tints the line and adds an icon.
const outcome = computed<'good' | 'bad' | undefined>(() => {
  const data = headline.value?.data;
  if (!data || data.action !== 'MATCH') return undefined;
  return data.success === false ? 'bad' : 'good';
});
</script>

<template>
  <div class="event-log" data-test="event-log" :data-outcome="outcome">
    <i v-if="outcome" class="pi outcome-icon" :class="outcome === 'bad' ? 'pi-times-circle' : 'pi-check-circle'"
       aria-hidden="true"></i>
    <p class="event-line" data-test="event-log-line" aria-live="polite" aria-atomic="true" :title="text || undefined">{{ text }}</p>
  </div>
</template>

<style scoped>
.event-log {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  /* Reserved even when empty: a line that appears must not move the cards under the pointer. */
  height: 1.1rem;
  font-size: 0.75rem;
  line-height: 1.1rem;
  color: rgba(255, 255, 255, 0.7);
}
.event-line {
  margin: 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.outcome-icon { flex: none; font-size: 0.75rem; }
.event-log[data-outcome='good'] { color: rgba(var(--accent-active-rgb), 1); }
.event-log[data-outcome='bad'] { color: #fff; }
.event-log[data-outcome='bad'] .outcome-icon { color: rgba(var(--accent-negative-rgb), 1); }
</style>
