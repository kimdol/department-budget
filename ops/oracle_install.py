import argparse
import io
import re
from pathlib import Path
from zipfile import ZipFile


def installation_sql(war_path):
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
        blocks = ["whenever oserror exit failure", "whenever sqlerror exit failure rollback",
                  "set define off", "set sqlblanklines on",
                  "declare object_count number; begin select count(*) into object_count from user_objects; "
                  "if object_count > 0 then raise_application_error(-20001,'Empty schema required'); end if; end;",
                  "/"]
        for name in scripts:
            blocks.extend(["prompt " + Path(name).name, war.read(name).decode()])
        blocks.extend(["prompt " + Path(libraries[0]).name, batch_sql, "exit success commit"])
        return "\n".join(blocks) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("war", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    script = installation_sql(args.war)
    with args.output.open("x", encoding="utf-8", newline="\n") as output:
        output.write(script)


if __name__ == "__main__":
    main()
