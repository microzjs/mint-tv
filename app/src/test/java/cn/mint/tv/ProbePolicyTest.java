package cn.mint.tv;
import org.junit.Test;import static org.junit.Assert.*;
public class ProbePolicyTest {
    @Test public void decoderFailureCanRetryOnlyWhenForegroundIdle(){assertTrue(ProbePolicy.retryHardware(4001,false,false));assertTrue(ProbePolicy.retryHardware(4003,false,false));assertFalse(ProbePolicy.retryHardware(4001,true,false));}
    @Test public void hardwareFailureDoesNotLoop(){assertFalse(ProbePolicy.retryHardware(4001,false,true));}
    @Test public void softwareFormatUnsupportedCanRetryHardware(){assertTrue(ProbePolicy.retryHardware(4005,false,false));assertTrue(ProbePolicy.retryHardware(4004,false,false));assertFalse(ProbePolicy.retryHardware(4005,true,false));}
    @Test public void reclaimingResourcesMustNotStartMoreDecoders(){assertFalse(ProbePolicy.retryHardware(4006,false,false));}
    @Test public void foregroundAllocationSynchronouslyReleasesProbe(){int[] canceled={0};ProbeBudget.cancelHardware=()->canceled[0]++;try{ProbeBudget.foreground(true);assertEquals(1,canceled[0]);assertNull(ProbeBudget.cancelHardware);ProbeBudget.foreground(true);assertEquals(1,canceled[0]);}finally{ProbeBudget.foreground(false);ProbeBudget.cancelHardware=null;}}
    @Test public void networkFailureDoesNotAllocateHardware(){assertFalse(ProbePolicy.retryHardware(2001,false,false));assertEquals(-1,ProbePolicy.verdict(2001));}
    @Test public void decoderShortageNeverProvesChannelDead(){assertEquals(0,ProbePolicy.verdict(4001));assertEquals(0,ProbePolicy.verdict(4002));assertEquals(0,ProbePolicy.verdict(5001));assertFalse(ProbeVerdict.hidden(false,new int[]{0,-1}));}
}
