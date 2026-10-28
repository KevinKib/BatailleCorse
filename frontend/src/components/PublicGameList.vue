<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { Button } from 'primevue';
import type { GameDefinition } from '../model/games';
import type { PublicLobby } from '../model/lobby/PublicLobby';
import { useI18n } from '../composables/useI18n';
import { joinErrorMessage } from '../composables/joinErrorMessage';
import { gameChoice } from '../locales/gameChoice';
import { format, plural } from '../locales/format';

const props = defineProps<{
  game: GameDefinition;
  lobbies: PublicLobby[];
  /** Every public game of the type, which can be more than `lobbies` when truncated. */
  total: number;
  truncated: boolean;
  loading: boolean;
  online: boolean;
  /** The batched text for the polite live region. */
  announcement: string;
  /** Claims a seat in the game and enters its room; a rejection is shown in the row. */
  join: (gameId: string) => Promise<void>;
}>();

const i18n = useI18n();
const ui = i18n.join.publicGames;
const gameName = computed(() => gameChoice(i18n, props.game.key).name);

const headingId = useId();
const heading = ref<HTMLElement | null>(null);

// "Open games only" is remembered per device; storage can be missing or refuse (private window).
const STORAGE_KEY = 'publicGames.openOnly';
function readOpenOnly(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) !== 'false';
  } catch {
    return true;
  }
}
const openOnly = ref(readOpenOnly());
watch(openOnly, value => {
  try {
    localStorage.setItem(STORAGE_KEY, String(value));
  } catch {
    // Not remembered; the toggle still works for this visit.
  }
});

const openCount = computed(() => props.lobbies.filter(l => l.state === 'OPEN').length);
const visible = computed(() => (openOnly.value ? props.lobbies.filter(l => l.state === 'OPEN') : props.lobbies));
const countText = computed(() => format(
  openOnly.value ? ui.openCount : ui.totalCount,
  { open: openCount.value, total: props.total },
));
const truncatedText = computed(() => format(ui.truncated, { shown: props.lobbies.length, total: props.total }));

const animated = !(typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);

const stateLabel = { OPEN: ui.open, FULL: ui.full, IN_PLAY: ui.inPlay } as const;

function playersText(l: PublicLobby): string {
  const base = format(ui.players, { joined: l.joined, max: l.maxPlayers });
  return l.bots > 0 ? `${base} ${plural(ui.bots, l.bots)}` : base;
}

function dots(l: PublicLobby): Array<'human' | 'bot' | 'free'> {
  return Array.from({ length: l.maxPlayers }, (_, i) =>
    i < l.joined - l.bots ? 'human' : i < l.joined ? 'bot' : 'free');
}

// A host who typed no name is called by the label of seat 1, in the player's language.
function hostLabel(l: PublicLobby): string {
  return l.hostName?.trim() || format(i18n.lobby.playerLabel, { n: 1 });
}

function joinLabel(l: PublicLobby): string {
  return format(ui.joinAria, { host: hostLabel(l), joined: l.joined, max: l.maxPlayers });
}

// Row errors and in-flight joins, by game id.
const rowErrors = ref<Record<string, string>>({});
const joining = ref<Set<string>>(new Set());

async function onJoin(l: PublicLobby) {
  if (joining.value.has(l.gameId)) return;
  joining.value = new Set(joining.value).add(l.gameId);
  rowErrors.value = { ...rowErrors.value, [l.gameId]: '' };
  try {
    await props.join(l.gameId);
  } catch (e) {
    rowErrors.value = { ...rowErrors.value, [l.gameId]: joinErrorMessage(e) };
  } finally {
    const next = new Set(joining.value);
    next.delete(l.gameId);
    joining.value = next;
  }
}

// A focused row that leaves the list would drop the focus on the page: move it to the heading instead.
watch(visible, next => {
  const focusedRow = document.activeElement?.closest<HTMLElement>('[data-game-id]');
  const id = focusedRow?.dataset.gameId;
  if (id && !next.some(l => l.gameId === id)) {
    void nextTick(() => heading.value?.focus());
  }
}, { flush: 'pre' });
</script>

