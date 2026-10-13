import { createRouter, createWebHistory, type RouteLocationNormalized, type Router, type RouterHistory } from 'vue-router';
import GamePickerView from './view/GamePickerView.vue';
import GameHomeView from './view/GameHomeView.vue';
import GameJoinView from './view/GameJoinView.vue';
import GameScreen from './view/alpha/GameScreen.vue';
import BullshitGameScreen from './view/bullshit/BullshitGameScreen.vue';
import KoboGameScreen from './view/kobo/KoboGameScreen.vue';
import ThreeMenMorrisGameScreen from './view/threemenmorris/ThreeMenMorrisGameScreen.vue';
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
  // Both games create their lobby straight from the Game home: the former /create and /new paths lead there.
  { path: `${BASE}/new`, redirect: BASE },
  { path: `${BASE}/create`, redirect: BASE },
  { path: `${BASE}/room/:id`, name: 'room', component: GameScreen },
  { path: '/games/bullshit/new', redirect: '/games/bullshit' },
  { path: '/games/bullshit/create', redirect: '/games/bullshit' },
  { path: '/games/bullshit/room/:id', name: 'bullshit-room', component: BullshitGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/games/kobo/room/:id', name: 'kobo-room', component: KoboGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/games/three-men-morris/new', redirect: '/games/three-men-morris' },
  { path: '/games/three-men-morris/create', redirect: '/games/three-men-morris' },
  { path: '/games/three-men-morris/room/:id', name: 'three-men-morris-room', component: ThreeMenMorrisGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/:pathMatch(.*)*', redirect: { name: 'games' } },
];

export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  return createRouter({ history, routes });
}
