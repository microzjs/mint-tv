package cn.mint.tv;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;
public class UpdateReleaseTest {
 String release(String url,String name,boolean preview,long size)throws Exception{return new JSONObject().put("tag_name","v1.6.1").put("draft",false).put("prerelease",preview).put("assets",new JSONArray().put(new JSONObject().put("name",name).put("browser_download_url",url).put("size",size).put("state","uploaded"))).toString();}
 String url=UpdateRelease.ROOT+"v1.6.1/mint-tv-1.6.1.apk";
 @Test public void selectsOfficialRelease()throws Exception{assertEquals("1.6.1",UpdateRelease.parse(release(url,"mint-tv-1.6.1.apk",false,1024)).version);}
 @Test public void versionsCompareNumerically(){assertTrue(UpdateRelease.newer("1.10.0","1.9.9"));assertFalse(UpdateRelease.newer("1.6.1","1.6.1"));assertFalse(UpdateRelease.newer("1.5.2","1.6.0"));}
 @Test(expected=IllegalArgumentException.class)public void refusesPrerelease()throws Exception{UpdateRelease.parse(release(url,"mint-tv-1.6.1.apk",true,1024));}
 @Test(expected=IllegalArgumentException.class)public void refusesExternalDownloadHost()throws Exception{UpdateRelease.parse(release("https://evil.test/app.apk","mint-tv-1.6.1.apk",false,1024));}
 @Test(expected=IllegalArgumentException.class)public void refusesOversizedAsset()throws Exception{UpdateRelease.parse(release(url,"mint-tv-1.6.1.apk",false,50000000));}
 @Test(expected=IllegalArgumentException.class)public void refusesWrongAssetName()throws Exception{UpdateRelease.parse(release(url,"other.apk",false,1024));}
 @Test(expected=IllegalArgumentException.class)public void refusesInvalidDigest()throws Exception{JSONObject r=new JSONObject(release(url,"mint-tv-1.6.1.apk",false,1024));r.getJSONArray("assets").getJSONObject(0).put("digest","sha256:bad");UpdateRelease.parse(r.toString());}
}
