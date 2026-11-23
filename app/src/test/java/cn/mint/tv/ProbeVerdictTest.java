package cn.mint.tv;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProbeVerdictTest {
 @Test public void allFailuresHide(){assertTrue(ProbeVerdict.hidden(false,new int[]{-1,-1}));}
 @Test public void anySuccessShows(){assertFalse(ProbeVerdict.hidden(true,new int[]{-1,1,-1}));}
 @Test public void unknownDoesNotHideNewChannel(){assertFalse(ProbeVerdict.hidden(false,new int[]{-1,0}));}
 @Test public void unknownDoesNotRestoreHiddenChannel(){assertTrue(ProbeVerdict.hidden(true,new int[]{0,-1}));}
 @Test public void emptyIsNotProofOfFailure(){assertFalse(ProbeVerdict.hidden(false,new int[]{}));}
 @Test public void emptyDoesNotRestoreHidden(){assertTrue(ProbeVerdict.hidden(true,new int[]{}));}
}
