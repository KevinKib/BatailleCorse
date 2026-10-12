import { defineStore } from "pinia";
import { computed, ref } from "vue";

import webSocketService from '../service/WebSocketService';
import type BatailleCorse from "../model/BatailleCorse";
import type Card from "../model/Card";
import type Response from "../model/Response";
import type SessionSeat from '../model/SessionSeat';
import type { LobbyView } from '../model/lobby/LobbyView';
import GameSession from '../application/GameSession';
import type { GameEvent } from '../application/GameEvent';
import { useSeatPresence } from '../composables/useSeatPresence';

/** Where my own Play again request stands. */
export type RematchState = 'idle' | 'pending' | 'failed';

export const useBatailleCorseStore = defineStore('bataille-corse-store', () => {

  const state = ref<BatailleCorse>();
  const lobby = ref<LobbyView | null>(null);
  const gameId = ref<string | null>(null);
  const myPlayerIndex = ref<number>(0);
  // Every seat of the room: names, bots and their tiers (from the lobby, or the session view after a reload).
  const seats = ref<SessionSeat[]>([]);
  // Last refusal of a lobby command (server message, or 'connection' when it could not be sent), cleared by the
  // next lobby view. A refused move at the table is not shown: the state it carries says enough.
  const lobbyError = ref<string | null>(null);
  // A Start is on its way: from the click until the game state or a refusal comes back.
  const starting = ref(false);
  const rematch = ref<RematchState>('idle');
  // The host removed me from the lobby: the screen leads to the Game selection with a notice.
  const removedByHost = ref(false);

  const lastSend = ref<{ playerIndex: number; seq: number; topCard: Card | undefined } | null>(null);
  const lastGrab = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);
  const lastSlap = ref<{ seq: number } | null>(null);
  const lastSuccessfulSlap = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);
  const lastErroneousSlap = ref<{ playerIndex: number; seq: number } | null>(null);

  let animationResolve: (() => void) | null = null;
  // slapSeq lives in the store because the slap flash animation fires optimistically
  // at the moment the user presses the button, before the server responds.
  let slapSeq = 0;

  // BC has exactly one opponent: the only seat whose disconnection matters is the other one.
  const presence = useSeatPresence({
    presentSeats: () => [1 - myPlayerIndex.value],
  });

  const phase = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (state.value) return state.value.isOver() ? 'finished' : 'playing';
    return lobby.value ? 'lobby' : 'connecting';
  });

  const mySeatInfo = computed(() => seats.value.find(s => s.id === myPlayerIndex.value) ?? null);
  const opponentSeat = computed(() => seats.value.find(s => s.id !== myPlayerIndex.value) ?? null);

  function applyEvent(event: GameEvent) {
    switch (event.type) {
      case 'state-update':
        state.value = event.state;
        lobby.value = null;
        lobbyError.value = null;
        starting.value = false;
        break;
      case 'lobby-update':
        lobby.value = event.lobby;
        state.value = undefined;
        lobbyError.value = null;
        starting.value = false;
        rematch.value = 'idle';
        presence.reset();
        break;
      case 'removed':
        lobby.value = null;
        state.value = undefined;
        gameId.value = null;
        seats.value = [];
        lobbyError.value = null;
        starting.value = false;
        removedByHost.value = true;
        break;
      case 'seats-change':    seats.value = event.seats; break;
      case 'game-id-change':  gameId.value = event.gameId; break;
      case 'my-index-change': myPlayerIndex.value = event.playerIndex; break;
      case 'error':
        lobbyError.value = state.value ? null : event.message;
        starting.value = false;
        break;
      case 'send':            lastSend.value = event; break;
      case 'grab':            lastGrab.value = event; break;
      case 'slap':            lastSlap.value = { seq: ++slapSeq }; break;
      case 'successful-slap': lastSuccessfulSlap.value = event; break;
      case 'erroneous-slap':  lastErroneousSlap.value = event; break;
      case 'presence-event':  presence.applyPresenceEvent(event.eventType, event.eventData); break;
    }
  }

  const session = new GameSession(
    webSocketService,
    {
      onEvent: applyEvent,
      awaitAnimation: () => new Promise<void>(resolve => { animationResolve = resolve; }),
    },
  );

  function notifyAnimationComplete() {
    animationResolve?.();
    animationResolve = null;
  }

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

  // One Start at a time: the button is busy until the answer, so a second click is not sent.
  function startGame(): boolean {
    if (starting.value) return false;
    starting.value = requestLobbyChange(() => session.startGame());
    return starting.value;
  }

  // Leaves the lobby and forgets everything about it, so the next room starts from a clean store.
  // Returns whether the lobby was left: false when the command could not be sent (the player is still seated).
  function leave(): boolean {
    if (!requestLobbyChange(() => session.leave())) return false;
    lobby.value = null;
    state.value = undefined;
    gameId.value = null;
    seats.value = [];
    lobbyError.value = null;
    return true;
  }

  async function playAgain(name?: string) {
    presence.reset();
    rematch.value = 'pending';
    try {
      await session.playAgain(name);
    } catch {
      rematch.value = 'failed';
    }
  }

  function reset() {
    state.value = undefined;
    lobby.value = null;
    seats.value = [];
    lobbyError.value = null;
    starting.value = false;
    rematch.value = 'idle';
    presence.reset();
  }

  return {
    state,
    lobby,
    gameId,
    myPlayerIndex,
    seats,
    mySeatInfo,
    opponentSeat,
    lobbyError,
    removedByHost,
    starting,
    rematch,
    phase,
    lastSend,
    lastGrab,
    lastSlap,
    lastSuccessfulSlap,
    lastErroneousSlap,
    liveDisconnections: presence.liveDisconnections,
    applyEvent,
    create:          (name?: string) => { reset(); return session.create(name); },
    join:            (id: string, name?: string) => { reset(); return session.join(id, name); },
    restore:         (id: string, seat: number, token: string) => { reset(); session.restore(id, seat, token); },
    hydrate:         () => session.hydrate(),
    startGame,
    addBot:          () => requestLobbyChange(() => session.addBot()),
    removeBot:       (seat: number) => requestLobbyChange(() => session.removeBot(seat)),
    removeSeat:      (seat: number) => requestLobbyChange(() => session.removeSeat(seat)),
    setOption:       (key: string, value: string) => requestLobbyChange(() => session.setOption(key, value)),
    setSeatOption:   (seat: number, key: string, value: string) => requestLobbyChange(() => session.setSeatOption(seat, key, value)),
    rename:          (name: string) => requestLobbyChange(() => session.rename(name)),
    leave,
    playAgain,
    send:            () => session.send(),
    slap:            () => session.slap(),
    forfeit:         () => session.forfeit(),
    onResponse:      (r: Response) => { session.onResponse(r); },
    notifyAnimationComplete,
    cancelAutoGrab:  () => session.cancelAll(),
  };

});
