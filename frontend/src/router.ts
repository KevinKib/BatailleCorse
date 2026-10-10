import { createRouter, createWebHistory, type Router, type RouterHistory } from 'vue-router';
import GamePickerView from './view/GamePickerView.vue';
import GameScreen from './view/alpha/GameScreen.vue';
import StartGame from './view/alpha/StartGame.vue';
import LobbyView from './view/alpha/LobbyView.vue';
import BullshitGameScreen from './view/bullshit/BullshitGameScreen.vue';
import BullshitStartGame from './view/bullshit/BullshitStartGame.vue';

const BASE = '/games/bataillecorse';

// `/` and any unknown path lead to the game picker; invite links keep their paths.
export const routes = [
  { path: '/', redirect: { name: 'games' } },
  { path: '/games', name: 'games', component: GamePickerView },
  { path: `${BASE}`,        name: 'home',   component: LobbyView },
  { path: `${BASE}/create`, name: 'create', component: StartGame },
  { path: `${BASE}/join/:id?`, name: 'join', component: StartGame },
  { path: `${BASE}/room/:id`,  name: 'room', component: GameScreen },
  { path: '/games/bullshit/create',    name: 'bullshit-create', component: BullshitStartGame },
  { path: '/games/bullshit/join/:id?', name: 'bullshit-join',   component: BullshitStartGame },
  { path: '/games/bullshit/room/:id',  name: 'bullshit-room',   component: BullshitGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/:pathMatch(.*)*', redirect: { name: 'games' } },
];

export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  return createRouter({ history, routes });
}
