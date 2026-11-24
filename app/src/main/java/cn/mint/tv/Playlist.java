package cn.mint.tv;

import java.util.*;
import java.util.regex.*;

/** Pure Java playlist parser; insertion order is source priority. */
public final class Playlist {
 public static final class Channel {
  public final String name, group;
  public String tvgId="",logo="";
  public final List<String> urls = new ArrayList<>();
  public Channel(String n,String g){name=n;group=g;}
 }
 public static List<Channel> parse(String text) {
  LinkedHashMap<String,Channel> map=new LinkedHashMap<>();
  String group="导入频道",name="",tvgId="",logo="";
  for(String raw:text.replace("\uFEFF","").split("\r?\n")) {
   String line=raw.trim(); if(line.isEmpty()) continue;
   if(line.startsWith("#EXTINF:")) {
    int comma=line.lastIndexOf(','); name=comma>=0?line.substring(comma+1).trim():"直播";
    Matcher m=Pattern.compile("group-title=\"([^\"]*)\"").matcher(line);
    if(m.find()) group=m.group(1); Matcher id=Pattern.compile("tvg-id=\"([^\"]*)\"").matcher(line);tvgId=id.find()?id.group(1):"";Matcher image=Pattern.compile("tvg-logo=\"([^\"]*)\"").matcher(line);logo=image.find()?image.group(1):"";continue;
   }
   if(line.startsWith("#")) continue;
   int comma=line.indexOf(','); String url=line;
   if(comma>=0 && !line.matches("(?i)^(https?|udp|rtp|rtsp)://.*")) {
    name=line.substring(0,comma).trim();url=line.substring(comma+1).trim();
    tvgId="";logo="";if(url.equals("#genre#")){group=name;name="";continue;}
   }
   if(name.isEmpty()) name="直播";
   for(String u:url.split("#(?=(?:https?|udp|rtsp|rtp)://)")) {
    // TXT sources commonly append $线路名; a $ in a query remains intact.
    int label=u.indexOf('$'); if(label>0 && !u.substring(0,label).contains("?"))u=u.substring(0,label);
    u=u.trim();if(!u.matches("(?i)^(https?|udp|rtp|rtsp)://.+")) continue;
    String key=key(name); Channel c=map.get(key);
    if(c==null){c=new Channel(name,group);map.put(key,c);}
    if(c.tvgId.isEmpty())c.tvgId=tvgId;if(c.logo.isEmpty())c.logo=logo;
    if(!c.urls.contains(u)) c.urls.add(u);
   }
  }
  return new ArrayList<>(map.values());
 }
 public static String key(String name){return name.replaceAll("\\s+","").toUpperCase(Locale.ROOT);}
 public static List<Channel> merge(List<Channel> a,List<Channel>b){
  LinkedHashMap<String,Channel> m=new LinkedHashMap<>();
  for(List<Channel> list:Arrays.asList(a,b))for(Channel c:list){
   Channel d=m.get(key(c.name));if(d==null){d=new Channel(c.name,c.group);m.put(key(c.name),d);}
   if(d.tvgId.isEmpty())d.tvgId=c.tvgId;if(d.logo.isEmpty())d.logo=c.logo;for(String u:c.urls)if(!d.urls.contains(u))d.urls.add(u);
  }return new ArrayList<>(m.values());
 }
}
