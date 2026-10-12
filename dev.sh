#!/usr/bin/env bash
# Starts Postgres, Keycloak, the API (dev profile), and the web dev server. Ctrl+C stops the API
# and web server; the containers keep running (stop them with `docker compose down`).
set -euo pipefail
cd "$(dirname "$0")"

for port in 8080 5173; do
  if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then
    echo "Port $port is already in use; stop the process holding it and retry." >&2
    exit 1
  fi
done

docker compose up -d --wait

# In the dev container, compose ports are published on the Docker host, not on this container's
# loopback. The browser still uses localhost, so the token issuer is unchanged.
if [ -f /.dockerenv ]; then
  export DB_HOST="${DB_HOST:-host.docker.internal}"
  export KEYCLOAK_URL="${KEYCLOAK_URL:-http://host.docker.internal:8081}"
fi

[ -d web/node_modules ] || npm --prefix web install

# Kills both dev servers, including Gradle's and npm's child processes, on exit.
trap 'kill 0' EXIT

(cd api && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun) &
npm --prefix web run dev &

wait
