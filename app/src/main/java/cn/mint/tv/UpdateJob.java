package cn.mint.tv;
import android.app.job.*;
import java.util.concurrent.*;
public class UpdateJob extends JobService {
 private final ExecutorService executor=Store.IO;private Future<?> task;
 @Override public boolean onStartJob(JobParameters p){task=executor.submit(()->{Store.update(this);jobFinished(p,false);});return true;}
 @Override public boolean onStopJob(JobParameters p){if(task!=null)task.cancel(true);return true;}
 @Override public void onDestroy(){super.onDestroy();}
}

