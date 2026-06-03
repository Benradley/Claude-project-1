import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(() => ({
  plugins: [react()],

  // AWS production: the React SPA is served at the CloudFront distribution root.
  // CloudFront routes /api/* to Elastic Beanstalk, everything else to S3.
  // No sub-path prefix needed — base stays '/' in all environments.
  base: '/',

  server: {
    port: 5173,

    // Allow any hostname (Cloudflare quick tunnels generate a new URL each run).
    allowedHosts: true,

    // Proxy /api calls to the Spring Boot backend during local development.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
}))
