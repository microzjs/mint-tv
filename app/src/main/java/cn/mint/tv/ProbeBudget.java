package cn.mint.tv;
/** Volatile hints only; elapsed time must never be persisted across boots. */
public final class ProbeBudget {
 public static volatile long yieldUntil,revision;
 public static synchronized void pause(long ms){revision++;yieldUntil=Math.max(yieldUntil,android.os.SystemClock.elapsedRealtime()+ms);}
}
