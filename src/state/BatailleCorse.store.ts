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

  // const player0Ai = new AI(0, 500);
  const player1Ai = new AI(1, 730);

  function create() {
    webSocketService.publish({
      destination: '/app/create'
    });
  }

  function send(playerIndex: number) {
    webSocketService.publish({
      destination: `/app/send/${playerIndex}`
    });
  }

  function slap(playerIndex: number) {
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
    create,
    send,
    slap,
    grab,
    onResponse,
  }
  
});
