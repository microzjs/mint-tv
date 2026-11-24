package cn.mint.tv;
import java.util.Locale;
/** Conservative metadata matching; do not collapse CCTV5+ into CCTV5 or remove 4K. */
public final class ChannelNames {
 public static String key(String name){String out=name.toUpperCase(Locale.ROOT).replaceAll("[\\s_·•-]","");out=out.replaceAll("[（(](?:HD|高清|标清)[）)]$","").replaceAll("(?:HD|高清|标清)$","");return out;}
}
