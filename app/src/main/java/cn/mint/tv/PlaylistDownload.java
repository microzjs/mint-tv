package cn.mint.tv;

import java.io.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

/** Only the public built-in playlist uses a mirror; private subscriptions never do. */
public final class PlaylistDownload {
    static final String PREFIX="https://raw.githubusercontent.com/xisohi/CHINA-IPTV/main/Unicast/";
    interface Fetcher { String fetch(String url,long timeoutMs)throws Exception; }
    interface Clock { long millis(); }
    public static final class Result {
        public final String text,origin;
        Result(String text,String origin){this.text=text;this.origin=origin;}
    }
    static String[] candidates(String url){
        if(!url.startsWith(PREFIX))throw new IllegalArgumentException("仅支持内置公开列表");
        return new String[]{url,"https://cdn.jsdelivr.net/gh/xisohi/CHINA-IPTV@main/Unicast/"+url.substring(PREFIX.length())};
    }
    static Result load(String url,Fetcher fetcher,Clock clock)throws IOException {
        long deadline=clock.millis()+8000;String[] urls=candidates(url);
        for(int i=0;i<urls.length;i++){
            long remaining=deadline-clock.millis();if(remaining<=0)break;
            try{String text=fetcher.fetch(urls[i],Math.min(i==0?3500:4500,remaining));
                if(clock.millis()<=deadline&&!Playlist.parse(text).isEmpty())return new Result(text,i==0?"上游列表":"备用下载");
            }catch(Exception ignored){}
        }
        throw new IOException("暂时无法更新频道，请检查网络或稍后重试");
    }
    public static Result load(String url)throws IOException {
        return load(url,(address,ms)->{
            OkHttpClient client=new OkHttpClient.Builder().callTimeout(ms,TimeUnit.MILLISECONDS)
                .connectTimeout(ms,TimeUnit.MILLISECONDS).readTimeout(ms,TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false).build();
            try(Response response=client.newCall(new Request.Builder().url(address).header("User-Agent","MintTV").build()).execute()){
                if(!response.isSuccessful()||response.body()==null)throw new IOException("下载未完成");
                try(InputStream in=response.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[8192];int n;
                    while((n=in.read(bytes))!=-1){if(out.size()+n>8*1024*1024)throw new IOException("列表过大");out.write(bytes,0,n);}
                    byte[] body=out.toByteArray();
                    try{return java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(body)).toString();}
                    catch(java.nio.charset.CharacterCodingException e){return new String(body,java.nio.charset.Charset.forName("GB18030"));}
                }
            }finally{client.connectionPool().evictAll();client.dispatcher().executorService().shutdown();}
        },()->System.nanoTime()/1000000L);
    }
}
