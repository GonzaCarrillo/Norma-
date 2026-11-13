'use strict';

const $ = id => document.getElementById(id);

// --- 1. Gestión de sesión ---
let session;
let storageAvailable = true;
try {
  session = localStorage.getItem('norma-session');
  if (!session || !/^[a-zA-Z0-9_-]{8,80}$/.test(session) || session === 'DEFAULT') {
    session = crypto.randomUUID();
    localStorage.setItem('norma-session', session);
  }
} catch {
  storageAvailable = false;
  session = crypto.randomUUID();
}
const query = '?sessionId=' + encodeURIComponent(session);

// --- 2. Estado de la aplicación ---
let products = [];
let busy = false;
let recorder = null;
let recordedChunks = [];
let audioStream = null;
let activeZoomPieceNumber = null;
let lastActivePhotoTrigger = null;
let eventSource = null;

// Coordinador de refrescos
let invalidationGen = 0;
let inFlight = false;
let refreshPending = false;
let abortController = null;
let lastValidProducts = null;
let connectionState = 'conectando'; // 'conectando' | 'abierta' | 'reconectando' | 'noDisponible'
let dataState = 'cargando';         // 'cargando' | 'listo' | 'vacío' | 'error'
let isSpeaking = false;

const money = n => '$ ' + new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 }).format(n);

// --- 3. Lectura nativa por voz (speechSynthesis) ---
function stopSpeech() {
  if ('speechSynthesis' in window) {
    try { window.speechSynthesis.cancel(); } catch {}
  }
  isSpeaking = false;
  const sil = $('silence');
  if (sil) sil.hidden = true;
  const zSil = $('zoom-silence');
  if (zSil) zSil.hidden = true;
}

function speak(text, onEnd) {
  if (!('speechSynthesis' in window) || !('SpeechSynthesisUtterance' in window)) {
    $('status').textContent = 'Este navegador no ofrece lectura en voz alta. Podés leer las piezas en pantalla.';
    return;
  }
  try {
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'es-AR';
    const voices = window.speechSynthesis.getVoices();
    const voice = voices.find(v => v.lang === 'es-AR' || v.lang === 'es_AR') ||
                  voices.find(v => v.lang && v.lang.startsWith('es'));
    if (voice) u.voice = voice;

    isSpeaking = true;
    const sil = $('silence');
    if (sil) sil.hidden = false;
    const zSil = $('zoom-silence');
    if (zSil && $('zoom')?.open) zSil.hidden = false;

    u.onend = () => {
      isSpeaking = false;
      if (sil) sil.hidden = true;
      if (zSil) zSil.hidden = true;
      if (typeof onEnd === 'function') onEnd();
    };
    u.onerror = () => {
      isSpeaking = false;
      if (sil) sil.hidden = true;
      if (zSil) zSil.hidden = true;
    };
    window.speechSynthesis.speak(u);
  } catch {
    isSpeaking = false;
  }
}

// Escuchar cambios de voces para navegadores que cargan voces asíncronamente
if ('speechSynthesis' in window && 'onvoiceschanged' in window.speechSynthesis) {
  window.speechSynthesis.onvoiceschanged = () => {};
}

// --- 4. Coordinador de refresco (GET /api/products) ---
async function requestRefresh() {
  invalidationGen++;
  if (inFlight) {
    refreshPending = true;
    return;
  }
  await doFetch();
}

