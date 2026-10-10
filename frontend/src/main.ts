
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

import { createAppRouter } from './router';

const router = createAppRouter();

const pinia = createPinia();
app.use(pinia);
app.use(router);

app.mount('#app');
webSocketService.init();

