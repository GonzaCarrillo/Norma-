package com.norma.taller;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class BotEngine {
 public record Reply(String replyText,String replyAudioUrl,String state){}
 private final Catalog catalog;private final IntentParser parser;private final ObjectMapper json;
 public BotEngine(Catalog c,IntentParser p,ObjectMapper j){catalog=c;parser=p;json=j;}
 private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 @Transactional public Reply process(String id,String text,String image){
 catalog.ensure(id);var session=catalog.session(id);String s=IntentParser.norm(text==null?"":text);
 if(s.contains("equivoque")||s.equals("deshacer")||s.contains("volve atras")){
 if(session.snapshot()==null)return reply(id,"Todavía no hay un cambio para deshacer.");
 try{catalog.restore(id,json.readValue(session.snapshot(),new TypeReference<java.util.List<Catalog.Product>>(){}));}catch(Exception e){throw new IllegalStateException(e);}
 catalog.snapshot(id,null);catalog.conversation(id,"IDLE",null,null);return reply(id,"Tranquila Norma, ya dejé todo como estaba antes.");
 }
 if(session.state().equals("AWAITING_CONFIRMATION")){
 if(s.matches("si|dale|subilo|esta bien|confirmo|confirmar")){
 try{var intent=json.readValue(session.pending(),IntentParser.Intent.class);catalog.snapshot(id,encode(catalog.all(id)));catalog.apply(id,intent);}catch(java.io.IOException e){throw new IllegalStateException(e);}
 catalog.conversation(id,"IDLE",null,null);return reply(id,"¡Listo Norma! Ya quedó actualizado en la página.");
 }
 if(s.matches("no|para|cancelar|cancela")){catalog.conversation(id,"IDLE",null,null);return reply(id,"Cancelé el cambio, mandame de nuevo tranqui.");}
 if(s.startsWith("no ")||s.startsWith("dije ")){
 try{var old=json.readValue(session.pending(),IntentParser.Intent.class);Integer price=parser.price(s);if(price!=null&&(old.kind().equals("CREATE")||old.kind().equals("PRICE"))){var corrected=new IntentParser.Intent(old.kind(),old.number(),old.title(),price,old.image());return prepare(id,corrected);}}catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 return reply(id,"Hay un cambio pendiente. Decime sí para confirmar, no para cancelar, o corregime el precio.");
 }
 String photo=image!=null?image:session.tempImage();
 if(s.isBlank()){
 if(photo!=null && session.pending()!=null){
 try{var old=json.readValue(session.pending(),IntentParser.Intent.class);return prepare(id,new IntentParser.Intent(old.kind(),old.number(),old.title(),old.price(),photo));}catch(java.io.IOException e){throw new IllegalStateException(e);}
 }
 if(photo!=null){catalog.conversation(id,"AWAITING_DETAILS",null,photo);return reply(id,"Qué linda foto, Norma. Decime el nombre y el precio, o de qué pieza es la foto nueva.");}
 return reply(id,"Mandame una foto o contame qué pieza querés cambiar.");
 }
 try{
 var intent=parser.parse(text,photo,catalog.all(id));
 if((intent.kind().equals("CREATE")||intent.kind().equals("IMAGE"))&&photo==null){
 catalog.conversation(id,"AWAITING_DETAILS",encode(intent),null);return reply(id,"Me falta la foto. Mandámela y repetí el nombre y precio, o el numerito de la pieza.");
 }
 return prepare(id,intent);
 }catch(IllegalArgumentException e){return reply(id,e.getMessage());}
 }
 private Reply prepare(String id,IntentParser.Intent intent){
 catalog.conversation(id,"AWAITING_CONFIRMATION",encode(intent),intent.image());
 String description=switch(intent.kind()){
 case "CREATE" -> "publicar "+intent.title()+" a "+intent.price()+" pesos";
 case "PRICE" -> "cambiar el precio de la pieza "+intent.number()+" a "+intent.price()+" pesos";
 case "IMAGE" -> "cambiar la foto de la pieza "+intent.number();
 case "SOLD" -> "retirar la pieza "+intent.number()+" porque está vendida";
 default -> throw new IllegalArgumentException("No entendí.");
 };return reply(id,"¿Confirmás "+description+"? Decime sí o no.");
 }
 private Reply reply(String id,String text){return new Reply(text,null,catalog.session(id).state());}
}
