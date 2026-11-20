set -euo pipefail
: "${ORACLE_SERVICE_CONTAINER:?}"
: "${BUDGET_DB_PASSWORD:?}"

database=(docker exec -i "$ORACLE_SERVICE_CONTAINER")
work=target/oracle-restore
mkdir -p "$work"
dump_dir="/tmp/budget-restore-${GITHUB_RUN_ID:?}-${GITHUB_RUN_ATTEMPT:?}"
"${database[@]}" mkdir "$dump_dir"

"${database[@]}" sqlplus -s '/ as sysdba' <<SQL
whenever sqlerror exit failure rollback
alter session set container=FREEPDB1;
create user budget_restore identified by "$BUDGET_DB_PASSWORD" quota unlimited on users;
grant create session, create table, create sequence to budget_restore;
create directory budget_ci_export as '$dump_dir';
grant read, write on directory budget_ci_export to budget,budget_restore;
exit success
SQL

read_ledger() {
    local schema="$1"
    local output="$2"
    {
        printf 'whenever sqlerror exit failure rollback\nconnect %s/%s@//localhost:1521/FREEPDB1\n' "$schema" "$BUDGET_DB_PASSWORD"
        cat ops/oracle_reconcile.sql
    } | "${database[@]}" sqlplus -s /nolog > "$output"
}

read_ledger budget "$work/source.txt"
pump() {
    local program="$1" schema="$2"
    shift 2
    printf 'userid=%s/%s@//localhost:1521/FREEPDB1\n' "$schema" "$BUDGET_DB_PASSWORD" |
        "${database[@]}" bash -c 'umask 077; file=$(mktemp); trap '\''rm -f "$file"'\'' EXIT; cat > "$file"; "$1" parfile="$file" "${@:2}"' bash "$program" "$@"
}
SECONDS=0
pump expdp budget schemas=BUDGET directory=BUDGET_CI_EXPORT dumpfile=budget.dmp logfile=export.log
export_seconds=$SECONDS
SECONDS=0
pump impdp budget_restore remap_schema=BUDGET:BUDGET_RESTORE directory=BUDGET_CI_EXPORT dumpfile=budget.dmp logfile=import.log
import_seconds=$SECONDS
SECONDS=0
read_ledger budget_restore "$work/restored.txt"
for kind in WORKSPACE DEPARTMENT ALLOCATION POSTING BALANCE LINE POLICY CLAIM EVIDENCE REVIEW; do
    grep -q "^${kind}|" "$work/source.txt"
done
diff -u "$work/source.txt" "$work/restored.txt"
sha256sum "$work/source.txt" "$work/restored.txt" > "$work/checksums.txt"
printf 'export_seconds=%d\nimport_seconds=%d\nreconciliation_seconds=%d\n' \
    "$export_seconds" "$import_seconds" "$SECONDS" > "$work/durations.txt"
