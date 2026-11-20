import json
import os
import re
import subprocess
from math import ceil
from pathlib import Path


def report_sql():
    source = Path("src/main/java/kr/budget/closing/ClosingMapper.java").read_text(encoding="utf-8")
    return re.search(r'@Select\("""\s*(select c\.category,count\(distinct c\.claim_id\).*?)\s*"""\)\s*List<Line> currentLines',
                     source, re.S)[1].replace("#{workspace}", ":workspace").replace("#{period}", ":period")


def experiment():
    query = report_sql()
    script = ["""whenever sqlerror exit failure rollback
whenever oserror exit failure
set sqlblanklines on serveroutput on pagesize 0 linesize 3000 trimout on heading off feedback off
alter session set container=FREEPDB1;
alter session set current_schema=budget_release;
update expense_claim set category=case mod(trunc((inbox_order-1)/12),3)
    when 0 then 'EDUCATION' when 1 then 'CONFERENCE' else 'TRAVEL' end
    where workspace_id like 'inbox-measurement-%';
insert into budget_posting(claim_id,workspace_id,kind,committed_delta,spent_delta,period_key)
    select claim_id,workspace_id,'SETTLEMENT',-180000,180000,
           '2026-'||lpad(to_char(mod(inbox_order-1,12)+1),2,'0')
    from expense_claim where workspace_id like 'inbox-measurement-%';
commit;
select 'FIXTURE|'||count(*)||'|'||count(distinct period_key)||'|'||count(distinct workspace_id)
    from budget_posting where workspace_id like 'inbox-measurement-%';
create index budget_release.exp_month_trial on budget_release.budget_posting(workspace_id,period_key,claim_id) invisible;
exec dbms_stats.gather_table_stats('BUDGET_RELEASE','BUDGET_POSTING',cascade=>true);
exec dbms_stats.gather_table_stats('BUDGET_RELEASE','EXPENSE_CLAIM',cascade=>true);
alter session set statistics_level=all;
variable workspace varchar2(36)
variable period varchar2(7)
exec :workspace := 'inbox-measurement-50';
exec :period := '2026-11';"""]
    for iteration in range(21):
        for mode in (("baseline", "candidate") if iteration % 2 == 0 else ("candidate", "baseline")):
            enabled = "true" if mode == "candidate" else "false"
            script.append(f"alter session set optimizer_use_invisible_indexes={enabled};")
            if iteration < 2:
                script.extend(["set serveroutput off", f"prompt PLAN {iteration} {mode}", query + ";",
                               "select plan_table_output from table(dbms_xplan.display_cursor(null,null,'ALLSTATS LAST'));",
                               "prompt END PLAN", "set serveroutput on"])
            script.append(f"""declare
                started timestamp with time zone := systimestamp;
                elapsed interval day to second(9);
                groups_seen number := 0;
            begin
                for r in ({query}) loop
                    if r.category not in ('CONFERENCE','EDUCATION','TRAVEL')
                       or r.claim_count != (case when r.category='TRAVEL' then 277 else 278 end)
                       or r.spent != r.claim_count*180000 then
                        raise_application_error(-20001,'Monthly report mismatch');
                    end if;
                    groups_seen := groups_seen+1;
                end loop;
                if groups_seen != 3 then raise_application_error(-20002,'Missing categories'); end if;
                elapsed := systimestamp-started;
                dbms_output.put_line('TIME|{iteration}|{mode}|'||
                    round((extract(minute from elapsed)*60+extract(second from elapsed))*1000000));
            end;
/""")
    script.append("exit success")
    return "\n".join(script) + "\n"


if __name__ == "__main__":
    folder = Path("target/oracle-month")
    folder.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(["docker", "exec", "-i", os.environ["ORACLE_SERVICE_CONTAINER"], "sqlplus", "-s", "/ as sysdba"],
                            input=experiment(), text=True, stdout=subprocess.PIPE)
    (folder / "plans.txt").write_text(result.stdout, encoding="utf-8")
    result.check_returncode()
    if "FIXTURE|1000000|12|100" not in result.stdout.splitlines():
        raise ValueError("Unexpected monthly report population")
    metadata = json.loads(Path("target/oracle-inbox/latency.json").read_text())
    del metadata["queries"]
    for mode in ("baseline", "candidate"):
        for iteration in (0, 1):
            plan = result.stdout.split(f"PLAN {iteration} {mode}\n", 1)[1].split("END PLAN", 1)[0]
            if "Buffers" not in plan or "BUDGET_POSTING" not in plan and "EXP_MONTH_TRIAL" not in plan:
                raise ValueError("Missing monthly report execution plan")
        rows = re.findall(rf"^TIME\|(\d+)\|{mode}\|(\d+)$", result.stdout, re.MULTILINE)
        if [int(i) for i, _ in rows] != list(range(21)):
            raise ValueError("Incomplete monthly measurement")
        samples = [int(value) for _, value in rows[1:]]
        metadata[mode] = {"microseconds": samples, "p95_microseconds": sorted(samples)[ceil(len(samples)*0.95)-1]}
    (folder / "latency.json").write_text(json.dumps(metadata, indent=2)+"\n", encoding="utf-8")
