from pathlib import Path
import re
import subprocess
import os


def measurement_sql():
    mapper = Path("src/main/java/kr/budget/claims/BudgetMapper.java").read_text(encoding="utf-8")
    columns = re.search(r'String COLUMNS = "([^"]+)";', mapper)[1]
    sql = [
        "whenever sqlerror exit failure rollback",
        "whenever oserror exit failure",
        "set pagesize 0 linesize 3000 trimout on tab off feedback off heading off",
        "alter session set container=FREEPDB1;",
        "alter session set current_schema=budget_release;",
        "alter session set statistics_level=all;",
        """insert into budget_workspace(workspace_id)
            select 'inbox-measurement-' || level from dual connect by level <= 100;""",
        """insert into expense_claim(claim_id, workspace_id, title, category, status,
            estimated, days, formula, inbox_order)
            select 'sample-' || d.department || '-' || c.seq, 'inbox-measurement-' || d.department,
            'Training ' || c.seq,'EDUCATION','SUBMITTED',300000,1,'eligible_cost',c.seq
            from (select level department from dual connect by level <= 100) d
            cross join (select level seq from dual connect by level <= 10000) c;""",
        "commit;",
        """select 'FIXTURE|' || count(*) || '|' || count(distinct workspace_id)
            from expense_claim where workspace_id like 'inbox-measurement-%';""",
        "exec dbms_stats.gather_table_stats('BUDGET_RELEASE','EXPENSE_CLAIM',cascade=>true);",
        "variable workspace varchar2(36)",
        "exec :workspace := 'inbox-measurement-50';",
        "variable boundary number",
        "exec :boundary := 1001;",
    ]
    queries = {
        "offset": f"select {columns} from expense_claim where workspace_id=:workspace "
                  "order by inbox_order desc offset 9000 rows fetch next 51 rows only;",
        "cursor": f"select {columns} from expense_claim where workspace_id=:workspace and inbox_order < :boundary "
                  "order by inbox_order desc fetch first 51 rows only;",
    }
    for run in range(4):
        for name in (("offset", "cursor") if run % 2 == 0 else ("cursor", "offset")):
            sql.extend([f"prompt BEGIN {run} {name}", queries[name],
                        "select plan_table_output from table(dbms_xplan.display_cursor(null,null,'ALLSTATS LAST'));",
                        f"prompt END {run} {name}"])
    sql.append("exit success")
    return "\n".join(sql) + "\n"


if __name__ == "__main__":
    result = subprocess.run(["docker", "exec", "-i", os.environ["ORACLE_SERVICE_CONTAINER"],
                             "sqlplus", "-s", "/ as sysdba"], input=measurement_sql(),
                            text=True, stdout=subprocess.PIPE, check=False)
    directory = Path("target/oracle-inbox")
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "plans.txt").write_text(result.stdout, encoding="utf-8")
    result.check_returncode()
    if "FIXTURE|1000000|100" not in result.stdout.splitlines():
        raise ValueError("Expected one million claims in 100 departments")
    for run in range(4):
        for name in ("offset", "cursor"):
            section = result.stdout.split(f"BEGIN {run} {name}\n")[1].split(f"END {run} {name}")[0]
            ids = re.findall(r"^sample-50-(\d+)\s", section, re.MULTILINE)
            if ids != [str(i) for i in range(1000, 949, -1)] or "Buffers" not in section:
                raise ValueError(f"Missing rows or execution statistics in {run} {name}")
