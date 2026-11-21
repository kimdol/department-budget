set -euo pipefail
: "${ORACLE_SERVICE_CONTAINER:?}"
: "${BUDGET_DB_PASSWORD:?}"

database=(docker exec -i "$ORACLE_SERVICE_CONTAINER")
work=target/oracle-restore
mkdir -p "$work"
dump_dir="/tmp/budget-restore-${GITHUB_RUN_ID:?}-${GITHUB_RUN_ATTEMPT:?}"
restore_files="/opt/oracle/oradata/budget-verify-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
source_pdb=FREEPDB1
restore_pdb=BUDGETVERIFY
"${database[@]}" mkdir "$dump_dir" "$restore_files"

"${database[@]}" sqlplus -s '/ as sysdba' <<SQL
whenever sqlerror exit failure rollback
alter session set container=$source_pdb;
create directory budget_ci_export as '$dump_dir';
grant read, write on directory budget_ci_export to budget;
alter session set container=CDB\$ROOT;
create pluggable database $restore_pdb admin user budget_verify_admin
  identified by "$BUDGET_DB_PASSWORD" create_file_dest='$restore_files';
alter pluggable database $restore_pdb open;
alter system register;
alter session set container=$restore_pdb;
create tablespace budget_verify datafile size 16M autoextend on next 8M maxsize 256M;
create user budget_restore identified by "$BUDGET_DB_PASSWORD"
  default tablespace budget_verify quota unlimited on budget_verify;
grant create session, create table, create sequence to budget_restore;
create directory budget_ci_export as '$dump_dir';
grant read, write on directory budget_ci_export to budget_restore;
exit success
SQL

read_sql() {
    local schema="$1"
    local pdb="$2"
    {
        printf 'whenever sqlerror exit failure rollback\nconnect %s/%s@//localhost:1521/%s\n' "$schema" "$BUDGET_DB_PASSWORD" "$pdb"
        cat
    } | "${database[@]}" sqlplus -s -L /nolog
}

identity_sql="set pagesize 0 feedback off heading off
select sys_context('USERENV','CON_NAME')||'|'||sys_context('USERENV','SESSION_USER') from dual;
exit success"
{
    read_sql budget "$source_pdb" <<< "$identity_sql"
    read_sql budget_restore "$restore_pdb" <<< "$identity_sql"
} | grep -E '^[A-Z0-9_]+[|][A-Z0-9_]+$' > "$work/containers.txt"
diff -u <(printf '%s\n' 'FREEPDB1|BUDGET' 'BUDGETVERIFY|BUDGET_RESTORE') "$work/containers.txt"

read_sql budget "$source_pdb" < ops/oracle_reconcile.sql > "$work/source.txt"
pump() {
    local program="$1" schema="$2" pdb="$3"
    shift 3
    printf 'userid=%s/%s@//localhost:1521/%s\n' "$schema" "$BUDGET_DB_PASSWORD" "$pdb" |
        "${database[@]}" bash -c 'umask 077; file=$(mktemp); trap '\''rm -f "$file"'\'' EXIT; cat > "$file"; "$1" parfile="$file" "${@:2}"' bash "$program" "$@"
}
SECONDS=0
pump expdp budget "$source_pdb" schemas=BUDGET directory=BUDGET_CI_EXPORT dumpfile=budget.dmp logfile=export.log
export_seconds=$SECONDS
SECONDS=0
pump impdp budget_restore "$restore_pdb" remap_schema=BUDGET:BUDGET_RESTORE remap_tablespace=USERS:BUDGET_VERIFY \
    directory=BUDGET_CI_EXPORT dumpfile=budget.dmp logfile=import.log
import_seconds=$SECONDS
SECONDS=0
read_sql budget_restore "$restore_pdb" < ops/oracle_reconcile.sql > "$work/restored.txt"
for kind in WORKSPACE DEPARTMENT ALLOCATION POSTING BALANCE LINE POLICY CLAIM REPLACEMENT VERSION OUTBOX EVIDENCE REVIEW; do
    grep -q "^${kind}|" "$work/source.txt"
done
diff -u "$work/source.txt" "$work/restored.txt"
sha256sum "$work/source.txt" "$work/restored.txt" > "$work/checksums.txt"
printf 'export_seconds=%d\nimport_seconds=%d\nreconciliation_seconds=%d\n' \
    "$export_seconds" "$import_seconds" "$SECONDS" > "$work/durations.txt"
