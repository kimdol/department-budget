import io
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from zipfile import ZipFile

from oracle_install import installation_sql


def package(path, scripts, batch_sql="create table jobs(id number);\n"):
    batch = io.BytesIO()
    with ZipFile(batch, "w") as archive:
        archive.writestr("org/springframework/batch/core/schema-oracle.sql",batch_sql)
    with ZipFile(path, "w") as war:
        for version, sql in scripts:
            war.writestr(f"WEB-INF/classes/db/V{version:03}__budget.sql",sql)
        war.writestr("WEB-INF/lib/spring-batch-core-test.jar",batch.getvalue())
    return path


class UpgradeTest(unittest.TestCase):
    def test_release_fingerprint_matches_install_despite_line_endings(self):
        with TemporaryDirectory() as folder:
            root = Path(folder)
            prior = package(root/"prior.war",[(1,"select 1 from dual;\r\n")])
            new = package(root/"new.war",[(1,"select 1 from dual;\n"),(2,"select 2 from dual;\n")])
            old_install = installation_sql(prior)
            old_hash = old_install.split("phase='READY',fingerprint='")[1].split("'")[0]
            upgrade = installation_sql(new,prior)
            self.assertIn("and last_version=1 and fingerprint='"+old_hash+"'",upgrade)
            self.assertNotIn("prompt V001",upgrade)
            self.assertIn("prompt V002",upgrade)
            self.assertNotIn("create table jobs",upgrade)

    def test_rejects_changed_history_batch_schema_and_missing_migrations(self):
        with TemporaryDirectory() as folder:
            root = Path(folder)
            prior = package(root/"prior.war",[(1,"old")])
            for scripts,batch in [([(1,"modified"),(2,"new")],"create table jobs(id number);\n"),
                                  ([(1,"old"),(2,"new")],"changed batch"),
                                  ([(1,"old"),(3,"new")],"create table jobs(id number);\n")]:
                with self.subTest(scripts=scripts,batch=batch), self.assertRaises(ValueError):
                    installation_sql(package(root/"candidate.war",scripts,batch),prior)
            with self.assertRaises(ValueError):
                installation_sql(prior,prior)


if __name__ == "__main__":
    unittest.main()
