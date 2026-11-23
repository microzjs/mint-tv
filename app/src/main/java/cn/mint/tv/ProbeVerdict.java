package cn.mint.tv;
/** Unknown / interrupted probes are never proof of failure. */
public final class ProbeVerdict {
    public static boolean hidden(boolean previouslyHidden,int[] states){
        boolean complete=states.length>0;
        for(int state:states){if(state==1)return false;if(state==0)complete=false;}
        return complete||previouslyHidden;
    }
}
