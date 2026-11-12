package com.norma.taller;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
@Repository
public class Catalog {
 public record Product(long id,String sessionId,int pieceNumber,String title,int price,String imageUrl,boolean sold) {}
 public record Session(String id,String state,String pending,String tempImage,String snapshot,int nextNumber) {}
 private final JdbcClient db;
 public Catalog(JdbcClient db){this.db=db;}
 public static String valid(String id){
  if(id==null || !id.matches("[a-zA-Z0-9_-]{8,80}") || id.equals("DEFAULT")) throw new IllegalArgumentException("La sesión no es válida."); return id;
 }
 @Transactional public void ensure(String id){
  valid(id);
  int added=db.sql("INSERT OR IGNORE INTO sessions(id) VALUES(?)").param(id).update();
  if(added>0){
   String[] titles={"Cuenco rústico","Jarrón de gres","Taza panzona","Maceta de barro","Plato del taller","Florero azul","Compotera","Vasito de cerámica"};
   for(int i=0;i<8;i++) insert(id,i+1,titles[i],new int[]{15000,18500,12000,20000,14000,24000,16500,9000}[i],"/assets/pieza-"+(i+1)+".webp",false);
  }
 }
 public List<Product> all(String id){return db.sql("SELECT id,session_id,piece_number,title,price,image_url,sold FROM products WHERE session_id=? ORDER BY piece_number").param(id).query(Product.class).list();}
 public Session session(String id){return db.sql("SELECT id,state,pending,temp_image,snapshot,next_number FROM sessions WHERE id=?").param(id).query(Session.class).single();}
 public void conversation(String id,String state,String pending,String image){db.sql("UPDATE sessions SET state=?,pending=?,temp_image=? WHERE id=?").params(Arrays.asList(state,pending,image,id)).update();}
 public void snapshot(String id,String json){db.sql("UPDATE sessions SET snapshot=? WHERE id=?").params(Arrays.asList(json,id)).update();}
 public void insert(String id,int number,String title,int price,String image,boolean sold){db.sql("INSERT INTO products(session_id,piece_number,title,price,image_url,sold) VALUES(?,?,?,?,?,?)").params(id,number,title,price,image,sold?1:0).update();}
 public void apply(String id,IntentParser.Intent intent){
  switch(intent.kind()){
   case "CREATE" -> {int n=session(id).nextNumber();insert(id,n,intent.title(),intent.price(),intent.image(),false);db.sql("UPDATE sessions SET next_number=next_number+1 WHERE id=?").param(id).update();}
   case "PRICE" -> db.sql("UPDATE products SET price=? WHERE session_id=? AND piece_number=?").params(intent.price(),id,intent.number()).update();
   case "IMAGE" -> db.sql("UPDATE products SET image_url=? WHERE session_id=? AND piece_number=?").params(intent.image(),id,intent.number()).update();
   case "SOLD" -> db.sql("UPDATE products SET sold=1 WHERE session_id=? AND piece_number=?").params(id,intent.number()).update();
   default -> throw new IllegalArgumentException("No entendí el cambio.");
  }
 }
 public void restore(String id,List<Product> products){
  db.sql("DELETE FROM products WHERE session_id=?").param(id).update();
  for(var p:products)insert(id,p.pieceNumber(),p.title(),p.price(),p.imageUrl(),p.sold());
 }
 @Transactional public void reset(String id){
  valid(id);db.sql("DELETE FROM products WHERE session_id=?").param(id).update();db.sql("DELETE FROM sessions WHERE id=?").param(id).update();ensure(id);
 }
}
