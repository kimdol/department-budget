import argparse
import csv
import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import threading
import time
from urllib.error import URLError
from urllib.request import urlopen
import zipfile

parser = argparse.ArgumentParser(description="Measure Java process memory on Windows using the verified WAR.")
parser.add_argument("--bundle", type=Path, required=True)
parser.add_argument("--java", type=Path, required=True)
parser.add_argument("--h2", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
work = root / "target" / "micro-memory"
work.mkdir(parents=True, exist_ok=True)
war = work / "department-budget.war"
with zipfile.ZipFile(args.bundle) as release:
    war.write_bytes(release.read("department-budget.war"))
java = str(args.java.resolve())
h2 = args.h2.resolve()
schema = ",".join("classpath:db/" + path.name for path in sorted((root / "src/main/resources/db").glob("*.sql")))

class MemoryCounters(ctypes.Structure):
    _fields_ = [("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD)] + [
        (name, ctypes.c_size_t) for name in
        ("PeakWorkingSetSize", "WorkingSetSize", "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage",
         "QuotaPeakNonPagedPoolUsage", "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage", "PrivateUsage")
    ]

memory_info = ctypes.WinDLL("psapi", use_last_error=True).GetProcessMemoryInfo
memory_info.argtypes = [wintypes.HANDLE, ctypes.POINTER(MemoryCounters), wintypes.DWORD]
memory_info.restype = wintypes.BOOL

env = dict(os.environ, SERVER_ADDRESS="127.0.0.1", SERVER_PORT="18753", HTTPS_ONLY="false",
           SERVER_SERVLET_SESSION_COOKIE_SECURE="false", TEST_EXTERNAL_RELEASE="1",
           SPRING_DATASOURCE_URL="jdbc:h2:mem:micro;MODE=Oracle", SPRING_DATASOURCE_USERNAME="sa",
           SPRING_DATASOURCE_PASSWORD="local-test", SPRING_SQL_INIT_MODE="always",
           SPRING_SQL_INIT_SCHEMA_LOCATIONS=schema, SPRING_BATCH_JDBC_INITIALIZE_SCHEMA="always")
for variable in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS", "TEST_DEPLOYMENT_URL", "TEST_TLS_SPKI", "TEST_PHP_ADDRESS"):
    env.pop(variable, None)
options = {
    "baseline": ([], {}),
    "micro": (["-Xms64m", "-Xmx256m", "-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=128m", "-XX:ReservedCodeCacheSize=48m"],
              {"SERVER_TOMCAT_THREADS_MAX": "32", "SERVER_TOMCAT_THREADS_MIN_SPARE": "4",
               "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE": "4", "SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE": "1"})
}
def measure(label, iteration):
    flags, settings = options[label]
    command = [java, "-XX:ActiveProcessorCount=1", *flags, "-cp", str(war)+os.pathsep+str(h2),
               "org.springframework.boot.loader.launch.WarLauncher", "--spring.profiles.active=proxy"]
    start = time.monotonic()
    with (work / (f"{iteration}-{label}-java.log")).open("w", encoding="utf-8") as app_log:
        app = subprocess.Popen(command, env=env | settings, stdout=app_log, stderr=subprocess.STDOUT)
        done = threading.Event()
        data = []
        def sample():
            while not done.is_set():
                counters = MemoryCounters()
                counters.cb = ctypes.sizeof(counters)
                if memory_info(int(app._handle), ctypes.byref(counters), counters.cb):
                    data.append((time.monotonic()-start, counters.WorkingSetSize, counters.PrivateUsage))
                done.wait(0.1)
        sampler = threading.Thread(target=sample)
        sampler.start()
        try:
            while time.monotonic()-start < 75 and app.poll() is None:
                try:
                    with urlopen("http://127.0.0.1:18753/", timeout=1) as page:
                        if page.status == 200:
                            break
                except (OSError, URLError):
                    time.sleep(0.1)
            else:
                raise RuntimeError("Application did not start; see "+str(app_log.name))
            ready = time.monotonic()-start
            with (work / (f"{iteration}-{label}-browser.log")).open("w", encoding="utf-8") as browser_log:
                subprocess.run(["node", "node_modules/@playwright/test/cli.js", "test", "claims.spec.ts", "--reporter=list"],
                               cwd=root/"frontend", env=env, stdout=browser_log, stderr=subprocess.STDOUT,
                               timeout=70, check=True)
        finally:
            done.set()
            sampler.join()
            app.terminate()
            app.wait(timeout=15)
    result = {"iteration":iteration,"profile":label,"readySeconds":round(ready,3),"sampleCount":len(data),
              "peakWorkingSetBytes":max(row[1] for row in data),"peakPrivateBytes":max(row[2] for row in data),
              "jvmFlags":flags,"environment":settings,"samples":data}
    print(label, "ready",result["readySeconds"],"max working set MiB",round(result["peakWorkingSetBytes"]/2**20,1),
          "max private MiB",round(result["peakPrivateBytes"]/2**20,1),flush=True)
    return result

def save_results(directory, result):
    with (directory / "samples.csv").open("w", encoding="utf-8", newline="") as file:
        rows = csv.writer(file, lineterminator="\n")
        rows.writerow(["iteration", "profile", "elapsed_seconds", "working_set_bytes", "private_bytes"])
        for run in result["runs"]:
            rows.writerows((run["iteration"], run["profile"], *row) for row in run.pop("samples"))
    (directory / "results.json").write_text(json.dumps(result, indent=2)+"\n", encoding="utf-8")


if __name__ == "__main__":
    order = ("baseline", "micro", "micro", "baseline")
    result = {"platform":platform.platform(),"availableLogicalCpus":os.cpu_count(),"activeProcessorCount":1,
              "javaVersion":subprocess.check_output([java,"-version"],stderr=subprocess.STDOUT,text=True),
              "warSha256":hashlib.sha256(war.read_bytes()).hexdigest(),"schema":"fresh H2 in Oracle mode",
              "sampleIntervalSeconds":0.1,"runs":[measure(label, iteration) for iteration, label in enumerate(order, 1)]}
    save_results(work, result)
