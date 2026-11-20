set -euo pipefail
: "${BUDGET_DB_PASSWORD:?}"
work=target/https-proxy
mkdir -p "$work"
export BUDGET_DB_URL=jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1 BUDGET_DB_USER=budget_release
java -jar target/department-budget-0.1.0.war --spring.profiles.active=proxy > "$work/tomcat.log" 2>&1 &
server=$!
proxy=
cleanup() {
    if [[ -n "$proxy" ]]; then docker rm -fv "$proxy" > /dev/null; fi
    kill "$server" 2>/dev/null || true
    wait "$server" 2>/dev/null || true
}
trap cleanup EXIT
for attempt in {1..60}; do
    kill -0 "$server" || { tail -20 "$work/tomcat.log"; exit 1; }
    if curl -fsS http://127.0.0.1:8080/ > /dev/null 2>&1; then break; fi
    sleep 1
done
proxy=$(docker run -d --network host -e BUDGET_PUBLIC_HOST=localhost:18758 \
    -v "$PWD/ops/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2-alpine)
for attempt in {1..30}; do
    if docker cp "$proxy:/data/caddy/pki/authorities/local/root.crt" "$work/root.crt" 2>/dev/null; then break; fi
    sleep 1
done
export TEST_DEPLOYMENT_URL=https://localhost:18758 NODE_EXTRA_CA_CERTS="$PWD/$work/root.crt"
curl --cacert "$NODE_EXTRA_CA_CERTS" -fsS "$TEST_DEPLOYMENT_URL" > /dev/null
if ! command -v certutil >/dev/null; then sudo apt-get update -qq && sudo apt-get install -y libnss3-tools; fi
export HOME="$PWD/$work/browser-home"
for database in "$HOME/.pki/nssdb" "$HOME/.local/share/pki/nssdb"; do
    mkdir -p "$database"
    certutil -N -d "sql:$database" --empty-password
    certutil -A -d "sql:$database" -n "Budget CI Root" -t 'C,,' -i "$NODE_EXTRA_CA_CERTS"
done
cd frontend
node node_modules/@playwright/test/cli.js test https.spec.ts claims.spec.ts --reporter=list
