# syntax=docker/dockerfile:1.7

ARG NODE_IMAGE=node:24-alpine
ARG NGINX_IMAGE=nginx:1.28-alpine

FROM ${NODE_IMAGE} AS build
WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --ignore-scripts --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

FROM ${NGINX_IMAGE} AS runtime
LABEL org.opencontainers.image.title="enterprise-platform-frontend" \
      org.opencontainers.image.description="国企数字化治理与经营赋能平台前端"

COPY deploy/nginx/nginx.conf /etc/nginx/nginx.conf
COPY --from=build --chown=nginx:nginx /workspace/dist /usr/share/nginx/html

USER nginx
EXPOSE 8080

ENTRYPOINT ["nginx", "-g", "daemon off;"]
