import { createRouter, createWebHistory, type RouteLocationNormalized, type Router, type RouterHistory } from 'vue-router';
import GamePickerView from './view/GamePickerView.vue';
import GameHomeView from './view/GameHomeView.vue';
import PrivacyView from './view/PrivacyView.vue';
import GameJoinView from './view/GameJoinView.vue';
import BatailleCorseGameScreen from './view/bataillecorse/BatailleCorseGameScreen.vue';
import BullshitGameScreen from './view/bullshit/BullshitGameScreen.vue';
import KoboGameScreen from './view/kobo/KoboGameScreen.vue';
import ThreeMenMorrisGameScreen from './view/threemenmorris/ThreeMenMorrisGameScreen.vue';
import PresidentGameScreen from './view/president/PresidentGameScreen.vue';
import MangolaGameScreen from './view/mangola/MangolaGameScreen.vue';
import NinetyNineGameScreen from './view/ninetynine/NinetyNineGameScreen.vue';
import DraughtsGameScreen from './view/draughts/DraughtsGameScreen.vue';
import { findGame } from './model/games';

const BASE = '/bataillecorse';

// A `:game` that is not in the registry (for example a game whose frontend does not exist yet)
// leads back to the Game selection.
const knownGame = (to: RouteLocationNormalized) => (findGame(to.params.game) ? true : { name: 'games' });

// `/` is the game picker and any unknown path leads to it; invite links keep their paths.
// The former `/games` prefix (the site used to live under it) redirects, so old shared links keep working.
export const routes = [
  { path: '/', name: 'games', component: GamePickerView },
  { path: '/games/:rest(.*)*', redirect: (to: RouteLocationNormalized) => ({ path: `/${[to.params.rest].flat().filter(Boolean).join('/')}`, query: to.query, hash: to.hash }) },
  // Before `/:game`, so that `privacy` is never taken for a game key.
  { path: '/privacy', name: 'privacy', component: PrivacyView },
  { path: '/:game', name: 'game-home', component: GameHomeView, beforeEnter: knownGame },
  { path: '/:game/join/:id?', name: 'game-join', component: GameJoinView, beforeEnter: knownGame },
  // Both games create their lobby straight from the Game home: the former /create and /new paths lead there.
  { path: `${BASE}/new`, redirect: BASE },
  { path: `${BASE}/create`, redirect: BASE },
  { path: `${BASE}/room/:id`, name: 'room', component: BatailleCorseGameScreen },
  { path: '/bullshit/new', redirect: '/bullshit' },
  { path: '/bullshit/create', redirect: '/bullshit' },
  { path: '/bullshit/room/:id', name: 'bullshit-room', component: BullshitGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/kobo/room/:id', name: 'kobo-room', component: KoboGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/three-men-morris/new', redirect: '/three-men-morris' },
  { path: '/three-men-morris/create', redirect: '/three-men-morris' },
  { path: '/three-men-morris/room/:id', name: 'three-men-morris-room', component: ThreeMenMorrisGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/president/new', redirect: '/president' },
  { path: '/president/create', redirect: '/president' },
  { path: '/president/room/:id', name: 'president-room', component: PresidentGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/mangola/room/:id', name: 'mangola-room', component: MangolaGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/ninety-nine/room/:id', name: 'ninety-nine-room', component: NinetyNineGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/draughts/room/:id', name: 'draughts-room', component: DraughtsGameScreen, props: (route: { params: Record<string, unknown> }) => ({ gameId: route.params.id }) },
  { path: '/:pathMatch(.*)*', redirect: { name: 'games' } },
];

export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  return createRouter({ history, routes });
}
