# 빌드 단계 — devDependencies(typescript)가 여기에만 있고 런타임 이미지엔 안 남는다.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist
# root로 돌 이유가 없다.
USER node
EXPOSE 8080
CMD ["node", "dist/index.js"]
