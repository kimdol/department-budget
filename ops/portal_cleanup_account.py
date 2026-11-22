"""Run as root on the demo VM. The existing receiver keeps its original grants."""
import argparse
import fcntl
import getpass
import grp
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile


def query(command, sql, stage, environment=None, missing_grant_ok=False):
    result = subprocess.run(command, input=sql, env=environment, text=True, capture_output=True)
    if result.returncode:
        # Server error text can quote a statement containing the generated password.
        match = re.search(r"\bERROR (\d+)\b", result.stderr)
        code = match.group(1) if match else "client error"
        if missing_grant_ok and code == "1141":
            return None
        raise SystemExit(f"{stage} failed ({code})")
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", action="store_true")
    args = parser.parse_args()
    dsn = os.environ["PORTAL_MYSQL_DSN"]
    host = re.search(r"(?:mysql:|;)host=([^;]+)", dsn).group(1)
    command = ["mysql", "--protocol=tcp", "--ssl-mode=REQUIRED", "--connect-timeout=8",
               "--batch", "--skip-column-names", "--host", host, "budget"]
    if args.inventory:
        environment = {**os.environ, "MYSQL_PWD": os.environ["PORTAL_MYSQL_PASSWORD"]}
        subprocess.run(command + ["--user", os.environ["PORTAL_MYSQL_USER"]],
                       input="""select concat('mysql_receipts=',count(*)) from budget_event_receipt;
select concat('mysql_summaries=',count(*)) from budget_claim_summary;
select concat('mysql_visible_table_bytes=',sum(data_length+index_length)) from information_schema.tables where table_schema='budget';
""", text=True, env=environment, check=True)
        return
    config = Path("/etc/department-budget/cleanup.cnf")
    owner = "budget_cleanup"
    admin = getpass.getpass("MySQL budgetadmin password: ")
    with (config.parent / ".cleanup-setup.lock").open("a") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        provision(command, host, config, owner, admin)


def provision(command, host, config, owner, admin):
    # Restrict the maintenance principal to the connecting VM, as observed by MySQL.
    env = {**os.environ, "MYSQL_PWD": admin}
    login = command + ["--user", "budgetadmin"]
    peer = query(login, "select substring_index(user(),'@',-1);", "Administrator login", env)
    if not re.fullmatch(r"[A-Za-z0-9.:-]+", peer):
        raise ValueError("Unexpected MySQL client host")
    account = f"'{owner}'@'{peer}'"
    grants = query(login, f"SHOW GRANTS FOR {account};", "Check existing account", env, missing_grant_ok=True)
    client = ["mysql", f"--defaults-file={config}", "--connect-timeout=8", "--batch", "--skip-column-names"]
    if grants is None:
        # A prior interruption may have written the file before CREATE USER succeeded.
        password = secrets.token_urlsafe(18) + "aA7!"
        with tempfile.NamedTemporaryFile(mode="w", dir=config.parent, delete=False) as candidate:
            candidate.write(f"[client]\nuser={owner}\npassword={password}\nhost={host}\nprotocol=tcp\nssl-mode=REQUIRED\n")
        os.replace(candidate.name, config)
        query(login, f"CREATE USER {account} IDENTIFIED BY '{password}' REQUIRE SSL;", "Create cleanup account", env)
    if not config.is_file() or query(client, "select current_user();", "Verify saved credential") != f"{owner}@{peer}":
        raise SystemExit("Existing account does not match the saved credential")
    sql = f"""GRANT SELECT(event_id), DELETE ON budget.budget_event_receipt TO {account};
GRANT SELECT(workspace_id), DELETE ON budget.budget_claim_summary TO {account};
"""
    query(login, sql, "Grant cleanup permissions", env)
    query(client + ["budget"], """START TRANSACTION;
DELETE FROM budget_event_receipt WHERE event_id IS NULL AND 1=0;
DELETE FROM budget_claim_summary WHERE workspace_id IS NULL AND 1=0;
ROLLBACK;""", "Verify cleanup permissions")
    os.chown(config, 0, grp.getgrnam("department-budget").gr_gid)
    os.chmod(config, 0o640)
    print("Cleanup account verified. No visitor records were deleted.")


if __name__ == "__main__":
    main()