async function doFetch() {
  do {
    refreshPending = false;
    const currentGen = invalidationGen;
    inFlight = true;

    if (abortController) {
      try { abortController.abort(); } catch {}
    }
    abortController = new AbortController();
    const signal = abortController.signal;
    const timeoutId = setTimeout(() => {
      try { abortController.abort(); } catch {}
    }, 10000);

    try {
      if (!lastValidProducts) {
        $('status').textContent = 'Buscando las piezas del taller…';
      }
      $('retry').hidden = true;

      const response = await fetch('/api/products' + query, {
        headers: { 'Cache-Control': 'no-store' },
        signal
      });
      clearTimeout(timeoutId);

      if (!response.ok) {
        const errData = await response.json().catch(() => ({}));
        throw new Error(errData.message || 'No pude obtener el catálogo. Probá otra vez.');
      }
      const data = await response.json();
      if (!Array.isArray(data)) {
        throw new Error('Respuesta inesperada del servidor.');
      }

      // Si llegó otra invalidación mientras estábamos en vuelo, descartar render y repetir
      if (currentGen !== invalidationGen) {
        continue;
      }

      lastValidProducts = data;
      products = data;
      dataState = data.length === 0 ? 'vacío' : 'listo';
      renderProducts(data);

      if (data.length === 0) {
        $('status').textContent = 'Por ahora no hay piezas disponibles. Pronto habrá más novedades del taller.';
      } else {
        if (connectionState === 'reconectando') {
          $('status').textContent = 'Reconectando con el taller…';
        } else {
          $('status').textContent = '';
        }
      }
    } catch (err) {
      clearTimeout(timeoutId);
      if (signal.aborted && currentGen !== invalidationGen) {
        continue;
      }
      dataState = 'error';
      $('retry').hidden = false;
      const msg = err.name === 'AbortError' ? 'El servidor tardó demasiado en responder.' : (err.message || 'Error de conexión.');
      if (lastValidProducts) {
        $('status').textContent = msg + ' Mostrando piezas anteriores (pueden estar desactualizadas).';
      } else {
        $('status').textContent = msg + ' Probá reintentar.';
      }
    } finally {
      inFlight = false;
    }
  } while (refreshPending);
}

// --- 5. Reconciliación DOM por pieceNumber ---
function renderProducts(items) {
  if (isSpeaking && !$('zoom')?.open) {
    stopSpeech();
    $('status').textContent = 'El catálogo cambió. Podés volver a escuchar.';
  }

  const container = $('piezas');
  const existingCards = new Map();
  for (const card of container.querySelectorAll('.card')) {
    const num = parseInt(card.dataset.pieceNumber, 10);
    if (!isNaN(num)) existingCards.set(num, card);
  }

  const newPieceNumbers = new Set(items.map(p => p.pieceNumber));

  // Si la pieza que está en zoom se vendió o eliminó
  if (activeZoomPieceNumber !== null && !newPieceNumbers.has(activeZoomPieceNumber)) {
    if ($('zoom')?.open) {
      closeZoom();
    }
    activeZoomPieceNumber = null;
    const remainingPhotos = container.querySelectorAll('.photo');
    if (remainingPhotos.length > 0) {
      remainingPhotos[0].focus();
    } else {
      container.focus();
    }
  }

  // Eliminar tarjetas que ya no existen
  for (const [num, card] of existingCards) {
    if (!newPieceNumbers.has(num)) {
      if (card.contains(document.activeElement)) {
        const nextCard = card.nextElementSibling || card.previousElementSibling;
        if (nextCard?.querySelector('.photo')) {
          nextCard.querySelector('.photo').focus();
        } else {
          container.focus();
        }
      }
      card.remove();
    }
  }

  // Insertar o actualizar tarjetas preservando orden exacto
  for (let i = 0; i < items.length; i++) {
    const p = items[i];
    let card = existingCards.get(p.pieceNumber);
    if (card) {
      updateCard(card, p);
    } else {
      card = createCard(p);
    }

    const currentAtIndex = container.children[i];
    if (currentAtIndex !== card) {
      container.insertBefore(card, currentAtIndex || null);
    }
  }
}

