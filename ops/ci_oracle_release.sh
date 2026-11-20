set -euo pipefail
: "${ORACLE_SERVICE_CONTAINER:?}"
: "${BUDGET_DB_PASSWORD:?}"
database=(docker exec -i "$ORACLE_SERVICE_CONTAINER")
work=target/oracle-release
mkdir -p "$work"
python3 ops/oracle_install.py target/department-budget-0.1.0.war "$work/install.sql"
"${database[@]}" sqlplus -s '/ as sysdba' <<SQL
whenever sqlerror exit failure rollback
alter session set container=FREEPDB1;
create user budget_release identified by "$BUDGET_DB_PASSWORD" quota unlimited on users;
grant create session, create table, create sequence to budget_release;
exit success
SQL
install_release() {
    {
        printf 'whenever sqlerror exit failure rollback\nconnect budget_release/%s@//localhost:1521/FREEPDB1\n' "$BUDGET_DB_PASSWORD"
        cat "$work/install.sql"
    } | "${database[@]}" sqlplus -s /nolog
}
install_release > "$work/initial.log" || { tail -12 "$work/initial.log"; exit 1; }
if install_release > "$work/repeat.log"; then
    exit 1
fi
grep -q 'ORA-20001: Empty schema required' "$work/repeat.log"

export BUDGET_DB_USER=budget_release BUDGET_DB_URL=jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1
export HTTPS_ONLY=false SERVER_ADDRESS=127.0.0.1 SERVER_PORT=18753
java -jar target/department-budget-0.1.0.war > "$work/tomcat.log" 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true; wait "$server" 2>/dev/null || true' EXIT
for attempt in {1..60}; do
    kill -0 "$server" || { tail -25 "$work/tomcat.log"; exit 1; }
    if curl -fsS --max-time 2 "http://127.0.0.1:$SERVER_PORT/" > /dev/null 2>&1; then break; fi
    sleep 1
done
curl -fsS --max-time 3 "http://127.0.0.1:$SERVER_PORT/" > /dev/null
(
    cd frontend
    TEST_EXTERNAL_RELEASE=1 npx --no-install playwright test claims.spec.ts hospital-policy.spec.ts travel-approval.spec.ts inbox-pages.spec.ts --reporter=list
)
