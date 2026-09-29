# ============================================================================
#  Imagen del frontend Angular de FoodFlow (HU-607)
# ============================================================================
#  Compila la aplicación con Node y la sirve como estáticos con Nginx.
#  El navegador solo habla con el API Gateway (regla 1): la URL base es la de
#  core/api-config.ts (http://localhost:8080, puerto GATEWAY_PORT publicado).
#  Imágenes: docs/wiki/04-implementacion/versiones.md (Node.js 24.21.0, Nginx 1.30.5).
# ============================================================================
FROM node:24.21.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.30.5-alpine
COPY --from=build /app/dist/foodflow-web/browser/ /usr/share/nginx/html/
# Las rutas de Angular (/orders, /orders/{id}) se resuelven en el navegador: cualquier ruta
# que no sea un archivo devuelve index.html.
COPY <<'CONF' /etc/nginx/conf.d/default.conf
server {
    listen 80;
    root /usr/share/nginx/html;
    location / {
        try_files $uri $uri/ /index.html;
    }
}
CONF
