package cn.mint.tv;
import org.junit.Test;import static org.junit.Assert.*;
public class FirstProfileTest {
 @Test public void freshInstallHasNoDefaultProvince(){assertNull(NetworkProfile.saved("",""));}
 @Test public void partialOrInvalidProfileDoesNotBecomeFujian(){assertNull(NetworkProfile.saved("fujian",""));assertNull(NetworkProfile.saved("unknown","mobile"));assertNull(NetworkProfile.saved(null,null));}
 @Test public void validSavedProfileIsRetained(){assertEquals("guangdong/telecom",NetworkProfile.saved("guangdong","telecom").key());}
}
