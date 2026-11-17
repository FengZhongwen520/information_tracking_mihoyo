<#
.SYNOPSIS
  AI-facing deployment script for the AI Information Auto-Tracking app.

.DESCRIPTION
  Read this header before running. The script is intentionally explicit so an
  agent can understand the whole deployment path without guessing.

  Target server facts (current production):
    host           106.55.36.254
    ssh user       ubuntu
    ssh key        ~/.ssh/id_ai_tracking2
    panel          BaoTa (BT) + Nginx
    BT site        106.55.36.254_80 (site id 2)
    web root       /www/wwwroot/ai-tracking/frontend
    backend root   /www/wwwroot/ai-tracking/backend
    systemd unit   ai-tracking-backend
    backend bind   127.0.0.1:8080
    app port       48317 (HTTPS, public IP)
    app URL        https://106.55.36.254:48317/
    port 80        ACME challenge only; do not serve the app there
                   webroot /var/www/acme (BT vhost app include is disabled)
    app snippet    /www/server/panel/vhost/nginx/snippets/ai-tracking-app.conf
                   (kept outside the BT extension dir so the panel cannot
                    re-inject app routes into the 80 vhost)
    TLS            Let's Encrypt IP certificate for 106.55.36.254
                   (shortlived profile, 6-day validity, snap certbot)
    domain         metahubgo.cn / www.metahubgo.cn (blocked until ICP filing)

  Important production constraint:
    The domain is currently intercepted by Tencent Cloud's ICP filing block.
    Server-side health checks can still pass, but public requests to the domain
    may return 302 to https://dnspod.qcloud.com/static/webblock.html until the
    domain completes ICP filing / access filing. Do not "fix" this by changing
    Nginx; it is a compliance block. IP access remains available.

  Deployment flow:
    1. Build frontend and backend locally.
    2. Upload versioned tarballs to /tmp/ai-deploy-<stamp> on the server.
    3. Back up the current frontend and jar.
    4. Replace frontend files and backend jar.
    5. Restart systemd, reload BT Nginx, run health checks.
    6. Roll back the previous jar/frontend if the health check fails.

  Safe to rerun: yes. Existing app.env, systemd unit and Nginx extension are
  only created when missing; secrets are not rotated by this script.

  Example:
    pwsh -NoProfile -File .\deploy\ai-deploy.ps1
    pwsh -NoProfile -File .\deploy\ai-deploy.ps1 -SkipBuild

  Secrets:
    No password is stored here. SSH key authentication is required. Do not put
    the server password or SSH private key into the repository.
#>

[CmdletBinding()]
param(
    [string]$Server = "106.55.36.254",
    [string]$User = "ubuntu",
    [string]$KeyPath = "$HOME\.ssh\id_ai_tracking2",
    [string]$RemoteRoot = "/www/wwwroot/ai-tracking",
    [string]$ServiceName = "ai-tracking-backend",
    [string]$SiteConfName = "106.55.36.254_80",
    [string]$Domain = "metahubgo.cn",
    [string]$JarName = "ai-tracking-backend-0.1.0.jar",
    [int]$AppPort = 48317,
    [string]$CertName = "106.55.36.254",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$staging = Join-Path $env:TEMP "ai-deploy-$stamp"
$remoteTmp = "/tmp/ai-deploy-$stamp"
$remoteTarget = "$User@$Server"

function Assert-LastExit {
    param([string]$Step)
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE"
    }
}

function Write-Step {
    param([string]$Message)
    Write-Host "[deploy] $Message" -ForegroundColor Cyan
}

if (-not (Test-Path (Join-Path $repoRoot "frontend/package.json"))) {
    throw "Cannot find frontend/package.json. Run this script from the repository."
}
if (-not (Test-Path $KeyPath)) {
    throw "SSH key not found: $KeyPath"
}

New-Item -ItemType Directory -Path $staging -Force | Out-Null

