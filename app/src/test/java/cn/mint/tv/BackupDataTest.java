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
}
