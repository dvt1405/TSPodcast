#!/usr/bin/env bash
# One-time (and re-runnable) setup of an Ubuntu 22.04/24.04 VPS for TSPodcast.
#
# Usage (as root, from a copy of the repo's deploy/ folder on the VPS):
#   sudo DEPLOY_PUBKEY="ssh-ed25519 AAAA... github-actions" \
#        DOMAIN=example.com ACME_EMAIL=you@example.com \
#        WEB_IMAGE=ghcr.io/<owner>/tspodcast-web \
#        bash ./scripts/bootstrap-vps.sh
#
# Settings (environment variables):
#   DEPLOY_PUBKEY / DEPLOY_PUBKEY_FILE  public key CI uses to log in as DEPLOY_USER (required
#                                       the first time; kept afterwards)
#   DEPLOY_USER       default "deploy"
#   ADMIN_USER        your own sudo user; default: the user who ran sudo. It must
#                     have ~/.ssh/authorized_keys, because password and root logins
#                     are switched off. Run as plain root (no sudo): set it, and
#                     root's keys are copied to it.
#   DOMAIN, ACME_EMAIL, WEB_IMAGE   optional, prefill /etc/tspodcast/compose.env
#   SWAP_SIZE         swap file size when RAM < 4 GB and no swap yet (default 2G)
#   SKIP_SSH_HARDENING=1   leave sshd untouched (not recommended)
#
# What it does (each step checks first, so running it again is safe):
#   apt upgrade + base packages; ADMIN_USER with sudo; DEPLOY_USER (groups docker,
#   tspodcast; no sudo); SSH: keys only, no root login; ufw: 22/tcp (rate
#   limited), 80/tcp, 443/tcp+udp; fail2ban (sshd); unattended-upgrades; Docker
#   Engine + compose plugin from Docker's apt repo, log rotation; swap; folders:
#     /opt/tspodcast        copy of deploy/ (owner DEPLOY_USER)
#     /etc/tspodcast        env files, root:tspodcast 750, files 640
#     /var/lib/tspodcast    deployed tags, locks (owner DEPLOY_USER)
#     /var/backups/tspodcast  Postgres dumps (owner DEPLOY_USER, 750)
#   systemd units for backups/crawl are installed but NOT enabled.
#
# Prints no secrets. Never deletes existing env files or keys.
set -euo pipefail

