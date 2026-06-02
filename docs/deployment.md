# Deployment

## Voraussetzungen

Erstelle `.env.db` im Projektverzeichnis (nie committen — steht in `.gitignore`):

```
MARIADB_ROOT_PASSWORD=<geheim>
MARIADB_DATABASE=CLIMATE
MARIADB_USER=climate_user
MARIADB_PASSWORD=<geheim>
```

## Entwicklung (Dev)

```bash
docker compose up -d
```

`docker-compose.yml` — startet db (Port `${DB_PORT:-3306}` auf dem Host erreichbar zum Debuggen), migrations und app.

## Produktion

```bash
docker compose -f docker-compose.prod.yml up -d
```

`docker-compose.prod.yml` — identischer Stack, aber der db-Service exportiert **keinen** Host-Port. Die Datenbank ist ausschliesslich compose-intern erreichbar (`expose: ["3306"]`). Der App-Port (`${APP_PORT:-8092}`) bleibt nach aussen offen.

## Migrations-Runner (manuell)

```bash
docker compose [-f docker-compose.prod.yml] run --rm migrations
```
