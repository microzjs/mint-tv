package cn.mint.tv;
import android.content.Context;import android.widget.FrameLayout;
final class PreviewFrame extends FrameLayout {PreviewFrame(Context c){super(c);}protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w),height=MeasureSpec.getSize(h);int chosen=Math.min(height,Math.round(width*9f/16));super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(width,Math.round(chosen*16f/9)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(chosen,MeasureSpec.EXACTLY));}}
