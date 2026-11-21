package cn.mint.tv;

import android.content.*;
import android.app.job.*;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Store {
 public static final java.util.concurrent.ExecutorService IO=java.util.concurrent.Executors.newSingleThreadExecutor();
 public static final String DEFAULT="https://raw.githubusercontent.com/xisohi/CHINA-IPTV/main/Unicast/fujian/mobile.txt";
 public static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("mint",0);}
 public static String read(InputStream in)throws IOException {try(InputStream s=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){
  byte[] a=new byte[8192];int n;while((n=s.read(a))!=-1){if(b.size()+n>8*1024*1024)throw new IOException("列表超过 8MB");b.write(a,0,n);}byte[] bytes=b.toByteArray();try{return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException e){return new String(bytes,java.nio.charset.Charset.forName("GB18030"));}}}
 public static String fetch(String url)throws Exception {
  URL u=new URL(url);for(int i=0;i<6;i++){
   if(!u.getProtocol().equals("http")&&!u.getProtocol().equals("https"))throw new IOException("请输入 HTTP/HTTPS 地址");
   HttpURLConnection h=(HttpURLConnection)u.openConnection();h.setConnectTimeout(12000);h.setReadTimeout(15000);h.setInstanceFollowRedirects(false);h.setRequestProperty("User-Agent","MintTV/1.0");
   try {int code=h.getResponseCode();if(code>=300&&code<400){u=new URL(u,h.getHeaderField("Location"));continue;}if(code!=200)throw new IOException("HTTP "+code);return read(h.getInputStream());}finally{h.disconnect();}
  }throw new IOException("重定向次数过多");
 }
 public static synchronized JSONArray imports(Context c){try{return new JSONArray(prefs(c).getString("imports","[]"));}catch(Exception e){return new JSONArray();}}
 public static synchronized List<Playlist.Channel> channels(Context c){
  String base=prefs(c).getString("base","");if(base.isEmpty())try{base=read(c.getAssets().open("fujian-mobile.txt"));}catch(Exception ignored){}
  List<Playlist.Channel> all=Playlist.parse(base);JSONArray a=imports(c);
  for(int i=0;i<a.length();i++)all=Playlist.merge(all,Playlist.parse(a.optJSONObject(i).optString("text")));return all;
 }
 public static synchronized void add(Context c,String title,String url,String text)throws Exception{
  if(Playlist.parse(text).isEmpty())throw new IOException("未找到有效频道，请选择 M3U 或 TXT 列表");
  JSONArray a=imports(c);JSONObject o=new JSONObject();o.put("title",title);o.put("url",url);o.put("text",text);
  for(int i=a.length()-1;i>=0;i--)if(!url.isEmpty()&&a.getJSONObject(i).optString("url").equals(url))a.remove(i);
  a.put(o);prefs(c).edit().putString("imports",a.toString()).commit();
 }
 public static synchronized void remove(Context c,int index){JSONArray a=imports(c);a.remove(index);prefs(c).edit().putString("imports",a.toString()).commit();}
 public static String update(Context c){
  int ok=0,fail=0;String reason="";
  try{String text=fetch(DEFAULT);if(Playlist.parse(text).isEmpty())throw new IOException("远端列表为空");prefs(c).edit().putString("base",text).putLong("baseTime",System.currentTimeMillis()).commit();ok++;}catch(Exception e){fail++;reason=e.getMessage();}
  JSONArray a=imports(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);String url=o.optString("url");if(url.isEmpty())continue;
   try{String t=fetch(url);if(Playlist.parse(t).isEmpty())throw new IOException("列表为空");synchronized(Store.class){JSONArray latest=imports(c);for(int j=0;j<latest.length();j++)if(latest.optJSONObject(j).optString("url").equals(url))latest.optJSONObject(j).put("text",t);prefs(c).edit().putString("imports",latest.toString()).commit();}ok++;}catch(Exception e){fail++;}
  }
  String result=fail==0?"更新完成 · "+ok+" 个订阅":"已更新 "+ok+" 个 · "+fail+" 个失败，保留原列表"+(reason.isEmpty()?"":"\n"+reason);
  prefs(c).edit().putLong("attempt",System.currentTimeMillis()).putString("result",result).commit();return result;
 }
 public static void schedule(Context c){JobScheduler s=c.getSystemService(JobScheduler.class);s.schedule(new JobInfo.Builder(2401,new ComponentName(c,UpdateJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(24*60*60*1000L).setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
}

