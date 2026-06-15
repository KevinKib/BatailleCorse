import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

import webSocketService from '../service/WebSocketService';
import BullshitSession, { type BullshitSessionEvent } from '../application/BullshitSession';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { CallBullshitEventData } from '../model/bullshit/BullshitEvents';
import type Card from '../model/Card';

export const useBullshitStore = defineStore('bullshit-store', () => {
  const state = ref<BullshitState | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  const waiting = ref<boolean>(false);
  const reveal = ref<CallBullshitEventData | null>(null);
  const selectedCards = ref<Card[]>([]);

  const session = new BullshitSession(webSocketService, {
    onEvent(event: BullshitSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: BullshitSessionEvent) {
    switch (event.type) {
      case 'state-update': state.value = event.state; break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'JOIN') waiting.value = false;
        if (event.eventType === 'CALL_BULLSHIT') reveal.value = event.eventData as CallBullshitEventData;
        else reveal.value = null;
        break;
    }
  }

  function markCreated() { waiting.value = true; }

  const me = computed(() => state.value?.players.find(p => p.id === String(mySeat.value)) ?? null);
  const isMyTurn = computed(() => me.value?.isCurrentPlayer ?? false);
  const canDiscard = computed(() => state.value?.availableActions.includes('DISCARD') ?? false);
  const canCallBullshit = computed(() => state.value?.availableActions.includes('CALL_BULLSHIT') ?? false);
  const iWon = computed(() =>
    state.value?.outcome.status === 'FINISHED' && state.value.outcome.winnerId === String(mySeat.value));
  const phase = computed<'connecting' | 'waiting' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    if (waiting.value) return 'waiting';
    return 'playing';
  });

  function toggleCard(card: Card) {
    const i = selectedCards.value.findIndex(c => c.name === card.name);
    if (i >= 0) selectedCards.value.splice(i, 1);
    else if (selectedCards.value.length < 4) selectedCards.value.push(card);
  }
  function clearSelection() { selectedCards.value = []; }

  return {
    state, gameId, mySeat, waiting, reveal, selectedCards,
    isMyTurn, canDiscard, canCallBullshit, iWon, phase,
    applyEvent, markCreated, toggleCard, clearSelection,
    create: (name?: string) => { markCreated(); session.create(name); },
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    discard: () => { session.discard(selectedCards.value); clearSelection(); },
    callBullshit: () => session.callBullshit(),
  };
});
