package cn.mint.tv;

import org.json.*;

import java.util.*;



public final class BackupData {

 public static final Set<String> KEYS=new HashSet<>(Arrays.asList("base","imports","province","carrier","manualProvince","manualCarrier","favorites","recent","channelNumbers","epgUrl","channelAspects"));

 public static JSONObject export(Map<String,?> state)throws Exception{JSONObject payload=new JSONObject();for(String k:KEYS)if(state.containsKey(k)){Object v=state.get(k);payload.put(k,v instanceof Set?new JSONArray((Set<?>)v):v);}return new JSONObject().put("format","mint-tv-backup").put("version",1).put("data",payload);}

 public static Map<String,Object> validate(String text)throws Exception{

  if(text.length()>32*1024*1024)throw new IllegalArgumentException("备份超过限制");JSONObject file=new JSONObject(text);

  if(!file.optString("format").equals("mint-tv-backup")||file.optInt("version")!=1)throw new IllegalArgumentException("不支持的备份格式");

  JSONObject data=file.getJSONObject("data");Map<String,Object> out=new HashMap<>();

  for(Iterator<String> it=data.keys();it.hasNext();){String key=it.next();if(!KEYS.contains(key))throw new IllegalArgumentException("备份含未知字段");Object value=data.get(key);

   if(key.equals("favorites")){if(!(value instanceof JSONArray))throw new IllegalArgumentException("收藏格式错误");JSONArray a=(JSONArray)value;if(a.length()>5000)throw new IllegalArgumentException("收藏过多");Set<String> set=new HashSet<>();for(int i=0;i<a.length();i++){String n=a.getString(i);if(n.length()>256)throw new IllegalArgumentException("收藏名称过长");set.add(n);}value=set;}

   else if(!(value instanceof String))throw new IllegalArgumentException("字段格式错误："+key);

   out.put(key,value);

  }

  String province=(String)out.get("province"),carrier=(String)out.get("carrier");if(province!=null||carrier!=null)new NetworkProfile(province==null?"":province,carrier==null?"":carrier);

  if(out.containsKey("manualProvince")||out.containsKey("manualCarrier"))new NetworkProfile((String)get(out,"manualProvince",""),(String)get(out,"manualCarrier",""));

  String base=(String)out.get("base");if(base!=null&&!base.isEmpty()&&Playlist.parse(base).isEmpty())throw new IllegalArgumentException("备份中无有效默认源");

  JSONArray imports=new JSONArray((String)get(out,"imports","[]"));if(imports.length()>100)throw new IllegalArgumentException("订阅过多");for(int i=0;i<imports.length();i++){JSONObject v=imports.getJSONObject(i);if(Playlist.parse(v.getString("text")).isEmpty())throw new IllegalArgumentException("订阅内容无效");String url=v.optString("url");if(!url.isEmpty())http(url);}

  String epg=(String)get(out,"epgUrl","");if(!epg.isEmpty())http(epg);

  JSONArray recent=new JSONArray((String)get(out,"recent","[]"));if(recent.length()>20)throw new IllegalArgumentException("最近观看过多");for(int i=0;i<recent.length();i++)recent.getString(i);

  JSONObject numbers=new JSONObject((String)get(out,"channelNumbers","{}"));if(numbers.length()>5000)throw new IllegalArgumentException("频道编号过多");Set<Integer> used=new HashSet<>();for(Iterator<String> it=numbers.keys();it.hasNext();){int n=numbers.getInt(it.next());if(n<1||n>9999||!used.add(n))throw new IllegalArgumentException("频道编号重复或越界");}

  JSONObject aspects=new JSONObject((String)get(out,"channelAspects","{}"));if(aspects.length()>5000)throw new IllegalArgumentException("画面比例记录过多");for(Iterator<String> it=aspects.keys();it.hasNext();){int mode=aspects.getInt(it.next());if(mode<0||mode>4)throw new IllegalArgumentException("画面比例无效");}
  return out;

 }

 public static Map<String,Object> restored(Map<String,?> current,Map<String,Object> replacement){

  boolean same=true;for(String k:Arrays.asList("base","imports","province","carrier"))if(!Objects.equals(current.get(k),replacement.get(k)))same=false;

  Map<String,Object> next=new HashMap<>(current);for(String k:new HashSet<>(next.keySet()))if(KEYS.contains(k)||(!same&&k.startsWith("health"))||k.startsWith("epg")||k.equals("last")||k.equals("geoTime")||k.startsWith("detected")||k.equals("geoStatus"))next.remove(k);

  next.putAll(replacement);return next;

 }

 static Object get(Map<String,Object> m,String k,String d){return m.containsKey(k)?m.get(k):d;}

 static void http(String v){try{java.net.URI u=new java.net.URI(v);if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null)throw new IllegalArgumentException();}catch(Exception e){throw new IllegalArgumentException("请输入有效的 HTTP/HTTPS 地址");}}

}

