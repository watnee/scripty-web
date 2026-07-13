#!/usr/bin/env python3
"""Tiny reverse proxy so the Claude Code preview panel can attach to an
already-running Scripty dev server (scripts/dev-server.sh) without owning it.

Listens on $PORT (assigned by the preview panel) and forwards every request to
http://localhost:$SCRIPTY_TARGET_PORT (default 8091).
"""
import os
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LISTEN_PORT = int(os.environ.get("PORT", "9091"))
TARGET_PORT = int(os.environ.get("SCRIPTY_TARGET_PORT", "8092"))
TARGET = f"http://localhost:{TARGET_PORT}"

HOP_BY_HOP = {
    "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
    "te", "trailers", "transfer-encoding", "upgrade",
}


class ProxyHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _forward(self):
        body = None
        length = self.headers.get("Content-Length")
        if length:
            body = self.rfile.read(int(length))
        url = TARGET + self.path
        req = urllib.request.Request(url, data=body, method=self.command)
        for key, value in self.headers.items():
            if key.lower() not in HOP_BY_HOP and key.lower() != "host":
                req.add_header(key, value)
        req.add_header("Host", f"localhost:{TARGET_PORT}")
        opener = urllib.request.build_opener()  # no redirect-following: pass 3xx through
        opener.handlers = [h for h in opener.handlers
                           if h.__class__.__name__ != "HTTPRedirectHandler"]
        try:
            resp = opener.open(req, timeout=120)
        except urllib.error.HTTPError as e:
            resp = e
        except Exception as e:  # target down
            self.send_response(502)
            self.send_header("Content-Type", "text/plain")
            msg = f"proxy error: {e}".encode()
            self.send_header("Content-Length", str(len(msg)))
            self.end_headers()
            self.wfile.write(msg)
            return
        data = resp.read()
        self.send_response(resp.status if hasattr(resp, "status") else resp.code)
        for key, value in resp.headers.items():
            if key.lower() not in HOP_BY_HOP and key.lower() != "content-length":
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    do_GET = do_POST = do_PUT = do_DELETE = do_PATCH = do_HEAD = do_OPTIONS = _forward

    def log_message(self, fmt, *args):
        pass


if __name__ == "__main__":
    print(f"preview proxy :{LISTEN_PORT} -> {TARGET}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", LISTEN_PORT), ProxyHandler).serve_forever()
