package cn.mint.tv;
import android.app.*;import android.content.*;import android.content.pm.*;import android.net.Uri;import android.os.*;

import okhttp3.*;import java.io.*;import java.security.MessageDigest;import java.util.*;import java.util.concurrent.*;

/** On-demand only. Download privately, validate identity, then ask Android to install. */
@androidx.media3.common.util.UnstableApi
final class AppUpdate {
 final MainActivity a;final Handler main=new Handler(Looper.getMainLooper());
 final ExecutorService worker=Executors.newSingleThreadExecutor();volatile Call call;volatile boolean canceled;boolean busy;ProgressDialog progress;
 File pending;String expectedVersion;long lastProgress;
 AppUpdate(MainActivity a){this.a=a;}
 static String version(android.content.Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception e){return "未知";}}
 void check(){
  if(busy){a.toast("正在检查或下载更新");return;}busy=true;canceled=false;
  progress=new ProgressDialog(a);progress.setTitle("系统更新");progress.setMessage("正在检查 GitHub 最新正式版…");progress.setCancelable(true);progress.setOnCancelListener(d->cancel());progress.show();
  worker.execute(()->{
   OkHttpClient client=new OkHttpClient.Builder().callTimeout(120,TimeUnit.SECONDS).connectTimeout(10,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).followSslRedirects(false).build();
   File partial=null;
   try{
    call=client.newCall(new Request.Builder().url("https://api.github.com/repos/microzjs/mint-tv/releases/latest").header("Accept","application/vnd.github+json").header("User-Agent","MintTV").build());call.timeout().timeout(15,TimeUnit.SECONDS);
    String json;try(Response r=call.execute()){if(!r.isSuccessful()||r.body()==null)throw new IOException();json=new String(read(r.body().byteStream(),512*1024),java.nio.charset.StandardCharsets.UTF_8);}
    UpdateRelease release=UpdateRelease.parse(json);
    if(!UpdateRelease.newer(release.version,version(a))){done("当前已是最新正式版 "+version(a));return;}
    if(canceled)return;File dir=new File(a.getCacheDir(),"updater");if(!dir.exists()&&!dir.mkdirs())throw new IOException();partial=new File(dir,"download.part");
    call=client.newCall(new Request.Builder().url(release.url).header("User-Agent","MintTV").build());if(canceled)return;
    MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
    try(Response r=call.execute()){
     if(!r.isSuccessful()||r.body()==null||!r.request().url().isHttps())throw new IOException();
     try(InputStream in=r.body().byteStream();FileOutputStream out=new FileOutputStream(partial)){
      byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(canceled)throw new IOException();count+=n;if(count>release.size)throw new IOException();out.write(b,0,n);digest.update(b,0,n);
       long now=SystemClock.elapsedRealtime();if(now-lastProgress>400){lastProgress=now;final int pct=(int)(count*100/release.size);main.post(()->{if(alive()&&progress!=null)progress.setMessage("正在下载 "+release.version+" · "+pct+"%");});}
      }out.getFD().sync();
     }
    }
    if(count!=release.size)throw new IOException();String hash=hex(digest.digest());if(!release.digest.isEmpty()&&!release.digest.substring(7).equalsIgnoreCase(hash))throw new IOException();
    validate(partial,release.version);File target=new File(dir,"update.apk");if(target.exists()&&!target.delete())throw new IOException();if(!partial.renameTo(target))throw new IOException();
    main.post(()->{finish();if(alive()&&!canceled){pending=target;expectedVersion=release.version;install();}});
   }catch(Exception e){if(!canceled)done("更新未完成，请检查 GitHub 网络连接后重试；现有版本和数据不受影响");}
   finally{if(canceled)main.post(this::finish);if(partial!=null&&partial.exists())partial.delete();client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
  });
 }
 static byte[] read(InputStream stream,int max)throws IOException{try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>max)throw new IOException();out.write(b,0,n);}return out.toByteArray();}}
 static String hex(byte[] b){StringBuilder out=new StringBuilder();for(byte x:b)out.append(String.format(Locale.ROOT,"%02x",x&255));return out.toString();}
 void validate(File file,String version)throws Exception{
  PackageManager pm=a.getPackageManager();PackageInfo apk=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES),installed=pm.getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNATURES);
  if(apk==null||!a.getPackageName().equals(apk.packageName)||!version.equals(apk.versionName)||apk.versionCode<=installed.versionCode||apk.signatures==null||installed.signatures==null)throw new IOException();
  Set<String> x=new HashSet<>(),y=new HashSet<>();for(Signature s:apk.signatures)x.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));for(Signature s:installed.signatures)y.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));if(x.isEmpty()||!x.equals(y))throw new IOException();
 }
 void install(){
  if(pending==null||!pending.isFile())return;
  try{
   if(Build.VERSION.SDK_INT>=26&&!a.getPackageManager().canRequestPackageInstalls()){
    new AlertDialog.Builder(a).setTitle("允许安装应用更新").setMessage("安装包已下载并校验。请在下一页允许薄荷电视安装更新，再返回继续；原数据会保留。")
     .setPositiveButton("去设置",(d,w)->{try{a.startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName())),42);}catch(Exception e){a.toast("请在盒子设置中允许薄荷电视安装应用，再点系统更新重试");}}).setNegativeButton("暂不安装",(d,w)->pending=null).show();return;
   }
   Uri uri=Uri.parse("content://"+a.getPackageName()+".updates/apk");
   Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("应用更新",uri));a.startActivity(intent);pending=null;
  }catch(Exception e){a.toast("无法打开系统安装器，请到 GitHub 下载页手动安装");}
 }
 void permissionReturned(){if(Build.VERSION.SDK_INT<26||a.getPackageManager().canRequestPackageInstalls())install();else a.toast("安装授权未开启，可稍后在系统更新中重试");}
 boolean alive(){return !a.isDestroyed()&&!a.isFinishing();}
 void finish(){busy=false;if(progress!=null){progress.dismiss();progress=null;}}
 void done(String message){main.post(()->{finish();if(alive()&&!canceled)a.toast(message);});}
 void cancel(){canceled=true;Call c=call;if(c!=null)c.cancel();}
 void close(){cancel();finish();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
