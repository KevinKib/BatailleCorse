import { defineStore } from "pinia";
import { readonly, Ref, ref } from "vue";

import webSocketService from '../service/WebSocketService';
import BatailleCorse from "../service/model/BatailleCorse";
import Response from "../service/model/Response";

export const useBatailleCorseStore = defineStore('bataille-corse-store', () => {

  const state = ref<BatailleCorse>();

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
    console.log(state.value);
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
