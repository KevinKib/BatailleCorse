# HTTPS (Let's Encrypt) runbook

The prod stack terminates TLS at the nginx `gateway` using free, auto-renewing
Let's Encrypt certificates. This is a one-time setup; renewals are automatic.

Covered hostnames: **kevinkib.com** and **www.kevinkib.com**.

## How it works

- `gateway` listens on **80** (ACME challenge + redirect to HTTPS) and **443**
  (the app). Certs and the challenge webroot are shared via Docker volumes
  (`certbot-certs`, `certbot-webroot`).
- The `certbot` service runs `certbot renew` every 12h. The gateway reloads
  nginx every 6h so renewed certs are picked up with no downtime.
- `deploy/docker/init-letsencrypt.sh` performs the **first** issuance.

## Prerequisites (do these first)

1. **DNS** — both names must resolve to the server's public IP:
   ```bash
   dig +short kevinkib.com
   dig +short www.kevinkib.com
   ```
   Both must print the VPS IP. Fix the A records at your registrar if not, and
   wait for propagation before continuing.

2. **Firewall** — open both ports on the VPS:
   ```bash
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   ```
   (Also make sure your cloud provider's security group allows 80 and 443.)

3. **Code** — get this branch onto the server:
   ```bash
   git pull            # on main, once this PR is merged
   ```

## First issuance

From the **repo root** on the server:

```bash
# OPTIONAL dry run: edit init-letsencrypt.sh and set staging=1, run it, confirm
# it succeeds (untrusted cert), then set staging=0 and run again for the real one.
bash deploy/docker/init-letsencrypt.sh
```

Then bring the full stack up:

```bash
docker compose -f docker-compose.prod.yml up --build -d
```

> ⚠️ **Rate limits:** Let's Encrypt allows ~5 duplicate-cert issuances per week.
> If you'll be re-running this while debugging, use `staging=1` first to avoid
> getting locked out of real issuance.

## Verify

```bash
curl -sI http://kevinkib.com        | head -n1   # expect 301 -> https
curl -sI https://kevinkib.com       | head -n1   # expect 200
curl -sI https://www.kevinkib.com   | head -n1   # expect 200
```

In the browser: open `https://kevinkib.com`, confirm the padlock, start a solo
game, and confirm the **Copy** button on the "waiting for opponent" screen works
(the Clipboard API only works in a secure context — that was the original
reason for this change).

## Renewal

Automatic — nothing to do. To watch or force it:

```bash
docker compose -f docker-compose.prod.yml logs -f certbot
docker compose -f docker-compose.prod.yml run --rm certbot renew --dry-run
```

## Troubleshooting

- **`certbot` challenge fails:** DNS isn't pointing at the server yet, or port 80
  is blocked. Re-check the prerequisites. Test with `staging=1`.
- **nginx won't start ("cannot load certificate"):** the cert volume is empty —
  run `init-letsencrypt.sh` (it seeds a temporary cert so nginx can boot).
- **Browser still shows "Not secure":** hard-refresh; confirm 443 is published
  and the firewall/security group allows it.
