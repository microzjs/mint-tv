import cn.mint.tv.Playlist;
import java.nio.file.*;
public class PlaylistTest {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  var a=Playlist.parse("央视,#genre#\nCCTV1,http://a/one\nCCTV1,http://b/two\nCCTV1,http://a/one\n坏频道,\n");
  check(a.size()==1,"empty channel excluded");check(a.get(0).urls.size()==2,"duplicate sources removed");check(a.get(0).urls.get(0).equals("http://a/one"),"source order preserved");
  var b=Playlist.parse("\uFEFF#EXTM3U\n#EXTINF:-1 group-title=\"福建\",东南卫视\nhttps://a/live.m3u8?token=a$b\n#EXTINF:-1 group-title=\"央视\",CCTV1\nhttp://c/three\n");
  check(b.get(0).group.equals("福建"),"M3U group");check(b.get(0).urls.get(0).endsWith("a$b"),"query preserved");
  var c=Playlist.merge(a,b);check(c.size()==2,"merge by name");check(c.get(0).urls.size()==3,"import appended as fallback");
  check(Playlist.parse("ABC,http://a/a$主线#http://b/b$备用").get(0).urls.size()==2,"TXT multiple source syntax");
  var real=Playlist.parse(Files.readString(Path.of(args[0])));check(real.size()>50,"real Fujian list");
  System.out.println("PASS: parser cases; Fujian channels="+real.size()+", sources="+real.stream().mapToInt(x->x.urls.size()).sum());
 }
}
