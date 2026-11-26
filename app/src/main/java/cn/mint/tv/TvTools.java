package cn.mint.tv;

import android.app.*;

import android.content.*;

import android.net.Uri;

import android.widget.*;

import java.io.*;

import java.util.*;

import org.json.*;

/** Small native dialogs, no background framework or additional player. */

@androidx.media3.common.util.UnstableApi

final class TvTools {

 final MainActivity a;TvTools(MainActivity a){this.a=a;}

 void show(){new AlertDialog.Builder(a).setTitle("频道与网络设置").setItems(new String[]{"隐藏频道 / 单频道重检","重新检测所有频道","网络适配","节目单","使用说明","免责声明"},(d,i)->{if(i==0)hidden();if(i==1)new AlertDialog.Builder(a).setTitle("重新检测所有频道？").setMessage("后台试播会消耗带宽，直播继续播放。").setPositiveButton("开始",(x,w)->a.io.execute(()->Health.ensure(a,true))).setNegativeButton("取消",null).show();if(i==2)a.networkSettings();if(i==3)epg();if(i==4)help();if(i==5)disclaimer();}).setNegativeButton("返回",null).show();}
 void textDialog(String title,String content){ScrollView scroll=new ScrollView(a);scroll.setFocusable(true);scroll.setFillViewport(true);TextView text=a.text(content,14,a.TEXT);text.setPadding(a.dp(20),a.dp(4),a.dp(20),a.dp(6));text.setLineSpacing(0,1f);scroll.addView(text,new android.view.ViewGroup.LayoutParams(-1,-2));AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title).setView(scroll).setPositiveButton("知道了",null).create();dialog.setOnShowListener(d->scroll.requestFocus());dialog.show();}
 void disclaimer(){textDialog("免责声明",Disclaimer.TEXT);}
 void help(){textDialog("使用说明","选台：左右切分类和频道，上下选频道，确定进入全屏。\n\n主页按钮：频道列表按右到线路，再按左右切换线路、收藏、画面；按下回频道，上到设置。\n\n全屏：确定打开选台；短按换台，长按收藏，再次长按取消；返回收起选台或回主页。\n\n详情：频道列表按右到详细信息；展开后 60 秒无操作收起，普通选台 30 秒。\n\n画面：被压窄的频道可在画面菜单选强制 16:9，按频道保存。\n\n导入：导入菜单支持网址、文件，或同一局域网手机扫码发送。\n\n频道源：首次无内置列表，联网识别地区后尝试获取对应列表；失败可选地区或导入有权使用的源。缓存和导入会保留。\n\n更新：更新频道源只刷新列表；系统更新下载软件，安装按系统提示确认。");}
 void hidden(){List<Playlist.Channel> list=new ArrayList<>();for(Playlist.Channel c:a.channels)if(Health.hidden(a,c))list.add(c);if(list.isEmpty()){a.toast("没有隐藏频道；可在播放线路菜单重检当前频道");return;}String[] labels=new String[list.size()];for(int i=0;i<labels.length;i++){Playlist.Channel c=list.get(i);JSONObject r=Health.results(a).optJSONObject(Playlist.key(c.name));labels[i]=c.name+" · "+(r!=null&&r.optBoolean("uncertain")?"检测不确定，保留隐藏":"全部源不可用")+"\n"+(r==null?"":android.text.format.DateFormat.format("MM-dd HH:mm",r.optLong("checkedAt")));}new AlertDialog.Builder(a).setTitle("隐藏频道 · 选择单独重检").setItems(labels,(d,i)->{a.io.execute(()->Health.recheck(a,list.get(i)));a.toast("已加入单频道检测队列；成功后自动显示");}).setNegativeButton("返回",null).show();}

 void epg(){EditText input=new EditText(a);input.setSingleLine(true);input.setInputType(17);input.setHint("XMLTV / XMLTV.gz 的 HTTP(S) 地址");input.setText(Store.prefs(a).getString("epgUrl",""));LinearLayout box=a.col();box.setPadding(a.dp(24),a.dp(12),a.dp(24),0);box.addView(input);TextView status=a.text(Store.prefs(a).getString("epgStatus","仅缓存每频道当前及下一节目；每 6 小时更新，失败保留缓存。"),12,a.MUTED);box.addView(status);new AlertDialog.Builder(a).setTitle("简版节目单（可选）").setView(box).setPositiveButton("保存并更新",(d,w)->{String url=input.getText().toString().trim();try{if(!url.isEmpty())BackupData.http(url);}catch(Exception e){a.toast(e.getMessage());return;}Store.prefs(a).edit().putString("epgUrl",url).putLong("epgTime",0).putString("epgData","{}").apply();Epg.schedule(a,true,()->{if(!a.isDestroyed()){a.updateEpg();a.toast(Store.prefs(a).getString("epgStatus",url.isEmpty()?"节目单已关闭":""));}});}).setNegativeButton("返回",null).show();}

}
