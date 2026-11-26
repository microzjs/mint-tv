package cn.mint.tv;
import org.junit.*;import static org.junit.Assert.*;import org.json.*;
public class ChannelAspectTest {
 @Test public void appliesOnlyReportedChannelCorrections(){JSONObject d=ChannelAspect.defaults("{}");assertEquals(1,ChannelAspect.mode(d.toString(),"宁夏卫视"));assertEquals(1,ChannelAspect.mode(d.toString(),"河北卫视"));assertEquals(0,ChannelAspect.mode(d.toString(),"CCTV1"));}
 @Test public void neverOverridesUserChoice()throws Exception{JSONObject saved=new JSONObject().put(Playlist.key("宁夏卫视"),0);assertEquals(0,ChannelAspect.mode(ChannelAspect.defaults(saved.toString()).toString(),"宁夏卫视"));}
 @Test public void newlyReportedStationsDefaultToWidescreen(){String d=ChannelAspect.defaults("{}").toString();for(String name:new String[]{"西藏卫视","新疆卫视","康巴卫视","兵团卫视","内蒙古卫视","内蒙古视"})assertEquals(name,1,ChannelAspect.mode(d,name));}
 @Test public void invalidModesFallBackToAutomatic(){assertEquals(0,ChannelAspect.mode("invalid","A"));assertEquals(0,ChannelAspect.mode("{\"A\":99}","A"));}
 @Test public void matchingPreservesDistinctChannels(){assertEquals("CCTV1",ChannelNames.key("CCTV - 1 HD"));assertNotEquals(ChannelNames.key("CCTV5+"),ChannelNames.key("CCTV5"));assertNotEquals(ChannelNames.key("CCTV4K"),ChannelNames.key("CCTV4"));}
}
