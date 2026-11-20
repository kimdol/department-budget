from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from oracle_install import installation_sql
from package_release import digest, package
from test_oracle_install import package as test_war


class ReleasePackageTest(unittest.TestCase):
    def test_requires_matching_commit_war_and_schema(self):
        project = Path(__file__).resolve().parents[1]
        with TemporaryDirectory() as temporary:
            root = Path(temporary)
            commit = "a"*40
            war = test_war(root/"app.war", [(1,"select 1 from dual;")])
            checksum = digest(war.read_bytes())
            sql = root/"install.sql"
            report = root/"rollback.txt"
            output = root/"release"
            for recorded_revision, recorded_hash, script in [
                ("b"*40,checksum,installation_sql(war)),
                (commit,"0"*64,installation_sql(war)),
                (commit,checksum,"select 2 from dual;")
            ]:
                report.write_text(f"current {recorded_revision} {recorded_hash}\n")
                sql.write_text(script,encoding="utf-8")
                with self.assertRaises(ValueError):
                    package(project,war,sql,report,commit,output)
                self.assertFalse(output.exists())
            sql.write_text(installation_sql(war),encoding="utf-8")
            package(project,war,sql,report,commit,output)
            expected = {"department-budget.war","install.sql","commit.txt","Caddyfile",
                        "department-budget.service","CheckRuntimeDatabase.java","oracle_install.py"}
            lines = (output/"SHA256SUMS").read_text().splitlines()
            self.assertEqual({line.split()[1] for line in lines},expected)
            for line in lines:
                value, name = line.split()
                self.assertEqual(value,digest((output/name).read_bytes()))
            with self.assertRaises(FileExistsError):
                package(project,war,sql,report,commit,output)


if __name__ == "__main__":
    unittest.main()
