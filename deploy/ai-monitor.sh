#!/usr/bin/env bash
set -u

LOG=/var/log/ai-tracking-monitor.log
ROOT=/www/wwwroot/ai-tracking
APP_PORT=48317
failed=0

mark() {
    echo "$(date -Is) $*" >> "$LOG"
}

code=$(curl -s -o /dev/null -w '%{http_code}' -m 10 http://127.0.0.1:8080/api/health || echo 000)
if [ "$code" != "200" ]; then
    mark "health check failed: http=$code"
    failed=1
fi

if ! systemctl is-active --quiet ai-tracking-backend; then
    mark "systemd service is not active"
    failed=1
fi

disk=$(df --output=pcent / | tail -1 | tr -dc '0-9')
if [ "${disk:-0}" -ge 85 ]; then
    mark "disk usage is ${disk}%"
    failed=1
fi

if command -v openssl >/dev/null 2>&1; then
    if ! echo | openssl s_client -connect "127.0.0.1:$APP_PORT" -servername 106.55.36.254 \
        -verify_quiet >/dev/null 2>&1; then
        mark "tls endpoint is not serving a trusted certificate"
        failed=1
    fi
    if ! echo | openssl s_client -connect "127.0.0.1:$APP_PORT" -servername 106.55.36.254 \
        2>/dev/null | openssl x509 -noout -checkend 172800 >/dev/null 2>&1; then
        mark "tls certificate expires within 2 days or cannot be read"
        failed=1
    fi
fi

if [ "$failed" -eq 1 ]; then
    webhook=$(grep -E '^ALERT_WEBHOOK_URL=' "$ROOT/backend/app.env" 2>/dev/null | cut -d= -f2-)
    if [ -n "${webhook:-}" ]; then
        curl -s -m 10 -H 'Content-Type: application/json' \
            -d "{\"text\":\"ai-tracking alert on $(hostname): see $LOG\"}" "$webhook" \
            >/dev/null 2>&1 || true
    fi
fi

exit "$failed"
