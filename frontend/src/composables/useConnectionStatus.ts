import { readonly, ref } from 'vue';

// Whether this browser currently has a live socket to the server. Written by the WebSocket
// service (connect / close), read by the game screens to tell the player they are offline.
// Optimistic at start: the screens have their own "joining" state before the first connection.
const online = ref(true);

export function setConnectionOnline(value: boolean): void {
  online.value = value;
}

export function useConnectionStatus() {
  return { online: readonly(online) };
}
