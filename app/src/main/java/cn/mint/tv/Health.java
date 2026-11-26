package cn.mint.tv;

import android.app.job.*;
import android.content.*;
import org.json.*;
import java.util.*;

/** Durable, network-scoped scan state. Unknown channels remain visible. */
public final class Health {
    static final int JOB=2402;
    public static synchronized void ensure(Context c,boolean force){
        if(Store.activeProfile(c)==null)return;
        String scope=Store.activeProfile(c).key();
        if(!force&&scope.equals(Store.prefs(c).getString("healthScope",""))){
            upgradeQuality(c);
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
            .putInt("healthQualityVersion",2).putString("healthPriority","[]").putInt("healthSingleUrl",0).putString("healthSinglePartial","[]").putBoolean("healthDone",false).putLong("healthRevision",System.currentTimeMillis()).apply();
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
    static boolean valid(Context c){return Store.activeProfile(c)!=null&&Store.activeProfile(c).key().equals(Store.prefs(c).getString("healthScope",""));}
    public static synchronized boolean hidden(Context c,Playlist.Channel channel){
        JSONObject r=valid(c)?results(c).optJSONObject(Playlist.key(channel.name)):null;return r!=null&&r.optBoolean("hidden");
    }
    public static synchronized int[] order(Context c,Playlist.Channel channel){
        JSONObject r=valid(c)?results(c).optJSONObject(Playlist.key(channel.name)):null;
        return SourceRanking.order(channel.urls,r==null?null:r.optJSONArray("good"),r==null?null:r.optJSONObject("quality"));
    }
    public static int first(Context c,Playlist.Channel channel){int[] order=order(c,channel);return order.length==0?0:order[0];}
    /** Foreground first-frame evidence is as authoritative as a successful background probe. */
    public static synchronized void played(Context c,String epoch,Playlist.Channel channel,String address,int width,int height){
        if(!valid(c)||!epoch.equals(Store.prefs(c).getString("healthEpoch",""))||width<=0||height<=0)return;
        try{JSONObject all=results(c),r=all.optJSONObject(Playlist.key(channel.name));if(r==null)r=new JSONObject();
            JSONArray good=r.optJSONArray("good");if(good==null)good=new JSONArray();boolean found=false;
            for(int i=0;i<good.length();i++)if(address.equals(good.optString(i)))found=true;
            if(!found)good.put(address);JSONObject quality=r.optJSONObject("quality");if(quality==null)quality=new JSONObject();
            JSONObject old=quality.optJSONObject(address);if(found&&!r.optBoolean("hidden")&&old!=null&&old.optInt("width")==width&&old.optInt("height")==height)return;
            quality.put(address,SourceRanking.sample(1,width,height));r.put("good",good).put("quality",quality).put("hidden",false);
            all.put(Playlist.key(channel.name),r);Store.prefs(c).edit().putString("healthResults",all.toString()).putLong("healthRevision",System.currentTimeMillis()).apply();
        }catch(Exception e){android.util.Log.w("MintTV","Playback quality deferred");}
    }
    /** Retry only unresolved sources while the UI is stopped, at most once per network per day. */
    public static synchronized void queueUncertain(Context c){
        if(!valid(c)||ProbeBudget.foreground)return;long now=System.currentTimeMillis();String scope=Store.activeProfile(c).key();
        if(scope.equals(Store.prefs(c).getString("backfillScope",""))&&now-Store.prefs(c).getLong("backfillTime",0)<86400000L)return;
        try{JSONArray queue=new JSONArray(Store.prefs(c).getString("healthPriority","[]"));Set<String> names=new HashSet<>();
            for(int i=0;i<queue.length();i++)names.add(queue.getJSONObject(i).optString("name"));boolean added=false;
            for(Playlist.Channel channel:Store.channels(c)){JSONObject r=results(c).optJSONObject(Playlist.key(channel.name));
                if(r==null||!r.optBoolean("uncertain")||r.optBoolean("hidden")||names.contains(channel.name))continue;
                JSONArray good=r.optJSONArray("good"),urls=new JSONArray();
                for(String address:channel.urls){boolean known=false;if(good!=null)for(int i=0;i<good.length();i++)if(address.equals(good.optString(i)))known=true;if(!known)urls.put(address);}
                if(urls.length()>0){queue.put(new JSONObject().put("name",channel.name).put("urls",urls).put("qualityOnly",true).put("merge",true));added=true;}
            }
            if(added){Store.prefs(c).edit().putString("healthPriority",queue.toString()).putString("backfillScope",scope).putLong("backfillTime",now).apply();schedule(c,false);}
        }catch(Exception e){android.util.Log.w("MintTV","Unresolved quality retry deferred");}
    }
    public static synchronized String qualityLabel(Context c,Playlist.Channel channel,int index){
        JSONObject r=valid(c)?results(c).optJSONObject(Playlist.key(channel.name)):null;JSONObject q=r==null?null:r.optJSONObject("quality");JSONObject v=q==null?null:q.optJSONObject(channel.urls.get(index));
        return SourceRanking.pixels(v)>0?v.optInt("width")+" × "+v.optInt("height"):"分辨率待检测";
    }
    private static void upgradeQuality(Context c){
        if(Store.prefs(c).getInt("healthQualityVersion",0)>=2)return;
        try{JSONArray queue=new JSONArray(Store.prefs(c).getString("healthPriority","[]"));Set<String> queued=new HashSet<>();for(int i=0;i<queue.length();i++)queued.add(queue.getJSONObject(i).optString("name"));
            for(Playlist.Channel channel:Store.channels(c)){JSONObject r=results(c).optJSONObject(Playlist.key(channel.name));if(r==null||r.optBoolean("hidden")||queued.contains(channel.name))continue;JSONArray good=r.optJSONArray("good"),urls=new JSONArray();if(good!=null)for(String url:channel.urls)for(int i=0;i<good.length();i++)if(url.equals(good.optString(i))){urls.put(url);break;}
                if(urls.length()>0)queue.put(new JSONObject().put("name",channel.name).put("urls",urls).put("qualityOnly",true));}
            Store.prefs(c).edit().putString("healthPriority",queue.toString()).putInt("healthQualityVersion",2).apply();
        }catch(Exception e){android.util.Log.w("MintTV","Quality upgrade postponed",e);}
    }
    static synchronized boolean record(Context c,String epoch,JSONObject task,JSONArray states,int nextChannel){
        if(!epoch.equals(Store.prefs(c).getString("healthEpoch","")))return false;
        JSONObject all=results(c),r=new JSONObject();JSONArray good=new JSONArray();JSONObject quality=new JSONObject();boolean uncertain=false;
        JSONArray urls=task.optJSONArray("urls");for(int i=0;i<states.length();i++){
            if(SourceRanking.verdict(states.opt(i))==1){good.put(urls.optString(i));JSONObject sample=states.optJSONObject(i);if(sample!=null)try{quality.put(urls.optString(i),sample);}catch(Exception ignored){}}if(SourceRanking.verdict(states.opt(i))==0)uncertain=true;
        }
        int[] verdicts=new int[states.length()];for(int i=0;i<verdicts.length;i++)verdicts[i]=SourceRanking.verdict(states.opt(i));
        JSONObject previous=all.optJSONObject(Playlist.key(task.optString("name")));
        if(previous!=null){JSONArray before=previous.optJSONArray("good");JSONObject oldQuality=previous.optJSONObject("quality");for(int i=0;i<states.length();i++){String address=urls.optString(i);boolean wasGood=false;if(before!=null)for(int k=0;k<before.length();k++)if(address.equals(before.optString(k)))wasGood=true;
            if(SourceRanking.verdict(states.opt(i))==0&&wasGood)good.put(address);
            if(SourceRanking.verdict(states.opt(i))>=0&&oldQuality!=null&&SourceRanking.pixels(quality.optJSONObject(address))==0&&oldQuality.optJSONObject(address)!=null)try{quality.put(address,oldQuality.getJSONObject(address));}catch(Exception ignored){}
        }}
        if(task.optBoolean("merge"))SourceRanking.mergeUntested(good,quality,previous,urls);
        try{r.put("hidden",task.optBoolean("qualityOnly")?previous!=null&&previous.optBoolean("hidden"):ProbeVerdict.hidden(previous!=null&&previous.optBoolean("hidden"),verdicts));if(uncertain&&good.length()==0&&previous!=null)good=previous.optJSONArray("good")==null?good:previous.optJSONArray("good");if(uncertain&&quality.length()==0&&previous!=null&&previous.optJSONObject("quality")!=null)quality=previous.optJSONObject("quality");r.put("good",good);r.put("quality",quality);r.put("uncertain",uncertain);r.put("checkedAt",System.currentTimeMillis());
            all.put(Playlist.key(task.optString("name")),r);}catch(Exception ignored){}
        android.util.Log.i("MintTV","PROBE_CHANNEL_COMPLETE "+nextChannel+" "+task.optString("name")+" good="+good.length());
        SharedPreferences.Editor edit=Store.prefs(c).edit().putString("healthResults",all.toString()).putLong("healthRevision",System.currentTimeMillis());
        if(nextChannel<0){try{JSONArray queue=new JSONArray(Store.prefs(c).getString("healthPriority","[]"));queue.remove(0);edit.putString("healthPriority",queue.toString());}catch(Exception ignored){}edit.putInt("healthSingleUrl",0).putString("healthSinglePartial","[]");}
        else edit.putInt("healthChannel",nextChannel).putInt("healthUrl",0).putString("healthPartial","[]");edit.apply();return true;
    }
    public static synchronized void recheck(Context c,Playlist.Channel channel){
        ensure(c,false);try{JSONArray old=new JSONArray(Store.prefs(c).getString("healthPriority","[]")),queue=new JSONArray();queue.put(new JSONObject().put("name",channel.name).put("urls",new JSONArray(channel.urls)));for(int i=0;i<old.length();i++)if(!old.getJSONObject(i).optString("name").equals(channel.name))queue.put(old.getJSONObject(i));
            Store.prefs(c).edit().putString("healthPriority",queue.toString()).putInt("healthSingleUrl",0).putString("healthSinglePartial","[]").apply();schedule(c,true);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    public static synchronized void queueImported(Context c,List<Playlist.Channel> imported){
        ensure(c,false);if(!valid(c))return;try{Set<String> names=new HashSet<>();for(Playlist.Channel channel:imported)names.add(Playlist.key(channel.name));JSONArray queue=new JSONArray(Store.prefs(c).getString("healthPriority","[]"));boolean changed=false;
            for(Playlist.Channel channel:Store.channels(c)){if(!names.contains(Playlist.key(channel.name)))continue;JSONArray urls=new JSONArray(channel.urls);boolean exists=false;for(int i=0;i<queue.length();i++){JSONObject task=queue.getJSONObject(i);if(task.optString("name").equals(channel.name)&&!task.optBoolean("qualityOnly")&&urls.toString().equals(task.optJSONArray("urls").toString()))exists=true;}if(!exists){queue.put(new JSONObject().put("name",channel.name).put("urls",urls));changed=true;}}
            if(changed){Store.prefs(c).edit().putString("healthPriority",queue.toString()).apply();schedule(c,true);}
        }catch(Exception e){android.util.Log.w("MintTV","Imported scan postponed",e);}
    }
    static int priorityCount(Context c){try{return new JSONArray(Store.prefs(c).getString("healthPriority","[]")).length();}catch(Exception e){return 0;}}
    public static synchronized String status(Context c){
        int total=0;try{total=new JSONArray(Store.prefs(c).getString("healthTasks","[]")).length();}catch(Exception ignored){}
        int hidden=0,uncertain=0;JSONObject all=results(c);for(Iterator<String> it=all.keys();it.hasNext();){JSONObject v=all.optJSONObject(it.next());if(v!=null&&v.optBoolean("hidden"))hidden++;if(v!=null&&v.optBoolean("uncertain"))uncertain++;}
        return "已隐藏 "+hidden+" 个 · "+(Store.prefs(c).getBoolean("healthDone",false)?"检测完成":"后台检测中")+" · "+Store.prefs(c).getInt("healthChannel",0)+" / "+total+" 个频道 · 待确认 "+uncertain+" 个 · 高清优选队列 "+priorityCount(c);
    }
}