function createCard(p) {
  const card = document.createElement('article');
  card.className = 'card';
  card.dataset.pieceNumber = p.pieceNumber;

  const photoBtn = document.createElement('button');
  photoBtn.type = 'button';
  photoBtn.className = 'photo';
  photoBtn.setAttribute('aria-label', 'Ampliar foto de la pieza ' + p.pieceNumber + ': ' + p.title);

  const img = document.createElement('img');
  img.src = p.imageUrl;
  img.alt = p.title;
  img.loading = 'lazy';
  img.width = 800;
  img.height = 600;

  img.onerror = () => {
    img.onerror = null;
    img.style.display = 'none';
    let fallback = card.querySelector('.photo-fallback');
    if (!fallback) {
      fallback = document.createElement('div');
      fallback.className = 'photo-fallback';
      fallback.style.display = 'flex';
      fallback.style.alignItems = 'center';
      fallback.style.justifyContent = 'center';
      fallback.style.aspectRatio = '4 / 3';
      fallback.style.padding = '1rem';
      fallback.style.textAlign = 'center';
      fallback.style.color = '#1a120c';
      fallback.style.fontWeight = '600';
      fallback.textContent = 'No pude cargar la foto de la pieza ' + p.pieceNumber + ': ' + p.title;
      photoBtn.append(fallback);
    }
    photoBtn.setAttribute('aria-label', 'Foto no disponible de la pieza ' + p.pieceNumber + ': ' + p.title);
  };

  photoBtn.append(img);
  photoBtn.onclick = () => openZoom(p, photoBtn);

  const body = document.createElement('div');
  body.className = 'card-body';

  const numSpan = document.createElement('span');
  numSpan.className = 'number';
  numSpan.textContent = 'Pieza #' + p.pieceNumber;

  const titleH2 = document.createElement('h2');
  titleH2.textContent = p.title;

  const priceP = document.createElement('p');
  priceP.className = 'price';
  priceP.textContent = money(p.price);

  body.append(numSpan, titleH2, priceP);
  card.append(photoBtn, body);
  return card;
}

function updateCard(card, p) {
  const photoBtn = card.querySelector('.photo');
  photoBtn.setAttribute('aria-label', 'Ampliar foto de la pieza ' + p.pieceNumber + ': ' + p.title);
  const img = photoBtn.querySelector('img');
  if (img && img.getAttribute('src') !== p.imageUrl) {
    img.src = p.imageUrl;
    img.alt = p.title;
    img.style.display = 'block';
    const fallback = photoBtn.querySelector('.photo-fallback');
    if (fallback) fallback.remove();
  }
  const titleH2 = card.querySelector('h2');
  if (titleH2.textContent !== p.title) titleH2.textContent = p.title;
  const priceP = card.querySelector('.price');
  const formattedPrice = money(p.price);
  if (priceP.textContent !== formattedPrice) priceP.textContent = formattedPrice;

  if (activeZoomPieceNumber === p.pieceNumber && $('zoom')?.open) {
    $('zoom-caption').textContent = 'Pieza #' + p.pieceNumber + ' · ' + p.title + ' · ' + formattedPrice;
    $('zoom-image').alt = p.title;
  }
}

// --- 6. Diálogo de Zoom ---
function openZoom(p, triggerElement) {
  lastActivePhotoTrigger = triggerElement;
  activeZoomPieceNumber = p.pieceNumber;

  if ($('sim')?.open) {
    $('sim').close();
  }

  $('zoom-image').src = p.imageUrl;
  $('zoom-image').alt = p.title;
  $('zoom-caption').textContent = 'Pieza #' + p.pieceNumber + ' · ' + p.title + ' · ' + money(p.price);
  $('zoom').showModal();
  $('close-zoom').focus();
  speak('Pieza número ' + p.pieceNumber + ': ' + p.title + ', ' + p.price + ' pesos.');
}

function closeZoom() {
  if ($('zoom')?.open) {
    $('zoom').close();
  }
  activeZoomPieceNumber = null;
  stopSpeech();
  if (lastActivePhotoTrigger && document.body.contains(lastActivePhotoTrigger)) {
    try { lastActivePhotoTrigger.focus(); } catch {}
  }
}

