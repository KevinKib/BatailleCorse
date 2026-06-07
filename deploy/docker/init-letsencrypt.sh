#!/usr/bin/env bash
#
# One-time Let's Encrypt bootstrap for the nginx gateway.
#
# Solves the chicken-and-egg problem: the 443 server block in gateway.conf
# references certificate files that don't exist yet, so nginx can't start to
# answer the ACME HTTP-01 challenge. We first drop in a throwaway self-signed
# cert so nginx starts, then replace it with the real Let's Encrypt one.
#
# Run this ONCE on the server, from the repo root:
#   bash deploy/docker/init-letsencrypt.sh
#
# Renewals afterwards are automatic (the certbot service in the compose file).
set -euo pipefail

# --- Config -----------------------------------------------------------------
domains=(kevinkib.com www.kevinkib.com)
email="kevinbrivel.kibongui@gmail.com"   # used by Let's Encrypt for expiry notices
# Set to 1 to hit Let's Encrypt's STAGING environment first (untrusted certs,
# but far higher rate limits — use it to dry-run before the real issuance).
staging=0
# ----------------------------------------------------------------------------

compose="docker compose -f docker-compose.prod.yml"
primary="${domains[0]}"
live_path="/etc/letsencrypt/live/${primary}"

if [[ ! -f "deploy/docker/settings.xml" ]]; then
  echo "Error: run this from the repo root (deploy/docker/settings.xml not found)." >&2
  exit 1
fi

echo "### 1/5 Creating a temporary self-signed certificate for ${primary}..."
$compose run --rm --entrypoint "\
  sh -c 'mkdir -p ${live_path} && \
    openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
      -keyout ${live_path}/privkey.pem \
      -out ${live_path}/fullchain.pem \
      -subj /CN=localhost'" certbot

echo "### 2/5 Building and starting the gateway (so it can serve the challenge)..."
$compose up --build -d gateway

echo "### 3/5 Removing the temporary certificate..."
$compose run --rm --entrypoint "\
  rm -rf /etc/letsencrypt/live/${primary} \
         /etc/letsencrypt/archive/${primary} \
         /etc/letsencrypt/renewal/${primary}.conf" certbot

echo "### 4/5 Requesting the real Let's Encrypt certificate..."
domain_args=""
for d in "${domains[@]}"; do domain_args="${domain_args} -d ${d}"; done
staging_arg=""
[[ "${staging}" != "0" ]] && staging_arg="--staging"

$compose run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    ${staging_arg} ${domain_args} \
    --email ${email} --agree-tos --no-eff-email --force-renewal" certbot

echo "### 5/5 Reloading the gateway with the real certificate..."
$compose exec gateway nginx -s reload

echo
echo "Done. Bring the whole stack up with:"
echo "  ${compose} up --build -d"
