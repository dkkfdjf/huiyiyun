# syntax=docker/dockerfile:1
# 慧医云前端镜像:多阶段构建
#   build  阶段用 node:22-alpine 跑 vite build → /app/dist(Vite 在 build 时把 VITE_* 写死进产物)
#   runtime 阶段用 nginx:stable-alpine 托管 dist,并把 /api/ 反代到 compose 里的 backend 服务
#
# 构建上下文 = frontend/ 目录。高德地图密钥经 build args 注入(来自 .env,不进代码仓库)。

# ===== Stage 1: build =====
FROM node:22-alpine AS build
WORKDIR /app
# 先拷锁文件单独装依赖,做缓存层:改代码不重装 node_modules
COPY package.json package-lock.json ./
RUN npm ci
# 高德地图 Web JS API 密钥(build-time):由 docker-compose 的 build.args 注入(源自 .env)
# 留空时前端自动降级(占位地图 + 手填经纬度),不影响网点 CRUD
ARG VITE_AMAP_KEY=
ARG VITE_AMAP_SECURITY=
ENV VITE_AMAP_KEY=${VITE_AMAP_KEY}
ENV VITE_AMAP_SECURITY=${VITE_AMAP_SECURITY}
COPY . .
RUN npm run build

# ===== Stage 2: nginx 托管 + 反代 =====
FROM nginx:stable-alpine
# 删掉默认 server 块,换成本项目的(含 /api/ → backend:8080 反代 + SPA 回退)
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