$('close-zoom').onclick = closeZoom;
$('zoom').onclose = () => {
  activeZoomPieceNumber = null;
  stopSpeech();
  if (lastActivePhotoTrigger && document.body.contains(lastActivePhotoTrigger)) {
    try { lastActivePhotoTrigger.focus(); } catch {}
  }
};
$('zoom-silence').onclick = stopSpeech;

// --- 7. Controles de accesibilidad y barra superior ---
$('font').onclick = () => {
  const isLarge = document.documentElement.classList.toggle('large');
  document.body.classList.toggle('large', isLarge);
  $('font').setAttribute('aria-pressed', isLarge ? 'true' : 'false');
  $('font').textContent = isLarge ? 'Letra habitual' : 'Letra más grande';
};

$('listen').onclick = () => {
  if (!('speechSynthesis' in window)) {
    $('status').textContent = 'Este navegador no ofrece lectura en voz alta. Podés leer las piezas en pantalla.';
    return;
  }
  if (!products || products.length === 0) {
    $('status').textContent = 'Por ahora no hay piezas para escuchar.';
    return;
  }
  const text = products.map(p => 'Pieza número ' + p.pieceNumber + ': ' + p.title + ', ' + p.price + ' pesos.').join(' ');
  speak(text);
};

$('silence').onclick = stopSpeech;
$('retry').onclick = () => requestRefresh();

// --- 8. Conexión SSE (/api/catalog/stream) ---
function setupEventSource() {
  if (!('EventSource' in window)) {
    connectionState = 'noDisponible';
    return;
  }
  if (eventSource) {
    try { eventSource.close(); } catch {}
    eventSource = null;
  }
  try {
    eventSource = new EventSource('/api/catalog/stream' + query);
    connectionState = 'conectando';

    eventSource.onopen = () => {
      connectionState = 'abierta';
      requestRefresh();
    };

    eventSource.addEventListener('catalog-update', () => {
      requestRefresh();
    });

    eventSource.onerror = () => {
      connectionState = 'reconectando';
      if ($('status').textContent === '') {
        $('status').textContent = 'Reconectando con el taller…';
      }
    };
  } catch {
    connectionState = 'noDisponible';
  }
}

window.addEventListener('pagehide', () => {
  if (eventSource) {
    try { eventSource.close(); } catch {}
    eventSource = null;
  }
  if (abortController) {
    try { abortController.abort(); } catch {}
  }
  stopSpeech();
});

window.addEventListener('pageshow', (event) => {
  if (event.persisted && !eventSource) {
    setupEventSource();
    requestRefresh();
  }
});

// --- 9. Simulador existente de Norma ---
async function requestApi(url, options) {
  const response = await fetch(url, options);
  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.message || 'No pude completar el pedido. Probá otra vez.');
  }
  return data;
}

$('open').onclick = () => {
  if ($('zoom')?.open) closeZoom();
  $('sim').showModal();
};

$('close').onclick = () => {
  $('sim').close();
  $('open').focus();
};

$('sim').onclose = () => {
  $('open').focus();
};

function bubble(who, text) {
  const p = document.createElement('p');
  p.className = 'bubble' + (who === 'Norma' ? ' me' : '');
  p.textContent = who + ': ' + text;
  $('chat').append(p);
  p.scrollIntoView({ block: 'nearest' });
}

