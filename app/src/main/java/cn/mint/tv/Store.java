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
 public static android.content.SharedPreferences prefs(Context c){return LocalState.open(c);}
 public static String read(InputStream in)throws IOException {try(InputStream s=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){
  byte[] a=new byte[8192];int n;while((n=s.read(a))!=-1){if(b.size()+n>8*1024*1024)throw new IOException("列表超过 8MB");b.write(a,0,n);}byte[] bytes=b.toByteArray();try{return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException e){return new String(bytes,java.nio.charset.Charset.forName("GB18030"));}}}
 public static String fetch(String url)throws Exception {
  return fetch(url,12000,15000);
 }
 public static String fetch(String url,int connectTimeout,int readTimeout)throws Exception {
  URL u=new URL(url);for(int i=0;i<6;i++){
   if(!u.getProtocol().equals("http")&&!u.getProtocol().equals("https"))throw new IOException("请输入 HTTP/HTTPS 地址");
   HttpURLConnection h=(HttpURLConnection)u.openConnection();h.setConnectTimeout(connectTimeout);h.setReadTimeout(readTimeout);h.setInstanceFollowRedirects(false);h.setRequestProperty("User-Agent","MintTV/1.1");
   try {int code=h.getResponseCode();if(code>=300&&code<400){u=new URL(u,h.getHeaderField("Location"));continue;}if(code!=200)throw new IOException("HTTP "+code);return read(h.getInputStream());}finally{h.disconnect();}
  }throw new IOException("重定向次数过多");
 }
 public static synchronized JSONArray imports(Context c){try{return new JSONArray(prefs(c).getString("imports","[]"));}catch(Exception e){return new JSONArray();}}
 private static String cachedBase,cachedImports; private static List<Playlist.Channel> cachedChannels;
 public static synchronized List<Playlist.Channel> channels(Context c){
  String baseKey=prefs(c).getString("base",""),importKey=prefs(c).getString("imports","[]");
  if(cachedChannels!=null&&baseKey==cachedBase&&importKey==cachedImports)return new ArrayList<>(cachedChannels);
  String base=prefs(c).getString("base","");
  List<Playlist.Channel> all=Playlist.parse(base);JSONArray a=imports(c);
  for(int i=0;i<a.length();i++)all=Playlist.merge(all,Playlist.parse(a.optJSONObject(i).optString("text")));cachedBase=baseKey;cachedImports=importKey;cachedChannels=all;return new ArrayList<>(all);
 }
 public static synchronized void add(Context c,String title,String url,String text)throws Exception{
  if(Playlist.parse(text).isEmpty())throw new IOException("未找到有效频道，请选择 M3U 或 TXT 列表");
  JSONArray a=imports(c);JSONObject o=new JSONObject();o.put("title",title);o.put("url",url);o.put("text",text);
  for(int i=a.length()-1;i>=0;i--)if(!url.isEmpty()&&a.getJSONObject(i).optString("url").equals(url))a.remove(i);
  a.put(o);if(a.toString().getBytes(StandardCharsets.UTF_8).length>24*1024*1024)throw new IOException("导入源总量超过 24MB 限制");prefs(c).edit().putString("imports",a.toString()).apply();
 }
 public static synchronized void remove(Context c,int index){JSONArray a=imports(c);a.remove(index);prefs(c).edit().putString("imports",a.toString()).apply();}
 public static NetworkProfile activeProfile(Context c){
  return NetworkProfile.saved(prefs(c).getString("province",""),prefs(c).getString("carrier",""));
 }
 public static String profileLabel(Context c){NetworkProfile p=activeProfile(c);return p==null?"尚未匹配网络":p.label();}
 public static boolean isManual(Context c){return prefs(c).contains("manualProvince");}
 public static void manual(Context c,NetworkProfile p){prefs(c).edit().putString("manualProvince",p.province).putString("manualCarrier",p.carrier).apply();}
 public static void automatic(Context c){prefs(c).edit().remove("manualProvince").remove("manualCarrier").putLong("geoTime",0).apply();}
 public static final class RefreshResult {
  public final String message;public final boolean changed,profileChanged;
  RefreshResult(String m,boolean c,boolean p){message=m;changed=c;profileChanged=p;}
 }
 public static String update(Context c){return refresh(c,true,true).message;}
 /** Called on IO only. Swap playlist and its profile in one durable preferences transaction. */
 public static RefreshResult refresh(Context c,boolean forceLists,boolean forceDetection){
  long now=System.currentTimeMillis();NetworkProfile active=activeProfile(c),desired=active;
  String geoNote="";
  if(isManual(c)){
   desired=new NetworkProfile(prefs(c).getString("manualProvince",""),prefs(c).getString("manualCarrier",""));
   geoNote="手动选择 · "+desired.label();
  }else if(forceDetection||now-prefs(c).getLong("geoTime",0)>60000){
   try{desired=NetworkResolver.resolve();geoNote="自动识别 · "+desired.label();android.util.Log.i("MintTV","NETWORK_MATCH "+desired.key());
    prefs(c).edit().putString("detectedProvince",desired.province).putString("detectedCarrier",desired.carrier).apply();
   }catch(Exception e){geoNote="暂时无法识别地区，已保留原设置；可手动选择省份与运营商";android.util.Log.w("MintTV","NETWORK_LOOKUP_FAILED retained="+(active==null?"none":active.key()));}
   prefs(c).edit().putLong("geoTime",now).putString("geoStatus",geoNote).apply();
  }else{
   try{NetworkProfile cached=NetworkProfile.saved(prefs(c).getString("detectedProvince",""),prefs(c).getString("detectedCarrier",""));if(cached!=null)desired=cached;}catch(Exception ignored){}
   geoNote=prefs(c).getString("geoStatus","");
  }
  boolean profileChanged=desired!=null&&(active==null||!active.sameNetwork(desired));
  boolean due=forceLists||now-prefs(c).getLong("baseTime",0)>24*3600000L;
  boolean needBase=desired!=null&&(profileChanged||due||!prefs(c).contains("province"));
  int imported=0,importFailed=0;boolean baseSuccess=false;int ok=0,fail=0;boolean changed=false,swapped=false;String reason="";
  if(needBase){
   try{PlaylistDownload.Result downloaded=PlaylistDownload.load(desired.url());String text=downloaded.text;if(Playlist.parse(text).isEmpty())throw new IOException("匹配地区的源列表为空");
    prefs(c).edit().putString("base",text).putString("province",desired.province).putString("carrier",desired.carrier)
     .putString("baseOrigin",downloaded.origin).putLong("baseTime",now).apply();ok++;baseSuccess=true;changed=true;swapped=profileChanged;android.util.Log.i("MintTV","PLAYLIST_UPDATED "+desired.key());
   }catch(Exception e){fail++;reason="暂时无法获取匹配列表，请检查网络或手动选择地区";}
  }
  if(due){JSONArray a=imports(c);for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);String url=o.optString("url");if(url.isEmpty())continue;
   try{String t=fetch(url);if(Playlist.parse(t).isEmpty())throw new IOException("列表为空");synchronized(Store.class){JSONArray latest=imports(c);for(int j=0;j<latest.length();j++)if(latest.optJSONObject(j).optString("url").equals(url))latest.optJSONObject(j).put("text",t).put("updateError","");prefs(c).edit().putString("imports",latest.toString()).apply();}ok++;imported++;changed=true;}catch(Exception e){fail++;importFailed++;synchronized(Store.class){JSONArray latest=imports(c);for(int j=0;j<latest.length();j++)if(latest.optJSONObject(j).optString("url").equals(url))try{latest.optJSONObject(j).put("updateError","更新失败，保留原频道；请检查订阅地址或服务是否仍可访问");}catch(Exception ignored){}prefs(c).edit().putString("imports",latest.toString()).apply();}}
  }}
  String result=RefreshStatus.summary(needBase,baseSuccess,profileLabel(c),imported,importFailed);
  if(desired==null)result="尚未识别省份与运营商，未下载频道；可手动选择地区或导入直播源";
  if(!needBase)android.util.Log.i("MintTV","NETWORK_RETAIN "+(active==null?"none":active.key()));
  if(!geoNote.isEmpty())result+="\n"+geoNote;
  prefs(c).edit().putLong("attempt",now).putString("result",result).apply();
  return new RefreshResult(result,changed,swapped);
 }
 public static void schedule(Context c){
  JobScheduler s=c.getSystemService(JobScheduler.class);
  // Reopening the app must not restart the periodic window or cause an extra daily download.
  for(JobInfo existing:s.getAllPendingJobs())if(existing.getId()==2401)return;
  s.schedule(new JobInfo.Builder(2401,new ComponentName(c,UpdateJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setPeriodic(24*60*60*1000L).setBackoffCriteria(30*60*1000L,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
 }
}

