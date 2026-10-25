import Aura from '@primeuix/themes/aura';

// PrimeVue theme options, kept apart from main.ts so a test can read them.
export const primeVueConfig = {
  theme: {
    preset: Aura,
    options: {
      prefix: 'p',
      // The class index.html puts on <html>: always the dark tokens and `color-scheme: dark`, never the OS theme
      // ('system'), because the app has a single, dark look (black inherited text on the felt otherwise).
      darkModeSelector: '.my-app-dark',
      cssLayer: {
        name: 'primevue',
        order: 'theme, base, primevue'
      }
    }
  }
};
