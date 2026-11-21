package cn.mint.tv;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.text.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.*;
import androidx.media3.ui.PlayerView;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
 final int BG=0xff0b1115,PANEL=0xff141f26,TEXT=0xffedf5f2,MUTED=0xff8b9da5,GREEN=0xff69edb4;
 final Handler handler=new Handler(Looper.getMainLooper());final ExecutorService io=Store.IO;
 List<Playlist.Channel> channels=new ArrayList<>(),visible=new ArrayList<>();Set<String> favorites=new HashSet<>();
 String category="全部频道",query="";Playlist.Channel current;int sourceIndex=0,generation=0;boolean fullscreen=false,updating=false,stopped=false,rendered=false;
 FrameLayout root,videoBox;LinearLayout home,groups,cards;TextView state,title,subtitle,summary,clock;PlayerView video;ExoPlayer player;Runnable timeout;EditText search;
 android.net.wifi.WifiManager.MulticastLock multicast;
 int dp(float v){return Math.round(getResources().getDisplayMetrics().density*v);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().getDecorView().setSystemUiVisibility(5894);
  favorites=new HashSet<>(Store.prefs(this).getStringSet("favorites",new HashSet<>()));channels=Store.channels(this);Store.schedule(this);
  android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(wifi!=null){multicast=wifi.createMulticastLock("mint-stream");multicast.setReferenceCounted(false);}
  build();tick();if(System.currentTimeMillis()-Store.prefs(this).getLong("attempt",0)>24*3600000L)refresh(false);
 }
 android.graphics.drawable.Drawable background(int normal){android.graphics.drawable.StateListDrawable d=new android.graphics.drawable.StateListDrawable();d.addState(new int[]{android.R.attr.state_focused},shape(0xff25483f,GREEN));d.addState(new int[]{android.R.attr.state_pressed},shape(0xff25483f,GREEN));d.addState(new int[]{},shape(normal,0xff24343c));return d;}
 GradientDrawable shape(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));d.setStroke(dp(1),stroke);return d;}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 TextView button(String s,Runnable action){TextView b=text(s,13,TEXT);b.setGravity(Gravity.CENTER);b.setPadding(dp(10),dp(6),dp(10),dp(6));b.setBackground(background(PANEL));b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->action.run());b.setOnFocusChangeListener((v,f)->{v.setElevation(f?dp(5):0);});return b;}
 LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
 LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 void add(LinearLayout l,View v,int w,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));p.setMargins(dp(4),dp(4),dp(4),dp(4));l.addView(v,p);}
 void compact(LinearLayout l,View v,int w,int h){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w<0?w:dp(w),dp(h));p.setMargins(dp(4),dp(2),dp(4),dp(2));l.addView(v,p);}
 void build(){
  root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);home=col();home.setPadding(dp(22),dp(14),dp(22),dp(8));root.addView(home,new FrameLayout.LayoutParams(-1,-1));
  LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(this);logo.setImageResource(cn.mint.tv.R.drawable.ic_launcher);add(header,logo,44,44);
  LinearLayout branding=col();TextView brand=text("薄荷电视",23,TEXT);brand.setTypeface(null,Typeface.BOLD);branding.addView(brand);branding.addView(text("MINT TV  /  福建移动",10,GREEN));header.addView(branding,new LinearLayout.LayoutParams(0,-2,1));
  clock=text("",15,MUTED);add(header,clock,64,42);add(header,button("↓ 导入",this::importDialog),86,42);add(header,button("⚙ 设置",this::settings),86,42);home.addView(header);
  LinearLayout content=row();home.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout left=col();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(136),-1);lp.setMargins(0,dp(12),dp(14),0);content.addView(left,lp);
  add(left,text("频道资料库",11,MUTED),-1,24);ScrollView gs=new ScrollView(this);groups=col();gs.setFillViewport(false);gs.addView(groups);left.addView(gs,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout main=col();content.addView(main,new LinearLayout.LayoutParams(0,-1,1));
  LinearLayout hero=row();main.addView(hero,new LinearLayout.LayoutParams(-1,dp(183)));
  videoBox=new FrameLayout(this);videoBox.setBackground(shape(PANEL,0xff24343c));LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(0,-1,1.25f);vp.setMargins(dp(4),dp(10),dp(12),dp(4));hero.addView(videoBox,vp);
  video=new PlayerView(this);video.setUseController(false);video.setFocusable(false);videoBox.addView(video,new FrameLayout.LayoutParams(-1,-1));
  TextView placeholder=text("▶\n选择一个频道，开始观看",17,MUTED);placeholder.setGravity(Gravity.CENTER);placeholder.setTag("placeholder");videoBox.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
  TextView expand=button("⛶ 全屏",()->{if(current==null)toast("请先选择频道");else setFullscreen(!fullscreen);});expand.setTag("expand");FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(dp(83),dp(38),Gravity.RIGHT|Gravity.BOTTOM);ep.setMargins(0,0,dp(8),dp(8));videoBox.addView(expand,ep);
  LinearLayout info=col();info.setPadding(dp(6),dp(8),0,0);hero.addView(info,new LinearLayout.LayoutParams(0,-1,1));
  compact(info,text("●  LIVE  /  直播",11,GREEN),-1,20);title=text("把电视，变简单。",24,TEXT);title.setTypeface(null,Typeface.BOLD);compact(info,title,-1,38);
  subtitle=text("福建移动专属频道 · 每日自动更新",11,MUTED);compact(info,subtitle,-1,20);
  state=text("按方向键选择频道，按确定键播放",12,MUTED);state.setMaxLines(2);compact(info,state,-1,30);
  LinearLayout controls=row();add(controls,button("线路",this::sources),66,37);add(controls,button("收藏",this::toggleFavorite),66,37);add(controls,button("重试",()->{if(current!=null)play(current);}),66,37);info.addView(controls);
  LinearLayout tools=row();tools.setGravity(Gravity.CENTER_VERTICAL);summary=text("全部频道",15,TEXT);tools.addView(summary,new LinearLayout.LayoutParams(0,dp(48),1));
  search=new EditText(this);search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setTextSize(13);search.setSingleLine(true);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);search.setHint("搜索频道");search.setPadding(dp(12),0,dp(12),0);search.setBackground(background(PANEL));add(tools,search,160,36);
  search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();renderCards();}public void afterTextChanged(Editable e){}});main.addView(tools);
  ScrollView sc=new ScrollView(this);sc.setFillViewport(false);cards=col();sc.addView(cards);main.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  TextView footer=text("↑↓←→ 选择   ·   确定 播放   ·   菜单 切换线路   ·   全屏上下键换台",10,MUTED);add(home,footer,-1,22);renderGroups();renderCards();
 }
 void renderGroups(){groups.removeAllViews();LinkedHashSet<String> names=new LinkedHashSet<>(Arrays.asList("全部频道","我的收藏"));for(Playlist.Channel c:channels)names.add(c.group);
  for(String name:names){TextView b=button((name.equals(category)?"▎ ":"")+name,()->{category=name;renderGroups();renderCards();View chosen=groups.findViewWithTag(name);if(chosen!=null)chosen.requestFocus();});b.setTag(name);if(name.equals(category))b.setTextColor(GREEN);add(groups,b,-1,44);}
 }
 void renderCards(){if(cards==null)return;cards.removeAllViews();visible.clear();for(Playlist.Channel c:channels){boolean group=category.equals("全部频道")||category.equals(c.group)||(category.equals("我的收藏")&&favorites.contains(Playlist.key(c.name)));if(group&&c.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))visible.add(c);}
  summary.setText(category+"  ·  "+visible.size());if(visible.isEmpty()){add(cards,text("没有匹配的频道。可搜索、导入或添加收藏。",14,MUTED),-1,72);return;}
  int columns=getResources().getConfiguration().screenWidthDp<800?3:4;
  for(int i=0;i<visible.size();i+=columns){LinearLayout r=row();cards.addView(r);for(int j=0;j<columns;j++){int k=i+j;if(k>=visible.size()){r.addView(new View(this),new LinearLayout.LayoutParams(0,dp(76),1));continue;}Playlist.Channel c=visible.get(k);
   LinearLayout card=col();card.setPadding(dp(12),dp(9),dp(12),dp(8));card.setBackground(background(current!=null&&Playlist.key(current.name).equals(Playlist.key(c.name))?0xff18362d:PANEL));card.setFocusable(true);card.setClickable(true);
   TextView n=text(c.name,16,TEXT);n.setSingleLine(true);n.setEllipsize(TextUtils.TruncateAt.END);n.setTypeface(null,Typeface.BOLD);card.addView(n,new LinearLayout.LayoutParams(-1,0,1));TextView meta=text((favorites.contains(Playlist.key(c.name))?"★  ":"")+String.format(Locale.ROOT,"%02d",channels.indexOf(c)+1)+"   /   "+c.urls.size()+" 条线路",10,GREEN);card.addView(meta);
   card.setOnClickListener(v->play(c));card.setOnLongClickListener(v->{if(current!=c)play(c);sources();return true;});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(76),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));r.addView(card,p);
  }}
 }
 void initPlayer(){if(player!=null)return;DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("MintTV/1.0").setConnectTimeoutMs(6500).setReadTimeoutMs(6500).setAllowCrossProtocolRedirects(true);
  player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http))).build();video.setPlayer(player);player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
  player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException error){android.util.Log.w("MintTV","source="+sourceIndex+" "+error.getErrorCodeName());advance("线路不可用");}
   @Override public void onRenderedFirstFrame(){rendered=true;cancelTimeout();state.setText("正在播放 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());android.util.Log.i("MintTV","FIRST_FRAME "+current.name+" source="+sourceIndex);}
   @Override public void onIsPlayingChanged(boolean playing){if(playing&&rendered){cancelTimeout();state.setText("正在播放 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());}}
   @Override public void onPlaybackStateChanged(int s){if(current==null)return;if(s==Player.STATE_BUFFERING){state.setText("正在连接 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());armTimeout();}if(s==Player.STATE_ENDED)advance("直播已中断");}
  });
 }
 void play(Playlist.Channel c){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();current=c;sourceIndex=0;title.setText(c.name);subtitle.setText(c.group+"  ·  "+c.urls.size()+" 条可选线路");View ph=videoBox.findViewWithTag("placeholder");if(ph!=null)ph.setVisibility(View.GONE);Store.prefs(this).edit().putString("last",c.name).apply();trySource();}
 void trySource(){cancelTimeout();initPlayer();rendered=false;if(multicast!=null&&!multicast.isHeld())multicast.acquire();generation++;int g=generation;state.setText("正在连接 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());player.stop();
  String url=current.urls.get(sourceIndex);if(url.startsWith("rtp://")||url.startsWith("rtsp://")){handler.post(()->{if(g==generation)advance("此协议需导入 HTTP 代理源");});return;}
  player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));player.prepare();player.play();armTimeout();
 }
 void armTimeout(){cancelTimeout();final int g=generation;timeout=()->{if(g==generation&&!stopped)advance("连接或缓冲超时");};handler.postDelayed(timeout,14000);}
 void cancelTimeout(){if(timeout!=null)handler.removeCallbacks(timeout);timeout=null;}
 void advance(String reason){cancelTimeout();if(stopped||current==null)return;if(sourceIndex+1<current.urls.size()){sourceIndex++;trySource();}else{generation++;if(player!=null)player.stop();state.setText("全部线路暂不可用 · 检查福建移动网络后重试");toast(reason+"，已尝试全部 "+current.urls.size()+" 条线路");}}
 void sources(){if(current==null){toast("请先选择频道");return;}String[] items=new String[current.urls.size()];for(int i=0;i<items.length;i++){Uri u=Uri.parse(current.urls.get(i));items[i]="线路 "+(i+1)+(i==sourceIndex?"  · 当前":"")+"\n"+u.getHost();}new AlertDialog.Builder(this).setTitle(current.name+" · 选择线路").setSingleChoiceItems(items,sourceIndex,(d,i)->{sourceIndex=i;trySource();d.dismiss();}).setNeutralButton("从第一条重新检测",(d,w)->play(current)).setNegativeButton("返回",null).show();}
 void toggleFavorite(){if(current==null){toast("请先选择频道");return;}String k=Playlist.key(current.name);if(!favorites.add(k))favorites.remove(k);Store.prefs(this).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();renderCards();toast(favorites.contains(k)?"已收藏 "+current.name:"已取消收藏");}
 void setFullscreen(boolean yes){if(fullscreen==yes)return;fullscreen=yes;((TextView)videoBox.findViewWithTag("expand")).setText(yes?"返回列表":"⛶ 全屏");((android.view.ViewGroup)videoBox.getParent()).removeView(videoBox);if(yes){home.setVisibility(View.GONE);root.addView(videoBox,new FrameLayout.LayoutParams(-1,-1));videoBox.setFocusable(true);videoBox.requestFocus();toast("上下换台 · 左键线路 · 确定菜单 · 返回频道列表");}else{root.removeView(videoBox);home.setVisibility(View.VISIBLE);LinearLayout hero=(LinearLayout)((LinearLayout)((LinearLayout)home.getChildAt(1)).getChildAt(1)).getChildAt(0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1.25f);p.setMargins(dp(4),dp(10),dp(12),dp(4));hero.addView(videoBox,0,p);videoBox.setFocusable(false);}}
 void channelStep(int step){if(channels.isEmpty())return;List<Playlist.Channel> list=visible.isEmpty()?channels:visible;int index=-1;for(int i=0;i<list.size();i++)if(current!=null&&Playlist.key(list.get(i).name).equals(Playlist.key(current.name)))index=i;play(list.get((index+step+list.size())%list.size()));}
 @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN){int k=e.getKeyCode();if(k==KeyEvent.KEYCODE_MENU){sources();return true;}if(k==KeyEvent.KEYCODE_CHANNEL_UP){channelStep(1);return true;}if(k==KeyEvent.KEYCODE_CHANNEL_DOWN){channelStep(-1);return true;}if(fullscreen){if(k==KeyEvent.KEYCODE_DPAD_UP){channelStep(-1);return true;}if(k==KeyEvent.KEYCODE_DPAD_DOWN){channelStep(1);return true;}if(k==KeyEvent.KEYCODE_DPAD_LEFT){sources();return true;}if(k==KeyEvent.KEYCODE_DPAD_RIGHT){setFullscreen(false);return true;}if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){new AlertDialog.Builder(this).setTitle(current==null?"播放菜单":current.name).setItems(new String[]{"切换线路","收藏 / 取消收藏","返回频道列表"},(d,i)->{if(i==0)sources();if(i==1)toggleFavorite();if(i==2)setFullscreen(false);}).show();return true;}}}return super.dispatchKeyEvent(e);}
 @Override public void onBackPressed(){if(fullscreen){setFullscreen(false);return;}new AlertDialog.Builder(this).setTitle("退出薄荷电视？").setPositiveButton("退出",(d,w)->finish()).setNegativeButton("继续观看",null).show();}
 void importDialog(){new AlertDialog.Builder(this).setTitle("导入直播源").setItems(new String[]{"订阅网址 · M3U / TXT，每日更新","本地文件 · M3U / M3U8 / TXT","管理已导入的源"},(d,i)->{if(i==0)urlDialog();if(i==1){Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);in.setType("*/*");in.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(in,21);}if(i==2)manage();}).setNegativeButton("返回",null).show();}
 void urlDialog(){EditText input=new EditText(this);input.setTextColor(TEXT);input.setSingleLine(true);input.setHint("https://example.com/live.m3u");input.setInputType(17);LinearLayout box=col();box.setPadding(dp(24),dp(12),dp(24),0);box.addView(input);new AlertDialog.Builder(this).setTitle("添加订阅网址").setView(box).setPositiveButton("导入",(d,w)->{String url=input.getText().toString().trim();toast("正在导入…");io.execute(()->{try{String text=Store.fetch(url);Store.add(this,Uri.parse(url).getHost(),url,text);handler.post(()->{reload();toast("导入成功");});}catch(Exception e){handler.post(()->toast("导入失败："+e.getMessage()));}});}).setNegativeButton("取消",null).show();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==21&&result==RESULT_OK&&data!=null){Uri uri=data.getData();io.execute(()->{try{String text=Store.read(getContentResolver().openInputStream(uri));Store.add(this,"本地列表 "+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date()),"",text);handler.post(()->{reload();toast("文件导入成功");});}catch(Exception e){handler.post(()->toast("导入失败："+e.getMessage()));}});}}
 void manage(){JSONArray a=Store.imports(this);if(a.length()==0){toast("还没有导入的列表");return;}String[] names=new String[a.length()];for(int i=0;i<names.length;i++)names[i]=a.optJSONObject(i).optString("title")+(a.optJSONObject(i).optString("url").isEmpty()?" · 本地":" · 订阅");new AlertDialog.Builder(this).setTitle("选择要移除的列表").setItems(names,(d,i)->new AlertDialog.Builder(this).setTitle("移除 "+names[i]+"？").setPositiveButton("移除",(x,w)->{Store.remove(this,i);reload();}).setNegativeButton("取消",null).show()).setNegativeButton("返回",null).show();}
 void settings(){long t=Store.prefs(this).getLong("baseTime",0);String date=t==0?"使用随安装包附带的列表":new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(t));new AlertDialog.Builder(this).setTitle("薄荷电视 · 设置").setMessage("福建移动 / CHINA-IPTV\n最近成功更新："+date+"\n"+Store.prefs(this).getString("result","")+"\n\n每天后台尝试更新；休眠、断网时由系统延后，打开应用会补更。失败时保留已有频道。\n\n播放按列表顺序尝试线路，14 秒未出画面或缓冲超时自动切换。部分运营商源只能在对应宽带网络播放。\n\n遥控器：方向键选择，确定播放，菜单选线路；全屏上下键换台，返回退出全屏。\n版本 1.0.0").setPositiveButton(updating?"更新中…":"立即更新",(d,w)->refresh(true)).setNeutralButton("导入直播源",(d,w)->importDialog()).setNegativeButton("返回",null).show();}
 void refresh(boolean notify){if(updating)return;updating=true;if(notify)toast("正在更新订阅…");io.execute(()->{String result=Store.update(this);handler.post(()->{updating=false;reload();if(notify)toast(result);});});}
 void reload(){channels=Store.channels(this);renderGroups();renderCards();}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 void tick(){if(clock!=null)clock.setText(new java.text.SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date()));handler.postDelayed(this::tick,30000);}
 @Override protected void onStart(){super.onStart();stopped=false;if(current!=null&&player==null)trySource();}
 @Override protected void onStop(){stopped=true;generation++;cancelTimeout();if(player!=null){video.setPlayer(null);player.release();player=null;}if(multicast!=null&&multicast.isHeld())multicast.release();super.onStop();}
 @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}





