package cn.mint.tv;
/** Decoder shortages and probe timeouts are inconclusive, never evidence of a dead channel. */
public final class ProbePolicy {
    public static boolean retryHardware(int errorCode,boolean foreground,boolean alreadyHardware){
        return !foreground&&!alreadyHardware&&errorCode>=4001&&errorCode<=4005;
    }
    public static int verdict(int errorCode){return errorCode>=4000&&errorCode<6000?0:-1;}
}
