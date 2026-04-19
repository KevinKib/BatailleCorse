# ---- Base stage ----
FROM node:20-alpine AS base

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci

# Copy source
COPY . .

# ---- Dev stage ----
FROM base AS dev

# Run app
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

# ---- Build stage ----
FROM base AS build

# Run app
RUN npm run build


# ---- Production stage ----
FROM nginx:alpine AS prod

COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy base output
COPY --from=build /app/dist /usr/share/nginx/html

# Expose web port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]