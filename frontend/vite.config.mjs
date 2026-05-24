import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import svgLoader from 'vite-svg-loader'

export default defineConfig({
  plugins: [vue(), tailwindcss(), svgLoader()],
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
  base: './',
  // resolve: {
  //   alias: {
  //     src: "/src"
  //   },
  // },
})