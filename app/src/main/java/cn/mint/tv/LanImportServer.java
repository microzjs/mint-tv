package cn.mint.tv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Short-lived LAN import session, bounded requests and no third-party HTTP runtime. */
public final class LanImportServer implements AutoCloseable {
 public interface Importer { String link(String url)throws Exception;String file(String name,byte[] bytes)throws Exception; }
 private final ServerSocket server;private final String token,host;private final Importer importer;private final byte[] page;
 private final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<>());
 private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2));
 private final ScheduledExecutorService expiry=Executors.newSingleThreadScheduledExecutor();
 private volatile boolean closed;private final long started=System.nanoTime();
 public LanImportServer(InetAddress address,byte[] page,Importer importer)throws IOException{
  if(!(address.isSiteLocalAddress()||address.isLoopbackAddress()))throw new IOException("请连接局域网");
  this.page=page;this.importer=importer;byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);StringBuilder key=new StringBuilder();for(byte b:bytes)key.append(String.format(Locale.ROOT,"%02x",b&255));token=key.toString();
  server=new ServerSocket();server.bind(new InetSocketAddress(address,0),4);host=address.getHostAddress()+":"+server.getLocalPort();
  Thread thread=new Thread(this::accept,"mint-lan-import");thread.setDaemon(true);thread.start();expiry.schedule(this::close,10,TimeUnit.MINUTES);
 }
 public String url(){return "http://"+host+"/"+token+"/";}public boolean active(){return !closed&&System.nanoTime()-started<TimeUnit.MINUTES.toNanos(10);}
 private void accept(){while(!closed)try{Socket socket=server.accept();socket.setSoTimeout(10000);sockets.add(socket);try{workers.execute(()->serve(socket));}catch(RejectedExecutionException e){sockets.remove(socket);socket.close();}}catch(IOException e){if(!closed)close();}}
 private void serve(Socket socket){try(Socket client=socket){BufferedInputStream in=new BufferedInputStream(client.getInputStream());OutputStream out=client.getOutputStream();String request=line(in,2048);String[] parts=request.split(" ");if(parts.length!=3||!parts[2].startsWith("HTTP/1.")){reply(out,400,"text/plain","请求格式错误".getBytes(StandardCharsets.UTF_8));return;}
  Map<String,String> headers=new HashMap<>();int size=request.length();for(int i=0;;i++){String h=line(in,8192);size+=h.length();if(i>32||size>16384)throw new IOException("请求头过大");if(h.isEmpty())break;int colon=h.indexOf(':');if(colon<=0)throw new IOException("请求头无效");String key=h.substring(0,colon).trim().toLowerCase(Locale.ROOT);if(headers.containsKey(key))throw new IOException("重复请求头");headers.put(key,h.substring(colon+1).trim());}
  if(!active()){reply(out,410,"text/plain","会话已关闭".getBytes(StandardCharsets.UTF_8));return;}
  if(!host.equalsIgnoreCase(headers.get("host"))){reply(out,403,"text/plain",new byte[0]);return;}
  String prefix="/"+token+"/",path=parts[1];if(!path.startsWith(prefix)){reply(out,404,"text/plain",new byte[0]);return;}
  if(parts[0].equals("GET")&&path.equals(prefix)){reply(out,200,"text/html; charset=utf-8",page);return;}
  if(!parts[0].equals("POST")){reply(out,405,"text/plain",new byte[0]);return;}
  String origin=headers.get("origin");if(origin!=null&&!origin.equals("http://"+host)){reply(out,403,"text/plain",new byte[0]);return;}
  if(headers.containsKey("transfer-encoding")){reply(out,400,"text/plain",new byte[0]);return;}
  long length;try{length=Long.parseLong(headers.get("content-length"));}catch(Exception e){reply(out,411,"text/plain",new byte[0]);return;}
  int max=path.equals(prefix+"api/url")?8192:8*1024*1024;if(length<=0||length>max){reply(out,413,"text/plain","文件最多 8 MB".getBytes(StandardCharsets.UTF_8));return;}
  if(!path.equals(prefix+"api/url")&&!path.equals(prefix+"api/file")){reply(out,404,"text/plain",new byte[0]);return;}
  byte[] body=new byte[(int)length];int at=0;while(at<body.length){int n=in.read(body,at,body.length-at);if(n<0)throw new EOFException();at+=n;}if(!active())return;
  try{String message;if(path.endsWith("api/url")){String type=headers.get("content-type");if(type==null||!type.startsWith("application/json")){reply(out,415,"text/plain",new byte[0]);return;}String url=new JSONObject(new String(body,StandardCharsets.UTF_8)).getString("url").trim();BackupData.http(url);message=importer.link(url);}else{String filename=URLDecoder.decode(headers.containsKey("x-mint-filename")?headers.get("x-mint-filename"):"直播列表.txt","UTF-8");if(filename.length()>256||!filename.toLowerCase(Locale.ROOT).matches(".*\\.(m3u|m3u8|txt)$"))throw new IOException("请选择 M3U / M3U8 / TXT 文件");message=importer.file(filename,body);}reply(out,200,"application/json",new JSONObject().put("ok",true).put("message",message).toString().getBytes(StandardCharsets.UTF_8));}
  catch(Exception e){String message=e instanceof IllegalArgumentException||e instanceof IOException?e.getMessage():"导入失败，请检查地址和文件内容";reply(out,400,"application/json",new JSONObject().put("ok",false).put("message",message).toString().getBytes(StandardCharsets.UTF_8));}
 }catch(Exception ignored){}finally{sockets.remove(socket);}}
 private static String line(InputStream in,int max)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int b;while((b=in.read())!=-1){if(b=='\n')return new String(out.toByteArray(),StandardCharsets.US_ASCII).replace("\r","");if(out.size()>=max)throw new IOException("请求行过长");out.write(b);}throw new EOFException();}
 private static void reply(OutputStream out,int status,String type,byte[] data)throws IOException{String headers="HTTP/1.1 "+status+" Result\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'\r\n\r\n";out.write(headers.getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();}
 public void close(){if(closed)return;closed=true;try{server.close();}catch(IOException ignored){}synchronized(sockets){for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}sockets.clear();}workers.shutdownNow();expiry.shutdownNow();}
}
