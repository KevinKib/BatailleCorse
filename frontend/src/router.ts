import { createRouter, createWebHistory, type RouteLocationNormalized, type Router, type RouterHistory } from 'vue-router';
import GamePickerView from './view/GamePickerView.vue';
import GameHomeView from './view/GameHomeView.vue';
import GameJoinView from './view/GameJoinView.vue';
import GameScreen from './view/alpha/GameScreen.vue';
import StartGame from './view/alpha/StartGame.vue';
import BullshitGameScreen from './view/bullshit/BullshitGameScreen.vue';
import BullshitStartGame from './view/bullshit/BullshitStartGame.vue';
import { findGame } from './model/games';

const BASE = '/games/bataillecorse';

// A `:game` that is not in the registry (for example a game whose frontend does not exist yet)
// leads back to the Game selection.
const knownGame = (to: RouteLocationNormalized) => (findGame(to.params.game) ? true : { name: 'games' });

// `/` and any unknown path lead to the game picker; invite links keep their paths.
export const routes = [
  { path: '/', redirect: { name: 'games' } },
  { path: '/games', name: 'games', component: GamePickerView },
  { path: '/games/:game', name: 'game-home', component: GameHomeView, beforeEnter: knownGame },
  { path: '/games/:game/join/:id?', name: 'game-join', component: GameJoinView, beforeEnter: knownGame },
  // The create pages of the two games live under /new until the shared lobby creates games
  // straight away; the former /create paths lead to the Game home.
  { path: `${BASE}/new`, name: 'create', component: StartGame },
  { path: `${BASE}/create`, redirect: BASE },
  { path: `${BASE}/room/:id`, name: 'room', component: GameScreen },
  { path: '/games/bullshit/new', name: 'bullshit-create', component: BullshitStartGame },
  { path: '/games/bullshit/create', redirect: '/games/bullshit' },
  { path: '/games/bullshit/room/:id', name: 'bullshit-room', component: BullshitGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/:pathMatch(.*)*', redirect: { name: 'games' } },
];

export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  return createRouter({ history, routes });
}
