"""Apply the verified V025 to V026 migration using SQLcl and the existing service environment."""
import os
from pathlib import Path
import re
import subprocess
import sys

from oracle_install import installation_sql


def main():
    old, candidate, sqlcl = map(Path, sys.argv[1:])
    sql = installation_sql(candidate, upgrade_from=old)
    if sql.count("prompt V0") != 1 or "prompt V026__demo_retention.sql" not in sql:
        raise SystemExit("Expected only the demo retention migration")
    password = os.environ["BUDGET_DB_PASSWORD"]
    url = os.environ["BUDGET_DB_URL"]
    user = os.environ["BUDGET_DB_USER"]
    if not url.startswith("jdbc:oracle:thin:@") or not re.fullmatch(r"[A-Za-z0-9_]+",user):
        raise SystemExit("Unexpected database configuration")
    dsn = url[len("jdbc:oracle:thin:@"):]
    if any(ch in password for ch in '"\r\n') or any(ch in dsn for ch in '\r\n'):
        raise SystemExit("Unsupported SQLcl login format")
    login = f'set echo off\nset define off\nwhenever sqlerror exit failure rollback\nconnect {user}/"{password}"@{dsn}\n'
    result = subprocess.run(["bash",str(sqlcl),"-s","-l","/nolog"],input=login+sql,text=True,
                            capture_output=True,timeout=240)
    codes = re.findall(r"\b(?:ORA|SP2|TNS)-\d+",result.stdout+result.stderr)
    if result.returncode or codes:
        raise SystemExit(f"SQLcl migration failed; exit {result.returncode}, codes {sorted(set(codes))}")
    print("V026 demo retention migration applied")


if __name__=="__main__":
    main()
