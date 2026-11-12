'use strict';
const $=id=>document.getElementById(id);
let session;
try{session=localStorage.getItem('norma-session');if(!session){session=crypto.randomUUID();localStorage.setItem('norma-session',session);}}catch{session=crypto.randomUUID();}
const query='?sessionId='+encodeURIComponent(session);
let products=[],busy=false,recorder,recorded=[],stream;
const money=n=>'$ '+new Intl.NumberFormat('es-AR',{maximumFractionDigits:0}).format(n);
function speak(text){if(!('speechSynthesis'in window))return;speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(text);u.lang='es-AR';const voice=speechSynthesis.getVoices().find(v=>v.lang==='es-AR');if(voice)u.voice=voice;speechSynthesis.speak(u);}
async function request(url,options){const response=await fetch(url,options);const data=await response.json();if(!response.ok)throw Error(data.message||'No pude completar el pedido. Probá otra vez.');return data;}
async function load(){try{products=await request('/api/products'+query);$('piezas').replaceChildren();for(const p of products){
 const card=document.createElement('article');card.className='card';
 const photo=document.createElement('button');photo.className='photo';photo.setAttribute('aria-label','Ampliar foto de la pieza '+p.pieceNumber+': '+p.title);
 const img=document.createElement('img');img.src=p.imageUrl;img.alt=p.title;img.loading='lazy';img.width=800;img.height=600;photo.append(img);photo.onclick=()=>{$('zoom-image').src=p.imageUrl;$('zoom-image').alt=p.title;$('zoom').showModal();speak('Pieza número '+p.pieceNumber+': '+p.title+', '+p.price+' pesos');};
 const body=document.createElement('div');body.className='card-body';const number=document.createElement('span');number.className='number';number.textContent='Pieza #'+p.pieceNumber;const title=document.createElement('h2');title.textContent=p.title;const price=document.createElement('p');price.className='price';price.textContent=money(p.price);body.append(number,title,price);card.append(photo,body);$('piezas').append(card);
 }$('status').textContent=products.length?'':'Por ahora no hay piezas disponibles. Pronto habrá más novedades del taller.';
 }catch(e){$('status').textContent=e.message+' Recargá la página para volver a intentar.';}}
$('font').onclick=()=>{const large=document.body.classList.toggle('large');$('font').setAttribute('aria-pressed',large);$('font').textContent=large?'Letra habitual':'Letra más grande';};
$('listen').onclick=()=>{if(!('speechSynthesis'in window)){$('status').textContent='Este navegador no ofrece lectura en voz alta.';return;}speak(products.map(p=>'Pieza número '+p.pieceNumber+': '+p.title+', '+p.price+' pesos.').join(' '));$('silence').hidden=false;};
$('silence').onclick=()=>{speechSynthesis.cancel();$('silence').hidden=true;};
$('close-zoom').onclick=()=>{$('zoom').close();window.speechSynthesis?.cancel();};
$('open').onclick=()=>$('sim').showModal();
$('close').onclick=()=>$('sim').close();
function bubble(who,text){const p=document.createElement('p');p.className='bubble'+(who==='Norma'?' me':'');p.textContent=who+': '+text;$('chat').append(p);p.scrollIntoView({block:'nearest'});}
async function send(text,image,audio){
 if(busy)return;busy=true;$('bot-status').textContent='Escuchando tu mensaje…';$('sim').querySelectorAll('button').forEach(b=>b.disabled=true);
 const body=new FormData();body.append('sessionId',session);if(text)body.append('text',text);if(image)body.append('image',image,'foto.jpg');if(audio)body.append('audio',audio,'audio.webm');
 bubble('Norma',text|| (audio?'Mensaje de voz':'Foto de la pieza'));
 try{const reply=await request('/api/bot/message',{method:'POST',body});bubble('Asistente',reply.replyText);if(reply.replyAudioUrl){$('reply-audio').src=reply.replyAudioUrl;$('reply-audio').hidden=false;$('reply-audio').play().catch(()=>speak(reply.replyText));}else speak(reply.replyText);$('bot-status').textContent=reply.state==='AWAITING_CONFIRMATION'?'Esperando tu confirmación.':'Podés mandar otro mensaje.';await load();}
 catch(e){$('bot-status').textContent=e.message; bubble('Asistente',e.message);}
 finally{busy=false;$('sim').querySelectorAll('button').forEach(b=>b.disabled=false);}
}
$('message').onsubmit=e=>{e.preventDefault();send($('text').value,$('photo').files[0]);$('text').value='';$('photo').value='';};
$('yes').onclick=()=>send('Sí');$('no').onclick=()=>send('No');$('undo').onclick=()=>send('Me equivoqué');
$('reset').onclick=async()=>{if(busy)return;try{await request('/api/reset'+query,{method:'POST'});$('chat').replaceChildren();bubble('Asistente','Restauré el catálogo original de Norma.');await load();}catch(e){$('bot-status').textContent=e.message;}};
document.querySelectorAll('[data-demo]').forEach(b=>b.onclick=async()=>{
 const type=b.dataset.demo;const target=products.find(p=>p.pieceNumber===2)||products[0];if(type!=='create'&&!target){$('bot-status').textContent='Restaurá el catálogo para probar este ejemplo.';return;}
 const text=type==='create'?'Cuenco del patio a quince mil pesos':type==='price'?'A la número '+target.pieceNumber+' subila a veinte mil pesos':type==='image'?'Esta foto es para cambiar la de la pieza '+target.pieceNumber:'Ya vendí la pieza '+target.pieceNumber;
 // Ejemplos con transcripción conocida: no simulan una llamada al proveedor STT.
 const demo=new Audio('/assets/demo-'+type+'.mp3');demo.play().catch(()=>speak(text));
 try{const image=['create','image'].includes(type)?await fetch('/assets/pieza-1.webp').then(r=>r.blob()):null;await send(text,image);}catch{$('bot-status').textContent='No pude cargar el ejemplo.';}
});
$('record').onclick=async()=>{
 if(recorder?.state==='recording'){recorder.stop();$('record').textContent='Grabar audio';return;}
 if(!navigator.mediaDevices?.getUserMedia||!window.MediaRecorder){$('bot-status').textContent='Tu navegador no permite grabar aquí. Escribí el mensaje o usá un ejemplo.';return;}
 try{stream=await navigator.mediaDevices.getUserMedia({audio:true});recorded=[];recorder=new MediaRecorder(stream);recorder.ondataavailable=e=>recorded.push(e.data);recorder.onstop=()=>{stream.getTracks().forEach(t=>t.stop());send('',null,new Blob(recorded,{type:recorder.mimeType}));};recorder.start();$('record').textContent='Terminar y enviar audio';setTimeout(()=>{if(recorder.state==='recording'){$('record').textContent='Grabar audio';recorder.stop();}},60000);}catch{$('bot-status').textContent='No pude abrir el micrófono. Podés escribir o usar un ejemplo.';}
};
load();const events=new EventSource('/api/catalog/stream'+query);events.addEventListener('catalog-update',load);events.onerror=()=>{$('status').textContent='Reconectando con el taller…';};