try {
    Write-Step "Checking SSH connectivity"
    & ssh -i $KeyPath -o BatchMode=yes -o ConnectTimeout=10 $remoteTarget "echo SSH_OK"
    Assert-LastExit "SSH connectivity check"

    if (-not $SkipBuild) {
        Write-Step "Building frontend"
        Push-Location (Join-Path $repoRoot "frontend")
        try {
            if (-not (Test-Path "node_modules")) {
                & npm ci
                Assert-LastExit "npm ci"
            }
            & npm run build
            Assert-LastExit "frontend build"
        } finally {
            Pop-Location
        }

        Write-Step "Building backend"
        Push-Location (Join-Path $repoRoot "backend")
        try {
            & mvn -q -DskipTests package
            Assert-LastExit "backend build"
        } finally {
            Pop-Location
        }
    } else {
        Write-Step "Skipping builds; reusing existing artifacts"
    }

    $frontendDist = Join-Path $repoRoot "frontend/dist"
    $jarPath = Join-Path $repoRoot "backend/target/$JarName"
    if (-not (Test-Path (Join-Path $frontendDist "index.html"))) {
        throw "Frontend build output missing: $frontendDist/index.html"
    }
    if (-not (Test-Path $jarPath)) {
        throw "Backend build output missing: $jarPath"
    }

    Write-Step "Packing artifacts"
    $frontendTar = Join-Path $staging "frontend.tar.gz"
    $backendTar = Join-Path $staging "backend.tar.gz"
    $monitorScriptPath = Join-Path $repoRoot "deploy/ai-monitor.sh"
    if (-not (Test-Path $monitorScriptPath)) {
        throw "Monitor script missing: $monitorScriptPath"
    }
    & tar -czf $frontendTar -C $frontendDist .
    Assert-LastExit "frontend packaging"
    & tar -czf $backendTar -C (Split-Path -Parent $jarPath) $JarName
    Assert-LastExit "backend packaging"

    $remoteScriptPath = Join-Path $staging "remote-deploy.sh"
    $remoteScript = @'
#!/usr/bin/env bash
set -euo pipefail

STAMP="$1"
ROOT="$2"
SERVICE="$3"
SITE_CONF="$4"
DOMAIN="$5"
JAR_NAME="$6"
PUBLIC_IP="$7"
APP_PORT="$8"
CERT_NAME="$9"

TMP="/tmp/ai-deploy-$STAMP"
LIVE_FRONTEND="$ROOT/frontend"
LIVE_BACKEND="$ROOT/backend"
BACKUP_ROOT="$ROOT/backups"
BACKUP="$BACKUP_ROOT/$STAMP"
NGINX_EXT="/www/server/panel/vhost/nginx/extension/$SITE_CONF"
NGINX_VHOST="/www/server/panel/vhost/nginx/$SITE_CONF.conf"
APP_CONF="/www/server/panel/vhost/nginx/snippets/ai-tracking-app.conf"
RATE_CONF="/www/server/panel/vhost/nginx/00-ai-tracking-ratelimit.conf"
PORT_CONF="/www/server/panel/vhost/nginx/ai-tracking-port.conf"
sudo rm -f /www/server/panel/vhost/nginx/ai-tracking-ratelimit.conf

echo "[remote] stamp=$STAMP"
echo "[remote] root=$ROOT"

if [ ! -f "$TMP/frontend.tar.gz" ] || [ ! -f "$TMP/backend.tar.gz" ]; then
    echo "[remote] uploaded artifacts are missing"
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    echo "[remote] installing OpenJDK 17"
    sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jre-headless
fi

sudo mkdir -p "$LIVE_FRONTEND" "$LIVE_BACKEND" "$LIVE_BACKEND/data" "$LIVE_BACKEND/logs" "$BACKUP_ROOT" "$BACKUP" "$BACKUP_ROOT/data"

if [ ! -f "$LIVE_BACKEND/app.env" ]; then
    echo "[remote] creating app.env"
    SECRET=$(openssl rand -base64 48 | tr -d '\n')
    sudo tee "$LIVE_BACKEND/app.env" >/dev/null <<EOF
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
AUTH_SECRET=$SECRET
AUTH_RSA_KEY_PATH=$LIVE_BACKEND/data/auth-rsa-private.key
CORS_ALLOWED_ORIGINS=http://$DOMAIN,https://$DOMAIN,http://www.$DOMAIN,https://www.$DOMAIN,http://$PUBLIC_IP,https://$PUBLIC_IP:$APP_PORT
HOYOVERSE_SYNC_ENABLED=true
HOYOVERSE_BASE_URL=https://api.ennead.cc/mihoyo
HOYOVERSE_LOCALE=zh-cn
HOYOVERSE_SYNC_INTERVAL_MS=1800000
H2_CONSOLE_ENABLED=false
IMAGE_CACHE_MAX_BYTES=2147483648
IMAGE_CACHE_MAX_AGE_DAYS=30
BACKUP_ENABLED=true
BACKUP_DIR=$BACKUP_ROOT/data
BACKUP_RETENTION_DAYS=30
EOF
    unset SECRET
    sudo chmod 600 "$LIVE_BACKEND/app.env"
fi

ensure_env() {
    local key="$1"
    local value="$2"
    if ! sudo grep -q "^${key}=" "$LIVE_BACKEND/app.env"; then
        echo "$key=$value" | sudo tee -a "$LIVE_BACKEND/app.env" >/dev/null
    fi
}

ensure_env H2_CONSOLE_ENABLED false
ensure_env IMAGE_CACHE_MAX_BYTES 2147483648
ensure_env IMAGE_CACHE_MAX_AGE_DAYS 30
ensure_env BACKUP_ENABLED true
ensure_env BACKUP_DIR "$BACKUP_ROOT/data"
ensure_env BACKUP_RETENTION_DAYS 30
sudo chown -R www:www "$BACKUP_ROOT/data"
sudo chmod 700 "$BACKUP_ROOT/data"

if ! sudo grep -q "https://$PUBLIC_IP:$APP_PORT" "$LIVE_BACKEND/app.env"; then
    echo "[remote] adding app origin to CORS_ALLOWED_ORIGINS"
    sudo sed -i "s#^CORS_ALLOWED_ORIGINS=\(.*\)#CORS_ALLOWED_ORIGINS=\1,https://$PUBLIC_IP:$APP_PORT#" "$LIVE_BACKEND/app.env"
fi

if [ ! -f "/etc/systemd/system/$SERVICE.service" ]; then
    echo "[remote] creating systemd unit"
    sudo tee "/etc/systemd/system/$SERVICE.service" >/dev/null <<EOF
[Unit]
Description=AI Information Auto Tracking Backend
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=www
Group=www
WorkingDirectory=$LIVE_BACKEND
EnvironmentFile=$LIVE_BACKEND/app.env
ExecStart=/usr/bin/java -Xms256m -Xmx768m -jar $LIVE_BACKEND/$JAR_NAME
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
LimitNOFILE=65535
StandardOutput=append:$LIVE_BACKEND/logs/backend.out.log
StandardError=append:$LIVE_BACKEND/logs/backend.err.log

[Install]
WantedBy=multi-user.target
EOF
    sudo systemctl daemon-reload
fi

if [ -f "$NGINX_EXT/app.conf" ]; then
    echo "[remote] moving legacy BT extension app.conf out of the 80 vhost"
    sudo mv "$NGINX_EXT/app.conf" "$BACKUP_ROOT/app.conf.extension.disabled"
fi

echo "[remote] backing up Nginx configuration"
sudo cp -a "$APP_CONF" "$TMP/app.conf.prev" 2>/dev/null || true
sudo cp -a "$RATE_CONF" "$TMP/ratelimit.conf.prev" 2>/dev/null || true
sudo cp -a "$PORT_CONF" "$TMP/port.conf.prev" 2>/dev/null || true

echo "[remote] writing Nginx API/SPA snippet and security headers"
sudo mkdir -p "$(dirname "$APP_CONF")"
sudo tee "$APP_CONF" >/dev/null <<'EOF'
add_header Strict-Transport-Security "max-age=31536000" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://static.wikia.nocookie.net https://*.hoyoverse.com https://*.hoyolab.com; connect-src 'self'; font-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; worker-src 'self'; manifest-src 'self'" always;

location ^~ /api/auth/ {
    limit_req zone=ai_auth burst=8 nodelay;
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 10s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}

location ^~ /api/images {
    limit_req zone=ai_images burst=120 nodelay;
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 10s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}

location ^~ /api/ {
    limit_req zone=ai_api burst=120 nodelay;
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 10s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}

location = /index.html {
    expires -1;
    try_files $uri =404;
}

location = /sw.js {
    expires -1;
    try_files $uri =404;
}

location /assets/ {
    expires 1y;
    try_files $uri =404;
}

location / {
    try_files $uri $uri/ /index.html;
}
EOF

echo "[remote] writing Nginx rate-limit zones"
sudo tee "$RATE_CONF" >/dev/null <<'EOF'
limit_req_zone $binary_remote_addr zone=ai_auth:10m rate=20r/m;
limit_req_zone $binary_remote_addr zone=ai_images:10m rate=600r/m;
limit_req_zone $binary_remote_addr zone=ai_api:10m rate=600r/m;
limit_req_status 429;
EOF

if [ ! -f "/etc/letsencrypt/live/$CERT_NAME/fullchain.pem" ]; then
    echo "[remote] ERROR: TLS certificate missing for $CERT_NAME"
    echo "[remote] issue it with: certbot certonly --webroot -w $LIVE_FRONTEND --ip-address $PUBLIC_IP --preferred-profile shortlived"
    exit 1
fi

echo "[remote] writing high-port HTTPS vhost on $APP_PORT"
sudo tee "$PORT_CONF" >/dev/null <<EOF
server {
    listen $APP_PORT ssl;
    server_name $PUBLIC_IP;
    server_tokens off;

    ssl_certificate     /etc/letsencrypt/live/$CERT_NAME/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$CERT_NAME/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:AI_TRACKING_TLS:10m;
    ssl_session_timeout 10m;

    root $LIVE_FRONTEND;
    index index.html;

    include $APP_CONF;
}
EOF

if ! sudo nginx -t; then
    echo "[remote] Nginx configuration is invalid; restoring previous files"
    [ -f "$TMP/app.conf.prev" ] && sudo cp -a "$TMP/app.conf.prev" "$APP_CONF"
    [ -f "$TMP/ratelimit.conf.prev" ] && sudo cp -a "$TMP/ratelimit.conf.prev" "$RATE_CONF"
    [ -f "$TMP/port.conf.prev" ] && sudo cp -a "$TMP/port.conf.prev" "$PORT_CONF"
    exit 1
fi

echo "[remote] backing up current release"
if [ -d "$LIVE_FRONTEND" ]; then
    sudo tar -czf "$BACKUP/frontend.tar.gz" --exclude='.user.ini' -C "$LIVE_FRONTEND" .
fi
if [ -f "$LIVE_BACKEND/$JAR_NAME" ]; then
    sudo cp -a "$LIVE_BACKEND/$JAR_NAME" "$BACKUP/$JAR_NAME"
fi

echo "[remote] deploying frontend"
sudo rm -rf "$TMP/frontend"
sudo mkdir -p "$TMP/frontend"
sudo tar -xzf "$TMP/frontend.tar.gz" -C "$TMP/frontend"
sudo rsync -a --delete --exclude='.user.ini' "$TMP/frontend/" "$LIVE_FRONTEND/"
sudo find "$LIVE_FRONTEND" -not -name '.user.ini' -exec chown www:www {} +
sudo find "$LIVE_FRONTEND" -type d -exec chmod 755 {} +
sudo find "$LIVE_FRONTEND" -type f -not -name '.user.ini' -exec chmod 644 {} +

echo "[remote] deploying backend jar"
sudo rm -rf "$TMP/backend"
sudo mkdir -p "$TMP/backend"
sudo tar -xzf "$TMP/backend.tar.gz" -C "$TMP/backend"
sudo install -o www -g www -m 644 "$TMP/backend/$JAR_NAME" "$LIVE_BACKEND/$JAR_NAME.new"
sudo mv -f "$LIVE_BACKEND/$JAR_NAME.new" "$LIVE_BACKEND/$JAR_NAME"
sudo chown -R www:www "$LIVE_BACKEND"
sudo chmod 700 "$LIVE_BACKEND/data"
sudo find "$LIVE_BACKEND/data" -maxdepth 1 -type f -name '*.key' -exec chmod 600 {} +
sudo find "$LIVE_BACKEND/data" -maxdepth 1 -type f -name 'tracker*' -exec chmod 600 {} +
sudo chmod 600 "$LIVE_BACKEND/app.env"

echo "[remote] restarting service"
sudo systemctl enable "$SERVICE" >/dev/null 2>&1 || true
sudo systemctl restart "$SERVICE"

healthy=0
for _ in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/health || true)
    if [ "$code" = "200" ]; then
        healthy=1
        break
    fi
    sleep 1
