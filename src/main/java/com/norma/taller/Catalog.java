package com.norma.taller;
import java.util.*;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
@DependsOn("catalogDatabaseInitializer")
public class Catalog {
 public record Product(long id,String sessionId,int pieceNumber,String title,int price,String imageUrl,boolean sold) {}
 public record Session(String id,String state,String pending,String tempImage,String snapshot,int nextNumber) {}
 private final JdbcClient db;
 public Catalog(JdbcClient db){this.db=db;}
 public static String valid(String id){
  if(id==null || !id.matches("[a-zA-Z0-9_-]{8,80}") || id.equals("DEFAULT")) throw new IllegalArgumentException("La sesión no es válida."); return id;
 }
 @Transactional
 public void ensure(String id){
  valid(id);
  int added=db.sql("INSERT INTO sessions(id) VALUES(?) ON CONFLICT(id) DO NOTHING").param(id).update();
  if(added>0){
   int cloned=db.sql("INSERT INTO products(session_id,piece_number,title,price,image_url,sold) " +
     "SELECT ?,piece_number,title,price,image_url,sold FROM products WHERE session_id='DEFAULT'").param(id).update();
   if(cloned!=8){
    throw new IllegalStateException("Fallo al clonar semilla canónica: se clonaron " + cloned + " de 8 piezas esperadas.");
   }
  }
 }
 public List<Product> all(String id){
  valid(id);
  session(id);
  return db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id=? ORDER BY piece_number").param(id).query(Product.class).list();
 }
 public Session session(String id){
  valid(id);
  var list=db.sql("SELECT id,state,pending,temp_image,snapshot,next_number FROM sessions WHERE id=?").param(id).query(Session.class).list();
  if(list.isEmpty()){
   throw new IllegalArgumentException("Sesión no encontrada: " + id);
  }
  return list.get(0);

 }
 @Transactional
 public void conversation(String id,String state,String pending,String image){
  valid(id);
  int rows=db.sql("UPDATE sessions SET state=?,pending=?,temp_image=?,updated_at=CURRENT_TIMESTAMP WHERE id=?").params(Arrays.asList(state,pending,image,id)).update();
  if(rows!=1) throw new IllegalArgumentException("No se pudo actualizar conversación para la sesión: " + id);
 }
 @Transactional
 public void snapshot(String id,String json){
  valid(id);
  int rows=db.sql("UPDATE sessions SET snapshot=?,updated_at=CURRENT_TIMESTAMP WHERE id=?").params(Arrays.asList(json,id)).update();
  if(rows!=1) throw new IllegalArgumentException("No se pudo actualizar snapshot para la sesión: " + id);
 }

 @Transactional
 public void insert(String id,int number,String title,int price,String image,boolean sold){
  valid(id);
  session(id);
  if(number<1 || number>2147483646) throw new IllegalArgumentException("piece_number fuera de rango: " + number);
  if(title==null || title.trim().isEmpty()) throw new IllegalArgumentException("title no puede ser vacio.");
  if(price<=0) throw new IllegalArgumentException("price debe ser un entero positivo.");
  if(image==null || image.trim().isEmpty()) throw new IllegalArgumentException("image no puede ser vacia.");

  int inserted=db.sql("INSERT INTO products(session_id,piece_number,title,price,image_url,sold,updated_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)")
    .params(id,number,title,price,image,sold?1:0).update();
  if(inserted!=1) throw new IllegalStateException("No se pudo insertar producto.");

  // Actualizar high-water mark en sessions: max(next_number, number + 1)
  db.sql("UPDATE sessions SET next_number=MAX(next_number, ?), updated_at=CURRENT_TIMESTAMP WHERE id=?")
    .params(number+1, id).update();
 }

