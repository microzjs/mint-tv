package cn.mint.tv;
import android.content.Context;import android.graphics.*;import android.view.View;
/** Fixed-size ring buffer, one sample/second, no animation thread. */
final class RateGraph extends View {
 final float[] samples=new float[60];int count,cursor;final Paint paint=new Paint(3);final Path path=new Path();
 RateGraph(Context c){super(c);setContentDescription("最近60秒下载流码率曲线");}
 void add(double value){samples[cursor]=(float)Math.max(0,value);cursor=(cursor+1)%samples.length;count=Math.min(count+1,samples.length);invalidate();}
 protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),max=1;for(float v:samples)max=Math.max(max,v);paint.setColor(0xff345047);paint.setStrokeWidth(1);for(int i=0;i<5;i++)c.drawLine(0,h*i/4,w,h*i/4,paint);path.reset();for(int i=0;i<count;i++){float x=w*(60-count+i)/59f,y=h-3-(h-18)*samples[(cursor-count+i+60)%60]/max;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}paint.setColor(0xff69edb4);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);c.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(22);c.drawText(String.format(java.util.Locale.ROOT,"%.1f Mbps",max),5,22,paint);}
}
