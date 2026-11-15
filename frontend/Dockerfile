# ---- Build stage: produces the static React site ----
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

# ---- Runtime stage: nginx serves the site and forwards /api to the backend ----
FROM nginxinc/nginx-unprivileged:1.29-alpine
# Where nginx forwards API calls. Override at runtime, e.g. BACKEND_URL=http://api.internal:8080
ENV BACKEND_URL=http://api:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 8080
