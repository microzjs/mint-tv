package cn.mint.tv;
import java.io.*;
import java.text.*;
import java.util.*;
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import org.json.*;

/** Streaming XMLTV, only two programmes per relevant channel; no DOM or schedule wall. */
public final class EpgParser {
 public static final class Entry {
  public final long start,end;public final String title;
  Entry(long s,long e,String t){start=s;end=e;title=t;}
  JSONObject json()throws Exception{return new JSONObject().put("start",start).put("end",end).put("title",title);}
 }
 public static JSONObject parse(InputStream stream,Map<String,String> ids,Set<String> names,long now)throws Exception{
  Map<String,String> aliases=new HashMap<>(ids);Map<String,Entry[]> selected=new HashMap<>();
  SAXParserFactory factory=SAXParserFactory.newInstance();factory.setNamespaceAware(false);
  try{factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);}catch(Exception ignored){}
  try{factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);}catch(Exception ignored){}
  javax.xml.parsers.SAXParser parser=factory.newSAXParser();org.xml.sax.XMLReader reader=parser.getXMLReader();
  reader.setProperty("http://xml.org/sax/properties/lexical-handler",new org.xml.sax.ext.DefaultHandler2(){public void startEntity(String name)throws SAXException{if(!Arrays.asList("amp","lt","gt","quot","apos","[dtd]").contains(name))throw new SAXException("不允许自定义实体");}});
  reader.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
  reader.setContentHandler(new DefaultHandler(){String channelId,key,field="";StringBuilder text=new StringBuilder();long start,end;int elements;
   public void startElement(String uri,String local,String q,Attributes a)throws SAXException{
    if(++elements>500000)throw new SAXException("节目单项目过多");
    if(q.equals("channel")){channelId=a.getValue("id");}
    if(q.equals("programme")){key=aliases.get(a.getValue("channel"));start=time(a.getValue("start"));end=time(a.getValue("stop"));}
    if(q.equals("display-name")||q.equals("title")){field=q;text.setLength(0);}
   }
   public void characters(char[] c,int offset,int length){if(!field.isEmpty()&&text.length()<1024)text.append(c,offset,Math.min(length,1024-text.length()));}
   public void endElement(String uri,String local,String q){
    if(q.equals("display-name")&&channelId!=null){String name=Playlist.key(text.toString().trim());if(names.contains(name)){if(!aliases.containsKey(channelId))aliases.put(channelId,name);};field="";}
    if(q.equals("channel"))channelId=null;
    if(q.equals("title")){field="";if(text.toString().trim().isEmpty()||key==null||start<0||end<=start||end<=now||start>now+48*3600000L)return;
     Entry[] pair=selected.get(key);if(pair==null){pair=new Entry[2];selected.put(key,pair);}Entry item=new Entry(start,end,text.toString().trim());
     int at=start<=now?0:1;if(pair[at]==null||(at==0?start>pair[at].start:start<pair[at].start))pair[at]=item;
    }
    if(q.equals("programme"))key=null;
   }
  });
  reader.parse(new InputSource(new Limited(stream,32*1024*1024)));
  JSONObject out=new JSONObject();for(Map.Entry<String,Entry[]> e:selected.entrySet()){JSONObject pair=new JSONObject();if(e.getValue()[0]!=null)pair.put("now",e.getValue()[0].json());if(e.getValue()[1]!=null)pair.put("next",e.getValue()[1].json());out.put(e.getKey(),pair);}return out;
 }
 static long time(String value){try{if(value==null)return -1;String v=value.trim();if(v.matches("[0-9]{14}"))v+=" +0000";if(!v.matches("[0-9]{14} [+-][0-9]{4}"))return -1;
  SimpleDateFormat f=new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.ROOT);f.setLenient(false);return f.parse(v).getTime();}catch(Exception e){return -1;}}
 static final class Limited extends FilterInputStream {long left;Limited(InputStream in,long max){super(in);left=max;}
  public int read()throws IOException{int v=super.read();if(v>=0&&--left<0)throw new IOException("节目单超过 32MB");return v;}
  public int read(byte[] b,int o,int len)throws IOException{int n=super.read(b,o,len);if(n>0&&(left-=n)<0)throw new IOException("节目单超过 32MB");return n;}}
}
