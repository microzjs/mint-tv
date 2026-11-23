package cn.mint.tv;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Copy-on-write buckets, one recoverable commit pointer; no Android dependency. */
public final class StateDisk {
 private final File dir; private JSONObject index=new JSONObject();
 public StateDisk(File directory)throws Exception{dir=directory;if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建数据目录");
  File backup=new File(dir,"index.bak"),main=new File(dir,"index.json");
  if(backup.exists()){if(main.exists()&&!main.delete())throw new IOException("无法恢复索引");if(!backup.renameTo(main))throw new IOException("无法恢复索引");}
  if(main.exists())index=new JSONObject(read(main));
 }
 public synchronized Map<String,Object> load()throws Exception{
  Map<String,Object> out=new HashMap<>();for(Iterator<String> it=index.keys();it.hasNext();){String bucket=it.next();JSONObject data=new JSONObject(read(new File(dir,index.getString(bucket))));
   for(Iterator<String> keys=data.keys();keys.hasNext();){String key=keys.next();Object value=data.get(key);if(value instanceof JSONArray){Set<String> set=new HashSet<>();JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)set.add(a.getString(i));value=set;}out.put(key,value);}
  }return out;
 }
 public synchronized void commit(Map<String,Map<String,Object>> buckets)throws Exception{
  JSONObject next=new JSONObject(index.toString());
  for(Map.Entry<String,Map<String,Object>> b:buckets.entrySet()){
   String name=b.getKey()+"-"+UUID.randomUUID()+".json";JSONObject obj=new JSONObject();
   for(Map.Entry<String,Object> e:b.getValue().entrySet())obj.put(e.getKey(),e.getValue() instanceof Set?new JSONArray((Set<?>)e.getValue()):e.getValue());
   write(new File(dir,name),obj.toString());next.put(b.getKey(),name);
  }
  File main=new File(dir,"index.json"),bak=new File(dir,"index.bak"),tmp=new File(dir,"index.new");write(tmp,next.toString());
  if(main.exists()&&!main.renameTo(bak))throw new IOException("无法保存数据索引备份");
  if(!tmp.renameTo(main)){if(bak.exists())bak.renameTo(main);throw new IOException("无法提交数据索引");}
  if(bak.exists()&&!bak.delete())throw new IOException("无法完成数据索引提交");index=next;
  Set<String> keep=new HashSet<>(Arrays.asList("index.json"));for(Iterator<String> it=index.keys();it.hasNext();)keep.add(index.getString(it.next()));
  File[] files=dir.listFiles();if(files!=null)for(File file:files)if(file.getName().endsWith(".json")&&!keep.contains(file.getName()))file.delete();
 }
 private static String read(File f)throws Exception{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>64*1024*1024)throw new IOException("数据超过限制");out.write(b,0,n);}return out.toString("UTF-8");}}
 private static void write(File f,String value)throws Exception{try(FileOutputStream out=new FileOutputStream(f)){out.write(value.getBytes(StandardCharsets.UTF_8));out.flush();out.getFD().sync();}}
}
