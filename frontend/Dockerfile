FROM node:22-alpine

WORKDIR /app

# Dipendenze prima del codice (cache)
COPY package*.json ./
RUN npm ci

COPY . .

EXPOSE 5173

# Vite dev server — host: '0.0.0.0' già configurato in vite.config.ts
CMD ["npm", "run", "dev"]