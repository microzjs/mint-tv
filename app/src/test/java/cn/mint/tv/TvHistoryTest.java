package cn.mint.tv;
import org.junit.*;import static org.junit.Assert.*;import java.util.*;import org.json.*;
public class TvHistoryTest {
 @Test public void numbersSurviveReorderingAndInsertion()throws Exception{List<Playlist.Channel> first=Playlist.parse("A,http://a/live\nB,http://b/live");JSONObject old=TvHistory.numbers("{}",first);JSONObject newer=TvHistory.numbers(old.toString(),Playlist.parse("B,http://b/live\nC,http://c/live\nA,http://a/live"));assertEquals(old.getInt(Playlist.key("A")),newer.getInt(Playlist.key("A")));assertEquals(old.getInt(Playlist.key("B")),newer.getInt(Playlist.key("B")));assertNotEquals(newer.getInt(Playlist.key("A")),newer.getInt(Playlist.key("C")));}
 @Test public void removedChannelNumberIsReservedForReturn()throws Exception{JSONObject old=TvHistory.numbers("{}",Playlist.parse("A,http://a/live"));JSONObject newer=TvHistory.numbers(old.toString(),Playlist.parse("B,http://b/live"));assertEquals(old.getInt(Playlist.key("A")),newer.getInt(Playlist.key("A")));assertNotEquals(newer.getInt(Playlist.key("A")),newer.getInt(Playlist.key("B")));}
 @Test public void recentIsUniqueNewestFirst(){String saved="[]";for(int i=0;i<30;i++)saved=TvHistory.recent(saved,"channel"+i).toString();saved=TvHistory.recent(saved,"channel20").toString();List<String> result=TvHistory.recent(saved);assertEquals(20,result.size());assertEquals("channel20",result.get(0));assertEquals(20,new HashSet<>(result).size());}
 @Test public void malformedHistoryDoesNotPreventPlayback(){assertEquals("a",TvHistory.recent("invalid","a").optString(0));}
}
