# Operational Runbook (Phase 8c)

For the single-VPS Docker Compose + Nginx setup built in Phases 5-6, once it's actually deployed to a real
server with a real domain. Everything here assumes: this repo checked out on the server, `.env` filled in
(see `.env.example`), and the stack already running via

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

To save typing, put this in your shell first:

```bash
alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml'
```

(commands below use `dc` for that.)

## Deploy

**Quick deploy** - simplest, but there's a brief window where both `backend` and `backend2` can be down
together (Compose doesn't guarantee it restarts them one at a time):

```bash
git pull
dc up -d --build
```

**Safe (rolling) deploy** - redeploys one backend instance at a time, so the other keeps serving the whole
time; nginx's passive health check (Phase 6b) naturally stops sending it traffic during its brief restart.
This is the same idea a load balancer + multiple instances is for - use it:

```bash
git pull
dc build backend backend2 frontend

dc up -d --no-deps backend          # --no-deps: don't also touch mysql
# wait until it's healthy before touching the other one:
dc logs -f backend                  # ctrl-C once you see "Started SalesTrackerApplication"

dc up -d --no-deps backend2
dc logs -f backend2                 # same, wait for it to start

dc up -d --no-deps frontend         # rebuilds the static files; nginx itself doesn't need this step
```

**Either way, after backend/backend2 get recreated, reload nginx** so it picks up their new container IPs
(Phase 6b: nginx resolves those hostnames once, at startup/reload, not per request):

```bash
dc exec frontend nginx -s reload
```

**Database migrations** run automatically - Flyway applies any new migration in `backend/src/main/resources/
db/migration` the moment the new backend image starts. There's no separate migration step to run by hand.

**Verify the deploy worked**:
```bash
curl -s https://<your-domain>/actuator/health   # expect {"status":"UP"}
dc ps                                           # everything "healthy" or "running"
```

## Rollback

```bash
git checkout <previous-tag-or-commit>
```
then redeploy (quick or rolling, as above) with that older code.

**The one real nuance**: Flyway migrations are forward-only - there's no automatic "undo". Rolling back the
*code* does not roll back the *database schema*.
- If the bad deploy **didn't** add a new migration, rollback is simple and safe - the old code runs fine
  against the unchanged schema.
- If it **did** add a migration that already ran, rolling back the code while the schema stays ahead can
  break things (old code that doesn't expect the new column/table). In that case:
  1. Prefer **rolling forward** with a fix over rolling back, when possible - it's almost always simpler than
     trying to undo a schema change.
  2. If you genuinely need to undo the schema too, write a new migration that reverses it (Flyway has no
     auto-generated "down" migrations) and deploy that.
  3. If the data itself got corrupted by the bad migration, restoring from a backup (see below) taken before
     the deploy is the reliable way back - not a schema-level guess under pressure.

## Incident response

**First checks, in order**:
1. What did the uptime monitor alert say (Phase 8b), or hit `/actuator/health` yourself - is it nginx that's
   down, or a real backend behind it?
2. `dc ps` - which container is actually unhealthy or exited?
3. Logs - either Grafana (`Explore` → `{container="..."}`, if the monitoring overlay is running) or directly:
   `dc logs --tail 200 -f <service>`.

**Common issues**:
- **One container crash-looping**: `dc logs <service>` for the actual error, then `dc up -d --no-deps <service>`
  to recreate it (picks up `restart: unless-stopped`'s job too, but a manual recreate is faster to watch).
- **Disk filling up**: usually logs. `docker system df` to see what's using space. Compose already caps
  `frontend`'s log size (Phase 6c); if `backend`/`backend2`/`mysql` need the same, add the same `logging:`
  block used for `frontend` in `docker-compose.prod.yml`.
- **TLS certificate looks wrong/expired**: check the `certbot` container's logs (`dc logs certbot`); a
  renewal that succeeded but was never followed by a reload is the most likely cause - see "Certificate
  renewal" below.
- **Database connection errors from the backend**: check `dc ps mysql` and `dc logs mysql`; if MySQL itself
  is fine, check `MYSQL_PASSWORD`/`DB_HOST` in `.env` haven't drifted from what the container was started
  with (an `.env` edit doesn't affect an already-running container until it's recreated).
- **Data looks wrong/missing after a bad deploy or an operator mistake**: this is when to restore from
  backup - see below. Don't try to hand-fix rows under pressure; restore to a known-good point and redo
  whatever legitimate work happened after it, if any.

## Backup & restore (Phase 8a)

Scripts: `scripts/backup-db.sh`, `scripts/restore-db.sh` (see README: "Backups & recovery" for what they do).

**Scheduled backup** - add to the server's crontab (`crontab -e`), daily at 3am server time:
```
0 3 * * * cd /path/to/this/repo && ./scripts/backup-db.sh >> /var/log/sales-tracker-backup.log 2>&1
```

**Restore drill** - do this quarterly against the real bucket, not just once when it's built. "A backup
you've never restored from is not a backup":
1. Pick a recent backup:
   ```bash
   set -a; . ./.env; set +a
   docker run --rm -e AWS_ACCESS_KEY_ID="$BACKUP_S3_ACCESS_KEY" -e AWS_SECRET_ACCESS_KEY="$BACKUP_S3_SECRET_KEY" \
     amazon/aws-cli s3 ls "s3://$BACKUP_S3_BUCKET/" --endpoint-url "$BACKUP_S3_ENDPOINT"
   ```
2. Run `./scripts/restore-db.sh <that-filename>` for real, against the live database - it already asks you
   to type the database name to confirm before touching anything, which is the same confirmation a genuine
   incident restore would need, so the drill exercises the exact real procedure rather than a simulated one.
   (This is exactly the kind of thing worth doing right after a backup, on a quiet day, precisely so it's
   not a surprise the first time it matters for real.)
3. Confirm: log into the app, check a tenant you know existed, check its recent orders are there.
4. Note how long the whole thing took - that's your real recovery time, not a guess.

## Certificate renewal (Phase 6a)

The `certbot` container renews automatically, but **a renewal only replaces the files on disk** - nginx
keeps the old certificate loaded in memory until reloaded. Add a weekly reload to cron (harmless even when
nothing actually renewed that week):
```
0 4 * * 0 cd /path/to/this/repo && docker compose -f docker-compose.yml -f docker-compose.prod.yml exec frontend nginx -s reload >> /var/log/sales-tracker-nginx-reload.log 2>&1
```

## All scheduled jobs, together

```
0 3 * * *   ./scripts/backup-db.sh                    # daily backup
0 4 * * 0   ... nginx -s reload                        # weekly, picks up any cert renewal
```
(the quarterly restore drill is deliberately not on cron - it's a "do this and actually watch it work" task,
not a fire-and-forget one)

## When this setup needs to change

Not before it actually needs to. Specifically:
- **Backend instances stop fitting on one server** → that's the point to add a second server, which means
  actual orchestration (nginx alone can't load-balance across hosts the way it does across containers on one
  host) - revisit then.
- **Zero-downtime deploys become a hard requirement** (not just "nice to have", which the rolling deploy
  above already covers reasonably well) → that's when a real deployment tool (blue/green, a proper
  orchestrator) earns its complexity.
- **Traffic gets spiky enough to need auto-scaling** → same answer - Kubernetes or similar, not before.

Until one of those is actually true, adding it speculatively is solving a problem this project doesn't have
yet, at the cost of real complexity it would carry every day in the meantime.