 @Transactional
 public void apply(String id,IntentParser.Intent intent){
  valid(id);
  Session s=session(id);
  if(intent==null || intent.kind()==null) throw new IllegalArgumentException("Intent invalido.");

  switch(intent.kind()){
   case "CREATE" -> {
    int n=s.nextNumber();
    if(n>=2147483647) throw new IllegalArgumentException("Agotamiento del espacio de numeracion.");
    if(intent.title()==null || intent.title().trim().isEmpty()) throw new IllegalArgumentException("Titulo requerido.");
    if(intent.price()==null || intent.price()<=0) throw new IllegalArgumentException("Precio invalido.");
    if(intent.image()==null || intent.image().trim().isEmpty()) throw new IllegalArgumentException("Imagen requerida.");

    int inserted=db.sql("INSERT INTO products(session_id,piece_number,title,price,image_url,sold,updated_at) VALUES(?,?,?,?,?,0,CURRENT_TIMESTAMP)")
      .params(id,n,intent.title(),intent.price(),intent.image()).update();
    if(inserted!=1) throw new IllegalStateException("Fallo al crear producto.");

    db.sql("UPDATE sessions SET next_number=next_number+1, updated_at=CURRENT_TIMESTAMP WHERE id=?")
      .param(id).update();
   }
   case "PRICE" -> {
    if(intent.number()==null || intent.number()<1) throw new IllegalArgumentException("Numero de pieza requerido.");
    if(intent.price()==null || intent.price()<=0) throw new IllegalArgumentException("Precio debe ser entero positivo.");
    int rows=db.sql("UPDATE products SET price=?, updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND piece_number=?")
      .params(intent.price(),id,intent.number()).update();
    if(rows!=1) throw new IllegalArgumentException("Pieza no encontrada en la sesion: #" + intent.number());
   }
   case "IMAGE" -> {
    if(intent.number()==null || intent.number()<1) throw new IllegalArgumentException("Numero de pieza requerido.");
    if(intent.image()==null || intent.image().trim().isEmpty()) throw new IllegalArgumentException("Imagen requerida.");
    int rows=db.sql("UPDATE products SET image_url=?, updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND piece_number=?")
      .params(intent.image(),id,intent.number()).update();
    if(rows!=1) throw new IllegalArgumentException("Pieza no encontrada en la sesion: #" + intent.number());
   }
   case "SOLD" -> {
    if(intent.number()==null || intent.number()<1) throw new IllegalArgumentException("Numero de pieza requerido.");
    int rows=db.sql("UPDATE products SET sold=1, updated_at=CURRENT_TIMESTAMP WHERE session_id=? AND piece_number=?")
      .params(id,intent.number()).update();
    if(rows!=1) throw new IllegalArgumentException("Pieza no encontrada en la sesion: #" + intent.number());
   }
   default -> throw new IllegalArgumentException("No entendí el cambio.");
  }
 }

 @Transactional
 public void restore(String id,List<Product> products){
  valid(id);
  Session s=session(id);
  if(products==null) throw new IllegalArgumentException("Lista de productos no puede ser null.");

  // Prevalidar lote completo antes de mutar
  Set<Integer> seenNumbers = new HashSet<>();
  int maxRestored = 0;
  for(Product p : products){
   if(p==null) throw new IllegalArgumentException("Producto en lista no puede ser null.");
   if(!id.equals(p.sessionId())) throw new IllegalArgumentException("El producto pertenece a otra sesion: " + p.sessionId());
   if(p.pieceNumber()<1 || p.pieceNumber()>2147483646) throw new IllegalArgumentException("piece_number invalido: " + p.pieceNumber());
   if(!seenNumbers.add(p.pieceNumber())) throw new IllegalArgumentException("Numero de pieza duplicado en restore: " + p.pieceNumber());
   if(p.title()==null || p.title().trim().isEmpty()) throw new IllegalArgumentException("title vacio en restore.");
   if(p.price()<=0) throw new IllegalArgumentException("price debe ser entero positivo.");
   if(p.imageUrl()==null || p.imageUrl().trim().isEmpty()) throw new IllegalArgumentException("image_url vacio en restore.");
   if(p.pieceNumber()>maxRestored) maxRestored = p.pieceNumber();
  }

  db.sql("DELETE FROM products WHERE session_id=?").param(id).update();
  for(Product p : products){
   int inserted=db.sql("INSERT INTO products(session_id,piece_number,title,price,image_url,sold,updated_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)")
     .params(id,p.pieceNumber(),p.title(),p.price(),p.imageUrl(),p.sold()?1:0).update();
   if(inserted!=1) throw new IllegalStateException("Error al restaurar producto.");
  }

  // Conservar max(contador anterior, max(número restaurado)+1, 9)
  int newNext = Math.max(s.nextNumber(), Math.max(maxRestored + 1, 9));
  db.sql("UPDATE sessions SET next_number=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")
    .params(newNext, id).update();
 }

 @Transactional
 public void reset(String id){
  valid(id);
  ensure(id);
  Session s=session(id);

  db.sql("DELETE FROM products WHERE session_id=?").param(id).update();
  int cloned=db.sql("INSERT INTO products(session_id,piece_number,title,price,image_url,sold,updated_at) " +
    "SELECT ?,piece_number,title,price,image_url,sold,CURRENT_TIMESTAMP FROM products WHERE session_id='DEFAULT'")
    .param(id).update();
  if(cloned!=8) throw new IllegalStateException("Fallo al resetear catalogo con DEFAULT.");

  // Preservar high-water contador, limpiar temporales y snapshot, state IDLE
  int newNext = Math.max(s.nextNumber(), 9);
  db.sql("UPDATE sessions SET state='IDLE', pending=NULL, temp_image=NULL, snapshot=NULL, next_number=?, updated_at=CURRENT_TIMESTAMP WHERE id=?")
    .params(newNext, id).update();
 }

}
