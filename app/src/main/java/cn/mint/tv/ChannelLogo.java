package cn.mint.tv;
import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;
import android.view.View;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** One current-channel logo; bounded disk and bitmap cache, no image framework. */
final class ChannelLogo implements AutoCloseable {
 private final Context context;private final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
 private final android.util.LruCache<String,Bitmap> cache=new android.util.LruCache<String,Bitmap>(512*1024){protected int sizeOf(String key,Bitmap b){return b.getAllocationByteCount();}};
 private volatile HttpURLConnection connection;private volatile int generation;private String requested="";
 ChannelLogo(Context c){context=c.getApplicationContext();}
 void show(ImageView view,String address){Object old=view.getTag();if(address.equals(old))return;view.setTag(address);view.setImageDrawable(null);view.setVisibility(View.GONE);if(address.isEmpty())return;Bitmap b=cache.get(address);if(b!=null){view.setImageBitmap(b);view.setVisibility(View.VISIBLE);return;}int ticket=++generation;requested=address;java.lang.ref.WeakReference<ImageView> ref=new java.lang.ref.WeakReference<>(view);worker.execute(()->{if(ticket!=generation)return;Bitmap bitmap=null;try{bitmap=load(address);if(bitmap!=null)cache.put(address,bitmap);}catch(Exception ignored){}final Bitmap ready=bitmap;main.post(()->{ImageView target=ref.get();if(ticket==generation&&target!=null&&address.equals(target.getTag())&&ready!=null){target.setImageBitmap(ready);target.setVisibility(View.VISIBLE);}});});}
 private Bitmap load(String address)throws Exception{File dir=new File(context.getCacheDir(),"logos-v1");if(!dir.exists()&&!dir.mkdirs())return null;String key=hex(java.security.MessageDigest.getInstance("SHA-256").digest(address.getBytes(java.nio.charset.StandardCharsets.UTF_8)));File file=new File(dir,key);byte[] bytes=null;boolean fetched=false;
  if(file.exists()&&System.currentTimeMillis()-file.lastModified()<7*86400000L)try(FileInputStream in=new FileInputStream(file)){bytes=read(in);}
  if(bytes==null){URL url=new URL(address);for(int i=0;i<3;i++){if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))return null;HttpURLConnection h=(HttpURLConnection)url.openConnection();connection=h;h.setConnectTimeout(5000);h.setReadTimeout(5000);h.setInstanceFollowRedirects(false);try{int status=h.getResponseCode();if(status>=300&&status<400){url=new URL(url,h.getHeaderField("Location"));continue;}if(status!=200)return null;try(InputStream in=h.getInputStream()){bytes=read(in);fetched=true;}break;}finally{h.disconnect();connection=null;}}if(bytes==null)return null;}
  BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<=0||options.outHeight<=0||options.outWidth>4096||options.outHeight>4096)return null;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>256||options.outHeight/options.inSampleSize>256)options.inSampleSize*=2;options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)return null;
  if(fetched){try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}File[] files=dir.listFiles();if(files!=null){Arrays.sort(files,(a,b)->Long.compare(a.lastModified(),b.lastModified()));long total=0;for(File f:files)total+=f.length();for(File f:files){if(total<=4*1024*1024)break;long len=f.length();if(f.delete())total-=len;}}}return bitmap;
 }
 private static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>256*1024)throw new IOException("台标过大");out.write(b,0,n);}return out.toByteArray();}
 private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
 public void close(){generation++;worker.shutdownNow();HttpURLConnection h=connection;if(h!=null)h.disconnect();cache.evictAll();}
}
