import http.client
import ssl
import threading
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


@contextmanager
def portal_tls(address, work):
    class Forward(BaseHTTPRequestHandler):
        def forward(self):
            if urlsplit(self.path).path not in ("/receive.php", "/status.php"):
                self.send_error(404)
                return
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 <= length <= 8192:
                self.send_error(413)
                return
            body = self.rfile.read(length)
            upstream = http.client.HTTPConnection(address, timeout=5)
            try:
                headers = {key: self.headers[key] for key in ("Authorization","Content-Type") if key in self.headers}
                upstream.request(self.command, self.path, body, headers)
                result = upstream.getresponse()
                content = result.read()
                self.send_response(result.status)
                for key in ("Cache-Control","Content-Type"):
                    if result.getheader(key):
                        self.send_header(key,result.getheader(key))
                self.send_header("Content-Length",str(len(content)))
                self.end_headers()
                self.wfile.write(content)
            finally:
                upstream.close()

        do_GET = forward
        do_POST = forward

        def log_message(self, format, *args):
            pass

    with ThreadingHTTPServer(("127.0.0.1",0),Forward) as server:
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(work / "cert.pem",work / "key.pem")
        server.socket = context.wrap_socket(server.socket, server_side=True)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield f"https://127.0.0.1:{server.server_port}/receive.php"
        finally:
            server.shutdown()
            thread.join()
