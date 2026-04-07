import { defineStore } from "pinia";
import { readonly, Ref, ref } from "vue";

import webSocketService from '../service/WebSocketService';
import BatailleCorse from "../service/model/BatailleCorse";
import Response from "../service/model/Response";
import AI from "../service/model/ai/AI";

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

  let sendSeq = 0;
  const lastSend = ref<{ playerIndex: number; seq: number } | null>(null);

  let grabSeq = 0;
  const lastGrab = ref<{ winnerPlayerIndex: number; seq: number } | null>(null);

  let slapSeq = 0;
  const lastSlap = ref<{ seq: number } | null>(null);

  let successfulSlapSeq = 0;
  const lastSuccessfulSlap = ref<{ winnerPlayerIndex: number; seq: number } | null>(null);

  // const player0Ai = new AI(0, 500);
  const player1Ai = new AI(1, 600);

  function create() {
    webSocketService.publish({
      destination: '/app/create'
    });
  }

  function send(playerIndex: number) {
    lastSend.value = { playerIndex, seq: ++sendSeq };
    webSocketService.publish({
      destination: `/app/send/${playerIndex}`
    });
  }

  function slap(playerIndex: number) {
    lastSlap.value = { seq: ++slapSeq };
    webSocketService.publish({
      destination: `/app/slap/${playerIndex}`
    });
  }

  function grab(playerIndex: number) {
    webSocketService.publish({
      destination: `/app/grab/${playerIndex}`
    });
  }

  function onResponse(response: Response) {
    console.log('onResponse', response);

    if (response.eventType.trim() === 'GRAB') {
      const winnerPlayerIndex = Number(state.value?.pile.playerThatAddedLastHonourCard?.id);
      if (!isNaN(winnerPlayerIndex)) {
        lastGrab.value = { winnerPlayerIndex, seq: ++grabSeq };
      }
    }

    if (response.eventType === 'SLAP' && state.value?.pile.grabbable) {
      const winnerPlayerIndex = Number(response.state.pile.playerThatAddedLastHonourCard?.id);
      if (!isNaN(winnerPlayerIndex)) {
        lastSuccessfulSlap.value = { winnerPlayerIndex, seq: ++successfulSlapSeq };
      }
    }

    state.value = response.state;
    
    if (autoGrabEnabled && state.value.pile.grabbable) {
      setTimeout(autoGrab, 1500);
    }

    
    
    // player0Ai.play();
    player1Ai.play();
  }

  function autoGrab() {
    if (state.value.pile.grabbable) {
      // TODO: beware as pile can become grabbable on the next round. Reset the setTimeout if the pile is grabbed (?)

      const playerIndex = Number(state.value.pile.playerThatAddedLastHonourCard.id);
      if (playerIndex != undefined) {
        grab((playerIndex));
      }
    }
  }

  return {
    // state: readonly(state),
    state,
    lastSend,
    lastGrab,
    lastSlap,
    lastSuccessfulSlap,
    create,
    send,
    slap,
    grab,
    onResponse,
  }
  
});
