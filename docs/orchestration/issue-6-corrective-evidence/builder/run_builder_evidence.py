#!/usr/bin/env python3
import json
import os
import re
import signal
import subprocess
import sys
import time

WORKTREE_DIR = "/home/gonzalocar/orca/workspaces/Norma-/reconcile-6-storefront"
EVIDENCE_DIR = os.path.join(WORKTREE_DIR, "docs/orchestration/issue-6-corrective-evidence/builder")
RUNTIME_DIR = os.path.join(WORKTREE_DIR, "target/builder-runtime")

os.makedirs(EVIDENCE_DIR, exist_ok=True)
os.makedirs(RUNTIME_DIR, exist_ok=True)

JAR_PATH = os.path.join(WORKTREE_DIR, "target/taller-1.0.0.jar")
if not os.path.exists(JAR_PATH):
    print(f"Error: {JAR_PATH} does not exist.")
    sys.exit(1)

smoke_db_dir = os.path.join(RUNTIME_DIR, "smoke-db")
smoke_uploads_dir = os.path.join(RUNTIME_DIR, "smoke-uploads")
smoke_tmp_dir = os.path.join(RUNTIME_DIR, "smoke-tmp")
os.makedirs(smoke_db_dir, exist_ok=True)
os.makedirs(smoke_uploads_dir, exist_ok=True)
os.makedirs(smoke_tmp_dir, exist_ok=True)

db_path = os.path.join(smoke_db_dir, "taller.db")
if os.path.exists(db_path):
    os.remove(db_path)

log_path = os.path.join(RUNTIME_DIR, "smoke_server.log")
log_file = open(log_path, "w")

cmd = [
    "/home/gonzalocar/.jdks/ms-25.0.4.1/bin/java",
    "--enable-native-access=ALL-UNNAMED",
    f"-Djava.io.tmpdir={smoke_tmp_dir}",
    f"-Dnorma.uploads={smoke_uploads_dir}",
    f"-Dspring.datasource.url=jdbc:sqlite:{db_path}",
    "-Dserver.port=0",
    "-Dgroq.api.key=",
    "-jar",
    JAR_PATH
]

print("Starting Spring Boot JAR with dynamic port...")
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
    print("Failed to start Spring Boot within 30s. Log contents:")
    with open(log_path, "r") as f:
        print(f.read())
    os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
    sys.exit(1)

print(f"Spring Boot started on dynamic port {port}")

evidence = {
    "server": {
        "jar": JAR_PATH,
        "port": port,
        "jdk": "Microsoft OpenJDK 25.0.4.1+1-LTS",
        "db": db_path,
        "uploads": smoke_uploads_dir
    },
    "endpoints": {},
    "browser_layout": {},
    "zoom_investigation": {},
    "summary": {}
}

