import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },

  build: {
    // Output to src/main/resources/static/ — Spring Boot serves this at /
    // and spring-boot-maven-plugin bundles it into the fat JAR.
    outDir: '../resources/static',
    emptyOutDir: true,
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          react:  ['react', 'react-dom', 'react-router-dom'],
          query:  ['@tanstack/react-query'],
          vendor: ['axios', 'zustand', 'zod'],
        },
      },
    },
  },

  server: {
    port: 5173,
    // Proxy all /api calls to the running Spring Boot backend (no CORS needed).
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});

