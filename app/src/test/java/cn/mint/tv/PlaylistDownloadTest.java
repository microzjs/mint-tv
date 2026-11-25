package cn.mint.tv;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.*;
public class PlaylistDownloadTest {
    final String url=PlaylistDownload.PREFIX+"fujian/mobile.txt";
    final String valid="CCTV1,http://example.test/live";
    @Test public void validPrimaryDoesNotContactMirror()throws Exception{
        List<String> called=new ArrayList<>();PlaylistDownload.Result result=PlaylistDownload.load(url,(u,t)->{called.add(u);return valid;},()->0);
        assertEquals(1,called.size());assertEquals(valid,result.text);assertEquals("上游列表",result.origin);
    }
    @Test public void primaryFailureUsesSameRegionalFileOnMirror()throws Exception{
        List<String> called=new ArrayList<>();PlaylistDownload.Result result=PlaylistDownload.load(url,(u,t)->{called.add(u);if(called.size()==1)throw new IOException();return valid;},()->0);
        assertTrue(called.get(1).endsWith("@main/Unicast/fujian/mobile.txt"));assertEquals("备用下载",result.origin);
    }
    @Test public void htmlErrorPageFallsThroughToMirror()throws Exception{
        int[] count={0};assertEquals(valid,PlaylistDownload.load(url,(u,t)->++count[0]==1?"<html>unavailable</html>":valid,()->0).text);assertEquals(2,count[0]);
    }
    @Test public void remainingBudgetShrinksSecondAttempt()throws Exception{
        long[] now={0};List<Long> budgets=new ArrayList<>();PlaylistDownload.load(url,(u,t)->{budgets.add(t);if(budgets.size()==1){now[0]=6000;throw new IOException();}return valid;},()->now[0]);
        assertEquals(Arrays.asList(3500L,2000L),budgets);
    }
    @Test public void expiredBudgetDoesNotStartAnotherRequest(){
        long[] now={0};int[] count={0};try{PlaylistDownload.load(url,(u,t)->{count[0]++;now[0]=8001;throw new IOException();},()->now[0]);fail();}catch(IOException expected){}assertEquals(1,count[0]);
    }
    @Test public void bothInvalidNeverReturnEmptyReplacement(){try{PlaylistDownload.load(url,(u,t)->"",()->0);fail();}catch(IOException expected){assertFalse(expected.getMessage().contains("http"));}}
    @Test public void privateSubscriptionNeverGoesToPublicMirror(){try{PlaylistDownload.candidates("https://private.test/list?token=secret");fail();}catch(IllegalArgumentException expected){}}
    @Test public void responseAfterDeadlineIsRejected(){long[] now={0};try{PlaylistDownload.load(url,(u,t)->{now[0]=9000;return valid;},()->now[0]);fail();}catch(IOException expected){}}
}
