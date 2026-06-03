import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// ── AWS build config ──────────────────────────────────────────────────────────
// Replace the original drone-dashboard/vite.config.js with this file when
// deploying to S3 + CloudFront.
//
// Key difference from the Cloudflare-tunnel version:
//   base: '/'  (not '/droneproject')
//
// CloudFront serves the SPA at the distribution root, so no sub-path prefix
// is needed.  API calls (/api/*) are routed by a CloudFront behaviour to
// Elastic Beanstalk — the relative /api paths in authApi.js / flightApi.js
// work unchanged.

export default defineConfig(() => ({
  plugins: [react()],

  // Serve at the CloudFront root in all environments.
  base: '/',

  server: {
    port: 5173,

    // Allow Cloudflare quick-tunnel hostnames during local testing.
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
