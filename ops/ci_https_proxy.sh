set -euo pipefail
: "${BUDGET_DB_PASSWORD:?}"
work=target/https-proxy
mkdir -p "$work"
proxy=
mysql=
server=
php_server=
cleanup() {
    if [[ -n "$proxy" ]]; then docker rm -fv "$proxy" > /dev/null; fi
    if [[ -n "$mysql" ]]; then docker rm -fv "$mysql" > /dev/null; fi
    for pid in "$server" "$php_server"; do
        if [[ -n "$pid" ]]; then kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi
    done
}
trap cleanup EXIT
export MYSQL_PASSWORD=$(openssl rand -hex 24)
export MYSQL_DATABASE=budget MYSQL_USER=receiver
mysql=$(docker run -d -p 127.0.0.1:13306:3306 -e MYSQL_RANDOM_ROOT_PASSWORD=yes \
    -e MYSQL_DATABASE -e MYSQL_USER -e MYSQL_PASSWORD \
    -v "$PWD/portal/mysql/V001__projection.sql:/docker-entrypoint-initdb.d/projection.sql:ro" mysql:8.4)
mysql_query() {
    docker exec -e MYSQL_PWD="$MYSQL_PASSWORD" "$mysql" mysql --protocol=TCP -h127.0.0.1 \
        -u "$MYSQL_USER" -N -B "$MYSQL_DATABASE" -e "$1"
}
for attempt in {1..90}; do
    if mysql_query 'SELECT COUNT(*) FROM budget_event_receipt' >/dev/null 2>&1; then break; fi
    sleep 1