done

if [ "$healthy" != "1" ]; then
    echo "[remote] health check failed; rolling back"
    if [ -f "$BACKUP/$JAR_NAME" ]; then
        sudo cp -a "$BACKUP/$JAR_NAME" "$LIVE_BACKEND/$JAR_NAME"
    fi
    if [ -f "$BACKUP/frontend.tar.gz" ]; then
        sudo rm -rf "$TMP/rollback"
        sudo mkdir -p "$TMP/rollback"
        sudo tar -xzf "$BACKUP/frontend.tar.gz" -C "$TMP/rollback"
        sudo rsync -a --delete --exclude='.user.ini' "$TMP/rollback/" "$LIVE_FRONTEND/"
        sudo chown -R www:www "$LIVE_FRONTEND" 2>/dev/null || true
    fi
    sudo systemctl restart "$SERVICE" || true
    exit 1
fi

if [ -f /etc/init.d/nginx ]; then
    sudo nginx -t
    sudo /etc/init.d/nginx reload
else
    sudo nginx -t
    sudo nginx -s reload
fi

echo "[remote] installing log rotation"
sudo tee /etc/logrotate.d/ai-tracking >/dev/null <<EOF
$LIVE_BACKEND/logs/*.log {
    daily
    rotate 14
    missingok
    notifempty
    compress
    delaycompress
    copytruncate
    su www www
}
EOF

if [ -f "$TMP/ai-monitor.sh" ]; then
    echo "[remote] installing local health monitor"
    sudo install -o root -g root -m 755 "$TMP/ai-monitor.sh" /usr/local/bin/ai-tracking-monitor
    sudo tee /etc/cron.d/ai-tracking-monitor >/dev/null <<'EOF'
*/5 * * * * root /usr/local/bin/ai-tracking-monitor >/dev/null 2>&1
EOF
    sudo chmod 644 /etc/cron.d/ai-tracking-monitor