log() { printf '\n==> %s\n' "$*"; }
info() { printf '    %s\n' "$*"; }
die() {
  printf '\nERROR: %s\n' "$*" >&2
  exit 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  sed -n '2,34p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
fi

[[ $EUID -eq 0 ]] || die "run as root (sudo bash $0)"
# shellcheck disable=SC1091
. /etc/os-release
[[ "${ID:-}" == "ubuntu" ]] || die "this script supports Ubuntu only (found ${ID:-unknown})"

SRC_DIR="$(cd "$(dirname "$(readlink -f "$0")")/.." && pwd)"
[[ -f "$SRC_DIR/docker-compose.yml" ]] || die "run it from a copy of the repo's deploy/ folder ($SRC_DIR has no docker-compose.yml)"

DEPLOY_USER="${DEPLOY_USER:-deploy}"
ADMIN_USER="${ADMIN_USER:-${SUDO_USER:-}}"
SWAP_SIZE="${SWAP_SIZE:-2G}"
APP_DIR=/opt/tspodcast
ETC_DIR=/etc/tspodcast
STATE_DIR=/var/lib/tspodcast
BACKUP_DIR=/var/backups/tspodcast
export DEBIAN_FRONTEND=noninteractive
# Ubuntu's needrestart would otherwise open an interactive dialog after upgrades.
export NEEDRESTART_MODE=l

[[ "$DEPLOY_USER" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || die "invalid DEPLOY_USER"
[[ -z "$ADMIN_USER" || "$ADMIN_USER" =~ ^[a-z_][a-z0-9_-]{0,31}$ ]] || die "invalid ADMIN_USER"
[[ "$ADMIN_USER" != "root" ]] || die "ADMIN_USER must not be root (root login gets disabled); pick a name, e.g. ADMIN_USER=tuan"
[[ "$ADMIN_USER" != "$DEPLOY_USER" ]] || die "ADMIN_USER and DEPLOY_USER must differ (the deploy user has no sudo)"

# ---------------------------------------------------------------- packages
log "Updating packages"
apt-get update -q
apt-get -y -q -o Dpkg::Options::=--force-confold upgrade
apt-get install -y -q ca-certificates curl gnupg ufw fail2ban python3-systemd \
  unattended-upgrades apt-listchanges util-linux openssl

# ---------------------------------------------------------------- users
log "Groups and users"
getent group tspodcast >/dev/null || groupadd --system tspodcast
getent group docker >/dev/null || groupadd --system docker

if [[ -n "$ADMIN_USER" ]]; then
  if ! id "$ADMIN_USER" >/dev/null 2>&1; then
    useradd --create-home --shell /bin/bash "$ADMIN_USER"
    info "created admin user $ADMIN_USER (set a password for sudo: passwd $ADMIN_USER)"
  fi
  usermod -aG sudo "$ADMIN_USER"
  admin_home="$(getent passwd "$ADMIN_USER" | cut -d: -f6)"
  if [[ ! -s "$admin_home/.ssh/authorized_keys" && -s /root/.ssh/authorized_keys ]]; then
    install -d -m 700 -o "$ADMIN_USER" -g "$ADMIN_USER" "$admin_home/.ssh"
    install -m 600 -o "$ADMIN_USER" -g "$ADMIN_USER" /root/.ssh/authorized_keys "$admin_home/.ssh/authorized_keys"
    info "copied root's authorized_keys to $ADMIN_USER"
  fi
  if ! passwd -S "$ADMIN_USER" 2>/dev/null | awk '{ exit ($2 == "P") ? 0 : 1 }'; then
    info "NOTE: $ADMIN_USER has no password, so sudo will ask for one it cannot check."
    info "      Set it now in another session: sudo passwd $ADMIN_USER"
  fi
fi

if ! id "$DEPLOY_USER" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "$DEPLOY_USER"
  passwd -l "$DEPLOY_USER" >/dev/null
  info "created $DEPLOY_USER (password locked, SSH key only)"
fi
usermod -aG docker,tspodcast "$DEPLOY_USER"
[[ -z "$ADMIN_USER" ]] || usermod -aG docker,tspodcast "$ADMIN_USER"

deploy_home="$(getent passwd "$DEPLOY_USER" | cut -d: -f6)"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$deploy_home/.ssh"
auth="$deploy_home/.ssh/authorized_keys"
touch "$auth"
chown "$DEPLOY_USER:$DEPLOY_USER" "$auth"
chmod 600 "$auth"
pubkey="${DEPLOY_PUBKEY:-}"
if [[ -z "$pubkey" && -n "${DEPLOY_PUBKEY_FILE:-}" ]]; then
  pubkey="$(cat "$DEPLOY_PUBKEY_FILE")"
fi
if [[ -n "$pubkey" ]]; then
  [[ "$pubkey" =~ ^(ssh-ed25519|ecdsa-sha2-nistp256|ssh-rsa)\ [A-Za-z0-9+/=]+ ]] || die "DEPLOY_PUBKEY does not look like an OpenSSH public key"
  key_body="$(awk '{ print $2 }' <<<"$pubkey")"
  if ! grep -qF "$key_body" "$auth"; then
    # No forwarding, no X11: the key only runs commands (tar, deploy.sh).
    printf 'no-agent-forwarding,no-port-forwarding,no-X11-forwarding %s\n' "$pubkey" >>"$auth"
    info "added the CI public key for $DEPLOY_USER"
  else
    info "CI public key already present"
  fi
fi
[[ -s "$auth" ]] || die "$DEPLOY_USER has no SSH key: pass DEPLOY_PUBKEY='ssh-ed25519 ...'"

# ---------------------------------------------------------------- SSH
if [[ "${SKIP_SSH_HARDENING:-0}" != "1" ]]; then
  log "SSH hardening"
  login_ok=0
  for u in $ADMIN_USER; do
    h="$(getent passwd "$u" | cut -d: -f6)"
    [[ -s "$h/.ssh/authorized_keys" ]] && login_ok=1
  done
  if [[ "$login_ok" != "1" ]]; then
    die "no admin user with an SSH key (ADMIN_USER='${ADMIN_USER}'): disabling root/password login now would lock you out. Set ADMIN_USER or add a key first."
  fi
  # 01- sorts before the 50-cloud-init.conf some providers ship; in sshd the
  # first value read wins.
  cat >/etc/ssh/sshd_config.d/01-tspodcast-hardening.conf <<'EOF'
# Managed by deploy/scripts/bootstrap-vps.sh
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
MaxAuthTries 3
LoginGraceTime 30
X11Forwarding no
AllowAgentForwarding no
EOF
  chmod 644 /etc/ssh/sshd_config.d/01-tspodcast-hardening.conf
  sshd -t || die "sshd config test failed; fix /etc/ssh/sshd_config.d/01-tspodcast-hardening.conf"
  systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || systemctl restart ssh
  info "effective: $(sshd -T 2>/dev/null | grep -E '^(permitrootlogin|passwordauthentication) ' | tr '\n' ' ')"
  info "KEEP THIS SESSION OPEN and test a new login as $ADMIN_USER in another terminal."
fi

# ---------------------------------------------------------------- firewall
log "Firewall (ufw)"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw limit 22/tcp comment 'SSH' >/dev/null
ufw allow 80/tcp comment 'HTTP (ACME + redirect)' >/dev/null
ufw allow 443/tcp comment 'HTTPS' >/dev/null
ufw allow 443/udp comment 'HTTP/3' >/dev/null
ufw --force enable >/dev/null
ufw status verbose | sed 's/^/    /'
info "Note: ports published by Docker bypass ufw. The stack publishes only 80/443 and 127.0.0.1:3000."

# ---------------------------------------------------------------- fail2ban
log "fail2ban"
cat >/etc/fail2ban/jail.d/tspodcast.local <<'EOF'
# Managed by deploy/scripts/bootstrap-vps.sh
[DEFAULT]
backend = systemd
bantime = 1h
findtime = 10m
maxretry = 5

[sshd]
enabled = true
EOF
systemctl enable --now fail2ban >/dev/null
systemctl restart fail2ban

# ---------------------------------------------------------------- unattended-upgrades
log "Automatic security updates"
cat >/etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
APT::Periodic::AutocleanInterval "7";
EOF
cat >/etc/apt/apt.conf.d/52tspodcast-unattended <<'EOF'
// Managed by deploy/scripts/bootstrap-vps.sh. Security updates only (Ubuntu default
// origins); no automatic reboot: reboot by hand when /var/run/reboot-required exists.
Unattended-Upgrade::Remove-Unused-Dependencies "true";
Unattended-Upgrade::Automatic-Reboot "false";
EOF
systemctl enable --now unattended-upgrades >/dev/null

# ---------------------------------------------------------------- Docker
log "Docker Engine + compose plugin"
if ! command -v docker >/dev/null || ! docker compose version >/dev/null 2>&1; then
  for pkg in docker.io docker-doc docker-compose docker-compose-v2 podman-docker containerd runc; do
    if dpkg -s "$pkg" >/dev/null 2>&1; then apt-get remove -y -q "$pkg"; fi
  done
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  codename="${UBUNTU_CODENAME:-$VERSION_CODENAME}"
  printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
    "$(dpkg --print-architecture)" "$codename" >/etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  info "already installed: $(docker --version)"
fi
if [[ ! -e /etc/docker/daemon.json ]]; then
  install -d -m 755 /etc/docker
  cat >/etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "5" },
  "live-restore": true,
  "no-new-privileges": true
}
EOF
  systemctl restart docker
  info "wrote /etc/docker/daemon.json (log rotation, live-restore)"
else
  info "/etc/docker/daemon.json exists, left as is"
fi
systemctl enable --now docker >/dev/null
info "$(docker --version); $(docker compose version)"

# ---------------------------------------------------------------- swap
log "Swap"
mem_kb="$(awk '/^MemTotal:/ { print $2 }' /proc/meminfo)"
if [[ -n "$(swapon --show --noheadings)" ]]; then
  info "swap already on: $(swapon --show --noheadings | awk '{ print $1, $3 }' | tr '\n' ' ')"
elif ((mem_kb < 4 * 1024 * 1024)); then
  if [[ ! -e /swapfile ]]; then
    fallocate -l "$SWAP_SIZE" /swapfile || dd if=/dev/zero of=/swapfile bs=1M count="$(numfmt --from=iec "$SWAP_SIZE" | awk '{ print int($1 / 1048576) }')"
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
  fi
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
  info "added $SWAP_SIZE swap (RAM $((mem_kb / 1024)) MB)"
else
  info "RAM $((mem_kb / 1024)) MB >= 4 GB, no swap added"
fi
printf 'vm.swappiness = 10\n' >/etc/sysctl.d/60-tspodcast.conf
sysctl -q -p /etc/sysctl.d/60-tspodcast.conf

# ---------------------------------------------------------------- folders
log "Folders"
install -d -m 755 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR"
install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$STATE_DIR"
install -d -m 750 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$BACKUP_DIR" "$BACKUP_DIR/postgres"
install -d -m 750 -o root -g tspodcast "$ETC_DIR"
install -d -m 755 -o root -g root "$ETC_DIR/caddy"

if [[ "$SRC_DIR" != "$APP_DIR" ]]; then
  # Copy deploy/ (no env files with values live there, only *.example).
  tar -C "$SRC_DIR" -cf - . | tar -C "$APP_DIR" -xf - --no-same-owner
  chown -R "$DEPLOY_USER:$DEPLOY_USER" "$APP_DIR"
  info "copied $SRC_DIR to $APP_DIR"
fi
chmod 755 "$APP_DIR"/scripts/*.sh "$APP_DIR"/scripts/healthcheck/*.sh

# Env files: create from the templates once, never overwrite.
for name in compose web backend; do
  dst="$ETC_DIR/$name.env"
  if [[ ! -e "$dst" ]]; then
    install -m 640 -o root -g tspodcast "$APP_DIR/env/$name.env.example" "$dst"
    info "created $dst from the template (fill it in)"
  else
    chown root:tspodcast "$dst"
    chmod 640 "$dst"
    info "$dst exists, permissions reset to root:tspodcast 640"
  fi
done
set_compose_value() {
  local key="$1" value="$2" file="$ETC_DIR/compose.env"
  [[ -n "$value" ]] || return 0
  [[ "$value" =~ ^[A-Za-z0-9@._:/-]+$ ]] || die "unexpected characters in $key"
  if grep -qE "^${key}=$" "$file"; then
    sed -i "s|^${key}=$|${key}=${value}|" "$file"
    info "compose.env: $key set"
  fi
}
set_compose_value DOMAIN "${DOMAIN:-}"
set_compose_value ACME_EMAIL "${ACME_EMAIL:-}"
set_compose_value WEB_IMAGE "${WEB_IMAGE:-}"

# ---------------------------------------------------------------- systemd units
log "systemd units (installed, not enabled)"
for unit in "$APP_DIR"/systemd/*.service "$APP_DIR"/systemd/*.timer; do
  sed "s/^User=deploy$/User=$DEPLOY_USER/; s/^Group=deploy$/Group=$DEPLOY_USER/" "$unit" \
    >"/etc/systemd/system/$(basename "$unit")"
done
systemctl daemon-reload
info "enable backups once the backend runs: systemctl enable --now tspodcast-backup.timer"

# ---------------------------------------------------------------- summary
log "Done"
cat <<EOF
    Next steps (docs/deploy/README.md §3-§5):
      1. sudoedit $ETC_DIR/compose.env   (DOMAIN, ACME_EMAIL, WEB_IMAGE)
      2. sudoedit $ETC_DIR/web.env       (PODCASTINDEX_*, optional ANTHROPIC_API_KEY, ...)
      3. as $DEPLOY_USER: docker login ghcr.io   (or let CI do it)
      4. first deploy: GitHub Actions "Deploy web (VPS)", or $APP_DIR/scripts/deploy.sh <tag>
    Group changes apply to new logins only: log out and back in before using docker.
EOF
if [[ -e /var/run/reboot-required ]]; then
  echo "    A reboot is pending from package updates: sudo reboot (after testing SSH)."
fi
