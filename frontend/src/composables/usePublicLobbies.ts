import { computed, onScopeDispose, readonly, ref } from 'vue';
import type { GameDefinition } from '../model/games';
import type { PublicLobby, PublicLobbyList } from '../model/lobby/PublicLobby';
import webSocketService from '../service/WebSocketService';
import { useConnectionStatus } from './useConnectionStatus';
import { useI18n } from './useI18n';
import { format, plural } from '../locales/format';

/** At most one announcement per this many milliseconds: a busy list must not talk over the screen reader. */
const ANNOUNCE_EVERY_MS = 2000;

function openCount(lobbies: PublicLobby[]): number {
  return lobbies.filter(l => l.state === 'OPEN').length;
}

/**
 * The public game list of a game type: a REST snapshot, then the whole-list pushes of the topic. A list older than
 * the one held is dropped (`version`); after a reconnect the snapshot is fetched again and replaces the list
 * whatever its version (a restarted server counts from zero). Exposes the batched text for a polite live region:
 * silent for the first snapshot and for reconnect snapshots.
 */
export function usePublicLobbies(game: GameDefinition) {
  const ui = useI18n().join.publicGames;
  const { online } = useConnectionStatus();

  const list = ref<PublicLobbyList | null>(null);
  const loading = ref(true);
  const announcement = ref('');

  let version = -1;
  let disposed = false;

  // Announcement batching.
  let pendingNew = 0;
  let announcedOpen = 0;
  let lastAnnouncedAt = Number.NEGATIVE_INFINITY;
  let timer: ReturnType<typeof setTimeout> | null = null;

  function emit() {
    timer = null;
    const open = openCount(list.value?.lobbies ?? []);
    if (pendingNew === 0 && open === announcedOpen) return;
    const news = pendingNew > 0 ? `${plural(ui.announce.newGames, pendingNew)} ` : '';
    const state = open > 0 ? format(ui.announce.openNow, { open }) : ui.announce.noneOpen;
    announcement.value = news + state;
    pendingNew = 0;
    announcedOpen = open;
    lastAnnouncedAt = Date.now();
  }

  function scheduleAnnouncement() {
    if (timer !== null) return;
    const wait = lastAnnouncedAt + ANNOUNCE_EVERY_MS - Date.now();
    if (wait <= 0) emit();
    else timer = setTimeout(emit, wait);
  }

  function apply(next: PublicLobbyList, how: 'push' | 'silent' | 'replace') {
    if (how !== 'replace' && next.version <= version) return;
    const hadList = list.value !== null;
    const known = new Set((list.value?.lobbies ?? []).map(l => l.gameId));
    version = next.version;
    list.value = next;
    loading.value = false;
    if (how === 'push' && hadList) {
      pendingNew += next.lobbies.filter(l => !known.has(l.gameId)).length;
      if (pendingNew > 0 || openCount(next.lobbies) !== announcedOpen) scheduleAnnouncement();
    } else {
      announcedOpen = openCount(next.lobbies);
      pendingNew = 0;
    }
  }

  async function fetchSnapshot(how: 'silent' | 'replace') {
    try {
      const res = await fetch(`/api/lobbies/${game.serverType}`);
      if (disposed) return;
      if (res.ok) apply(await res.json() as PublicLobbyList, how);
    } catch {
      // Offline or server down: the topic and the next reconnect will bring the list.
    }
    if (!disposed) loading.value = false;
  }

  void fetchSnapshot('silent');
  const unsubscribe = webSocketService.subscribeToPublicLobbies(
    game.serverType,
    pushed => apply(pushed, 'push'),
    () => { void fetchSnapshot('replace'); },
  );

  onScopeDispose(() => {
    disposed = true;
    unsubscribe();
    if (timer !== null) clearTimeout(timer);
  });

  return {
    lobbies: computed(() => list.value?.lobbies ?? []),
    total: computed(() => list.value?.total ?? 0),
    truncated: computed(() => list.value?.truncated ?? false),
    loading: readonly(loading),
    online,
    announcement: readonly(announcement),
  };
}
