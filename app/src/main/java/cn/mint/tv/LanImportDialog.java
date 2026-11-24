package cn.mint.tv;
import android.app.*;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

@androidx.media3.common.util.UnstableApi
final class LanImportDialog implements AutoCloseable {
 final MainActivity a;LanImportServer server;AlertDialog dialog;boolean closed;
 LanImportDialog(MainActivity a){this.a=a;}
 void show(){LinearLayout box=a.row();box.setPadding(a.dp(24),a.dp(16),a.dp(24),a.dp(8));TextView details=a.text("正在建立局域网会话…",14,a.TEXT);details.setMaxLines(8);box.addView(details,new LinearLayout.LayoutParams(0,-2,1));ImageView qr=new ImageView(a);box.addView(qr,new LinearLayout.LayoutParams(a.dp(200),a.dp(200)));dialog=new AlertDialog.Builder(a).setTitle("手机扫码导入").setView(box).setNegativeButton("关闭",(d,w)->close()).create();dialog.setOnDismissListener(d->close());dialog.show();
  a.io.execute(()->{try{InetAddress address=localAddress();byte[] page;try(InputStream in=a.getAssets().open("lan-import.html");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);page=out.toByteArray();}
   if(closed)return;server=new LanImportServer(address,page,new LanImportServer.Importer(){public String link(String url)throws Exception{String text=Store.fetch(url,7000,10000);if(closed||!server.active())throw new IOException("会话已关闭");Store.add(a,"手机订阅 · "+new URL(url).getHost(),url,text);Health.queueImported(a,Playlist.parse(text));changed();return "订阅已导入，已保留每日更新";}public String file(String name,byte[] bytes)throws Exception{String text=Store.read(new ByteArrayInputStream(bytes));if(closed||!server.active())throw new IOException("会话已关闭");Store.add(a,name,"",text);Health.queueImported(a,Playlist.parse(text));changed();return "文件已导入，可在电视上选台";}});
   if(closed){server.close();return;}String url=server.url();BitMatrix matrix=new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,600,600,Collections.singletonMap(EncodeHintType.MARGIN,3));Bitmap bitmap=Bitmap.createBitmap(matrix.getWidth(),matrix.getHeight(),Bitmap.Config.RGB_565);int[] pixels=new int[matrix.getWidth()*matrix.getHeight()];for(int y=0;y<matrix.getHeight();y++)for(int x=0;x<matrix.getWidth();x++)pixels[y*matrix.getWidth()+x]=matrix.get(x,y)?0xff101c22:0xffffffff;bitmap.setPixels(pixels,0,matrix.getWidth(),0,0,matrix.getWidth(),matrix.getHeight());
   a.handler.post(()->{if(closed||a.isDestroyed())return;details.setText("手机与盒子连接同一路由器，扫码后上传文件或粘贴订阅链接。\n\n"+url+"\n\n有效期 10 分钟；关闭窗口即停止接收。无需登录或安装手机应用。");qr.setImageBitmap(bitmap);});a.handler.postDelayed(()->{if(!closed){details.setText("会话已到期，请关闭后重新打开二维码。");qr.setImageDrawable(null);if(server!=null)server.close();}},10*60000L);
  }catch(Exception e){a.handler.post(()->details.setText("无法启动："+e.getMessage()+"\n请确认盒子已连上 Wi-Fi 或网线。"));}});
 }
 void changed(){a.handler.post(()->{if(!a.isDestroyed()){a.reload();a.toast("已从手机导入直播源");}});}
 static InetAddress localAddress()throws Exception{InetAddress fallback=null;Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();while(interfaces.hasMoreElements()){NetworkInterface net=interfaces.nextElement();if(!net.isUp()||net.isLoopback()||net.getName().startsWith("tun")||net.getName().startsWith("ppp"))continue;Enumeration<InetAddress> all=net.getInetAddresses();while(all.hasMoreElements()){InetAddress ip=all.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress()){if(net.getName().startsWith("wlan")||net.getName().startsWith("eth"))return ip;fallback=ip;}}}if(fallback==null)throw new IOException("没有可用的局域网 IPv4 地址");return fallback;}
 public void close(){closed=true;if(server!=null)server.close();if(dialog!=null&&dialog.isShowing())dialog.dismiss();}
}
