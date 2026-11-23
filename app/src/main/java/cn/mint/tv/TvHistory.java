package cn.mint.tv;
import org.json.*;
import java.util.*;
/** Stable numbers survive playlist order changes; only twenty recent channels. */
public final class TvHistory {
 public static JSONObject numbers(String saved,List<Playlist.Channel> channels){JSONObject out;try{out=new JSONObject(saved);}catch(Exception e){out=new JSONObject();}Set<Integer> used=new HashSet<>();for(Iterator<String> it=out.keys();it.hasNext();)used.add(out.optInt(it.next()));int n=1;for(Playlist.Channel c:channels){String k=Playlist.key(c.name);if(out.has(k))continue;while(used.contains(n)&&n<=9999)n++;if(n>9999||out.length()>=5000)break;try{out.put(k,n);used.add(n);}catch(Exception ignored){}}return out;}
 public static JSONArray recent(String saved,String key){JSONArray old;try{old=new JSONArray(saved);}catch(Exception e){old=new JSONArray();}JSONArray out=new JSONArray();out.put(key);for(int i=0;i<old.length()&&out.length()<20;i++){String v=old.optString(i);if(!v.equals(key))out.put(v);}return out;}
 public static List<String> recent(String saved){List<String> out=new ArrayList<>();try{JSONArray a=new JSONArray(saved);for(int i=0;i<a.length();i++)out.add(a.getString(i));}catch(Exception ignored){}return out;}
}
