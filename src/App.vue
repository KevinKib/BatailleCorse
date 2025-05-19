<script setup></script>
<template>
  <h1>Test WebSocket Vue</h1>
</template>

<!-- <script setup>
import { onMounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

onMounted(() => {
  console.log("🧩 WebSocket setup (Vue)")

  const client = new Client({
    // webSocketFactory: () => new SockJS(`${import.meta.env.VITE_BACKEND_URL}/connect`),
    // webSocketFactory: () => new SockJS('http://host.docker.internal:8080/connect'),
    webSocketFactory: () => new SockJS('/connect'),
    reconnectDelay: 5000,
    debug: (str) => console.log(`[STOMP] ${str}`),
    onConnect: () => {
      console.log('✅ Connected to STOMP')

      client.subscribe('/topic/greetings', (message) => {
        console.log('📨 Received:', message.body)
      })

      client.publish({
        destination: '/app/hello',
        body: 'Hello from Vue with @stomp/stompjs',
      })
    },
    onStompError: (frame) => {
      console.error('❌ STOMP Error:', frame.headers['message'])
      console.error('Details:', frame.body)
    }
  })

  client.activate()
})
</script>

<template>
  <h1>Test WebSocket Vue</h1>
</template> -->