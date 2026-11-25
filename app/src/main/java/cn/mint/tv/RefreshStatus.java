package cn.mint.tv;
/** A failed optional subscription must not conceal a successful regional update. */
public final class RefreshStatus {
 public static String summary(boolean attempted,boolean success,String profile,int imported,int failed){
  String message=attempted?(success?"地区频道源已更新 · "+profile:"地区频道源更新失败，已保留旧列表；请检查网络或选择地区"):"地区频道源未变化 · 沿用已有列表";
  if(imported+failed>0)message+="\n导入订阅："+imported+" 个已更新"+(failed>0?"，"+failed+" 个失败；旧内容已保留，可在导入管理查看":"");
  return message;
 }
}
