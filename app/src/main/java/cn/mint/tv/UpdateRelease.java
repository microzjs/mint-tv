package cn.mint.tv;
import org.json.*;
/** Strict public release selection; never forward private subscriptions to update servers. */
public final class UpdateRelease {
 public static final String ROOT="https://github.com/microzjs/mint-tv/releases/download/";
 public final String version,url,digest;public final long size;
 UpdateRelease(String v,String u,String d,long s){version=v;url=u;digest=d;size=s;}
 static int[] parts(String v){if(!v.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException();String[] a=v.replaceFirst("^v","").split("\\.");return new int[]{Integer.parseInt(a[0]),Integer.parseInt(a[1]),Integer.parseInt(a[2])};}
 public static boolean newer(String a,String b){int[] x=parts(a),y=parts(b);for(int i=0;i<3;i++)if(x[i]!=y[i])return x[i]>y[i];return false;}
 public static UpdateRelease parse(String json)throws Exception{
  JSONObject r=new JSONObject(json);if(r.optBoolean("draft")||r.optBoolean("prerelease"))throw new IllegalArgumentException();
  String tag=r.getString("tag_name");parts(tag);String version=tag.replaceFirst("^v","");JSONArray assets=r.getJSONArray("assets");
  for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);String name="mint-tv-"+version+".apk",url=a.optString("browser_download_url"),digest=a.optString("digest","");long size=a.optLong("size");
   if(name.equals(a.optString("name"))&&"uploaded".equals(a.optString("state"))&&url.equals(ROOT+tag+"/"+name)&&size>0&&size<=40*1024*1024){
    if(!digest.isEmpty()&&!digest.matches("sha256:[a-fA-F0-9]{64}"))throw new IllegalArgumentException();return new UpdateRelease(version,url,digest,size);
   }
  }throw new IllegalArgumentException();
 }
}