<template>
  <section class="public-games" :aria-labelledby="headingId" data-test="public-games">
    <div class="pg-head">
      <h3 :id="headingId" ref="heading" class="pg-title" tabindex="-1" data-test="pg-heading">
        {{ ui.title }}
        <span class="pg-count" data-test="pg-count">{{ countText }}</span>
      </h3>
      <label class="pg-toggle">
        <input v-model="openOnly" type="checkbox" data-test="pg-toggle">
        <span>{{ ui.openOnly }}</span>
      </label>
    </div>

    <p v-if="!online" class="pg-notice" role="status" data-test="pg-offline">{{ ui.offline }}</p>

    <div
      class="pg-region"
      :class="{ 'pg-region--dim': !online }"
      role="region"
      tabindex="0"
      :aria-labelledby="headingId"
      :aria-busy="loading"
      data-test="pg-region">
      <p v-if="loading" class="pg-empty" data-test="pg-loading">{{ ui.loading }}</p>
      <div v-else-if="visible.length === 0" class="pg-empty" data-test="pg-empty">
        <p>{{ format(ui.empty, { game: gameName }) }}</p>
        <p class="pg-hint">{{ ui.emptyHint }}</p>
        <RouterLink :to="game.gameHomeRoute" class="pg-new" data-test="pg-new-game">{{ ui.newGame }}</RouterLink>
      </div>
      <TransitionGroup
        v-else
        tag="ul"
        name="pg-row"
        :css="animated"
        class="pg-list"
        :class="{ animated }"
        data-test="pg-list">
        <li
          v-for="l in visible"
          :key="l.gameId"
          class="pg-row"
          :class="{ 'row--muted': l.state !== 'OPEN' }"
          :data-game-id="l.gameId"
          data-test="pg-row">
          <div class="pg-main">
            <span class="pg-host">{{ hostLabel(l) }}</span>
            <span class="pg-badge" :class="`badge--${l.state.toLowerCase()}`" data-test="pg-badge">{{ stateLabel[l.state] }}</span>
          </div>
          <div class="pg-meta">
            <span class="pg-dots" aria-hidden="true" data-test="pg-dots">
              <span v-for="(d, i) in dots(l)" :key="i" class="dot" :class="`dot--${d}`" />
            </span>
            <span class="pg-players">{{ playersText(l) }}</span>
          </div>
          <Button
            v-if="l.state === 'OPEN'"
            type="button"
            class="pg-join"
            :label="ui.join"
            :aria-label="joinLabel(l)"
            :loading="joining.has(l.gameId)"
            size="small"
            severity="success"
            rounded
            data-test="pg-join"
            @click="onJoin(l)" />
          <p v-if="rowErrors[l.gameId]" class="pg-row-error" role="alert" data-test="pg-row-error">{{ rowErrors[l.gameId] }}</p>
        </li>
      </TransitionGroup>
    </div>

    <p v-if="truncated" class="pg-footer" data-test="pg-truncated">{{ truncatedText }}</p>
    <p class="sr-only" role="status" aria-live="polite" aria-atomic="true" data-test="pg-live">{{ announcement }}</p>
  </section>
</template>

<style scoped>
.public-games { width: 100%; display: flex; flex-direction: column; gap: 10px; }
.pg-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px 16px; }
.pg-title {
  margin: 0;
  font-size: 0.875rem;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.85);
}
.pg-title:focus-visible { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 4px; border-radius: 4px; }
.pg-count { margin-left: 8px; font-weight: 400; letter-spacing: 0.04em; text-transform: none; color: rgba(255, 255, 255, 0.7); }
.pg-toggle { display: inline-flex; align-items: center; gap: 8px; min-height: 44px; font-size: 0.875rem; color: rgba(255, 255, 255, 0.85); cursor: pointer; }
.pg-toggle input { width: 20px; height: 20px; accent-color: var(--gold, #e8c96d); cursor: pointer; }
.pg-notice { margin: 0; padding: 6px 12px; font-size: 0.8125rem; border-radius: 8px; color: rgba(255, 255, 255, 0.85); background: rgba(255, 255, 255, 0.08); border: 1px solid rgba(255, 255, 255, 0.2); }

.pg-region {
  max-height: min(45vh, 420px);
  overflow-y: auto;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.25);
}
.pg-region:focus-visible { outline: 2px solid rgba(232, 201, 109, 0.7); outline-offset: 2px; }
.pg-region--dim .pg-list { opacity: 0.7; }

.pg-empty { margin: 0; padding: 16px; display: flex; flex-direction: column; gap: 6px; align-items: center; text-align: center; font-size: 0.9375rem; color: rgba(255, 255, 255, 0.85); }
.pg-empty p { margin: 0; }
.pg-hint { font-size: 0.8125rem; color: rgba(255, 255, 255, 0.7); }
.pg-new {
  display: inline-flex; align-items: center; min-height: 44px; padding: 0 16px;
  color: var(--gold, #e8c96d); font-weight: 700; text-decoration: underline;
}

.pg-list { list-style: none; margin: 0; padding: 0; }
.pg-row {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 4px 12px;
  padding: 10px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.92);
}
.pg-row:last-child { border-bottom: 0; }
.row--muted { color: rgba(255, 255, 255, 0.62); }
.pg-main { display: flex; align-items: center; gap: 8px; min-width: 0; }
.pg-host { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 700; }
.pg-badge { flex: none; padding: 1px 8px; font-size: 0.75rem; border-radius: 999px; border: 1px solid currentColor; }
.badge--open { color: #7fd99a; }
.badge--full, .badge--in_play { color: rgba(255, 255, 255, 0.7); }
.pg-meta { display: flex; align-items: center; gap: 8px; font-size: 0.8125rem; grid-column: 1; }
.pg-dots { display: inline-flex; gap: 4px; }
.dot { width: 9px; height: 9px; border: 1.5px solid currentColor; box-sizing: border-box; border-radius: 50%; }
.dot--human { background: currentColor; }
.dot--bot { background: currentColor; border-radius: 2px; }
.dot--free { background: transparent; }
.pg-join { grid-column: 2; grid-row: 1 / span 2; min-height: 44px; min-width: 44px; }
.pg-row-error { grid-column: 1 / -1; margin: 4px 0 0; padding: 6px 10px; font-size: 0.8125rem; border-radius: 8px; color: #ffd0d0; background: rgba(200, 40, 40, 0.25); border: 1px solid rgba(255, 120, 120, 0.4); }
.pg-footer { margin: 0; text-align: center; font-size: 0.8125rem; color: rgba(255, 255, 255, 0.7); }

.sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }

.animated .pg-row-enter-active { transition: opacity 150ms ease; }
.animated .pg-row-enter-from { opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .animated .pg-row-enter-active { transition: none; }
}
</style>
