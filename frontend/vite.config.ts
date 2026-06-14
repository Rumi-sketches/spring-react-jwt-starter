import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const BACKEND_URL = process.env.VITE_BACKEND_URL || 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',        // necessario quando gira in Docker
    proxy: {
      '/api': {
        target: BACKEND_URL,
        changeOrigin: true,
      }
    }
  }
})