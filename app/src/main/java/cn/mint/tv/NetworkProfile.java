package cn.mint.tv;

import java.util.Locale;

/** Validated repository paths, independent of Android for regression tests. */
public final class NetworkProfile {
    public static final String[][] PROVINCES = {
        {"anhui", "安徽"}, {"beijing", "北京"}, {"chongqing", "重庆"},
        {"fujian", "福建"}, {"gansu", "甘肃"}, {"guangdong", "广东"},
        {"guangxi", "广西"}, {"guizhou", "贵州"}, {"hainan", "海南"},
        {"hebei", "河北"}, {"heilongjiang", "黑龙江"}, {"henan", "河南"},
        {"hubei", "湖北"}, {"hunan", "湖南"}, {"jiangsu", "江苏"},
        {"jiangxi", "江西"}, {"jilin", "吉林"}, {"liaoning", "辽宁"},
        {"neimenggu", "内蒙古"}, {"ningxia", "宁夏"}, {"qinghai", "青海"},
        {"shaanxi", "陕西"}, {"shandong", "山东"}, {"shanghai", "上海"},
        {"shanxi", "山西"}, {"sichuan", "四川"}, {"tianjin", "天津"},
        {"xinjiang", "新疆"}, {"xizang", "西藏"}, {"yunnan", "云南"},
        {"zhejiang", "浙江"}
    };
    public static final String[][] CARRIERS = {
        {"mobile", "移动"}, {"telecom", "电信"}, {"unicom", "联通"}
    };
    public final String province, carrier;

    public NetworkProfile(String province, String carrier) {
        if (!known(PROVINCES, province) || !known(CARRIERS, carrier)) {
            throw new IllegalArgumentException("不支持的省份或运营商");
        }
        this.province = province;
        this.carrier = carrier;
    }

    private static boolean known(String[][] list, String key) {
        for (String[] item : list) if (item[0].equals(key)) return true;
        return false;
    }

    public String key() { return province + "/" + carrier; }
    public boolean sameNetwork(NetworkProfile other) {
        return other != null && key().equals(other.key());
    }
    public String label() {
        String p = province, c = carrier;
        for (String[] item : PROVINCES) if (item[0].equals(province)) p = item[1];
        for (String[] item : CARRIERS) if (item[0].equals(carrier)) c = item[1];
        return p + c;
    }
    public String url() {
        return "https://raw.githubusercontent.com/xisohi/CHINA-IPTV/main/Unicast/"
            + province + "/" + carrier + ".txt";
    }

    public static NetworkProfile detect(String country, String region, String isp) {
        if (!("CN".equalsIgnoreCase(country) || "中国".equals(country)
                || "China".equalsIgnoreCase(country))) return null;
        String p = normalizeProvince(region);
        String c = normalizeCarrier(isp);
        return p == null || c == null ? null : new NetworkProfile(p, c);
    }

    static String normalizeProvince(String value) {
        String n = value.toLowerCase(Locale.ROOT).replaceAll("[\\s_'-]", "");
        // Shaanxi / Shanxi must remain distinct.
        for (String[] item : PROVINCES) {
            if (n.startsWith(item[0]) || n.startsWith(item[1])) return item[0];
        }
        if (n.startsWith("innermongolia") || n.startsWith("neimongol")) return "neimenggu";
        if (n.startsWith("tibet")) return "xizang";
        return null;
    }

    static String normalizeCarrier(String value) {
        String n = value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
        if (n.contains("移动") || n.contains("chinamobile") || n.contains("cmcc")) return "mobile";
        if (n.contains("联通") || n.contains("unicom") || n.contains("cncgroup")
                || n.contains("china169")) return "unicom";
        if (n.contains("电信") || n.contains("telecom") || n.contains("chinanet")
                || n.contains("ctgnet")) return "telecom";
        return null;
    }
}
