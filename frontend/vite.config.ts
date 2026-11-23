import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// This proxy exists only while `vite` dev server is running.
// `vite build` and `vite preview` do not proxy. A deployed build needs
// VITE_API_BASE_URL set to the backend origin, without a trailing slash.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
