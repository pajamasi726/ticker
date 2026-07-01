import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies /api to the backend (./gradlew :ticker-server-sample:bootRun on :8080).
export default defineConfig({
  plugins: [react()],
  // Relative asset URLs so the bundle works whether served at "/" or under ticker.server.base-path
  // (e.g. "/ticker/") — resolved against the injected <base href>.
  base: './',
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    outDir: 'dist',
  },
})
