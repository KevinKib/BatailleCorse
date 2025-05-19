import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // define: {
  //   global: {},
  // },
  server: {
    host: true,
    port: 5173,
  }
})
// server: {
  //   proxy: {
  //     '/connect': 'http://localhost:8080',
  //   },
  // },

// server: {
  //   proxy: {
  //     '/connect': {
  //       target: 'http://172.31.112.1:8080',
  //       changeOrigin: true,
  //       ws: true,
  //     },
  //   },
  // },
  // server: {
  //   host: true, // ← permet l’accès réseau externe
  //   proxy: {
  //     '/connect': {
  //       target: 'http://host.docker.internal:8080',
  //       changeOrigin: true,
  //       ws: true,
  //     },
  //   },
  // },
  // server: {
  //   host: '0.0.0.0', // ← rend le serveur accessible depuis le réseau
  //   port: 5173,
  //   proxy: {
  //     '/connect': {
  //       target: 'http://host.docker.internal:8080', // ← redirige vers Spring Boot sur Windows
  //       changeOrigin: true,
  //       ws: true,
  //     },
  //   },
  // },
  // server: {
  //   proxy: {
  //     '/connect': {
  //       target: 'http://host.docker.internal:8080',
  //       changeOrigin: true,
  //       ws: true,
  //       configure: (proxy, options) => {
  //         proxy.on('proxyReq', (proxyReq, req, res) => {
  //           console.log('Proxying WS request:', req.url);
  //         });
  //       }
  //     },
  //   },
  // },