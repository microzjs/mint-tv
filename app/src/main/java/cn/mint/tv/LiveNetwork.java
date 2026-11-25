package cn.mint.tv;
import android.content.Context;import android.net.*;import java.net.*;import java.util.*;import okhttp3.*;import androidx.media3.datasource.*;import androidx.media3.datasource.okhttp.OkHttpDataSource;
/** Direct sockets for live media only. Transparent router policies remain outside the app. */
@androidx.media3.common.util.UnstableApi
final class LiveNetwork {
 static Network bound;static OkHttpClient client;
 static synchronized OkHttpClient client(Context c){ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);Network direct=null;for(Network n:cm.getAllNetworks()){NetworkCapabilities caps=cm.getNetworkCapabilities(n);if(caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)){direct=n;if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))break;}}
 if(client==null||!Objects.equals(bound,direct)){OkHttpClient.Builder b=new OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).connectTimeout(7,java.util.concurrent.TimeUnit.SECONDS).readTimeout(7,java.util.concurrent.TimeUnit.SECONDS).followSslRedirects(true);if(direct!=null){final Network network=direct;b.socketFactory(network.getSocketFactory()).dns(host->Arrays.asList(network.getAllByName(host)));}client=b.build();bound=direct;}return client;}
 static HttpDataSource.Factory http(Context c){Context app=c.getApplicationContext();return new HttpDataSource.Factory(){Map<String,String> headers=new HashMap<>();public HttpDataSource.Factory setDefaultRequestProperties(Map<String,String> values){headers=new HashMap<>(values);return this;}public HttpDataSource createDataSource(){return new OkHttpDataSource.Factory(client(app)).setUserAgent("MintTV/1.5").setDefaultRequestProperties(headers).createDataSource();}};}
}