done
mysql_query 'SELECT COUNT(*) FROM budget_event_receipt' >/dev/null
export BUDGET_PORTAL_TOKEN=$(openssl rand -hex 32)
export PORTAL_MYSQL_DSN='mysql:host=127.0.0.1;port=13306;dbname=budget'
export PORTAL_MYSQL_USER="$MYSQL_USER" PORTAL_MYSQL_PASSWORD="$MYSQL_PASSWORD"
mkdir -p "$work/portal"
cp portal/receive.php portal/status.php "$work/portal/"
export TEST_PORTAL_RECEIVER="$PWD/$work/portal/receive.php"
php -S 127.0.0.1:18760 -t "$work/portal" > "$work/php.log" 2>&1 &
php_server=$!
cp ops/Caddyfile "$work/Caddyfile"
printf '\n127.0.0.1:18759 {\n reverse_proxy 127.0.0.1:18760\n}\n' >> "$work/Caddyfile"
proxy=$(docker run -d --network host -e BUDGET_PUBLIC_HOST=localhost:18758 \
    -v "$PWD/$work/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2-alpine)
for attempt in {1..30}; do
    if docker cp "$proxy:/data/caddy/pki/authorities/local/root.crt" "$work/root.crt" 2>/dev/null; then break; fi
    sleep 1
done
export TEST_DEPLOYMENT_URL=https://localhost:18758 NODE_EXTRA_CA_CERTS="$PWD/$work/root.crt"
keytool -importcert -noprompt -alias portal -file "$NODE_EXTRA_CA_CERTS" -keystore "$work/trust.p12" \
    -storetype PKCS12 -storepass changeit
export BUDGET_DB_URL=jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1 BUDGET_DB_USER=budget_release
export BUDGET_PORTAL_URL=https://127.0.0.1:18759/receive.php BUDGET_PORTAL_DELAY_MS=3000
java -Djavax.net.ssl.trustStore="$PWD/$work/trust.p12" -Djavax.net.ssl.trustStorePassword=changeit \
    -jar target/department-budget-0.1.0.war --spring.profiles.active=proxy > "$work/tomcat.log" 2>&1 &
server=$!
for attempt in {1..60}; do
    kill -0 "$server" || { tail -20 "$work/tomcat.log"; exit 1; }
    if curl -fsS http://127.0.0.1:8080/ > /dev/null 2>&1; then break; fi
    sleep 1
done
curl --cacert "$NODE_EXTRA_CA_CERTS" -fsS "$TEST_DEPLOYMENT_URL" > /dev/null
if ! command -v certutil >/dev/null; then sudo apt-get update -qq && sudo apt-get install -y libnss3-tools; fi
export HOME="$PWD/$work/browser-home"
for database in "$HOME/.pki/nssdb" "$HOME/.local/share/pki/nssdb"; do
    mkdir -p "$database"
    certutil -N -d "sql:$database" --empty-password
    certutil -A -d "sql:$database" -n "Budget CI Root" -t 'C,,' -i "$NODE_EXTRA_CA_CERTS"
done
cd frontend
export RECORD_PORTAL_CLAIM="$PWD/../$work/claim.txt"
CI_PROXY_PROBE=1 RECORD_TLS=1 TEST_PHP_ADDRESS=127.0.0.1:18760 node node_modules/@playwright/test/cli.js test https.spec.ts claims.spec.ts portal-retry.spec.ts --reporter=list
claim=$(cat "$RECORD_PORTAL_CLAIM")
[[ "$claim" =~ ^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$ ]]
projection=$(mysql_query "select concat(workspace_id,'|',claim_id,'|',sequence_no,'|',event_type,'|',amount)
    from budget_claim_summary where claim_id='$claim'")
oracle=$(docker exec -i "$ORACLE_SERVICE_CONTAINER" sqlplus -s '/ as sysdba' <<SQL
whenever sqlerror exit failure rollback
set heading off feedback off pagesize 0 verify off linesize 200
alter session set container=FREEPDB1;
select c.workspace_id||'|'||c.claim_id||'|'||o.sequence_no||'|'||o.event_type||'|'||p.spent_delta
from budget_release.expense_claim c
join budget_release.budget_posting p on p.workspace_id=c.workspace_id and p.claim_id=c.claim_id
join budget_release.portal_outbox o on o.workspace_id=c.workspace_id and o.claim_id=c.claim_id
where c.claim_id='$claim' and p.kind='SETTLEMENT' and o.sequence_no=2 and o.amount=p.spent_delta;
exit success
SQL
)
oracle=$(printf '%s' "$oracle" | sed '/^[[:space:]]*$/d;s/[[:space:]]*$//')
[[ "$projection" == *"|$claim|2|SETTLEMENT|180000" && "$projection" == "$oracle" ]]
printf 'Oracle and MySQL reconciliation: %s\n' "$projection"
cd ..
docker exec -i "$ORACLE_SERVICE_CONTAINER" sqlplus -s '/ as sysdba' > "$work/settlement.csv" <<SQL
whenever sqlerror exit failure rollback
set heading off feedback off pagesize 0 verify off linesize 512 trimout on
alter session set container=FREEPDB1;
select event_id||','||workspace_id||','||claim_id||','||sequence_no||','||event_type||','||amount
from budget_release.portal_outbox where claim_id='$claim' and sequence_no=2;
exit success
SQL
jar=portal-importer/target/portal-importer-0.1.0.jar
java -jar "$jar" csv < "$work/settlement.csv" > "$work/settlement.json"
java -jar "$jar" json < "$work/settlement.json" > "$work/replay.json"
cmp "$work/settlement.json" "$work/replay.json"
event_id=$(python3 -c 'import json,sys,uuid; print(uuid.UUID(json.load(open(sys.argv[1]))["eventId"]))' "$work/replay.json")
receipt_count() { mysql_query "select count(*) from budget_event_receipt where event_id='$event_id'"; }
[[ "$(receipt_count)" == 1 ]]
http_status=$(curl --cacert "$NODE_EXTRA_CA_CERTS" -sS -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $BUDGET_PORTAL_TOKEN" -H 'Content-Type: application/json' \
    --data-binary "@$work/replay.json" "$BUDGET_PORTAL_URL")
[[ "$http_status" == 204 && "$(receipt_count)" == 1 ]]
[[ "$(mysql_query "select amount from budget_claim_summary where claim_id='$claim' and sequence_no=2")" == 180000 ]]
