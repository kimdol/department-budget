import argparse
import hashlib
import re
import shutil
from pathlib import Path
from tempfile import TemporaryDirectory

from oracle_install import installation_sql


def digest(content):
    return hashlib.sha256(content).hexdigest()


def package(project, war, tested_sql, rollback_report, commit, output):
    label, tested_commit, tested_hash = rollback_report.read_text().splitlines()[0].split()
    if label != "current" or not re.fullmatch("[0-9a-f]{40}", commit) or commit != tested_commit:
        raise ValueError("시험한 커밋과 배포 커밋이 다릅니다.")
    if output.exists():
        raise FileExistsError(output)
    with TemporaryDirectory(dir=output.parent) as temporary:
        bundle = Path(temporary) / "release"
        bundle.mkdir()
        shutil.copyfile(war, bundle / "department-budget.war")
        if digest((bundle / "department-budget.war").read_bytes()) != tested_hash:
            raise ValueError("복귀 시험에 사용한 WAR 해시와 다릅니다.")
        shutil.copyfile(tested_sql, bundle / "install.sql")
        if (bundle / "install.sql").read_text(encoding="utf-8") != installation_sql(bundle / "department-budget.war"):
            raise ValueError("Oracle에서 시험한 설치 SQL과 WAR의 정의가 다릅니다.")
        for name in ["Caddyfile", "department-budget.service", "CheckRuntimeDatabase.java", "oracle_install.py",
                     "Caddyfile.portal", "portal-fpm.conf", "department-budget-portal.service"]:
            shutil.copyfile(project / "ops" / name, bundle / name)
        for name in ["portal/receive.php", "portal/status.php", "portal/mysql/V001__projection.sql"]:
            target = bundle / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(project / name, target)
        (bundle / "commit.txt").write_text(commit+"\n", encoding="ascii", newline="\n")
        files = sorted(file for file in bundle.rglob("*") if file.is_file())
        checksums = [digest(file.read_bytes())+"  "+file.relative_to(bundle).as_posix() for file in files]
        (bundle / "SHA256SUMS").write_text("\n".join(checksums)+"\n", encoding="ascii", newline="\n")
        bundle.rename(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("commit")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    package(root, root/"target/department-budget-0.1.0.war", root/"target/oracle-release/install.sql",
            root/"target/war-rollback/releases.txt", args.commit, args.output)
