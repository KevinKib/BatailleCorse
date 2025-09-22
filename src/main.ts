
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
import { createMemoryHistory, createRouter } from 'vue-router';

const routes = [
  { path: '/', component: StartGame },
  { path: '/game', component: GameScreen },
  { path: '/debug', component: Debug },
]

const router = createRouter({
  history: createMemoryHistory(),
  routes,
});

const pinia = createPinia();
app.use(pinia);
app.use(router);

app.mount('#app');
webSocketService.init();

