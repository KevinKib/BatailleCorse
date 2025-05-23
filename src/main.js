
import { createApp } from 'vue';
import App from './App.vue';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';

import webSocketService from './service/WebSocketService';


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

app.mount('#app');
webSocketService.init();