import json
import os
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import uuid
from contextlib import ExitStack
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_for(check, process):
    for _ in range(200):
        if process.poll() is not None:
            raise RuntimeError("시험 서버가 시작 중 종료되었습니다.")
        if check():
            return
        time.sleep(0.2)
    raise TimeoutError("시험 서버 준비 시간 초과")


def stop(process):
    if process.poll() is None:
        process.terminate()
        process.wait(timeout=20)


def main():
    root = Path(__file__).resolve().parents[1]
    php = shutil.which("php")
    mysql = shutil.which("mysql")
    daemon = shutil.which("mysqld")
    if not all([php, mysql, daemon]):
        raise RuntimeError("PATH에 php, mysql, mysqld 실행 파일이 필요합니다.")
    with tempfile.TemporaryDirectory() as folder, ExitStack() as stack:
        directory = Path(folder)
        port = free_port()
        password = secrets.token_hex(24)
        token = secrets.token_hex(32)
        env = dict(os.environ, MYSQL_PWD=password)
        args = [daemon, "--no-defaults", "--datadir=" + str(directory / "data")]
        if os.name == "nt":
            args.append("--no-monitor")
        subprocess.run(args + ["--initialize-insecure"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        init = directory / "initialize.sql"
        init.write_text("ALTER USER 'root'@'localhost' IDENTIFIED BY '" + password + "';\n", encoding="ascii")
        db = subprocess.Popen(args + ["--port=" + str(port), "--bind-address=127.0.0.1", "--mysqlx=0",
                                      "--init-file=" + str(init)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        stack.callback(stop, db)
        cli = [mysql, "--no-defaults", "--protocol=TCP", "--host=127.0.0.1", "--port=" + str(port),
               "--user=root", "--batch", "--skip-column-names"]
        def sql(statement, check=True):
            return subprocess.run(cli, input=statement, text=True, capture_output=True, env=env, check=check)
        wait_for(lambda: sql("SELECT 1", False).returncode == 0, db)
        def shutdown():
            sql("SHUTDOWN", False)
            db.wait(timeout=30)
        stack.callback(shutdown)
        init.unlink()
        receiver_password = secrets.token_hex(24)
        sql("CREATE DATABASE budget; USE budget;\n" + (root / "portal/mysql/V001__projection.sql").read_text() +
            "\nCREATE USER 'receiver'@'localhost' IDENTIFIED BY '" + receiver_password +
            "'; GRANT SELECT,INSERT,UPDATE ON budget.* TO 'receiver'@'localhost';")
        webport = free_port()
        address = "127.0.0.1:" + str(webport)
        php_args = [php]
        if os.name == "nt":
            php_args.extend(["-n", "-d", "extension_dir=" + str(Path(php).parent / "ext"), "-d", "extension=pdo_mysql"])
        server = subprocess.Popen(php_args + ["-S", address, "-t", str(root / "portal")],
            env=dict(os.environ, BUDGET_PORTAL_TOKEN=token, PORTAL_MYSQL_DSN=f"mysql:host=127.0.0.1;port={port};dbname=budget",
                     PORTAL_MYSQL_USER="receiver", PORTAL_MYSQL_PASSWORD=receiver_password),
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        stack.callback(stop, server)
        def send(data=None):
            body = json.dumps(data).encode() if data is not None else None
            request = Request("http://" + address + "/receive.php", body,
                              {"Authorization": "Bearer " + token, "Content-Type": "application/json"})
            try:
                with urlopen(request, timeout=3) as response:
                    return response.status
            except HTTPError as error:
                return error.code
            except URLError:
                return 0
        wait_for(lambda: send() == 405, server)
        approval = dict(eventId=str(uuid.uuid4()), claimId=str(uuid.uuid4()), workspaceId=str(uuid.uuid4()),
                        sequenceNo=1, eventType="APPROVAL", amount=200000)
        settlement = dict(approval, eventId=str(uuid.uuid4()), sequenceNo=2, eventType="SETTLEMENT", amount=180000)
        replay = settlement
        if "--importer" in sys.argv:
            jar = root / "portal-importer/target/portal-importer-0.1.0.jar"
            csv = ",".join(str(settlement[k]) for k in ("eventId","workspaceId","claimId","sequenceNo","eventType","amount"))
            def normalize(fmt, value):
                return subprocess.run(["java","-jar",str(jar),fmt], input=value.encode(), capture_output=True, check=True).stdout
            canonical = normalize("csv",csv)
            if canonical != normalize("json",json.dumps(settlement)):
                raise AssertionError("JSON·CSV 메시지 불일치")
            replay = json.loads(canonical)
        statuses = [send(settlement), send(approval), send(approval), send(settlement), send(replay),
                    send(dict(settlement, amount=179999)), send(dict(approval, eventId=str(uuid.uuid4())))]
        if statuses != [409, 204, 204, 204, 204, 409, 409]:
            raise AssertionError(statuses)
        def read_status(workspace, credential):
            request = Request(f"http://{address}/status.php?workspace={workspace}&claim={approval['claimId']}",
                              headers={"Authorization": "Bearer " + credential})
            try:
                with urlopen(request, timeout=3) as response:
                    if response.headers.get("Cache-Control") != "no-store":
                        raise AssertionError("포털 조회 캐시 정책")
                    return response.status, json.load(response)
            except HTTPError as error:
                return error.code, None
        if read_status(approval["workspaceId"],token) != (200,dict(sequence=2,event="SETTLEMENT",amount=180000)):
            raise AssertionError("포털 수신 금액")
        if read_status(uuid.uuid4(),token)[0] != 404 or read_status(approval["workspaceId"],"")[0] != 403:
            raise AssertionError("포털 조회 범위")
        rows = sql("USE budget; SELECT COUNT(*) FROM budget_event_receipt; "
                   "SELECT event_type,amount FROM budget_claim_summary;").stdout.splitlines()
        if rows != ["2", "SETTLEMENT\t180000"]:
            raise AssertionError(rows)
        print(json.dumps(dict(mysql=sql("SELECT VERSION()").stdout.strip(), http=statuses, receipts=2, settled=180000)))
        if "--browser" in sys.argv:
            subprocess.run([sys.executable,str(root/"ops/test_https.py")],
                           env=dict(os.environ,TEST_PHP_ADDRESS=address,BUDGET_PORTAL_TOKEN=token), check=True)


if __name__ == "__main__":
    main()
