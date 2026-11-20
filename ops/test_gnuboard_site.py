from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import build_opener, HTTPCookieProcessor
from zipfile import ZipFile
import os
import secrets
import shutil
import socket
import subprocess
import time


def verify_site(root, framework, directory, php_args, address, mysql_port, sql, reader_env, workspace, claim):
    archive = directory / "framework.zip"
    subprocess.run(["git","-C",str(framework),"archive","--format=zip","-o",str(archive),"HEAD"],check=True)
    site = directory / "gnuboard"
    with ZipFile(archive) as source:
        source.extractall(site)
    (site / "data").mkdir(exist_ok=True)
    shutil.copytree(root / "gnuboard",site,dirs_exist_ok=True)
    password = secrets.token_hex(20)
    login_password = secrets.token_hex(16)
    sql("CREATE DATABASE board CHARACTER SET utf8mb4; CREATE USER 'board'@'localhost' IDENTIFIED BY '"+
        password+"'; GRANT ALL ON board.* TO 'board'@'localhost';")
    args = list(php_args)
    if os.name == "nt":
        for extension in ["mysqli","mbstring","gd"]:
            args.extend(["-d","extension="+extension])
    args.extend(["-d",f"mysqli.default_port={mysql_port}","-d","display_errors=0","-S",address,"-t",str(site)])
    server = subprocess.Popen(args,cwd=site,env=reader_env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    try:
        host, port = address.rsplit(":",1)
        for attempt in range(100):
            if server.poll() is not None:
                raise RuntimeError("그누보드 PHP 시작 실패")
            try:
                with socket.create_connection((host,int(port)),timeout=0.2):
                    break
            except OSError:
                time.sleep(0.1)
        else:
            raise TimeoutError("그누보드 PHP 준비 시간 초과")
        browser = build_opener(HTTPCookieProcessor(CookieJar()))
        def visit(path, fields=None):
            data = urlencode(fields).encode() if fields is not None else None
            with browser.open("http://"+address+path,data=data,timeout=25) as response:
                return response.read().decode("utf-8","replace"),response.headers
        visit("/install/install_config.php",{"agree":"동의함"})
        visit("/install/install_db.php",dict(mysql_host="127.0.0.1",mysql_user="board",mysql_pass=password,
                                             mysql_db="board",table_prefix="g5_",admin_id="finance",admin_pass=login_password,
                                             admin_name="시연 재무",admin_email="finance@example.test",g5_install=1))
        if not (site/"data/dbconfig.php").exists():
            raise AssertionError("그누보드 설치 실패")
        def denied():
            try:
                visit("/plugin/budget/statement.php")
            except HTTPError as error:
                if error.code == 403:
                    return
                raise
            raise AssertionError("비회원 보고서 접근 허용")
        denied()
        visit("/bbs/login.php")
        visit("/bbs/login_check.php",dict(mb_id="finance",mb_password=login_password))
        home,_ = visit("/")
        if "/plugin/budget/statement.php" not in home:
            raise AssertionError("로그인 후 기본 테마에 조회 링크 없음")
        report_path = "/plugin/budget/statement.php?"+urlencode({"claim":claim})
        empty,_ = visit(report_path)
        if "열람 가능한 승인·정산 내역이 없습니다." not in empty:
            raise AssertionError("미배정 회원의 조회 범위")
        sql("INSERT INTO budget.budget_portal_reader VALUES ('finance','"+workspace+"');")
        report,headers = visit(report_path)
        if not all(value in report for value in [claim,"정산 확정","180,000원"]) or "no-store" not in headers["Cache-Control"]:
            raise AssertionError("회원 정산 조회 또는 캐시 정책 오류")
        visit("/bbs/logout.php")
        denied()
        print("GnuBoard install, login, theme, report scope, no-store and logout verified")
    finally:
        server.terminate()
        server.wait(timeout=20)
