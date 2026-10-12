import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Same-origin /api calls avoid CORS; port 5173 matches the ehr-web client's redirect URIs.
export default defineConfig({
  plugins: [react()],
  server: {
    // IPv4 loopback; Node may otherwise bind only ::1, which dev container port forwarding misses.
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: { '/api': process.env.API_URL ?? 'http://localhost:8080' },
  },
})
