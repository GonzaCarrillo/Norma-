package com.norma.taller;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
@Service
public class IntentParser {
 public record Intent(String kind,Integer number,String title,Integer price,String image){}
 public static String norm(String s){return Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","").replaceAll("[¡!¿?.,]"," ").replaceAll("\\s+"," ").trim();}
 private static final Map<String,Integer> WORDS=new HashMap<>();
 static {
 String[] units={"cero","uno","dos","tres","cuatro","cinco","seis","siete","ocho","nueve","diez","once","doce","trece","catorce","quince","dieciseis","diecisiete","dieciocho","diecinueve","veinte","veintiuno","veintidos","veintitres","veinticuatro","veinticinco","veintiseis","veintisiete","veintiocho","veintinueve"};
 for(int i=0;i<units.length;i++)WORDS.put(units[i],i);
 String[] tens={"treinta","cuarenta","cincuenta","sesenta","setenta","ochenta","noventa"};
 for(int i=0;i<tens.length;i++)WORDS.put(tens[i],30+10*i);
 String[] hundreds={"cien","doscientos","trescientos","cuatrocientos","quinientos","seiscientos","setecientos","ochocientos","novecientos"};
 for(int i=0;i<hundreds.length;i++)WORDS.put(hundreds[i],100*(i+1));WORDS.put("ciento",100);
 }
 public Integer price(String raw){
 if(raw.matches(".*-\\s*\\d.*"))return null;
 String s=norm(raw.replaceAll("(?<=\\d)\\.(?=\\d{3})",""));
 var digit=Pattern.compile("(?<!\\d)(\\d{1,9})(?:\\s*(mil|lucas))?").matcher(s);
 if(digit.find()){long v=Long.parseLong(digit.group(1));if(digit.group(2)!=null)v*=1000;return checked(v);}
 long total=0,chunk=0; boolean found=false;int prev=0;
 for(String w:s.split(" ")){
 if(w.equals("mil")||w.equals("lucas")){total+=(chunk==0?1:chunk)*1000;chunk=0;found=true;prev=0;}
 else if(WORDS.containsKey(w)){int v=WORDS.get(w);if(prev>0&&prev<100&&v>=100&&total==0){total=chunk*1000;chunk=0;}chunk+=v;prev=v;found=true;}
 else if(found&&!w.equals("y"))break;
 }return found?checked(total+chunk):null;
 }
 private Integer checked(long n){return n>0&&n<=100000000?(int)n:null;}
 public Intent parse(String raw,String image,List<Catalog.Product> products){
 String s=norm(raw);
 if(s.contains("equivoque")||s.contains("volve atras")||s.equals("deshacer"))return new Intent("UNDO",null,null,null,null);
 String kind=s.matches(".*(vendi|vendida|saca|quita|retira).*")?"SOLD":s.matches(".*(cambia.*foto|foto.*cambia|foto.*pieza|foto.*numero).*")?"IMAGE":s.matches(".*(subi|baja|precio|cobr|vale|ponele|cambia).*")?"PRICE":"CREATE";
 if(s.contains("subila")||s.contains("bajala")||s.contains("precio")||s.contains("ponele")||s.contains("cambiale"))kind="PRICE";
 Integer number=null;
 var m=Pattern.compile("(?:#|numero\\s+|pieza\\s+)(\\d+|[a-z]+)").matcher(s);
 if(m.find()){try{number=Integer.valueOf(m.group(1));}catch(NumberFormatException e){number=WORDS.get(m.group(1));}}
 if(!kind.equals("CREATE")){
  final Integer n=number;
  var matches=products.stream().filter(p->!p.sold()).filter(p->n!=null?p.pieceNumber()==n:s.contains(norm(p.title()))).toList();
  if(matches.size()!=1)throw new IllegalArgumentException("Decime el numerito de una pieza disponible para no confundirlas.");
  number=matches.getFirst().pieceNumber();
 }
 String amount=s;
 var a=Pattern.compile("(?:\\s+a\\s+|\\s+por\\s+|\\s+precio\\s+)(.+)$").matcher(s);
 if(a.find())amount=a.group(1);else if(!kind.equals("CREATE"))amount=s.replaceFirst("(?:numero|pieza|la)\\s+(?:\\d+|[a-z]+)","");
 Integer price=kind.equals("CREATE")||kind.equals("PRICE")?price(amount):null;
 if((kind.equals("CREATE")||kind.equals("PRICE"))&&price==null)throw new IllegalArgumentException("Decime un precio mayor a cero, por ejemplo quince mil pesos.");
 String title=raw.replaceFirst("(?i)^(?:agregá|agrega|publicá|publica|subí|subi|es |esta es )\\s*","").replaceFirst("(?i)\\s+(?:a|por)\\s+.*$","").trim();
 if(kind.equals("CREATE")&&(title.isBlank()||title.length()>100))throw new IllegalArgumentException("Decime un nombre de hasta cien letras.");
 return new Intent(kind,number,title,price,image);
 }
}
