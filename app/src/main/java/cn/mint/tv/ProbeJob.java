package cn.mint.tv;

import android.app.job.*;
import android.os.*;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.media3.common.*;
import androidx.media3.exoplayer.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.source.*;
import org.json.*;

/** Sequential real decoder probes; no audio output or audio focus. Progress survives process death. */
@androidx.media3.common.util.UnstableApi
public class ProbeJob extends JobService {
    final Handler handler=new Handler(Looper.getMainLooper());
    ExoPlayer probe;SurfaceTexture texture;Surface surface;JobParameters params;
    JSONArray tasks,partial;String epoch;int channel,url;long started;boolean active,testing;
    Runnable timeout;
    public boolean onStartJob(JobParameters p){
        params=p;epoch=Store.prefs(this).getString("healthEpoch","");
        if(Store.prefs(this).getBoolean("healthDone",false)||!Health.valid(this))return false;
        try{tasks=new JSONArray(Store.prefs(this).getString("healthTasks","[]"));partial=new JSONArray(Store.prefs(this).getString("healthPartial","[]"));}
        catch(Exception e){return false;}
        channel=Store.prefs(this).getInt("healthChannel",0);url=Store.prefs(this).getInt("healthUrl",0);
        active=true;started=SystemClock.elapsedRealtime();handler.post(this::next);return true;
    }
    boolean current(){return active&&epoch.equals(Store.prefs(this).getString("healthEpoch",""))&&Health.valid(this);}
    void next(){
        release();if(!current()){finish(false);return;}
        if(SystemClock.elapsedRealtime()-started>240000){finish(true);return;}
        if(channel>=tasks.length()){
            synchronized(Health.class){if(!current()){finish(false);return;}Store.prefs(this).edit().putBoolean("healthDone",true).putLong("healthRevision",System.currentTimeMillis()).commit();}finish(false);return;
        }
        JSONObject task=tasks.optJSONObject(channel);JSONArray urls=task.optJSONArray("urls");
        if(url>=urls.length()){
            if(!Health.record(this,epoch,task,partial,channel+1)){finish(false);return;}
            channel++;url=0;partial=new JSONArray();handler.post(this::next);return;
        }
        android.net.NetworkInfo network=getSystemService(android.net.ConnectivityManager.class).getActiveNetworkInfo();
        if(network==null||!network.isConnected()){finish(true);return;}
        String address=urls.optString(url);testing=true; if(address.startsWith("rtp://")||address.startsWith("rtsp://")){complete(-1);return;}
        try{
            DefaultRenderersFactory renderers=new DefaultRenderersFactory(this).setMediaCodecSelector((mime,secure,tunnel)->{
                java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> all=androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mime,secure,tunnel);
                java.util.List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> software=new java.util.ArrayList<>();
                for(androidx.media3.exoplayer.mediacodec.MediaCodecInfo codec:all)if(codec.softwareOnly)software.add(codec);
                return software;
            });
            DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(5000).setReadTimeoutMs(5000).setAllowCrossProtocolRedirects(true);
            probe=new ExoPlayer.Builder(this,renderers).setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(1000,2500,250,500).build()).setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http))).build();
            probe.setVolume(0);probe.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).build(),false);
            texture=new SurfaceTexture(0);surface=new Surface(texture);probe.setVideoSurface(surface);
            probe.addListener(new Player.Listener(){
                public void onRenderedFirstFrame(){complete(1);}
                public void onPlayerError(PlaybackException e){
                    // Decoder / resource limitations are inconclusive, never hide a possibly playable channel.
                    complete(e.errorCode>=4000&&e.errorCode<6000?0:-1);
                }
            });
            timeout=()->complete(-1);handler.postDelayed(timeout,14000);
            probe.setMediaItem(MediaItem.fromUri(address));probe.prepare();probe.play();
        }catch(Exception e){complete(0);}
    }
    void complete(int result){
        if(!testing||!current())return;testing=false;if(timeout!=null)handler.removeCallbacks(timeout);
        android.net.NetworkInfo n=getSystemService(android.net.ConnectivityManager.class).getActiveNetworkInfo();
        if(n==null||!n.isConnected()){finish(true);return;}
        synchronized(Health.class){if(!current())return;partial.put(result);url++;
            Store.prefs(this).edit().putInt("healthUrl",url).putString("healthPartial",partial.toString()).commit();}

        handler.post(this::next);
    }
    void release(){if(timeout!=null)handler.removeCallbacks(timeout);testing=false;
        if(probe!=null){probe.release();probe=null;}if(surface!=null){surface.release();surface=null;}if(texture!=null){texture.release();texture=null;}}
    void finish(boolean retry){active=false;release();jobFinished(params,retry);}
    public boolean onStopJob(JobParameters p){active=false;handler.removeCallbacksAndMessages(null);release();return true;}
    public void onDestroy(){active=false;handler.removeCallbacksAndMessages(null);release();super.onDestroy();}
}
