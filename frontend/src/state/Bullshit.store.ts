import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

import webSocketService from '../service/WebSocketService';
import BullshitSession, { type BullshitSessionEvent } from '../application/BullshitSession';
import type { BullshitState, BullshitView } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/bullshit/LobbyView';
import type { CallBullshitEventData } from '../model/bullshit/BullshitEvents';
import type Card from '../model/Card';
import { bullshitRematchButton } from '../model/bullshit/BullshitRematch';

export const useBullshitStore = defineStore('bullshit-store', () => {
  const state = ref<BullshitView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  const reveal = ref<CallBullshitEventData | null>(null);
  const selectedCards = ref<Card[]>([]);
  const rematchRequested = ref(false);
  const rematchReady = ref(0);
  const rematchEligible = ref(0);

  const session = new BullshitSession(webSocketService, {
    onEvent(event: BullshitSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: BullshitSessionEvent) {
    switch (event.type) {
      case 'state-update': state.value = event.state; break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') reveal.value = event.eventData as CallBullshitEventData;
        else reveal.value = null;
        if (event.eventType === 'REMATCH') {
          const d = event.eventData as { status: string; ready: number; eligible: number };
          rematchReady.value = d.ready;
          rematchEligible.value = d.eligible;
          if (d.status === 'STARTED') rematchRequested.value = false;
        }
        break;
    }
  }

  const game = computed<BullshitState | null>(() =>
    state.value && state.value.started ? state.value : null);
  const lobby = computed<LobbyView | null>(() =>
    state.value && !state.value.started ? state.value : null);

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

  const rematchButton = computed(() =>
    bullshitRematchButton({ iRequested: rematchRequested.value, ready: rematchReady.value, eligible: rematchEligible.value }));

  function requestRematch() {
    rematchRequested.value = true;
    session.rematch();
  }

  function leaveRematch() {
    session.leaveRematch();
  }

  function toggleCard(card: Card) {
    const i = selectedCards.value.findIndex(c => c.name === card.name);
    if (i >= 0) selectedCards.value.splice(i, 1);
    else if (selectedCards.value.length < 4) selectedCards.value.push(card);
  }
  function clearSelection() { selectedCards.value = []; }

  return {
    state, game, lobby, gameId, mySeat, reveal, selectedCards,
    isMyTurn, canDiscard, canCallBullshit, iWon, isHost, canStart, phase,
    rematchRequested, rematchReady, rematchEligible, rematchButton,
    applyEvent, toggleCard, clearSelection,
    create: (name?: string) => session.create(name),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: () => session.startGame(),
    discard: () => { session.discard(selectedCards.value); clearSelection(); },
    callBullshit: () => session.callBullshit(),
    rematch: requestRematch,
    leaveRematch,
  };
});
