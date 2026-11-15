import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development, API calls are proxied to the Spring Boot backend on port 8080,
// so the browser sees one origin (same as production behind nginx).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
  build: {
    sourcemap: false,
  },
});
