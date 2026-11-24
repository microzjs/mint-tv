package cn.mint.tv;

import android.content.*;

import android.database.Cursor;

import android.net.Uri;

import android.provider.MediaStore;

import android.os.Environment;

import java.io.*;

import java.util.*;

/** Scoped-storage backup for TVs whose system document picker is a stub. */

@androidx.media3.common.util.UnstableApi

@android.annotation.TargetApi(29)

final class PublicBackups {

 static final String PATH=Environment.DIRECTORY_DOWNLOADS+"/MintTV/";

 static void save(MainActivity a){a.io.execute(()->{Uri uri=null;try{LocalState.get().flush();byte[] bytes=BackupData.export(Store.prefs(a).getAll()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>32*1024*1024)throw new IOException("备份超过 32 MB");String name="MintTV-backup-"+new java.text.SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".json";ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,"application/json");v.put(MediaStore.MediaColumns.RELATIVE_PATH,PATH);v.put(MediaStore.MediaColumns.IS_PENDING,1);uri=a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("无法创建备份文件");try(OutputStream out=a.getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("无法写入备份");out.write(bytes);}ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);a.getContentResolver().update(uri,done,null,null);a.handler.post(()->new android.app.AlertDialog.Builder(a).setTitle("备份已导出").setMessage("下载 / MintTV / "+name+"\n\n可以通过 USB 或文件管理器拷走。备份含订阅凭据，请自行保管。").setPositiveButton("知道了",null).show());}catch(Exception e){if(uri!=null)try{a.getContentResolver().delete(uri,null,null);}catch(Exception ignored){}a.handler.post(()->a.toast("导出失败："+e.getMessage()));}});}

 static void choose(MainActivity a){a.io.execute(()->{try{List<Uri> uris=new ArrayList<>();List<String> labels=new ArrayList<>();String[] projection={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME};try(Cursor cursor=a.getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,projection,MediaStore.MediaColumns.RELATIVE_PATH+" = ?",new String[]{PATH},MediaStore.MediaColumns.DATE_MODIFIED+" DESC")){if(cursor!=null)while(cursor.moveToNext()&&uris.size()<20){String name=cursor.getString(1);if(name!=null&&name.startsWith("MintTV-backup-")&&name.endsWith(".json")){uris.add(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cursor.getLong(0)));labels.add(name);}}}a.handler.post(()->{if(uris.isEmpty()){a.toast("没有可读取的本机备份；跨设备文件请通过系统文件选择器导入");return;}new android.app.AlertDialog.Builder(a).setTitle("下载 / MintTV · 选择备份").setItems(labels.toArray(new String[0]),(d,i)->new TvTools(a).result(23,uris.get(i))).setNegativeButton("返回",null).show();});}catch(Exception e){a.handler.post(()->a.toast("读取备份失败："+e.getMessage()));}});}

}

