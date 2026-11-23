package cn.mint.tv;

import android.app.job.*;
import android.content.*;
import org.json.*;
import java.util.*;

/** Durable, network-scoped scan state. Unknown channels remain visible. */
public final class Health {
    static final int JOB=2402;
    public static synchronized void ensure(Context c,boolean force){
        if(!Store.prefs(c).contains("province"))return;
        String scope=Store.activeProfile(c).key();
        if(!force&&scope.equals(Store.prefs(c).getString("healthScope",""))){
            if(!Store.prefs(c).getBoolean("healthDone",false)||!Store.prefs(c).getString("healthPriority","[]").equals("[]"))schedule(c,false);
            return;
        }
        JSONObject old=results(c);JSONObject retained=new JSONObject();
        if(force&&scope.equals(Store.prefs(c).getString("healthScope",""))){
            for(Iterator<String> it=old.keys();it.hasNext();){String key=it.next();JSONObject v=old.optJSONObject(key);
                if(v!=null)try{retained.put(key,v);}catch(Exception ignored){}
            }
        }
        JSONArray tasks=new JSONArray();for(Playlist.Channel channel:Store.channels(c)){
            JSONObject task=new JSONObject();try{task.put("name",channel.name);task.put("urls",new JSONArray(channel.urls));tasks.put(task);}catch(Exception ignored){}
        }
        Store.prefs(c).edit().putString("healthScope",scope).putString("healthEpoch",UUID.randomUUID().toString())
            .putString("healthTasks",tasks.toString()).putString("healthResults",retained.toString())
            .putInt("healthChannel",0).putInt("healthUrl",0).putString("healthPartial","[]")
            .putString("healthPriority","[]").putInt("healthSingleUrl",0).putString("healthSinglePartial","[]").putBoolean("healthDone",false).putLong("healthRevision",System.currentTimeMillis()).apply();
        schedule(c,true);
    }
    static void schedule(Context c,boolean replace){
        JobScheduler scheduler=c.getSystemService(JobScheduler.class);
        if(!replace)for(JobInfo job:scheduler.getAllPendingJobs())if(job.getId()==JOB)return;
        if(replace)scheduler.cancel(JOB);
        scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(c,"cn.mint.tv.ProbeJob"))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true).setMinimumLatency(1000)
            .setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_LINEAR).build());
    }
    private static String cachedText; private static JSONObject cachedResults=new JSONObject();
    static synchronized JSONObject results(Context c){String text=Store.prefs(c).getString("healthResults","{}");
        if(text!=cachedText){try{cachedResults=new JSONObject(text);}catch(Exception e){cachedResults=new JSONObject();}cachedText=text;}return cachedResults;
    }
    static boolean valid(Context c){return Store.activeProfile(c).key().equals(Store.prefs(c).getString("healthScope",""));}
    public static synchronized boolean hidden(Context c,Playlist.Channel channel){
        JSONObject r=valid(c)?results(c).optJSONObject(Playlist.key(channel.name)):null;return r!=null&&r.optBoolean("hidden");
    }
    public static synchronized int first(Context c,Playlist.Channel channel){
        JSONObject r=valid(c)?results(c).optJSONObject(Playlist.key(channel.name)):null;
        JSONArray good=r==null?null:r.optJSONArray("good");
        if(good!=null)for(String url:channel.urls)for(int i=0;i<good.length();i++)if(url.equals(good.optString(i)))return channel.urls.indexOf(url);
        return 0;
    }
    static synchronized boolean record(Context c,String epoch,JSONObject task,JSONArray states,int nextChannel){
        if(!epoch.equals(Store.prefs(c).getString("healthEpoch","")))return false;
        JSONObject all=results(c),r=new JSONObject();JSONArray good=new JSONArray();boolean uncertain=false;
        JSONArray urls=task.optJSONArray("urls");for(int i=0;i<states.length();i++){
            if(states.optInt(i)==1)good.put(urls.optString(i));if(states.optInt(i)==0)uncertain=true;
        }
        int[] verdicts=new int[states.length()];for(int i=0;i<verdicts.length;i++)verdicts[i]=states.optInt(i);
        JSONObject previous=all.optJSONObject(Playlist.key(task.optString("name")));
        try{r.put("hidden",ProbeVerdict.hidden(previous!=null&&previous.optBoolean("hidden"),verdicts));if(uncertain&&good.length()==0&&previous!=null)good=previous.optJSONArray("good")==null?good:previous.optJSONArray("good");r.put("good",good);r.put("uncertain",uncertain);r.put("checkedAt",System.currentTimeMillis());
            all.put(Playlist.key(task.optString("name")),r);}catch(Exception ignored){}
        android.util.Log.i("MintTV","PROBE_CHANNEL_COMPLETE "+nextChannel+" "+task.optString("name")+" good="+good.length());
        SharedPreferences.Editor edit=Store.prefs(c).edit().putString("healthResults",all.toString()).putLong("healthRevision",System.currentTimeMillis());
        if(nextChannel<0){try{JSONArray queue=new JSONArray(Store.prefs(c).getString("healthPriority","[]"));queue.remove(0);edit.putString("healthPriority",queue.toString());}catch(Exception ignored){}edit.putInt("healthSingleUrl",0).putString("healthSinglePartial","[]");}
        else edit.putInt("healthChannel",nextChannel).putInt("healthUrl",0).putString("healthPartial","[]");edit.apply();return true;
    }
    public static synchronized void recheck(Context c,Playlist.Channel channel){
        ensure(c,false);try{JSONArray queue=new JSONArray(Store.prefs(c).getString("healthPriority","[]"));for(int i=0;i<queue.length();i++)if(queue.getJSONObject(i).getString("name").equals(channel.name))return;
            queue.put(new JSONObject().put("name",channel.name).put("urls",new JSONArray(channel.urls)));Store.prefs(c).edit().putString("healthPriority",queue.toString()).apply();schedule(c,true);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static synchronized String status(Context c){
        int total=0;try{total=new JSONArray(Store.prefs(c).getString("healthTasks","[]")).length();}catch(Exception ignored){}
        int hidden=0;JSONObject all=results(c);for(Iterator<String> it=all.keys();it.hasNext();){JSONObject v=all.optJSONObject(it.next());if(v!=null&&v.optBoolean("hidden"))hidden++;}
        return "已隐藏 "+hidden+" 个 · "+(Store.prefs(c).getBoolean("healthDone",false)?"检测完成":"后台检测中")+" · "+Store.prefs(c).getInt("healthChannel",0)+" / "+total+" 个频道";
    }
}

