package cn.mint.tv;
import android.content.Context;
import androidx.media3.ui.*;

/** Correct source aspect metadata per channel without altering media or other apps. */
@androidx.media3.common.util.UnstableApi
final class TvPlayerView extends PlayerView {
 private float naturalRatio;private int presentation;
 TvPlayerView(Context c){super(c);}
 void presentation(int value){presentation=value;setResizeMode(value==3?AspectRatioFrameLayout.RESIZE_MODE_ZOOM:value==4?AspectRatioFrameLayout.RESIZE_MODE_FILL:AspectRatioFrameLayout.RESIZE_MODE_FIT);AspectRatioFrameLayout frame=findViewById(androidx.media3.ui.R.id.exo_content_frame);onContentAspectRatioChanged(frame,naturalRatio);}
 @Override protected void onContentAspectRatioChanged(AspectRatioFrameLayout frame,float ratio){naturalRatio=ratio;super.onContentAspectRatioChanged(frame,presentation==1?16f/9:presentation==2?4f/3:ratio);}
}
