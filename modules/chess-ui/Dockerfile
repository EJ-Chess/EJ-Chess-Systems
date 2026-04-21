# ── EJa Chess — Web UI ────────────────────────────────────────────────────────
# Multi-stage: Node builds the React app, nginx serves the static files
# and proxies /games → game-service:8080.
# ─────────────────────────────────────────────────────────────────────────────

# Stage 1 — Build
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2 — Serve
FROM nginx:alpine
COPY --from=build /app/dist          /usr/share/nginx/html
COPY             nginx.conf          /etc/nginx/conf.d/default.conf
EXPOSE 80
