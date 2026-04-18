# ---- Base stage ----
FROM node:20-alpine AS base

WORKDIR /app

# Install dependencies
COPY package*.json ./
RUN npm ci


# ---- Dev stage ----
FROM base AS dev

# Copy source
COPY . .

# Run app
CMD ["npm", "run", "dev", "--", "--host", "0.0.0.0"]

# ---- Build stage ----
FROM base AS build

# Copy source
COPY . .

# Run app
RUN npm run build


# ---- Production stage ----
FROM nginx:alpine AS prod

# Copy base output
COPY --from=build /app/dist /usr/share/nginx/html

# Expose web port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]