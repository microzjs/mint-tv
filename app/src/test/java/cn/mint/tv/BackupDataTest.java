package cn.mint.tv;

import org.junit.*;import static org.junit.Assert.*;import java.util.*;import org.json.*;

public class BackupDataTest {

 String file(JSONObject data)throws Exception{return new JSONObject().put("format","mint-tv-backup").put("version",1).put("data",data).toString();}

 @Test public void restoresImportsAndFavoritesWithoutDerivedHealth()throws Exception{Map<String,Object> state=new HashMap<>();state.put("base","央视,http://example.com/live");state.put("favorites",new HashSet<>(Arrays.asList("央视")));state.put("healthResults","device specific");state.put("epgData","derived");state.put("channelNumbers","{\"央视\":12}");Map<String,Object> out=BackupData.validate(BackupData.export(state).toString());assertEquals(state.get("favorites"),out.get("favorites"));assertEquals(state.get("base"),out.get("base"));assertFalse(out.containsKey("healthResults"));assertFalse(out.containsKey("epgData"));}

 @Test(expected=IllegalArgumentException.class) public void rejectsUnknownField()throws Exception{BackupData.validate(file(new JSONObject().put("healthDone",true)));}

 @Test(expected=IllegalArgumentException.class) public void rejectsDuplicateChannelNumbers()throws Exception{BackupData.validate(file(new JSONObject().put("channelNumbers","{\"a\":1,\"b\":1}")));}

 @Test(expected=IllegalArgumentException.class) public void rejectsInvalidProvince()throws Exception{BackupData.validate(file(new JSONObject().put("province","../fujian").put("carrier","mobile")));}

 @Test(expected=IllegalArgumentException.class) public void rejectsNonHttpEpg()throws Exception{BackupData.validate(file(new JSONObject().put("epgUrl","file:///secret")));}

 @Test(expected=IllegalArgumentException.class) public void rejectsEmptySubscription()throws Exception{BackupData.validate(file(new JSONObject().put("imports","[{\"text\":\"nothing\"}]")));}

 @Test(expected=IllegalArgumentException.class) public void rejectsWrongFieldType()throws Exception{BackupData.validate(file(new JSONObject().put("base",12)));}

 @Test public void acceptsEmptyNewInstallationBackup()throws Exception{assertTrue(BackupData.validate(file(new JSONObject())).isEmpty());}

 @Test public void sameDeviceSameListsKeepLocalScanCache()throws Exception{Map<String,Object> state=new HashMap<>();state.put("base","A,http://a/live");state.put("province","fujian");state.put("carrier","mobile");state.put("healthDone",true);state.put("healthResults","local-verdict");state.put("epgData","stale");Map<String,Object> replacement=BackupData.validate(BackupData.export(state).toString());Map<String,Object> next=BackupData.restored(state,replacement);assertEquals(true,next.get("healthDone"));assertEquals("local-verdict",next.get("healthResults"));assertFalse(next.containsKey("epgData"));}

 @Test public void changedListsInvalidateLocalScanCache(){Map<String,Object> state=new HashMap<>();state.put("base","old");state.put("healthDone",true);Map<String,Object> replacement=new HashMap<>();replacement.put("base","new");assertFalse(BackupData.restored(state,replacement).containsKey("healthDone"));}

 @Test public void anotherDeviceCannotImportScanVerdicts(){Map<String,Object> state=new HashMap<>();Map<String,Object> replacement=new HashMap<>();replacement.put("base","A,http://a/live");assertFalse(BackupData.restored(state,replacement).containsKey("healthDone"));}

}

