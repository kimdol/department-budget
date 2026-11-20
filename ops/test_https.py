import base64
import hashlib
import os
from pathlib import Path
import secrets
import subprocess
import tempfile


def main():
    root = Path(__file__).resolve().parents[1]
    openssl = os.environ.get("OPENSSL", "openssl")
    with tempfile.TemporaryDirectory() as directory:
        work = Path(directory)
        password = secrets.token_hex(24)
        env = dict(os.environ, SERVER_SSL_KEY_STORE_PASSWORD=password)
        def run(*args, data=None):
            return subprocess.run([openssl, *args], cwd=work, env=env, input=data,
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout
        (work / "cert.cnf").write_text("[req]\ndistinguished_name=subject\nx509_extensions=extensions\nprompt=no\n"
            "[subject]\nCN=Budget Loopback Test\n[extensions]\nsubjectAltName=IP:127.0.0.1\n"
            "basicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n", encoding="ascii")
        run("req", "-x509", "-newkey", "rsa:2048", "-sha256", "-nodes", "-days", "1", "-config", "cert.cnf",
            "-keyout", "key.pem", "-out", "cert.pem")
        run("pkcs12", "-export", "-inkey", "key.pem", "-in", "cert.pem", "-out", "server.p12",
            "-passout", "env:SERVER_SSL_KEY_STORE_PASSWORD")
        public_key = run("x509", "-in", "cert.pem", "-pubkey", "-noout")
        spki = run("pkey", "-pubin", "-outform", "der", data=public_key)
        env.update(TEST_TLS_SPKI=base64.b64encode(hashlib.sha256(spki).digest()).decode(),
                   SERVER_SSL_KEY_STORE=(work / "server.p12").as_uri(), SERVER_SSL_KEY_STORE_TYPE="PKCS12",
                   NODE_EXTRA_CA_CERTS=str(work / "cert.pem"), TEST_REUSE_LOCAL_SERVER="0")
        cli = root / "frontend/node_modules/@playwright/test/cli.js"
        subprocess.run(["node", str(cli), "test", "--reporter=list"], cwd=root / "frontend", env=env, check=True)


if __name__ == "__main__":
    main()
