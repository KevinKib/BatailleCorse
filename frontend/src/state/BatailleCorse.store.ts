import { defineStore } from "pinia";
import { ref } from "vue";

import webSocketService from '../service/WebSocketService';
import BatailleCorse from "../service/model/BatailleCorse";
import type Card from "../service/model/Card";
import Response from "../service/model/Response";
import AI from "../service/model/ai/AI";
import SlapEventData from '../service/model/event/SlapEventData';
import GrabEventData from '../service/model/event/GrabEventData';
import CreateEventData from "../service/model/event/CreateEventData";
import { useSettingsStore } from './Settings.store';

const DIFFICULTY_REACTION_TIMES = [2100, 1800, 1500, 1200, 900, 700, 600, 500, 400];

/*

{
  "currentPlayer": {
    "nbCards": 24,
    "id": "1"
  },
  "players": [
    {
      "nbCards": 25,
      "id": "0"
    },
    {
      "nbCards": 24,
      "id": "1"
    }
  ],
  "pile": [
    {
      "name": "HEART_8",
      "rank": "8",
      "suit": "HEART"
    },
    {
      "name": "HEART_9",
      "rank": "9",
      "suit": "HEART"
    },
    {
      "name": "SPADE_KING",
      "rank": "KING",
      "suit": "SPADE"
    }
  ]
}

*/

export const useBatailleCorseStore = defineStore('bataille-corse-store', () => {

  const autoGrabEnabled = true;
  const state = ref<BatailleCorse>();

  let autoGrabTimeoutId: ReturnType<typeof setTimeout> | null = null;

  // Server events are buffered so that each GRAB/SLAP animation plays to completion
  // before the next event is applied. SEND and CREATE events are non-blocking.
  const CATCHUP_THRESHOLD = 3; // skip animations when this many events are still waiting
  const eventQueue: Response[] = [];
  let isProcessingQueue = false;
  let animationResolve: (() => void) | null = null;

  let sendSeq = 0;
  // topCard is snapshotted at call time so the watcher doesn't need flush:'sync'
  const lastSend = ref<{ playerIndex: number; seq: number; topCard: Card | undefined } | null>(null);

  let grabSeq = 0;
  // pileCards snapshotted before state update so the watcher always gets the right cards
  const lastGrab = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);

  let slapSeq = 0;
  const lastSlap = ref<{ seq: number } | null>(null);

  let successfulSlapSeq = 0;
  const lastSuccessfulSlap = ref<{ winnerPlayerIndex: number; seq: number; pileCards: Card[] } | null>(null);

  let erroneousSlapSeq = 0;
  const lastErroneousSlap = ref<{ playerIndex: number; seq: number } | null>(null);

  const gameId = ref<string | null>(null);

  const settingsStore = useSettingsStore();

  // const player0Ai = new AI(0, 500);
  let player1Ai = new AI(1, DIFFICULTY_REACTION_TIMES[settingsStore.difficulty]);

  function create(playerName?: string) {
    player1Ai = new AI(1, DIFFICULTY_REACTION_TIMES[settingsStore.difficulty]);
    webSocketService.publish('/app/create', playerName ? JSON.stringify({ playerName }) : undefined);
  }

  function send(playerIndex: number) {
    const topCard = state.value?.pile.cards.at(0);
    lastSend.value = { playerIndex, seq: ++sendSeq, topCard };
    webSocketService.publish(`/app/send`, JSON.stringify({
        gameId: gameId.value,
        playerIndex: playerIndex,
      })
    );
  }

  function slap(playerIndex: number) {
    lastSlap.value = { seq: ++slapSeq };
    webSocketService.publish(`/app/slap`, JSON.stringify({
          gameId: gameId.value,
          playerIndex: playerIndex,
        })
    );
  }

  function grab(playerIndex: number) {
    webSocketService.publish(`/app/grab`, JSON.stringify({
          gameId: gameId.value,
          playerIndex: playerIndex,
        })
    );
  }

  function onResponse(response: Response) {
    eventQueue.push(response);
    if (!isProcessingQueue) drainQueue();
  }

  async function drainQueue() {
    isProcessingQueue = true;
    while (eventQueue.length > 0) {
      const response = eventQueue.shift()!;
      await processEvent(response);
    }
    isProcessingQueue = false;
  }

  async function processEvent(response: Response) {
    const skipAnimation = eventQueue.length >= CATCHUP_THRESHOLD;
    let needsAnimationWait = false;

    if (response.eventType === 'CREATE') {
      const createData = response.eventData as CreateEventData;
      gameId.value = createData.game.id;
    }

    if (response.eventType === 'GRAB') {
      const grabData = response.eventData as GrabEventData;
      const winnerPlayerIndex = Number(grabData.player?.id);
      if (!isNaN(winnerPlayerIndex)) {
        const pileCards = [...(state.value?.pile.cards ?? [])];
        lastGrab.value = { winnerPlayerIndex, seq: ++grabSeq, pileCards };
        needsAnimationWait = true;
      }
    }

    if (response.eventType === 'SLAP') {
      const slapData = response.eventData as SlapEventData;
      const slapperIndex = Number(slapData.player?.id);
      if (slapData.isSuccessful && !isNaN(slapperIndex)) {
        const pileCards = [...(state.value?.pile.cards ?? [])];
        lastSuccessfulSlap.value = { winnerPlayerIndex: slapperIndex, seq: ++successfulSlapSeq, pileCards };
        needsAnimationWait = true;
      } else if (!slapData.isSuccessful && !isNaN(slapperIndex)) {
        lastErroneousSlap.value = { playerIndex: slapperIndex, seq: ++erroneousSlapSeq };
        needsAnimationWait = true;
      }
    }

    state.value = response.state;

    if (autoGrabTimeoutId !== null) {
      clearTimeout(autoGrabTimeoutId);
      autoGrabTimeoutId = null;
    }
    if (autoGrabEnabled && state.value.pile.grabbable) {
      autoGrabTimeoutId = setTimeout(() => {
        autoGrabTimeoutId = null;
        autoGrab();
      }, 1500);
    }

    // player0Ai.play();
    player1Ai.play();

    if (needsAnimationWait && !skipAnimation) {
      await new Promise<void>(resolve => { animationResolve = resolve; });
    }
  }

  /** Called by the component once a blocking animation (GRAB/SLAP) finishes. */
  function notifyAnimationComplete() {
    animationResolve?.();
    animationResolve = null;
  }

  function cancelAutoGrab() {
    if (autoGrabTimeoutId !== null) {
      clearTimeout(autoGrabTimeoutId);
      autoGrabTimeoutId = null;
    }
  }

  function autoGrab() {
    if (state.value.pile.grabbable) {
      const playerIndex = Number(state.value.pile.playerThatAddedLastHonourCard.id);
      if (playerIndex != undefined) {
        grab((playerIndex));
      }
    }
  }

  return {
    state,
    lastSend,
    lastGrab,
    lastSlap,
    lastSuccessfulSlap,
    lastErroneousSlap,
    create,
    send,
    slap,
    grab,
    onResponse,
    notifyAnimationComplete,
    cancelAutoGrab,
  }

});
