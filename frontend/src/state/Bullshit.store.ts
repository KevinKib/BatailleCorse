import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import BullshitSession, { type BullshitSessionEvent } from '../application/BullshitSession';
import type { BullshitState, BullshitView } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/bullshit/LobbyView';
import type { CallBullshitEventData } from '../model/bullshit/BullshitEvents';
import type Card from '../model/Card';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { type ClaimMode } from '../model/bullshit/claimMode';

export const REVEAL_HOLD_MS = 3000;
export { FORFEIT_NOTICE_HOLD_MS };

export const useBullshitStore = defineStore('bullshit-store', () => {
  const state = ref<BullshitView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  const reveal = ref<CallBullshitEventData | null>(null);
  const selectedCards = ref<Card[]>([]);
  let revealTimer: ReturnType<typeof setTimeout> | null = null;

  const game = computed<BullshitState | null>(() =>
    state.value && state.value.started ? state.value : null);
  const lobby = computed<LobbyView | null>(() =>
    state.value && !state.value.started ? state.value : null);

  const presence = useSeatPresence({
    presentSeats: () => (game.value?.players ?? []).map(p => Number(p.id)),
  });

  const session = new BullshitSession(webSocketService, {
    onEvent(event: BullshitSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: BullshitSessionEvent) {
    switch (event.type) {
      case 'state-update': state.value = event.state; break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') {
          reveal.value = event.eventData as CallBullshitEventData;
          if (revealTimer !== null) clearTimeout(revealTimer);
          revealTimer = setTimeout(() => { reveal.value = null; revealTimer = null; }, REVEAL_HOLD_MS);
        } else {
          presence.applyPresenceEvent(event.eventType, event.eventData);
        }
        break;
    }
  }

  const me = computed(() => game.value?.players.find(p => p.id === String(mySeat.value)) ?? null);
  const isMyTurn = computed(() => me.value?.isCurrentPlayer ?? false);
  const canDiscard = computed(() => game.value?.availableActions.includes('DISCARD') ?? false);
  const canCallBullshit = computed(() => game.value?.availableActions.includes('CALL_BULLSHIT') ?? false);
  const iWon = computed(() =>
    game.value?.outcome.status === 'FINISHED' && game.value.outcome.winnerId === String(mySeat.value));
  const isHost = computed(() => mySeat.value === 0);
  const canStart = computed(() => lobby.value?.canStart ?? false);
  const phase = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(phase, (p) => { if (p === 'lobby') presence.reset(); });

  function playAgain() {
    presence.reset();
    return session.playAgain();
  }

  function toggleCard(card: Card) {
    const i = selectedCards.value.findIndex(c => c.name === card.name);
    if (i >= 0) selectedCards.value.splice(i, 1);
    else if (selectedCards.value.length < 4) selectedCards.value.push(card);
  }
  function clearSelection() { selectedCards.value = []; }

  return {
    state, game, lobby, gameId, mySeat, reveal, selectedCards,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    isMyTurn, canDiscard, canCallBullshit, iWon, isHost, canStart, phase,
    applyEvent, toggleCard, clearSelection,
    create: (name?: string, claimMode?: ClaimMode) => session.create(name, claimMode),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: () => session.startGame(),
    discard: () => { session.discard(selectedCards.value); clearSelection(); },
    callBullshit: () => session.callBullshit(),
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
