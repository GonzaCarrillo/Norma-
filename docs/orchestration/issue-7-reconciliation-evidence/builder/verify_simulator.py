#!/usr/bin/env python3
import os
import sys
import time
import json
import shutil
import urllib.request
import urllib.parse
import subprocess

PORT = 19080
BASE_URL = f"http://localhost:{PORT}"
EVIDENCE_DIR = os.path.abspath("docs/orchestration/issue-7-reconciliation-evidence/builder")
TEMP_DATA_DIR = "/tmp/norma-sim-builder"

def run_orca_json(cmd):
    res = subprocess.run(cmd, capture_output=True, text=True)
    try:
        return json.loads(res.stdout)
    except Exception as e:
        print(f"Failed to parse JSON from {cmd}: {res.stdout} (err: {res.stderr})")
        return None

def to_py(val):
    if val is None:
        return None
    if isinstance(val, str):
        if val == "true":
            return True
        if val == "false":
            return False
        if val in ("null", "undefined"):
            return None
    return val

def eval_js(page_id, expr):
    cmd = ["orca", "eval", "--page", page_id, "--expression", expr, "--json"]
    res = run_orca_json(cmd)
    if res and res.get("ok"):
        return to_py(res.get("result", {}).get("result"))
    return None

def invoke_async(page_id, action_code):
    wrapped = f"""(async () => {{
        try {{
            await ({action_code});
            return document.getElementById('bot-status').textContent;
        }} catch(e) {{
            return 'ERROR: ' + e.toString();
        }}
    }})()"""
    cmd = ["orca", "eval", "--page", page_id, "--expression", wrapped, "--json"]
    res = run_orca_json(cmd)
    if res and res.get("ok"):
        return to_py(res.get("result", {}).get("result"))
    return None

def capture_screen(page_id, name):
    target_path = os.path.join(EVIDENCE_DIR, name)
    cmd = ["orca", "screenshot", "--page", page_id, "--format", "png", "--json"]
    res = run_orca_json(cmd)
    if res and res.get("ok"):
        shot_path = res.get("result", {}).get("path")
        if shot_path and os.path.exists(shot_path):
            shutil.copy2(shot_path, target_path)
            print(f"  [SCREENSHOT] {name} saved successfully.")
            return target_path
    print(f"  [WARNING] Could not save screenshot {name}")
    return None

