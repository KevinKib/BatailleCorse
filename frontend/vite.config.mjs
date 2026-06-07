import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import svgLoader from 'vite-svg-loader'

export default defineConfig({
  plugins: [vue(), tailwindcss(), svgLoader()],
  test: {
    environment: 'happy-dom',
  },
  server: {
    host: true,
    port: 5173,
    hmr: {
      host: 'localhost',
      port: 5173,
    },
    proxy: {
      "/connect": {
        target: "http://backend:8080",
        ws: true,
        changeOrigin: true
      },
      "/api": {
        target: "http://backend:8080",
        changeOrigin: true
      }
    }
  },
  // Absolute base so asset URLs (/assets/...) resolve correctly on deep links
  // like /room/:id. A relative base ('./') breaks hard-loaded nested routes:
  // the browser resolves ./assets against /room/ and 404s into the SPA fallback.
  base: '/',
})
