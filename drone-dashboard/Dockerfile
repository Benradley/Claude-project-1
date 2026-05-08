# ── Stage 1: Build ─────────────────────────────────────────────────────────────
# Node 20 Alpine to install deps and run the Vite production build.
FROM node:20-alpine AS build

WORKDIR /app

# Install deps first (cached layer — only re-runs when package-lock.json changes)
COPY package*.json ./
RUN npm ci --silent

# Copy source and build
COPY . .
RUN npm run build
# Output goes to /app/dist

# ── Stage 2: Serve ─────────────────────────────────────────────────────────────
# Minimal Nginx Alpine to serve the static bundle and proxy /api/* to the backend.
FROM nginx:alpine

# Replace the default Nginx config with our SPA + proxy config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built Vite output
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
