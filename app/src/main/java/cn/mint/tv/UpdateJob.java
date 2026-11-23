package cn.mint.tv;
import android.app.job.*;
import java.util.concurrent.*;
public class UpdateJob extends JobService {
 private final ExecutorService executor=Store.IO;private Future<?> task;
 @Override public boolean onStartJob(JobParameters p){task=executor.submit(()->{try{LocalState.open(this);Store.update(this);Health.ensure(this,false);jobFinished(p,false);}catch(Exception e){android.util.Log.e("MintTV","后台更新失败，保留数据",e);jobFinished(p,true);}});return true;}
 @Override public boolean onStopJob(JobParameters p){if(task!=null)task.cancel(true);return true;}
 @Override public void onDestroy(){super.onDestroy();}
}

