
import './fonts.css';
import { createApp } from 'vue';
import App from './App.vue';
import PrimeVue from 'primevue/config';
import { primeVueConfig } from './primeVueConfig';

import { initLocale } from './locales/locale';
import webSocketService from './service/WebSocketService';
import { createPinia } from 'pinia';

const app = createApp(App);
app.use(PrimeVue, primeVueConfig);

import { createAppRouter } from './router';

const router = createAppRouter();

const pinia = createPinia();
app.use(pinia);
app.use(router);

// The locale is resolved and its messages loaded before anything renders.
initLocale().then(() => {
  app.mount('#app');
  webSocketService.init();
});

