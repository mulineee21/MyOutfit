# ==============================================================================
# Etapa 1: Build TypeScript (Node.js 22 sobre Debian Bookworm Slim para Sharp/libvips)
# ==============================================================================
FROM node:22-bookworm-slim AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# ==============================================================================
# Etapa 2: Runtime ligero y endurecido en producción
# ==============================================================================
FROM node:22-bookworm-slim AS runner

ENV NODE_ENV=production
WORKDIR /app

# Crear estructura del volumen multimedia y asignar permisos al usuario sin privilegios `node`
RUN mkdir -p /var/lib/myoutfit/media/avatars \
             /var/lib/myoutfit/media/outfits/full \
             /var/lib/myoutfit/media/outfits/thumbs \
    && chown -R node:node /var/lib/myoutfit/media /app

COPY --chown=node:node package*.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY --from=builder --chown=node:node /app/dist ./dist

USER node
EXPOSE 3000

CMD ["node", "dist/server.js"]
