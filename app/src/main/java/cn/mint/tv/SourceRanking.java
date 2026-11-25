package cn.mint.tv;
import java.util.*;
import org.json.*;

/** Prefer decoded, usable sources by pixel count; ties keep provider order. */
public final class SourceRanking {
    public static int verdict(Object state) { return state instanceof JSONObject ? ((JSONObject)state).optInt("state",0) : state instanceof Number ? ((Number)state).intValue() : 0; }
    public static JSONObject sample(int state,int width,int height) { JSONObject out=new JSONObject();try { out.put("state",state).put("width",Math.max(0,width)).put("height",Math.max(0,height)); } catch(JSONException ignored) {} return out; }
    /** A partial quality scan must not discard successful sources it did not test. */
    public static void mergeUntested(JSONArray good,JSONObject quality,JSONObject previous,JSONArray tested){
        if(previous==null)return;JSONArray before=previous.optJSONArray("good");JSONObject old=previous.optJSONObject("quality");
        if(before==null)return;
        for(int i=0;i<before.length();i++){String address=before.optString(i);boolean included=false;
            for(int j=0;j<tested.length();j++)if(address.equals(tested.optString(j)))included=true;
            if(!included){good.put(address);if(old!=null&&old.has(address))try{quality.put(address,old.getJSONObject(address));}catch(JSONException ignored){}}
        }
    }
    public static int[] order(List<String> urls,JSONArray good,JSONObject quality) {
        Set<String> usable=new HashSet<>();if(good!=null)for(int i=0;i<good.length();i++)usable.add(good.optString(i));
        List<Integer> indices=new ArrayList<>();for(int i=0;i<urls.size();i++)indices.add(i);
        Collections.sort(indices,(a,b)->{boolean x=usable.contains(urls.get(a)),y=usable.contains(urls.get(b));if(x!=y)return x?-1:1;if(x){long px=pixels(quality==null?null:quality.optJSONObject(urls.get(a))),py=pixels(quality==null?null:quality.optJSONObject(urls.get(b)));if(px!=py)return Long.compare(py,px);}return Integer.compare(a,b);});
        int[] out=new int[indices.size()];for(int i=0;i<out.length;i++)out[i]=indices.get(i);return out;
    }
    public static long pixels(JSONObject quality) {if(quality==null)return 0;int w=quality.optInt("width"),h=quality.optInt("height");return w>0&&h>0&&(w<=16384&&h<=16384)?(long)w*h:0;}
    public static int[] withFirst(int[] order,int first) { int[] out=new int[order.length];if(out.length==0)return out;out[0]=first;int n=1;for(int x:order)if(x!=first&&n<out.length)out[n++]=x;return out; }
}
