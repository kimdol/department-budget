"""Run as root on the demo VM. The existing receiver keeps its original grants."""
import argparse
import getpass
import grp
import os
from pathlib import Path
import re
import secrets
import subprocess
import tempfile


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
    if config.exists():
        raise SystemExit("Cleanup configuration already exists")
    owner = "budget_cleanup"
    admin = getpass.getpass("MySQL budgetadmin password: ")
    password = secrets.token_hex(32)
    # Restrict the maintenance principal to the connecting VM, as observed by MySQL.
    env = {**os.environ, "MYSQL_PWD": admin}
    login = command + ["--user", "budgetadmin"]
    peer = subprocess.check_output(login + ["-e", "select substring_index(user(),'@',-1)"], env=env, text=True).strip()
    if not re.fullmatch(r"[A-Za-z0-9.:-]+", peer):
        raise ValueError("Unexpected MySQL client host")
    account = f"'{owner}'@'{peer}'"
    sql = f"""CREATE USER {account} IDENTIFIED BY '{password}' REQUIRE SSL;
GRANT SELECT(event_id), DELETE ON budget.budget_event_receipt TO {account};
GRANT SELECT(workspace_id), DELETE ON budget.budget_claim_summary TO {account};
"""
    # Keep a recoverable, private configuration even if only some grants succeed.
    with tempfile.NamedTemporaryFile(mode="w", dir=config.parent, delete=False) as candidate:
        candidate.write(f"[client]\nuser={owner}\npassword={password}\nhost={host}\nprotocol=tcp\nssl-mode=REQUIRED\n")
    os.replace(candidate.name, config)
    subprocess.run(login, input=sql, env=env, text=True, check=True, stderr=subprocess.DEVNULL)
    os.chown(config, 0, grp.getgrnam("department-budget").gr_gid)
    os.chmod(config, 0o640)


if __name__ == "__main__":
    main()
