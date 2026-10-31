# syntax=docker/dockerfile:1
#
# Production image of the TSPodcast web (Next.js standalone server).
# Built by .github/workflows/deploy-web.yml, or on the VPS with
# `docker compose build web` (docs/deploy/README.md).
#
# No secret goes into this image: PODCASTINDEX_* and ANTHROPIC_API_KEY are read
# at runtime from /etc/tspodcast/web.env (deploy/docker-compose.yml). Only the
# public NEXT_PUBLIC_* values below are baked in, because Next.js inlines them
# into the server and browser bundles at build time.

ARG NODE_IMAGE=node:22-alpine

# ---------- deps: install exactly what package-lock.json says ----------
FROM ${NODE_IMAGE} AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund

# ---------- build: next build with output: "standalone" ----------
FROM ${NODE_IMAGE} AS build
WORKDIR /app
# Canonical origin (canonical URLs, Open Graph, sitemap, robots). Required.
ARG NEXT_PUBLIC_SITE_URL
# UI language of this deployment: vi or en.
ARG NEXT_PUBLIC_SITE_LOCALE=vi
# "1" only for a staging/preview image: robots.txt is static and disallows all.
ARG SITE_NOINDEX=
ENV NEXT_PUBLIC_SITE_URL=${NEXT_PUBLIC_SITE_URL} \
    NEXT_PUBLIC_SITE_LOCALE=${NEXT_PUBLIC_SITE_LOCALE} \
    SITE_NOINDEX=${SITE_NOINDEX} \
    NEXT_OUTPUT_STANDALONE=1 \
    NEXT_TELEMETRY_DISABLED=1
RUN if [ -z "$NEXT_PUBLIC_SITE_URL" ]; then \
      echo "NEXT_PUBLIC_SITE_URL build arg is required (e.g. https://example.com)" >&2; exit 1; \
    fi
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# After the build: drop the keyless build-time copies of ISR pages
# (scripts/prune-isr-seeds.mjs), add public/ and static assets, and move the
# compiled server into a seed folder that docker-entrypoint.sh copies into the
# writable .next/server mount at start (Next writes ISR pages there).
RUN npm run build \
 && node scripts/prune-isr-seeds.mjs .next/standalone/.next \
 && cp -r public .next/standalone/public \
 && cp -r .next/static .next/standalone/.next/static \
 && mv .next/standalone/.next/server .next/standalone/.next/server.seed \
 && mkdir -p .next/standalone/.next/server .next/standalone/.next/cache

# ---------- runtime ----------
FROM ${NODE_IMAGE} AS runtime
WORKDIR /app
# Docker sets HOSTNAME to the container id; Next binds to it unless overridden.
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

# Code is owned by root (the node user cannot change it even without
# read_only); only the two folders Next writes to at runtime belong to node.
COPY --from=build /app/.next/standalone ./
COPY --chmod=0755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chown node:node /app/.next/server /app/.next/cache

ARG GIT_SHA=unknown
LABEL org.opencontainers.image.title="tspodcast-web" \
      org.opencontainers.image.description="TSPodcast web (Next.js standalone)" \
      org.opencontainers.image.revision="${GIT_SHA}"

# In production the container runs with read_only: true; .next/server,
# .next/cache and /tmp are the only writable mounts (deploy/docker-compose.yml).
USER node
EXPOSE 3000

# robots.txt is static (no PodcastIndex call), so this checks the server only.
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD ["node", "-e", "fetch('http://127.0.0.1:'+(process.env.PORT||3000)+'/robots.txt').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
CMD ["node", "server.js"]
