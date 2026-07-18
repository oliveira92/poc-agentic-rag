import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

/**
 * Proxy para a API Spring (localhost:8080): a UI conversa com /api e /actuator na MESMA
 * origem — zero CORS, zero mudança no backend. Vale para `dev` e para `preview`.
 */
const proxy = {
  '/api': 'http://localhost:8080',
  '/actuator': 'http://localhost:8080',
};

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy },
  preview: { port: 4173, proxy },
});
