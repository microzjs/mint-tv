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
    int sourceIndex=0, generation=0,sourceAttempt=0;int[] sourceOrder=new int[0];
    AppUpdate updater;
    AppUpdate updater(){if(updater==null)updater=new AppUpdate(this);return updater;}
    ChannelLogo logos;TextView drawerTitle,drawerProgram;ImageView drawerLogo;LanImportDialog lanImport;
    boolean fullscreen=false, drawerOpen=false, updating=false, stopped=true, rendered=false;
    boolean pendingDetection=false, pendingForce=false, loadingLists=false;
    boolean consumeSelectUp=false; boolean ready=false;
    JSONObject numbers=new JSONObject();String digits="";final Runnable tuneNumber=this::finishNumber;
    LinearLayout statsPanel;TextView statsToggle;RateGraph rateGraph;boolean statsDetailed;
    TextView diagnostics;String playbackStatus="请选择频道"; String videoDecoder="未知",audioDecoder="未知"; long telemetryTime,telemetryBytes;
    final java.util.concurrent.atomic.AtomicLong mediaBytes=new java.util.concurrent.atomic.AtomicLong();
    long healthRevision=-1;
    final Runnable telemetryTick=new Runnable(){public void run(){
        long revision=Store.prefs(MainActivity.this).getLong("healthRevision",0);
        if(revision!=healthRevision){healthRevision=revision;assignNumbers();renderChannels(false);if(drawerOpen)renderChannels(true);}
        if(player!=null)updateDiagnostics();handler.postDelayed(this,1000);
    }};
    FrameLayout root, videoBox, drawer;
    LinearLayout home;
    ViewGroup previewParent;
    ViewGroup.LayoutParams previewParams;
    int previewIndex;
    TextView title, state, networkLabel, clock, hud;
    ListView categoryList, channelList, drawerGroups, drawerChannels;
    RowsAdapter categoryAdapter, channelAdapter, drawerGroupAdapter, drawerChannelAdapter;
    TvPlayerView video;
    ExoPlayer player;
    Runnable timeout;
    ConnectivityManager connectivity;
    ConnectivityManager.NetworkCallback networkCallback;
    final Runnable autoHideDrawer=()->{if(fullscreen&&drawerOpen)closeDrawer();};
    void resetDrawerTimeout(){handler.removeCallbacks(autoHideDrawer);if(fullscreen&&drawerOpen)handler.postDelayed(autoHideDrawer,statsDetailed?60000:30000);}
    Playlist.Channel heldChannel;boolean heldFavorite;
    final Runnable favoriteHold=()->{if(heldChannel!=null&&drawerOpen&&drawerChannels.hasFocus()){heldFavorite=true;toggleFavorite(heldChannel);resetDrawerTimeout();}};
    boolean drawerConfirm(KeyEvent e){
        int key=e.getKeyCode();if(key!=KeyEvent.KEYCODE_DPAD_CENTER&&key!=KeyEvent.KEYCODE_ENTER&&key!=KeyEvent.KEYCODE_NUMPAD_ENTER)return false;
        if(e.getAction()==KeyEvent.ACTION_UP&&heldChannel!=null){handler.removeCallbacks(favoriteHold);Playlist.Channel selected=heldChannel;boolean favorite=heldFavorite;heldChannel=null;heldFavorite=false;if(!favorite&&drawerOpen&&drawerChannels.hasFocus()){int pos=drawerChannels.getSelectedItemPosition();if(pos>=0&&pos<drawerVisible.size()&&Playlist.key(selected.name).equals(Playlist.key(drawerVisible.get(pos).name)))drawerChannels.performItemClick(drawerChannels.getSelectedView(),pos,pos);}return true;}
        if(e.getAction()==KeyEvent.ACTION_DOWN&&drawerOpen&&drawerChannels.hasFocus()){if(e.getRepeatCount()==0){handler.removeCallbacks(favoriteHold);int pos=drawerChannels.getSelectedItemPosition();heldChannel=pos>=0&&pos<drawerVisible.size()?drawerVisible.get(pos):null;heldFavorite=false;if(heldChannel!=null)handler.postDelayed(favoriteHold,ViewConfiguration.getLongPressTimeout());}return true;}return false;
    }
    final Runnable hideHud=()->{if(hud!=null)hud.setVisibility(View.GONE);};
    final Runnable networkChanged=()->refresh(false,true);
    final Runnable clockTick=new Runnable(){public void run(){
        if(ready&&!stopped&&!channels.isEmpty()){updateEpg();Epg.schedule(MainActivity.this,false,()->{if(!isDestroyed())updateEpg();});}
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
        TextView loading=text("正在安全加载本地数据…",18,TEXT);loading.setGravity(Gravity.CENTER);loading.setBackgroundColor(BG);setContentView(loading);
        io.execute(()->{
            try{LocalState.open(this);Epg.defaults(this);if(!Store.prefs(this).getBoolean("aspectCorrections164",false))Store.prefs(this).edit().putString("channelAspects",ChannelAspect.defaults(Store.prefs(this).getString("channelAspects","{}")).toString()).putBoolean("aspectCorrections164",true).apply();java.util.List<Playlist.Channel> loaded=Store.channels(this);
                handler.post(()->{if(isDestroyed())return;channels.addAll(loaded);favorites=new HashSet<>(Store.prefs(this).getStringSet("favorites",new HashSet<>()));
                    ready=true;logos=new ChannelLogo(this);assignNumbers();Store.schedule(this);
                    build();handler.post(clockTick);handler.post(telemetryTick);registerNetwork();refresh(false,true);
                    String last=Store.prefs(this).getString("last","");
                    for(Playlist.Channel c:channels)if(c.name.equals(last)&&!Health.hidden(this,c)){
                        if(isTv()&&!stopped){play(c);setFullscreen(true);}else focusChannel(c);return;}
                    channelList.requestFocus();
                });
            }catch(Exception e){handler.post(()->loading.setText("本地数据加载失败，原数据未覆盖。请保留应用并重新打开。"));}
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
    TextView searchButton,importButton,settingsButton,sourceButton,favoriteButton,aspectButton;
    void build(){
        root=new FrameLayout(this);root.setBackgroundColor(BG);setContentView(root);
        home=col();home.setPadding(dp(24),dp(16),dp(24),dp(10));root.addView(home,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);add(header,logo,42,42);
        TextView brand=text("薄荷电视",23,TEXT);brand.setTypeface(null,Typeface.BOLD);add(header,brand,116,46);
        add(header,text("v"+AppUpdate.version(this),12,MUTED),58,46);
        networkLabel=text("",12,GREEN);networkLabel.setFocusable(true);networkLabel.setClickable(true);networkLabel.setOnClickListener(v->networkStatus());header.addView(networkLabel,new LinearLayout.LayoutParams(0,dp(46),1));
        clock=text("",14,MUTED);add(header,clock,52,42);
        searchButton=button("搜索",this::searchDialog);add(header,searchButton,66,40);
        importButton=button("导入",this::importDialog);add(header,importButton,66,40);
        settingsButton=button("设置",this::settings);add(header,settingsButton,66,40);home.addView(header);
        LinearLayout body=row();LinearLayout.LayoutParams bodyParams=new LinearLayout.LayoutParams(-1,0,1);
        bodyParams.topMargin=dp(12);home.addView(body,bodyParams);
        LinearLayout rail=col();add(rail,text("频道分类",12,MUTED),-1,28);
        categoryList=list();categoryAdapter=new RowsAdapter();categoryList.setAdapter(categoryAdapter);
        rail.addView(categoryList,new LinearLayout.LayoutParams(-1,0,1));body.addView(rail,new LinearLayout.LayoutParams(dp(108),-1));
        LinearLayout stations=col();stations.setPadding(dp(10),0,dp(10),0);add(stations,text("选择频道 · 确定直接观看",12,MUTED),-1,28);
        channelList=list();channelAdapter=new RowsAdapter();channelList.setAdapter(channelAdapter);
        stations.addView(channelList,new LinearLayout.LayoutParams(-1,0,1));body.addView(stations,new LinearLayout.LayoutParams(dp(174),-1));
        LinearLayout preview=col();preview.setPadding(dp(10),0,0,0);body.addView(preview,new LinearLayout.LayoutParams(0,-1,1));
        videoBox=new PreviewFrame(this);videoBox.setBackground(shape(PANEL,0xff263940));
        preview.addView(videoBox,new LinearLayout.LayoutParams(-1,0,1));
        video=new TvPlayerView(this);applyAspect();video.setUseController(false);video.setFocusable(false);videoBox.addView(video,new FrameLayout.LayoutParams(-1,-1));
        TextView placeholder=text("▶\n选择频道，按确定开始观看",17,MUTED);placeholder.setGravity(Gravity.CENTER);placeholder.setTag("placeholder");
        videoBox.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout details=row();details.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words=col();title=text("把电视，变简单。",18,TEXT);title.setTypeface(null,Typeface.BOLD);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);add(words,title,-1,24);
        state=text("请选择频道，按确定观看",11,MUTED);state.setSingleLine(true);state.setEllipsize(TextUtils.TruncateAt.END);add(words,state,-1,20);details.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout controls=row();controls.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);sourceButton=button("线路",this::sources);favoriteButton=button("收藏",this::toggleFavorite);aspectButton=button("画面",this::aspectDialog);add(controls,sourceButton,54,30);add(controls,favoriteButton,54,30);add(controls,aspectButton,54,30);details.addView(controls,new LinearLayout.LayoutParams(-2,-2));preview.addView(details);
        add(home,text("左右切换分类 / 频道   ·   确定直接观看   ·   播放中确定打开选台   ·   选台列表长按确定收藏 / 取消",11,MUTED),-1,24);
        hud=text("",15,TEXT);hud.setPadding(dp(18),dp(12),dp(18),dp(12));hud.setBackground(shape(0xe6152229,0xff285746));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);hp.setMargins(dp(28),dp(24),dp(28),0);
        root.addView(hud,hp);hud.setVisibility(View.GONE);
        wireMainLists();wireHomeFocus(); reloadViews(); updateNetworkLabel();
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
        channelList.setOnItemClickListener((p,v,pos,id)->{if(pos<visible.size()){play(visible.get(pos));setFullscreen(true);}else recovery();});
        channelList.setOnItemLongClickListener((p,v,pos,id)->{if(pos<visible.size()){play(visible.get(pos));sources();}return true;});
    }
    void wireHomeFocus(){
        networkLabel.setBackground(background(PANEL));
        categoryList.setOnKeyListener((v,k,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(k==KeyEvent.KEYCODE_DPAD_RIGHT){channelList.requestFocus();return true;}if(k==KeyEvent.KEYCODE_DPAD_UP&&categoryList.getSelectedItemPosition()<=0){networkLabel.requestFocus();return true;}return false;});
        channelList.setOnKeyListener((v,k,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;if(k==KeyEvent.KEYCODE_DPAD_LEFT){categoryList.requestFocus();return true;}if(k==KeyEvent.KEYCODE_DPAD_RIGHT){sourceButton.requestFocus();return true;}if(k==KeyEvent.KEYCODE_DPAD_UP&&channelList.getSelectedItemPosition()<=0){searchButton.requestFocus();return true;}return false;});
        focusKeys(sourceButton,channelList,favoriteButton,settingsButton,channelList);
        focusKeys(favoriteButton,sourceButton,aspectButton,settingsButton,channelList);
        focusKeys(aspectButton,favoriteButton,null,settingsButton,channelList);
        focusKeys(networkLabel,null,searchButton,null,categoryList);
        focusKeys(searchButton,networkLabel,importButton,null,channelList);
        focusKeys(importButton,searchButton,settingsButton,null,sourceButton);
        focusKeys(settingsButton,importButton,null,null,aspectButton);
    }
    void focusKeys(View view,View left,View right,View up,View down){view.setOnKeyListener((v,k,e)->{if(e.getAction()!=KeyEvent.ACTION_DOWN)return false;View next=k==KeyEvent.KEYCODE_DPAD_LEFT?left:k==KeyEvent.KEYCODE_DPAD_RIGHT?right:k==KeyEvent.KEYCODE_DPAD_UP?up:k==KeyEvent.KEYCODE_DPAD_DOWN?down:null;if(k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT||k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN){if(next!=null)next.requestFocus();return true;}return false;});}
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
            +numbers.optInt(Playlist.key(c.name))+"  "+c.name+(favorites.contains(Playlist.key(c.name))?"  ★":""));
        if(labels.isEmpty())labels.add("暂无频道 · 确定恢复");
        adapter.rows=labels;adapter.notifyDataSetChanged();
    }
    class RowsAdapter extends BaseAdapter {
        final boolean translucent;RowsAdapter(){this(false);}RowsAdapter(boolean translucent){this.translucent=translucent;}
        List<String> rows=new ArrayList<>();
        public int getCount(){return rows.size();}
        public Object getItem(int pos){return rows.get(pos);}
        public long getItemId(int pos){return pos;}
        public View getView(int pos,View convert,ViewGroup parent){
            TextView t=convert instanceof TextView?(TextView)convert:text("",15,TEXT);
            t.setText(rows.get(pos));t.setTextSize(translucent?14:15);t.setPadding(dp(translucent?8:12),dp(translucent?5:7),dp(8),dp(translucent?5:7));t.setMinHeight(dp(translucent?40:46));
            t.setMaxLines(2);t.setEllipsize(TextUtils.TruncateAt.END);t.setBackground(translucent?drawerRowBackground():background(PANEL));if(translucent)t.setShadowLayer(dp(2),0,dp(1),0xff000000);
            t.setLayoutParams(new AbsListView.LayoutParams(-1,dp(translucent?(rows.get(pos).contains("\n")?48:40):(rows.get(pos).contains("\n")?58:46))));
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
        drawer=new FrameLayout(this);drawer.setBackground(shape(0xe6101c22,0x905c8274));
        int width=Math.min(260,Math.max(240,(int)(getResources().getConfiguration().screenWidthDp*0.28f)));
        FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(dp(width),-1,Gravity.LEFT);
        panel.setMargins(dp(8),dp(8),0,dp(8));root.addView(drawer,panel);
        LinearLayout shell=col();shell.setPadding(dp(8),dp(8),dp(8),dp(6));drawer.addView(shell,new FrameLayout.LayoutParams(-1,-1));
        add(shell,text("直播选台",17,GREEN),-1,24);
        add(shell,text("确定换台 · 长按收藏 / 取消",10,MUTED),-1,18);
        LinearLayout lists=row();shell.addView(lists,new LinearLayout.LayoutParams(-1,0,1));
        drawerGroups=list();drawerGroupAdapter=new RowsAdapter(true);drawerGroupAdapter.rows=new ArrayList<>(categories);drawerGroups.setAdapter(drawerGroupAdapter);
        lists.addView(drawerGroups,new LinearLayout.LayoutParams(dp(80),-1));
        drawerChannels=list();drawerChannelAdapter=new RowsAdapter(true);drawerChannels.setAdapter(drawerChannelAdapter);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1);cp.leftMargin=dp(8);lists.addView(drawerChannels,cp);
        LinearLayout info=row();info.setGravity(Gravity.CENTER_VERTICAL);info.setPadding(dp(10),dp(7),dp(10),dp(7));info.setBackground(shape(0xd0101c22,0x605c8274));
        drawerLogo=new ImageView(this);drawerLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);drawerLogo.setBackground(shape(0xe8ffffff,0x00ffffff));add(info,drawerLogo,40,32);
        LinearLayout words=col();drawerTitle=text("",13,GREEN);drawerTitle.setTypeface(null,Typeface.BOLD);drawerTitle.setMaxLines(1);drawerTitle.setEllipsize(TextUtils.TruncateAt.END);words.addView(drawerTitle,new LinearLayout.LayoutParams(-1,dp(20)));drawerProgram=text("",11,TEXT);drawerProgram.setMaxLines(2);drawerProgram.setEllipsize(TextUtils.TruncateAt.END);words.addView(drawerProgram,new LinearLayout.LayoutParams(-1,dp(30)));info.addView(words,new LinearLayout.LayoutParams(0,-2,1));add(shell,info,-1,66);updateDrawerInfo();
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
                if(k==KeyEvent.KEYCODE_DPAD_RIGHT){statsToggle.requestFocus();return true;}}return false;
        });
        drawerChannels.setOnItemClickListener((p,v,pos,id)->{
            if(pos<drawerVisible.size()){Playlist.Channel selected=drawerVisible.get(pos);category=drawerCategory;
                if(!Playlist.key(selected.name).equals(Playlist.key(current.name)))play(selected);
                closeDrawer();}
        });
        drawerChannels.setOnItemLongClickListener((p,v,pos,id)->{if(pos<drawerVisible.size())toggleFavorite(drawerVisible.get(pos));return true;});
        if(focusGroups)drawerGroups.requestFocus();else drawerChannels.requestFocus();resetDrawerTimeout();
        android.util.Log.i("MintTV","DRAWER_OPEN channel="+current.name+" playing="+(player!=null&&player.isPlaying()));
    }
    void closeDrawer(){handler.removeCallbacks(favoriteHold);handler.removeCallbacks(autoHideDrawer);if(!drawerOpen)return;drawerOpen=false;if(statsPanel!=null)statsPanel.setVisibility(View.GONE);root.removeView(drawer);drawer=null;drawerLogo=null;drawerProgram=null;drawerTitle=null;videoBox.requestFocus();
        android.util.Log.i("MintTV","DRAWER_CLOSE channel="+(current==null?"":current.name)+" playing="+(player!=null&&player.isPlaying()));}
    void setFullscreen(boolean yes){
        if(fullscreen==yes)return;closeDrawer();fullscreen=yes;
        if(yes){
            previewParent=(ViewGroup)videoBox.getParent();previewIndex=previewParent.indexOfChild(videoBox);previewParams=videoBox.getLayoutParams();
            previewParent.removeView(videoBox);home.setVisibility(View.GONE);
            root.addView(videoBox,0,new FrameLayout.LayoutParams(-1,-1));videoBox.setFocusable(true);videoBox.requestFocus();showHud();
        }else{
            root.removeView(videoBox);previewParent.addView(videoBox,previewIndex,previewParams);
            home.setVisibility(View.VISIBLE);videoBox.setFocusable(false);hud.setVisibility(View.GONE);reloadViews();if(current!=null)focusChannel(current);
        }
    }
    void showHud(){
        if(!fullscreen||drawerOpen||current==null)return;
        hud.setText(current.name+"   ·   线路 "+(sourceIndex+1)+" / "+current.urls.size()+(Store.prefs(this).getString("epgUrl","").isEmpty()?"":"\n"+Epg.summary(this,current.name))+"\n确定 选台  ·  菜单 线路  ·  返回 主页");
        hud.setVisibility(View.VISIBLE);hud.bringToFront();handler.removeCallbacks(hideHud);handler.postDelayed(hideHud,4000);
    }
    void showDiagnostics(){
        if(statsPanel==null){statsPanel=col();statsPanel.setPadding(dp(10),dp(6),dp(10),dp(6));statsPanel.setBackground(shape(0xe5101c22,0xff345047));diagnostics=text("",12,TEXT);statsPanel.addView(diagnostics,new LinearLayout.LayoutParams(-1,-2));rateGraph=new RateGraph(this);statsPanel.addView(rateGraph,new LinearLayout.LayoutParams(-1,dp(70)));statsToggle=button("详细信息",()->{statsDetailed=!statsDetailed;updateDiagnostics();resetDrawerTimeout();});statsToggle.setPadding(dp(6),0,dp(6),0);statsToggle.setOnKeyListener((v,k,e)->{if(e.getAction()==KeyEvent.ACTION_DOWN&&k==KeyEvent.KEYCODE_DPAD_LEFT){drawerChannels.requestFocus();return true;}return false;});statsPanel.addView(statsToggle,new LinearLayout.LayoutParams(-1,dp(28)));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(Math.min(220,getResources().getConfiguration().screenWidthDp/2-30)),-2,Gravity.TOP|Gravity.RIGHT);p.setMargins(0,dp(16),dp(16),0);root.addView(statsPanel,p);}
        statsDetailed=false;statsPanel.setVisibility(View.VISIBLE);statsPanel.bringToFront();updateDiagnostics();
    }
    String fmt(Format f){return f==null?"等待轨道信息":(f.sampleMimeType==null?"未知":f.sampleMimeType)+(f.codecs==null?"":" / "+f.codecs);}
    void updateDiagnostics(){
        if(diagnostics==null)return;long now=android.os.SystemClock.elapsedRealtime(),bytes=mediaBytes.get();
        double rate=(bytes-telemetryBytes)*8.0/Math.max(1,now-telemetryTime)/1000.0;telemetryBytes=bytes;telemetryTime=now;if(rateGraph!=null)rateGraph.add(rate);
        Format v=player==null?null:player.getVideoFormat(),a=player==null?null:player.getAudioFormat();
        String channels=a==null||a.channelCount<0?"声道未知":a.channelCount==1?"单声道":a.channelCount==2?"立体声 2.0":a.channelCount+" 声道";
        FrameLayout.LayoutParams statsSize=(FrameLayout.LayoutParams)statsPanel.getLayoutParams();int desired=dp(Math.min(statsDetailed?320:220,getResources().getConfiguration().screenWidthDp/2-30));if(statsSize.width!=desired){statsSize.width=desired;statsPanel.setLayoutParams(statsSize);}
        rateGraph.setVisibility(statsDetailed?View.VISIBLE:View.GONE);statsToggle.setText(statsDetailed?"收起详细信息":"详细信息");
        if(!statsDetailed){diagnostics.setText((v!=null?v.width+" × "+v.height:"分辨率待获取")+String.format(Locale.CHINA,"\n实时流码率 %.2f Mbps",rate));return;}
        diagnostics.setText(String.format(Locale.CHINA,"实时流码率（下载） %.2f Mbps\n",rate)
            +"视频："+(v==null?"分辨率待获取":v.width+" × "+v.height)+" · "+fmt(v)
            +"\n音频："+fmt(a)+"\n"+channels+(a!=null&&a.sampleRate>0?" / "+a.sampleRate+" Hz":"")
            +"\n显示："+(current!=null&&ChannelAspect.mode(Store.prefs(this).getString("channelAspects","{}"),current.name)==1?"强制 16:9":current!=null&&ChannelAspect.mode(Store.prefs(this).getString("channelAspects","{}"),current.name)==2?"强制 4:3":"自动 / 缩放")+"\n解码器："+videoDecoder+"\n"+audioDecoder);
    }
    void initPlayer(){
        if(player!=null)return;
        HttpDataSource.Factory http=LiveNetwork.http(this);
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
            public void onRenderedFirstFrame(){rememberPlayback();rendered=true;cancelTimeout();setPlaybackState("正在播放 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());
                android.util.Log.i("MintTV","FIRST_FRAME "+current.name+" source="+sourceIndex);}
            public void onIsPlayingChanged(boolean playing){if(playing&&rendered){cancelTimeout();setPlaybackState("正在播放 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());}}
            public void onPlaybackStateChanged(int s){if(current==null||stopped)return;
                if(s==Player.STATE_BUFFERING){setPlaybackState("正在连接 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());armTimeout();}
                if(s==Player.STATE_ENDED)advance("直播已中断");}
        });
    }
    void play(Playlist.Channel c){
        ProbeBudget.pause(8000);digits="";handler.removeCallbacks(tuneNumber);
        current=c;applyAspect();sourceOrder=Health.order(this,c);sourceAttempt=0;sourceIndex=sourceOrder[0];title.setText(c.name);
        View placeholder=videoBox.findViewWithTag("placeholder");if(placeholder!=null)placeholder.setVisibility(View.GONE);
        Store.prefs(this).edit().putString("last",c.name).apply();updateEpg();if(!stopped)trySource();renderChannels(false);showHud();
    }
    void trySource(){
        ProbeBudget.foreground(true);ProbeBudget.pause(8000);cancelTimeout();initPlayer();videoDecoder="等待解码";audioDecoder="等待解码";rendered=false;
        generation++;int g=generation;setPlaybackState("正在连接 · 线路 "+(sourceIndex+1)+" / "+current.urls.size());player.stop();
        String url=current.urls.get(sourceIndex);
        if(url.startsWith("rtp://")||url.startsWith("rtsp://")){handler.post(()->{if(g==generation)advance("请导入 HTTP/HTTPS 直播源");});return;}
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));player.prepare();player.play();armTimeout();
    }
    void armTimeout(){cancelTimeout();final int g=generation;timeout=()->{if(g==generation&&!stopped)advance("连接或缓冲超时");};handler.postDelayed(timeout,14000);}
    void cancelTimeout(){if(timeout!=null)handler.removeCallbacks(timeout);timeout=null;}
    void advance(String reason){
        cancelTimeout();if(stopped||current==null)return;
        if(sourceAttempt+1<sourceOrder.length){sourceAttempt++;sourceIndex=sourceOrder[sourceAttempt];trySource();}
        else{generation++;if(player!=null)player.stop();ProbeBudget.foreground(false);setPlaybackState("线路暂不可用 · 按重试恢复");toast(reason+"，已尝试全部 "+current.urls.size()+" 条线路");}
    }
    void rememberPlayback(){
        if(current==null||player==null)return;Format format=player.getVideoFormat();if(format==null)return;
        String epoch=Store.prefs(this).getString("healthEpoch","");Playlist.Channel channel=current;String address=current.urls.get(sourceIndex);int width=format.width,height=format.height;
        io.execute(()->Health.played(this,epoch,channel,address,width,height));
    }
    void recovery(){
        new AlertDialog.Builder(this).setTitle("恢复观看 · 选择操作")
            .setItems(new String[]{"重试当前频道","更新列表并重新检测","选择省份与运营商","检查网络连接"},(d,i)->{
                if(i==0){if(current!=null)play(current);else{query="";category="全部频道";reloadViews();toast("请选择频道，或更新列表并重新检测");}}
                if(i==1){refresh(true,true);io.execute(()->Health.ensure(this,true));toast("正在更新并重新检测，已有频道仍可观看");}
                if(i==2)networkSettings();
                if(i==3)try{startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));}catch(Exception e){toast("请在盒子系统设置中检查网络连接");}
            }).setNegativeButton("返回",null).show();
    }
    void sources(){
        if(current==null){toast("请先选择频道");return;}
        String[] labels=new String[current.urls.size()];for(int i=0;i<labels.length;i++)labels[i]="线路 "+(i+1)+(i==sourceIndex?" · 当前":"")+" · "+Health.qualityLabel(this,current,i)+"\n"+Uri.parse(current.urls.get(i)).getHost();
        new AlertDialog.Builder(this).setTitle(current.name+" · 线路").setSingleChoiceItems(labels,sourceIndex,(d,i)->{sourceOrder=SourceRanking.withFirst(Health.order(this,current),i);sourceAttempt=0;sourceIndex=i;trySource();d.dismiss();showHud();})
            .setNeutralButton("重检当前频道",(d,w)->{Playlist.Channel selected=current;io.execute(()->Health.recheck(this,selected));toast("已加入单频道检测队列");}).setPositiveButton("画面比例",(d,w)->aspectDialog()).setNegativeButton("返回",null).show();
    }
    void toggleFavorite(){if(current==null){toast("请先选择频道");return;}toggleFavorite(current);}
    void toggleFavorite(Playlist.Channel channel){
        String key=Playlist.key(channel.name);
        if(!favorites.add(key))favorites.remove(key);Store.prefs(this).edit().putStringSet("favorites",new HashSet<>(favorites)).apply();
        renderChannels(false);if(drawerOpen)renderChannels(true);toast(favorites.contains(key)?"已收藏 "+channel.name:"已取消收藏 "+channel.name);
    }
    void channelStep(int step){
        List<Playlist.Channel> pool=filtered(category,false);if(pool.isEmpty())pool=filtered("全部频道",false);if(pool.isEmpty())return;
        int at=-1;for(int i=0;i<pool.size();i++)if(current!=null&&Playlist.key(pool.get(i).name).equals(Playlist.key(current.name)))at=i;
        play(pool.get((at+step+pool.size())%pool.size()));
    }
    void assignNumbers(){numbers=new JSONObject();int n=0;for(Playlist.Channel c:channels)if(!Health.hidden(this,c))try{numbers.put(Playlist.key(c.name),++n);}catch(Exception ignored){}if(networkLabel!=null)updateNetworkLabel();}
    android.graphics.drawable.Drawable drawerRowBackground(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},shape(0xd0285746,GREEN));d.addState(new int[]{android.R.attr.state_focused},shape(0xc0285746,GREEN));d.addState(new int[]{android.R.attr.state_selected},shape(0xb0285746,GREEN));d.addState(new int[]{},shape(0x50152229,0x50608072));return d;}
    void updateDrawerInfo(){if(!drawerOpen||current==null||drawerProgram==null)return;drawerTitle.setText("正在观看 · "+current.name);drawerProgram.setText(Store.prefs(this).getString("epgUrl","").isEmpty()?"节目单已关闭":Epg.summary(this,current.name));String url=current.logo;if(url.isEmpty())url=Epg.logo(this,current.name);if(url.isEmpty()&&Store.prefs(this).getBoolean("defaultLogos",true))url="https://raw.githubusercontent.com/sparkssssssssss/epg/main/logo/"+Uri.encode(ChannelNames.key(current.name))+".png";logos.show(drawerLogo,url);}
    void applyAspect(){if(video!=null)video.presentation(current==null?0:ChannelAspect.mode(Store.prefs(this).getString("channelAspects","{}"),current.name));}
    void aspectDialog(){if(current==null){toast("请先选择频道");return;}new AlertDialog.Builder(this).setTitle(current.name+" · 画面比例").setSingleChoiceItems(new String[]{"自动 · 按信号比例","强制 16:9 · 修正画面被压窄","强制 4:3 · 保持经典画幅","等比放大填满 · 裁切边缘","拉伸铺满 · 随屏幕比例"},ChannelAspect.mode(Store.prefs(this).getString("channelAspects","{}"),current.name),(d,i)->{try{JSONObject modes=new JSONObject(Store.prefs(this).getString("channelAspects","{}"));modes.put(Playlist.key(current.name),i);Store.prefs(this).edit().putString("channelAspects",modes.toString()).apply();applyAspect();}catch(Exception ignored){}d.dismiss();}).setNegativeButton("返回",null).show();}
    void updateEpg(){updateDrawerInfo();updatePlaybackSummary();}
    void setPlaybackState(String value){playbackStatus=value;updatePlaybackSummary();}
    void updatePlaybackSummary(){if(state==null)return;String programme=current==null||Store.prefs(this).getString("epgUrl","").isEmpty()?"":Epg.summary(this,current.name).split("\\n",2)[0];state.setText((current==null?"":current.group+" · ")+playbackStatus+(programme.isEmpty()?"":"  |  "+programme));}

    void finishNumber(){if(digits.isEmpty())return;if(!hasWindowFocus()){digits="";hideHud.run();return;}int n=Integer.parseInt(digits);digits="";for(Playlist.Channel c:channels)if(numbers.optInt(Playlist.key(c.name))==n){if(Health.hidden(this,c)){toast("该频道已隐藏，请在设置中重检");hideHud.run();return;}closeDrawer();play(c);setFullscreen(true);return;}hideHud.run();toast("没有该编号的频道");}
    @Override public boolean dispatchTouchEvent(MotionEvent e){if(drawerOpen)resetDrawerTimeout();return super.dispatchTouchEvent(e);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(drawerOpen)resetDrawerTimeout();
        int digit=e.getKeyCode()-KeyEvent.KEYCODE_0;if(e.getKeyCode()>=KeyEvent.KEYCODE_NUMPAD_0&&e.getKeyCode()<=KeyEvent.KEYCODE_NUMPAD_9)digit=e.getKeyCode()-KeyEvent.KEYCODE_NUMPAD_0;
        if(ready&&hasWindowFocus()&&digit>=0&&digit<=9){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){if(digits.length()>=4)digits="";digits+=digit;hud.setText("频道号："+digits);hud.setVisibility(View.VISIBLE);hud.bringToFront();handler.removeCallbacks(hideHud);handler.removeCallbacks(tuneNumber);handler.postDelayed(tuneNumber,1400);}return true;}
        if(!digits.isEmpty()&&e.getAction()==KeyEvent.ACTION_DOWN&&(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER)){consumeSelectUp=true;handler.removeCallbacks(tuneNumber);finishNumber();return true;}

        // The opening DOWN belongs to the player, so its UP must not select a drawer row.
        if(consumeSelectUp&&(e.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||e.getKeyCode()==KeyEvent.KEYCODE_ENTER)){
            if(e.getAction()==KeyEvent.ACTION_UP)consumeSelectUp=false;
            return true;
        }
        if(drawerConfirm(e))return true;
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
        if(!digits.isEmpty()){digits="";handler.removeCallbacks(tuneNumber);hideHud.run();return;}if(drawerOpen){closeDrawer();return;}if(fullscreen){setFullscreen(false);return;}
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
 void urlDialog(){new AlertDialog.Builder(this).setTitle("添加订阅 / 导入直播源").setItems(new String[]{"手机扫码 · 局域网上传文件或发送网址","遥控器输入订阅网址"},(d,i)->{if(i==0){if(lanImport!=null)lanImport.close();lanImport=new LanImportDialog(this);lanImport.show();}else manualUrlDialog();}).setNegativeButton("返回",null).show();}
 void manualUrlDialog(){EditText input=new EditText(this);input.setTextColor(TEXT);input.setSingleLine(true);input.setHint("https://example.com/live.m3u");input.setInputType(17);LinearLayout box=col();box.setPadding(dp(24),dp(12),dp(24),0);box.addView(input);new AlertDialog.Builder(this).setTitle("添加订阅网址").setView(box).setPositiveButton("导入",(d,w)->{String url=input.getText().toString().trim();toast("正在导入…");io.execute(()->{try{String text=Store.fetch(url);Store.add(this,Uri.parse(url).getHost(),url,text);Health.queueImported(this,Playlist.parse(text));handler.post(()->{reload();toast("导入成功");});}catch(Exception e){handler.post(()->toast("导入失败："+e.getMessage()));}});}).setNegativeButton("取消",null).show();}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==42){if(updater!=null)updater.permissionReturned();return;}if(request==21&&result==RESULT_OK&&data!=null){Uri uri=data.getData();io.execute(()->{try{String text=Store.read(getContentResolver().openInputStream(uri));Store.add(this,"本地列表 "+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date()),"",text);Health.queueImported(this,Playlist.parse(text));handler.post(()->{reload();toast("文件导入成功");});}catch(Exception e){handler.post(()->toast("导入失败："+e.getMessage()));}});}}
 void manage(){JSONArray a=Store.imports(this);if(a.length()==0){toast("还没有导入的列表");return;}String[] names=new String[a.length()];for(int i=0;i<names.length;i++)names[i]=a.optJSONObject(i).optString("title")+(a.optJSONObject(i).optString("url").isEmpty()?" · 本地":" · 订阅")+(a.optJSONObject(i).optString("updateError").isEmpty()?"":"\n"+a.optJSONObject(i).optString("updateError"));new AlertDialog.Builder(this).setTitle("导入管理 · 选择后可移除").setItems(names,(d,i)->new AlertDialog.Builder(this).setTitle("移除 "+names[i]+"？").setPositiveButton("移除",(x,w)->{Store.remove(this,i);reload();}).setNegativeButton("取消",null).show()).setNegativeButton("返回",null).show();}

    View settingsContent(String message){ScrollView scroll=new ScrollView(this);LinearLayout box=col();box.setPadding(dp(20),dp(4),dp(20),dp(6));TextView description=text(message,14,TEXT);box.addView(description,new LinearLayout.LayoutParams(-1,-2));LinearLayout links=row();for(TextView entry:new TextView[]{button("使用说明",()->new TvTools(this).help()),button("免责声明",()->new TvTools(this).disclaimer())}){LinearLayout.LayoutParams link=new LinearLayout.LayoutParams(0,dp(36),1);link.setMargins(dp(4),dp(8),dp(4),dp(4));links.addView(entry,link);}box.addView(links);scroll.addView(box);return scroll;}
    void settings(){
        long t=Store.prefs(this).getLong("baseTime",0);
        String date=t==0?"尚未下载频道列表":new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(t));
        new AlertDialog.Builder(this).setTitle("薄荷电视 "+AppUpdate.version(this)+" · "+Store.profileLabel(this))
            .setView(settingsContent((Store.isManual(this)?"手动模式":"自动适配")+"\n"+Store.prefs(this).getString("geoStatus","首次联网后识别省份和运营商")
                +"\n列表来源："+Store.prefs(this).getString("baseOrigin",t==0?"无内置源":"上次缓存")+"\n最近源更新："+date+"\n"+Store.prefs(this).getString("result","")
                +"\n\n首次及网络变化时识别公网出口的省份 / 运营商；匹配不变保留原源，每日更新列表。识别或下载失败保留缓存。识别结果不准确时，可手动选择。"
                +"\n\n确定打开直播选台，左右切换分类和频道，返回先收起选台。菜单键选择线路。"))
            .setPositiveButton("更新频道源",(d,w)->refresh(true,true)).setNeutralButton("系统更新",(d,w)->updater().check()).setNegativeButton("更多设置",(d,w)->new TvTools(this).show()).show();
    }
    void networkStatus(){
        LinearLayout box=col();box.setPadding(dp(24),dp(6),dp(24),dp(8));
        TextView profile=text(Store.profileLabel(this),25,GREEN);profile.setTypeface(null,Typeface.BOLD);add(box,profile,-1,42);
        add(box,text(Store.isManual(this)?"手动匹配 · 已保留用户选择":"自动匹配 · 根据公网出口的省份与运营商",13,TEXT),-1,28);
        TextView detail=text("来源：对应省份 / 运营商的直播源列表\n缓存：同省同运营商，沿用已有源与检测结果\n画质：在已验证可播源中，优先最高分辨率\n更新：每日检查，失败保留现有列表",14,TEXT);detail.setMaxLines(5);add(box,detail,-1,108);
        TextView scan=text(Health.status(this),12,MUTED);scan.setMaxLines(3);add(box,scan,-1,54);
        new AlertDialog.Builder(this).setTitle("网络适配状态").setView(box).setPositiveButton("知道了",null).setNeutralButton("网络设置",(d,w)->networkSettings()).show();
    }
    void networkSettings(){
        new AlertDialog.Builder(this).setTitle("网络适配 · "+Store.profileLabel(this))
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
    void updateNetworkLabel(){networkLabel.setText(Store.profileLabel(this)+" · "+(Store.isManual(this)?"手动":"自动适配")+" · "+numbers.length()+" 个频道");}
    void refresh(boolean force){refresh(force,force);}
    void refresh(boolean forceLists,boolean forceDetection){
        if(updating){pendingForce|=forceLists;pendingDetection|=forceDetection;return;}
        updating=true;if(forceLists)toast("正在更新频道源…");
        io.execute(()->{
            Store.RefreshResult result=Store.refresh(this,forceLists,forceDetection);Health.ensure(this,false);
            handler.post(()->{
                if(isDestroyed())return;updating=false;
                if(result.changed)reload(result.profileChanged);updateNetworkLabel();
                if(forceLists||result.profileChanged)toast(result.message);
                if(pendingDetection||pendingForce){boolean f=pendingForce;pendingForce=false;pendingDetection=false;refresh(f,true);}
            });
        });
    }
    void reload(){reload(false);}
    void reload(boolean retune){io.execute(()->{java.util.List<Playlist.Channel> loaded=Store.channels(this);handler.post(()->{
        if(isDestroyed())return;channels.clear();channels.addAll(loaded);assignNumbers();reloadViews();if(retune&&current!=null&&!stopped)for(Playlist.Channel c:channels)if(Playlist.key(c.name).equals(Playlist.key(current.name))){play(c);break;}
    });});}
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
    @Override protected void onResume(){super.onResume();setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);if(ready)refresh(false,true);}
    @Override protected void onPause(){setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);super.onPause();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);}
    @Override protected void onStart(){super.onStart();stopped=false;if(current!=null&&player==null)trySource();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)ProbeBudget.pause(60000);}
    @Override protected void onStop(){
        if(lanImport!=null){lanImport.close();lanImport=null;}handler.removeCallbacks(autoHideDrawer);if(drawerOpen)closeDrawer();stopped=true;heldChannel=null;heldFavorite=false;handler.removeCallbacks(favoriteHold);digits="";handler.removeCallbacks(tuneNumber);generation++;cancelTimeout();
        if(player!=null){video.setPlayer(null);player.release();player=null;}
        ProbeBudget.foreground(false);if(ready)io.execute(()->Health.queueUncertain(this));super.onStop();
    }
    @Override protected void onDestroy(){
        if(networkCallback!=null)try{connectivity.unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}
        if(updater!=null)updater.close();if(logos!=null)logos.close();if(lanImport!=null)lanImport.close();handler.removeCallbacksAndMessages(null);super.onDestroy();
    }
}
