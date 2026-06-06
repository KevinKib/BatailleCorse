
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

import Debug from './view/debug/Debug.vue';
import GameScreen from './view/alpha/GameScreen.vue';
import StartGame from './view/alpha/StartGame.vue';
import LobbyView from './view/alpha/LobbyView.vue';
import { createWebHistory, createRouter } from 'vue-router';

const routes = [
  { path: '/', component: LobbyView },
  { path: '/create', component: StartGame },
  { path: '/join/:id?', component: StartGame },
  { path: '/room/:id', component: GameScreen },
  { path: '/debug', component: Debug },
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