fi

page_code=$(curl -sk -o /dev/null -w '%{http_code}' "https://127.0.0.1:$APP_PORT/" || true)
api_code=$(curl -sk -o /dev/null -w '%{http_code}' "https://127.0.0.1:$APP_PORT/api/health" || true)
echo "[remote] verify page=$page_code api=$api_code"
if [ "$page_code" != "200" ] || [ "$api_code" != "200" ]; then
    echo "[remote] verification failed"
    exit 1
fi

if ! sudo grep -q "$DOMAIN" "$NGINX_VHOST"; then
    echo "[remote] WARN: domain is not bound in $NGINX_VHOST"
fi

jar_sha=$(sha256sum "$LIVE_BACKEND/$JAR_NAME" | awk '{print $1}')
frontend_sha=$(sha256sum "$LIVE_FRONTEND/index.html" | awk '{print $1}')
echo "$STAMP frontend=ok frontend_sha256=$frontend_sha jar_sha256=$jar_sha" | sudo tee -a "$ROOT/RELEASES.log" >/dev/null

echo "[remote] pruning old backups (keep 5)"
sudo find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' \
    | sort -nr | tail -n +6 | awk '{print $2}' | xargs -r sudo rm -rf

sudo rm -rf "$TMP"
echo "[remote] DEPLOY_OK"
'@

    $remoteScript = $remoteScript.Replace("`r`n", "`n")
    [IO.File]::WriteAllText($remoteScriptPath, $remoteScript, (New-Object System.Text.UTF8Encoding($false)))

    Write-Step "Uploading artifacts to $remoteTmp"
    & ssh -i $KeyPath -o BatchMode=yes $remoteTarget "mkdir -p '$remoteTmp'"
    Assert-LastExit "create remote staging directory"
    & scp -i $KeyPath -o BatchMode=yes $frontendTar $backendTar $remoteScriptPath $monitorScriptPath "${remoteTarget}:$remoteTmp/"
    Assert-LastExit "artifact upload"

    Write-Step "Running remote deployment"
    & ssh -i $KeyPath -o BatchMode=yes $remoteTarget "sudo bash '$remoteTmp/remote-deploy.sh' '$stamp' '$RemoteRoot' '$ServiceName' '$SiteConfName' '$Domain' '$JarName' '$Server' '$AppPort' '$CertName'"
    Assert-LastExit "remote deployment"

    Write-Step "Final checks"
    & ssh -i $KeyPath -o BatchMode=yes $remoteTarget "sudo systemctl is-active '$ServiceName'; curl -s -o /dev/null -w 'api=%{http_code}\n' http://127.0.0.1:8080/api/health; curl -sk -o /dev/null -w 'page=%{http_code}\n' https://127.0.0.1:$AppPort/"
    Assert-LastExit "final status check"

    Write-Host ""
    Write-Host "DEPLOY_OK" -ForegroundColor Green
    Write-Host "Release: $stamp"
    Write-Host "Server:  $Server"
    Write-Host "IP URL:  https://${Server}:$AppPort/"
    Write-Host "Domain:  http://$Domain/ (may be blocked until ICP filing is complete)"
    Write-Host "Logs:    $RemoteRoot/backend/logs/backend.out.log"
}
finally {
    if (Test-Path $staging) {
        Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
    }
}