async function send(text, image, audio) {
  if (busy) return;
  busy = true;
  $('bot-status').textContent = 'Escuchando tu mensaje…';
  $('sim').querySelectorAll('button').forEach(b => b.disabled = true);

  const body = new FormData();
  body.append('sessionId', session);
  if (text) body.append('text', text);
  if (image) body.append('image', image, 'foto.jpg');
  if (audio) body.append('audio', audio, 'audio.webm');

  bubble('Norma', text || (audio ? 'Mensaje de voz' : 'Foto de la pieza'));

  try {
    const reply = await requestApi('/api/bot/message', { method: 'POST', body });
    bubble('Asistente', reply.replyText);
    if (reply.replyAudioUrl) {
      $('reply-audio').src = reply.replyAudioUrl;
      $('reply-audio').hidden = false;
      $('reply-audio').play().catch(() => speak(reply.replyText));
    } else {
      speak(reply.replyText);
    }
    $('bot-status').textContent = reply.state === 'AWAITING_CONFIRMATION' ? 'Esperando tu confirmación.' : 'Podés mandar otro mensaje.';
    await requestRefresh();
  } catch (e) {
    $('bot-status').textContent = e.message;
    bubble('Asistente', e.message);
  } finally {
    busy = false;
    $('sim').querySelectorAll('button').forEach(b => b.disabled = false);
  }
}

$('message').onsubmit = e => {
  e.preventDefault();
  const txt = $('text').value;
  const file = $('photo').files[0];
  send(txt, file);
  $('text').value = '';
  $('photo').value = '';
};

$('yes').onclick = () => send('Sí');
$('no').onclick = () => send('No');
$('undo').onclick = () => send('Me equivoqué');

$('reset').onclick = async () => {
  if (busy) return;
  busy = true;
  $('sim').querySelectorAll('button').forEach(b => b.disabled = true);
  try {
    await requestApi('/api/reset' + query, { method: 'POST' });
    $('chat').replaceChildren();
    bubble('Asistente', 'Restauré el catálogo original de Norma.');
    await requestRefresh();
  } catch (e) {
    $('bot-status').textContent = e.message;
  } finally {
    busy = false;
    $('sim').querySelectorAll('button').forEach(b => b.disabled = false);
  }
};

document.querySelectorAll('[data-demo]').forEach(b => b.onclick = async () => {
  const type = b.dataset.demo;
  const target = products.find(p => p.pieceNumber === 2) || products[0];
  if (type !== 'create' && !target) {
    $('bot-status').textContent = 'Restaurá el catálogo para probar este ejemplo.';
    return;
  }
  const text = type === 'create' ? 'Cuenco del patio a quince mil pesos' :
               type === 'price' ? 'A la número ' + target.pieceNumber + ' subila a veinte mil pesos' :
               type === 'image' ? 'Esta foto es para cambiar la de la pieza ' + target.pieceNumber :
               'Ya vendí la pieza ' + target.pieceNumber;

  const demo = new Audio('/assets/demo-' + type + '.mp3');
  demo.play().catch(() => speak(text));

  try {
    const image = ['create', 'image'].includes(type) ? await fetch('/assets/pieza-1.webp').then(r => r.blob()) : null;
    await send(text, image);
  } catch {
    $('bot-status').textContent = 'No pude cargar el ejemplo.';
  }
});

$('record').onclick = async () => {
  if (recorder?.state === 'recording') {
    recorder.stop();
    $('record').textContent = 'Grabar audio';
    return;
  }
  if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
    $('bot-status').textContent = 'Tu navegador no permite grabar aquí. Escribí el mensaje o usá un ejemplo.';
    return;
  }
  try {
    audioStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    recordedChunks = [];
    recorder = new MediaRecorder(audioStream);
    recorder.ondataavailable = e => recordedChunks.push(e.data);
    recorder.onstop = () => {
      audioStream.getTracks().forEach(t => t.stop());
      send('', null, new Blob(recordedChunks, { type: recorder.mimeType }));
    };
    recorder.start();
    $('record').textContent = 'Terminar y enviar audio';
    setTimeout(() => {
      if (recorder?.state === 'recording') {
        $('record').textContent = 'Grabar audio';
        recorder.stop();
      }
    }, 60000);
  } catch {
    $('bot-status').textContent = 'No pude abrir el micrófono. Podés escribir o usar un ejemplo.';
  }
};

// --- 10. Inicialización ---
requestRefresh();
setupEventSource();
