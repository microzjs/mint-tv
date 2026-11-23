package cn.mint.tv;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
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
    final int BG=0xff0b1115, PANEL=0xff152229, TEXT=0xffedf5f2, MUTED=0xff8b9da5, GREEN=0xff69edb4;
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService io=Store.IO;
    final List<Playlist.Channel> channels=new ArrayList<>(), visible=new ArrayList<>(), drawerVisible=new ArrayList<>();
    final List<String> categories=new ArrayList<>();
    Set<String> favorites=new HashSet<>();
    String category="全部频道", drawerCategory="全部频道", query="";
    Playlist.Channel current;
    int sourceIndex=0, generation=0;
    boolean fullscreen=false, drawerOpen=false, updating=false, stopped=true, rendered=false;
    boolean pendingDetection=false, pendingForce=false, loadingLists=false;
    boolean consumeSelectUp=false;
    TextView diagnostics; String videoDecoder="未知",audioDecoder="未知"; long telemetryTime,telemetryBytes;
    final java.util.concurrent.atomic.AtomicLong mediaBytes=new java.util.concurrent.atomic.AtomicLong();
    long healthRevision=-1;
    final Runnable telemetryTick=new Runnable(){public void run(){
        long revision=Store.prefs(MainActivity.this).getLong("healthRevision",0);
        if(revision!=healthRevision){healthRevision=revision;renderChannels(false);if(drawerOpen)renderChannels(true);}
        if(drawerOpen)updateDiagnostics();handler.postDelayed(this,1000);
    }};
    FrameLayout root, videoBox, drawer;
    LinearLayout home;
    ViewGroup previewParent;
    ViewGroup.LayoutParams previewParams;
    int previewIndex;
    TextView title, subtitle, state, networkLabel, clock, hud, expand;
    ListView categoryList, channelList, drawerGroups, drawerChannels;
    RowsAdapter categoryAdapter, channelAdapter, drawerGroupAdapter, drawerChannelAdapter;
    PlayerView video;
    ExoPlayer player;
    Runnable timeout;
    ConnectivityManager connectivity;
    ConnectivityManager.NetworkCallback networkCallback;
    android.net.wifi.WifiManager.MulticastLock multicast;
    final Runnable hideHud=()->{if(hud!=null)hud.setVisibility(View.GONE);};
    final Runnable networkChanged=()->refresh(false,true);
    final Runnable clockTick=new Runnable(){public void run(){
        if(clock!=null)clock.setText(new java.text.SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date()));
        if(!stopped&&System.currentTimeMillis()-Store.prefs(MainActivity.this).getLong("geoTime",0)>15*60000L)
            refresh(false,false);
        handler.postDelayed(this,60000);
    }};

    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        favorites=new HashSet<>(Store.prefs(this).getStringSet("favorites",new HashSet<>()));
        channels.addAll(Store.channels(this));
        Store.schedule(this);
        android.net.wifi.WifiManager wifi=(android.net.wifi.WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        if(wifi!=null){multicast=wifi.createMulticastLock("mint-stream");multicast.setReferenceCounted(false);}
        build(); handler.post(clockTick); handler.post(telemetryTick); registerNetwork();
        handler.post(()->{
            String last=Store.prefs(this).getString("last","");
            for(Playlist.Channel c:channels)if(c.name.equals(last)&&!Health.hidden(this,c)){
                if(isTv()){play(c);setFullscreen(true);}else focusChannel(c);return;
            }
            channelList.requestFocus();
        });
    }
    boolean isTv(){
        UiModeManager manager=(UiModeManager)getSystemService(UI_MODE_SERVICE);
        return manager.getCurrentModeType()==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
            ||getPackageManager().hasSystemFeature("android.software.leanback");
    }
    GradientDrawable shape(int color,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(10));d.setStroke(dp(1),stroke);return d;
    }
    android.graphics.drawable.Drawable background(int normal){
        StateListDrawable d=new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed},shape(0xff285746,GREEN));
        d.addState(new int[]{android.R.attr.state_focused},shape(0xff285746,GREEN));
        d.addState(new int[]{android.R.attr.state_selected},shape(0xff285746,GREEN));
        d.addState(new int[]{},shape(normal,0xff263940));return d;
    }
    TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    TextView button(String s,Runnable action){
        TextView b=text(s,14,TEXT);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(6),dp(12),dp(6));
        b.setBackground(background(PANEL));b.setFocusable(true);b.setClickable(true);b.setOnClickListener(v->action.run());return b;
    }
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void add(LinearLayout parent,View child,int width,int height){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));
        p.setMargins(dp(4),dp(4),dp(4),dp(4));parent.addView(child,p);
    }
    ListView list(){
        ListView v=new ListView(this);v.setDivider(null);v.setDividerHeight(dp(5));v.setSelector(android.R.color.transparent);
        v.setCacheColorHint(android.graphics.Color.TRANSPARENT);v.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        v.setItemsCanFocus(false);v.setFocusable(true);v.setPadding(dp(3),dp(3),dp(3),dp(3));v.setClipToPadding(false);return v;
    }
    void build(){
        root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
        home=col();home.setPadding(dp(24),dp(16),dp(24),dp(10));root.addView(home,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);add(header,logo,42,42);
        TextView brand=text("薄荷电视",23,TEXT);brand.setTypeface(null,Typeface.BOLD);add(header,brand,130,46);
        networkLabel=text("",12,GREEN);header.addView(networkLabel,new LinearLayout.LayoutParams(0,dp(46),1));
        clock=text("",14,MUTED);add(header,clock,52,42);
        add(header,button("搜索",this::searchDialog),66,40);
        add(header,button("导入",this::importDialog),66,40);
        add(header,button("设置",this::settings),66,40);home.addView(header);
        LinearLayout body=row();LinearLayout.LayoutParams bodyParams=new LinearLayout.LayoutParams(-1,0,1);
        bodyParams.topMargin=dp(12);home.addView(body,bodyParams);
        LinearLayout rail=col();add(rail,text("频道分类",12,MUTED),-1,28);
        categoryList=list();categoryAdapter=new RowsAdapter();categoryList.setAdapter(categoryAdapter);
        rail.addView(categoryList,new LinearLayout.LayoutParams(-1,0,1));body.addView(rail,new LinearLayout.LayoutParams(dp(124),-1));
        LinearLayout stations=col();stations.setPadding(dp(10),0,dp(10),0);add(stations,text("选择频道 · 确定直接观看",12,MUTED),-1,28);
        channelList=list();channelAdapter=new RowsAdapter();channelList.setAdapter(channelAdapter);
        stations.addView(channelList,new LinearLayout.LayoutParams(-1,0,1));body.addView(stations,new LinearLayout.LayoutParams(dp(230),-1));
        LinearLayout preview=col();preview.setPadding(dp(10),0,0,0);body.addView(preview,new LinearLayout.LayoutParams(0,-1,1));
        videoBox=new FrameLayout(this);videoBox.setBackground(shape(PANEL,0xff263940));
        preview.addView(videoBox,new LinearLayout.LayoutParams(-1,0,1));
        video=new PlayerView(this);video.setUseController(false);video.setFocusable(false);videoBox.addView(video,new FrameLayout.LayoutParams(-1,-1));
        TextView placeholder=text("▶\n选择频道，按确定开始观看",17,MUTED);placeholder.setGravity(Gravity.CENTER);placeholder.setTag("placeholder");
        videoBox.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        expand=button("全屏",()->{if(current==null){toast("请先选择频道");return;}setFullscreen(true);});
        FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(dp(76),dp(38),Gravity.RIGHT|Gravity.BOTTOM);
        ep.setMargins(0,0,dp(10),dp(10));videoBox.addView(expand,ep);
        title=text("把电视，变简单。",21,TEXT);title.setTypeface(null,Typeface.BOLD);add(preview,title,-1,34);
        subtitle=text("按确定直接全屏观看",12,MUTED);add(preview,subtitle,-1,20);
        state=text("播放中按确定，左侧选台",12,GREEN);state.setMaxLines(2);add(preview,state,-1,30);
        LinearLayout controls=row();add(controls,button("线路",this::sources),76,38);
        add(controls,button("收藏",this::toggleFavorite),76,38);add(controls,button("重试",()->{if(current!=null)play(current);}),76,38);preview.addView(controls);
        add(home,text("左右切换分类 / 频道   ·   确定直接观看   ·   播放中确定打开选台   ·   返回关闭选台 / 返回主页",11,MUTED),-1,24);
        hud=text("",15,TEXT);hud.setPadding(dp(18),dp(12),dp(18),dp(12));hud.setBackground(shape(0xe6152229,0xff285746));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);hp.setMargins(dp(28),dp(24),dp(28),0);
        root.addView(hud,hp);hud.setVisibility(View.GONE);
        wireMainLists(); reloadViews(); updateNetworkLabel();
    }
    void wireMainLists(){
        categoryList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                if(!loadingLists&&categoryList.hasFocus())selectCategory(pos,false);
            }
        });
        categoryList.setOnItemClickListener((p,v,pos,id)->{selectCategory(pos,false);channelList.requestFocus();});
        categoryList.setOnKeyListener((v,k,e)->{
            if(e.getAction()==KeyEvent.ACTION_DOWN&&k==KeyEvent.KEYCODE_DPAD_RIGHT){channelList.requestFocus();return true;}return false;
        });
        channelList.setOnKeyListener((v,k,e)->{
            if(e.getAction()==KeyEvent.ACTION_DOWN&&k==KeyEvent.KEYCODE_DPAD_LEFT){categoryList.requestFocus();return true;}return false;
        });
        channelList.setOnItemClickListener((p,v,pos,id)->{if(pos<visible.size()){play(visible.get(pos));setFullscreen(true);}});
        channelList.setOnItemLongClickListener((p,v,pos,id)->{if(pos<visible.size()){play(visible.get(pos));sources();}return true;});
    }
    void reloadViews(){
        loadingLists=true;categories.clear();categories.add("全部频道");categories.add("我的收藏");
        for(Playlist.Channel c:channels)if(!categories.contains(c.group))categories.add(c.group);
        if(!categories.contains(category))category="全部频道";
        categoryAdapter.rows=new ArrayList<>(categories);categoryAdapter.notifyDataSetChanged();
        categoryList.setSelection(categories.indexOf(category));loadingLists=false;renderChannels(false);
        if(drawerOpen){if(!categories.contains(drawerCategory))drawerCategory="全部频道";
            drawerGroupAdapter.rows=new ArrayList<>(categories);drawerGroupAdapter.notifyDataSetChanged();renderChannels(true);}
    }
    void selectCategory(int pos,boolean inDrawer){
        if(pos<0||pos>=categories.size())return;
        String selected=categories.get(pos);
        if(inDrawer){if(selected.equals(drawerCategory))return;drawerCategory=selected;}
        else{if(selected.equals(category))return;category=selected;}
        renderChannels(inDrawer);
    }
    List<Playlist.Channel> filtered(String group,boolean applySearch){
        List<Playlist.Channel> out=new ArrayList<>();
        for(Playlist.Channel c:channels){
            if(Health.hidden(this,c))continue;
            boolean match=group.equals("全部频道")||group.equals(c.group)||group.equals("我的收藏")&&favorites.contains(Playlist.key(c.name));
            if(match&&(!applySearch||c.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))))out.add(c);
        }return out;
    }
    void renderChannels(boolean inDrawer){
        List<Playlist.Channel> target=inDrawer?drawerVisible:visible;
        target.clear();target.addAll(filtered(inDrawer?drawerCategory:category,!inDrawer));
        RowsAdapter adapter=inDrawer?drawerChannelAdapter:channelAdapter;
        ArrayList<String> labels=new ArrayList<>();
        for(Playlist.Channel c:target)labels.add((current!=null&&Playlist.key(current.name).equals(Playlist.key(c.name))?"▶ ":"")
            +c.name+"\n"+(favorites.contains(Playlist.key(c.name))?"★  ":"")+c.urls.size()+" 条线路");
        if(labels.isEmpty())labels.add("暂无匹配频道\n切换分类或清空搜索");
        adapter.rows=labels;adapter.notifyDataSetChanged();
    }
    class RowsAdapter extends BaseAdapter {
        List<String> rows=new ArrayList<>();
        public int getCount(){return rows.size();}
        public Object getItem(int pos){return rows.get(pos);}
        public long getItemId(int pos){return pos;}
        public View getView(int pos,View convert,ViewGroup parent){
            TextView t=convert instanceof TextView?(TextView)convert:text("",15,TEXT);
            t.setText(rows.get(pos));t.setPadding(dp(12),dp(7),dp(10),dp(7));t.setMinHeight(dp(46));
            t.setMaxLines(2);t.setEllipsize(TextUtils.TruncateAt.END);t.setBackground(background(PANEL));
            t.setLayoutParams(new AbsListView.LayoutParams(-1,dp(rows.get(pos).contains("\n")?58:46)));
            return t;
        }
    }
    void focusChannel(Playlist.Channel c){
        int index=-1;for(int i=0;i<visible.size();i++)if(Playlist.key(visible.get(i).name).equals(Playlist.key(c.name)))index=i;
        if(index>=0)channelList.setSelection(index);channelList.requestFocus();
    }
    void openDrawer(boolean focusGroups){
        if(!fullscreen||current==null)return;
        if(drawerOpen){if(focusGroups)drawerGroups.requestFocus();return;}
        drawerOpen=true;telemetryTime=android.os.SystemClock.elapsedRealtime();telemetryBytes=mediaBytes.get();showDiagnostics();hud.setVisibility(View.GONE);drawerCategory=category;
        if(current!=null&&!query.isEmpty())drawerCategory="全部频道";
        drawer=new FrameLayout(this);drawer.setBackground(shape(0xfa101c22,0xff345047));
        int width=Math.min(460,Math.max(300,(int)(getResources().getConfiguration().screenWidthDp*0.53f)));
        FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(dp(width),-1,Gravity.LEFT);
        panel.setMargins(dp(18),dp(22),0,dp(22));root.addView(drawer,panel);
        LinearLayout shell=col();shell.setPadding(dp(12),dp(12),dp(12),dp(10));drawer.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        add(shell,text("直播选台",21,GREEN),-1,32);
        add(shell,text("左右选分类 / 频道 · 确定换台 · 返回收起",11,MUTED),-1,24);
        LinearLayout lists=row();shell.addView(lists,new LinearLayout.LayoutParams(-1,0,1));
        drawerGroups=list();drawerGroupAdapter=new RowsAdapter();drawerGroupAdapter.rows=new ArrayList<>(categories);drawerGroups.setAdapter(drawerGroupAdapter);
        lists.addView(drawerGroups,new LinearLayout.LayoutParams(dp(118),-1));
        drawerChannels=list();drawerChannelAdapter=new RowsAdapter();drawerChannels.setAdapter(drawerChannelAdapter);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.leftMargin=dp(8);lists.addView(drawerChannels,cp);
        drawerGroups.setSelection(Math.max(0,categories.indexOf(drawerCategory)));renderChannels(true);
        for(int i=0;i<drawerVisible.size();i++)if(Playlist.key(drawerVisible.get(i).name).equals(Playlist.key(current.name)))drawerChannels.setSelection(i);
        drawerGroups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(drawerGroups.hasFocus())selectCategory(pos,true);}
        });
        drawerGroups.setOnItemClickListener((p,v,pos,id)->{selectCategory(pos,true);drawerChannels.requestFocus();});
        drawerGroups.setOnKeyListener((v,k,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN&&k==KeyEvent.KEYCODE_DPAD_RIGHT){drawerChannels.requestFocus();return true;}return false;});
        drawerChannels.setOnKeyListener((v,k,e)->{
            if(e.getAction()==KeyEvent.ACTION_DOWN){if(k==KeyEvent.KEYCODE_DPAD_LEFT){drawerGroups.requestFocus();return true;}
                if(k==KeyEvent.KEYCODE_DPAD_RIGHT){closeDrawer();return true;}}return false;
        });
        drawerChannels.setOnItemClickListener((p,v,pos,id)->{
            if(pos<drawerVisible.size()){Playlist.Channel selected=drawerVisible.get(pos);category=drawerCategory;
                if(!Playlist.key(selected.name).equals(Playlist.key(current.name)))play(selected);
                closeDrawer();}
        });
        if(focusGroups)drawerGroups.requestFocus();else drawerChannels.requestFocus();
        android.util.Log.i("MintTV","DRAWER_OPEN channel="+current.name+" playing="+(player!=null&&player.isPlaying()));
    }
    void closeDrawer(){if(!drawerOpen)return;drawerOpen=false;if(diagnostics!=null)diagnostics.setVisibility(View.GONE);root.removeView(drawer);drawer=null;videoBox.requestFocus();
        android.util.Log.i("MintTV","DRAWER_CLOSE channel="+(current==null?"":current.name)+" playing="+(player!=null&&player.isPlaying()));}
    void setFullscreen(boolean yes){
        if(fullscreen==yes)return;closeDrawer();fullscreen=yes;
        if(yes){
            previewParent=(ViewGroup)videoBox.getParent();previewIndex=previewParent.indexOfChild(videoBox);previewParams=videoBox.getLayoutParams();
            previewParent.removeView(videoBox);home.setVisibility(View.GONE);expand.setVisibility(View.GONE);
            root.addView(videoBox,0,new FrameLayout.LayoutParams(-1,-1));videoBox.setFocusable(true);videoBox.requestFocus();showHud();
        }else{
            root.removeView(videoBox);previewParent.addView(videoBox,previewIndex,previewParams);expand.setVisibility(View.VISIBLE);
            home.setVisibility(View.VISIBLE);videoBox.setFocusable(false);hud.setVisibility(View.GONE);reloadViews();if(current!=null)focusChannel(current);
        }
    }
    void showHud(){
        if(!fullscreen||drawerOpen||current==null)return;
        hud.setText(current.name+"   ·   线路 "+(sourceIndex+1)+" / "+current.urls.size()+"\n确定 选台  ·  菜单 线路  ·  返回 主页");
        hud.setVisibility(View.VISIBLE);hud.bringToFront();handler.removeCallbacks(hideHud);handler.postDelayed(hideHud,4000);
    }
    void showDiagnostics(){
        if(diagnostics==null){diagnostics=text("",12,TEXT);diagnostics.setPadding(dp(14),dp(10),dp(14),dp(10));
            diagnostics.setBackground(shape(0xed101c22,0xff345047));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(Math.min(400,getResources().getConfiguration().screenWidthDp/2-30)),-2,Gravity.TOP|Gravity.RIGHT);
            p.setMargins(0,dp(24),dp(24),0);root.addView(diagnostics,p);}
        diagnostics.setVisibility(View.VISIBLE);diagnostics.bringToFront();updateDiagnostics();
    }
    String fmt(Format f){return f==null?"等待轨道信息":(f.sampleMimeType==null?"未知":f.sampleMimeType)+(f.codecs==null?"":" / "+f.codecs);}
    void updateDiagnostics(){
        if(diagnostics==null)return;long now=android.os.SystemClock.elapsedRealtime(),bytes=mediaBytes.get();
        double rate=(bytes-telemetryBytes)*8.0/Math.max(1,now-telemetryTime)/1000.0;telemetryBytes=bytes;telemetryTime=now;
        Format v=player==null?null:player.getVideoFormat(),a=player==null?null:player.getAudioFormat();
        String channels=a==null||a.channelCount<0?"声道未知":a.channelCount==1?"单声道":a.channelCount==2?"立体声 2.0":a.channelCount+" 声道";
        diagnostics.setText(String.format(Locale.CHINA,"实时流码率（下载） %.2f Mbps\n",rate)
            +"含音频/封装；分片缓存会造成波动\n视频："+fmt(v)+(v==null?"":"\n"+v.width+" × "+v.height+" / 标称 "+(v.bitrate>0?String.format(Locale.CHINA,"%.2f Mbps",v.bitrate/1000000.0):"未知"))
            +"\n音频："+fmt(a)+"\n"+channels+(a!=null&&a.sampleRate>0?" / "+a.sampleRate+" Hz":"")
            +"\n解码器："+videoDecoder+"\n"+audioDecoder);
    }
    void initPlayer(){
        if(player!=null)return;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("MintTV/1.1").setConnectTimeoutMs(6500).setReadTimeoutMs(6500).setAllowCrossProtocolRedirects(true);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http).setTransferListener(new TransferListener(){
            public void onTransferInitializing(DataSource d,DataSpec spec,boolean network){}
            public void onTransferStart(DataSource d,DataSpec spec,boolean network){}
            public void onTransferEnd(DataSource d,DataSpec spec,boolean network){}
            public void onBytesTransferred(DataSource d,DataSpec spec,boolean network,int count){if(network)mediaBytes.addAndGet(count);}
        }))).build();
        player.addAnalyticsListener(new androidx.media3.exoplayer.analytics.AnalyticsListener(){
            public void onVideoDecoderInitialized(EventTime t,String name,long at,long duration){videoDecoder=name;}
            public void onAudioDecoderInitialized(EventTime t,String name,long at,long duration){audioDecoder=name;}
        });
        video.setPlayer(player);player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.addListener(new Player.Listener(){
            public void onPlayerError(PlaybackException e){android.util.Log.w("MintTV","source="+sourceIndex+" "+e.getErrorCodeName());advance("线路不可用");}
            public void onRenderedFirstFrame(){rendered=true;cancelTimeout();state.setText("正在播放 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());
                android.util.Log.i("MintTV","FIRST_FRAME "+current.name+" source="+sourceIndex);}
            public void onIsPlayingChanged(boolean playing){if(playing&&rendered){cancelTimeout();state.setText("正在播放 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());}}
            public void onPlaybackStateChanged(int s){if(current==null||stopped)return;
                if(s==Player.STATE_BUFFERING){state.setText("正在连接 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());armTimeout();}
                if(s==Player.STATE_ENDED)advance("直播已中断");}
        });
    }
    void play(Playlist.Channel c){
        current=c;sourceIndex=Health.first(this,c);title.setText(c.name);subtitle.setText(c.group+" · "+c.urls.size()+" 条可选线路");
        View placeholder=videoBox.findViewWithTag("placeholder");if(placeholder!=null)placeholder.setVisibility(View.GONE);
        Store.prefs(this).edit().putString("last",c.name).apply();trySource();showHud();
    }
    void trySource(){
        cancelTimeout();initPlayer();videoDecoder="等待解码";audioDecoder="等待解码";rendered=false;if(multicast!=null&&!multicast.isHeld())multicast.acquire();
        generation++;int g=generation;state.setText("正在连接 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());player.stop();
        String url=current.urls.get(sourceIndex);
        if(url.startsWith("rtp://")||url.startsWith("rtsp://")){handler.post(()->{if(g==generation)advance("此协议需导入 HTTP 代理源");});return;}
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));player.prepare();player.play();armTimeout();
    }
    void armTimeout(){cancelTimeout();final int g=generation;timeout=()->{if(g==generation&&!stopped)advance("连接或缓冲超时");};handler.postDelayed(timeout,14000);}
    void cancelTimeout(){if(timeout!=null)handler.removeCallbacks(timeout);timeout=null;}
    void advance(String reason){
        cancelTimeout();if(stopped||current==null)return;
        if(sourceIndex+1<current.urls.size()){sourceIndex++;trySource();}
        else{generation++;if(player!=null)player.stop();state.setText("全部线路暂不可用 · 检查网络或选择其他源");toast(reason+"，已尝试全部 "+current.urls.size()+" 条线路");}
    }
    void sources(){
        if(current==null){toast("请先选择频道");return;}
        String[] labels=new String[current.urls.size()];for(int i=0;i<labels.length;i++)labels[i]="线路 "+(i+1)+(i==sourceIndex?" · 当前":"")+"\n"+Uri.parse(current.urls.get(i)).getHost();
        new AlertDialog.Builder(this).setTitle(current.name+" · 线路").setSingleChoiceItems(labels,sourceIndex,(d,i)->{sourceIndex=i;trySource();d.dismiss();showHud();})
            .setNeutralButton("从第一条重试",(d,w)->play(current)).setNegativeButton("返回",null).show();
    }
    void toggleFavorite(){
        if(current==null){toast("请先选择频道");return;}String key=Playlist.key(current.name);
        if(!favorites.add(key))favorites.remove(key);Store.prefs(this).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();
        renderChannels(false);if(drawerOpen)renderChannels(true);toast(favorites.contains(key)?"已收藏 "+current.name:"已取消收藏");
    }
    void channelStep(int step){
        List<Playlist.Channel> pool=filtered(category,false);if(pool.isEmpty())pool=filtered("全部频道",false);if(pool.isEmpty())return;
        int at=-1;for(int i=0;i<pool.size();i++)if(current!=null&&Playlist.key(pool.get(i).name).equals(Playlist.key(current.name)))at=i;
        play(pool.get((at+step+pool.size())%pool.size()));
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        // The opening DOWN belongs to the player, so its UP must not select a drawer row.
        if(consumeSelectUp&&(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
            if(e.getAction()==KeyEvent.ACTION_UP)consumeSelectUp=false;
            return true;
        }
        if(e.getAction()==KeyEvent.ACTION_DOWN){int k=e.getKeyCode();
            if(k==KeyEvent.KEYCODE_MENU){sources();return true;}
            if(!drawerOpen&&(k==KeyEvent.KEYCODE_CHANNEL_UP||k==KeyEvent.KEYCODE_CHANNEL_DOWN)){channelStep(k==KeyEvent.KEYCODE_CHANNEL_UP?1:-1);return true;}
            if(fullscreen&&!drawerOpen){
                if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){consumeSelectUp=true;openDrawer(false);return true;}
                if(k==KeyEvent.KEYCODE_DPAD_LEFT){openDrawer(true);return true;}
                if(k==KeyEvent.KEYCODE_DPAD_RIGHT){sources();return true;}
                if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){channelStep(k==KeyEvent.KEYCODE_DPAD_UP?-1:1);return true;}
            }
        }return super.dispatchKeyEvent(e);
    }
    @Override public void onBackPressed(){
        if(drawerOpen){closeDrawer();return;}if(fullscreen){setFullscreen(false);return;}
        new AlertDialog.Builder(this).setTitle("退出薄荷电视？").setPositiveButton("退出",(d,w)->finishAndRemoveTask()).setNegativeButton("继续观看",null).show();
    }
    void searchDialog(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(query);input.setTextColor(TEXT);input.setHint("频道名称");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        LinearLayout box=col();box.setPadding(dp(24),dp(8),dp(24),0);box.addView(input);
        new AlertDialog.Builder(this).setTitle("搜索频道").setView(box).setPositiveButton("搜索",(d,w)->{query=input.getText().toString().trim();category="全部频道";reloadViews();channelList.requestFocus();})
            .setNeutralButton("清空搜索",(d,w)->{query="";reloadViews();channelList.requestFocus();}).setNegativeButton("返回",null).show();
    }
 void importDialog(){new AlertDialog.Builder(this).setTitle("导入直播源").setItems(new String[]{"订阅网址 · M3U / TXT，每日更新","本地文件 · M3U / M3U8 / TXT","管理已导入的源"},(d,i)->{if(i==0)urlDialog();if(i==1){Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);in.setType("*/*");in.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(in,21);}if(i==2)manage();}).setNegativeButton("返回",null).show();}
 void urlDialog(){EditText input=new EditText(this);input.setTextColor(TEXT);input.setSingleLine(true);input.setHint("https://example.com/live.m3u");input.setInputType(17);LinearLayout box=col();box.setPadding(dp(24),dp(12),dp(24),0);box.addView(input);new AlertDialog.Builder(this).setTitle("添加订阅网址").setView(box).setPositiveButton("导入",(d,w)->{String url=input.getText().toString().trim();toast("正在导入…");io.execute(()->{try{String text=Store.fetch(url);Store.add(this,Uri.parse(url).getHost(),url,text);handler.post(()->{reload();toast("导入成功");});}catch(Exception e){handler.post(()->toast("导入失败："+e.getMessage()));}});}).setNegativeButton("取消",null).show();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==21&&result==RESULT_OK&&data!=null){Uri uri=data.getData();io.execute(()->{try{String text=Store.read(getContentResolver().openInputStream(uri));Store.add(this,"本地列表 "+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date()),"",text);handler.post(()->{reload();toast("文件导入成功");});}catch(Exception e){handler.post(()->toast("导入失败："+e.getMessage()));}});}}
 void manage(){JSONArray a=Store.imports(this);if(a.length()==0){toast("还没有导入的列表");return;}String[] names=new String[a.length()];for(int i=0;i<names.length;i++)names[i]=a.optJSONObject(i).optString("title")+(a.optJSONObject(i).optString("url").isEmpty()?" · 本地":" · 订阅");new AlertDialog.Builder(this).setTitle("选择要移除的列表").setItems(names,(d,i)->new AlertDialog.Builder(this).setTitle("移除 "+names[i]+"？").setPositiveButton("移除",(x,w)->{Store.remove(this,i);reload();}).setNegativeButton("取消",null).show()).setNegativeButton("返回",null).show();}

    void settings(){
        long t=Store.prefs(this).getLong("baseTime",0);
        String date=t==0?"安装包附带的备用列表":new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(t));
        new AlertDialog.Builder(this).setTitle("薄荷电视 1.2.0 · "+Store.activeProfile(this).label())
            .setMessage((Store.isManual(this)?"手动模式":"自动适配")+"\n"+Store.prefs(this).getString("geoStatus","首次联网后识别省份和运营商")
                +"\n最近源更新："+date+"\n"+Store.prefs(this).getString("result","")
                +"\n\n首次及网络变化时识别公网出口的省份 / 运营商；匹配不变保留原源，每日更新列表。识别或下载失败保留缓存。代理 / VPN 可能改变识别结果，可手动选择。"
                +"\n\n确定打开直播选台，左右切换分类和频道，返回先收起选台。菜单键选择线路。")
            .setPositiveButton("立即更新",(d,w)->refresh(true,true)).setNeutralButton("检测 / 网络",(d,w)->new AlertDialog.Builder(this).setTitle(Health.status(this)).setItems(new String[]{"重新检测所有频道（恢复可用频道）","网络适配"},(a,i)->{if(i==0){new AlertDialog.Builder(this).setTitle("重新检测全部频道源？").setMessage("将逐一静音试播，消耗带宽；隐藏频道仅在检测成功后恢复。现有直播继续播放。").setPositiveButton("开始检测",(b,k)->{Health.ensure(this,true);toast("已开始后台检测");}).setNegativeButton("取消",null).show();}else networkSettings();}).setNegativeButton("返回",null).show()).setNegativeButton("返回",null).show();
    }
    void networkSettings(){
        new AlertDialog.Builder(this).setTitle("网络适配 · "+Store.activeProfile(this).label())
            .setItems(new String[]{"自动识别省份与运营商","手动选择省份与运营商","查看识别服务说明"},(d,i)->{
                if(i==0){Store.automatic(this);refresh(true,true);}
                if(i==1){String[] labels=new String[NetworkProfile.PROVINCES.length];for(int p=0;p<labels.length;p++)labels[p]=NetworkProfile.PROVINCES[p][1];
                    new AlertDialog.Builder(this).setTitle("选择省份").setItems(labels,(a,p)->{
                        String[] carriers=new String[NetworkProfile.CARRIERS.length];for(int k=0;k<carriers.length;k++)carriers[k]=NetworkProfile.CARRIERS[k][1];
                        new AlertDialog.Builder(this).setTitle(labels[p]+" · 运营商").setItems(carriers,(b,k)->{
                            Store.manual(this,new NetworkProfile(NetworkProfile.PROVINCES[p][0],NetworkProfile.CARRIERS[k][0]));refresh(true,false);
                        }).setNegativeButton("返回",null).show();
                    }).setNegativeButton("返回",null).show();
                }
                if(i==2)new AlertDialog.Builder(this).setTitle("公网识别说明").setMessage("由盒子通过 HTTPS 访问 myip.ipip.net，失败时尝试 ipwho.is。服务方可看到请求的公网 IP，用于识别省份和运营商。应用只保存匹配的省份、运营商及检测时间，不保存公网 IP。\n\n可选手动模式，停止后续自动识别。匹配只决定优先源列表，实际是否可播仍以播放器试播为准。")
                    .setPositiveButton("知道了",null).show();
            }).setNegativeButton("返回",null).show();
    }
    void updateNetworkLabel(){networkLabel.setText(Store.activeProfile(this).label()+" · "+(Store.isManual(this)?"手动":"自动适配"));}
    void refresh(boolean force){refresh(force,force);}
    void refresh(boolean forceLists,boolean forceDetection){
        if(updating){pendingForce|=forceLists;pendingDetection|=forceDetection;return;}
        updating=true;if(forceLists)toast("正在更新频道源…");
        io.execute(()->{
            Store.RefreshResult result=Store.refresh(this,forceLists,forceDetection);
            handler.post(()->{
                if(isDestroyed())return;updating=false;
                Health.ensure(this,false);if(result.changed){reload();
                    if(result.profileChanged&&current!=null){
                        for(Playlist.Channel c:channels)if(Playlist.key(c.name).equals(Playlist.key(current.name))){play(c);break;}
                    }
                }updateNetworkLabel();
                if(forceLists||result.profileChanged)toast(result.message);
                if(pendingDetection||pendingForce){boolean f=pendingForce;pendingForce=false;pendingDetection=false;refresh(f,true);}
            });
        });
    }
    void reload(){
        String playingName=current==null?null:Playlist.key(current.name);
        channels.clear();channels.addAll(Store.channels(this));reloadViews();
        // Keep the current decoder/session playing during an update. New URLs apply on the next tune.
        if(current!=null&&player==null){for(Playlist.Channel c:channels)if(Playlist.key(c.name).equals(playingName)){current=c;break;}}
    }
    void registerNetwork(){
        connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        networkCallback=new ConnectivityManager.NetworkCallback(){
            final Set<Network> validated=Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Network,Boolean>());
            public void onAvailable(Network n){queueCheck();}
            public void onCapabilitiesChanged(Network n,NetworkCapabilities caps){
                if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)&&validated.add(n))queueCheck();
            }
            public void onLost(Network n){validated.remove(n);}
            void queueCheck(){handler.post(()->{if(stopped)return;handler.removeCallbacks(networkChanged);handler.postDelayed(networkChanged,1800);});}
        };
        try{if(Build.VERSION.SDK_INT>=24)connectivity.registerDefaultNetworkCallback(networkCallback);
            else connectivity.registerNetworkCallback(new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),networkCallback);
        }catch(Exception ignored){networkCallback=null;}
    }
    void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);refresh(false,true);}
    @Override protected void onPause(){setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);super.onPause();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);}
    @Override protected void onStart(){super.onStart();stopped=false;if(current!=null&&player==null)trySource();}
    @Override protected void onStop(){
        stopped=true;generation++;cancelTimeout();
        if(player!=null){video.setPlayer(null);player.release();player=null;}
        if(multicast!=null&&multicast.isHeld())multicast.release();super.onStop();
    }
    @Override protected void onDestroy(){
        if(networkCallback!=null)try{connectivity.unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}
        handler.removeCallbacksAndMessages(null);super.onDestroy();
    }
}
