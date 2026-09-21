import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // dev only: forward API calls to Spring Boot so the browser sees a single origin
    proxy: { '/api': 'http://localhost:8080' },
  },
})
