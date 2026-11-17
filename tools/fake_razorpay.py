#!/usr/bin/env python3
"""A pretend Razorpay for tests. It is NOT Razorpay, but its error words and its link format were copied from real Razorpay test-mode answers (16 Nov 2026).

Run:    python3 tools/fake_razorpay.py 8099
Use:    RAZORPAY_URL=http://localhost:8099 RAZORPAY_KEY_ID=rzp_test_pretend RAZORPAY_KEY_SECRET=pretend-secret

It checks the same rules the real page lists: the login (key id and secret), amount at least 100,
expire_by at least 15 minutes ahead, reference_id unique and at most 40 characters.

Remote control (not part of Razorpay):
    POST /_control?delay=8      every answer waits 8 seconds (to test the timeout)
    POST /_control?fail=500     every answer is this status (0 = normal)
    POST /_paid/<plink id>      the link becomes paid (for the next class)
    GET  /_count                how many links exist
"""
import base64
import json
import random
import string
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

KEY_ID, KEY_SECRET = "rzp_test_pretend", "pretend-secret"
links = {}          # plink id -> link
references = set()  # reference ids already used
state = {"delay": 0.0, "fail": 0}


def error(description, field=None):
    body = {"error": {"code": "BAD_REQUEST_ERROR", "description": description, "source": "business",
                      "step": "payment_initiation", "reason": "input_validation_failed", "metadata": {}}}
    if field:
        body["error"]["field"] = field
    return body


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def read_body(self):
        # Java's HTTP client sends the body in pieces (Transfer-Encoding: chunked). A real server reads that too.
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            body = b""
            while True:
                size = int(self.rfile.readline().strip() or b"0", 16)
                if size == 0:
                    self.rfile.readline()
                    return body
                body += self.rfile.read(size)
                self.rfile.readline()
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def authorised(self):
        header = self.headers.get("Authorization", "")
        if not header.startswith("Basic "):
            return False
        try:
            return base64.b64decode(header[6:]).decode() == f"{KEY_ID}:{KEY_SECRET}"
        except Exception:
            return False

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/_count":
            return self.send(200, {"count": len(links)})
        if path.startswith("/v1/payment_links/"):
            if not self.authorised():
                return self.send(401, error("The api key provided is invalid"))
            link = links.get(path.rsplit("/", 1)[1])
            return self.send(200, link) if link else self.send(400, error("The id provided does not exist"))
        self.send(404, error("not found"))

    def do_POST(self):
        parsed = urlparse(self.path)
        raw = self.read_body()
        if parsed.path == "/_control":
            q = parse_qs(parsed.query)
            state["delay"] = float(q.get("delay", [state["delay"]])[0])
            state["fail"] = int(q.get("fail", [state["fail"]])[0])
            return self.send(200, state)
        if parsed.path.startswith("/_paid/"):
            links[parsed.path.rsplit("/", 1)[1]]["status"] = "paid"
            return self.send(200, {"ok": True})
        if parsed.path.rstrip("/") != "/v1/payment_links":
            return self.send(404, error("not found"))

        time.sleep(state["delay"])
        if state["fail"]:
            return self.send(state["fail"], {"error": {"code": "SERVER_ERROR", "description": "pretend failure"}})
        if not self.authorised():
            return self.send(401, error("The api key provided is invalid"))
        try:
            req = json.loads(raw)
        except Exception:
            return self.send(400, error("body is not JSON"))

        amount = req.get("amount")
        if not isinstance(amount, int) or amount < 100:
            return self.send(400, error("amount: must be at least 100", "amount"))
        expire_by = req.get("expire_by")
        if expire_by is not None and expire_by < time.time() + 15 * 60:
            return self.send(400, error("expire_by: timestamp must be atleast 15 minutes in future.", "expire_by"))
        ref = req.get("reference_id")
        if ref is not None and len(ref) > 40:
            return self.send(400, error("reference_id: must not be longer than 40 characters", "reference_id"))
        if ref is not None and ref in references:
            return self.send(400, error(f"payment link with given reference_id: {ref} already exists. Please create a payment link with a different reference_id"))
        if not str(req.get("callback_url", "http://x")).startswith(("http://", "https://")):
            return self.send(400, error("callback_url: not a valid URL", "callback_url"))

        pid = "plink_" + "".join(random.choices(string.ascii_letters + string.digits, k=14))
        short = "https://rzp.io/rzp/" + "".join(random.choices(string.ascii_letters + string.digits, k=8))
        link = {"id": pid, "short_url": short, "status": "created", "amount": amount,
                "currency": req.get("currency", "INR"), "reference_id": ref, "expire_by": expire_by,
                "description": req.get("description"), "callback_url": req.get("callback_url"),
                "accept_partial": False, "notes": None, "created_at": int(time.time())}
        links[pid] = link
        if ref is not None:
            references.add(ref)
        self.send(200, link)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1]) if len(sys.argv) > 1 else 8099), Handler).serve_forever()
