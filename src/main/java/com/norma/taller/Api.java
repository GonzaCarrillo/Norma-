package com.norma.taller;
import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.*;
@RestController
public class Api {
 private final Catalog catalog;private final BotEngine bot;private final MediaService media;
 private final Map<String,CopyOnWriteArrayList<SseEmitter>> streams=new ConcurrentHashMap<>();
 public Api(Catalog c,BotEngine b,MediaService m){catalog=c;bot=b;media=m;}
 @GetMapping("/api/ping") public String ping(){return "PONG";}
 @GetMapping("/api/products") public List<Catalog.Product> products(@RequestParam String sessionId){catalog.ensure(sessionId);return catalog.all(sessionId).stream().filter(p->!p.sold()).toList();}
 @PostMapping("/api/reset") public Map<String,String> reset(@RequestParam String sessionId){catalog.reset(sessionId);publish(sessionId);return Map.of("message","El catálogo original ya está restaurado.");}
 @PostMapping(value="/api/bot/message",consumes="multipart/form-data")
 public BotEngine.Reply message(@RequestParam String sessionId,@RequestParam(required=false)String text,@RequestParam(required=false) MultipartFile image,@RequestParam(required=false)MultipartFile audio)throws IOException{
 Catalog.valid(sessionId);
 boolean hasText = text != null && !text.trim().isEmpty();
 boolean hasAudio = audio != null && !audio.isEmpty();
 if (hasText && hasAudio) {
  throw new IllegalArgumentException("Mandame el texto o el audio con los detalles, uno por vez.");
 }
 if(text!=null&&text.length()>1000)throw new IllegalArgumentException("El mensaje es demasiado largo.");
 if(hasAudio)text=media.transcribe(audio);
 String photo=image==null||image.isEmpty()?null:media.image(image);
 var result=bot.process(sessionId,text,photo);publish(sessionId);
 return new BotEngine.Reply(result.replyText(),media.speak(result.replyText()),result.state());
 }
 @GetMapping(value="/api/catalog/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 public SseEmitter stream(@RequestParam String sessionId){
 catalog.ensure(sessionId);var emitter=new SseEmitter(300000L);var list=streams.computeIfAbsent(sessionId,k->new CopyOnWriteArrayList<>());
 if(list.size()>=5)throw new IllegalArgumentException("Ya hay varias ventanas abiertas para esta sesión.");
 list.add(emitter);Runnable cleanup=()->{list.remove(emitter);if(list.isEmpty())streams.remove(sessionId,list);};
 emitter.onCompletion(cleanup);emitter.onTimeout(()->{cleanup.run();emitter.complete();});emitter.onError(e->cleanup.run());
 try{emitter.send(SseEmitter.event().name("catalog-update").data("conectado"));}catch(IOException e){cleanup.run();}
 return emitter;
 }
 void publish(String id){for(var e:streams.getOrDefault(id,new CopyOnWriteArrayList<>()))try{e.send(SseEmitter.event().name("catalog-update").data("actualizado"));}catch(Exception ex){e.complete();}}
}
@Configuration
class MediaConfig implements WebMvcConfigurer{
 private final MediaService media;MediaConfig(MediaService m){media=m;}
 public void addResourceHandlers(ResourceHandlerRegistry registry){
  if(media!=null&&media.root!=null){
   registry.addResourceHandler("/media/**").addResourceLocations(media.root.toUri().toString());
  }
 }
}
@RestControllerAdvice
class ApiErrors{
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) ResponseEntity<Map<String,String>> large(){return ResponseEntity.status(413).body(Map.of("message","El archivo es demasiado grande. Probá con uno más chico."));}
 @ExceptionHandler(Exception.class) ResponseEntity<Map<String,String>> error(Exception e){org.slf4j.LoggerFactory.getLogger(ApiErrors.class).error("Fallo de solicitud",e);return ResponseEntity.status(500).body(Map.of("message","No pude completar el pedido. Intentá otra vez."));}
}
