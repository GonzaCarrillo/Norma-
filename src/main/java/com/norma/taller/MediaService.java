package com.norma.taller;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.LinkedMultiValueMap;
@Service
public class MediaService {
 final Path root;private final String key;
 private final RestClient groq;
 public MediaService(@Value("${norma.uploads}")String dir,@Value("${groq.api.key}")String key)throws IOException{
 root=Path.of(dir).toAbsolutePath();Files.createDirectories(root);this.key=key;
 var http=java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(20));
 groq=RestClient.builder().baseUrl("https://api.groq.com/openai/v1").requestFactory(factory).build();
 }
 public String image(MultipartFile file)throws IOException{
 if(file.isEmpty()||file.getSize()>15*1024*1024)throw new IllegalArgumentException("La foto está vacía o pesa más de 15 MB.");
 java.awt.image.BufferedImage source;
 try(var input=ImageIO.createImageInputStream(file.getInputStream())){
 var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Mandame una foto JPEG, PNG o WebP válida.");
 var reader=readers.next();try{reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>24000000)throw new IllegalArgumentException("La foto es demasiado grande. Reducila a menos de 24 megapíxeles.");source=reader.read(0);}finally{reader.dispose();}
 }
 var img=Thumbnails.of(source).size(800,600).crop(Positions.CENTER).asBufferedImage();
 String name=UUID.randomUUID()+".webp";Path dest=root.resolve(name);
 var writer=ImageIO.getImageWritersByMIMEType("image/webp").next();
 try{
 for(float quality=.8f;quality>=.1f;quality-=.1f){
 try(ImageOutputStream out=ImageIO.createImageOutputStream(dest.toFile())){
 writer.setOutput(out);var param=writer.getDefaultWriteParam();param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);param.setCompressionType(param.getCompressionTypes()[0]);param.setCompressionQuality(quality);writer.write(null,new IIOImage(img,null,null),param);
 }
 if(Files.size(dest)<80000)return "/media/"+name;
 }
 Files.deleteIfExists(dest);throw new IllegalArgumentException("No pude reducir esta foto. Probá con otra.");
 }finally{writer.dispose();}
 }
 public String transcribe(MultipartFile audio)throws IOException{
 if(key.isBlank())throw new IllegalArgumentException("La transcripción no está configurada. Podés escribir el mensaje o usar un ejemplo.");
 Path temp=Files.createTempFile("norma-audio-", ".webm");
 try{
 audio.transferTo(temp);var body=new LinkedMultiValueMap<String,Object>();body.add("file",new FileSystemResource(temp));body.add("model","whisper-large-v3-turbo");body.add("language","es");
 var result=groq.post().uri("/audio/transcriptions").header("Authorization","Bearer "+key).contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(com.fasterxml.jackson.databind.JsonNode.class);
 if(result==null||result.path("text").asText().isBlank())throw new IllegalArgumentException("No pude escuchar el audio. Probá otra vez o escribime.");
 return result.path("text").asText();
 }catch(org.springframework.web.client.RestClientException e){throw new IllegalArgumentException("No pude transcribir ahora. Probá otra vez o escribime.");}finally{Files.deleteIfExists(temp);}
 }
 public String speak(String text){
 String name=UUID.randomUUID()+".mp3";Path file=root.resolve(name);Process p=null;
 try{
 p=new ProcessBuilder("edge-tts","--voice","es-AR-TomasNeural","--text",text,"--write-media",file.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
 if(p.waitFor(12,TimeUnit.SECONDS)&&p.exitValue()==0&&Files.size(file)>0)return "/media/"+name;
 }catch(Exception ignored){if(ignored instanceof InterruptedException)Thread.currentThread().interrupt();}
 finally{if(p!=null&&p.isAlive())p.destroyForcibly();}
 try{Files.deleteIfExists(file);}catch(IOException ignored){}return null;
 }
}
