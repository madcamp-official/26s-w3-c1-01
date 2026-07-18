# 프론트(Next.js). 백엔드는 server/Dockerfile에 따로 있다.

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# ⚠️ NEXT_PUBLIC_* 는 빌드 시점에 번들에 **박힌다.** 런타임 환경변수로는 못 바꾼다.
# 도메인이 바뀌면 다시 빌드해야 한다. compose가 build args로 넘겨준다.
ARG NEXT_PUBLIC_SITE_URL
ENV NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
# standalone은 필요한 node_modules만 추려서 server.js 옆에 넣어 준다.
COPY --from=build /app/public ./public
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
USER node
EXPOSE 3000
CMD ["node", "server.js"]