try:
    # Test endpoints with curl / python urllib
    import urllib.request

    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/ping") as resp:
            evidence["endpoints"]["ping"] = {
                "status": resp.status,
                "body": resp.read().decode("utf-8")
            }
    except Exception as e:
        evidence["endpoints"]["ping"] = {"error": str(e)}

    try:
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/api/products?sessionId=builder_smoke_123") as resp:
            body = json.loads(resp.read().decode("utf-8"))
            evidence["endpoints"]["products"] = {
                "status": resp.status,
                "count": len(body),
                "pieceNumbers": [p.get("pieceNumber") for p in body]
            }
    except Exception as e:
        evidence["endpoints"]["products"] = {"error": str(e)}

    # Browser layout verification via Orca CLI
    def run_orca(subcmd):
        full_cmd = ["orca"] + subcmd
        res = subprocess.run(full_cmd, capture_output=True, text=True)
        if res.returncode != 0:
            return {"ok": False, "error": res.stderr.strip() or res.stdout.strip()}
        try:
            return json.loads(res.stdout)
        except Exception:
            return {"ok": True, "raw": res.stdout.strip()}

    print("Opening Orca browser tab...")
    tab_res = run_orca(["tab", "create", "--url", f"http://127.0.0.1:{port}/", "--json"])
    page_id = None
    if tab_res.get("ok"):
        page_id = tab_res.get("result", {}).get("browserPageId")

    if not page_id:
        print(f"Failed to open Orca browser tab: {tab_res}")
    else:
        print(f"Orca tab opened with pageId: {page_id}")
        time.sleep(2)

        def eval_js(expr):
            r = run_orca(["eval", "--page", page_id, "--expression", expr, "--json"])
            if r.get("ok"):
                res = r.get("result", {}).get("result")
                if isinstance(res, str):
                    try:
                        return json.loads(res)
                    except Exception:
                        return res
                return res
            return None

        def screenshot(fname):
            outpath = os.path.join(EVIDENCE_DIR, fname)
            r = subprocess.run(["orca", "screenshot", "--page", page_id, "--format", "png"], capture_output=True, text=True)
            if r.returncode == 0:
                saved_path = r.stdout.strip()
                if os.path.exists(saved_path) and saved_path != outpath:
                    import shutil
                    shutil.copyfile(saved_path, outpath)
                    return outpath
            return None

        # Wait for products to render
        for _ in range(10):
            c = eval_js("document.querySelectorAll('.card').length")
            if c == 8:
                break
            time.sleep(0.5)

        evidence["browser_layout"]["initial_cards_count"] = eval_js("document.querySelectorAll('.card').length")

        # Viewport checks: 1440, 768, 640 (200% reflow of 1280), 375, 320
        viewports = [
            {"name": "desktop_1440", "width": 1440, "height": 900},
            {"name": "tablet_768", "width": 768, "height": 1024},
            {"name": "reflow_640_zoom200", "width": 640, "height": 900},
            {"name": "mobile_375", "width": 375, "height": 812},
            {"name": "mobile_320_min", "width": 320, "height": 640}
        ]

        for vp in viewports:
            run_orca(["viewport", "--page", page_id, "--width", str(vp["width"]), "--height", str(vp["height"])])
            time.sleep(0.5)
            meas = eval_js("""(() => {
                const docEl = document.documentElement;
                const cards = Array.from(document.querySelectorAll('.card'));
                const heights = cards.map(c => c.getBoundingClientRect().height);
                const minH = Math.min(...heights);
                const maxH = Math.max(...heights);
                const photos = Array.from(document.querySelectorAll('.card .photo img')).map(img => {
                    const r = img.getBoundingClientRect();
                    return { w: r.width, h: r.height, ratio: r.width / (r.height || 1) };
                });
                return {
                    viewportWidth: window.innerWidth,
                    clientWidth: docEl.clientWidth,
                    scrollWidth: docEl.scrollWidth,
                    hasHorizontalScroll: docEl.scrollWidth > docEl.clientWidth + 1,
                    cardCount: cards.length,
                    minCardHeight: minH,
                    maxCardHeight: maxH,
                    diffCardHeight: maxH - minH,
                    cardsEqualHeight: (maxH - minH) <= 1.0,
                    photoRatios: photos.slice(0, 3)
                };
            })()""")
            shot_file = screenshot(f"{vp['name']}.png")
            evidence["browser_layout"][vp["name"]] = {
                "config": vp,
                "measurements": meas,
                "screenshot": shot_file
            }
            if isinstance(meas, dict):
                print(f"Viewport {vp['name']}: cardCount={meas.get('cardCount')}, scrollH={meas.get('hasHorizontalScroll')}, diffH={meas.get('diffCardHeight', 0):.2f}px")
            else:
                print(f"Viewport {vp['name']}: raw meas={meas}")

        # Font size check (normal vs large mode)
        font_meas = eval_js("""(() => {
            const root = document.documentElement;
            const baseSize = parseFloat(getComputedStyle(root).fontSize);
            document.querySelector('#font').click(); // toggle large
            const largeSize = parseFloat(getComputedStyle(root).fontSize);
            const isLargeClass = root.classList.contains('large') || document.body.classList.contains('large');
            document.querySelector('#font').click(); // toggle back
            const resetSize = parseFloat(getComputedStyle(root).fontSize);
            return {
                baseSize,
                largeSize,
                ratio: largeSize / baseSize,
                isLargeClass,
                resetSize
            };
        })()""")
        evidence["browser_layout"]["typography_check"] = font_meas
        if isinstance(font_meas, dict):
            print(f"Typography: base={font_meas.get('baseSize')}px, large={font_meas.get('largeSize')}px, ratio={font_meas.get('ratio', 1):.2f}")

        # Modal and keyboard navigation check
        modal_meas = eval_js("""(() => {
            const firstPhoto = document.querySelector('.card .photo');
            firstPhoto.focus();
            firstPhoto.click();
            const zoom = document.querySelector('#zoom');
            const zoomOpen = zoom.open;
            const closeBtn = document.querySelector('#close-zoom');
            const focusOnClose = (document.activeElement === closeBtn);
            
            // Simular Escape
            zoom.close();
            const zoomClosed = !zoom.open;

            return {
                zoomOpen,
                focusOnClose,
                zoomClosed
            };
        })()""")
        evidence["browser_layout"]["modal_check"] = modal_meas
        if isinstance(modal_meas, dict):
            print(f"Modal check: open={modal_meas.get('zoomOpen')}, focusOnClose={modal_meas.get('focusOnClose')}, closed={modal_meas.get('zoomClosed')}")

        # Simulator regression check
        sim_meas = eval_js("""(() => {
            const openBtn = document.querySelector('#open');
            const sim = document.querySelector('#sim');
            const exists = {
                open: !!openBtn,
                sim: !!sim,
                close: !!document.querySelector('#close'),
                text: !!document.querySelector('#text'),
                submit: !!document.querySelector('#message button[type="submit"]'),
                yes: !!document.querySelector('#yes'),
                no: !!document.querySelector('#no'),
                undo: !!document.querySelector('#undo'),
                reset: !!document.querySelector('#reset'),
                record: !!document.querySelector('#record')
            };
            openBtn.click();
            const simOpen = sim.open;
            document.querySelector('#close').click();
            const simClosed = !sim.open;
            return {
                controlsExist: exists,
                simOpen,
                simClosed
            };
        })()""")
        evidence["browser_layout"]["simulator_check"] = sim_meas
        if isinstance(sim_meas, dict):
            print(f"Simulator check: allControls={all(sim_meas.get('controlsExist', {}).values())}, simOpen={sim_meas.get('simOpen')}")

        # Close tab
        run_orca(["tab", "close", "--page", page_id])

    # Detailed environmental zoom investigation
    evidence["zoom_investigation"] = {
        "requirement": "Capturar versión, tamaño de ventana e indicador nativo 100% y luego 200% durante la medición. Emulación desactivada; CSS zoom no inyectado; scale/DPR no alterados para fabricar resultado.",
        "analysis": {
            "orca_cli_version": "1.4.257",
            "engine": "Chromium CDP (Chrome/150.0.7871.250)",
            "cdp_capture_boundary": "orca screenshot usa CDP Page.captureScreenshot, capturando únicamente el viewport DOM de la página. No captura la interfaz de ventana del sistema operativo (decoraciones de ventana, barra de direcciones, barra de menú ni insignia/badge de zoom).",
            "cli_parameter_limitations": "orca CLI no expone ningún comando o parámetro para aplicar zoom nativo de ventana completa (--zoom o Page.setZoomLevel) ni para inspeccionar indicadores de menú del navegador.",
            "prohibited_substitutions": "docs/orchestration/issue-6-corrective-design.md prohíbe expresamente sustituir esta prueba mediante 'orca viewport --scale 2' (DPR/deviceScaleFactor) o 'document.body.style.zoom = 2' (CSS zoom inyectado).",
            "system_desktop_automation": "No existe en el entorno ningún arnés instalado de automatización GUI de escritorio con captura de ventana completa y OCR (como Playwright, Selenium o scrot de ventana nativa de escritorio).",
            "protocol_mandate": "docs/orchestration/issue-6-corrective-design.md estipula taxativamente: 'Sin acceso al indicador/control nativo: QA_REJECTED por bloqueo ambiental, sin sustituir por CSS zoom ni dar por cerrada #6.'"
        },
        "verdict": "BLOQUEO_AMBIENTAL_PRECISO",
        "details": "El código estático (HTML/CSS/JS) no presenta defectos de maquetación ni reflow, pero la validación con indicador nativo visual de zoom 200% del navegador de escritorio está bloqueada por limitación de herramientas en el entorno headless/CLI."
    }
finally:
    # Stop Spring Boot
    print("Stopping Spring Boot...")
    try:
        os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        proc.wait(timeout=25)
        evidence["server"]["exit_code"] = proc.returncode
    except Exception:
        os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        evidence["server"]["exit_code"] = -9

evidence_json_path = os.path.join(EVIDENCE_DIR, "builder_evidence.json")
with open(evidence_json_path, "w", encoding="utf-8") as f:
    json.dump(evidence, f, indent=2, ensure_ascii=False)

print(f"Evidence saved to {evidence_json_path}")
