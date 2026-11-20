set -euo pipefail
: "${ORACLE_SERVICE_CONTAINER:?}" "${BUDGET_DB_PASSWORD:?}" "${RUNNER_TEMP:?}"
work=target/oracle-upgrade
mkdir -p "$work"
python3 ops/test_oracle_install.py
war=target/department-budget-0.1.0.war
previous="$RUNNER_TEMP/previous-budget-release/$war"
python3 ops/oracle_install.py "$previous" "$work/old-install.sql"
python3 ops/oracle_install.py "$war" "$work/upgrade.sql" --upgrade-from "$previous"
docker exec -i "$ORACLE_SERVICE_CONTAINER" sqlplus -s '/ as sysdba' <<SQL
whenever sqlerror exit failure
alter session set container=FREEPDB1;
create user budget_upgrade identified by "$BUDGET_DB_PASSWORD" quota unlimited on users;
grant create session, create table, create sequence to budget_upgrade;
exit success
SQL
run_sql() {
    {
        printf 'whenever sqlerror exit failure rollback\nconnect budget_upgrade/%s@//localhost:1521/FREEPDB1\n' "$BUDGET_DB_PASSWORD"
        cat "$1"
    } | docker exec -i "$ORACLE_SERVICE_CONTAINER" sqlplus -s /nolog
}
run_sql "$work/old-install.sql" > "$work/old.log" || { tail -12 "$work/old.log"; exit 1; }
cat > "$work/seed.sql" <<'SQL'
insert into budget_workspace(workspace_id,spent) values('upgrade-dept',180000);
insert into expense_claim(claim_id,workspace_id,title,category,status,estimated,days,formula,inbox_order,approved,actual,settled)
values('upgrade-claim','upgrade-dept','Training','EDUCATION','SETTLED',300000,1,'eligible_cost',claim_inbox_seq.nextval,200000,225000,180000);
insert into budget_posting(claim_id,workspace_id,kind,committed_delta,spent_delta,period_key)
values('upgrade-claim','upgrade-dept','APPROVAL',200000,0,'2026-11');
insert into budget_posting(claim_id,workspace_id,kind,committed_delta,spent_delta,period_key)
values('upgrade-claim','upgrade-dept','SETTLEMENT',-200000,180000,'2026-11');
exit success commit
SQL
run_sql "$work/seed.sql" > "$work/seed.log"
run_sql "$work/upgrade.sql" > "$work/apply.log" || { tail -12 "$work/apply.log"; exit 1; }
if run_sql "$work/upgrade.sql" > "$work/repeat.log"; then exit 1; fi
grep -Fq 'ORA-20002: Unexpected source release' "$work/repeat.log"
cat > "$work/check.sql" <<'SQL'
set heading off feedback off pagesize 0 linesize 200
select 'RELEASE|'||last_version||'|'||phase||'|'||fingerprint from budget_schema_release;
select 'LEDGER|'||c.claim_id||'|'||c.applicant_id||'|'||count(*)||'|'||sum(p.committed_delta)||'|'||sum(p.spent_delta)||'|'||(w.allocation-w.committed-w.spent)
from expense_claim c join budget_posting p on c.claim_id=p.claim_id join budget_workspace w on c.workspace_id=w.workspace_id
where c.claim_id='upgrade-claim' group by c.claim_id,c.applicant_id,w.allocation,w.committed,w.spent;
exit success
SQL
run_sql "$work/check.sql" > "$work/result.txt"
grep -Eq '^RELEASE\|[0-9]+\|READY\|[0-9a-f]{64}$' "$work/result.txt"
grep -Fxq 'LEDGER|upgrade-claim|STAFF_01|2|0|180000|820000' "$work/result.txt"
cat "$work/result.txt"
