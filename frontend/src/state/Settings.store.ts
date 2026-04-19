import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

export const useSettingsStore = defineStore('settings', () => {
  const playerName = ref<string>(localStorage.getItem('bc_playerName') ?? '');
  const sendKey    = ref<string>(localStorage.getItem('bc_sendKey')    ?? 'q');
  const slapKey    = ref<string>(localStorage.getItem('bc_slapKey')    ?? 'd');
  const difficulty = ref<number>(Number(localStorage.getItem('bc_difficulty') ?? 1));

  watch(playerName, v => localStorage.setItem('bc_playerName', v));
  watch(sendKey,    v => localStorage.setItem('bc_sendKey', v));
  watch(slapKey,    v => localStorage.setItem('bc_slapKey', v));
  watch(difficulty, v => localStorage.setItem('bc_difficulty', String(v)));

  return { playerName, sendKey, slapKey, difficulty };
});
