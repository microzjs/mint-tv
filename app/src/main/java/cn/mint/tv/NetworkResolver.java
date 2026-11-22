package cn.mint.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

/** Lookups originate on the viewing device, never on the developer's computer. */
public final class NetworkResolver {
    public static NetworkProfile resolve() throws Exception {
        for (String endpoint : new String[]{"https://myip.ipip.net/json", "https://ipwho.is/"}) {
            try {
                NetworkProfile profile = parse(new JSONObject(Store.fetch(endpoint, 5000, 6000)));
                if (profile != null) return profile;
            } catch (Exception ignored) {
                // A failed / foreign / unrecognized lookup must not erase the last good profile.
            }
        }
        throw new IOException("未识别到支持的国内省份和运营商，保留原源；可在设置中手动选择");
    }

    static NetworkProfile parse(JSONObject json) {
        if ("ok".equals(json.optString("ret"))) {
            JSONObject data = json.optJSONObject("data");
            JSONArray location = data == null ? null : data.optJSONArray("location");
            if (location == null || location.length() < 5) return null;
            return NetworkProfile.detect(location.optString(0), location.optString(1), location.optString(4));
        }
        if (json.optBoolean("success", false)) {
            JSONObject connection = json.optJSONObject("connection");
            if (connection == null) return null;
            NetworkProfile isp = NetworkProfile.detect(json.optString("country_code"),
                json.optString("region"), connection.optString("isp"));
            return isp != null ? isp : NetworkProfile.detect(json.optString("country_code"),
                json.optString("region"), connection.optString("org"));
        }
        return null;
    }
}
