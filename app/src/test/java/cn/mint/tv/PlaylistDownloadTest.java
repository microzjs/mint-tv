package cn.mint.tv;
import org.junit.Test;import static org.junit.Assert.*;import java.io.IOException;
public class PlaylistDownloadTest {
 final String url=PlaylistDownload.PREFIX+"fujian/mobile.txt",valid="A,http://example.test/live";
 @Test public void onlyGitHubOriginalIsUsed(){assertArrayEquals(new String[]{url},PlaylistDownload.candidates(url));}
 @Test public void validListLoadsWithinBudget()throws Exception{assertEquals(valid,PlaylistDownload.load(url,(u,t)->{assertEquals(8000,t);return valid;},()->0).text);}
 @Test public void failedOriginalDoesNotContactAMirror(){int[] requests={0};try{PlaylistDownload.load(url,(u,t)->{requests[0]++;throw new IOException();},()->0);fail();}catch(IOException expected){}assertEquals(1,requests[0]);}
 @Test public void invalidListNeverReplacesCache(){try{PlaylistDownload.load(url,(u,t)->"<html>error</html>",()->0);fail();}catch(IOException expected){}}
 @Test public void lateResponseIsRejected(){long[] now={0};try{PlaylistDownload.load(url,(u,t)->{now[0]=9000;return valid;},()->now[0]);fail();}catch(IOException expected){}}
 @Test(expected=IllegalArgumentException.class)public void privateSubscriptionNeverReachesPublicFetch(){PlaylistDownload.candidates("https://private.test/list?token=secret");}
}