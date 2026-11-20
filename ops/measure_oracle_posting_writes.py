import json
import os
from pathlib import Path
import re
import subprocess


def measure_block(block, mode):
    return f"""declare
    started timestamp with time zone;
    elapsed interval day to second(9);
    redo_before number;
    redo_after number;
    n number;
    net number;
    function redo_bytes return number is
        amount number;
    begin
        select s.value into amount from v$mystat s join v$statname t on t.statistic#=s.statistic#
        where t.name='redo size';
        return amount;
    end;
begin
    select count(*) into n from all_indexes where owner='BUDGET_RELEASE' and index_name='EXP_MONTH_TRIAL';
    if n != {int(mode == "candidate")} then raise_application_error(-20003,'Unexpected index state'); end if;
    for sample in 0..10 loop
        redo_before := redo_bytes();
        started := systimestamp;
        for i in 1..500 loop
            insert into budget_release.budget_posting(workspace_id,claim_id,kind,committed_delta,spent_delta,period_key)
            values('inbox-measurement-50','sample-50-'||to_char(i),'WRITE_TRIAL',200000,0,'2026-11');
            commit write immediate wait;
        end loop;
        elapsed := systimestamp-started;
        redo_after := redo_bytes();
        select count(*),sum(committed_delta) into n,net from budget_release.budget_posting
        where workspace_id='inbox-measurement-50' and kind='WRITE_TRIAL';
        if n != 500 or net != 100000000 then raise_application_error(-20004,'Posting batch mismatch'); end if;
        dbms_output.put_line('WRITE|{block}|{mode}|'||sample||'|'||
            round((extract(minute from elapsed)*60+extract(second from elapsed))*1000000)||'|'||(redo_after-redo_before));
        delete from budget_release.budget_posting where workspace_id='inbox-measurement-50' and kind='WRITE_TRIAL';
        commit;
    end loop;
end;
/"""


def experiment():
    sql = ["whenever sqlerror exit failure rollback", "whenever oserror exit failure",
           "set sqlblanklines on", "set feedback off", "set heading off", "set pagesize 0",
           "alter session set container=FREEPDB1;", "alter session set current_schema=budget_release;",
           "set serveroutput on size unlimited",
           "exec dbms_output.put_line('OUTPUT_READY');",
           "select 'POPULATION|'||count(*) from budget_posting where workspace_id like 'inbox-measurement-%';",
           measure_block(1, "candidate"), "drop index budget_release.exp_month_trial;",
           measure_block(2, "baseline"), measure_block(3, "baseline"),
           "create index budget_release.exp_month_trial on budget_release.budget_posting(workspace_id,period_key,claim_id) invisible;",
           measure_block(4, "candidate"),
           "alter index budget_release.exp_month_trial rename to posting_month_claim;",
           "alter index budget_release.posting_month_claim visible;", "exit success"]
    return "\n".join(sql)+"\n"


if __name__ == "__main__":
    folder = Path("target/oracle-month")
    result = subprocess.run(["docker","exec","-i",os.environ["ORACLE_SERVICE_CONTAINER"],"sqlplus","-s","/ as sysdba"],
                            input=experiment(),text=True,stdout=subprocess.PIPE)
    (folder/"posting-writes.txt").write_text(result.stdout,encoding="utf-8")
    result.check_returncode()
    if "OUTPUT_READY" not in result.stdout.splitlines():
        raise ValueError("SQL*Plus did not capture DBMS_OUTPUT in the measurement PDB")
    if "POPULATION|1000000" not in result.stdout.splitlines():
        raise ValueError("Missing one million existing postings")
    rows = re.findall(r"^WRITE\|(\d)\|(baseline|candidate)\|(\d+)\|(\d+)\|(\d+)$",result.stdout,re.M)
    output = {}
    for mode, blocks in (("baseline",(2,3)),("candidate",(1,4))):
        observations = [(int(b),int(s),int(t),int(r)) for b,m,s,t,r in rows if m==mode]
        if [(b,s) for b,s,_,_ in observations] != [(b,s) for b in blocks for s in range(11)]:
            raise ValueError("Incomplete posting write samples")
        samples = [(t,r) for _,s,t,r in observations if s]
        output[mode] = {"batch_p95_us": sorted(t for t,_ in samples)[18],
                        "mean_redo_bytes_per_posting": sum(r for _,r in samples)/(500*len(samples))}
    (folder/"posting-summary.json").write_text(json.dumps(output,indent=2)+"\n",encoding="utf-8")
