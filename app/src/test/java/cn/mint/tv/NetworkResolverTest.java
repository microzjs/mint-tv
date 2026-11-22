package cn.mint.tv;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkResolverTest {
    private NetworkProfile parse(String value) throws Exception {
        return NetworkResolver.parse(new JSONObject(value));
    }

    @Test public void ipipChineseResponse() throws Exception {
        NetworkProfile p=parse("{\"ret\":\"ok\",\"data\":{\"ip\":\"203.0.113.10\",\"location\":[\"中国\",\"福建\",\"福州\",\"\",\"移动\"]}}");
        assertEquals("fujian/mobile",p.key());
    }
    @Test public void sameRegionAndCarrierIgnoresAddressChange() throws Exception {
        String fixture="{\"ret\":\"ok\",\"data\":{\"ip\":\"%s\",\"location\":[\"中国\",\"福建\",\"福州\",\"\",\"移动\"]}}";
        assertTrue(parse(String.format(fixture,"203.0.113.10")).sameNetwork(parse(String.format(fixture,"203.0.113.20"))));
    }
    @Test public void ipwhoEnglishResponse() throws Exception {
        assertEquals("zhejiang/telecom",parse("{\"success\":true,\"country_code\":\"CN\",\"region\":\"Zhejiang\",\"connection\":{\"isp\":\"China Telecom\"}}").key());
    }
    @Test public void ispTakesPriorityOverOrganization() throws Exception {
        assertEquals("fujian/unicom",parse("{\"success\":true,\"country_code\":\"CN\",\"region\":\"Fujian\",\"connection\":{\"isp\":\"China Unicom\",\"org\":\"China Mobile\"}}").key());
    }
    @Test public void organizationFallback() throws Exception {
        assertEquals("fujian/mobile",parse("{\"success\":true,\"country_code\":\"CN\",\"region\":\"Fujian\",\"connection\":{\"isp\":\"Unknown\",\"org\":\"China Mobile\"}}").key());
    }
    @Test public void failureAndMalformedDataDoNotSelectDefault() throws Exception {
        assertNull(parse("{\"success\":false,\"message\":\"rate limited\"}"));
        assertNull(parse("{\"ret\":\"ok\",\"data\":{\"location\":[\"中国\",\"福建\"]}}"));
        assertNull(parse("{\"success\":true}"));
    }
    @Test public void foreignOrUnsupportedNetworkPreservesPreviousSelection() throws Exception {
        assertNull(parse("{\"success\":true,\"country_code\":\"SG\",\"region\":\"Singapore\",\"connection\":{\"isp\":\"China Mobile\"}}"));
        assertNull(parse("{\"ret\":\"ok\",\"data\":{\"location\":[\"中国\",\"福建\",\"福州\",\"\",\"Unknown ISP\"]}}"));
    }
    @Test public void dataCannotInjectARepositoryPath() throws Exception {
        assertNull(parse("{\"success\":true,\"country_code\":\"CN\",\"region\":\"../fujian\",\"connection\":{\"isp\":\"China Mobile\"}}"));
    }
}
