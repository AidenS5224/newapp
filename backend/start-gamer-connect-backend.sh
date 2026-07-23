#!/usr/bin/env sh
set -eu
export GAMER_CONNECT_HOST="${GAMER_CONNECT_HOST:-0.0.0.0}"
export GAMER_CONNECT_PORT="${GAMER_CONNECT_PORT:-8080}"
python3 "$(dirname "$0")/app.py"
