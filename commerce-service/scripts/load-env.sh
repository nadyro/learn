#!/usr/bin/env bash
# Sourced by the other scripts: loads the optional .env file (local port overrides, see .env.example), the same file
# Docker Compose and the "local" Spring profile read, so every component agrees on the ports.
ENV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi
