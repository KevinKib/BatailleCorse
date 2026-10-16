<script setup lang="ts">
import { computed } from 'vue';
import type { PresidentStanding } from '../../model/president/PresidentState';
import { useI18n } from '../../composables/useI18n';

// The seats ranked by points, as the server sends them (`standings`): the running score of any match, and the
// final one. Seats with the same points share a place. Names are user input: only ever rendered as text.
const props = defineProps<{
  standings: PresidentStanding[];
  nameOf: (seat: number) => string;
  mySeat: number;
}>();
const ui = useI18n().presidentUi;
const rows = computed(() => props.standings.map((s, i, all) => ({
  ...s,
  rank: all.findIndex(o => o.points === s.points) + 1,
  me: s.seat === props.mySeat,
  key: `${s.seat}-${i}`,
})));
</script>

<template>
  <ol class="standings" :aria-label="ui.standings.title">
    <li v-for="r in rows" :key="r.key" class="standing" :class="{ 'standing--me': r.me }" data-test="standing-row">
      <span class="standing-rank" data-test="standing-rank">{{ r.rank }}</span>
      <span class="standing-name" data-test="standing-name">{{ nameOf(r.seat) }}</span>
      <span class="standing-points" data-test="standing-points">{{ r.points }}</span>
    </li>
  </ol>
</template>

<style scoped>
.standings { margin: 0; padding: 0; list-style: none; width: 100%; display: flex; flex-direction: column; gap: 2px; }
.standing {
  display: grid; grid-template-columns: 1.6rem minmax(0, 1fr) auto; align-items: center; column-gap: var(--space-2);
  padding: 4px 8px; border-radius: 8px; font-size: 0.9rem; text-align: left;
}
.standing--me { background: rgba(255, 255, 255, 0.1); font-weight: 700; }
.standing-rank { color: var(--gold); font-family: var(--font-title); }
.standing-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.standing-points { font-weight: 700; }
</style>
