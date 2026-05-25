import { defineStore } from "pinia";
import { ref } from "vue";

import webSocketService from '../service/WebSocketService';
import BatailleCorse from "../model/BatailleCorse";
import type Card from "../model/Card";
import type Response from "../model/Response";
import AI from "../model/ai/AI";
import { useSettingsStore } from './Settings.store';
import { DIFFICULTY } from '../model/Difficulty';
import GameSession from '../application/GameSession';
import type { GameEvent } from '../application/GameEvent';

export const useBatailleCorseStore = defineStore('bataille-corse-store', () => {

  const state = ref<BatailleCorse>();
  const gameId = ref<string | null>(null);
  const lastSend = ref<{ playerIndex: number; seq: number; topCard: Card | undefined } | null>(null);
  const lastGrab = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);
  const lastSlap = ref<{ seq: number } | null>(null);
  const lastSuccessfulSlap = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);
  const lastErroneousSlap = ref<{ playerIndex: number; seq: number } | null>(null);

  let animationResolve: (() => void) | null = null;
  // slapSeq lives in the store because the slap flash animation fires optimistically
  // at the moment the user presses the button, before the server responds.
  let slapSeq = 0;

  const settingsStore = useSettingsStore();

  const session = new GameSession(
    webSocketService,
    {
      onEvent(event: GameEvent) {
        switch (event.type) {
          case 'state-update':    state.value = event.state; break;
          case 'game-id-change':  gameId.value = event.gameId; break;
          case 'send':            lastSend.value = event; break;
          case 'grab':            lastGrab.value = event; break;
          case 'slap':            lastSlap.value = { seq: ++slapSeq }; break;
          case 'successful-slap': lastSuccessfulSlap.value = event; break;
          case 'erroneous-slap':  lastErroneousSlap.value = event; break;
        }
      },
      awaitAnimation: () => new Promise<void>(resolve => { animationResolve = resolve; }),
    },
    () => new AI(1, DIFFICULTY[settingsStore.difficulty].reactionTime),
  );

  function notifyAnimationComplete() {
    animationResolve?.();
    animationResolve = null;
  }

  return {
    state,
    gameId,
    lastSend,
    lastGrab,
    lastSlap,
    lastSuccessfulSlap,
    lastErroneousSlap,
    create:               (name?: string) => session.create(name),
    hydrate:              (id: string, s: BatailleCorse) => session.hydrate(id, s),
    restoreTokens:        (tokens: Record<number, string>) => session.restoreTokens(tokens),
    send:                 (playerIndex: number) => session.send(playerIndex),
    slap:                 (playerIndex: number) => session.slap(playerIndex),
    grab:                 (playerIndex: number) => session.grab(playerIndex),
    onResponse:           (r: Response) => { session.onResponse(r); },
    notifyAnimationComplete,
    cancelAutoGrab:       () => session.cancelAll(),
  };

});
