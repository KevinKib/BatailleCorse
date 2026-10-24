import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import svgLoader from 'vite-svg-loader'

// The backend is the Compose service `backend`, so both proxies only resolve on the Compose network.
const backendProxy = {
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

export default defineConfig({
  plugins: [vue(), tailwindcss(), svgLoader()],
  test: {
    environment: 'happy-dom',
  },
  server: {
    host: true,
    port: 5173,
    // Specs and Cypress files are not part of the app's module graph, but Vite still
    // reloads the page for them and the polling watcher (CHOKIDAR_USEPOLLING in the dev
    // container) stats every file on a bind mount several times a second. Keep them out so
    // editing a test does not reload a game in progress and the idle dev server stays cheap.
    // Skipped under Vitest, whose watch mode relies on the same watcher to re-run tests.
    watch: process.env.VITEST ? undefined : {
      ignored: ['**/*.test.ts', '**/*.cy.ts', '**/cypress/**', '**/dist/**'],
    },
    hmr: {
      host: 'localhost',
      // Port the browser uses for the hot-reload socket. Override it when the stack
      // is published on another host port (parallel dev stacks, see CLAUDE.md).
      clientPort: Number(process.env.VITE_HMR_CLIENT_PORT) || 5173,
    },
    proxy: backendProxy,
  },
  // `vite preview` serves the production build for the e2e stack (frontend Dockerfile, target e2e)
  // on the dev server's container port, so the Compose port mappings stay the same.
  preview: {
    host: true,
    port: 5173,
    strictPort: true,
    proxy: backendProxy,
  },
  // Absolute base so asset URLs (/assets/...) resolve correctly on deep links
  // like /room/:id. A relative base ('./') breaks hard-loaded nested routes:
  // the browser resolves ./assets against /room/ and 404s into the SPA fallback.
  base: '/',
})