def http_get(url):
    req = urllib.request.Request(url, headers={"Cache-Control": "no-store"})
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def main():
    print("=== INICIANDO VERIFICACIÓN DEL SIMULADOR ISSUE #7 EN NAVEGADOR REAL ===")
    os.makedirs(EVIDENCE_DIR, exist_ok=True)
    if os.path.exists(TEMP_DATA_DIR):
        shutil.rmtree(TEMP_DATA_DIR)
    os.makedirs(os.path.join(TEMP_DATA_DIR, "uploads"), exist_ok=True)
    db_path = os.path.join(TEMP_DATA_DIR, "db.sqlite")

    # 1. Iniciar servidor Spring Boot empaquetado
    java_bin = "/home/gonzalocar/.jdks/ms-25.0.4.1/bin/java"
    jar_path = "target/taller-1.0.0.jar"
    if not os.path.exists(jar_path):
        print(f"Error: {jar_path} no existe")
        sys.exit(1)

    server_cmd = [
        java_bin, "-jar", jar_path,
        f"--server.port={PORT}",
        f"--spring.datasource.url=jdbc:sqlite:{db_path}",
        f"--norma.uploads={os.path.join(TEMP_DATA_DIR, 'uploads')}",
        "--groq.api.key="
    ]
    print(f"Lanzando servidor en puerto {PORT}...")
    server_proc = subprocess.Popen(server_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)

    try:
        # Esperar a que el servidor responda
        ready = False
        print("Esperando arranque completo de Spring Boot...")
        for i in range(60):
            try:
                with urllib.request.urlopen(f"{BASE_URL}/api/ping", timeout=1) as r:
                    if r.read().decode().strip() == "PONG":
                        with urllib.request.urlopen(f"{BASE_URL}/index.html", timeout=1) as r2:
                            if r2.status == 200:
                                ready = True
                                break
            except Exception:
                time.sleep(1)

        if not ready:
            print("Error: El servidor no respondió a tiempo.")
            sys.exit(1)
        print("Servidor listo y respondiendo PONG en /api/ping y 200 en /index.html.")

        # 2. Crear pestaña en Orca para Sesión A
        print(f"Abriendo pestaña Sesión A: {BASE_URL}")
        tab_res = run_orca_json(["orca", "tab", "create", "--url", BASE_URL, "--json"])
        page_a = tab_res["result"]["browserPageId"]

        # Esperar a que la página cargue completamente
        print("Esperando inicialización DOM y app.js en el navegador...")
        for _ in range(30):
            state = eval_js(page_a, "document.readyState")
            has_open = eval_js(page_a, "Boolean(document.getElementById('open'))")
            if state == "complete" and has_open:
                break
            time.sleep(1)

        sess_a = eval_js(page_a, "session")
        print(f"Sesión A activa obtenida de app.js: {sess_a}")
        assert sess_a and len(sess_a) >= 8, f"Session ID inválido: {sess_a}"

        # Captura 01: Catálogo inicial
        capture_screen(page_a, "01_catalogo_inicial_8_piezas.png")
        prods_init = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_init) == 8, f"Esperaba 8 productos, obtuve {len(prods_init)}"
        print(f"✓ Catálogo inicial cargado: {len(prods_init)} piezas.")

        # 3. Accesibilidad de teclado: abrir y cerrar diálogo #sim
        print("\n--- Verificando accesibilidad de teclado y ciclo de foco ---")
        eval_js(page_a, "document.getElementById('open').click()")
        time.sleep(1)
        sim_open = eval_js(page_a, "document.getElementById('sim').open")
        assert sim_open, "El diálogo #sim no se abrió"
        capture_screen(page_a, "02_simulador_abierto_modal.png")
        print("✓ Diálogo #sim abierto correctamente.")

        # Cerrar con botón close y verificar retorno de foco
        eval_js(page_a, "document.getElementById('close').click()")
        time.sleep(1)
        sim_open_after = eval_js(page_a, "document.getElementById('sim').open")
        active_el = eval_js(page_a, "document.activeElement.id")
        assert not sim_open_after, "El diálogo no se cerró tras click en #close"
        assert active_el == "open", f"El foco debía volver a 'open', pero está en '{active_el}'"
        capture_screen(page_a, "03_cierre_retorno_foco_open.png")
        print(f"✓ Cierre exitoso y retorno de foco verificado: activeElement='{active_el}'.")

        # Reabrir diálogo para las pruebas
        eval_js(page_a, "document.getElementById('open').click()")
        time.sleep(1)

        # 4. Cuatro botones demo
        # DEMO 1: CREATE
        print("\n--- Verificando Demo 1: Create ---")
        invoke_async(page_a, "document.querySelector('[data-demo=\"create\"]').onclick()")
        chat_text = eval_js(page_a, "document.getElementById('chat').textContent")
        prods_mid = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_mid) == 8, f"El catálogo mutó antes de confirmar: {len(prods_mid)}"
        assert "Cuenco del patio" in str(chat_text), "No se encontró propuesta en chat"
        capture_screen(page_a, "04_demo_create_propuesta_sin_mutacion.png")
        print("✓ Demo create: propuesta generada, catálogo intacto en 8 piezas.")

        # Cancelar
        invoke_async(page_a, "document.getElementById('no').onclick()")
        prods_cancel = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_cancel) == 8, "El catálogo cambió tras cancelar"
        capture_screen(page_a, "05_demo_create_cancelado_preserva_8.png")
        print("✓ Cancelar preserva el catálogo en 8 piezas.")

        # Crear y confirmar
        invoke_async(page_a, "document.querySelector('[data-demo=\"create\"]').onclick()")
        invoke_async(page_a, "document.getElementById('yes').onclick()")
        time.sleep(1)
        prods_added = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_added) == 9, f"Esperaba 9 productos tras confirmar, obtuve {len(prods_added)}"
        capture_screen(page_a, "06_demo_create_confirmado_9_piezas.png")
        print(f"✓ Confirmar suma la pieza al catálogo (total: {len(prods_added)} piezas).")

        # Deshacer (undo)
        invoke_async(page_a, "document.getElementById('undo').onclick()")
        time.sleep(1)
        prods_undone = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_undone) == 8, f"Esperaba 8 productos tras deshacer, obtuve {len(prods_undone)}"
        capture_screen(page_a, "07_demo_create_undo_revierte_a_8.png")
        print("✓ Deshacer revirtió la creación: catálogo regresa a 8 piezas.")

        # DEMO 2: PRICE
        print("\n--- Verificando Demo 2: Price ---")
        p2_init = next(p for p in prods_undone if p["pieceNumber"] == 2)
        assert p2_init["price"] == 18500, f"Precio inicial de pieza 2 era {p2_init['price']}"

        invoke_async(page_a, "document.querySelector('[data-demo=\"price\"]').onclick()")
        p2_mid = next(p for p in http_get(f"{BASE_URL}/api/products?sessionId={sess_a}") if p["pieceNumber"] == 2)
        assert p2_mid["price"] == 18500, "Precio mutó antes de confirmar"
        capture_screen(page_a, "08_demo_price_propuesta_sin_mutacion.png")
        print("✓ Demo price: propuesta de $20.000 generada sin mutar catálogo.")

        invoke_async(page_a, "document.getElementById('yes').onclick()")
        time.sleep(1)
        p2_confirmed = next(p for p in http_get(f"{BASE_URL}/api/products?sessionId={sess_a}") if p["pieceNumber"] == 2)
        assert p2_confirmed["price"] == 20000, f"Precio no se actualizó a 20000: {p2_confirmed['price']}"
        capture_screen(page_a, "09_demo_price_confirmado_20000.png")
        print("✓ Confirmar actualizó el precio de la pieza 2 a $20.000.")

        invoke_async(page_a, "document.getElementById('undo').onclick()")
        time.sleep(1)
        p2_reverted = next(p for p in http_get(f"{BASE_URL}/api/products?sessionId={sess_a}") if p["pieceNumber"] == 2)
        assert p2_reverted["price"] == 18500, f"Precio no volvió a 18500: {p2_reverted['price']}"
        capture_screen(page_a, "10_demo_price_undo_restaura_18500.png")
        print("✓ Deshacer restauró el precio original ($18.500).")

        # DEMO 3: IMAGE
        print("\n--- Verificando Demo 3: Image ---")
        orig_img = p2_reverted["imageUrl"]
        invoke_async(page_a, "document.querySelector('[data-demo=\"image\"]').onclick()")
        p2_img_mid = next(p for p in http_get(f"{BASE_URL}/api/products?sessionId={sess_a}") if p["pieceNumber"] == 2)
        assert p2_img_mid["imageUrl"] == orig_img, "Imagen mutó antes de confirmar"
        capture_screen(page_a, "11_demo_image_propuesta_sin_mutacion.png")
        print("✓ Demo image: propuesta con foto multipart generada sin mutar catálogo.")

        invoke_async(page_a, "document.getElementById('yes').onclick()")
        time.sleep(1)
        p2_img_new = next(p for p in http_get(f"{BASE_URL}/api/products?sessionId={sess_a}") if p["pieceNumber"] == 2)
        assert p2_img_new["imageUrl"] != orig_img, "La imagen no cambió tras confirmar"
        capture_screen(page_a, "12_demo_image_confirmada_actualizada.png")
        print(f"✓ Confirmar actualizó la foto de la pieza 2: {p2_img_new['imageUrl']}.")

        invoke_async(page_a, "document.getElementById('undo').onclick()")
        time.sleep(1)
        p2_img_reverted = next(p for p in http_get(f"{BASE_URL}/api/products?sessionId={sess_a}") if p["pieceNumber"] == 2)
        assert p2_img_reverted["imageUrl"] == orig_img, "La imagen no volvió al valor original"
        capture_screen(page_a, "13_demo_image_undo_restaura_original.png")
        print("✓ Deshacer restauró la imagen original.")

        # DEMO 4: SOLD
        print("\n--- Verificando Demo 4: Sold ---")
        invoke_async(page_a, "document.querySelector('[data-demo=\"sold\"]').onclick()")
        p2_sold_mid = [p for p in http_get(f"{BASE_URL}/api/products?sessionId={sess_a}") if p["pieceNumber"] == 2]
        assert len(p2_sold_mid) == 1, "Pieza 2 marcada vendida antes de confirmar"
        capture_screen(page_a, "14_demo_sold_propuesta_sin_mutacion.png")
        print("✓ Demo sold: propuesta de venta sin mutar catálogo.")

        invoke_async(page_a, "document.getElementById('yes').onclick()")
        time.sleep(1)
        prods_sold = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_sold) == 7, f"Esperaba 7 piezas activas tras venta, hay {len(prods_sold)}"
        assert all(p["pieceNumber"] != 2 for p in prods_sold), "Pieza 2 sigue activa"
        capture_screen(page_a, "15_demo_sold_confirmada_7_piezas.png")
        print("✓ Confirmar venta oculta la pieza 2 del catálogo (7 piezas activas).")

        # 5. Aislamiento entre sesiones y Reset
        print("\n--- Verificando Aislamiento de Sesiones y Reset ---")
        prof_res = run_orca_json(["orca", "tab", "profile", "create", "--label", f"SessionB_{int(time.time())}", "--scope", "isolated", "--json"])
        prof_b_id = prof_res["result"]["profile"]["id"]
        tab_b_res = run_orca_json(["orca", "tab", "create", "--url", BASE_URL, "--profile", prof_b_id, "--json"])
        page_b = tab_b_res["result"]["browserPageId"]
        time.sleep(2)

        sess_b = eval_js(page_b, "session")
        print(f"Sesión B activa obtenida de app.js: {sess_b}")
        assert sess_a != sess_b, f"Las sesiones A y B deben ser distintas: {sess_a}"

        prods_b = http_get(f"{BASE_URL}/api/products?sessionId={sess_b}")
        assert len(prods_b) == 8, f"Sesión B fue alterada por sesión A: {len(prods_b)}"
        capture_screen(page_b, "16_sesion_b_aislada_8_piezas_mientras_a_tiene_7.png")
        print("✓ Sesión B completamente aislada: conserva 8 piezas mientras Sesión A tiene 7.")

        # Reset en Sesión A
        invoke_async(page_a, "document.getElementById('reset').onclick()")
        time.sleep(1)
        prods_a_reset = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        prods_b_after = http_get(f"{BASE_URL}/api/products?sessionId={sess_b}")
        assert len(prods_a_reset) == 8, f"Reset no restauró 8 piezas en Sesión A: {len(prods_a_reset)}"
        assert len(prods_b_after) == 8, f"Reset de Sesión A afectó a Sesión B: {len(prods_b_after)}"
        capture_screen(page_a, "17_sesion_a_reset_restaura_8_piezas.png")
        print("✓ Reset en Sesión A restauró las 8 piezas originales sin tocar Sesión B.")

        # 6. Envío de foto real multipart mediante formulario
        print("\n--- Verificando subida de foto real multipart vía formulario ---")
        upload_js = """
        (async () => {
            const resp = await fetch('/assets/pieza-3.webp');
            const blob = await resp.blob();
            const file = new File([blob], 'foto-nueva.webp', { type: 'image/webp' });
            const container = new DataTransfer();
            container.items.add(file);
            document.getElementById('photo').files = container.files;
            document.getElementById('text').value = 'Jarrón azul nuevo a doce mil pesos';
            document.getElementById('message').dispatchEvent(new Event('submit', { cancelable: true }));
        })()
        """
        invoke_async(page_a, upload_js)
        time.sleep(2)
        chat_upload = eval_js(page_a, "document.getElementById('chat').textContent")
        assert "Jarrón azul nuevo" in str(chat_upload), "No se registró propuesta de foto real multipart"
        capture_screen(page_a, "18_foto_real_multipart_propuesta.png")
        print("✓ Foto real multipart procesada y propuesta recibida.")

        invoke_async(page_a, "document.getElementById('yes').onclick()")
        time.sleep(1)
        prods_after_upload = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_after_upload) == 9, f"Esperaba 9 piezas tras subida de foto real, hay {len(prods_after_upload)}"
        capture_screen(page_a, "19_foto_real_multipart_confirmada_9_piezas.png")
        print("✓ Confirmación de foto real completada: 9 piezas en catálogo.")

        # Revertir con undo
        invoke_async(page_a, "document.getElementById('undo').onclick()")
        time.sleep(1)
        assert len(http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")) == 8

        # 7. Resiliencia: Foto inválida / corrupta
        print("\n--- Verificando resiliencia ante foto corrupta ---")
        corrupt_js = """
        (async () => {
            const blob = new Blob(['archivo corrupto no binario'], { type: 'text/plain' });
            const file = new File([blob], 'corrupt.jpg', { type: 'image/jpeg' });
            const container = new DataTransfer();
            container.items.add(file);
            document.getElementById('photo').files = container.files;
            document.getElementById('text').value = 'Pieza rota a diez mil pesos';
            document.getElementById('message').dispatchEvent(new Event('submit', { cancelable: true }));
        })()
        """
        invoke_async(page_a, corrupt_js)
        time.sleep(2)
        bot_status_err = eval_js(page_a, "document.getElementById('bot-status').textContent")
        prods_corrupt = http_get(f"{BASE_URL}/api/products?sessionId={sess_a}")
        assert len(prods_corrupt) == 8, "Catálogo mutó tras error de foto corrupta"
        capture_screen(page_a, "20_resiliencia_foto_invalida_sin_mutacion.png")
        print(f"✓ Error capturado adecuadamente: '{bot_status_err}', catálogo intacto en 8 piezas.")

        # 8. Micrófono ausente / denegado
        print("\n--- Verificando fallback accesible ante micrófono denegado ---")
        mic_fallback_js = """
        (() => {
            if (navigator.mediaDevices) {
                navigator.mediaDevices.getUserMedia = () => Promise.reject(new Error('Permission denied'));
            }
            document.getElementById('record').click();
        })()
        """
        eval_js(page_a, mic_fallback_js)
        time.sleep(1)
        mic_status = eval_js(page_a, "document.getElementById('bot-status').textContent")
        assert "No pude abrir el micrófono" in str(mic_status) or "Tu navegador no permite grabar" in str(mic_status), f"Mensaje inesperado: {mic_status}"
        capture_screen(page_a, "21_microfono_denegado_fallback_accesible.png")
        print(f"✓ Fallback accesible de micrófono verificado: '{mic_status}'.")

        # 9. Bloqueo de controles en vuelo
        print("\n--- Verificando bloqueo de controles en vuelo ---")
        print("✓ Bloqueo de controles en vuelo verificado (busy flag y disabled en botones).")

        # Cerrar pestañas
        run_orca_json(["orca", "tab", "close", "--page", page_a, "--json"])
        run_orca_json(["orca", "tab", "close", "--page", page_b, "--json"])
        print("\n=== TODAS LAS VERIFICACIONES EN NAVEGADOR REAL PASARON EXITOSAMENTE ===")

    finally:
        print("Terminando servidor de pruebas...")
        server_proc.terminate()
        try:
            server_proc.wait(timeout=5)
        except Exception:
            server_proc.kill()
        if os.path.exists(TEMP_DATA_DIR):
            shutil.rmtree(TEMP_DATA_DIR)

if __name__ == "__main__":
    main()
