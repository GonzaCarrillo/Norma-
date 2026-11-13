#!/usr/bin/env python3
import json
import os
import re
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import urllib.parse

WORKTREE_DIR = "/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront"
EVIDENCE_DIR = os.path.join(WORKTREE_DIR, "docs/orchestration/issue-6-corrective-evidence/builder/sse")
RUNTIME_DIR = os.path.join(WORKTREE_DIR, "target/sse-runtime")

os.makedirs(EVIDENCE_DIR, exist_ok=True)
os.makedirs(RUNTIME_DIR, exist_ok=True)

JAR_PATH = os.path.join(WORKTREE_DIR, "target/taller-1.0.0.jar")
if not os.path.exists(JAR_PATH):
    print(f"JAR not found: {JAR_PATH}")
    sys.exit(1)

db_dir = os.path.join(RUNTIME_DIR, "db")
uploads_dir = os.path.join(RUNTIME_DIR, "uploads")
tmp_dir = os.path.join(RUNTIME_DIR, "tmp")
os.makedirs(db_dir, exist_ok=True)
os.makedirs(uploads_dir, exist_ok=True)
os.makedirs(tmp_dir, exist_ok=True)

db_path = os.path.join(db_dir, "taller.db")
if os.path.exists(db_path):
    os.remove(db_path)

log_path = os.path.join(RUNTIME_DIR, "sse_repro_server.log")
log_file = open(log_path, "w")

cmd = [
    "/home/gonzalocar/.jdks/ms-25.0.4.1/bin/java",
    "--enable-native-access=ALL-UNNAMED",
    f"-Djava.io.tmpdir={tmp_dir}",
    f"-Dnorma.uploads={uploads_dir}",
    f"-Dspring.datasource.url=jdbc:sqlite:{db_path}",
    "-Dserver.port=0",
    "-Dgroq.api.key=",
    "-jar",
    JAR_PATH
]

print("Starting Spring Boot server on dynamic port...")
proc = subprocess.Popen(cmd, stdout=log_file, stderr=subprocess.STDOUT, preexec_fn=os.setsid)

port = None
start_time = time.time()
while time.time() - start_time < 45:
    time.sleep(0.5)
    if os.path.exists(log_path):
        with open(log_path, "r") as f:
            content = f.read()
            m = re.search(r"Tomcat started on port[^\d]*(\d+)", content)
            if m:
                port = int(m.group(1))
                break

if not port:
    print("Could not find dynamic port within 45s.")
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
    sys.exit(1)

print(f"Spring Boot started on port {port}")

results = {
    "port": port,
    "runs": [],
    "stack_traces_observed": []
}

def send_multipart_message(session_id, text):
    boundary = "----WebKitFormBoundaryRepro123"
    body = (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="sessionId"\r\n\r\n'
        f"{session_id}\r\n"
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="text"\r\n\r\n'
        f"{text}\r\n"
        f"--{boundary}--\r\n"
    ).encode("utf-8")
    
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}/api/bot/message",
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            return resp.status, resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")
    except Exception as e:
        return 0, str(e)

try:
    # Run test iterations with different post-close delays
    delays_ms = [0, 5, 10, 20, 50, 100, 250]
    for idx, delay_ms in enumerate(delays_ms):
        sess = f"repro_sess_{idx}_{int(time.time())}"
        
        # 1. Open raw TCP socket for SSE
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect(("127.0.0.1", port))
        req = f"GET /api/catalog/stream?sessionId={sess} HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nAccept: text/event-stream\r\nConnection: keep-alive\r\n\r\n"
        s.sendall(req.encode("utf-8"))
        
        # Read until initial connected event
        s.settimeout(3.0)
        resp_data = b""
        try:
            while b"conectado" not in resp_data:
                chunk = s.recv(1024)
                if not chunk:
                    break
                resp_data += chunk
        except Exception:
            pass
        
        connected = b"conectado" in resp_data
        
        # 2. Abruptly close the SSE socket
        s.close()
        
        # Delay before sending mutation POST
        if delay_ms > 0:
            time.sleep(delay_ms / 1000.0)
            
        # 3. Send proposal message
        prop_status, prop_body = send_multipart_message(sess, "A la número 1 subila a treinta y cinco mil pesos")
        
        # 4. Send confirmation message
        yes_status, yes_body = send_multipart_message(sess, "Sí")
        
        run_record = {
            "iteration": idx,
            "delay_ms": delay_ms,
            "connected_event_received": connected,
            "proposal_status": prop_status,
            "proposal_body": prop_body[:100] if prop_body else None,
            "confirmation_status": yes_status,
            "confirmation_body": yes_body[:100] if yes_body else None,
            "reproduced_500": (prop_status == 500 or yes_status == 500)
        }
        results["runs"].append(run_record)
        print(f"Delay {delay_ms}ms: prop_status={prop_status}, yes_status={yes_status}, reproduced_500={run_record['reproduced_500']}")

finally:
    print("Stopping Spring Boot...")
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
    try:
        proc.wait(timeout=10)
    except Exception:
        os.killpg(os.getpgid(proc.pid), signal.SIGKILL)

# Inspect log for exact stack traces
with open(log_path, "r", encoding="utf-8", errors="replace") as f:
    log_content = f.read()

stacks = []
for block in re.split(r"(?=\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})", log_content):
    if "ERROR" in block and ("AsyncRequestNotUsableException" in block or "IllegalStateException" in block or "ApiErrors" in block):
        stacks.append(block.strip())

results["stack_traces_observed"] = stacks
results["total_500_count"] = sum(1 for r in results["runs"] if r["reproduced_500"])

out_json = os.path.join(EVIDENCE_DIR, "sse_repro_results.json")
with open(out_json, "w", encoding="utf-8") as f:
    json.dump(results, f, indent=2, ensure_ascii=False)

print(f"Saved reproduction results to {out_json}")
print(f"Total 500 errors observed: {results['total_500_count']}")
print(f"Total stack traces found in log: {len(stacks)}")
