import argparse
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

parser = argparse.ArgumentParser()
parser.add_argument("revision")
args = parser.parse_args()
source = "src/main/java/kr/budget/policy/ExpenseFormula.java"
classes = Path("target/classes").resolve()
jdk = Path(os.environ["JAVA_HOME"]) / "bin"
with TemporaryDirectory() as directory:
    root = Path(directory)
    data = subprocess.check_output(["git", "show", f"{args.revision}:{source}"])
    java = root / "ExpenseFormula.java"
    java.write_bytes(data)
    subprocess.run([str(jdk / "javac"), "-encoding", "UTF-8", "-cp", str(classes),
                    "-d", directory, str(java), "ops/FormulaTiming.java"], check=True)
    subprocess.run([str(jdk / "java"), "-Xms128m", "-Xmx128m", "-cp",
                    os.pathsep.join([directory, str(classes)]), "FormulaTiming"], check=True)
