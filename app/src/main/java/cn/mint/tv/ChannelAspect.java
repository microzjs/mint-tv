package cn.mint.tv;
import org.json.*;
public final class ChannelAspect {
 public static JSONObject defaults(String saved){JSONObject out;try{out=new JSONObject(saved);}catch(Exception e){out=new JSONObject();}try{if(!out.has(Playlist.key("宁夏卫视")))out.put(Playlist.key("宁夏卫视"),1);if(!out.has(Playlist.key("河北卫视")))out.put(Playlist.key("河北卫视"),1);}catch(Exception ignored){}return out;}
 public static int mode(String saved,String name){try{int value=new JSONObject(saved).optInt(Playlist.key(name),0);return value>=0&&value<=4?value:0;}catch(Exception e){return 0;}}
}
