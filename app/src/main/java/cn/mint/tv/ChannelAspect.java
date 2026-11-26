package cn.mint.tv;
import org.json.*;
public final class ChannelAspect {
 public static JSONObject defaults(String saved){JSONObject out;try{out=new JSONObject(saved);}catch(Exception e){out=new JSONObject();}try{for(String name:new String[]{"宁夏卫视","河北卫视","西藏卫视","新疆卫视","康巴卫视","兵团卫视","内蒙古卫视","内蒙古视","康巴藏语卫视"})if(!out.has(Playlist.key(name)))out.put(Playlist.key(name),1);}catch(Exception ignored){}return out;}
 public static int mode(String saved,String name){try{int value=new JSONObject(saved).optInt(Playlist.key(name),0);return value>=0&&value<=4?value:0;}catch(Exception e){return 0;}}
}
