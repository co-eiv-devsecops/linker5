#!/usr/bin/env bash

set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-linker.service}"
BRANCH="${BRANCH:-main}"
APP_DIR="${APP_DIR:-$HOME/linker5}"
MODULE_DIR="${MODULE_DIR:-$APP_DIR/linker5-java}"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://127.0.0.1:8080/}"
HEALTHCHECK_RETRIES="${HEALTHCHECK_RETRIES:-20}"
HEALTHCHECK_DELAY="${HEALTHCHECK_DELAY:-1}"

log() {
  printf '\n[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  log "ERROR: $*"
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

require_cmd git
require_cmd mvn
require_cmd curl
require_cmd systemctl
require_cmd sudo

[[ -d "$APP_DIR/.git" ]] || fail "Git repository not found at $APP_DIR"
[[ -f "$MODULE_DIR/pom.xml" ]] || fail "Maven module not found at $MODULE_DIR"

log "Deploying branch '$BRANCH' from $APP_DIR"

git -C "$APP_DIR" fetch origin
git -C "$APP_DIR" checkout "$BRANCH"
git -C "$APP_DIR" pull --ff-only origin "$BRANCH"

log "Building application jar"
mvn -f "$MODULE_DIR/pom.xml" clean package

JAR_PATH="$(ls -1 "$MODULE_DIR"/target/*-jar-with-dependencies.jar 2>/dev/null | head -n 1 || true)"
[[ -n "$JAR_PATH" ]] || fail "Built jar not found under $MODULE_DIR/target"

log "Built artifact: $JAR_PATH"

log "Restarting $SERVICE_NAME"
sudo systemctl restart "$SERVICE_NAME"
sudo systemctl is-active --quiet "$SERVICE_NAME" || {
  sudo systemctl status "$SERVICE_NAME" --no-pager || true
  sudo journalctl -u "$SERVICE_NAME" -n 100 --no-pager || true
  fail "$SERVICE_NAME failed to start"
}

log "Running healthcheck: $HEALTHCHECK_URL"

for ((attempt=1; attempt<=HEALTHCHECK_RETRIES; attempt++)); do
  if curl --fail --silent --show-error "$HEALTHCHECK_URL" >/dev/null; then
    log "Healthcheck passed on attempt $attempt"
    log "Deployment completed successfully"
    sudo systemctl status "$SERVICE_NAME" --no-pager
    exit 0
  fi

  log "Healthcheck attempt $attempt/$HEALTHCHECK_RETRIES failed; waiting ${HEALTHCHECK_DELAY}s"
  sleep "$HEALTHCHECK_DELAY"
done

sudo journalctl -u "$SERVICE_NAME" -n 100 --no-pager || true
fail "Healthcheck failed after $HEALTHCHECK_RETRIES attempts"

sudo systemctl status "$SERVICE_NAME" --no-pager
