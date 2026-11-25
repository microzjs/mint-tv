package cn.mint.tv;
/** Volatile hints only; elapsed time must never be persisted across boots. */
public final class ProbeBudget {
 public static volatile long yieldUntil,revision;
 public static volatile boolean foreground;
 static Runnable cancelHardware;
 // Called on main thread before foreground decoder allocation.
 public static void foreground(boolean value){foreground=value;if(value&&cancelHardware!=null){Runnable cancel=cancelHardware;cancelHardware=null;cancel.run();}}
 public static synchronized void pause(long ms){revision++;yieldUntil=Math.max(yieldUntil,android.os.SystemClock.elapsedRealtime()+ms);}
}
