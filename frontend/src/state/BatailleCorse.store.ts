import { defineStore } from "pinia";
import { readonly, Ref, ref } from "vue";

import webSocketService from '../service/WebSocketService';
import BatailleCorse from "../service/model/BatailleCorse";
import Response from "../service/model/Response";
import AI from "../service/model/ai/AI";
import SlapEventData from '../service/model/event/SlapEventData';
import GrabEventData from '../service/model/event/GrabEventData';
import CreateEventData from "../service/model/event/CreateEventData";

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

  let erroneousSlapSeq = 0;
  const lastErroneousSlap = ref<{ playerIndex: number; seq: number } | null>(null);

  const gameId = ref<string | null>(null);

  // const player0Ai = new AI(0, 500);
  const player1Ai = new AI(1, 1800);

  function create(playerName?: string) {
    webSocketService.publish('/app/create', playerName ? JSON.stringify({ playerName }) : undefined);
  }

  function send(playerIndex: number) {
    lastSend.value = { playerIndex, seq: ++sendSeq };
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
    // TODO: Responsibility separation for various event type handlers
    if (response.eventType === 'CREATE') {
      const createData = response.eventData as CreateEventData;
      gameId.value = createData.game.id;
    }

    if (response.eventType === 'GRAB') {
      const grabData = response.eventData as GrabEventData;
      const winnerPlayerIndex = Number(grabData.player?.id);
      if (!isNaN(winnerPlayerIndex)) {
        lastGrab.value = { winnerPlayerIndex, seq: ++grabSeq };
      }
    }

    if (response.eventType === 'SLAP') {
      const slapData = response.eventData as SlapEventData;
      const slapperIndex = Number(slapData.player?.id);
      if (slapData.isSuccessful && !isNaN(slapperIndex)) {
        lastSuccessfulSlap.value = { winnerPlayerIndex: slapperIndex, seq: ++successfulSlapSeq };
      } else if (!slapData.isSuccessful && !isNaN(slapperIndex)) {
        lastErroneousSlap.value = { playerIndex: slapperIndex, seq: ++erroneousSlapSeq };
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
  }

});
