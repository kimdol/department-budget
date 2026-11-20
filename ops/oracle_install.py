import argparse
import hashlib
import io
import re
from pathlib import Path
from zipfile import ZipFile


def schema_files(war_path):
    with ZipFile(war_path) as war:
        scripts = sorted(name for name in war.namelist()
                         if re.fullmatch(r"WEB-INF/classes/db/V\d{3}__\w+\.sql", name))
        versions = [int(Path(name).name[1:4]) for name in scripts]
        if not scripts or versions != list(range(1, len(scripts) + 1)):
            raise ValueError("업무 스키마 마이그레이션 번호를 확인하세요.")
        libraries = [name for name in war.namelist()
                     if re.fullmatch(r"WEB-INF/lib/spring-batch-core-[^/]+\.jar", name)]
        if len(libraries) != 1:
            raise ValueError("Spring Batch Core 라이브러리 한 개가 필요합니다.")
        with ZipFile(io.BytesIO(war.read(libraries[0]))) as batch:
            batch_sql = batch.read("org/springframework/batch/core/schema-oracle.sql").decode()
        migrations = [(Path(name).name,war.read(name).decode().replace("\r\n","\n")) for name in scripts]
        return migrations, batch_sql.replace("\r\n","\n")


def fingerprint(migrations, batch_sql):
    digest = hashlib.sha256(batch_sql.encode())
    for name, sql in migrations:
        digest.update(b"\0"+name.encode()+b"\0"+sql.encode())
    return digest.hexdigest()


def installation_sql(war_path, upgrade_from=None):
    migrations, batch_sql = schema_files(war_path)
    blocks = ["whenever oserror exit failure", "whenever sqlerror exit failure rollback",
              "set define off", "set sqlblanklines on"]
    start = 0
    if upgrade_from is None:
        blocks.extend(["declare object_count number; begin select count(*) into object_count from user_objects; "
                       "if object_count > 0 then raise_application_error(-20001,'Empty schema required'); end if; end;", "/",
                       "create table budget_schema_release (slot number primary key check(slot=1), "
                       "last_version number not null, phase varchar2(12) not null, fingerprint varchar2(64));",
                       "insert into budget_schema_release values(1,0,'INSTALLING',null);", "commit;"])
    else:
        prior, prior_batch = schema_files(upgrade_from)
        start = len(prior)
        if migrations[:start] != prior or batch_sql != prior_batch or len(migrations) <= start:
            raise ValueError("이전 WAR와 같은 마이그레이션·배치 정의 뒤에 새 업무 버전이 있어야 합니다.")
        previous_hash = fingerprint(prior,prior_batch)
        blocks.extend(["begin update budget_schema_release set phase='UPGRADING' where slot=1 and phase='READY' "
                       f"and last_version={start} and fingerprint='{previous_hash}'; "
                       "if sql%rowcount != 1 then raise_application_error(-20002,'Unexpected source release'); end if; end;",
                       "/", "commit;"])
    for version, (name,sql) in enumerate(migrations[start:],start+1):
        blocks.extend(["prompt " + name,sql,
                       f"update budget_schema_release set last_version={version} where slot=1;", "commit;"])
    if upgrade_from is None:
        blocks.extend(["prompt Spring Batch Oracle schema",batch_sql])
    blocks.extend([f"update budget_schema_release set phase='READY',fingerprint='{fingerprint(migrations,batch_sql)}' where slot=1;",
                   "exit success commit"])
    return "\n".join(blocks) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("war", type=Path)
    parser.add_argument("output", type=Path, nargs="?")
    parser.add_argument("--upgrade-from", type=Path)
    parser.add_argument("--release-id", action="store_true")
    args = parser.parse_args()
    if args.release_id:
        if args.output or args.upgrade_from:
            parser.error("--release-id accepts only a WAR")
        migrations, batch_sql = schema_files(args.war)
        print(f"{len(migrations)}:{fingerprint(migrations, batch_sql)}")
        return
    if args.output is None:
        parser.error("installation SQL output path required")
    script = installation_sql(args.war,args.upgrade_from)
    with args.output.open("x", encoding="utf-8", newline="\n") as output:
        output.write(script)


if __name__ == "__main__":
    main()
