set -euo pipefail
: "${ORACLE_SERVICE_CONTAINER:?}" "${BUDGET_DB_PASSWORD:?}" "${RUNNER_TEMP:?}"
claim=$(cat "${RECORD_ROLLBACK_CLAIM:?}")
[[ "$claim" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]
work="$PWD/target/war-rollback"
previous="$RUNNER_TEMP/previous-budget-release"
revision=d45c8473228506bd880cf0c12c92fc96830d3f35
mkdir -p "$work"
git worktree add --detach "$previous" "$revision"
(cd "$previous/frontend" && npm ci && npm run build)
(cd "$previous" && mvn -B -ntp -DskipTests package)
war=target/department-budget-0.1.0.war
printf 'current %s %s\nprevious %s %s\n' "$(git rev-parse HEAD)" "$(sha256sum "$war" | cut -d' ' -f1)" \
    "$revision" "$(sha256sum "$previous/$war" | cut -d' ' -f1)" > "$work/releases.txt"

read_claim() {
    docker exec -i "$ORACLE_SERVICE_CONTAINER" sqlplus -s '/ as sysdba' <<SQL
whenever sqlerror exit failure rollback
whenever oserror exit failure
set heading off feedback off pagesize 0 trimout on linesize 400
alter session set container=FREEPDB1;
alter session set current_schema=budget_release;
set transaction read only;
select 'CLAIM|' || claim_id || '|' || approved || '|' || actual || '|' || settled
from expense_claim where claim_id='$claim';
select 'POSTING|' || kind || '|' || committed_delta || '|' || spent_delta || '|' || period_key
from budget_posting where claim_id='$claim' order by kind;
select 'TOTAL|' || count(*) || '|' || sum(committed_delta) || '|' || sum(spent_delta)
from budget_posting where claim_id='$claim';
exit success
SQL
}
read_claim > "$work/before.txt"
grep -Fxq "CLAIM|$claim|200000|225000|180000" "$work/before.txt"
grep -Fxq 'TOTAL|3|0|150000' "$work/before.txt"
export BUDGET_DB_USER=budget_release BUDGET_DB_URL=jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1
export HTTPS_ONLY=false SERVER_ADDRESS=127.0.0.1 SERVER_PORT=18753
java -jar "$previous/$war" > "$work/tomcat.log" 2>&1 &
server=$!
trap 'kill "$server" 2>/dev/null || true; wait "$server" 2>/dev/null || true' EXIT
for attempt in {1..60}; do
    kill -0 "$server"
    if curl -fsS --max-time 2 "http://127.0.0.1:$SERVER_PORT/" > /dev/null 2>&1; then break; fi
    sleep 1
done
(cd frontend && TEST_EXTERNAL_RELEASE=1 npx --no-install playwright test travel-approval.spec.ts --reporter=list)
read_claim > "$work/after.txt"
diff -u "$work/before.txt" "$work/after.txt"
