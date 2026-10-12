import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import BullshitSession, { type BullshitSessionEvent } from '../application/BullshitSession';
import type { BullshitPlayer, BullshitState, BullshitView } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/lobby/LobbyView';
import type { CallBullshitEventData } from '../model/bullshit/BullshitEvents';
import type Card from '../model/Card';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import type { ForfeitReason } from '../model/ForfeitReason';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

/** Where my own play-again request stands (the others may click at their own pace). */
export type RematchState = 'idle' | 'pending' | 'failed';

export const REVEAL_HOLD_MS = 3000;
export { FORFEIT_NOTICE_HOLD_MS };

export const useBullshitStore = defineStore('bullshit-store', () => {
  const state = ref<BullshitView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  const reveal = ref<CallBullshitEventData | null>(null);
  const selectedCards = ref<Card[]>([]);
  // Every seat seen in this game, kept after it leaves the table (a forfeited player drops out of
  // `players`, yet "Bob forfeited" must still name him). Cleared when the room goes back to a lobby.
  const roster = ref<Record<string, BullshitPlayer>>({});
  // Last refusal of a lobby command (server message, or 'connection' when it could not be sent), cleared by the
  // next lobby refresh. Whatever the event type: a Start refusal is typed START, a Leave is typed JOIN.
  const lobbyError = ref<string | null>(null);
  // A Start is on its way: from the click until the game state or a refusal comes back.
  const starting = ref(false);
  // Rematch request of this client: pending while the HTTP call runs, failed when refused.
  const rematch = ref<RematchState>('idle');
  // The host removed me from the lobby: the screen leads to the Game selection with a notice.
  const removedByHost = ref(false);
  // Last seat that left (resigned or timed out), kept for the end screen after the notice is gone.
  const lastForfeit = ref<{ seat: number; reason: ForfeitReason | null } | null>(null);
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
      case 'state-update':
        reconcileSelection(state.value, event.state);
        state.value = event.state;
        lobbyError.value = null;
        starting.value = false;
        if (event.state.started) {
          for (const p of event.state.players) roster.value = { ...roster.value, [p.id]: p };
        } else {
          roster.value = {};
          lastForfeit.value = null;
          rematch.value = 'idle';
        }
        break;
      case 'game-id-change':
        if (gameId.value !== event.gameId) roster.value = {};
        gameId.value = event.gameId;
        break;
      case 'error':
        lobbyError.value = state.value?.started ? null : event.message;
        starting.value = false;
        break;
      case 'removed':
        state.value = null;
        clearSelection();
        gameId.value = null;
        roster.value = {};
        lobbyError.value = null;
        starting.value = false;
        removedByHost.value = true;
        break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'CALL_BULLSHIT') {
          reveal.value = event.eventData as CallBullshitEventData;
          if (revealTimer !== null) clearTimeout(revealTimer);
          revealTimer = setTimeout(() => { reveal.value = null; revealTimer = null; }, REVEAL_HOLD_MS);
        } else {
          if (event.eventType === 'FORFEIT') {
            const { loserSeat, reason } = event.eventData as ForfeitEventData;
            lastForfeit.value = { seat: loserSeat, reason: reason ?? null };
          }
          presence.applyPresenceEvent(event.eventType, event.eventData);
        }
        break;
    }
  }

  // A selection belongs to one deal: drop it when the room is not in a running game, when a game starts (after a
  // lobby or a finished game) or another game is loaded; otherwise keep only the cards still in my hand.
  function reconcileSelection(previous: BullshitView | null, next: BullshitView) {
    if (selectedCards.value.length === 0) return;
    if (!next.started) { selectedCards.value = []; return; }
    const sameRunningGame = previous?.started === true && previous.outcome.status !== 'FINISHED'
      && previous.id === next.id;
    if (!sameRunningGame) { selectedCards.value = []; return; }
    const hand = new Set(next.myHand.map(c => c.name));
    selectedCards.value = selectedCards.value.filter(c => hand.has(c.name));
  }

  const me = computed(() => game.value?.players.find(p => p.id === String(mySeat.value)) ?? null);
  const isMyTurn = computed(() => me.value?.isCurrentPlayer ?? false);
  const canDiscard = computed(() => game.value?.availableActions.includes('DISCARD') ?? false);
  const canCallBullshit = computed(() => game.value?.availableActions.includes('CALL_BULLSHIT') ?? false);
  const iWon = computed(() =>
    game.value?.outcome.status === 'FINISHED' && game.value.outcome.winnerId === String(mySeat.value));
  const phase = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(phase, (p) => { if (p === 'lobby') presence.reset(); });

  // Publishing throws while the socket is down (reconnecting): report it like a server refusal.
  // Returns whether the command was sent.
  function requestLobbyChange(send: () => void): boolean {
    lobbyError.value = null;
    try {
      send();
      return true;
    } catch {
      lobbyError.value = 'connection';
      return false;
    }
  }

  // Leaves the lobby and forgets everything about it, so the next room starts from a clean store.
  // Returns whether the lobby was left: false when the command could not be sent (the player is still seated).
  function leave(): boolean {
    if (!requestLobbyChange(() => session.leave())) return false;
    state.value = null;
    clearSelection();
    gameId.value = null;
    roster.value = {};
    lobbyError.value = null;
    return true;
  }

  // One Start at a time: the button is busy until the answer, so a second click is not sent.
  function startGame(): boolean {
    if (starting.value) return false;
    starting.value = requestLobbyChange(() => session.startGame());
    return starting.value;
  }

  async function playAgain() {
    presence.reset();
    clearSelection();
    rematch.value = 'pending';
    try {
      await session.playAgain();
    } catch {
      rematch.value = 'failed';
    }
  }

  function toggleCard(card: Card) {
    const i = selectedCards.value.findIndex(c => c.name === card.name);
    if (i >= 0) selectedCards.value.splice(i, 1);
    else if (selectedCards.value.length < 4) selectedCards.value.push(card);
  }
  function clearSelection() { selectedCards.value = []; }

  return {
    state, game, lobby, roster, lobbyError, removedByHost, starting, gameId, mySeat, reveal, selectedCards, rematch, lastForfeit,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    isMyTurn, canDiscard, canCallBullshit, iWon, phase,
    applyEvent, toggleCard, clearSelection,
    create: (name?: string) => session.create(name),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame,
    addBot: () => requestLobbyChange(() => session.addBot()),
    removeBot: (seat: number) => requestLobbyChange(() => session.removeBot(seat)),
    removeSeat: (seat: number) => requestLobbyChange(() => session.removeSeat(seat)),
    setOption: (key: string, value: string) => requestLobbyChange(() => session.setOption(key, value)),
    setSeatOption: (seat: number, key: string, value: string) => requestLobbyChange(() => session.setSeatOption(seat, key, value)),
    rename: (name: string) => requestLobbyChange(() => session.rename(name)),
    leave,
    discard: () => { session.discard(selectedCards.value); clearSelection(); },
    callBullshit: () => session.callBullshit(),
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
