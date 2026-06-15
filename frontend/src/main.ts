
import { createApp } from 'vue';
import App from './App.vue';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import Nora from '@primeuix/themes/nora';
import Lara from '@primeuix/themes/lara';

import webSocketService from './service/WebSocketService';
import { createPinia } from 'pinia';

const app = createApp(App);
app.use(PrimeVue, {
  theme: {
    preset: Aura,
    options: {
      prefix: 'p',
      darkModeSelector: 'system',
      cssLayer: {
        name: 'primevue',
        order: 'theme, base, primevue'
      }
    }
  }
});

import LandingView from './view/LandingView.vue';
import GameScreen from './view/alpha/GameScreen.vue';
import StartGame from './view/alpha/StartGame.vue';
import LobbyView from './view/alpha/LobbyView.vue';
import BullshitGameScreen from './view/bullshit/BullshitGameScreen.vue';
import BullshitStartGame from './view/bullshit/BullshitStartGame.vue';
import { createWebHistory, createRouter } from 'vue-router';

const BASE = '/games/bataillecorse';

const routes = [
  { path: '/', component: LandingView },
  { path: '/games', redirect: { name: 'home' } },
  { path: `${BASE}`,        name: 'home',   component: LobbyView },
  { path: `${BASE}/create`, name: 'create', component: StartGame },
  { path: `${BASE}/join/:id?`, name: 'join', component: StartGame },
  { path: `${BASE}/room/:id`,  name: 'room', component: GameScreen },
  { path: '/games/bullshit/create',    name: 'bullshit-create', component: BullshitStartGame },
  { path: '/games/bullshit/join/:id?', name: 'bullshit-join',   component: BullshitStartGame },
  { path: '/games/bullshit/room/:id',  name: 'bullshit-room',   component: BullshitGameScreen, props: route => ({ gameId: route.params.id }) },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
});

const pinia = createPinia();
app.use(pinia);
app.use(router);

app.mount('#app');
webSocketService.init();

