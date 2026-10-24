#!/usr/bin/env bash
# Kakascalendar Squad Server — one-shot installer for a Debian/Ubuntu box or LXC.
#
#   bash setup-lxc.sh server [--private]   install/refresh the server as a systemd service
#   bash setup-lxc.sh tunnel <hostname>     expose it through a Cloudflare Tunnel (e.g. kaka.example.com)
#   bash setup-lxc.sh update                pull the latest server.py and restart
#   bash setup-lxc.sh status                show service state, health and invite code
#
# --private  turns on an invite code (generated here) and makes everyone on the server friends.
set -euo pipefail

REPO_RAW="${REPO_RAW:-https://raw.githubusercontent.com/bxrninvxid/kakascalendar/main}"
APP_DIR=/opt/kakas
DATA_DIR=/var/lib/kakas
ENV_FILE=/etc/kakas/env
PORT="${PORT:-8000}"

c() { printf '\033[1;33m💩 %s\033[0m\n' "$*"; }
die() { printf '\033[1;31m✖ %s\033[0m\n' "$*" >&2; exit 1; }
[ "$(id -u)" = 0 ] || die "Run me as root"

fetch_server() {
  curl -fsSL "$REPO_RAW/server/server.py" -o "$APP_DIR/server.py.new"
  python3 -m py_compile "$APP_DIR/server.py.new"
  mv "$APP_DIR/server.py.new" "$APP_DIR/server.py"
}

health() { curl -fsS "http://127.0.0.1:$PORT/health" && echo; }

cmd_server() {
  local private=0
  [ "${1:-}" = "--private" ] && private=1
  c "Installing packages"
  apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq python3 curl ca-certificates openssl >/dev/null

  c "Creating service user and folders"
  id kakas >/dev/null 2>&1 || useradd --system --home "$DATA_DIR" --shell /usr/sbin/nologin kakas
  mkdir -p "$APP_DIR" "$DATA_DIR" /etc/kakas
  chown kakas:kakas "$DATA_DIR"

  c "Downloading server.py"
  fetch_server

  if [ ! -f "$ENV_FILE" ]; then
    c "Writing config"
    {
      echo "HOST=127.0.0.1"
      echo "PORT=$PORT"
      echo "KAKAS_DB=$DATA_DIR/kakas.db"
      if [ "$private" = 1 ]; then
        echo "KAKAS_INVITE_CODE=$(openssl rand -hex 12)"
        echo "KAKAS_AUTO_FRIENDS=1"
      fi
    } > "$ENV_FILE"
    chmod 640 "$ENV_FILE"; chgrp kakas "$ENV_FILE"
  fi

  c "Installing systemd service"
  cat > /etc/systemd/system/kakas.service <<EOF
[Unit]
Description=Kakascalendar Squad Server
After=network-online.target
Wants=network-online.target

[Service]
User=kakas
EnvironmentFile=$ENV_FILE
ExecStart=/usr/bin/python3 $APP_DIR/server.py
Restart=always
RestartSec=3
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$DATA_DIR
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
  systemctl daemon-reload
  systemctl enable --now kakas.service >/dev/null
  systemctl restart kakas.service
  sleep 2
  health || die "Server didn't answer. Check: journalctl -u kakas -n 50"
  cmd_status
}

cmd_tunnel() {
  local host="${1:-}"
  [ -n "$host" ] || die "Usage: setup-lxc.sh tunnel <hostname>"
  if ! command -v cloudflared >/dev/null; then
    c "Installing cloudflared"
    local arch; arch="$(dpkg --print-architecture)"
    curl -fsSL "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-$arch.deb" -o /tmp/cloudflared.deb
    dpkg -i /tmp/cloudflared.deb >/dev/null
    rm -f /tmp/cloudflared.deb
  fi
  if [ ! -f /root/.cloudflared/cert.pem ]; then
    c "Open the link below in a browser signed in to Cloudflare, pick your domain and click Authorize"
    cloudflared tunnel login
  fi
  if ! cloudflared tunnel list --name kakas --output json 2>/dev/null | grep -q '"id"'; then
    c "Creating tunnel 'kakas'"
    cloudflared tunnel create kakas
  fi
  local tid
  tid="$(cloudflared tunnel list --name kakas --output json | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["id"])')"
  c "Pointing $host at the tunnel"
  cloudflared tunnel route dns --overwrite-dns kakas "$host"

  mkdir -p /etc/cloudflared
  cp "/root/.cloudflared/$tid.json" /etc/cloudflared/
  cat > /etc/cloudflared/config.yml <<EOF
tunnel: $tid
credentials-file: /etc/cloudflared/$tid.json
ingress:
  - hostname: $host
    service: http://127.0.0.1:$PORT
  - service: http_status:404
EOF
  if [ ! -f /etc/systemd/system/cloudflared.service ]; then
    cloudflared service install
  fi
  systemctl restart cloudflared
  c "Waiting for the tunnel"
  for _ in $(seq 1 20); do
    if curl -fsS "https://$host/health" >/dev/null 2>&1; then
      curl -fsS "https://$host/health"; echo
      c "Live at https://$host 🎉"
      return
    fi
    sleep 3
  done
  echo "Tunnel is up, but DNS may need a minute. Try: curl https://$host/health"
}

cmd_update() {
  fetch_server
  systemctl restart kakas.service
  sleep 2
  health
}

cmd_status() {
  systemctl --no-pager --lines=0 status kakas.service || true
  health || true
  if grep -q '^KAKAS_INVITE_CODE=' "$ENV_FILE" 2>/dev/null; then
    c "Invite code: $(grep '^KAKAS_INVITE_CODE=' "$ENV_FILE" | cut -d= -f2)"
  fi
}

case "${1:-}" in
  server) shift; cmd_server "$@" ;;
  tunnel) shift; cmd_tunnel "$@" ;;
  update) cmd_update ;;
  status) cmd_status ;;
  *) sed -n '2,10p' "$0"; exit 1 ;;
esac
