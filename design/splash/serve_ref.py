"""Tiny static server so the in-app browser can pull the reference image.

  python design/splash/serve_ref.py 8765
"""
import functools
import http.server
import socketserver
import sys
from pathlib import Path


class Handler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write(fmt % args + "\n")


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
root = Path(__file__).resolve().parent / "assets"
handler = functools.partial(Handler, directory=str(root))
socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("127.0.0.1", port), handler) as httpd:
    print(f"serving {root} on http://127.0.0.1:{port}", flush=True)
    httpd.serve_forever()
