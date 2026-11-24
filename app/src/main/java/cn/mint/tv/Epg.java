package cn.mint.tv;
import android.content.*;
import java.util.*;
import java.io.*;
import java.net.*;
import org.json.*;

public final class Epg {
 public static final String DEFAULT_URL="https://epg.112114.xyz/pp.xml.gz";
 private static final java.util.concurrent.ExecutorService WORK=java.util.concurrent.Executors.newSingleThreadExecutor();
 private static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();
 public static void schedule(Context c,boolean force,Runnable changed){if(!BUSY.compareAndSet(false,true))return;Context app=c.getApplicationContext();WORK.execute(()->{try{refresh(app,force);}finally{BUSY.set(false);if(changed!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(changed);}});}
 public static void defaults(Context c){if(!Store.prefs(c).contains("epgUrl"))Store.prefs(c).edit().putString("epgUrl",DEFAULT_URL).apply();}
 private static String cached;private static JSONObject data=new JSONObject();
 public static void refresh(Context c,boolean force){String url=Store.prefs(c).getString("epgUrl","");if(url.isEmpty()||(!force&&System.currentTimeMillis()-Store.prefs(c).getLong("epgAttempt",0)<6*3600000L))return;
  Store.prefs(c).edit().putLong("epgAttempt",System.currentTimeMillis()).apply();
  try{JSONObject parsed=download(url,Store.channels(c),System.currentTimeMillis());if(!url.equals(Store.prefs(c).getString("epgUrl","")))return;if(parsed.length()==0)throw new IOException("未匹配到当前/下一节目，请核对 tvg-id 或频道名称");
   Store.prefs(c).edit().putString("epgData",parsed.toString()).putLong("epgTime",System.currentTimeMillis()).putString("epgStatus","节目单更新成功 · "+parsed.length()+" 个频道").apply();
  }catch(Exception e){if(!url.equals(Store.prefs(c).getString("epgUrl","")))return;Store.prefs(c).edit().putString("epgStatus","更新失败，保留缓存："+e.getMessage()).apply();}
 }
 static JSONObject download(String address,List<Playlist.Channel> channels,long now)throws Exception{
  Map<String,String> ids=new HashMap<>();Set<String> names=new HashSet<>();for(Playlist.Channel c:channels){String k=Playlist.key(c.name);names.add(k);if(!c.tvgId.isEmpty())ids.put(c.tvgId,k);}
  URL url=new URL(address);for(int i=0;i<3;i++){if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException("仅支持 HTTP/HTTPS 节目单");
   HttpURLConnection h=(HttpURLConnection)url.openConnection();h.setConnectTimeout(10000);h.setReadTimeout(15000);h.setInstanceFollowRedirects(false);
   try{int status=h.getResponseCode();if(status>=300&&status<400){url=new URL(url,h.getHeaderField("Location"));continue;}if(status!=200)throw new IOException("HTTP "+status);
    try(BufferedInputStream in=new BufferedInputStream(h.getInputStream())){in.mark(2);int a=in.read(),b=in.read();in.reset();
     InputStream xml=a==31&&b==139?new java.util.zip.GZIPInputStream(in):in;return EpgParser.parse(xml,ids,names,now);}
   }finally{h.disconnect();}
  }throw new IOException("重定向过多");
 }
 private static synchronized JSONObject data(Context c){String value=Store.prefs(c).getString("epgData","{}");if(value!=cached){try{data=new JSONObject(value);}catch(Exception e){data=new JSONObject();}cached=value;}return data;}
 public static synchronized String logo(Context c,String channel){JSONObject pair=data(c).optJSONObject(Playlist.key(channel));return pair==null?"":pair.optString("logo","");}
 public static synchronized String summary(Context c,String channel){JSONObject pair=data(c).optJSONObject(Playlist.key(channel));if(pair==null)return "节目资料暂不可用";
  long now=System.currentTimeMillis();JSONObject current=pair.optJSONObject("now"),next=pair.optJSONObject("next");String a="当前节目未知",b="";
  if(current!=null&&current.optLong("start")<=now&&current.optLong("end")>now)a="正在播："+current.optString("title");
  if(next!=null&&next.optLong("start")>now)b="\n接下来："+next.optString("title");
  else if(next!=null&&next.optLong("start")<=now&&next.optLong("end")>now)a="正在播："+next.optString("title");
  return a+b;
 }
}
