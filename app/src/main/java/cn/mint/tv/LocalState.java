package cn.mint.tv;
import android.content.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** In-memory transactional preferences facade; disk reads only during IO bootstrap. */
public final class LocalState implements SharedPreferences {
 private static volatile LocalState instance;
 private final Map<String,Object> values=new HashMap<>();
 private final Set<String> dirty=new HashSet<>();private final StateDisk disk;
 private final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor();
 private boolean queued;private volatile String error="";
 public static synchronized LocalState open(Context c){
  if(instance!=null)return instance;
  if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException("状态必须先在 IO 线程加载");
  try{instance=new LocalState(c.getApplicationContext());return instance;}catch(Exception e){throw new IllegalStateException("数据加载失败，未覆盖原数据",e);}
 }
 public static LocalState get(){if(instance==null)throw new IllegalStateException("状态尚未加载");return instance;}
 private LocalState(Context c)throws Exception{
  File dir=new File(c.getFilesDir(),"state-v1");boolean existing=new File(dir,"index.json").exists()||new File(dir,"index.bak").exists();disk=new StateDisk(dir);
  if(existing)values.putAll(disk.load());else{values.putAll(c.getSharedPreferences("mint",0).getAll());for(String k:values.keySet())dirty.add(bucket(k));persist();if(!error.isEmpty())throw new IOException(error);}
 }
 static String bucket(String k){if(k.equals("base")||k.equals("imports"))return "lists";if(k.equals("healthTasks"))return "scan-tasks";if(k.equals("healthResults"))return "scan-results";if(k.startsWith("health"))return "scan-progress";if(k.equals("epgData"))return "epg";return "settings";}
 private synchronized void queue(){if(queued)return;queued=true;writer.schedule(()->{synchronized(this){queued=false;}persist();},60,TimeUnit.MILLISECONDS);}
 private void persist(){
  Map<String,Map<String,Object>> snapshot=new HashMap<>();Set<String> pending;
  synchronized(this){pending=new HashSet<>(dirty);dirty.clear();for(String b:pending)snapshot.put(b,new HashMap<>());for(Map.Entry<String,Object> e:values.entrySet())if(snapshot.containsKey(bucket(e.getKey())))snapshot.get(bucket(e.getKey())).put(e.getKey(),e.getValue());}
  if(pending.isEmpty())return;
  try{disk.commit(snapshot);error="";}catch(Exception e){synchronized(this){dirty.addAll(pending);}error="数据尚未写入磁盘："+e.getMessage();android.util.Log.e("MintTV",error);writer.schedule(this::persist,10,TimeUnit.SECONDS);}
 }
 public String diskError(){return error;}
 public void flush()throws Exception{writer.submit(this::persist).get();if(!error.isEmpty())throw new IOException(error);}
 public void restore(Map<String,Object> replacement)throws Exception{
  writer.submit(()->{synchronized(this){Map<String,Object> next=new HashMap<>(values);
   for(String k:new HashSet<>(next.keySet()))if(BackupData.KEYS.contains(k)||k.startsWith("health")||k.startsWith("epg")||k.equals("last")||k.equals("geoTime")||k.startsWith("detected")||k.equals("geoStatus"))next.remove(k);
   next.putAll(replacement);Map<String,Map<String,Object>> buckets=new HashMap<>();for(String k:values.keySet())buckets.put(bucket(k),new HashMap<>());for(String k:next.keySet()){String b=bucket(k);if(!buckets.containsKey(b))buckets.put(b,new HashMap<>());buckets.get(b).put(k,next.get(k));}
   try{disk.commit(buckets);}catch(Exception e){throw new IllegalStateException(e);}values.clear();values.putAll(next);dirty.clear();error="";
  }}).get();
 }
 public synchronized Map<String,?> getAll(){Map<String,Object> out=new HashMap<>();for(String key:values.keySet())out.put(key,copy(values.get(key)));return out;}
 static Object copy(Object v){return v instanceof Set?new HashSet<>((Set<?>)v):v;}
 public synchronized String getString(String k,String d){Object v=values.get(k);return v instanceof String?(String)v:d;}
 @SuppressWarnings("unchecked") public synchronized Set<String> getStringSet(String k,Set<String>d){Object v=values.get(k);return v instanceof Set?new HashSet<>((Set<String>)v):new HashSet<>(d);}
 public synchronized int getInt(String k,int d){Object v=values.get(k);return v instanceof Number?((Number)v).intValue():d;}
 public synchronized long getLong(String k,long d){Object v=values.get(k);return v instanceof Number?((Number)v).longValue():d;}
 public synchronized float getFloat(String k,float d){Object v=values.get(k);return v instanceof Number?((Number)v).floatValue():d;}
 public synchronized boolean getBoolean(String k,boolean d){Object v=values.get(k);return v instanceof Boolean?(Boolean)v:d;}
 public synchronized boolean contains(String k){return values.containsKey(k);}
 public Editor edit(){return new Changes();}
 public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){throw new UnsupportedOperationException();}
 public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
 private final class Changes implements Editor {
  final Map<String,Object> changes=new HashMap<>();boolean clear;
  public Editor putString(String k,String v){changes.put(k,v);return this;}public Editor putStringSet(String k,Set<String>v){changes.put(k,v==null?null:new HashSet<>(v));return this;}
  public Editor putInt(String k,int v){changes.put(k,v);return this;}public Editor putLong(String k,long v){changes.put(k,v);return this;}public Editor putFloat(String k,float v){changes.put(k,v);return this;}public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}
  public Editor remove(String k){changes.put(k,null);return this;}public Editor clear(){clear=true;return this;}
  public boolean commit(){if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())throw new IllegalStateException("禁止主线程同步写入");apply();try{flush();return true;}catch(Exception e){return false;}}
  public void apply(){synchronized(LocalState.this){if(clear){for(String k:values.keySet())dirty.add(bucket(k));values.clear();}
   for(Map.Entry<String,Object> e:changes.entrySet()){Object old=values.get(e.getKey());if(!Objects.equals(old,e.getValue())){dirty.add(bucket(e.getKey()));if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),copy(e.getValue()));}}
   queue();}}
 }
}
